# Home screen widget

> Status: Shipped | Added in: 1.0 | Last updated: 2026-10-07

## Summary

A resizable Glance widget for the home and lock screen showing the current song (and album art when wide), with previous, play/pause and next. Tapping it opens Now Playing. Play/pause can resume the last session even when the app isn't running, and the widget keeps its last state after the process dies.

## Key files

Paths are relative to `app/src/main/java/com/autoomstudio/mp3studio/`.

| File | Role |
|---|---|
| `widget/MPlayWidget.kt` | `GlanceAppWidget`, responsive sizes SMALL 110x48, MEDIUM 180x48, WIDE 250x48. |
| `widget/MPlayWidgetReceiver.kt` | `GlanceAppWidgetReceiver`. |
| `widget/SkipActionCallback.kt` | Next/previous through a short-lived `MediaController`. |
| `widget/WidgetState.kt` | `WidgetState`, `Idle`, `widgetStateOf()`. |
| `widget/WidgetStatePublisher.kt` | `Player.Listener` in the service that writes state and artwork, then calls `updateAll()`. |
| `widget/WidgetStateStore.kt` | DataStore `widget_state` + artwork file. |
| `app/src/main/res/xml/mplay_widget_info.xml` | Widget provider metadata. |

## How it works

- **Publishing:** on item transition, play state, timeline or metadata change, the publisher snapshots state on the player thread, skips unchanged states, then (under a `Mutex` on `applicationScope`) saves state, decodes artwork to 256 px if it changed, and calls `MPlayWidget().updateAll()`. `onDestroy` publishes a paused state.
- **Rendering:** artwork at WIDE, title/artist at MEDIUM and above, buttons only at SMALL. Uses the app's Neon colour schemes.
- **Signed out (PRD AU4):** `provideGlance` waits for `AuthRepository.awaitReady()` and collects `AuthRepository.state`. Unless signed in, `SignedOutContent` shows "Sign in to MP3 Studio" (`widget_signed_out`) with no controls, and a tap opens `MainActivity` (the sign-in screen). `di/AuthEffects` calls `MPlayWidget().updateAll()` on every sign-in and sign-out so the widget switches even without a running session.
- **Actions:**
  - Whole widget: opens `MainActivity` with `EXTRA_OPEN_NOW_PLAYING` (unless idle).
  - Play/pause: broadcasts `KEYCODE_MEDIA_PLAY_PAUSE` to `playback.SignedInMediaButtonReceiver`, which can start the service and trigger `onPlaybackResumption` (only when signed in).
  - Previous/next: `SkipActionCallback` connects a controller, skips if anything is queued, and releases it.

## Data and persistence

- DataStore `widget_state`: `song_id`, `title`, `artist`, `is_playing`, `artwork_version`.
- File `filesDir/widget/artwork.png` (written atomically via `artwork.tmp`).

## Manifest, permissions and notifications

- `MPlayWidgetReceiver` (exported, `APPWIDGET_UPDATE`, `@xml/mplay_widget_info`).
- Widget info: min 110x48 dp, target 4x1 cells, resizable, `updatePeriodMillis="0"`, `home_screen|keyguard`.

## Tests

- `widget/WidgetStateTest.kt`: idle state, mapping, unknown-tag fallbacks, non-numeric media IDs.

## Known limitations and TODOs

- No periodic updates; refreshes only while `PlaybackService` runs.
- No progress display. Artwork only at WIDE.
- Skip does nothing when the queue is empty.

## Change history

| Date | Commit | Change |
|---|---|---|
| 2026-10-03 | `73cdb66` | Widget added with notification permission support. |
| 2026-10-07 | - | Signed-out view without controls; play/pause goes through `SignedInMediaButtonReceiver`; refreshed on sign-in and sign-out. See [accounts](accounts.md). |
