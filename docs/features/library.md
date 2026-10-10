# Library

> Status: Shipped | Added in: 1.0 | Last updated: 2026-10-10

## Summary

The library reads every music file from Android's MediaStore and shows it in Songs, Albums and Artists tabs, with search, sort, album/artist pages, multi-select and deletion. It refreshes itself when files change, and pull-to-refresh asks Android to re-index the music folders.

## Key files

Paths are relative to `app/src/main/java/com/autoomstudio/mp3studio/`.

| File | Role |
|---|---|
| `data/library/MediaStoreSongSource.kt` | MediaStore audio query that builds `Song`s. |
| `data/library/AlbumArtLoader.kt` | Album-art bytes, with an embedded-picture fallback for songs in `Download/`. |
| `ui/components/AlbumArtFetcher.kt` | Coil fetcher that sends album-art URIs through `AlbumArtLoader`. |
| `data/library/SongRepository.kt` | Reactive `songs()` flow; reloads on MediaStore changes, manual refresh and automatic scans. |
| `data/library/AudioFolderScanner.kt` | Runs `MediaScannerConnection` over public audio folders. |
| `data/library/AudioFolderWatcher.kt` | `FileObserver`-based change flow for those folders. |
| `data/library/LibraryQueries.kt` | Pure filter, sort and grouping; `SongSortOrder`. |
| `data/library/LibraryPreferences.kt` | Sort order in DataStore. |
| `data/library/SongMapper.kt` | Text cleanup and bitrate estimate. |
| `data/library/SongDeleter.kt` | Deletes files through MediaStore. |
| `data/model/Song.kt`, `Album.kt`, `Artist.kt` | Models. |
| `ui/library/LibraryViewModel.kt` | `LibraryUiState` (`Loading`, `NoPermission`, `Empty`, `Content`) pipeline. |
| `ui/library/LibraryScreen.kt` | Tabs and album/artist back stack. |
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
- Filter: `IS_MUSIC != 0 OR NULL`, and duration at least 30 s (`MIN_DURATION_MS`) or NULL. Files in `Music/MP3 Studio Clips/`, `Music/MP3 Studio Recordings/` and the pre-rename `Music/MPlay Clips/` and `Music/MPlay Recordings/` skip the length check (`SavedFolders.likePatterns`, tested in `data/library/SavedFoldersTest.kt`).
- Sorted by title (case-insensitive). Track number is `TRACK % 1000`.
- Album art: `content://media/external/audio/albumart/<albumId>` (`AlbumArtLoader.ALBUM_ART_URI`). See [Album art](#album-art).
- Missing title falls back to the file name; missing artist/album become "Unknown Artist"/"Unknown Album". Bitrate is estimated from size and duration when MediaStore has none.

### Album art

MediaProvider refuses album-art thumbnails for files in `Download/` (`FileNotFoundException: No thumbnails in Downloads directories`). `AlbumArtLoader.load(uri)` (shared through `AppContainer.albumArtLoader`) tries the album-art URI first. If that fails, it picks a song with the same `ALBUM_ID` and reads its embedded picture with `MediaMetadataRetriever`. Album IDs with no art at all are remembered in memory, so they aren't looked up again. The loader is used by:

- Coil, through `ui/components/AlbumArtFetcher`, which `MPlayApp` registers as the singleton `ImageLoader`. `ArtworkImage` is unchanged.
- The media session's bitmap loader (see [playback](playback.md)).
- The widget (see [widget](widget.md)).

### Refresh

`SongRepository.songs()` merges a `ContentObserver` (300 ms debounce), manual `refresh()` calls, and automatic scans (folder watcher debounced 2 s, plus every 60 s). Scans cover Music, Download, Podcasts, Audiobooks (10+), Recordings (12+), time out after 15 s, and never overlap (`Mutex`).

### ViewModel

Combines songs (also feeding `stemRepository.onLibraryChanged` and the duplicate index), the search query (250 ms debounce), sort order and `hide_duplicates` on `Dispatchers.Default`. Hidden duplicates are removed, then filtering and sorting run. `allSongs` stays unfiltered so detail pages stay complete during a search. `onAppForeground()` rescans if the last scan is older than 2 s.

### Sorting

Title; Artist then title; Album then track then title; Date added (newest); Duration (longest). Albums with several artists show "Various Artists"; "Unknown Artist" sorts last.

### Selection and song menu

Long-press starts selection; taps toggle. Bulk actions: add to playlist, play next, add to queue, delete, separate, remove from playlist. The song menu adds Cut and save, Set as ringtone, separate vocals or download stems, song info and delete.

### Deletion

- Android 11+: `MediaStore.createDeleteRequest` system dialog. `RESULT_OK` counts every requested song as deleted.
- Android 8-10: in-app confirmation, request `WRITE_EXTERNAL_STORAGE` if needed, then `contentResolver.delete` per song.
- Afterwards the songs are removed from the queue and from all playlists, and a snackbar reports the result.

## Data and persistence

- DataStore `library_prefs`: `sort_order` (default `Title`).
- DataStore `app_settings`: `hide_duplicates` (default `true`).
- No Room tables.

## Manifest, permissions and notifications

`READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE` (max 32), `WRITE_EXTERNAL_STORAGE` (max 29), `requestLegacyExternalStorage="true"`.

## Tests

- `data/library/LibraryQueriesTest.kt`: filtering, every sort order, grouping, Various Artists, Unknown Artist last.
- `data/library/SongMapperTest.kt`: bitrate and fallbacks.
- `data/library/AlbumArtLoaderTest.kt`: `albumIdOf` parsing of album-art URIs.
- `ui/library/SongSelectionTest.kt`: selection state.
- `ui/common/DurationFormatTest.kt`: duration formatting.

## Known limitations and TODOs

- Files under 30 s are hidden, except clips and recordings.
- The folder watcher is not recursive; the 60 s scan is the fallback.
- On Android 11+ there is no per-file check after the delete dialog.
- Artists are grouped by exact string, so "A feat. B" is its own artist.
- Songs in `Download/` with no embedded cover still show the placeholder. The album-art fallback uses the first song of the album, so an album split between folders may show another track's cover.

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
