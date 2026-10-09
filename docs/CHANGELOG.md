# Changelog

All notable changes to MPlay are recorded here, newest first. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions match `versionName` in [`app/build.gradle.kts`](../app/build.gradle.kts).

Each entry should say what changed and link to the feature doc. Use these groups: **Added**, **Changed**, **Fixed**, **Removed**, **Database** (Room schema changes), **Docs**.

## [Unreleased]

## [3.2.1] - 2026-10-09

Patch release: lofi tuning, swipe-away stopping all background work, and a sign-in screen fix. From now on each small release bumps the patch number (3.2.1, 3.2.2, ...).

### Changed
- `versionName` 3.2 to 3.2.1 and `versionCode` 7 to 8. Settings > About now shows only "Version 3.2.1"; the build number is no longer displayed (`about_version` string). ([settings](features/settings-and-about.md))
- Swiping MP3 Studio away from recents now ends all background work, not just music: the metronome stops, a sing-along take in progress is discarded, and vocal separation pauses (the song goes back to the queue and the queue resumes the next time the app opens). Pressing Back stops nothing. New `di/TaskRemoval`, called from `onTaskRemoved` of `PlaybackService`, `MetronomeService`, `RecordingService` and the new non-exported `SeparationTaskWatcher` service (started by `SeparationWorker` for the length of a run); `SeparationController.pause()`/`resume()`; unit test `TaskRemovalTest`. ([architecture](architecture.md), [metronome](features/metronome.md), [sing-along](features/sing-along.md), [vocal separation](features/vocal-separation.md), [playback](features/playback.md))
- Lofi mode sounds more natural: voices no longer turn deep (`LOFI_PLAYBACK` keeps 0.9x speed but pitch is now 0.98x instead of 0.9x), and instruments sit level with the vocals thanks to a -4 dB 2.5 kHz presence dip on the centre channel and stereo sides lifted 1.2x. The low-pass opens to 5 kHz and the reverb mix drops to 0.2. The vinyl crackle pops are gone; the soft hiss and bit-crush stay. New `Biquad.peaking`; new `LofiEffectTest` cases. ([lofi mode](features/lofi-mode.md))

### Fixed
- The two floating music notes on the left of the sign-in screen no longer look clipped. Notes sharing one `VectorPainter` were drawn from a bitmap cached at another note's size; each note now has its own painter (`SignInBackdrop.kt`). ([accounts](features/accounts.md))

## [3.2] - 2026-10-08

MP3 Studio: the rename from MPlay, mandatory Google sign-in, Free/Trial/Pro plans, the weekly AI Vocal Separator limit, admin screens, account deletion, metronome sync and time signature detection, and playback that stops when the app is swiped away.

### Added
- Admin Activity feed: the bell on the Admin dashboard shows a red badge with the number of new events and opens a list of new sign-ups, new subscriptions (with plan and provider) and deleted accounts, newest first; tapping a sign-up or subscription opens that user. Supabase migration `admin_activity` (`admin_activity()`, applied live) and the `activity` action of the `admin` Edge Function; DataStore key `admin_activity_seen_at`; pgTAP, Deno and `AdminViewModelTest` cases. ([admin](features/admin.md), [backend](backend.md))
- Delete account (PRD v3.2 M6, AU9, PR4): Settings > Delete account explains what is deleted and what stays on the phone, and needs `DELETE` typed to confirm. The server deletes the account, plan, trial and usage history at once and keeps trial claims (no second trial) and payment records without the user ID; the phone then signs out locally. The last admin and accounts with a renewing paid subscription are refused with a message. Deletions appear in the admin audit log without the email. ([accounts](features/accounts.md), [admin](features/admin.md))
- Supabase `account_deletion` migration (`delete_account()`, applied live) and the `delete-account` Edge Function; Deno `account_test.ts`. ([backend](backend.md))
- Abuse and quota tests (M6): pgTAP `abuse_test.sql` (trial reuse after deletion, usage replay tricks, deletion rules, a sweep of functions the app roles can call), `concurrency_check.sh` (20 parallel reservations, at most 10 granted) and `run_live.ps1`, which runs all SQL tests and the concurrency check against the live database over SSH. All passed on 2026-10-08. ([backend](backend.md))
- Unit tests: `AccountDeletionTest`, `deleteAccount()` cases in `AuthRepositoryTest`. ([accounts](features/accounts.md))
- Store review setup and reviewer notes. ([store review](store-review.md))
- Admin screens (PRD v3.2 M5): admins get an Admin row in Settings with user counts and weekly usage, user search with filters, a user detail page (plan, trial, subscriptions, payments, usage history, jobs), Make/Remove admin, Disable/Enable account, Grant Pro with an end date or Remove Pro, an Admins page to add admins by email (an invite if they haven't signed in yet; they become admin on first sign-in) and revoke invites, weekly usage with top users, and the audit log. The server refuses removing the last admin and disabling yourself. ([admin](features/admin.md))
- Supabase `admin` migration (`admin_invites`, `admin_*` functions, `subscriptions.provider` may be `admin`, `handle_new_user()` accepts invites, applied live) and the `admin` Edge Function; pgTAP `admin_test.sql`, Deno `admin_test.ts`. `entitlements` responses are read for `role`. ([backend](backend.md))
- Unit tests: `AdminViewModelTest`, `EntitlementsResponseTest`; test dependency `kotlinx-coroutines-test`. ([admin](features/admin.md))
- AI Vocal Separator weekly limit (PRD v3.2 M3): Free users can separate 10 songs a week, resetting Monday 00:00 India Standard Time; Trial and Pro are unlimited. Each song reserves a use on the server when queued (Free users need internet for that). A use counts when the job completes; cancelled or failed jobs give it back, and the outcome is sent by `UsageSyncWorker` once online. Picking more songs than remain queues the first ones with a "skipped" snackbar (the time notice warns about this first); none left opens the upgrade sheet with "Weekly limit reached" and the reset day. Usage ("7/10 songs used this week. 3 remaining.") shows in Settings, the queue screen, the time notice and Settings > Plans. ([vocal separation](features/vocal-separation.md), [plans](features/plans.md))
- Supabase `usage` migration (`usage_summary()`, `reserve_separation()`, `finish_separation()`, `denied` status in `ai_usage`, applied live) and Edge Functions `reserve-separation` and `finish-separation`; `entitlements` now also returns `usage`. pgTAP `usage_test.sql`, Deno `usage_test.ts`, admin usage SQL in the backend doc. ([backend](backend.md))
- Unit tests: `SeparationUsageGateTest`, `UsageReporterTest`, usage cases in `EntitlementPolicyTest`. ([vocal separation](features/vocal-separation.md))
- Plans and gating (PRD v3.2 M2): Free, Trial and Pro. The first sign-in claims a 30-day trial once per normalized email and device; new BPM detections and Sing Along need Trial or Pro and show an upgrade sheet otherwise (cached tempos and saved recordings stay usable). Settings > Plans with the current plan, dates and comparison table, a Plan row on the Account card, and a dismissible banner in the last 3 days of the trial. "Go Pro" is a disabled "Payments coming soon" button at ₹99 per month. The plan is cached in DataStore `entitlements` with a 7-day offline grace. ([plans](features/plans.md), [metronome](features/metronome.md), [sing-along](features/sing-along.md))
- Supabase `plans` migration (`compute_entitlements()`, `claim_trial()`, service-role only, applied live) and Edge Functions `entitlements` and `claim-trial` with peppered email/device hashes (`TRIAL_HASH_PEPPER`); pgTAP `plans_test.sql` and Deno tests. The app adds the supabase-kt `functions-kt` dependency. ([backend](backend.md))
- Unit tests: `EntitlementPolicyTest`, `EntitlementsRepositoryTest`, `TempoDetectionGateTest`. ([plans](features/plans.md))
- Mandatory Google sign-in (PRD v3.2 M1): sign-in screen with "Continue with Google" (Credential Manager with a nonce, Supabase `IDToken`), no guest mode, splash waits for the saved session, session encrypted with Tink and the Android Keystore in DataStore `auth_session`, disabled or revoked accounts signed out on the next foreground check. ([accounts](features/accounts.md))
- While signed out nothing plays: `PlaybackService` rejects controllers and resumption and stops on sign-out (saved queue kept), new `SignedInMediaButtonReceiver`, widget shows "Sign in to MP3 Studio" without controls, sign-out stops the metronome and sing-along and pauses the separation queue (`AuthEffects`, `SeparationBackend.cancel()`). ([accounts](features/accounts.md), [playback](features/playback.md), [widget](features/widget.md), [vocal separation](features/vocal-separation.md))
- Account card at the top of Settings: Google photo, name, email, Edit name (`profiles.display_name`), Log out with confirmation. Terms of Service link in About. ([accounts](features/accounts.md), [settings](features/settings-and-about.md))
- Supabase backend in `supabase/`: config (Google only), foundation migration with `profiles`, `subscriptions`, `trials`, `trial_claims`, `payment_events`, `ai_usage`, `admin_audit_log`, RLS on all tables, `handle_new_user` trigger, `is_admin()`, pgTAP RLS tests, first-Admin snippet. Applied to the live database. ([backend](backend.md))
- Unit tests: `AuthErrorsTest`, `NonceTest`, `AuthRepositoryTest`, `SavedFoldersTest`. ([accounts](features/accounts.md))
- Documentation set in `docs/` (feature docs, architecture, database, this changelog) and the Cursor rule `.cursor/rules/docs-maintenance.mdc` that keeps it updated. ([README](README.md))
- Metronome: own tab, Now Playing chip and sheet, 20-300 BPM, time signatures, accent, three click sounds, tap tempo, foreground service with Stop action. (`e13b2f7`, [metronome](features/metronome.md))
- BPM detection for the current song (spectral flux + autocorrelation), using the instrumental stem when available, cached per song. (`e13b2f7`, [metronome](features/metronome.md))
- Sing-along recording over the instrumental stem with count-in, review, voice/music/sync mixing, preview and save to `Music/MPlay Recordings`. (`e13b2f7`, [sing-along](features/sing-along.md))
- Separation time estimate based on this phone's measured speed. (`e13b2f7`, [vocal separation](features/vocal-separation.md))
- Permissions `RECORD_AUDIO` and `FOREGROUND_SERVICE_MICROPHONE`; services `MetronomeService` and `RecordingService`. (`e13b2f7`, [architecture](architecture.md))
- Time signature detection (MT18-MT21): Detect BPM also estimates 2/4, 3/4, 4/4 or 6/8 with a confidence, from the same analysis pass (`MeterDetector`). Medium/High results set the metronome's time signature with the BPM and show "Time signature set to 3/4" with Undo; Low results show a "Looks like 3/4. Use it?" chip; undetermined meters keep the current signature. For 6/8 the metronome clicks the eighth notes. The result card reads "120 BPM, 4/4". `versionCode` 5. ([metronome](features/metronome.md))
- Metronome sync with the song: after Detect BPM the metronome starts on the song's beats (accent on the downbeat), goes silent while the song is paused, re-aligns after seeks, speed changes and drift over 20 ms, and turns off when the song changes or the BPM is changed by hand. A "Sync with song" chip in the Now Playing sheet toggles it. New `BeatGrid`, `SongSync`, `MusicTimeline`, `BeatClock.align()`, `MetronomeEngine.heardFrame()`. ([metronome](features/metronome.md))
- Sing-along animations: the overlay cross-fades between states; while recording, the dot blinks, the timer digits roll, a scrolling waveform replaces the level bar and rings pulse around Stop with the voice; saving shows a circular progress ring; a saved take gets an animated check with a burst. All of it is skipped when system animations are off. ([sing-along](features/sing-along.md))
- `MediaPcmSource` gained `exactStart`, which drops decoded audio before `startUs`. ([vocal separation](features/vocal-separation.md))
- `PlaybackController` implements `MusicTimeline` (song ID, playing, seeks, position snapshot with speed) and takes `restoresSession`; `AppContainer` gives the metronome its own instance. ([playback](features/playback.md), [architecture](architecture.md))

### Changed
- Swiping MP3 Studio away from recents now stops the music and removes the notification, instead of playing on in the background. The queue and position are saved, so reopening the app or pressing play on the widget resumes it. ([playback](features/playback.md))
- Detect BPM is now on the Metronome tab as well as the Now Playing sheet: one tap analyses the song that's playing (disabled with "Play a song to detect its BPM" when nothing plays). The low-confidence and failed messages no longer point to Tap tempo. ([metronome](features/metronome.md))
- The "Profile" label in the bottom navigation bar now lines up with the other tab labels. ([settings](features/settings-and-about.md))
- Admin dashboard redesign: MP3 Studio wordmark header with an Admin chip and the Activity bell (the app top bar is hidden there), "Admin Dashboard" title, a stats card with six coloured tiles and a weekly usage row, three shortcut cards (Admins, AI Vocal Separator usage, Audit log), and a Users card with a pill search, filled filter chips and rows with initials avatars, a status dot, plan chip, weekly count and a "..." menu (View details, Make/Remove admin, Disable/Enable). ([admin](features/admin.md))
- Profile screen is more compact so it fits on one phone screen: the back button and title share a row, and the avatar, subscription card, category rows and text are smaller. Edit is now a small pencil icon button. ([settings](features/settings-and-about.md))
- `versionName` 3.1 to 3.2 and `versionCode` 6 to 7, so Settings > About shows 3.2 and the build installs over 3.1 builds. ([settings](features/settings-and-about.md))
- `versionCode` 5 to 6 (`versionName` stays 3.1), so sideloaded builds with the Profile tab install as an update and can be told apart from earlier 3.1 builds. ([architecture](architecture.md))
- The Settings tab is now the Profile tab, and its icon is the account photo. The Profile screen has a back button, the photo with name, email and an Edit button, a subscription card that opens Plans, an Admin card for admins, and cards for Appearance, Library, Vocal Separation, Background Playback and About, each opening its own page. Log out is below About. Delete account and the name field moved into the Edit page. The screen follows the light or dark theme. ([settings](features/settings-and-about.md), [accounts](features/accounts.md), [architecture](architecture.md))
- Redesigned sign-in screen: always dark, with a glowing floating M logo, "MP3 Studio" with a gradient "Studio", the "Your Music. Your Way." tagline, an animated hero (flowing waves, equalizer bars, floating notes, sparkles), a white glowing "Continue with Google" pill with the Google G and a spinner while signing in, and linked Terms and Privacy text. The entrance is staggered, and every animation is skipped when system animations are off. `MPlayAppTheme` gained `forceDark`. ([accounts](features/accounts.md), [settings](features/settings-and-about.md))
- Decided in M6 that old MPlay (`com.autoomstudio.mplay`) installs are not migrated: MP3 Studio installs as a separate app. The PRD's App ID decision has a dated note. ([architecture](architecture.md))
- `usage_test.sql` counts denied rows for its own test user, so it passes against a database with real usage. ([backend](backend.md))
- Offline grace checked end to end on the emulator: offline Trial works, 8 days without a check or a clock turned back locks Pro features with the "offline for more than 7 days" message, and the next foreground refresh restores the plan. ([plans](features/plans.md))
- Package renamed to `com.autoomstudio.mp3studio` everywhere: `applicationId` and `namespace` of `:app` and `:separation` (`com.autoomstudio.mp3studio.separation`), all Kotlin packages, the Room schema folder, and internal intent and command strings (`com.autoomstudio.mp3studio.command.*`). It installs as a new app, so data from an old `com.autoomstudio.mplay` install doesn't carry over. Google Android OAuth clients must use the new package. ([architecture](architecture.md), [accounts](features/accounts.md))
- Renamed MPlay to MP3 Studio: all strings, the wordmark ("MP3" in neon plus " Studio"), and save folders `Music/MP3 Studio Clips`, `Music/MP3 Studio Recordings`, `Music/MP3 Studio Stems`. Files in the old `MPlay Clips` and `MPlay Recordings` folders stay exempt from the 30 s minimum (`SavedFolders`). Package name and launcher icon unchanged. ([library](features/library.md), [clips](features/clips-and-ringtones.md), [sing-along](features/sing-along.md), [vocal separation](features/vocal-separation.md))
- The app now uses the network for accounts: `INTERNET` and `ACCESS_NETWORK_STATE`, cleartext blocked, `CheckNoInternetPermission` replaced by the `CheckAllowedPermissions` allow-list, new `CheckBackendConfig`, `BuildConfig` Supabase and Google settings from `local.properties`. `USE_BIOMETRIC`/`USE_FINGERPRINT` from `androidx.credentials` are removed. ([architecture](architecture.md))
- Signed-in screens get their ViewModels from `SignedInViewModelScope`, cleared on sign-out. ([architecture](architecture.md))
- Separation queue resumes from `SeparationController.onSignedIn()` instead of `start()`. ([vocal separation](features/vocal-separation.md))
- About privacy note, app description and permission rationale no longer say the app is offline. ([settings](features/settings-and-about.md))
- Metronome UI redesign (tab and Now Playing sheet): glowing BPM ring with beat dots and -/+ circles, thin tempo slider, Tap tempo and "Sync with song" tiles, Sound / Start-Stop / Settings row, Time signature card with a Custom dialog (beats and 1/4 or 1/8 unit), Accent card. Sound is a quick mute (`MetronomeController.setUserMuted`, not saved); Settings opens a sheet with click sound and volume. Detect BPM / ÷2 / ×2, the result card and its "Use it?" pill appear only in the sheet. ([metronome](features/metronome.md))
- The separation notice now shows before every separation, with an estimate and a "Don't show again" option (previously shown once). The time notice also appears in the notification, progress pill and queue. (`e13b2f7`)
- The "Separate vocals?" dialog text (`separation_notice_message`) is shorter: it asks for patience while MPlay separates the song and says the time depends on the phone's processor and available resources. It no longer mentions heat, battery, charging, result quality, personal use, or "Nothing is uploaded". ([vocal separation](features/vocal-separation.md))
- Now Playing chips wrap (`FlowRow`); the mini player shows a metronome indicator. (`e13b2f7`, [playback](features/playback.md))
- `PlaybackController` gained `play()`, `pause()`, `currentPositionMs()`, `isSeekable()`. (`e13b2f7`)
- Files in `Music/MPlay Recordings/` are exempt from the library's 30-second minimum. (`e13b2f7`, [library](features/library.md))
- Settings key `separation_notice_shown` replaced by `separation_notice_hidden` (no migration; old value ignored). (`e13b2f7`)

### Fixed
- Google sign-in no longer fails silently when Google rejects the chosen account (`[16] Account reauth failed`, reported as a cancellation): it shows "Google couldn't confirm this account on this phone" (`AuthError.AccountUnavailable`) and logs the exception. ([accounts](features/accounts.md))

### Removed
- Metronome Tap tempo (manual tapping to set the BPM), with `TapTempo` and `TapTempoTest`; Detect BPM replaces it. ([metronome](features/metronome.md))
- `:spike` module (the separation feasibility/benchmark app; never shipped, nothing depended on it) and the `song` mode of `tools/make_reference.py` that only fed it. ([architecture](architecture.md))

### Database
- Schema v4: new table `song_tempos`, `AutoMigration(3, 4)`. (`e13b2f7`, [database](database.md))
- Schema v5: `song_tempos` gains nullable `beatsPerBar`, `beatUnit`, `meterConfidence`, `meterBpm`, `AutoMigration(4, 5)`. Existing rows are kept; rows without a meter are re-analyzed once on the next Detect. ([database](database.md), [metronome](features/metronome.md))
- Schema v6: `song_tempos` gains nullable `downbeatMs`, `beatPeriodMs`, `AutoMigration(5, 6)`. Rows without a grid are re-analyzed once on the next Detect. ([database](database.md), [metronome](features/metronome.md))
- Schema v7: new table `usage_reports`; `separation_jobs` gains nullable `usageRef`, `usageUserId`, `AutoMigration(6, 7)`. Jobs queued before v7 have no reservation and send no report. ([database](database.md), [vocal separation](features/vocal-separation.md))

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
