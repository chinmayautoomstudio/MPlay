# Clips and ringtones

> Status: Shipped | Added in: 1.0 | Last updated: 2026-10-07

## Summary

From the song menu, **Cut and save** or **Set as ringtone** opens a full-screen trim editor with a waveform, two drag handles, 0.1 s nudge buttons and a looping preview. Cut and save asks for a name and saves an `.m4a` clip to `Music/MP3 Studio Clips`. Set as ringtone saves a clip and makes it the ringtone, notification or alarm sound. Both offer Undo.

## Key files

Paths are relative to `app/src/main/java/com/autoomstudio/mp3studio/`.

| File | Role |
|---|---|
| `data/clip/ClipExporter.kt` | Cuts the range to a temp AAC/M4A in `cacheDir` with Media3 Transformer. |
| `data/clip/ClipNames.kt` | Default name `"<title> (clip)"`, sanitising, `.m4a` extension. |
| `data/clip/ClipStore.kt` | Free-space check, MediaStore save, mark as sound, delete; `NotEnoughStorageException`. |
| `data/clip/PeakAccumulator.kt` | Folds 16-bit PCM into N normalised peak buckets. |
| `data/clip/RingtoneSetter.kt` | `Settings.System.canWrite` and `RingtoneManager` default URIs. |
| `data/clip/SoundType.kt` | `Ringtone` / `Notification` / `Alarm` mapping. |
| `data/clip/TrimRange.kt` | Immutable range: clamping, minimum length, nudges, ringtone default. |
| `data/clip/WaveformExtractor.kt` | Decodes the file with `MediaExtractor` + `MediaCodec` into peaks, emitting partial results. |
| `ui/trim/TrimEditorActivity.kt` | Hosts the editor; intent extras `com.autoomstudio.mp3studio.extra.TRIM_*`. |
| `ui/trim/TrimEditorScreen.kt` | Editor UI, sound type sheet, WRITE_SETTINGS dialog, snackbars. |
| `ui/trim/TrimEditorViewModel.kt` | State, preview player, export, ringtone setting, undo, error mapping. |
| `ui/trim/WaveformView.kt` | Canvas waveform with handles, playhead and accessibility actions. |

## How it works

1. `MainScreen` launches `TrimEditorActivity.intent(context, song, TrimMode.Cut | TrimMode.Ringtone)`. Ringtone mode starts with the first 30 s; Cut mode with the full track.
2. **Waveform:** decodes the whole file to 500 peak buckets, sending partial results about every 300 ms. A failure disables editing.
3. **Preview:** a private `ExoPlayer` loops the range (polls every 50 ms). It stops on `ON_STOP` and on audio focus loss.
4. **Export:** Media3 Transformer with `ClippingConfiguration`, video removed, AAC at 192 kbps, `InAppMp4Muxer` (non-streamable to avoid a ~400 KB reservation). Output `cacheDir/clip-<millis>.m4a`. Cancellable.
5. **Space check:** estimated clip size x2 plus 5 MB margin against the smaller of cache and external free space.
6. **Save:**
   - Android 10+: MediaStore insert into `Music/MP3 Studio Clips/` with `IS_PENDING`, `IS_MUSIC = 1`, title and artist.
   - Android 8-9: file in the public Music folder with `(n)` collision suffix, then media scan.
7. **Ringtone flow:** pick a type; if `Settings.System.canWrite()` is false, a dialog offers `ACTION_MANAGE_WRITE_SETTINGS` and retries on resume. The clip is saved, flagged `IS_RINGTONE` / `IS_NOTIFICATION` / `IS_ALARM`, and set as default. Undo restores the previous default URI. Without permission it shows a snackbar linking to sound settings.
8. **Errors:** storage, permission, unsupported file, export failed and save failed each map to a `TrimError` message.

Constants (`TrimRange`): `MIN_CLIP_MS = 1000`, `NUDGE_MS = 100`, `RINGTONE_DEFAULT_MS = 30000`, `LONG_RINGTONE_MS = 40000` (warning shown above this).

## Data and persistence

- MediaStore rows/files under `Music/MP3 Studio Clips/` (also shown in the library because `IS_MUSIC = 1`).
- System default sounds in `Settings.System`.
- No DataStore keys or Room tables.

## Manifest, permissions and notifications

- `TrimEditorActivity` (not exported, `Theme.MPlay`).
- `WRITE_SETTINGS` (special permission), `WRITE_EXTERNAL_STORAGE` (max 29) for legacy saves.

## Tests

- `data/clip/ClipNamesTest.kt`, `PeakAccumulatorTest.kt`, `TrimRangeTest.kt`.
- No tests for exporter, store, extractor, setter or ViewModel.

## Known limitations and TODOs

- Always re-encodes to AAC 192 kbps.
- The waveform assumes 16-bit PCM decoder output.
- Ringtone mode saves under the default name with no rename.
- The legacy save path does not write title or artist.
- `EXTRA_SONG_ID` is passed but unused.
- The preview takes audio focus, pausing main playback.

## Change history

| Date | Commit | Change |
|---|---|---|
| 2026-10-02 | `23e2ee1` | Clip editing, trim editor, ringtone setting. |
| 2026-10-07 | - | Clips folder renamed to `Music/MP3 Studio Clips` (`ClipStore.CLIPS_FOLDER`); `LEGACY_CLIPS_RELATIVE_PATH` keeps old clips exempt from the 30 s minimum. `TrimEditorActivity` finishes on sign-out. |
