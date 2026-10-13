# Playback

> Status: Shipped | Added in: 1.0 | Last updated: 2026-10-13

## Summary

Local songs play from a background Media3 session service, with the system media notification, lock-screen and headset controls, audio focus, and pause on headphone unplug. The UI is a single card that drags between a mini player and the full Now Playing screen, with seek bar, shuffle, repeat, an Up Next queue sheet (swipe up from Now Playing), and a sleep timer that fades out over the last 30 seconds. The queue, position, shuffle and repeat survive the app being killed.

## Key files

Paths are relative to `app/src/main/java/com/autoomstudio/mp3studio/`.

| File | Role |
|---|---|
| `playback/PlaybackService.kt` | `MediaSessionService`: owns `ExoPlayer` + `MediaSession`, custom commands, session saving, stem switching, lofi, resumption. |
| `playback/PlaybackController.kt` | UI-side `MediaController` wrapper. `state: StateFlow<NowPlayingState?>`, `queue: StateFlow<PlaybackQueue>`, `positionMs()`, queue/transport methods (`playQueueIndex`, `moveQueueItem`, `removeQueueIndex`, play next / add to queue). Main thread only. Also the metronome's `MusicTimeline`: `songId`, `playing`, `seeks` (position discontinuities and playback-parameter changes), `snapshot()` (position, `System.nanoTime()`, speed). `restoresSession = false` skips restoring the saved queue on connect. |
| `playback/PlaybackQueue.kt` | `QueueEntry`, `PlaybackQueue`, `playbackQueueOf()`, `clampQueueMove()` for the Up Next UI. |
| `playback/PlaybackCommands.kt` | Custom `SessionCommand`s, argument keys, session extras encode/decode. |
| `playback/NowPlayingState.kt` | `NowPlayingState`, `RepeatMode`, `nextRepeatMode()` (Off, All, One). |
| `playback/PlaybackSessionStore.kt` | DataStore for the saved queue (`SavedSession`) and modes (`PlaybackModes`). |
| `playback/SessionRestore.kt` | Pure `restoreQueue()` that rebuilds a saved queue against the current library. |
| `playback/SleepTimer.kt` | `SleepTimerStatus` and pure timer math. |
| `playback/SleepTimerRunner.kt` | `Player.Listener` in the service that ticks, fades and pauses. |
| `playback/AlbumArtBitmapLoader.kt` | Media3 `BitmapLoader` for the session: album-art URIs through `AlbumArtLoader`, everything else to `DataSourceBitmapLoader`. |
| `playback/SongMediaItems.kt` | `Song.toMediaItem()`, `originalUri`, `withPlaybackUri()` (used for stems). |
| `ui/playback/PlaybackViewModel.kt` | Implements `PlayerActions`; owns a `PlaybackController`; exposes `queue`. |
| `ui/playback/PlayerActions.kt` | Interface for transport, sleep timer, `setLofi`, `setStemMode`, queue jump/move/remove. |
| `ui/playback/ExpandablePlayer.kt` | One card that morphs from mini to full player by `expandProgress`. |
| `ui/playback/PlayerSheet.kt` | `AnchoredDraggableState<PlayerSheetValue>` anchors, fling and drag. |
| `ui/playback/UpNextSheet.kt` | Nested peek/expanded sheet on Now Playing: current + upcoming, tap to jump, drag reorder, remove. |
| `ui/playback/MiniPlayer.kt` | Title/artist, play/pause, next, scrub strip, metronome indicator. |
| `ui/playback/NowPlayingScreen.kt` | Artwork, seek bar (white thumb), outlined circular transport, `StemModeSelector`, five feature tiles (lofi, equalizer, metronome, separator, sing-along), Up Next sheet, other sheets. |
| `ui/playback/PlayPauseIcon.kt` | Lottie play/pause morph with icon fallback. |
| `playback/ControllerAccess.kt` | `ControllerAccess` (Full, Transport, Rejected), `controllerAccess()` and `transportPlayerCommands()` for controllers of the exported service. |
| `ui/playback/SwipeToSkip.kt` | `swipeToSkip` modifier, `SwipeSkipState` and `swipeDecision()`: swipe the album art or mini player to change song. |
| `ui/playback/SleepTimerSheet.kt` | Presets, end of song, 1-180 min slider, remaining-time chip. |

## How it works

### Service

- `ExoPlayer.Builder(this, EffectRenderersFactory(...))` with music audio attributes, `handleAudioFocus = true`, `setHandleAudioBecomingNoisy(true)`, `WAKE_MODE_LOCAL`. The sink runs Lofi then the [Equalizer](equalizer.md) processor before Media3 speed/pitch.
- Listeners: `SkipUnplayableListener` (a failing stem falls back to the original file; otherwise the broken item is removed), `SessionSaver`, `WidgetStatePublisher`, the `musicPlaying` mirror, `SleepTimerRunner`.
- On create it restores shuffle/repeat, applies the saved lofi flag, applies equalizer state from `EqualizerRepository` (and collects updates), loads `stemMode`, and watches `stemRepository.stemSets` to repoint stem URIs.
- The session activity `PendingIntent` opens `MainActivity` with `EXTRA_OPEN_NOW_PLAYING`. Notifications use Media3's default provider.
- The session's bitmap loader is `CacheBitmapLoader(AlbumArtBitmapLoader(...))`. Album-art URIs go through `AlbumArtLoader`, so songs in `Download/` (where MediaProvider refuses thumbnails) get their embedded cover in the notification. Other URIs go to `DataSourceBitmapLoader`. When a song has no art, `AlbumArtBitmapLoader` falls back to `AppLogoArtwork` (the app logo) so the notification still has a large icon. See [library](library.md#album-art).
- `SessionCallback`:
  - `onConnectAsync` waits for `AuthRepository.awaitReady()` and rejects every controller (app, notification, system UI, Bluetooth) while signed out (PRD AU4). Signed in, `controllerAccess()` decides: the app's own package (app, widget) gets `Full` (default commands plus `PlaybackCommands.all`); a trusted controller (`isTrusted`, the media notification controller, Android Auto companion or Automotive) gets `Transport` (`transportPlayerCommands()`: default player commands minus `NON_TRANSPORT_COMMANDS`, so no queue, volume, speed or track changes); any other app is rejected and logged at debug level.
  - `onCustomCommand` handles the commands below; anything else returns `ERROR_NOT_SUPPORTED`.
  - `onPlaybackResumption` rebuilds the saved queue for play requests from the widget, headset or system UI. It throws while signed out.
  - `onAddMediaItems` refuses items from any other package (failed future) and maps the app's own items through `resolve()` so the current `StemMode` applies.
- After a sign-in, a collector on `AuthRepository.state` waits for `SignedOut`, then `stopForSignOut()` saves the queue, cancels the sleep timer, stops and clears the player and calls `stopSelf()`. The saved queue (DataStore `playback_session`) is kept, so the next sign-in can resume it.
- `onTaskRemoved` (app swiped away from recents) always saves the queue, cancels the sleep timer, calls `TaskRemoval.onTaskRemoved()` (stops the metronome, sing-along and separation, see [architecture](../architecture.md#removal-from-recents)) and calls Media3's `pauseAllPlayersAndStopSelf()`, which pauses, dismisses the notification and stops the service, so music never keeps playing without the app. A plain `stopSelf()` isn't enough: Media3 re-posts the notification for the loaded queue and restarts the service. The saved queue lets the next launch, the widget or a headset press resume it. `onDestroy` clears `musicPlaying`, saves, publishes a paused widget state and releases.

### Custom commands and extras

| Command (`com.autoomstudio.mp3studio.command.*`) | Args |
|---|---|
| `SET_SLEEP_TIMER` | `minutes` |
| `SET_SLEEP_TIMER_END_OF_SONG` | |
| `EXTEND_SLEEP_TIMER` | |
| `CANCEL_SLEEP_TIMER` | |
| `SET_LOFI` | `enabled` |
| `SET_STEM_MODE` | `stem_mode` |

State flows back through session extras: `sleep_end_elapsed`, `sleep_end_of_song`, `lofi`, `stem_mode`, `equalizer_active`.

### Controller

- Builds `MediaController` asynchronously; `withController {}` queues calls until connected.
- `positionMs()` polls every 500 ms.
- On connect with an empty queue, `restoreSession()` loads the saved queue paused.
- Added in `e13b2f7` for sing-along: `play()`, `pause()`, `currentPositionMs()`, `isSeekable()`.
- `previousTrack()` uses `seekToPreviousMediaItem()`, so it always changes song; `previous()` (the Previous button) uses `seekToPrevious()`, which restarts the current song after 3 s.
- `sync()` also publishes `PlaybackQueue` from the Media3 timeline (title, artist, artwork, duration from `MediaMetadata`, with the current item's duration from the player when known). `Song.toMediaItem()` stores `durationMs` on metadata for Up Next rows.

### Up Next

- On the expanded Now Playing screen, a nested bottom sheet (`UpNextSheet`) peeks with an "Up Next" handle. Swipe up or tap the handle to expand; swipe down or Back returns to the peek, then Back collapses Now Playing.
- The list is `PlaybackQueue.upNext` (current index through the end of the timeline). Tap jumps with `playQueueIndex`; the drag handle calls `moveMediaItem`; the close control calls `removeMediaItem`.
- Rows are keyed by absolute timeline index so the same song can appear more than once.
- The shared morphing artwork in `ExpandablePlayer` is drawn above Now Playing content, so its alpha is multiplied by `(1 - UpNextSheetState.expandProgress)` and swipe-to-skip on the art is disabled while the sheet is mostly open, so the cover does not float over the queue.

### Swipe to skip

- Swipe the Now Playing album art (the shared artwork in `ExpandablePlayer`, while expanded) or the mini player row left for the next song and right for the previous one (`PlayerActions.next()` / `previousTrack()`).
- `swipeToSkip` uses `detectHorizontalDragGestures`, so vertical drags still expand or collapse the player, taps still open it, and the mini player's scrub strip keeps its own drags. One `SwipeSkipState` in `ExpandablePlayer` moves the artwork and the mini player's title and artist together.
- A swipe counts past 30% of the element's width or with a fling faster than 1000 dp/s (`swipeDecision()`); the content slides out, the song changes with a haptic tick, and the content slides back in from the other side. Otherwise it springs back. With no next or previous song (`hasNext` / `hasPrevious`) the drag only moves a little against resistance.
- TalkBack gets "Next song" and "Previous song" custom actions on both.

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
- `playback/PlaybackQueueTest.kt`: `playbackQueueOf` clamping, `upNext` slice, `clampQueueMove`.
- `ui/playback/SwipeToSkipTest.kt`: swipe threshold, fling, blocked directions.
- `playback/ControllerAccessTest.kt`: access per controller kind, transport keeps playback controls and drops queue changes.
- No tests for the service, controller, runner or the rest of the UI.

## Known limitations and TODOs

- Custom commands only work for MPlay's own controllers.
- The saved queue stores song IDs only; deleted songs are dropped on restore.
- Sleep timer state is lost if the service dies.
- If reading the saved session takes more than 3 s, a headset or widget play press while the app isn't running is ignored.
- Add to queue still appends at the end of the Media3 timeline (often the rest of the library list); Up Next shows those songs at the bottom of the upcoming list.

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
| 2026-10-08 | - | Swiping the app away from recents now always stops playback and the service (`onTaskRemoved`). |
| 2026-10-08 | - | `onTaskRemoved` also reports the removal to `TaskRemoval`, which stops the other background features. |
| 2026-10-10 | - | `AlbumArtBitmapLoader` on the session, so the notification shows the cover of songs in `Download/`. |
| 2026-10-10 | - | Swipe the album art or mini player left/right for the next/previous song (`SwipeToSkip.kt`, `PlayerActions.previousTrack()`). |
| 2026-10-10 | - | Controller policy for the exported service: other apps refused, trusted system controllers transport-only, `onAddMediaItems` limited to the app (`ControllerAccess.kt`). |
| 2026-10-12 | - | Equalizer processor after Lofi (`EffectRenderersFactory`); session extra `equalizer_active`; Now Playing Equalizer chip. See [equalizer](equalizer.md). |
| 2026-10-13 | - | Notification artwork falls back to the app logo via `AppLogoArtwork` when `AlbumArtLoader` has no art. |
| 2026-10-13 | - | Now Playing restyle: white seek thumb, outlined circular shuffle/prev/next/repeat, five equal feature tiles instead of the chip `FlowRow`. |
| 2026-10-13 | - | Up Next nested sheet on Now Playing (`UpNextSheet`, `PlaybackQueue`): swipe up for current + upcoming, tap to jump, drag reorder, remove. |
| 2026-10-13 | - | Shared Now Playing artwork fades with `UpNextSheetState.expandProgress` so it no longer overlays the Up Next list. |
