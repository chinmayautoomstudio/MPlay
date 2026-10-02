# MPlay: Product Requirements Document

**Product:** MPlay, an offline Android music player **Platform:** Android (native Kotlin, Jetpack Compose) **Version:** 1.0 (MVP) **Status:** Draft

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
- Provide a familiar player experience: notification controls, lock-screen controls, headset and Bluetooth button support.
- Be fully offline and privacy-first (no INTERNET permission).
- Handle libraries of 5,000+ songs smoothly.

**Non-Goals (v1)**

- Streaming, online radio, or any network feature.
- Accounts, cloud sync, or social features.
- Lyrics download, tag editing, or music discovery.
- Full audio editing (multi-track, effects, merging clips).
- Equalizer, sleep timer, Android Auto and Wear OS (candidates for v1.1+).

## 4. Target Users

- **Primary:** Android users with a personal MP3 collection who want a simple, ad-free player.
- **Secondary:** Users with limited data or storage who prefer lightweight offline apps.

## 5. Platform and Technical Constraints

| Area | Decision |
| --- | --- |
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Playback | AndroidX Media3 (ExoPlayer, MediaSessionService, MediaSession) |
| Library source | MediaStore (Audio) |
| Storage | Room (playlists), DataStore (settings, last session) |
| minSdk / targetSdk | 26 / 35 |
| Network | None. The INTERNET permission must not be declared. |
| Supported formats | MP3, FLAC, AAC/M4A, OGG (Vorbis/Opus), WAV, all decoded natively by Media3 ExoPlayer |
| Widget | Jetpack Glance app widget |
| Audio trimming | Media3 Transformer (clip and export to AAC/M4A); waveform drawn from decoded audio peaks |
| Distribution | Direct APK for now; Play Store deferred |
| Permissions | READ_MEDIA_AUDIO (API 33+), READ_EXTERNAL_STORAGE (API 32 and below), POST_NOTIFICATIONS (API 33+), FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, WRITE_SETTINGS (special permission, requested only when the user sets a ringtone), WRITE_EXTERNAL_STORAGE (maxSdkVersion 28, for saving clips on Android 9 and below) |

## 6. Functional Requirements

Priority: **P0** = must ship, **P1** = should ship, **P2** = nice to have.

### 6.1 Library and Scanning

| ID | Requirement | Priority |
| --- | --- | --- |
| L1 | Request audio permission with a clear rationale screen and a settings fallback if permanently denied | P0 |
| L2 | Read all device music via MediaStore, without restricting to the .mp3 extension | P0 |
| L8 | Officially support MP3, FLAC, AAC/M4A, OGG and WAV; verify each plays, shows metadata and album art, and seeks correctly | P0 |
| L3 | Show title, artist, album, duration and album art for each song | P0 |
| L4 | Fall back to filename for missing title and "Unknown Artist" for missing artist | P0 |
| L5 | Exclude audio under 30 seconds (ringtones, notification sounds) | P1 |
| L6 | Auto-refresh the library when files are added or removed | P1 |
| L7 | Browse by Songs, Albums and Artists tabs | P1 |

### 6.2 Playback

| ID | Requirement | Priority |
| --- | --- | --- |
| P1 | Play, pause, next, previous and seek | P0 |
| P2 | Tapping a song queues the current list and starts from that song | P0 |
| P3 | Playback continues when the app is closed from recents or the screen is off | P0 |
| P4 | Handle audio focus (pause or duck for calls and other apps, resume afterwards) | P0 |
| P5 | Auto-pause when headphones are unplugged | P0 |
| P6 | Gapless transition between tracks | P1 |
| P7 | Play next and Add to queue actions | P1 |
| P8 | Skip gracefully over files that were deleted or cannot be decoded | P0 |

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
| T1 | The three-dot menu on every song row and on Now Playing contains: Add to playlist, Play next, Add to queue, Cut and save, Set as ringtone, Song info | P0 |
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

## 7. Non-Functional Requirements

- **Performance:** Cold start under 2 seconds; library of 5,000 songs scrolls at 60 fps; search results update within 300 ms.
- **Reliability:** No crashes on corrupt files, deleted files or permission revocation. Playback survives screen-off and app swipe-away.
- **Battery:** No wake locks beyond what Media3 requires during playback; no background work when idle.
- **Privacy:** No network access, no analytics, no data leaves the device.
- **Size:** Release APK under 20 MB with R8 enabled.
- **Compatibility:** Android 8.0 (API 26) to Android 15 (API 35).
- **Accessibility:** Content descriptions on all controls, minimum 48dp touch targets, support for system font scaling.

## 8. User Flows

1. **First launch:** Splash → permission rationale → grant → library loads → song list.
2. **Play a song:** Tap song → mini-player appears and audio starts → notification appears.
3. **Background play:** Press home or lock the screen → music continues → control it from the notification or lock screen.
4. **Create a playlist:** Long-press song → Add to playlist → New playlist → name → saved.
5. **Search:** Tap search → type → filtered list → tap to play.
6. **Cut and save:** Three-dot menu → Cut and save → drag handles → preview → name the clip → Save → clip appears in the library.
7. **Set as ringtone:** Three-dot menu → Set as ringtone → adjust range → choose Phone ringtone / Notification / Alarm → grant Modify system settings (first time only) → confirmation with Undo.

## 9. Screens

- Library (Songs, Albums, Artists tabs) with search and sort
- Album / Artist detail
- Playlists list and Playlist detail
- Now Playing (full screen)
- Trim and Ringtone editor (waveform, handles, preview, Save and Set as actions)
- Mini-player (persistent)
- Permission rationale
- Settings (theme; optional in v1)

## 10. Success Metrics

Since MPlay collects no analytics, success is measured by testing and, if published, store signals:

- Zero known crashes in a 50-scenario manual test pass across at least 3 device brands.
- Background playback survives 60+ minutes of screen-off on Xiaomi, Samsung and Pixel devices.
- Notification controls work on Android 12, 13, 14 and 15.
- A trimmed clip can be set as ringtone on Pixel, Samsung and Xiaomi devices (Android 10 to 15) and plays for incoming calls.
- Play Store rating of 4.3+ (if published).

## 11. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Aggressive background-kill by OEMs (Xiaomi, Oppo, Vivo) | Use a proper foreground media service; add an in-app tip on battery-optimization exemption |
| Scoped storage and permission changes across Android versions | Use MediaStore only; version-specific permission handling; test on API 26, 29, 33 and 35 |
| Missing or wrong ID3 tags | Filename fallback and "Unknown" defaults |
| Large libraries slowing the UI | Stable LazyColumn keys, background queries, cached sorted lists |
| Foreground-service rules tightening in newer Android versions | Follow current `mediaPlayback` type requirements; retest each target SDK bump |
| WRITE_SETTINGS permission denied or unavailable on some OEMs | Explain why it is needed, deep-link to the system screen, and fall back to saving the clip so the user can pick it in system sound settings |
| Trim accuracy and quality (frame boundaries, re-encoding) | Use Media3 Transformer, export AAC at 192 kbps, and show a waveform with fine-tune controls |

## 12. Milestones

| Phase | Scope |
| --- | --- |
| M1: Foundation | Project setup, permissions, MediaStore library list |
| M2: Core playback | Media3 service, notification, lock-screen and headset controls |
| M3: Player UI | Mini-player, Now Playing, seek, theme |
| M4: Modes and discovery | Shuffle, repeat, search, sort, Albums and Artists tabs |
| M5: Playlists | Room database, playlist screens, queue actions |
| M6: Trim and ringtone | Three-dot menu, trim editor with waveform, save clips via MediaStore, set ringtone, notification and alarm sounds |
| M7: Widget and hardening | Home-screen widget, format QA (FLAC, AAC, OGG, WAV), session restore, edge cases, R8, testing on real devices, signed release APK |

## 13. Future Scope (v1.1+)

Sleep timer, equalizer and bass boost, folder browsing, favorites, contact-specific ringtones, Android Auto, Play Store release, embedded lyrics (offline), playback speed, backup and restore of playlists to a local file.

## 14. Decisions

- **Formats:** MP3, FLAC, AAC/M4A, OGG and WAV are officially supported in v1 (requirement L8).
- **Distribution:** Direct APK for now. Play Store release is deferred to a later version.
- **Widget:** Home-screen widget is included in v1 (section 6.8).
- **Trim and ringtone:** Included in v1, reached from the three-dot menu on songs and Now Playing (section 6.9).