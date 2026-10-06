# Changelog

All notable changes to MPlay are recorded here, newest first. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions match `versionName` in [`app/build.gradle.kts`](../app/build.gradle.kts).

Each entry should say what changed and link to the feature doc. Use these groups: **Added**, **Changed**, **Fixed**, **Removed**, **Database** (Room schema changes), **Docs**.

## [Unreleased]

Changes after 3.1 that haven't shipped in a version yet. `versionName` is still `3.1`.

### Added
- Documentation set in `docs/` (feature docs, architecture, database, this changelog) and the Cursor rule `.cursor/rules/docs-maintenance.mdc` that keeps it updated. ([README](README.md))
- Metronome: own tab, Now Playing chip and sheet, 20-300 BPM, time signatures, accent, three click sounds, tap tempo, foreground service with Stop action. (`e13b2f7`, [metronome](features/metronome.md))
- BPM detection for the current song (spectral flux + autocorrelation), using the instrumental stem when available, cached per song. (`e13b2f7`, [metronome](features/metronome.md))
- Sing-along recording over the instrumental stem with count-in, review, voice/music/sync mixing, preview and save to `Music/MPlay Recordings`. (`e13b2f7`, [sing-along](features/sing-along.md))
- Separation time estimate based on this phone's measured speed. (`e13b2f7`, [vocal separation](features/vocal-separation.md))
- Permissions `RECORD_AUDIO` and `FOREGROUND_SERVICE_MICROPHONE`; services `MetronomeService` and `RecordingService`. (`e13b2f7`, [architecture](architecture.md))

### Changed
- The separation notice now shows before every separation, with an estimate and a "Don't show again" option (previously shown once). The time notice also appears in the notification, progress pill and queue. (`e13b2f7`)
- The "Separate vocals?" dialog text (`separation_notice_message`) is shorter: it asks for patience while MPlay separates the song and says the time depends on the phone's processor and available resources. It no longer mentions heat, battery, charging, result quality, personal use, or "Nothing is uploaded". ([vocal separation](features/vocal-separation.md))
- Now Playing chips wrap (`FlowRow`); the mini player shows a metronome indicator. (`e13b2f7`, [playback](features/playback.md))
- `PlaybackController` gained `play()`, `pause()`, `currentPositionMs()`, `isSeekable()`. (`e13b2f7`)
- Files in `Music/MPlay Recordings/` are exempt from the library's 30-second minimum. (`e13b2f7`, [library](features/library.md))
- Settings key `separation_notice_shown` replaced by `separation_notice_hidden` (no migration; old value ignored). (`e13b2f7`)

### Database
- Schema v4: new table `song_tempos`, `AutoMigration(3, 4)`. (`e13b2f7`, [database](database.md))

## [3.1] - 2026-10-06

### Added
- About MPlay screen with version, links and privacy note. (`40a4c10`, [settings and about](features/settings-and-about.md))
- Open-source licenses screen. (`26863d9`)
- Thermal monitoring and adaptive thread count for separation. (`3c47315`, [vocal separation](features/vocal-separation.md))

### Changed
- Model import and separation improvements. (`26863d9`)

### Docs
- PRD v3.1. (`30cddab`)

## [3.0] - 2026-10-05

### Added
- On-device AI vocal separation (HT-Demucs on ONNX Runtime): queue, WorkManager worker, progress notification and pill, stem playback modes (Original / Instrumental / Vocals), stem export. (`55e39e6`, [vocal separation](features/vocal-separation.md))
- `:separation` module and `:separator` process for inference; model asset build tasks and SHA-256 checks. (`967eb22`)

### Changed
- Availability checks and notification titles. (`bbed725`)
- Auto-sizing stem mode labels on Now Playing. (`0a56dcd`)

### Database
- Schema v3: tables `stem_sets`, `separation_jobs`. (`55e39e6`)

## [2.0] - 2026-10-03

### Added
- Duplicate detection with hide-duplicates setting and review screen. (`201e1c9`, [duplicates](features/duplicates.md))
- Lofi mode and sleep timer. (`201e1c9`, [lofi](features/lofi-mode.md), [playback](features/playback.md))
- `LofiWaveIcon` and lofi chip on Now Playing. (`9dd9584`)

### Database
- Schema v2: tables `song_fingerprints`, `duplicate_overrides`. (`201e1c9`)

## [1.0] - 2026-10-03

### Added
- Initial app: library from MediaStore, Compose UI. (`5685cb3`, `ee66e5a`, [library](features/library.md))
- Media3 playback service and controller, session persistence and restore, mini player and Now Playing. (`7d8ad4f`, `72daa1c`, [playback](features/playback.md))
- Automatic folder scanning and watching. (`449ed10`)
- Sorting, search, album and artist pages. (`eb0fb2f`)
- Playlists. (`8dc73ee`, [playlists](features/playlists.md))
- Mini player scrubbing. (`69fb750`)
- Clip editor, Cut and save, Set as ringtone. (`23e2ee1`, [clips](features/clips-and-ringtones.md))
- Home screen widget and notification permission. (`73cdb66`, [widget](features/widget.md))
- Song deletion and multi-select UI. (`da53339`)

### Changed
- Playback UI refactor and artwork handling. (`3add93e`)
- Splash screen theme colours; removed unused launcher vector. (`3039fba`)
- UI components and animations. (`99970fe`)

### Fixed
- Expand progress calculation in `PlayerSheet`. (`ea2857f`)

### Database
- Schema v1: tables `playlists`, `playlist_songs`. (`8dc73ee`)

### Docs
- PRD v2. (`092c308`)
