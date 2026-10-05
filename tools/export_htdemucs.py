"""Exports HT-Demucs to ONNX with STFT and iSTFT left outside the graph.

The Android pipeline (module :separation) computes the spectrogram and its inverse itself, because
ONNX Runtime Mobile has no complex STFT. The exported graph takes one fixed-size segment:

  inputs   mix   float32 [1, 2, 343980]       segment audio, already normalized with whole-track stats
           mag   float32 [1, 4, 2048, 336]    spectrogram of `mix` as channels L.re, L.im, R.re, R.im
  outputs  spec  float32 [1, 4, 4, 2048, 336] per-source spectrogram, same channel layout as `mag`
           wave  float32 [1, 4, 2, 343980]    per-source time-branch output

Sources are ordered drums, bass, other, vocals. Each stem is istft(spec) + wave.

Usage (Python 3.10-3.12):
  pip install -r tools/requirements.txt
  python tools/export_htdemucs.py --out build/model/htdemucs.onnx
  python tools/export_htdemucs.py --out build/model/htdemucs_fp16.onnx --fp16

--fp16 stores weights as float16 with a Cast back to float32 in front of each use, so the file is about
half the size while every operator still runs in float32 on the CPU.
"""

import argparse
import os

import numpy as np
import onnx
import torch
from demucs.htdemucs import HTDemucs
from demucs.pretrained import get_model
from onnx import TensorProto, helper, numpy_helper

SEGMENT = 343980
FREQS = 2048
FRAMES = 336


class HTDemucsCore(torch.nn.Module):
    """HTDemucs.forward from demucs 4.0.1 without _spec/_magnitude at the start and _mask/_ispec at the end."""

    def __init__(self, model: HTDemucs):
        super().__init__()
        self.m = model

    def forward(self, mix, mag):
        m = self.m
        x = mag
        B, C, Fq, T = x.shape

        mean = x.mean(dim=(1, 2, 3), keepdim=True)
        std = x.std(dim=(1, 2, 3), keepdim=True)
        x = (x - mean) / (1e-5 + std)

        xt = mix
        meant = xt.mean(dim=(1, 2), keepdim=True)
        stdt = xt.std(dim=(1, 2), keepdim=True)
        xt = (xt - meant) / (1e-5 + stdt)

        saved, saved_t, lengths, lengths_t = [], [], [], []
        for idx, encode in enumerate(m.encoder):
            lengths.append(x.shape[-1])
            inject = None
            if idx < len(m.tencoder):
                lengths_t.append(xt.shape[-1])
                tenc = m.tencoder[idx]
                xt = tenc(xt)
                if not tenc.empty:
                    saved_t.append(xt)
                else:
                    inject = xt
            x = encode(x, inject)
            if idx == 0 and m.freq_emb is not None:
                frs = torch.arange(x.shape[-2], device=x.device)
                emb = m.freq_emb(frs).t()[None, :, :, None].expand_as(x)
                x = x + m.freq_emb_scale * emb
            saved.append(x)

        if m.crosstransformer:
            if m.bottom_channels:
                b, c, f, t = x.shape
                x = x.reshape(b, c, f * t)
                x = m.channel_upsampler(x)
                x = x.reshape(b, -1, f, t)
                xt = m.channel_upsampler_t(xt)
            x, xt = m.crosstransformer(x, xt)
            if m.bottom_channels:
                b, c, f, t = x.shape
                x = x.reshape(b, c, f * t)
                x = m.channel_downsampler(x)
                x = x.reshape(b, -1, f, t)
                xt = m.channel_downsampler_t(xt)

        for idx, decode in enumerate(m.decoder):
            skip = saved.pop(-1)
            x, pre = decode(x, skip, lengths.pop(-1))
            offset = m.depth - len(m.tdecoder)
            if idx >= offset:
                tdec = m.tdecoder[idx - offset]
                length_t = lengths_t.pop(-1)
                if tdec.empty:
                    pre = pre[:, :, 0]
                    xt, _ = tdec(pre, None, length_t)
                else:
                    skip = saved_t.pop(-1)
                    xt, _ = tdec(xt, skip, length_t)

        S = len(m.sources)
        x = x.view(B, S, -1, Fq, T)
        x = x * std[:, None] + mean[:, None]
        xt = xt.view(B, S, -1, SEGMENT)
        xt = xt * stdt[:, None] + meant[:, None]
        return x, xt


def load_model() -> HTDemucs:
    bag = get_model("htdemucs")
    model = bag.models[0] if hasattr(bag, "models") else bag
    assert isinstance(model, HTDemucs), type(model)
    assert list(model.sources) == ["drums", "bass", "other", "vocals"], model.sources
    assert int(model.segment * model.samplerate) == SEGMENT, model.segment
    assert model.nfft == 4096 and model.hop_length == 1024 and model.cac
    model.eval()
    return model


def reference_inputs(model: HTDemucs, mix: torch.Tensor):
    z = model._spec(mix)
    mag = model._magnitude(z)
    return z, mag


@torch.no_grad()
def check_core_matches_full_model(model: HTDemucs, core: HTDemucsCore):
    torch.manual_seed(0)
    mix = torch.randn(1, 2, SEGMENT) * 0.1
    full = model(mix)
    z, mag = reference_inputs(model, mix)
    spec, wave = core(mix, mag)
    zout = model._mask(z, spec)
    rebuilt = model._ispec(zout, SEGMENT) + wave
    err = (rebuilt - full).abs().max().item()
    print(f"core + external istft vs full model: max abs error {err:.2e}")
    assert err < 1e-3, err


def convert_weights_to_fp16(path_in: str, path_out: str, min_elements: int = 1024):
    model = onnx.load(path_in)
    graph = model.graph
    new_inits, cast_nodes = [], []
    for init in graph.initializer:
        if init.data_type != TensorProto.FLOAT or np.prod(init.dims) < min_elements:
            new_inits.append(init)
            continue
        half_name = init.name + "__fp16"
        half = numpy_helper.from_array(numpy_helper.to_array(init).astype(np.float16), half_name)
        new_inits.append(half)
        cast_nodes.append(helper.make_node("Cast", [half_name], [init.name], to=TensorProto.FLOAT))
    del graph.initializer[:]
    graph.initializer.extend(new_inits)
    nodes = list(graph.node)
    del graph.node[:]
    graph.node.extend(cast_nodes + nodes)
    onnx.checker.check_model(model)
    onnx.save(model, path_out)


@torch.no_grad()
def verify_onnx(model: HTDemucs, core: HTDemucsCore, path: str):
    import onnxruntime as ort

    torch.manual_seed(1)
    mix = torch.randn(1, 2, SEGMENT) * 0.1
    _, mag = reference_inputs(model, mix)
    spec, wave = core(mix, mag)
    session = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    out_spec, out_wave = session.run(["spec", "wave"], {"mix": mix.numpy(), "mag": mag.numpy()})
    print(f"onnx vs torch: spec max abs error {np.abs(out_spec - spec.numpy()).max():.2e}, "
          f"wave max abs error {np.abs(out_wave - wave.numpy()).max():.2e}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True)
    parser.add_argument("--fp16", action="store_true", help="store weights as float16")
    parser.add_argument("--opset", type=int, default=17)
    parser.add_argument("--skip-verify", action="store_true")
    args = parser.parse_args()

    model = load_model()
    core = HTDemucsCore(model).eval()
    if not args.skip_verify:
        check_core_matches_full_model(model, core)

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    fp32_path = args.out + ".fp32.tmp" if args.fp16 else args.out
    mix = torch.zeros(1, 2, SEGMENT)
    mag = torch.zeros(1, 4, FREQS, FRAMES)
    torch.onnx.export(
        core,
        (mix, mag),
        fp32_path,
        input_names=["mix", "mag"],
        output_names=["spec", "wave"],
        opset_version=args.opset,
        do_constant_folding=True,
    )
    if args.fp16:
        convert_weights_to_fp16(fp32_path, args.out)
        os.remove(fp32_path)
    print(f"wrote {args.out} ({os.path.getsize(args.out) / 1e6:.1f} MB)")
    if not args.skip_verify:
        verify_onnx(model, core, args.out)


if __name__ == "__main__":
    main()
