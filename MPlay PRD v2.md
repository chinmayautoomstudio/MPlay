# MPlay: Product Requirements Document (v2)

**Product:** MPlay, an offline Android music player **Platform:** Android (native Kotlin, Jetpack Compose) **Version:** 2.0 (builds on v1.0) **Status:** Draft

---

## What's New in v2

v2 keeps everything from v1 and adds three features:

1. **Sleep timer** (section 6.10): stop playback automatically after a chosen time.
2. **Duplicate removal** (section 6.11): duplicate songs are detected and hidden from the library list.
3. **Lofi mode** (section 6.12): one toggle that gives any song a live lofi-style sound.

---

## 1. Overview

MPlay is a lightweight, fully offline music player for Android. It plays audio files (mostly MP3, plus FLAC, AAC/M4A, OGG and WAV) already stored on the user's device, keeps playing in the background, and offers standard notification-bar and lock-screen controls. It has no accounts, no ads, no analytics and no internet permission.

## 2. Problem Statement

Many music apps push streaming, accounts, ads and tracking, and are heavy on storage and battery. People who keep their own MP3 collections want a fast, simple, private player that just plays their files reliably, including with the screen off.

## 3. Goals and Non-Goals

**Goals**

- Play local audio files reliably (MP3, FLAC, AAC/M4A, OGG, WAV), with background playback.
- Offer a home-screen widget for quick playback control.
- Let users cut a section of any song, save it as a new file, and set it as a ringtone, notification or alarm sound, all from the three-dot menu.
- Help users fall asleep to music with a sleep timer that fades out and stops playback.
- Keep the library clean by detecting duplicate songs and hiding the extra copies.
- Offer a one-tap lofi mode that makes existing songs sound lofi while playing.
- Provide a familiar player experience: notification controls, lock-screen controls, headset and Bluetooth button support.
- Be fully offline and privacy-first (no INTERNET permission).
- Handle libraries of 5,000+ songs smoothly.

**Non-Goals (v2)**

- Streaming, online radio, or any network feature.
- Accounts, cloud sync, or social features.
- Lyrics download, tag editing, or music discovery.
- Full audio editing (multi-track, effects, merging clips).
- Equalizer, Android Auto and Wear OS (candidates for later versions).
- Generating real lofi remixes (new chords, drums or arrangements). Lofi mode is a sound effect only.

## 4. Target Users

- **Primary:** Android users with a personal MP3 collection who want a simple, ad-free player.
- **Secondary:** Users with limited data or storage who prefer lightweight offline apps.

## 5. Platform and Technical Constraints

| Area | Decision |
| --- | --- |
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Playback | AndroidX Media3 (ExoPlayer, MediaSessionService, MediaSession) |
| Library source | MediaStore (Audio) |
| Storage | Room (playlists, duplicate fingerprints and overrides; schema v2 with a migration from v1), DataStore (settings, last session) |
| minSdk / targetSdk | 26 / 37 |
| Network | None. The INTERNET permission must not be declared. |
| Supported formats | MP3, FLAC, AAC/M4A, OGG (Vorbis/Opus), WAV, all decoded natively by Media3 ExoPlayer |
| Widget | Jetpack Glance app widget |
| Audio trimming | Media3 Transformer (clip and export to AAC/M4A); waveform drawn from decoded audio peaks |
| Lofi effect | Custom Media3 AudioProcessor chain (low-pass filter, bit-crush, slight slowdown, vinyl crackle mix) plus light reverb |
| Sleep timer | Timer inside MusicPlaybackService so it survives the app being closed |
| Duplicate detection | Normalized title + artist + duration match, with a partial content fingerprint (file size + hash of the first and last 64 KB) for exact copies |
| Distribution | Direct APK for now; Play Store deferred |
| Permissions | READ_MEDIA_AUDIO (API 33+), READ_EXTERNAL_STORAGE (API 32 and below), POST_NOTIFICATIONS (API 33+), FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK (used by Media3 during playback), WRITE_SETTINGS (special permission, requested only when the user sets a ringtone), WRITE_EXTERNAL_STORAGE (maxSdkVersion 29 together with requestLegacyExternalStorage, for saving clips on Android 10 and below). ACCESS_NETWORK_STATE, merged in by WorkManager through Glance, is removed from the merged manifest |

## 6. Functional Requirements

Priority: **P0** = must ship, **P1** = should ship, **P2** = nice to have.

### 6.1 Library and Scanning

| ID | Requirement | Priority |
| --- | --- | --- |
| L1 | Request audio permission with a clear rationale screen and a settings fallback if permanently denied | P0 |
| L2 | Read all device music via MediaStore, without restricting to the .mp3 extension | P0 |
| L3 | Show title, artist, album, duration and album art for each song | P0 |
| L4 | Fall back to filename for missing title and "Unknown Artist" for missing artist | P0 |
| L5 | Exclude audio under 30 seconds (ringtones, notification sounds) | P1 |
| L6 | Auto-refresh the library when files are added or removed | P1 |
| L7 | Browse by Songs, Albums and Artists tabs | P1 |
| L8 | Officially support MP3, FLAC, AAC/M4A, OGG and WAV; verify each plays, shows metadata and album art, and seeks correctly | P0 |

### 6.2 Playback

| ID | Requirement | Priority |
| --- | --- | --- |
| PB1 | Play, pause, next, previous and seek | P0 |
| PB2 | Tapping a song queues the current list and starts from that song | P0 |
| PB3 | Playback continues when the app is closed from recents or the screen is off | P0 |
| PB4 | Handle audio focus (pause or duck for calls and other apps, resume afterwards) | P0 |
| PB5 | Auto-pause when headphones are unplugged | P0 |
| PB6 | Gapless transition between tracks | P1 |
| PB7 | Play next and Add to queue actions | P1 |
| PB8 | Skip gracefully over files that were deleted or cannot be decoded | P0 |

### 6.3 Notification and System Controls

| ID | Requirement | Priority |
| --- | --- | --- |
| N1 | Media notification with title, artist, album art, play/pause, next, previous and a seek bar | P0 |
| N2 | Lock-screen media controls | P0 |
| N3 | Bluetooth and wired headset button support | P0 |
| N4 | Notification tap opens the Now Playing screen | P0 |
| N5 | Foreground service with type `mediaPlayback` | P0 |

### 6.4 Shuffle and Repeat

| ID | Requirement | Priority |
| --- | --- | --- |
| S1 | Shuffle toggle | P0 |
| S2 | Repeat modes: Off, Repeat All, Repeat One | P0 |
| S3 | Persist shuffle and repeat settings across launches | P1 |

### 6.5 Search and Sort

| ID | Requirement | Priority |
| --- | --- | --- |
| Q1 | Search by title, artist and album, case-insensitive, debounced | P0 |
| Q2 | Sort by Title, Artist, Album, Date added and Duration | P0 |
| Q3 | Persist the chosen sort order | P1 |

### 6.6 Playlists

| ID | Requirement | Priority |
| --- | --- | --- |
| PL1 | Create, rename and delete playlists | P0 |
| PL2 | Add songs to a playlist from any song's menu | P0 |
| PL3 | Remove and reorder songs within a playlist | P1 |
| PL4 | Play a playlist as the queue, with shuffle support | P0 |
| PL5 | Handle missing or deleted songs in a playlist without crashing | P0 |

### 6.7 Player UI

| ID | Requirement | Priority |
| --- | --- | --- |
| U1 | Persistent mini-player that expands to a full Now Playing screen | P0 |
| U2 | Now Playing: album art, title, artist, seek slider, elapsed and total time, transport controls, shuffle and repeat | P0 |
| U3 | Empty state and no-permission state | P0 |
| U4 | Dark and light theme, with Material 3 dynamic color | P1 |
| U5 | Restore last song and position on relaunch (paused) | P1 |

### 6.8 Home-Screen Widget

| ID | Requirement | Priority |
| --- | --- | --- |
| W1 | Widget shows album art, title and artist of the current song | P0 |
| W2 | Widget has play/pause, next and previous buttons that control the playback service | P0 |
| W3 | Tapping the widget body opens the Now Playing screen | P1 |
| W4 | Widget updates within 1 second of a track or play-state change and shows an idle state when nothing is queued | P1 |
| W5 | Light and dark theme support, resizable to at least two sizes | P2 |

### 6.9 Trim, Save and Ringtone (three-dot menu)

| ID | Requirement | Priority |
| --- | --- | --- |
| T1 | The three-dot menu on every song row and on Now Playing contains: Add to playlist, Play next, Add to queue, Cut and save, Set as ringtone, Song info, Delete from device (with the system confirmation dialog). The Now Playing menu additionally contains Sleep timer and Lofi mode | P0 |
| T2 | "Cut and save" opens a trim editor with a waveform and draggable start and end handles | P0 |
| T3 | Preview the selected range, with loop playback, before saving | P0 |
| T4 | Fine-tune start and end with time fields or ±0.1 s buttons | P1 |
| T5 | Save the clip as a new AAC/M4A file in Music/MPlay Clips via MediaStore, with an editable name (default "Song Title (clip)"). The original file is never modified | P0 |
| T6 | Saved clips appear in the library automatically | P1 |
| T7 | "Set as ringtone" opens the same editor with the first 30 seconds preselected; the user can adjust the range or use the full track, then chooses Phone ringtone, Notification sound or Alarm sound | P0 |
| T8 | On first use, explain why the WRITE_SETTINGS permission is needed and open the system permission screen; once granted, register the clip as ringtone, notification or alarm in MediaStore and set it as the system default | P0 |
| T9 | Show a confirmation after saving or setting, with an Undo action that restores the previous default sound | P1 |
| T10 | Optional fade in and fade out toggle | P2 |
| T11 | Enforce a minimum clip length of 1 second; warn when a ringtone selection is longer than 40 seconds | P1 |
| T12 | Handle failures (corrupt or unsupported file, low storage, permission denied) with clear error messages and no crash | P0 |

Trimming runs fully on-device and needs no network access.

### 6.10 Sleep Timer

| ID | Requirement | Priority |
| --- | --- | --- |
| ST1 | Sleep timer is available from the Now Playing screen (moon icon) and the Now Playing three-dot menu (not song rows) | P0 |
| ST2 | Presets: 15, 30, 45 and 60 minutes, plus "End of current song" and a custom time (1 to 180 minutes) | P0 |
| ST3 | Remaining time is visible on Now Playing while the timer runs, with options to cancel or extend | P0 |
| ST4 | Volume fades out over the last 30 seconds, then playback pauses and the queue is kept | P0 |
| ST5 | The timer runs in the playback service, so it still fires if the app is closed or the screen is off | P0 |
| ST6 | The timer is cancelled when playback is stopped or the queue is cleared, or when a new timer starts. A manual pause keeps it running | P1 |
| ST7 | Remember the last used duration as the suggested default | P2 |

### 6.11 Duplicate Removal

| ID | Requirement | Priority |
| --- | --- | --- |
| D1 | Detect duplicates as songs with the same normalized title and artist and a duration within 2 seconds, or files with an identical partial fingerprint (same file size and same hash of the first and last 64 KB) | P0 |
| D2 | Show only one copy of each duplicate group in the library, Albums, Artists and search results | P0 |
| D3 | Choose which copy to keep: prefer higher quality (lossless, then higher bitrate), then the earliest added. Bitrate comes from MediaStore on Android 11+ and is estimated as size × 8 / duration on older versions | P0 |
| D4 | Hiding is non-destructive: no files are deleted | P0 |
| D5 | Settings toggle "Hide duplicate songs" (on by default) | P1 |
| D6 | A "Review duplicates" screen lists the groups, lets the user switch which copy is kept, and restore hidden copies | P1 |
| D7 | A playlist entry that points to a hidden duplicate plays the kept copy | P1 |
| D8 | Optionally delete the extra copies from the device, with the system confirmation dialog | P2 |
| D9 | Run detection in the background and cache results so large libraries stay fast; re-run only for new or changed files | P0 |

### 6.12 Lofi Mode

| ID | Requirement | Priority |
| --- | --- | --- |
| LF1 | A "Lofi mode" toggle on the Now Playing screen, also available in the Now Playing three-dot menu | P0 |
| LF2 | When on, playback is processed live with a fixed preset: about 90% speed with lower pitch, low-pass filter near 4.5 kHz, light bit-crush, soft reverb and a quiet vinyl crackle layer | P0 |
| LF3 | When off, playback returns to the original sound with no audible glitch (switch under 300 ms) | P0 |
| LF4 | Lofi mode never modifies the audio files. It is not applied to saved clips, ringtones or the trim editor preview | P0 |
| LF5 | The toggle state persists across songs and app launches (off by default) | P1 |
| LF6 | Seek bar, elapsed and total time stay correct while playback speed is changed | P0 |
| LF7 | A short info text under the toggle explains that it adds a lofi sound effect and is not a remix | P1 |
| LF8 | Works with background playback, notification controls, sleep timer and all supported formats | P0 |

## 7. Non-Functional Requirements

- **Performance:** Cold start under 2 seconds; library of 5,000 songs scrolls at 60 fps; search results update within 300 ms.
- **Audio effects:** Lofi mode adds under 10% CPU on a mid-range device and must not cause dropouts or crackling beyond the intended vinyl layer.
- **Reliability:** No crashes on corrupt files, deleted files or permission revocation. Playback survives screen-off and app swipe-away.
- **Battery:** No wake locks beyond what Media3 requires during playback; no background work when idle.
- **Privacy:** No network access, no analytics, no data leaves the device.
- **Size:** Release APK under 20 MB with R8 enabled.
- **Compatibility:** Android 8.0 (API 26) to Android 17 (API 37).
- **Upgrades:** Updating from v1 keeps playlists, settings and the last session (Room migration from schema v1 to v2). The app version is 2.0.
- **Accessibility:** Content descriptions on all controls, minimum 48dp touch targets, support for system font scaling.

## 8. User Flows

1. **First launch:** Splash → permission rationale → grant → library loads → song list.
2. **Play a song:** Tap song → mini-player appears and audio starts → notification appears.
3. **Background play:** Press home or lock the screen → music continues → control it from the notification or lock screen.
4. **Create a playlist:** Long-press song → Add to playlist → New playlist → name → saved.
5. **Search:** Tap search → type → filtered list → tap to play.
6. **Cut and save:** Three-dot menu → Cut and save → drag handles → preview → name the clip → Save → clip appears in the library.
7. **Set as ringtone:** Three-dot menu → Set as ringtone → adjust range → choose Phone ringtone / Notification / Alarm → grant Modify system settings (first time only) → confirmation with Undo.
8. **Sleep timer:** Now Playing → moon icon → choose 30 minutes → countdown shows → music fades out and pauses at the end.
9. **Duplicates:** Library loads → duplicate copies are hidden automatically → Settings → Review duplicates → switch the kept copy or restore a hidden one.
10. **Lofi mode:** Now Playing → toggle Lofi mode → sound changes smoothly → toggle off to return to the original.

## 9. Screens

- Library (Songs, Albums, Artists tabs) with search and sort
- Album / Artist detail
- Playlists list and Playlist detail
- Now Playing (full screen)
- Trim and Ringtone editor (waveform, handles, preview, Save and Set as actions)
- Sleep timer sheet (presets, custom time, active countdown)
- Review duplicates (Settings)
- Lofi mode toggle on Now Playing
- Mini-player (persistent)
- Permission rationale
- Settings (theme, battery optimization tip, Hide duplicate songs, Review duplicates)

## 10. Success Metrics

Since MPlay collects no analytics, success is measured by testing and, if published, store signals:

- Zero known crashes in a 50-scenario manual test pass across at least 3 device brands.
- Background playback survives 60+ minutes of screen-off on Xiaomi, Samsung and Pixel devices.
- Notification controls work on Android 12 through 17.
- A trimmed clip can be set as ringtone on Pixel, Samsung and Xiaomi devices (Android 10 to 15) and plays for incoming calls.
- The sleep timer pauses playback within 2 seconds of the set time, including with the screen off for 60+ minutes.
- Duplicate detection finds at least 95% of duplicates in a test library of 2,000 songs with under 1% false positives.
- Lofi mode toggles without audible glitches and plays for 60+ minutes without dropouts on a mid-range device.
- Play Store rating of 4.3+ (if published).

## 11. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Aggressive background-kill by OEMs (Xiaomi, Oppo, Vivo) | Use a proper foreground media service; add an in-app tip on battery-optimization exemption |
| Scoped storage and permission changes across Android versions | Use MediaStore only; version-specific permission handling; test on API 26, 29, 33, 35 and 37 |
| Missing or wrong ID3 tags | Filename fallback and "Unknown" defaults |
| Large libraries slowing the UI | Stable LazyColumn keys, background queries, cached sorted lists |
| Foreground-service rules tightening in newer Android versions | Follow current `mediaPlayback` type requirements; retest each target SDK bump |
| WRITE_SETTINGS permission denied or unavailable on some OEMs | Explain why it is needed, deep-link to the system screen, and fall back to saving the clip so the user can pick it in system sound settings |
| Trim accuracy and quality (frame boundaries, re-encoding) | Use Media3 Transformer, export AAC at 192 kbps, and show a waveform with fine-tune controls |
| Users expect a real lofi remix from the toggle | Label it "Lofi mode" with an info text that it is a sound effect; consider sliders and Save as lofi later |
| Lower pitch from the slowdown sounds odd on vocals | Keep the slowdown mild (about 90%); add a speed control in a later version |
| Duplicate detection hides songs that are actually different versions (live, remix) | Require title, artist and duration to match; keep Review duplicates so users can restore any hidden copy |

## 12. Milestones

| Phase | Scope |
| --- | --- |
| M1: Foundation | Project setup, permissions, MediaStore library list |
| M2: Core playback | Media3 service, notification, lock-screen and headset controls |
| M3: Player UI | Mini-player, Now Playing, seek, theme |
| M4: Modes and discovery | Shuffle, repeat, search, sort, Albums and Artists tabs |
| M5: Playlists | Room database, playlist screens, queue actions |
| M6: Trim and ringtone | Three-dot menu, trim editor with waveform, save clips via MediaStore, set ringtone, notification and alarm sounds |
| M7: Sleep timer, duplicates and lofi | Sleep timer in the service, duplicate detection and review screen, lofi AudioProcessor chain and toggle |
| M8: Widget and hardening | Home-screen widget, format QA (FLAC, AAC, OGG, WAV), session restore, edge cases, R8, testing on real devices, signed release APK |

## 13. Future Scope (v2.1+)

Equalizer and bass boost, lofi intensity sliders and Save as lofi file, folder browsing, favorites, contact-specific ringtones, Android Auto, Play Store release, embedded lyrics (offline), playback speed, backup and restore of playlists to a local file.

## 14. Decisions

- **Formats:** MP3, FLAC, AAC/M4A, OGG and WAV are officially supported in v1 (requirement L8).
- **Distribution:** Direct APK for now. Play Store release is deferred to a later version.
- **Widget:** Home-screen widget is included in v1 (section 6.8).
- **Trim and ringtone:** Included in v1, reached from the three-dot menu on songs and Now Playing (section 6.9).
- **Sleep timer:** Included in v2 (section 6.10).
- **Duplicates:** Duplicate songs are hidden from the list, not deleted (section 6.11).
- **Lofi mode:** v2 ships a simple on/off toggle with one fixed preset; sliders and Save as lofi file are deferred (section 6.12).