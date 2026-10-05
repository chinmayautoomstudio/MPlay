# MPlay: Product Requirements Document (v3)

**Feature:** AI Vocal Separation **Product:** MPlay, an offline Android music player **Platform:** Android (native Kotlin, Jetpack Compose) **Version:** 3.0 **Status:** Draft

---

## 1. Overview

v3 adds **on-device AI vocal separation** to MPlay. The user picks a song, MPlay splits it into vocals and instrumental using the HT-Demucs model running entirely on the phone, and the user can then play the original, the instrumental (for singing along) or the vocals only. Everything stays offline: no network access, no accounts, no uploads.

Because on-device Demucs is demanding on memory, speed and battery, v3 begins with a **feasibility spike and go/no-go gate** (section 7). The full feature is built only if the spike passes.

## 2. Problem Statement

Users want an instrumental version of their own songs for singing along or practice, and a vocals-only version for study, without uploading music to a website or paying for a cloud service. Existing options are online tools that need an internet connection and upload the user's files. A private, offline option does not exist in most music players.

## 3. Goals and Non-Goals

**Goals**

- Separate any local song into vocals and instrumental fully on the device.
- Process each song once, cache the result, and make switching between Original, Instrumental and Vocals only instant.
- Keep the app responsive and the phone safe (battery, memory, heat) during processing.
- Design the pipeline so additional stems can be exposed in the future without redesign.

**Non-Goals (v3)**

- Cloud or online separation. All processing runs on the device.
- Real-time AI separation while a song is playing. Songs are processed ahead of time and cached.
- Exposing drums, bass, other, guitar or piano as separate outputs (they are future scope).
- Stem mixing, volume sliders per stem, or any audio editing of the separated tracks.
- Support for devices that fail the eligibility check.

## 4. Target Users

- **Primary:** People who want to sing along to their own music (karaoke) or practice with instrumental versions.
- **Secondary:** Musicians and learners who want to hear vocals or instruments in isolation.
- **Device profile:** Owners of recent mid-range and flagship Android phones with enough RAM and storage.

## 5. Platform and Technical Constraints

| Area | Decision |
| --- | --- |
| Model | HT-Demucs (single 4-stem model) exported to ONNX. The fp16-weights file is about 166 MB; the fp32 file is about 316 MB. MIT-licensed; confirm the license of the exact weights used |
| Inference runtime | ONNX Runtime Mobile. CPU is the baseline; GPU and other accelerators are evaluated in the spike |
| Audio pipeline | Decode any input (any sample rate; mono, stereo or multichannel), convert to 44.1 kHz stereo float audio, normalize using whole-track statistics with a two-pass decode so long files do not need to fit in memory, split into segments of about 7.8 seconds with overlap, run the model, stitch with overlap-add and stream the result to the encoder. STFT and iSTFT are done outside the model, in Kotlin first and moved to native code (C++ via JNI) only if profiling shows a need |
| Stems | The model produces drums, bass, other and vocals. v3 stores vocals and instrumental (drums + bass + other) only |
| Output format | AAC/M4A at about 192 kbps, stored in app-private storage |
| Processing service | Background job (WorkManager) running in a foreground service with a progress notification and a partial wake lock. Android 15 and later: media-processing service type, which has a 6-hour daily limit (handle the timeout by pausing and rescheduling). Android 10 to 14: data-sync service type. Android 8 and 9: no service type is needed. Runs in a separate process from playback, using WorkManager's multiprocess support (a remote worker in its own process); WorkManager itself still initializes only in the main process |
| Packaging | Separate "MPlay AI" build with the model bundled in the APK. The standard build does not include AI separation. The model file is stored uncompressed in the APK and memory-mapped, so it is not copied (which would double its storage) or loaded into a large in-memory array. If the app is ever published on Google Play, the model needs an install-time asset pack, because app plus model is close to the base download limit |
| App ID and signing | Debug and spike builds of MPlay AI use a separate app ID (".ai" suffix) so they install side by side with standard MPlay. Release builds use the same app ID and the same signing key as standard MPlay, so MPlay AI upgrades in place and keeps playlists and settings. Both release builds use the same version code (Android blocks installing a lower version code over a higher one), and both share an identical database schema. The standard build keeps playing cached stems and can delete them, but cannot create new ones |
| Build size | MPlay AI build is expected to be roughly 180 to 340 MB depending on the model variant |
| Device requirements | arm64-v8a. Minimum RAM and free storage to be fixed after the spike (starting assumption: 6 GB RAM, 1 GB free storage) |
| Network | None. The INTERNET permission must not be declared |
| Permissions | FOREGROUND_SERVICE plus the type permission: FOREGROUND_SERVICE_MEDIA_PROCESSING (Android 15 and later) or FOREGROUND_SERVICE_DATA_SYNC (Android 10 to 14). Android 8 and 9 need only FOREGROUND_SERVICE. Confirm current rules during the spike |

## 6. Functional Requirements

Priority: **P0** = must ship, **P1** = should ship, **P2** = nice to have.

### 6.1 Entry Points and Eligibility

| ID | Requirement | Priority |
| --- | --- | --- |
| AI1 | A "Separate vocals" action in the three-dot menu on song rows and on the Now Playing screen | P0 |
| AI2 | Before the first run, check device eligibility (architecture, RAM, free storage). If the device is unsupported, explain why and hide the feature | P0 |
| AI3 | Before processing, check there is enough free storage (about 11.5 MB per 4 minutes of audio for the two stems at 192 kbps, plus temporary working space) and warn if not | P0 |
| AI4 | First-run notice explaining that processing takes a few minutes per song, uses battery, and may leave faint traces of the removed part | P1 |

### 6.2 Processing

| ID | Requirement | Priority |
| --- | --- | --- |
| AI5 | Process each song once in a foreground service with progress percentage, estimated time remaining and a cancel button | P0 |
| AI6 | Process in chunks of about 7.8 seconds with overlap to keep memory bounded; target peak RAM under 1.5 GB (confirm in the spike) | P0 |
| AI7 | Run the model once to get all four stems internally. Store only vocals and instrumental (drums + bass + other) | P0 |
| AI8 | The original audio file is never modified | P0 |
| AI9 | If the app is killed or the phone restarts mid-process, resume or restart safely with no corrupt partial files | P0 |
| AI10 | Queue several songs and process them one at a time | P1 |
| AI11 | Option "Only process while charging" (off by default) | P1 |
| AI12 | Pause automatically when the battery is low or the device is overheating, and resume when safe. Use the system thermal status on Android 10 and later, and battery temperature on Android 8 and 9 | P1 |
| AI13 | Show a clear error and keep the app stable if processing fails (corrupt file, unsupported format, out of memory, low storage) | P0 |
| AI26 | Handle long files without holding the whole decoded song in memory: measure whole-track levels in a first pass, then stream segments in a second pass (or enforce a maximum song length, to be set after the spike) | P0 |
| AI27 | Accept any input sample rate and channel layout (mono, stereo, multichannel) and convert to 44.1 kHz stereo before the model | P0 |
| AI28 | Run separation in a separate process from playback, so a memory kill during processing never stops music (confirm in the spike) | P0 |
| AI29 | When the system ends the processing service (time limit, restart or kill), pause and reschedule the job. Start jobs from the app or a scheduled job, never from a boot broadcast | P0 |

### 6.3 Output and Cache

| ID | Requirement | Priority |
| --- | --- | --- |
| AI14 | Save results as AAC/M4A (about 192 kbps) in app-private storage, linked to the source song | P0 |
| AI15 | If the source file is deleted, moved or changed, detect it and remove or invalidate the cached result | P1 |
| AI16 | Optionally export vocals or instrumental to Music/MPlay Stems via MediaStore | P1 |
| AI17 | Import a model file from storage instead of bundling it, to allow a smaller build | P2 |

### 6.4 Playback Modes

| ID | Requirement | Priority |
| --- | --- | --- |
| AI18 | For a processed song, Now Playing offers three modes: Original, Instrumental and Vocals only | P0 |
| AI19 | Switching modes keeps the playback position. A brief pause is acceptable, but the switch completes within 500 ms | P0 |
| AI20 | The selected mode applies to the notification controls, lock screen and background playback | P0 |
| AI21 | A badge on processed songs in the library shows which songs are ready | P1 |
| AI22 | Existing playback features (sleep timer, lofi mode, queue, shuffle and repeat) work with the Instrumental and Vocals only modes | P1 |

### 6.5 Settings and Management

| ID | Requirement | Priority |
| --- | --- | --- |
| AI23 | Settings shows total cache size, with delete per song and delete all | P0 |
| AI24 | Settings toggles for "Only process while charging" and automatic pause on low battery | P1 |
| AI25 | A "Processing queue" screen showing current, waiting and completed jobs | P1 |

## 7. Feasibility Spike and Go/No-Go Gate

Before building the full feature, run a short spike: a throwaway Android app that runs the fp16 HT-Demucs model on a 30-second clip and then on a full 4-minute song.

**Test on at least three real devices:** one budget, one mid-range and one flagship.

**Measure:** processing time (real-time factor), peak RAM, battery drop, temperature and slowdown over a 5-minute run, and listening quality.

| Gate | Proposed threshold (adjust after seeing real numbers) |
| --- | --- |
| Speed | A 4-minute song finishes in about 12 minutes or less on the mid-range device (roughly 3 times real time) |
| Memory | Peak RAM under about 1.5 GB with no crash or app kill |
| Heat | No thermal shutdown during a 5-minute run; slowdown is acceptable |
| Quality | In a listening test of about 10 varied songs, most testers judge the instrumental acceptable for singing along |

**Also confirm in the spike:**

- CPU thread count (2, 4 and 6), chunk overlap (25% and 10%), and whether GPU or NNAPI helps
- That switching playback modes completes within 500 ms on real devices
- The minimum RAM to allow, and whether the separate processing process is needed to protect playback
- That the bundled model can be memory-mapped without being copied
- That output matches a reference run of the original Demucs within a tolerance

**Decision**

- **Go:** build the full feature for devices that pass the eligibility check.
- **Partial:** limit the feature to higher-end devices, reduce chunk overlap, or try lower-precision or smaller models (for example an MDX-Net-class vocal model).
- **No-go:** pause the feature and revisit when models or hardware improve.

## 8. Non-Functional Requirements

- **Performance:** The UI stays responsive during processing; switching playback modes takes under 500 ms; the app launches as fast as before the feature was added.
- **Memory:** Peak RAM during processing stays within the limit confirmed in the spike; processing runs in a separate process, so a memory kill during separation never stops MPlay playback.
- **Battery and heat:** Processing pauses safely on low battery or overheating; charging-only mode is available.
- **Reliability:** No crashes on corrupt files, low storage, cancelled jobs or app restarts; no partial or corrupt cached files.
- **Privacy:** No network access; no audio leaves the device; no analytics.
- **Storage:** Cached results use about 11.5 MB for a 4-minute song (two stems at 192 kbps, about 5.8 MB each), or roughly 3 MB per minute of music. A lower bitrate for the vocals stem can reduce this. Results can be cleared easily.
- **Compatibility:** arm64 devices on Android 8.0 (API 26) and above that pass the eligibility check. Thermal status listening needs Android 10, so Android 8 and 9 use battery temperature instead.
- **Accessibility:** Progress, status and mode controls have content descriptions, large touch targets and font scaling support.

## 9. User Flows

1. **Separate a song:** Three-dot menu → Separate vocals → first-run notice → progress notification → badge appears on the song.
2. **Play the instrumental:** Now Playing → choose Instrumental → music continues from the same position after a brief pause (under 500 ms).
3. **Vocals only:** Now Playing → choose Vocals only → playback continues from the same position.
4. **Queue several songs:** Select multiple songs → Separate vocals → they process one after another → progress shown in the processing queue screen.
5. **Cancel or pause:** Tap Cancel in the notification, or processing pauses automatically on low battery or overheating and resumes when safe.
6. **Free up space:** Settings → Separation cache → delete one song or all.
7. **Unsupported device:** The option is hidden, with an explanation in Settings.

## 10. Screens

- Three-dot menu item "Separate vocals"
- First-run notice
- Separation progress sheet and notification
- Processing queue
- Now Playing mode selector (Original, Instrumental, Vocals only)
- Library badge for processed songs
- Separation settings and cache management
- Unsupported-device explanation

## 11. Success Metrics

Since MPlay collects no analytics, success is measured by testing:

- The spike produces measured time, RAM and heat results on at least three real devices before the full build begins.
- On eligible devices, at least 95% of separation jobs complete without a crash or app kill in a test of 100 songs.
- A 4-minute song completes within the speed gate on the mid-range reference device.
- Playback mode switching takes under 500 ms on all test devices.
- In a listening test, most testers rate the instrumental acceptable for singing along.
- No corrupt cache files after 20 forced app kills during processing.

## 12. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Separation is too slow on mid-range phones | Run the spike first; process once and cache; limit to eligible devices; try smaller models or lower overlap |
| High RAM use gets the app killed | Chunked processing, RAM-based eligibility check, foreground service, test on 4 GB and 6 GB devices |
| The phone overheats and slows down during long jobs | Thermal monitoring with automatic pause, optional charging-only mode |
| Separation quality is not good enough (faint traces, watery sound) | Set expectations in the first-run notice; evaluate quality in the spike; consider a better model later |
| Large APK and storage use | Separate MPlay AI build, fp16 weights, AAC cache with easy cleanup |
| Porting the audio pipeline (STFT, overlap-add) introduces bugs | Port from a reference implementation and compare outputs against it in tests |
| Model license or weights terms limit use | Confirm the license of the exact weights before release |
| Android restricts long background work | Use a foreground service with the correct type; confirm requirements for newer Android versions during the spike |
| Users separate songs they do not own or share the results | Include a short personal-use note in the first-run notice |
| Long songs or mixes (for example 60 minutes) exhaust memory | Two-pass decode with streamed segments, or a maximum song length |
| The system's memory killer stops playback together with processing | Run separation in a separate process |
| The model is copied out of the APK, doubling its storage | Store it uncompressed and memory-map it |
| Service time limits or restarts interrupt a long job | Use the right service type per Android version, handle the timeout, and reschedule from the app or a scheduled job |

## 13. Milestones

| Phase | Scope |
| --- | --- |
| M1: Feasibility spike | Throwaway test app, measurements on three devices, go/no-go decision |
| M2: Audio pipeline | Decode, resample, STFT/iSTFT, chunking, overlap-add, AAC encoding, tests against a reference |
| M3: Processing service | Foreground service, progress, cancel, resume after kill, cache and file linking |
| M4: Playback modes and UI | Mode selector, instant switching, badges, notification and lock-screen support |
| M5: Management and safety | Queue, charging-only mode, battery and thermal pause, cache settings, eligibility check |
| M6: Hardening and release | Device testing, edge cases, MPlay AI build packaging and release |

## 14. Future Scope

Expose drums, bass, other, guitar and piano stems (6-stem model), a stem mixer with per-stem volume, export of any stem, faster or GPU/NPU-accelerated separation, smaller or better models, and background processing of newly added songs.

## 15. Decisions

- **Model:** HT-Demucs single 4-stem model, ONNX with fp16 weights, run on-device with ONNX Runtime Mobile.
- **Outputs in v3:** Vocals and instrumental only; other stems are future scope.
- **Processing approach:** Process once, cache the result, play the saved file. No real-time separation.
- **Packaging:** Separate MPlay AI build with the model bundled; the standard build stays small.
- **Gate:** A feasibility spike on real phones decides whether the full feature is built (section 7).
- **Mode switching:** A brief pause under 500 ms is accepted when switching between Original, Instrumental and Vocals only.
- **Cache size:** About 11.5 MB per 4-minute song at 192 kbps; a lower vocals bitrate is an option if storage matters.
- **App ID:** Debug and spike builds use an ".ai" app ID suffix; release builds share the standard app ID and signing key and upgrade in place.
- **Playback mode scope:** The selected mode is session-wide. It stays on for the next processed songs, and songs that have not been processed play the original.
- **Interrupted jobs:** A job interrupted by a kill, restart or time limit restarts from the beginning of that song; resuming per segment is future scope.