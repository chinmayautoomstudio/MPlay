# Database

> Last updated: 2026-10-06, schema version 5

## Overview

- Class: [`data/playlist/MPlayDatabase.kt`](../app/src/main/java/com/autoomstudio/mplay/data/playlist/MPlayDatabase.kt)
- File: `mplay.db`, built with `Room.databaseBuilder(...)`. No destructive fallback and no manual migrations.
- `exportSchema = true`. Schemas: [`app/schemas/com.autoomstudio.mplay.data.playlist.MPlayDatabase/`](../app/schemas/com.autoomstudio.mplay.data.playlist.MPlayDatabase/) `1.json` to `5.json`.
- DAOs: `playlistDao()`, `duplicateDao()`, `stemDao()`, `tempoDao()`.

Library songs are not stored here. They are read live from MediaStore, and every table refers to songs by MediaStore `_ID` without a foreign key.

## Version history

| Version | Commit | Migration | Tables added |
|---|---|---|---|
| 1 | `8dc73ee` (2026-10-02) | | `playlists`, `playlist_songs` |
| 2 | `201e1c9` (2026-10-03) | `AutoMigration(1, 2)` | `song_fingerprints`, `duplicate_overrides` |
| 3 | `55e39e6` (2026-10-05) | `AutoMigration(2, 3)` | `stem_sets`, `separation_jobs` |
| 4 | `e13b2f7` (2026-10-06) | `AutoMigration(3, 4)` | `song_tempos` |
| 5 | (2026-10-06) | `AutoMigration(4, 5)` | none; `song_tempos` gains `beatsPerBar`, `beatUnit`, `meterConfidence`, `meterBpm` |

Every migration so far only added tables or nullable columns; no existing column has been changed. The 4-to-5 migration is four `ALTER TABLE song_tempos ADD COLUMN ... DEFAULT NULL`, so existing rows are kept.

## Tables

All columns are NOT NULL unless marked nullable. The only SQL default values are the `DEFAULT NULL`s on the v5 `song_tempos` columns.

### `playlists` (v1), `PlaylistEntity`

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER | Primary key, autoincrement |
| `name` | TEXT | |
| `createdAt` | INTEGER | Epoch ms |
| `updatedAt` | INTEGER | Epoch ms |

### `playlist_songs` (v1), `PlaylistSongEntity`

| Column | Type | Notes |
|---|---|---|
| `playlistId` | INTEGER | FK to `playlists.id`, ON DELETE CASCADE |
| `songId` | INTEGER | MediaStore audio ID |
| `position` | INTEGER | 0-based order |

Primary key `(playlistId, songId)`; index on `playlistId`. A song can appear only once per playlist.

### `song_fingerprints` (v2), `SongFingerprintEntity`

| Column | Type | Notes |
|---|---|---|
| `songId` | INTEGER | Primary key |
| `sizeBytes` | INTEGER | Cache is valid only while size and date match |
| `dateModified` | INTEGER | |
| `hash` | TEXT | SHA-256 of size + first/last 64 KiB |

### `duplicate_overrides` (v2), `DuplicateOverrideEntity`

| Column | Type | Notes |
|---|---|---|
| `songId` | INTEGER | Primary key |
| `kind` | TEXT | `Keep` or `Restore` |

### `stem_sets` (v3), `StemSetEntity`

| Column | Type | Notes |
|---|---|---|
| `songId` | INTEGER | Primary key |
| `title`, `artist` | TEXT | |
| `sourceSizeBytes`, `sourceDateModified` | INTEGER | Detects a changed source file |
| `fingerprint` | TEXT, nullable | `FileFingerprinter` hash, used to re-attach stems after re-indexing |
| `vocalsPath`, `instrumentalPath` | TEXT | Absolute paths under `filesDir/stems/<songId>/` |
| `bytes` | INTEGER | Disk use |
| `createdAt` | INTEGER | |

### `separation_jobs` (v3), `SeparationJobEntity`

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER | Primary key, autoincrement |
| `songId`, `title`, `artist`, `sourceUri` | | |
| `durationMs`, `sourceSizeBytes`, `sourceDateModified` | INTEGER | |
| `state` | TEXT | `Queued`, `Running`, `Done`, `Failed`, `Cancelled` |
| `progress` | REAL | 0 to 1 |
| `remainingMs` | INTEGER, nullable | |
| `pauseReason` | TEXT, nullable | `Charging`, `Battery`, `Heat`, `TimeLimit`, `Interrupted`, `NeedsApp` |
| `error` | TEXT, nullable | `SourceMissing`, `UnsupportedFormat`, `CorruptFile`, `OutOfMemory`, `LowStorage`, `ModelUnavailable`, `ModelFailed`, `Unknown` |
| `enqueuedAt` | INTEGER | |
| `finishedAt` | INTEGER, nullable | |

Indexes on `songId` and `state`.

### `song_tempos` (v4, meter columns v5), `SongTempoEntity`

Declared in [`data/tempo/TempoEntities.kt`](../app/src/main/java/com/autoomstudio/mplay/data/tempo/TempoEntities.kt).

| Column | Type | Notes |
|---|---|---|
| `songId` | INTEGER | Primary key |
| `sizeBytes`, `dateModified` | INTEGER | Entry is deleted when these no longer match |
| `bpm` | REAL | Detected tempo |
| `confidence` | TEXT | `TempoConfidence`: `High`, `Medium`, `Low` |
| `beatsPerBar` | INTEGER, nullable (v5) | Detected time signature, 2, 3, 4 or 6 |
| `beatUnit` | INTEGER, nullable (v5) | 4 or 8 |
| `meterConfidence` | TEXT, nullable (v5) | `TempoConfidence` name; `None` = analyzed, meter undetermined; NULL = row from before v5, re-analyzed on next Detect |
| `meterBpm` | INTEGER, nullable (v5) | BPM the metronome uses with this meter (eighth-note rate for 6/8) |

Read and written through `TempoCache` ([`data/tempo/TempoCache.kt`](../app/src/main/java/com/autoomstudio/mplay/data/tempo/TempoCache.kt)).

## DAOs

- **`PlaylistDao`**: observe playlists (by `name COLLATE NOCASE, id`) and entries (by `playlistId, position`); `insert`, `rename`, `delete` (cascades), `removeSongsEverywhere`; transactions `addSongs` (append, skip existing, returns count), `removeSongs`, `reorder` (uses `PlaylistQueries.applyVisibleOrder`).
- **`DuplicateDao`**: observe/upsert/delete fingerprints; observe overrides; transaction `keep(songId, groupIds)`; `restore`; `hideAgain`.
- **`StemDao`**: stem set CRUD; job queue queries (`nextQueued`, `queuedCount`, `activeSongIds`, `observeJobs`, `observeJobState`); guarded state transitions (`markRunning`, `updateProgress`, `requeue`, `requeueAllRunning`, `setQueuedPauseReason`, `markDone`, `markFailed`, `failAllActive`, `cancel`, `retry`, `clearFinished`, `removeFinished`); transaction `complete(jobId, stemSet, now)`.
- **`TempoDao`**: `tempo(songId)`, `upsert(entry)`, `delete(songId)`.

## Changing the schema

1. Edit or add the `@Entity` and register it in `MPlayDatabase.entities`.
2. Bump `version` and add `AutoMigration(from = N, to = N + 1)`. Use a manual `Migration` or an `AutoMigrationSpec` for renames and deletes.
3. Build so Room exports `N+1.json`, and commit that file.
4. Update this document (version table and table section) and add a changelog entry.

There are no migration tests yet (`room-testing` is not a dependency).
