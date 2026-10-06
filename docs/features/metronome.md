# Metronome and BPM detection

> Status: Unreleased | Added in: next version after 3.1 | Last updated: 2026-10-06 (`e13b2f7`)

## Summary

A metronome available as its own tab and as a sheet on Now Playing: 20 to 300 BPM, time-signature presets, accented first beat, three generated click sounds, volume and tap tempo. It plays alongside music without pausing or ducking it, and keeps running with the screen off from a notification with a Stop button. On Now Playing it can detect the current song's BPM (with half/double fixes), cached per song.

## Key files

Paths are relative to `app/src/main/java/com/autoomstudio/mplay/`.

| File | Role |
|---|---|
| `metronome/BeatClock.kt` | Beat positions in audio frames; drift-free; tempo changes apply after the current beat. |
| `metronome/ClickSounds.kt` | Click sounds generated in code, cached per sound and accent. |
| `metronome/MetronomeEngine.kt` | Low-latency `AudioTrack`, writer thread, beat-watcher thread; `BeatTick`. |
| `metronome/MetronomeController.kt` | App-wide singleton: start/stop, debounced settings save, audio focus, call muting, service start; `MetronomeState`. |
| `metronome/MetronomeService.kt` | Foreground service, ongoing notification with Stop. |
| `metronome/MetronomeSettings.kt` | `MetronomeSettings`, `ClickSound`, `TimeSignature`, limits. |
| `metronome/TapTempo.kt` | Tap tempo. |
| `metronome/TempoDetector.kt` | Spectral flux + autocorrelation BPM estimator; `TempoEstimate`, `TempoConfidence`. |
| `data/tempo/SongTempoAnalyzer.kt` | Decodes 45 s of a song, runs the detector, caches in Room. |
| `data/tempo/TempoEntities.kt` | `SongTempoEntity` (`song_tempos`), `TempoDao`. |
| `ui/metronome/MetronomeControls.kt` | Shared controls (beat dots, BPM, tap, signature, accent, sound, volume). |
| `ui/metronome/MetronomeScreen.kt` | Metronome tab. |
| `ui/metronome/MetronomePlayerUi.kt` | `MetronomeChip`, `MetronomeSheet` (Detect BPM), `MetronomeMiniIndicator`. |
| `ui/metronome/MetronomeViewModel.kt` | Controller wrapper, tap tempo, detection states. |
| `ui/components/MetronomeIcons.kt` | Metronome vector icon. |
| `app/src/main/res/drawable/ic_notification_metronome.xml` | Notification icon. |

## How it works

### Audio engine

- Dedicated `AudioTrack` (float, mono, stream, `PERFORMANCE_MODE_LOW_LATENCY`, `USAGE_MEDIA` / `CONTENT_TYPE_SONIFICATION`) at the native output rate (fallback 48 kHz). The system mixes it with the music player.
- Sample-accurate scheduling: the writer thread (`THREAD_PRIORITY_URGENT_AUDIO`) fills 256-frame blocks and `BeatClock.beatsIn()` gives each beat's exact frame offset. Click tails continue into the next block.
- Beat n is at `ceil(anchor + n * sampleRate * 60 / bpm)` in double precision, so there is no drift. A BPM change re-anchors after the next beat.
- Visual sync: a watcher thread polls `playbackHeadPosition` every 4 ms and emits each beat when it is actually heard.

### Settings

- BPM 20-300 (default 120), via +/-1, slider, or a dialog.
- Presets 2/4, 3/4, 4/4, 6/8; beats per bar 1-12; only beat 1 is accented when accent is on. `beatUnit` is display-only.
- Sounds: `Classic` (1800/2600 Hz), `WoodBlock` (900/1250 Hz with overtone), `SoftBeep` (880/1320 Hz). Volume default 0.8.
- Saved with a 400 ms debounce.

### Tap tempo

Resets after 2 s without a tap, keeps the last 8 taps, needs at least 4. BPM = 60000 / average interval, rounded and clamped.

### Tempo detection

1. `SongTempoAnalyzer` uses the instrumental stem if available, otherwise the original. It decodes a 45 s window from the middle of the song (start clamped to 0-45 s), downmixes and decimates to 11,025 Hz.
2. `TempoDetector`: Hann window 1024 / hop 128, log-compressed spectral flux, moving-average removal and Gaussian smoothing, then normalised autocorrelation. Lags for 40-240 BPM are scored with a comb (`acf[lag] + 0.5 * acf[2 * lag]`) and a log-Gaussian prior around 120 BPM, refined by parabolic interpolation, folded into 60-200 BPM and rounded to 0.1.
3. Confidence: High if score at least 0.3, Medium at least 0.12, otherwise Low. Returns null for silence or under 400 envelope frames.
4. The result sets the metronome BPM; half/double buttons scale it.

### Audio focus and calls

- If MPlay music is playing, it doesn't request focus. Otherwise it requests `AUDIOFOCUS_GAIN` without pausing when ducked.
- On focus loss it waits 600 ms; if MPlay's own player took focus it keeps running, otherwise it stops. Transient loss mutes.
- Calls are detected by polling `AudioManager.mode` every 500 ms (no phone permission) and mute the clicks while the beat continues.

### Service and notification

`MetronomeController.start()` starts `MetronomeService` (`mediaPlayback` on API 29+). Channel `metronome`, ID 4201, text like "120 BPM · 4/4". Tap opens the Metronome tab (`EXTRA_OPEN_METRONOME`); Stop sends `com.autoomstudio.mplay.metronome.STOP`. The service stops itself when the metronome stops.

### Integration

- `PlaybackService` mirrors `isPlaying` into `AppContainer.musicPlaying`.
- Now Playing shows `MetronomeChip`; the mini player shows `MetronomeMiniIndicator`.
- Sing-along's count-in uses the metronome BPM if it is running.

## Data and persistence

- Room `song_tempos` (schema v4): `songId`, `sizeBytes`, `dateModified`, `bpm`, `confidence`. Stale entries are deleted when size or date change. See [database.md](../database.md).
- DataStore: `metronome_bpm`, `metronome_beats`, `metronome_unit`, `metronome_accent`, `metronome_sound`, `metronome_volume`. Running state is not saved.

## Manifest, permissions and notifications

- `metronome.MetronomeService` (not exported, `foregroundServiceType="mediaPlayback"`).
- Uses `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` (optional). No microphone or phone permission.

## Tests

- `metronome/BeatClockTest.kt`: exact spacing, under 1 frame error over 10 minutes, tempo change on next beat.
- `metronome/TapTempoTest.kt`: minimum taps, averaging, 8-tap window, reset, clamping.
- `metronome/TempoDetectorTest.kt`: synthetic click tracks at many tempos, octave folding, accents, noise, confidence, silence.
- No tests for click sounds, settings parsing, controller, engine, service, analyzer or UI.

## Known limitations and TODOs

- **No phase alignment with the song.** Detect BPM sets the tempo only; clicks don't land on the song's beats and nothing re-aligns on seek.
- 6/8 plays as six even beats; no subdivisions or per-beat accents.
- Detection folds to 60-200 BPM and uses one 45 s window; cached results have no score.
- Settings load asynchronously, so an early `update()` can be overwritten.
- `AudioTrack` creation failure is silent.
- Call detection can take up to 500 ms.
- `stop()` can block the main thread up to 500 ms per thread join.

## Change history

| Date | Commit | Change |
|---|---|---|
| 2026-10-06 | `e13b2f7` | Metronome feature, BPM detection, `song_tempos` table (schema v4), Metronome tab, Now Playing chip and sheet, foreground service. |
