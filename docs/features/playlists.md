# Playlists

> Status: Shipped | Added in: 1.0 | Last updated: 2026-10-06

## Summary

Users can create, rename and delete playlists, add songs from any song menu or a multi-selection, and reorder by dragging. Playlists store MediaStore song IDs, so a missing file shows as "unavailable" instead of being removed, and reappears if the file comes back.

## Key files

Paths are relative to `app/src/main/java/com/autoomstudio/mp3studio/`.

| File | Role |
|---|---|
| `data/playlist/PlaylistEntities.kt` | `PlaylistEntity`, `PlaylistSongEntity`. |
| `data/playlist/PlaylistDao.kt` | DAO with transactional `addSongs`, `removeSongs`, `reorder`. |
| `data/playlist/PlaylistRepository.kt` | Combines playlists and entries into `Flow<List<StoredPlaylist>>`; validates names; batches removals. |
| `data/playlist/PlaylistQueries.kt` | Pure `resolvePlaylist`, `applyVisibleOrder`, `moveItem`. |
| `data/model/Playlist.kt` | `StoredPlaylist`, `Playlist`. |
| `ui/playlist/PlaylistsViewModel.kt` | `PlaylistActions`, `PlaylistMessage` snackbars, resolution against the library. |
| `ui/playlist/PlaylistsScreen.kt` | List, New playlist FAB, rename/delete. |
| `ui/playlist/PlaylistDetailScreen.kt` | Header, unavailable count, reorderable list. |
| `ui/playlist/ReorderableSongList.kt` | Drag to reorder with accessibility actions. |
| `ui/playlist/AddToPlaylistSheet.kt` | Picker bottom sheet. |
| `ui/playlist/PlaylistNameDialog.kt` | Name dialog (also used for clips). |

## How it works

- **Resolving:** each stored ID is mapped through the duplicate `canonicalId` (so a hidden duplicate plays the kept copy), missing IDs are counted as unavailable, and a song reachable through two stored IDs appears once. Resolution waits for the library to load.
- **Adding:** `addSongs` de-duplicates, skips songs already present, appends after `MAX(position)` and returns the count. Messages: `Added`, `AlreadyIn`, `Created`.
- **Removing:** in batches of 500 to stay under SQLite's parameter limit. Deleting songs from the device removes them from every playlist.
- **Reordering:** local order updates while dragging; on drop, `reorder` keeps unavailable IDs in their slots and rewrites positions. Screen readers get Move up / Move down actions.
- **Deleting a playlist** cascades to its entries.
- Artwork is the first song's album art.

## Data and persistence

Room tables `playlists` and `playlist_songs`. See [database.md](../database.md).

## Manifest, permissions and notifications

None of its own.

## Tests

- `data/playlist/PlaylistQueriesTest.kt`: order, missing songs, kept duplicate copies, `moveItem`, `applyVisibleOrder`.
- No DAO or ViewModel tests.

## Known limitations and TODOs

- A song can appear only once per playlist.
- If a file is re-indexed with a new MediaStore ID, the entry becomes unavailable.
- No M3U import/export.

## Change history

| Date | Commit | Change |
|---|---|---|
| 2026-10-02 | `8dc73ee` | Playlists added (Room schema v1). |
| 2026-10-03 | `201e1c9` | Playlist entries resolve hidden duplicates to the kept copy. |
