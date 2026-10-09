# Lofi mode

> Status: Shipped | Added in: 2.0 | Last updated: 2026-10-09

## Summary

A toggle on Now Playing that makes music sound like an old record: playback at 85% speed with the pitch kept near the original, vocals pulled back so instruments sit level with them, a low-pass filter, light bit-crushing, a roomy reverb, and a soft vinyl hiss (no crackle). Toggling crossfades without clicks, and the setting is remembered.

## Key files

Paths are relative to `app/src/main/java/com/autoomstudio/mp3studio/`.

| File | Role |
|---|---|
| `playback/lofi/LofiAudioProcessor.kt` | Media3 `BaseAudioProcessor` running `LofiEffect` in the ExoPlayer audio sink. |
| `playback/lofi/LofiEffect.kt` | DSP: `LofiEffect`, `Biquad` (low-pass, peaking), `SchroederReverb`. |
| `playback/PlaybackService.kt` | `LofiRenderersFactory`, `applyLofi()`, `LOFI_PLAYBACK = PlaybackParameters(0.85f, 0.98f)`. |
| `ui/components/LofiWaveIcon.kt` | Animated five-bar icon. |
| `ui/playback/NowPlayingScreen.kt` | `LofiChip` and overflow menu item. |

## How it works

1. `LofiChip` calls `PlayerActions.setLofi`, which sends `SET_LOFI` to the service.
2. `applyLofi()` sets the processor's `@Volatile` enabled flag, switches `playbackParameters` between `LOFI_PLAYBACK` and default, publishes the `lofi` extra, and persists the flag.
3. `LofiRenderersFactory` installs the processor in `DefaultAudioSink`. It is always active; toggling only changes the wet mix, so the sink is never reconfigured.
4. The processor accepts 16-bit and float PCM. On flush (seek) it recreates the effect already settled, so it doesn't fade in again.

Wet chain per frame: for stereo, split into mid and side, apply a peaking dip to mid (vocals are usually centred) and multiply side by `SIDE_GAIN`, then recombine; other channel layouts get the dip per channel. Then low-pass, bit-crush, reverb, wet gain, and hiss, crossfaded with the dry signal and soft-clipped.

Effect constants: crossfade 0.15 s, presence dip -4 dB at 2500 Hz (Q 1.0), side gain 1.2, low-pass 5000 Hz (Q 0.707), 512 crush levels, reverb mix 0.3 (comb feedback 0.8, tail about 1.2 s), wet gain 0.85, hiss 0.01, soft clip above 0.9. Hiss uses a seeded xorshift generator so output is repeatable. If vocals still sit too high, `PRESENCE_DB` and `SIDE_GAIN` are the main tuning knobs.

## Data and persistence

- DataStore `app_settings`: `lofi_enabled` (default `false`).
- Runtime state in the session extra `lofi`.

## Manifest, permissions and notifications

None; runs inside `PlaybackService`.

## Tests

- `playback/lofi/LofiEffectTest.kt`: bypass, fade-in, crossfade under 300 ms, no clipping, low-pass, settled start after seek, repeatable output, silence gives only soft hiss (no crackle), vocal band quieter than bass, stereo sides lifted over the centre.

## Known limitations and TODOs

- Positions advance at 0.85x wall-clock time while enabled (the sleep timer accounts for this).
- The vocal pull-back relies on vocals being centred; hard-panned or mono vocals are only reduced by the presence dip.
- Only 16-bit and float PCM.
- Sing-along turns lofi off while recording and restores it afterwards.

## Change history

| Date | Commit | Change |
|---|---|---|
| 2026-10-03 | `201e1c9` | Lofi effect and processor. |
| 2026-10-03 | `9dd9584` | `LofiWaveIcon` and lofi chip on Now Playing. |
| 2026-10-08 | | Pitch 0.98x and speed 0.92x, vocal presence dip with stereo side lift, low shelf, 5.5 kHz low-pass, lighter reverb, crackle removed. |
| 2026-10-09 | | Retuned: speed 0.9x, side gain 1.2, low shelf removed, 5 kHz low-pass, reverb mix 0.2. |
| 2026-10-09 | | 3.2.2: speed 0.85x, reverb mix 0.3 with comb feedback 0.8 (longer tail). |
