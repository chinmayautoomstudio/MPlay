# MP3 Studio Developer Documentation

MP3 Studio (formerly MPlay) is a local-music player for Android (package `com.autoomstudio.mplay`) built with Jetpack Compose and Media3. On top of normal playback it adds playlists, duplicate detection, clip cutting and ringtones, a lofi effect, on-device AI vocal separation (HT-Demucs on ONNX Runtime), a metronome with BPM detection, and sing-along recording. Since PRD v3.2 the app requires a Google sign-in, backed by a self-hosted Supabase project; music and recordings still never leave the phone.

| Item | Value |
|---|---|
| Version | 3.1 (`versionCode = 5`), see [`app/build.gradle.kts`](../app/build.gradle.kts) |
| minSdk / targetSdk / compileSdk | 26 / 37 / 37 |
| Modules | `:app`, `:separation` (DSP + ONNX pipeline), `:spike` (feasibility test app) |
| Database | Room `mplay.db`, schema version 6 |
| Network | Supabase Auth and PostgREST only (`INTERNET`, `ACCESS_NETWORK_STATE`, no cleartext). The build fails if any permission outside `ALLOWED_PERMISSIONS` is merged into the manifest. |
| Backend | Self-hosted Supabase, see [backend.md](backend.md) |

## How these docs are organised

- [CHANGELOG.md](CHANGELOG.md): every change, newest first. Work that hasn't shipped in a version yet goes under `Unreleased`.
- [architecture.md](architecture.md): modules, dependency container, startup, navigation, services and processes.
- [database.md](database.md): Room tables, DAOs, schema versions and migrations.
- [backend.md](backend.md): Supabase project, server tables, Row Level Security, auth settings and migrations.
- `features/`: one file per feature area (listed below).
- [templates/feature-template.md](templates/feature-template.md): skeleton for new feature docs.

## Features

| Feature | Doc | Added in |
|---|---|---|
| Playback (service, queue, session restore, sleep timer, player UI) | [features/playback.md](features/playback.md) | 1.0 |
| Library (MediaStore, scanning, sorting, search, selection, deletion) | [features/library.md](features/library.md) | 1.0 |
| Playlists | [features/playlists.md](features/playlists.md) | 1.0 |
| Clips and ringtones (trim editor) | [features/clips-and-ringtones.md](features/clips-and-ringtones.md) | 1.0 |
| Home screen widget | [features/widget.md](features/widget.md) | 1.0 |
| Duplicate detection | [features/duplicates.md](features/duplicates.md) | 2.0 |
| Lofi mode | [features/lofi-mode.md](features/lofi-mode.md) | 2.0 |
| AI vocal separation (stems) | [features/vocal-separation.md](features/vocal-separation.md) | 3.0 |
| Settings, About, theme and shared components | [features/settings-and-about.md](features/settings-and-about.md) | 1.0, About in 3.1 |
| Metronome and BPM detection | [features/metronome.md](features/metronome.md) | Unreleased |
| Sing-along recording | [features/sing-along.md](features/sing-along.md) | Unreleased |
| Accounts and Google sign-in | [features/accounts.md](features/accounts.md) | Unreleased |

## Product requirement documents

The PRDs describe intent; these docs describe what the code actually does.

- [MPlay PRD.md](../MPlay%20PRD.md) (v1)
- [MPlay PRD v2.md](../MPlay%20PRD%20v2.md)
- [MPlay PRD v3_ AI Vocal Separation.md](../MPlay%20PRD%20v3_%20AI%20Vocal%20Separation.md)
- [MPlay PRD v3.1.md](../MPlay%20PRD%20v3.1.md)
- [MP3 Studio PRD v3.2.md](../MP3%20Studio%20PRD%20v3.2.md) (rename, accounts, plans)

## Keeping the docs current

The Cursor rule [`.cursor/rules/docs-maintenance.mdc`](../.cursor/rules/docs-maintenance.mdc) tells the agent to update these docs with every code change. When editing by hand, follow the same steps:

1. Add a line under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md).
2. Update the matching file in `features/`, including its "Change history" section.
3. For a new feature area, copy [templates/feature-template.md](templates/feature-template.md) into `features/` and add it to the table above.
4. If the Room schema changes, update [database.md](database.md).
5. When `versionName` is bumped, rename `Unreleased` in the changelog to the new version and date.
