# Playback

> Status: Shipped | Added in: 1.0 | Last updated: 2026-10-07

## Summary

Local songs play from a background Media3 session service, with the system media notification, lock-screen and headset controls, audio focus, and pause on headphone unplug. The UI is a single card that drags between a mini player and the full Now Playing screen, with seek bar, shuffle, repeat and a sleep timer that fades out over the last 30 seconds. The queue, position, shuffle and repeat survive the app being killed.

## Key files

Paths are relative to `app/src/main/java/com/autoomstudio/mplay/`.

| File | Role |
|---|---|
| `playback/PlaybackService.kt` | `MediaSessionService`: owns `ExoPlayer` + `MediaSession`, custom commands, session saving, stem switching, lofi, resumption. |
| `playback/PlaybackController.kt` | UI-side `MediaController` wrapper. `state: StateFlow<NowPlayingState?>`, `positionMs()`, queue/transport methods. Main thread only. Also the metronome's `MusicTimeline`: `songId`, `playing`, `seeks` (position discontinuities and playback-parameter changes), `snapshot()` (position, `System.nanoTime()`, speed). `restoresSession = false` skips restoring the saved queue on connect. |
| `playback/PlaybackCommands.kt` | Custom `SessionCommand`s, argument keys, session extras encode/decode. |
| `playback/NowPlayingState.kt` | `NowPlayingState`, `RepeatMode`, `nextRepeatMode()` (Off, All, One). |
| `playback/PlaybackSessionStore.kt` | DataStore for the saved queue (`SavedSession`) and modes (`PlaybackModes`). |
| `playback/SessionRestore.kt` | Pure `restoreQueue()` that rebuilds a saved queue against the current library. |
| `playback/SleepTimer.kt` | `SleepTimerStatus` and pure timer math. |
| `playback/SleepTimerRunner.kt` | `Player.Listener` in the service that ticks, fades and pauses. |
| `playback/SongMediaItems.kt` | `Song.toMediaItem()`, `originalUri`, `withPlaybackUri()` (used for stems). |
| `ui/playback/PlaybackViewModel.kt` | Implements `PlayerActions`; owns a `PlaybackController`. |
| `ui/playback/PlayerActions.kt` | Interface for transport, sleep timer, `setLofi`, `setStemMode`. |
| `ui/playback/ExpandablePlayer.kt` | One card that morphs from mini to full player by `expandProgress`. |
| `ui/playback/PlayerSheet.kt` | `AnchoredDraggableState<PlayerSheetValue>` anchors, fling and drag. |
| `ui/playback/MiniPlayer.kt` | Title/artist, play/pause, next, scrub strip, metronome indicator. |
| `ui/playback/NowPlayingScreen.kt` | Artwork, seek bar, transport, `StemModeSelector`, chip row (lofi, metronome, separator, sing-along), sheets. |
| `ui/playback/PlayPauseIcon.kt` | Lottie play/pause morph with icon fallback. |
| `ui/playback/SleepTimerSheet.kt` | Presets, end of song, 1-180 min slider, remaining-time chip. |

## How it works

### Service

- `ExoPlayer.Builder(this, LofiRenderersFactory(...))` with music audio attributes, `handleAudioFocus = true`, `setHandleAudioBecomingNoisy(true)`, `WAKE_MODE_LOCAL`.
- Listeners: `SkipUnplayableListener` (a failing stem falls back to the original file; otherwise the broken item is removed), `SessionSaver`, `WidgetStatePublisher`, the `musicPlaying` mirror, `SleepTimerRunner`.
- On create it restores shuffle/repeat, applies the saved lofi flag, loads `stemMode`, and watches `stemRepository.stemSets` to repoint stem URIs.
- The session activity `PendingIntent` opens `MainActivity` with `EXTRA_OPEN_NOW_PLAYING`. Notifications use Media3's default provider.
- `SessionCallback`:
  - `onConnectAsync` waits for `AuthRepository.awaitReady()` and rejects every controller (app, notification, system UI, Bluetooth) while signed out (PRD AU4). Signed in, it grants `PlaybackCommands.all` only to the app's own package.
  - `onCustomCommand` handles the commands below; anything else returns `ERROR_NOT_SUPPORTED`.
  - `onPlaybackResumption` rebuilds the saved queue for play requests from the widget, headset or system UI. It throws while signed out.
  - `onAddMediaItems` maps items through `resolve()` so the current `StemMode` applies.
- After a sign-in, a collector on `AuthRepository.state` waits for `SignedOut`, then `stopForSignOut()` saves the queue, cancels the sleep timer, stops and clears the player and calls `stopSelf()`. The saved queue (DataStore `playback_session`) is kept, so the next sign-in can resume it.
- `onTaskRemoved` saves and stops only if nothing is queued or playback is paused/ended. `onDestroy` clears `musicPlaying`, saves, publishes a paused widget state and releases.

### Custom commands and extras

| Command (`com.autoomstudio.mplay.command.*`) | Args |
|---|---|
| `SET_SLEEP_TIMER` | `minutes` |
| `SET_SLEEP_TIMER_END_OF_SONG` | |
| `EXTEND_SLEEP_TIMER` | |
| `CANCEL_SLEEP_TIMER` | |
| `SET_LOFI` | `enabled` |
| `SET_STEM_MODE` | `stem_mode` |

State flows back through session extras: `sleep_end_elapsed`, `sleep_end_of_song`, `lofi`, `stem_mode`.

### Controller

- Builds `MediaController` asynchronously; `withController {}` queues calls until connected.
- `positionMs()` polls every 500 ms.
- On connect with an empty queue, `restoreSession()` loads the saved queue paused.
- Added in `e13b2f7` for sing-along: `play()`, `pause()`, `currentPositionMs()`, `isSeekable()`.

### Sleep timer

- Constants: `FADE_MS = 30_000`, 1 to 180 minutes, default 30, extend by 10, presets 15/30/45/60.
- Timed mode fades volume linearly over the last 30 s, then pauses and keeps the queue.
- End-of-song mode uses `pauseAtEndOfMediaItems` and adjusts for playback speed (lofi runs at 0.9x).
- Auto-cancels when the queue empties. Runs only in memory.

### Session persistence

Saved on media item transition, timeline change, play/pause, discontinuity, and every 10 s while playing. Restore keeps the index if the song still exists, otherwise moves to the next surviving song at position 0, falling back to the last survivor.

## Data and persistence

- DataStore `playback_session`: `song_ids` (comma-joined), `index`, `position_ms`, `shuffle_enabled`, `repeat_mode`.
- DataStore `app_settings`: `last_sleep_minutes`, `lofi_enabled`, `stem_mode`.
- No Room tables.

## Manifest, permissions and notifications

- `PlaybackService`: exported, `foregroundServiceType="mediaPlayback"`, intent filter `androidx.media3.session.MediaSessionService`.
- `playback.SignedInMediaButtonReceiver` (extends Media3's `MediaButtonReceiver`) for `MEDIA_BUTTON`. `shouldStartForegroundService` waits up to 3 s for the saved session and returns false while signed out, because a service started in the foreground must start playing.
- Permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `POST_NOTIFICATIONS`.

## Tests

- `playback/NowPlayingStateTest.kt`: repeat cycle.
- `playback/SessionRestoreTest.kt`: missing songs, index shift, fallback, clamping.
- `playback/SleepTimerTest.kt`: start, clamp, extend, expiry, fade curve, speed-adjusted end of song.
- No tests for the service, controller, runner or UI.

## Known limitations and TODOs

- Custom commands only work for MPlay's own controllers.
- The saved queue stores song IDs only; deleted songs are dropped on restore.
- Sleep timer state is lost if the service dies.
- If reading the saved session takes more than 3 s, a headset or widget play press while the app isn't running is ignored.

## Change history

| Date | Commit | Change |
|---|---|---|
| 2026-10-02 | `7d8ad4f` | Playback service and controller integrated. |
| 2026-10-02 | `72daa1c` | Session persistence/restore, mini player, Now Playing. |
| 2026-10-02 | `3add93e` | Playback UI refactor, artwork handling. |
| 2026-10-02 | `69fb750` | Mini player scrubbing. |
| 2026-10-03 | `ea2857f` | Fixed expand progress in `PlayerSheet`. |
| 2026-10-03 | `201e1c9` | Sleep timer. |
| 2026-10-05 | `55e39e6` | Stem mode switching (`SET_STEM_MODE`). |
| 2026-10-05 | `0a56dcd` | Auto-sizing stem mode labels. |
| 2026-10-06 | `e13b2f7` | `musicPlaying` flag; `play`/`pause`/`currentPositionMs`/`isSeekable`; chip row became `FlowRow` with metronome and sing-along chips; `MetronomeMiniIndicator` in mini player. |
| 2026-10-06 | - | `PlaybackController` implements `MusicTimeline` for metronome sync; `restoresSession` parameter. See [metronome](metronome.md). |
| 2026-10-07 | - | Sign-in required: controllers rejected and resumption refused while signed out, service stops on sign-out, `SignedInMediaButtonReceiver`. See [accounts](accounts.md). |
