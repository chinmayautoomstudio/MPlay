# Library

> Status: Shipped | Added in: 1.0 | Last updated: 2026-10-14

## Summary

The library reads every music file from Android's MediaStore and shows it in Songs, Albums and Artists tabs (swipe or tap to switch), with search, sort, album/artist pages, multi-select and deletion. It refreshes itself when files change, and pull-to-refresh asks Android to re-index the music folders.

## Key files

Paths are relative to `app/src/main/java/com/autoomstudio/mp3studio/`.

| File | Role |
|---|---|
| `data/library/MediaStoreSongSource.kt` | MediaStore audio query that builds `Song`s. |
| `data/library/LibraryAudioSelection.kt` | Type-eligibility SQL (music, `IS_RECORDING`, `Recordings/` path). |
| `data/library/AlbumArtLoader.kt` | Album-art bytes, with an embedded-picture fallback for songs in `Download/`. |
| `data/library/AppLogoArtwork.kt` | Decodes `ic_mp3studio_logo` to square JPEG/bitmap for placeholders and embedding. |
| `data/library/CoverArtEmbedder.kt` | Writes the logo as MP4 `covr` (jaudiotagger) into clip/sing-along M4As. |
| `ui/components/AlbumArtFetcher.kt` | Coil fetcher that sends album-art URIs through `AlbumArtLoader`. |
| `ui/components/ArtworkImage.kt` | Album art with app-logo underlay when art is missing or fails to load. |
| `data/library/SongRepository.kt` | Reactive `songs()` flow; reloads on MediaStore changes, manual refresh and automatic scans. |
| `data/library/AudioFolderScanner.kt` | Runs `MediaScannerConnection` over public audio folders. |
| `data/library/AudioFolderWatcher.kt` | `FileObserver`-based change flow for those folders. |
| `data/library/LibraryQueries.kt` | Pure filter, sort and grouping; `SongSortOrder`. |
| `data/library/LibraryPreferences.kt` | Sort order and excluded folders in DataStore. |
| `data/library/FolderPaths.kt` | Normalize MediaStore folder paths for exclusion. |
| `data/library/AudioFolder.kt` | Folder path + song count for the exclude-folders UI. |
| `ui/library/ExcludeFoldersViewModel.kt` | Loads folders and toggles exclusions. |
| `ui/settings/ExcludeFoldersScreen.kt` | Profile → Library → Exclude folders. |
| `data/library/SongMapper.kt` | Text cleanup and bitrate estimate. |
| `data/library/SongDeleter.kt` | Deletes files through MediaStore. |
| `data/model/Song.kt`, `Album.kt`, `Artist.kt` | Models. |
| `ui/library/LibraryViewModel.kt` | `LibraryUiState` (`Loading`, `NoPermission`, `Empty`, `Content`) pipeline. |
| `ui/library/LibraryScreen.kt` | Songs/Albums/Artists tabs (`HorizontalPager` + `PrimaryTabRow`) and album/artist back stack. |
| `ui/library/SongsTab.kt`, `AlbumsTab.kt`, `ArtistsTab.kt` | Tab contents. |
| `ui/library/AlbumDetailScreen.kt`, `ArtistDetailScreen.kt` | Detail pages with Play all / Shuffle. |
| `ui/library/SongList.kt` | Song rows, song menu, `LocalSeparatedSongIds`. |
| `ui/library/SongSelection.kt` | Multi-select state. |
| `ui/library/DeleteSongsHost.kt` | Deletion flow and `DeleteResult`. |
| `ui/library/SongInfoDialog.kt` | Song info. |
| `ui/permission/AudioPermission.kt` | Audio permission state and rationale screen. |
| `ui/common/LegacyStorage.kt` | Requests `WRITE_EXTERNAL_STORAGE` before writes on Android 9 and below. |

## How it works

### Query

- Collection: `getContentUri(VOLUME_EXTERNAL)` on Android 10+, else `EXTERNAL_CONTENT_URI`. `BITRATE` only on Android 11+.
- Filter: music (`IS_MUSIC != 0 OR NULL`), voice recordings on Android 12+ (`IS_RECORDING != 0`), or anything under the public `Recordings/` tree (so Samsung call folders like `Recordings/Call/` count). Duration at least 30 s (`MIN_DURATION_MS`) or NULL. Files in `Music/MP3 Studio Clips/`, `Music/MP3 Studio Recordings/` and the pre-rename `Music/MPlay Clips/` and `Music/MPlay Recordings/` skip the length check (`SavedFolders.likePatterns`, tested in `data/library/SavedFoldersTest.kt`). Type clause built by `LibraryAudioSelection` (tested in `LibraryAudioSelectionTest.kt`).
- Excluded folders: relative paths from DataStore `excluded_folders` become `AND NOT (path LIKE ? OR …)` via `SavedFolders.likePatterns` (scoped `RELATIVE_PATH` on Android 10+, else absolute `DATA`). Applied in `querySongs` so library UI and playback restore stay consistent. Profile → Library → Exclude folders lists distinct folders from `queryAudioFolders()` (same eligibility rules, without exclusions) with per-folder switches. On Android 10+, if `RELATIVE_PATH` is missing, the folder key falls back to the parent of `DATA`.
- Sorted by title (case-insensitive). Track number is `TRACK % 1000`.
- Album art: `content://media/external/audio/albumart/<albumId>` (`AlbumArtLoader.ALBUM_ART_URI`). See [Album art](#album-art).
- Missing title falls back to the file name; missing artist/album become "Unknown Artist"/"Unknown Album". Bitrate is estimated from size and duration when MediaStore has none.

### Album art

MediaProvider refuses album-art thumbnails for files in `Download/` (`FileNotFoundException: No thumbnails in Downloads directories`). `AlbumArtLoader.load(uri)` (shared through `AppContainer.albumArtLoader`) tries the album-art URI first. If that fails, it picks a song with the same `ALBUM_ID` and reads its embedded picture with `MediaMetadataRetriever`. Album IDs with no art at all are remembered in memory, so they aren't looked up again. The loader is used by:

- Coil, through `ui/components/AlbumArtFetcher`, which `MPlayApp` registers as the singleton `ImageLoader`. `ArtworkImage` shows `R.drawable.ic_mp3studio_logo` under the loaded art (or alone when the URI is null / the load fails).
- The media session's bitmap loader (see [playback](playback.md)), which falls back to the same logo via `AppLogoArtwork`.
- The widget (see [widget](widget.md)).

`AppLogoArtwork` decodes that drawable to a square JPEG/bitmap (~512 px). `CoverArtEmbedder` (jaudiotagger) writes it as MP4 `covr` when saving [clips](clips-and-ringtones.md) and [sing-along](sing-along.md) recordings so the library and other apps treat the logo as real cover art.

### Refresh

`SongRepository.songs()` merges a `ContentObserver` (300 ms debounce), manual `refresh()` calls, and automatic scans (folder watcher debounced 2 s, plus every 60 s), then combines with `LibraryPreferences.excludedFolders` so toggles reload the list without a media rescan. Scans cover Music, Download, Podcasts, Audiobooks (10+), Recordings (12+), time out after 15 s, and never overlap (`Mutex`).

### ViewModel

Combines songs (also feeding `stemRepository.onLibraryChanged` and the duplicate index), the search query (250 ms debounce), sort order and `hide_duplicates` on `Dispatchers.Default`. Hidden duplicates are removed, then filtering and sorting run. `allSongs` stays unfiltered so detail pages stay complete during a search. `onAppForeground()` rescans if the last scan is older than 2 s.

### Tabs

`LibraryScreen` shows Songs, Albums and Artists in a `HorizontalPager` under a `PrimaryTabRow`. Swipe left/right or tap a tab to switch; the selected tab index is `rememberSaveable`. Album and artist detail pages stack on top of the tabs and are not part of the pager.

### Sorting

Title; Artist then title; Album then track then title; Date added (newest); Duration (longest). Albums with several artists show "Various Artists"; "Unknown Artist" sorts last.

### Selection and song menu

Long-press starts selection; taps toggle. Bulk actions: add to playlist, play next, add to queue, delete, separate, remove from playlist. The song menu adds Cut and save, Set as ringtone, separate vocals or download stems, song info and delete.

### Deletion

- Android 11+: `MediaStore.createDeleteRequest` system dialog. `RESULT_OK` counts every requested song as deleted.
- Android 8-10: in-app confirmation, request `WRITE_EXTERNAL_STORAGE` if needed, then `contentResolver.delete` per song.
- Afterwards the songs are removed from the queue and from all playlists, and a snackbar reports the result.

## Data and persistence

- DataStore `library_prefs`: `sort_order` (default `Title`); `excluded_folders` (string set of relative paths with trailing slash, default empty).
- DataStore `app_settings`: `hide_duplicates` (default `true`).
- No Room tables.

## Manifest, permissions and notifications

`READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE` (max 32), `WRITE_EXTERNAL_STORAGE` (max 29), `requestLegacyExternalStorage="true"`.

## Tests

- `data/library/LibraryQueriesTest.kt`: filtering, every sort order, grouping, Various Artists, Unknown Artist last.
- `data/library/FolderPathsTest.kt`: path normalize, absolute→relative, exclusion LIKE patterns.
- `data/library/LibraryAudioSelectionTest.kt`: `Recordings/` LIKE patterns; music / `IS_RECORDING` eligibility SQL.
- `data/library/SongMapperTest.kt`: bitrate and fallbacks.
- `data/library/AlbumArtLoaderTest.kt`: `albumIdOf` parsing of album-art URIs.
- `androidTest` `data/library/CoverArtEmbedderTest.kt`: embeds logo JPEG into a silent M4A; jaudiotagger and `MediaMetadataRetriever` both see cover art.
- `ui/library/SongSelectionTest.kt`: selection state.
- `ui/common/DurationFormatTest.kt`: duration formatting.

## Known limitations and TODOs

- Files under 30 s are hidden, except clips and recordings.
- The folder watcher is not recursive; the 60 s scan is the fallback.
- On Android 11+ there is no per-file check after the delete dialog.
- Artists are grouped by exact string, so "A feat. B" is its own artist.
- Songs in `Download/` with no embedded cover show the app logo placeholder. The album-art fallback uses the first song of the album, so an album split between folders may show another track's cover.

## Change history

| Date | Commit | Change |
|---|---|---|
| 2026-10-02 | `5685cb3`, `ee66e5a` | Initial library and UI. |
| 2026-10-02 | `449ed10` | Song repository refresh, folder scanner and watcher. |
| 2026-10-02 | `eb0fb2f` | Library management: sorting, search, album/artist pages. |
| 2026-10-03 | `da53339` | Song deletion and selection UI. |
| 2026-10-06 | `e13b2f7` | `Music/MPlay Recordings/` exempt from the 30 s minimum; Metronome tab added to `MainScreen`; `SingAlongOverlay` hosted. |
| 2026-10-07 | - | `data/library/SavedFolders.kt` builds the minimum-length exemption for the new MP3 Studio folders and the legacy MPlay ones. |
| 2026-10-10 | - | `AlbumArtLoader` and `AlbumArtFetcher`: embedded-picture fallback for songs in `Download/`. |
| 2026-10-13 | - | Missing album art shows `ic_mp3studio_logo` in `ArtworkImage`; `AppLogoArtwork` / `CoverArtEmbedder` for clip and sing-along cover embedding. |
| 2026-10-14 | - | Exclude folders: `excluded_folders` in `library_prefs`, SQL filter in `MediaStoreSongSource`, Profile → Library → Exclude folders UI. |
| 2026-10-14 | - | Swipe between Songs / Albums / Artists via `HorizontalPager` in `LibraryTabs`. |
| 2026-10-14 | - | Library eligibility includes `IS_RECORDING` and `Recordings/` so call/voice folders (e.g. `Recordings/Call/`) appear in Exclude folders. |
