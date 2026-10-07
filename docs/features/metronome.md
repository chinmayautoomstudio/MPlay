# Metronome and BPM detection

> Status: Unreleased | Added in: next version after 3.1 | Last updated: 2026-10-07

## Summary

A metronome available as its own tab and as a sheet on Now Playing: 20 to 300 BPM, time-signature presets, accented first beat, three generated click sounds, volume and tap tempo. It plays alongside music without pausing or ducking it, and keeps running with the screen off from a notification with a Stop button. On Now Playing it can detect the current song's BPM (with half/double fixes) and time signature (2/4, 3/4, 4/4, 6/8), cached per song, and then click in sync with the song: on its beats, silent while it is paused, re-aligned after seeks.

## Key files

Paths are relative to `app/src/main/java/com/autoomstudio/mplay/`.

| File | Role |
|---|---|
| `metronome/BeatClock.kt` | Beat positions in audio frames; drift-free; tempo changes apply after the current beat; `align()`/`unalign()` for a song's grid. |
| `metronome/ClickSounds.kt` | Click sounds generated in code, cached per sound and accent. |
| `metronome/MetronomeEngine.kt` | Low-latency `AudioTrack`, writer thread, beat-watcher thread; `BeatTick`; pending alignment and `heardFrame()`. |
| `metronome/MetronomeController.kt` | App-wide singleton: start/stop, debounced settings save, audio focus, call muting, service start, sync with the song; `MetronomeState`, `SyncState`. |
| `metronome/MusicTimeline.kt` | What sync needs from the player (`songId`, `playing`, `seeks`, `snapshot()`); `MusicSnapshot`. Implemented by `PlaybackController`. |
| `metronome/SongSync.kt` | Pure math from a `BeatGrid` and the two "heard at" timestamps to an engine `Alignment`, and the drift in ms. |
| `metronome/MetronomeService.kt` | Foreground service, ongoing notification with Stop. |
| `metronome/MetronomeSettings.kt` | `MetronomeSettings`, `ClickSound`, `TimeSignature`, limits. |
| `metronome/TapTempo.kt` | Tap tempo. |
| `metronome/TempoDetector.kt` | Spectral flux + autocorrelation BPM estimator; `detect()` gives `TempoEstimate`, `detectRhythm()` gives `RhythmEstimate` (tempo + meter); `TempoConfidence`. |
| `metronome/MeterDetector.kt` | Time signature estimator on the detector's envelopes and beat period; `MeterEstimate`, `BeatGrid`. |
| `data/tempo/SongTempoAnalyzer.kt` | Decodes 45 s of a song from an exact start, runs the detector, moves the grid to song time, caches in Room. |
| `data/tempo/TempoEntities.kt` | `SongTempoEntity` (`song_tempos`), `TempoDao`. |
| `data/tempo/TempoCache.kt` | Row to `RhythmEstimate` conversion and the cache decision (`TempoLookup`). |
| `ui/metronome/MetronomeDial.kt` | `BpmDial`: glowing BPM ring (pulses on each beat), beat dots, -/+ circles, decorative waveform bars. |
| `ui/metronome/MetronomeControls.kt` | Shared layout: header, dial, tempo slider, Tap tempo tile (`ActionTile`), Sound / Start-Stop / Settings row, sound settings sheet, Time signature card (presets, Custom dialog, beats-per-bar stepper), Accent card; `MetronomeCard`. |
| `ui/metronome/MetronomeScreen.kt` | Metronome tab. |
| `ui/metronome/MetronomePlayerUi.kt` | `MetronomeChip`, `MetronomeSheet` (Detect BPM / ÷2 / ×2 row, result card with "Looks like 3/4. Use it?" pill, Undo snackbar, "Sync with song" tile), `MetronomeMiniIndicator`. |
| `ui/metronome/MetronomeViewModel.kt` | Controller wrapper, tap tempo, detection states, applying/undoing the detected meter (`MeterApplied`), starting sync and `toggleSync()`. |
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

### Screen layout

- Both the tab and the Now Playing sheet use `MetronomeControls`: header ("Keep your perfect rhythm"; the sheet adds a back button that closes it), `BpmDial` (tap the number to type a BPM), slider with 20/300 labels, Tap tempo tile, then Sound / big Start-Stop / Settings, the Time signature card and the Accent card.
- The sheet adds, via `tempoExtras` and `syncTile`, the Detect BPM / ÷2 / ×2 row, the result card, and a "Sync with song" tile next to Tap tempo once a detection is done.
- **Sound** is a quick mute: `MetronomeController.setUserMuted()` sets `MetronomeState.muted`; the beat keeps running and the dial keeps pulsing. Not saved, but kept across stop and start.
- **Settings** opens a bottom sheet with the click sound and volume.
- **Custom** in the Time signature card opens a dialog for beats per bar (1-12) and beat unit (1/4 or 1/8 note); it shows the current signature when it isn't a preset.

### Tap tempo

Resets after 2 s without a tap, keeps the last 8 taps, needs at least 4. BPM = 60000 / average interval, rounded and clamped.

### Tempo detection

1. `SongTempoAnalyzer` uses the instrumental stem if available, otherwise the original. It decodes a 45 s window from the middle of the song (start clamped to 0-45 s), downmixes and decimates to 11,025 Hz.
2. `TempoDetector`: Hann window 1024 / hop 128, log-compressed spectral flux, moving-average removal and Gaussian smoothing, then normalised autocorrelation. Lags for 40-240 BPM are scored with a comb (`acf[lag] + 0.5 * acf[2 * lag]`) and a log-Gaussian prior around 120 BPM, refined by parabolic interpolation, folded into 60-200 BPM and rounded to 0.1.
3. Confidence: High if score at least 0.3, Medium at least 0.12, otherwise Low. Returns null for silence or under 400 envelope frames.
4. The result sets the metronome BPM; half/double buttons scale it.

### Time signature detection (MT18-MT21)

1. The same FFT pass also builds two linear-magnitude flux envelopes: full-band (`accents`) and below 200 Hz (`low`, kick and bass). The log envelope used for tempo hardly tells a loud hit from a soft one, so it isn't used for accents.
2. `MeterDetector` places a comb at the beat period (matching the reported BPM) at the phase with the most accent energy, and takes each beat's peak full-band and low-band strength, each divided by its mean and blended 0.4/0.6.
3. For bars of 2, 3, 4 and 6 beats it computes a t-statistic of the strongest position against the rest, weighted by the autocorrelation of the beat strengths at the bar length and its multiples. Duple (2, 4) and triple (3, 6) families get priors 1.0 and 0.85.
4. Nested bars: 4/4 needs beat 1 significantly stronger than beat 3, otherwise 2/4 (always Low). 6/8 needs pulse 4 weaker than pulse 1 but stronger than its neighbours, checked on the beat grid (tempo locked to eighths) and on the half-beat grid (tempo locked to quarters). Two-beat bars whose beats divide in three (onsets at 1/3 and 2/3 rather than 1/2) are 6/8 felt in two.
5. Confidence comes from the winning score (Medium at least 8, High at least 16) and its margin over the other family (1.3, 1.8). Low tempo confidence caps it at Low. A best score under 4 gives 4/4 Low. Null when there are fewer than 8 bars of 4 beats.
6. **6/8 and BPM**: `MeterEstimate.clickBpm` is the BPM the metronome uses with the meter. For 6/8 it is the eighth-note rate (tempo x1 when locked to eighths, x2 when locked to quarters, x3 when locked to the dotted-quarter pulse), so the metronome's six clicks fill one bar. If that rate would exceed 300 the result stays 2/4 (or 3/4) at the detected BPM.
7. `MetronomeViewModel.detect()`: with Medium/High confidence it sets BPM (`clickBpm`), `beatsPerBar` and `beatUnit` in one update and emits `MeterApplied`; the sheet shows "Time signature set to 3/4" with Undo, which restores the previous signature and the plain detected BPM. With Low confidence only the BPM is set and a "Looks like 3/4. Use it?" pill on the result card applies it. With no meter the signature is kept and the card says "Couldn't tell the time signature".
8. The card reads "120 BPM, 4/4" (6/8 adds "felt as" the pulse) with one confidence line for the tempo and one for the meter. The time-signature selector stays editable.

### Sync with the song

1. **Beat grid.** `MeterDetector` also returns where the beats are: the comb phase is refined between envelope frames (linear interpolation plus a parabola through the best offset), and the downbeat is the accented bar position. `TempoDetector.detectRhythm()` turns it into `BeatGrid(downbeatMs, periodMs)`, subtracting the onset envelope's delay (`ONSET_DELAY_SAMPLES` = 676, calibrated on synthetic loops to within about 2 ms). The period is the click period: the eighth note for 6/8, so it matches `clickBpm`. With no meter the grid uses the tempo's beat.
2. **Song time.** `SongTempoAnalyzer` decodes with `MediaPcmSource(exactStart = true)`, so the window starts exactly at `startMs`, and shifts the grid by `startMs`.
3. **Start.** After Detect, `MetronomeViewModel` calls `MetronomeController.syncTo(songId, grid)` (starting the metronome if needed). When the meter wasn't applied (Low confidence), the grid period is multiplied up to the detected tempo's beat. Applying the meter, Undo and ÷2/×2 (`scaleTempo()`, which scales the grid exactly) keep sync on; any other BPM change (slider, +/-, tap tempo) ends it. The "Sync with song" tile turns it on and off.
4. **Mapping.** `SongSync.align()` combines `MusicTimeline.snapshot()` (song position, `System.nanoTime()`, playback speed) with `MetronomeEngine.heardFrame()` (from `AudioTrack.getTimestamp`, falling back to the head position before audio flows) to find the output frame of song beat 0 and the frames per beat at the current speed.
5. **Engine.** `MetronomeEngine.align()` hands the `Alignment` to the writer thread, which calls `BeatClock.align()`. Beat indices become song beat numbers (negative before the downbeat), the bar position is `floorMod(beat, beatsPerBar)`, and the set BPM is ignored until `unalign()`. A grid beat less than 40% of a beat after the last click is skipped, so re-aligning never double-clicks.
6. **Following.** While synced, `MetronomeController` mutes the clicks while the music is paused (with calls and transient focus loss) and re-aligns when it resumes, after every seek or speed change (`seeks`), 500 ms after starting, and every 2 s if the drift is over 20 ms. When the song changes, sync ends; if sync started the metronome it stops too.
7. The "Sync with song" tile shows "Clicks follow the song" or "Sync paused with the music" and is highlighted while sync is on.

### Audio focus and calls

- If MPlay music is playing, it doesn't request focus. Otherwise it requests `AUDIOFOCUS_GAIN` without pausing when ducked.
- On focus loss it waits 600 ms; if MPlay's own player took focus it keeps running, otherwise it stops. Transient loss mutes.
- Calls are detected by polling `AudioManager.mode` every 500 ms (no phone permission) and mute the clicks while the beat continues.

### Service and notification

`MetronomeController.start()` starts `MetronomeService` (`mediaPlayback` on API 29+). Channel `metronome`, ID 4201, text like "120 BPM · 4/4". Tap opens the Metronome tab (`EXTRA_OPEN_METRONOME`); Stop sends `com.autoomstudio.mplay.metronome.STOP`. The service stops itself when the metronome stops.

### Integration

- `PlaybackService` mirrors `isPlaying` into `AppContainer.musicPlaying`.
- Now Playing shows `MetronomeChip`; the mini player shows `MetronomeMiniIndicator`.
- `AppContainer` gives `MetronomeController` its own main-thread `PlaybackController` as the `MusicTimeline` (`restoresSession = false`); it connects to `PlaybackService` only while sync is on.
- Sing-along's count-in uses the metronome BPM if it is running.

## Data and persistence

- Room `song_tempos` (schema v6): `songId`, `sizeBytes`, `dateModified`, `bpm`, `confidence`, and nullable `beatsPerBar`, `beatUnit`, `meterConfidence`, `meterBpm`, `downbeatMs`, `beatPeriodMs`. Stale entries are deleted when size or date change. `meterConfidence` or `beatPeriodMs` null means a row from before meter or grid detection, which `TempoCache.lookup` reports as `Incomplete` (one more analysis); `"None"` (`TempoCache.NO_METER`) means analyzed with no meter and `0.0` (`TempoCache.NO_GRID`) analyzed with no grid. See [database.md](../database.md).
- Sync state and the Sound mute toggle are not saved.
- DataStore: `metronome_bpm`, `metronome_beats`, `metronome_unit`, `metronome_accent`, `metronome_sound`, `metronome_volume`. Running state is not saved.

## Manifest, permissions and notifications

- `metronome.MetronomeService` (not exported, `foregroundServiceType="mediaPlayback"`).
- Uses `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` (optional). No microphone or phone permission.

## Tests

- `metronome/BeatClockTest.kt`: exact spacing, under 1 frame error over 10 minutes, tempo change on next beat; aligned beats and song numbering (including negative), skipping a beat too close to the last click, unalign carrying on at the tempo.
- `metronome/SongSyncTest.kt`: anchor and period from the two timestamps, playback speed, signed drift, changed period.
- `metronome/TapTempoTest.kt`: minimum taps, averaging, 8-tap window, reset, clamping.
- `metronome/TempoDetectorTest.kt`: synthetic click tracks at many tempos, octave folding, accents, noise, confidence, silence.
- `metronome/MeterDetectorTest.kt`: synthetic drum loops (4/4 at 90/120/140, 3/4 at 90/120, 2/4 at 100, 6/8 with accents on pulses 1 and 4), each clean and with noise (28 runs, at least 90% required); unaccented clicks give 4/4 Low; noise null or Low; silence and short clips null; 6/8 clicks the eighths; the grid's downbeat within 6 ms (measured about 2 ms) and period within 0.1%, also with the window starting mid-bar.
- `metronome/SyntheticAudio.kt`: shared test audio (`clicks`, `pattern`).
- `data/tempo/TempoCacheTest.kt`: schema 4 and 5 rows force re-analysis, changed files are stale, round-trips including the grid, 6/8 `meterBpm`, "no meter" and "no grid".
- No tests for click sounds, settings parsing, controller (sync job), engine, `PlaybackController` timeline, service, analyzer I/O (exact start), the 5-to-6 migration on a device, or UI.

## Known limitations and TODOs

- **Sync uses one fixed grid.** Songs that speed up or slow down (live recordings, rubato) drift from it; re-alignment only fixes the phase, not the tempo.
- **Sync accuracy is untested on devices.** MP3/AAC encoder delay may shift the decoded audio against what the player outputs by a constant tens of ms; Bluetooth output latency is only as good as `AudioTrack.getTimestamp` and Media3's position reporting.
- The grid comes from the 45 s middle window; beats before it are extrapolated.
- 6/8 plays as six even beats; no subdivisions or per-beat accents.
- Detection folds to 60-200 BPM and uses one 45 s window; cached results have no score.
- Time signature: a 4/4 bar whose beats 1 and 3 sound alike is the same pattern as 2/4, so it is reported as 2/4 Low (chip only). When the tempo lands on half time the meter is judged on that grid (for example 4/4 at 140 read at 70 BPM falls back to 4/4 Low). Only 2/4, 3/4, 4/4 and 6/8 are considered. Tuned on synthetic audio only.
- Settings load asynchronously, so an early `update()` can be overwritten.
- `AudioTrack` creation failure is silent.
- Call detection can take up to 500 ms.
- `stop()` can block the main thread up to 500 ms per thread join.
- Signing out stops the metronome (`di/AuthEffects` calls `MetronomeController.stop()`); "Sync with song" is not yet limited to Pro (planned for the plans milestone).

## Change history

| Date | Commit | Change |
|---|---|---|
| 2026-10-06 | `e13b2f7` | Metronome feature, BPM detection, `song_tempos` table (schema v4), Metronome tab, Now Playing chip and sheet, foreground service. |
| 2026-10-06 | - | Time signature detection (MT18-MT21): `MeterDetector`, `detectRhythm()`, meter columns in `song_tempos` (schema v5), apply/Undo snackbar, suggestion chip. |
| 2026-10-06 | - | Sync with the song: `BeatGrid`, exact-start decoding, grid columns in `song_tempos` (schema v6), `BeatClock.align`, `MetronomeEngine.heardFrame`, `SongSync`, `MusicTimeline`, "Sync with song" chip. |
| 2026-10-06 | - | UI redesign: `BpmDial` ring, slider, action tiles, Sound mute toggle (`setUserMuted`), sound settings sheet, Time signature and Accent cards, Custom time signature dialog; same layout in the tab and the Now Playing sheet. |
| 2026-10-07 | - | The metronome stops on sign-out. See [accounts](accounts.md). |
