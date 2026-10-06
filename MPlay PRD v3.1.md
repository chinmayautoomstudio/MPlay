# MPlay: Product Requirements Document (v3.1)

**Features:** Metronome, Sing Along, Separation Time Notice, About MPlay **Product:** MPlay, an offline Android music player **Platform:** Android (native Kotlin, Jetpack Compose) **Version:** 3.1 **Status:** Draft

---

## 1. Overview

v3.1 adds four things to MPlay:

1. **Metronome:** an adjustable metronome that is its own section of the app and also available inside the music player, with tap tempo and automatic detection of a song's tempo.
2. **Sing Along:** a button beside the AI vocal remover that plays a song's instrumental while recording the user's voice, then saves one file with the voice mixed with the instrumental.
3. **Separation time notice:** clear messages that separating vocals and instrumental can take time, depending on the phone's hardware.
4. **About MPlay:** a screen with app, company, contact, version and privacy information.

MPlay stays fully offline. The only new permissions are for the microphone, and they are used only while the user records a sing-along.

## 2. Problem Statement

- Musicians and learners practising with their own music have no built-in way to keep time against a song or find its tempo.
- People who separate vocals want to use the instrumental right away to sing, but have to use another app to record themselves.
- Vocal separation can take several minutes on some phones, and without a clear explanation users may think the app is stuck.
- Users have nowhere in the app to see who makes it, how to contact them, or what the app does with their data.

## 3. Goals and Non-Goals

**Goals**

- A reliable, sample-accurate metronome that mixes with music and keeps running with the screen off.
- Quick tempo setting through manual entry, tap tempo and automatic detection from the current song.
- A simple sing-along flow that produces one shareable file with the voice aligned to the instrumental.
- Honest, consistent messaging about how long separation takes, ideally with an estimate based on the user's own phone.
- A clear About screen that builds trust, including a plain-language privacy note.

**Non-Goals (v3.1)**

- Live singing effects such as reverb, pitch correction or harmonies.
- Video recording or sharing directly to social apps.
- Synced lyrics or a lyrics display for sing-along.
- Beat-phase alignment of the metronome to a song's downbeat.
- Rhythm patterns, subdivisions or polyrhythms in the metronome.
- Any network feature. MPlay must not request the INTERNET permission.

## 4. Target Users

- **Primary:** Singers, instrumentalists and learners who practise with their own music.
- **Secondary:** Casual users who want to sing along to a song's instrumental, and users who want to know more about the app and who makes it.

## 5. Platform and Technical Constraints

| Area | Decision |
| --- | --- |
| Metronome audio | Own low-latency audio output (AudioTrack, low-latency mode), separate from the music player, so it mixes with music at the system level |
| Metronome timing | Beats scheduled by counting audio frames with a double-precision frames-per-beat value; no timers or handlers for beat timing |
| Metronome in background | Must keep running with the screen off; runs in a foreground service of type media playback (either the existing playback service or a small dedicated one) |
| Tempo detection | On-device and offline: decode a mono low-rate signal (about 11 kHz) of 60 to 90 seconds from the middle of the song, compute an onset envelope, estimate tempo in the 60 to 200 BPM range with octave correction and a confidence score |
| Tempo cache | Detected tempo stored per song in the local database, invalidated when the file changes |
| Voice capture | AudioRecord, mono, 44.1 kHz, 16-bit, minimal-processing source, automatic gain control and noise suppression off where possible, written to a temporary file |
| Recording service | Foreground service of type microphone, started from visible UI, with a notification that has a Stop action |
| Mixing | Offline after the take: decode the instrumental stem file, mix with the voice file using the chosen levels and sync offset, protect against clipping, encode to AAC/M4A (192 kbps, 44.1 kHz stereo) |
| Saved recordings | Music/MPlay Recordings via MediaStore, appearing in the library automatically |
| Permissions | RECORD_AUDIO (runtime, requested on first recording) and FOREGROUND_SERVICE_MICROPHONE. No INTERNET permission |
| Links | Website, email and phone links open through system intents (browser, mail app, dialer) and need no extra permission |
| Data | Database schema updated with an automatic migration; existing playlists, stems and settings are preserved |
| Version | Version name 3.1, version code incremented |

## 6. Functional Requirements

Priority: **P0** = must ship, **P1** = should ship, **P2** = nice to have.

### 6.1 Metronome

| ID | Requirement | Priority |
| --- | --- | --- |
| MT1 | A "Metronome" section in the main navigation | P0 |
| MT2 | Start and stop; BPM from 20 to 300 using +/- buttons, a slider and typed entry, shown as a large number | P0 |
| MT3 | Time signature: 1 to 12 beats per bar, with presets for 2/4, 3/4, 4/4 and 6/8 | P0 |
| MT4 | Accent on beat 1, switchable on and off | P0 |
| MT5 | At least three click sounds (for example classic click, wood block, soft beep) | P1 |
| MT6 | Independent metronome volume | P0 |
| MT7 | Visual beat indicator that flashes in time with the audio, with beat 1 highlighted | P0 |
| MT8 | Tap tempo: BPM is the average of the last 4 to 8 taps; resets after 2 seconds without a tap | P0 |
| MT9 | "Detect BPM" for the current song from the player, with progress, the result and a confidence hint, plus x2 and /2 buttons to fix half-time and double-time results | P0 |
| MT10 | Cache each song's detected BPM so repeat detection is instant; discard it if the file changes | P1 |
| MT11 | When a song has separated stems, analyze the instrumental stem, since drums are clearer there | P2 |
| MT12 | A compact metronome control on Now Playing that opens a sheet with the same controls and Detect BPM, plus a small indicator on the mini player when the metronome is running | P0 |
| MT13 | The metronome mixes with the music and never pauses or ducks it; when nothing is playing it requests audio focus normally | P0 |
| MT14 | Sample-accurate timing with no drift over long sessions; BPM changes apply on the next beat without a click or glitch | P0 |
| MT15 | Keeps running with the screen off and when the app is in the background | P0 |
| MT16 | Pauses during phone calls | P1 |
| MT17 | Remember the last BPM, time signature, accent, sound and volume | P1 |

### 6.2 Sing Along

| ID | Requirement | Priority |
| --- | --- | --- |
| SA1 | A "Sing along" button placed right beside the AI vocal remover control on Now Playing | P0 |
| SA2 | If the song has not been separated, show "Separate vocals first" with a button to start separation (with the time notice from section 6.3); do not record | P0 |
| SA3 | Ask for the microphone permission with a rationale ("Used only while you record. Nothing leaves your phone"), handle denial, and offer "Open settings" if permanently denied | P0 |
| SA4 | Recommend headphones, detect wired or Bluetooth output, and warn that without headphones the instrumental will leak into the recording | P1 |
| SA5 | Prefer the built-in or wired microphone, and warn if only a Bluetooth headset microphone is available | P1 |
| SA6 | Optional 3-2-1 count-in that uses the metronome click when the metronome is on | P2 |
| SA7 | Playback switches to the instrumental from the start of the song (or from the current position if the user chooses) and recording starts at the same moment | P0 |
| SA8 | Recording screen with a timer, a live input level meter, and Stop and Retake buttons | P0 |
| SA9 | Recording and playback stop together at the end of the song; the previous playback mode is restored afterwards; lofi mode is turned off during the session and restored afterwards | P0 |
| SA10 | Recording runs in a microphone foreground service so it survives screen lock, with a notification that has a Stop action | P0 |
| SA11 | If a call comes in, audio focus is lost, headphones are unplugged, or a sleep timer stops playback, recording stops cleanly and the user is offered to save what was recorded | P0 |
| SA12 | Review screen: play back the mixed take, adjust voice level, instrumental level and a sync offset (about -300 to +300 ms), then Save or Discard | P0 |
| SA13 | Save one stereo AAC/M4A file (192 kbps, 44.1 kHz) into Music/MPlay Recordings via MediaStore, named "Song title - Sing along" with an editable name and a suffix if the name exists | P0 |
| SA14 | Mix offline against the instrumental stem file from the recording start position, with clipping protection | P0 |
| SA15 | Capture the voice as mono 44.1 kHz 16-bit audio into a temporary file during the take | P0 |
| SA16 | Delete temporary files on discard, failure or cancel; never leave partial files in the library, even after the app is killed | P0 |
| SA17 | Saved recordings appear in the library automatically | P1 |
| SA18 | Check there is enough free storage before recording and warn if not | P1 |

### 6.3 Separation Time Notice

| ID | Requirement | Priority |
| --- | --- | --- |
| SN1 | Before any separation starts (song menu, Now Playing, multi-select), show a confirmation dialog: separating can take several minutes per song, how long depends on the phone's processor, memory and temperature, the phone may get warm and use battery, and the user can keep using MPlay meanwhile. Buttons: Start and Cancel, with a "Don't show again" option for this dialog only | P0 |
| SN2 | A short version, "This may take a while, depending on your phone", is always visible on the progress sheet and the progress notification | P0 |
| SN3 | If a separation has finished on this phone before, show an estimate such as "About N minutes for this song on your phone", based on a rolling average of measured speed; show no number when there is no history | P1 |
| SN4 | While running, keep showing the time-remaining estimate and the "phone is warm, so processing is slower" message when cooling | P0 |
| SN5 | Use one shared notice component and one set of texts everywhere | P1 |

### 6.4 About MPlay

| ID | Requirement | Priority |
| --- | --- | --- |
| AB1 | An About screen reachable from Settings (and from the top bar menu if one exists) | P0 |
| AB2 | Show the MPlay logo and the Autoom Studio logo | P0 |
| AB3 | Text: MPlay is an offline music player made by Autoom Studio, an AI automation and IT solutions company based in India | P0 |
| AB4 | Website autoomstudio.com opens in the browser | P0 |
| AB5 | Contact details (email and phone, supplied by Autoom Studio): email opens the mail app, phone opens the dialer, and long-press copies the value | P0 |
| AB6 | Show the app version and build number, read at runtime (for example "Version 3.1 (build 4)") | P0 |
| AB7 | Privacy note: MPlay works fully offline, with no internet access, no accounts, no ads and no analytics; music and recordings stay on the phone; the microphone is used only when recording a sing-along | P0 |
| AB8 | If no browser, mail app or dialer is available, show a message instead of crashing | P1 |

## 7. Non-Functional Requirements

- **Timing accuracy:** Metronome drift under 10 ms over a 10-minute session; beat-indicator flashes within one frame of the audible click.
- **Performance:** Tempo detection finishes in about 5 seconds or less for a typical song on a mid-range phone (to be confirmed on device); the UI stays responsive during detection and recording.
- **Battery:** The metronome and recording use no wake locks beyond what the foreground service needs, and nothing runs in the background when stopped.
- **Reliability:** No crashes on permission denial, interruptions, low storage, missing stems or deleted source files; no partial files after forced app kills.
- **Privacy:** No network access; the microphone is used only during a recording; temporary voice files are stored in app-private cache and excluded from backup; recordings never leave the device unless the user shares them.
- **Compatibility:** Android 8.0 (API 26) and above, following the microphone foreground service rules of Android 14 and 15.
- **Accessibility:** All new controls have content descriptions, large touch targets, support for font scaling, and the beat indicator is not the only way to follow the metronome (sound plus visual).

## 8. User Flows

1. **Use the metronome:** Metronome tab → set BPM (slider, buttons, typing or tap tempo) → pick time signature and sound → Start.
2. **Metronome while listening:** Now Playing → metronome button → Detect BPM → BPM is filled in (adjust with x2 or /2 if needed) → Start; music keeps playing, the click mixes in.
3. **Sing along:** Now Playing → Sing along → grant microphone permission (first time) → read headphone advice → optional count-in → record while the instrumental plays → Stop → review, adjust levels and offset → name and Save.
4. **Sing along on an unseparated song:** Sing along → "Separate vocals first" → time notice dialog → Start → return when separation finishes.
5. **Separation notice:** Separate vocals → dialog explains it can take time and may show an estimate → Start → progress sheet and notification repeat the notice.
6. **About:** Settings → About MPlay → tap website, email or phone.

## 9. Screens

- Metronome section
- Metronome sheet on Now Playing and mini-player indicator
- BPM detection progress and result
- Sing-along pre-recording sheet (permission, headphones, levels, count-in)
- Recording screen (timer, level meter, Stop, Retake)
- Recording review (playback, levels, sync offset, Save and Discard)
- Separation confirmation dialog and estimate
- About MPlay

## 10. Success Metrics

MPlay collects no analytics, so success is measured by testing:

- Metronome drift stays under 10 ms in a 10-minute test, including with the screen off and music playing.
- Tempo detection is within plus or minus 2 BPM on at least 90% of a test set of songs with clear beats, and half/double correction fixes the rest.
- With wired headphones, the voice in a saved sing-along lines up with the instrumental within about 20 ms after the sync offset is set.
- 100% of 20 forced app kills during recording leave no partial files in the library.
- The separation notice appears at all entry points and in the notification.
- The About screen links all open correctly on test devices.

## 11. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Bluetooth headphones add delay, so the voice is out of sync | Recommend wired headphones, show a warning, and provide the sync offset control |
| Without headphones the instrumental leaks into the microphone | Detect output, warn clearly, and recommend headphones |
| Android 14 and 15 restrict microphone foreground services | Start the service from visible UI, request the right permission, and test on those versions |
| Tempo detection is wrong on songs with changing tempo or weak beats | Show a confidence hint, offer x2 and /2, and allow manual and tap tempo |
| The metronome is killed in the background by phone makers | Use a media-playback foreground service and test on several brands |
| The metronome fights with music for audio focus | Do not request focus while music plays; pause during calls |
| Lofi mode changes speed and breaks sync | Turn lofi off during sing-along and restore it afterwards |
| Recording runs out of storage | Check free space first and handle write failures cleanly |
| The separation estimate is wrong (heat, other apps) | Label it as approximate and update the time remaining while running |
| Users record and share songs they do not own | Include a short personal-use note in the first-run sing-along sheet |

## 12. Milestones

| Phase | Scope |
| --- | --- |
| M1: Quick wins | About MPlay screen; separation time notice, estimate and shared notice component |
| M2: Metronome engine | Low-latency engine, sample-accurate scheduling, background service, Metronome section, settings |
| M3: Metronome in the player | Now Playing sheet, mini-player indicator, tap tempo, BPM detection and cache |
| M4: Sing along | Pre-recording flow, microphone service, recording, offline mixing, review and save |
| M5: Hardening | Interruption handling, forced-kill tests, timing tests, multi-device testing, Android 14 and 15 checks |

## 13. Future Scope

Metronome subdivisions, rhythm patterns and presets; aligning the metronome to a song's downbeat; synced lyrics for sing-along; voice effects such as reverb and pitch correction; sharing recordings directly from the app; practice logs and loop sections for a song.

## 14. Decisions

- **Sing-along output:** One file with the voice mixed with the instrumental.
- **Metronome tempo:** Manual BPM, tap tempo and automatic detection of the song's BPM are all included.
- **About page contents:** Company name, logo and website, contact details (email and phone), app version and build number, and a privacy note.
- **Mixing method:** The voice is mixed offline against the instrumental stem file, not recorded from the speaker, which keeps the instrumental clean and allows a sync offset.
- **Lofi during sing-along:** Lofi mode is turned off during sing-along and restored afterwards.