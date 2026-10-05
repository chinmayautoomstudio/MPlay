"""Writes reference data that the Android pipeline is checked against.

1. DSP reference (no model weights needed), read by the JVM tests in :separation:
     python tools/make_reference.py dsp
   Writes separation/src/test/resources/reference/{meta.json, stft_frames.f32, istft_samples.f32}.
   The input is a synthetic test signal that the Kotlin test generates with the same formula.

2. Song reference (downloads the htdemucs weights once; needs ffmpeg on PATH), used by the spike app's comparison:
     python tools/make_reference.py song path/to/song.mp3 --out build/reference [--seconds 30]
   Runs the original Demucs with shifts=0 and overlap=0.25 (the settings the app uses) and writes
   <name>.vocals.f32 and <name>.instrumental.f32: interleaved stereo float32 little-endian at 44.1 kHz.
   Copy them next to the song on the device to compare in the spike app.
"""

import argparse
import json
import math
import os

import numpy as np
import torch

SEGMENT = 343980
SAMPLE_RATE = 44100
STFT_FRAMES = [0, 1, 2, 167, 334, 335]
ISTFT_DECIMATION = 97
DEFAULT_DSP_OUT = os.path.join(os.path.dirname(__file__), "..", "separation", "src", "test", "resources", "reference")


def test_signal(length: int) -> np.ndarray:
    """Must match ReferenceSignal.kt exactly."""
    out = np.zeros((2, length), dtype=np.float64)
    n = np.arange(length, dtype=np.float64)
    t = n / SAMPLE_RATE
    for c in range(2):
        state = 12345 + c
        noise = np.empty(length, dtype=np.float64)
        for i in range(length):
            state = (state * 1103515245 + 12345) % (1 << 31)
            noise[i] = state / float(1 << 31) * 2.0 - 1.0
        out[c] = 0.3 * np.sin(2 * math.pi * 220.0 * t + c) + 0.2 * np.sin(2 * math.pi * 3150.5 * t) + 0.1 * noise
    return out.astype(np.float32)


def mask_gain(freqs: int, frames: int) -> torch.Tensor:
    """Must match the gain in SpectrogramReferenceTest.kt."""
    f = torch.arange(freqs, dtype=torch.float64)[:, None]
    t = torch.arange(frames, dtype=torch.float64)[None, :]
    return (0.5 + 0.5 * torch.cos(0.013 * f + 0.17 * t)).to(torch.float32)


@torch.no_grad()
def make_dsp(out_dir: str):
    from demucs.htdemucs import HTDemucs

    model = HTDemucs(sources=["drums", "bass", "other", "vocals"]).eval()
    mix = torch.from_numpy(test_signal(SEGMENT))[None]
    z = model._spec(mix)  # [1, 2, 2048, 336] complex
    mag = model._magnitude(z)  # [1, 4, 2048, 336]
    assert mag.shape == (1, 4, 2048, 336), mag.shape

    frames = mag[0][:, :, STFT_FRAMES].permute(2, 0, 1).contiguous()  # [frame, channel, freq]
    os.makedirs(out_dir, exist_ok=True)
    frames.numpy().astype("<f4").tofile(os.path.join(out_dir, "stft_frames.f32"))

    masked = z * mask_gain(z.shape[-2], z.shape[-1])
    wave = model._ispec(masked[:, None], SEGMENT)[0, 0]  # [2, SEGMENT]
    wave[:, ::ISTFT_DECIMATION].numpy().astype("<f4").tofile(os.path.join(out_dir, "istft_samples.f32"))

    meta = {
        "segment": SEGMENT,
        "stftFrames": STFT_FRAMES,
        "istftDecimation": ISTFT_DECIMATION,
        "torch": torch.__version__,
    }
    with open(os.path.join(out_dir, "meta.json"), "w") as f:
        json.dump(meta, f, indent=2)
    print(f"wrote DSP reference to {os.path.abspath(out_dir)}")


@torch.no_grad()
def make_song(path: str, out_dir: str, seconds: float | None):
    from demucs.apply import apply_model
    from demucs.audio import AudioFile
    from demucs.pretrained import get_model

    model = get_model("htdemucs").eval()
    wav = AudioFile(path).read(streams=0, samplerate=SAMPLE_RATE, channels=2)
    if seconds:
        wav = wav[:, : int(seconds * SAMPLE_RATE)]
    ref = wav.mean(0)
    mean, std = ref.mean(), ref.std()
    sources = apply_model(model, ((wav - mean) / std)[None], shifts=0, split=True, overlap=0.25, progress=True)[0]
    names = list(model.sources)
    # The track mean is added once per output stem (Demucs adds it to each of the four sources).
    vocals = sources[names.index("vocals")] * std + mean
    instrumental = sum(sources[names.index(s)] for s in ("drums", "bass", "other")) * std + mean

    os.makedirs(out_dir, exist_ok=True)
    base = os.path.join(out_dir, os.path.splitext(os.path.basename(path))[0])
    for name, stem in (("vocals", vocals), ("instrumental", instrumental)):
        stem.t().contiguous().numpy().astype("<f4").tofile(f"{base}.{name}.f32")
    print(f"wrote {base}.vocals.f32 and {base}.instrumental.f32 ({wav.shape[-1]} frames)")


def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    dsp = sub.add_parser("dsp")
    dsp.add_argument("--out", default=DEFAULT_DSP_OUT)
    song = sub.add_parser("song")
    song.add_argument("path")
    song.add_argument("--out", default="build/reference")
    song.add_argument("--seconds", type=float, default=None)
    args = parser.parse_args()
    if args.command == "dsp":
        make_dsp(args.out)
    else:
        make_song(args.path, args.out, args.seconds)


if __name__ == "__main__":
    main()
