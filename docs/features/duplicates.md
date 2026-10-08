# Duplicate detection

> Status: Shipped | Added in: 2.0 | Last updated: 2026-10-06

## Summary

MPlay finds copies of the same song and, with "Hide duplicates" on (the default), shows only the best copy. **Settings > Review duplicates** lists each group, lets the user choose which copy to keep, show an extra copy again, or delete hidden copies. Playlist entries that point at a hidden copy play the kept one.

## Key files

Paths are relative to `app/src/main/java/com/autoomstudio/mp3studio/`.

| File | Role |
|---|---|
| `data/duplicates/DuplicateDetector.kt` | Matching algorithm, `DuplicateGroup`, `DuplicateIndex`. |
| `data/duplicates/FileFingerprinter.kt` | SHA-256 over size + first and last 64 KiB. Also used by stems. |
| `data/duplicates/DuplicateRepository.kt` | `index(songs): Flow<DuplicateIndex>`; computes missing fingerprints in the background. |
| `data/duplicates/DuplicateDao.kt` | Fingerprints and user overrides. |
| `data/duplicates/DuplicateEntities.kt` | `SongFingerprintEntity`, `DuplicateOverrideEntity`, `DuplicateOverrideKind`. |
| `ui/duplicates/ReviewDuplicatesScreen.kt` | Review screen and `DuplicateActions`. |

## How it works

### Matching (union-find)

1. **Metadata:** title and artist are normalised (lowercase, leading track numbers removed, punctuation to spaces). Words like "live" or "remix" are kept, so versions never match. Within a title+artist key, songs whose durations are within 2 s (`DURATION_TOLERANCE_MS`) are joined.
2. **Content:** songs with the same fingerprint are joined even if tags differ.
3. Groups of two or more are sorted by quality.

### Kept copy

Quality order: lossless (flac, wav, alac, aiff) first, then higher bitrate, then earliest added, then lower ID. A user `Keep` override wins; `Restore` keeps a copy visible. `DuplicateIndex` exposes `hiddenIds`, `visible(songs)`, `canonicalId(id)` and `groupOf(id)`.

### Background fingerprinting

Runs on `applicationScope` under a `Mutex`: prunes fingerprints for removed songs, only fingerprints songs whose byte size matches another song, skips valid cache entries, and upserts in batches of 50 so groups fill in progressively.

### Review screen

One card per group showing each copy's album, status (Kept, Restored, Hidden), quality and duration. Radio button keeps a copy; Restore / Hide again toggles visibility; Delete extras uses the library deletion flow.

## Data and persistence

- Room tables `song_fingerprints` and `duplicate_overrides` (see [database.md](../database.md)).
- DataStore `app_settings`: `hide_duplicates` (default `true`).

## Manifest, permissions and notifications

None of its own.

## Tests

- `data/duplicates/DuplicateDetectorTest.kt`: normalisation, duration tolerance, artist/version rules, fingerprint matching, quality order, overrides, `canonicalId`.
- No fingerprinter or repository tests.

## Known limitations and TODOs

- Duration joining is pairwise, so a chain A-B-C can group A and C even if they are more than 2 s apart.
- Content matching only catches byte-identical copies; the partial hash could in theory collide.
- `duplicate_overrides` rows are not cleaned up when songs disappear.
- A copy with no artist tag won't match a tagged copy by metadata.

## Change history

| Date | Commit | Change |
|---|---|---|
| 2026-10-03 | `201e1c9` | Duplicate detection, review screen, Room schema v2. |
