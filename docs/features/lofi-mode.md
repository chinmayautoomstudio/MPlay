# Lofi mode

> Status: Shipped | Added in: 2.0 | Last updated: 2026-10-06

## Summary

A toggle on Now Playing that makes music sound like an old record: playback at about 90% speed with lowered pitch, a low-pass filter, light bit-crushing, a small reverb, and vinyl hiss and crackle. Toggling crossfades without clicks, and the setting is remembered.

## Key files

Paths are relative to `app/src/main/java/com/autoomstudio/mp3studio/`.

| File | Role |
|---|---|
| `playback/lofi/LofiAudioProcessor.kt` | Media3 `BaseAudioProcessor` running `LofiEffect` in the ExoPlayer audio sink. |
| `playback/lofi/LofiEffect.kt` | DSP: `LofiEffect`, `Biquad` low-pass, `SchroederReverb`. |
| `playback/PlaybackService.kt` | `LofiRenderersFactory`, `applyLofi()`, `LOFI_PLAYBACK = PlaybackParameters(0.9f, 0.9f)`. |
| `ui/components/LofiWaveIcon.kt` | Animated five-bar icon. |
| `ui/playback/NowPlayingScreen.kt` | `LofiChip` and overflow menu item. |

## How it works

1. `LofiChip` calls `PlayerActions.setLofi`, which sends `SET_LOFI` to the service.
2. `applyLofi()` sets the processor's `@Volatile` enabled flag, switches `playbackParameters` between `LOFI_PLAYBACK` and default, publishes the `lofi` extra, and persists the flag.
3. `LofiRenderersFactory` installs the processor in `DefaultAudioSink`. It is always active; toggling only changes the wet mix, so the sink is never reconfigured.
4. The processor accepts 16-bit and float PCM. On flush (seek) it recreates the effect already settled, so it doesn't fade in again.

Effect constants: crossfade 0.15 s, low-pass 4500 Hz (Q 0.707), 512 crush levels, reverb mix 0.22, wet gain 0.9, hiss 0.01, 7 crackles/s, soft clip above 0.9. Noise uses a seeded xorshift generator so output is repeatable.

## Data and persistence

- DataStore `app_settings`: `lofi_enabled` (default `false`).
- Runtime state in the session extra `lofi`.

## Manifest, permissions and notifications

None; runs inside `PlaybackService`.

## Tests

- `playback/lofi/LofiEffectTest.kt`: bypass, fade-in, crossfade under 300 ms, no clipping, low-pass, settled start after seek, repeatable output.

## Known limitations and TODOs

- Positions advance at 0.9x wall-clock time while enabled (the sleep timer accounts for this).
- Only 16-bit and float PCM.
- Sing-along turns lofi off while recording and restores it afterwards.

## Change history

| Date | Commit | Change |
|---|---|---|
| 2026-10-03 | `201e1c9` | Lofi effect and processor. |
| 2026-10-03 | `9dd9584` | `LofiWaveIcon` and lofi chip on Now Playing. |
