# UI / UX improvements

> Last updated: 2026-10-17  
> Status: backlog (not implemented). Describes opportunities against the current Compose UI, not shipped behaviour.

Audit of the signed-in app UI (Library, Playlists, Metronome, Profile, Now Playing, Equalizer, Trim, Separation, Sing-Along) as of this date. Prefer these items when planning UX work; update the matching [feature docs](README.md#features) and [CHANGELOG](CHANGELOG.md) when an item ships.

## Priority summary

| Priority | Area | Why |
|---|---|---|
| High | Library browsing | Large libraries lack scrubbers, play-all shortcuts, and clear search feedback |
| High | Now Playing feature row & Equalizer path | Crowded tiles; EQ jumps to Profile instead of staying in player context |
| Medium | Selection & playlists | Multi-select and playlist search scale poorly |
| Medium | Profile chrome | Double headers and fade-only transitions |
| Lower | Studio polish | Trim zoom, EQ curve, progress-pill vs FAB collision |

---

## 1. Library browsing and navigation

### Fast scroll and jump-to-current

**Current:** [`SongsTab.kt`](../app/src/main/java/com/autoomstudio/mp3studio/ui/library/SongsTab.kt) and [`ArtistsTab.kt`](../app/src/main/java/com/autoomstudio/mp3studio/ui/library/ArtistsTab.kt) use plain `LazyColumn` / grids with no alphabet thumb and no jump to the playing track. Album/playlist detail already has Play / Shuffle via `DetailHeader`; the Songs tab does not.

**Improve:**

- Draggable alphabet / scroll thumb for large libraries (hundreds of tracks).
- Compact Songs header: song count plus **Play all** / **Shuffle all** (same pattern as album and playlist detail).
- **Jump to playing song** when the current track is off-screen.

**Touches:** `SongsTab`, `ArtistsTab`, [`LibraryComponents.kt`](../app/src/main/java/com/autoomstudio/mp3studio/ui/library/LibraryComponents.kt) / `DetailHeader`. Feature doc: [library](features/library.md).

### Sort direction and tab-aware sort

**Current:** [`MPlayTopBar.kt`](../app/src/main/java/com/autoomstudio/mp3studio/ui/components/MPlayTopBar.kt) `SortMenu` offers five orders (`Title`, `Artist`, `Album`, `DateAdded`, `Duration`) with no ascending/descending toggle. The sort control stays on screen for Albums and Artists even though it mainly sorts songs.

**Improve:**

- Toggle ascending / descending on the selected order.
- Adapt sort options (or visibility) to the active `LibraryTab`, or show the active sort as a chip above Songs.

**Touches:** `MPlayTopBar`, library prefs / `SongSortOrder`. Feature doc: [library](features/library.md), [settings](features/settings-and-about.md).

### Search feedback across tabs

**Current:** Search lives in the top bar; matches are split across Songs / Albums / Artists ([`LibraryScreen.kt`](../app/src/main/java/com/autoomstudio/mp3studio/ui/library/LibraryScreen.kt)) with no result counts on tab labels.

**Improve:**

- Badge tab labels while searching (e.g. `Songs (12)`, `Albums (3)`), or a unified results layout with recent-search chips.

**Touches:** `LibraryScreen`, `MPlayTopBar`. Feature doc: [library](features/library.md).

---

## 2. Now Playing and queue

### Equalizer path vs other studio sheets

**Current:** Metronome and Sing-Along open modal sheets over Now Playing ([`NowPlayingScreen.kt`](../app/src/main/java/com/autoomstudio/mp3studio/ui/playback/NowPlayingScreen.kt)). Equalizer collapses the player, switches to Profile, and opens `ProfilePage.Equalizer` ([`MainScreen.kt`](../app/src/main/java/com/autoomstudio/mp3studio/ui/main/MainScreen.kt)). Back from Equalizer lands on Profile, not Now Playing.

**Improve:**

- Open Equalizer as a sheet (or overlay) from Now Playing, matching Metronome / Sing-Along; **or**
- Restore back stack so Back from Equalizer re-expands Now Playing.

**Touches:** `MainScreen`, `NowPlayingContent` / `PlayerFeatureTiles`, `EqualizerScreen`. Feature docs: [playback](features/playback.md), [equalizer](features/equalizer.md).

### Crowded five-tile feature row

**Current:** `PlayerFeatureTiles` packs Lofi, Equalizer, Metronome, Separator, and Sing-Along into one `76.dp` row; labels auto-size down to ~7–9sp and get hard to read when Separator shows progress text.

**Improve:**

- Two-row bento (e.g. primary studio actions + effect toggles), or a horizontally scrollable chip/card row with ~11–12sp labels.

**Touches:** `NowPlayingScreen.kt` (`PlayerFeatureTiles`, `PlayerFeatureTile`). Feature doc: [playback](features/playback.md).

### Ambient colour from artwork

**Current:** Expanded player uses flat `background` and a static primary purple glow on art ([`ExpandablePlayer.kt`](../app/src/main/java/com/autoomstudio/mp3studio/ui/playback/ExpandablePlayer.kt), artwork shadow in `NowPlayingContent`).

**Improve:**

- Extract dominant / vibrant colour from current artwork (`AlbumArtLoader` / Palette) and animate a soft gradient behind Now Playing and optionally the mini player.

**Touches:** `ExpandablePlayer`, `NowPlayingContent`, `MiniPlayerContent`, album-art loading. Feature docs: [playback](features/playback.md), [library](features/library.md#album-art).

### Tappable artist / album from Now Playing

**Current:** Title and artist on Now Playing are non-interactive text.

**Improve:**

- Tap artist → collapse player → `ArtistRoute`; optional “Go to album / artist” in the song menu.

**Touches:** `NowPlayingContent`, `SongMenuItems`, library navigation. Feature docs: [playback](features/playback.md), [library](features/library.md).

### Up Next peek and queue actions

**Current:** [`UpNextSheet.kt`](../app/src/main/java/com/autoomstudio/mp3studio/ui/playback/UpNextSheet.kt) peek is a fixed “Up Next” bar with no next-title or remaining count; expanded sheet has no save-as-playlist or clear-upcoming.

**Improve:**

- Peek: `Up Next · <next title>` or remaining track count.
- Expanded: **Save as playlist** / **Clear upcoming**.

**Touches:** `UpNextSheet`, `PlaybackController` / queue APIs as needed. Feature doc: [playback](features/playback.md#up-next).

---

## 3. Multi-select and playlists

### Select all / range selection

**Current:** Long-press enters selection ([`SongList.kt`](../app/src/main/java/com/autoomstudio/mp3studio/ui/library/SongList.kt)); [`SelectionTopBar`](../app/src/main/java/com/autoomstudio/mp3studio/ui/components/SelectionTopBar.kt) has no Select all / Deselect all.

**Improve:**

- Select all / deselect all on the selection bar; optional drag/range select.

**Touches:** `SelectionTopBar`, `SongSelection`. Feature doc: [library](features/library.md).

### Search in playlists and add-to-playlist

**Current:** Top-bar search is Library-only ([`MainScreen.kt`](../app/src/main/java/com/autoomstudio/mp3studio/ui/main/MainScreen.kt)). Playlist list and detail have no filter; [`AddToPlaylistSheet`](../app/src/main/java/com/autoomstudio/mp3studio/ui/playlist/AddToPlaylistSheet.kt) does not filter by name.

**Improve:**

- Filter on [`PlaylistsScreen`](../app/src/main/java/com/autoomstudio/mp3studio/ui/playlist/PlaylistsScreen.kt) / playlist detail and inside Add to playlist.

**Touches:** playlists UI. Feature doc: [playlists](features/playlists.md).

---

## 4. Profile and settings chrome

### Double headers and transition style

**Current:**

1. Profile drill-downs use `fadeThrough()` ([`MainScreen.kt`](../app/src/main/java/com/autoomstudio/mp3studio/ui/main/MainScreen.kt)); Library / Playlists use `sharedAxisX`.
2. `MPlayTopBar` stays visible on most Profile sub-pages while those pages also draw `SubPageHeader` / `EqualizerHeader` (back + title), stacking two header rows.

**Improve:**

- Hide `MPlayTopBar` for all `Destination.Profile` pages (sub-pages already own Back).
- Use `sharedAxisX` for `profilePage` transitions.

**Touches:** `MainScreen` top bar conditions, Profile `AnimatedContent`. Feature docs: [settings](features/settings-and-about.md), [architecture](architecture.md#navigation).

---

## 5. Studio tools

### Trim editor zoom and playhead seek

**Current:** [`TrimEditorScreen.kt`](../app/src/main/java/com/autoomstudio/mp3studio/ui/trim/TrimEditorScreen.kt) shows a full-track waveform (~180dp); fine edit relies on nudge buttons.

**Improve:**

- Pinch-to-zoom or 1× / 2× / 4× zoom on `WaveformView`.
- Tap inside the selection to seek the preview playhead.

**Touches:** trim UI / waveform. Feature doc: [clips-and-ringtones](features/clips-and-ringtones.md).

### Equalizer response curve and preset chrome

**Current:** [`EqualizerScreen.kt`](../app/src/main/java/com/autoomstudio/mp3studio/ui/equalizer/EqualizerScreen.kt) has vertical band sliders plus both a preset dropdown and a full preset grid (duplication); no frequency-response curve.

**Improve:**

- Smooth response curve (neon fill) behind the bands.
- One preset strip + Save / Manage; drop the duplicate grid so EQ fits without long scroll.

**Touches:** `EqualizerScreen`. Feature doc: [equalizer](features/equalizer.md).

### Separation progress pill vs FAB

**Current:** [`SeparationProgressPill`](../app/src/main/java/com/autoomstudio/mp3studio/ui/separation/SeparationProgressPill.kt) sits bottom-centre of the scaffold body; Playlists’ **New playlist** FAB is bottom-end and can collide on small widths / long titles.

**Improve:**

- Shared bottom inset, or dock the pill as a slim banner above the mini-player slot.

**Touches:** `MainScreen`, `SeparationProgressPill`, `PlaylistsScreen`. Feature docs: [vocal-separation](features/vocal-separation.md), [playlists](features/playlists.md).

---

## Key UI files (reference)

| Surface | Primary files |
|---|---|
| Shell / nav | `ui/main/MainScreen.kt`, `ui/components/MPlayTopBar.kt` |
| Theme | `ui/theme/Theme.kt`, `Color.kt`, `Type.kt`, `Motion.kt` |
| Library | `ui/library/LibraryScreen.kt`, `SongsTab.kt`, `SongList.kt`, album/artist tabs & detail |
| Playlists | `ui/playlist/PlaylistsScreen.kt`, `PlaylistDetailScreen.kt`, `AddToPlaylistSheet.kt` |
| Player | `ui/playback/ExpandablePlayer.kt`, `MiniPlayer.kt`, `NowPlayingScreen.kt`, `UpNextSheet.kt` |
| Profile | `ui/settings/ProfileScreen.kt`, `SettingsPages.kt` |
| EQ / trim / separation / sing-along | `ui/equalizer/`, `ui/trim/`, `ui/separation/`, `ui/singalong/` |

## Change history

| Date | Change |
|---|---|
| 2026-10-17 | Initial backlog from UI audit of the current Compose app. |
