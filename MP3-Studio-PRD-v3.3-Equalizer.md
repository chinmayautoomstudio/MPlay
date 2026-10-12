Markdown · MP3-Studio-PRD-v3.3-Equalizer.md

# MP3 Studio v3.3: Equalizer PRD

**Product:** MP3 Studio, Android (Kotlin, Jetpack Compose, Media3) **Scope:** An equalizer for the music player: a 7-band equalizer with genre presets, a preamp, and three one-tap sound options (Bass Boost, Virtualizer, Loudness) **Status:** Draft, aligned to the UI concept (dark purple Equalizer screen) **Builds on:** The existing player, Lofi mode, AI Vocal Separation, Sing Along, Settings and Now Playing. Nothing here changes accounts, plans or payments.

---

## 1. Overview

MP3 Studio has no equalizer today, which is a basic feature in a music app. This release adds one that works for every song in the app, fully on the device with no internet needed.

The screen follows the UI concept:

- **On/Off switch** in the header.
- **Preset selector** (a drop-down showing the current preset) and a **grid of preset tiles**: Custom, Normal, Pop, Rock, Hip Hop, Jazz, Classical, Electronic, Vocal and Bass Boost.
- **Seven band sliders**, from bass to treble, at 60 Hz, 150 Hz, 400 Hz, 1 kHz, 2.5 kHz, 6 kHz and 14 kHz, each from −12 dB to +12 dB.
- **Preamp** slider from −12 dB to +12 dB.
- **Three switches:** Bass Boost, Virtualizer and Loudness.

The user tunes bass, mids and highs by moving the sliders, or jumps straight to a genre preset.

## 2. Goals and Non-Goals

**Goals**

- Change the sound of any song, in any playback mode (Original, Vocals only, Instrumental only), from a screen reachable in two taps while music plays.
- Presets that sound good immediately.
- Changes heard instantly, with no clicks, pops or gaps.
- No distortion when boosting loud songs.
- The same sound on every phone brand.
- The setting is remembered across songs, restarts and reboots.

**Non-Goals (this release)**

- A different setting per song, per playlist or per output device (see section 13).
- Applying the equalizer to saved clips, ringtones, separated stems on disk or Sing Along recordings (see 7.3).
- A real-time spectrum display.
- Reverb, surround or headphone-model correction curves.
- Separate Simple and Advanced modes. The seven sliders are the single control, with Bass, Mids and Highs shown as group labels (see 5.2).

## 3. Key Decisions

| Area | Decision |
| --- | --- |
| Engine | **Our own equalizer inside the player's audio chain**, not Android's `android.media.audiofx` effects. System effects differ by phone brand (band counts, frequencies, gain limits), can be missing or overridden by vendors, and depend on an audio session that changes when the player reconfigures. The app already has an audio processor for Lofi mode and a tested biquad filter, so the same approach gives identical sound on every phone. This applies to Bass Boost, Virtualizer and Loudness as well |
| Bands | 7 bands: 60 Hz (low shelf), 150 Hz, 400 Hz, 1 kHz, 2.5 kHz, 6 kHz (peaking), 14 kHz (high shelf) |
| Gain range | ±12 dB per band and for the preamp, in 0.5 dB steps |
| Presets | The tiles shown in the concept. **Normal** means all bands at 0 dB. **Custom** holds the user's own hand-tuned curve (see 5.3) |
| Plan | **Free for everyone.** It is a basic feature, so it is not locked behind Pro (change in `EntitlementPolicy` if you decide otherwise) |
| Headroom | Automatic headroom lowers the level by the largest total boost, and a soft limiter catches remaining peaks. The Preamp slider is the user's own extra gain, on top of this |
| Scope of the setting | One global setting for all songs |
| Storage | Local only (DataStore). Nothing is sent to the server |
| Permissions | None added |

## 4. Where the Equalizer Lives

- **Now Playing:** an **Equalizer** button next to the existing player actions (Lofi, AI Vocal Remover, Sing Along). It opens the Equalizer screen. The button shows an "on" state when the equalizer is active and not Normal.
- **Settings:** an **Equalizer** row opens the same screen.
- **Mini player and notification:** no equalizer controls.

The screen has a back arrow and keeps playing music while open.

## 5. User Experience

### 5.1 Screen layout (top to bottom, as in the concept)

1. **Header:** back arrow, "Equalizer" and the **On/Off switch**. Off passes the audio through unchanged and greys out the rest of the screen (sliders and switches stay visible but disabled). Switching is instant and gapless, so users can compare.
2. **Preset selector:** a drop-down showing the current preset name. It lists the built-in presets, then the user's saved presets, and has **Save current as preset…**.
3. **Band sliders panel:** seven vertical sliders on a −12 dB / 0 dB / +12 dB scale, labelled 60, 150, 400, 1K, 2.5K, 6K and 14K. A live frequency curve can sit behind the sliders (P1).
4. **Preset tile grid:** two rows of five tiles with icons: Custom, Normal, Pop, Rock, Hip Hop, Jazz, Classical, Electronic, Vocal and Bass Boost. The active tile is highlighted.
5. **Preamp:** a horizontal slider from −12 dB to +12 dB with its value shown (for example "0 dB").
6. **Switches:** Bass Boost, Virtualizer, Loudness.

### 5.2 Bass, mids and highs

The sliders are grouped by sound range, shown as small labels above or below the frequencies so the user knows where to go:

| Group | Bands |
| --- | --- |
| Bass | 60 Hz, 150 Hz |
| Mids | 400 Hz, 1 kHz, 2.5 kHz |
| Highs | 6 kHz, 14 kHz |

Tapping a group label does nothing special in this release (P2: dragging a group label moves the whole group).

### 5.3 Presets and Custom

Built-in presets (gains in dB for 60 / 150 / 400 / 1K / 2.5K / 6K / 14K Hz). These are starting values, to be tuned by ear (section 11):

| Preset | 60 | 150 | 400 | 1K | 2.5K | 6K | 14K | Intent |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Normal | 0 | 0 | 0 | 0 | 0 | 0 | 0 | No change |
| Pop | −1 | +2 | +3 | +4 | +2 | 0 | −1 | Forward vocals, tidy low end |
| Rock | +5 | +3 | −1 | −1 | +3 | +4 | +5 | Punchy drums and guitars |
| Hip Hop | +6 | +5 | +1 | −1 | +1 | +2 | +3 | Deep bass, crisp hats |
| Jazz | +3 | +2 | +1 | −1 | +1 | +3 | +4 | Warm and open |
| Classical | +4 | +3 | 0 | −2 | 0 | +3 | +4 | Natural and wide |
| Electronic | +5 | +4 | +1 | 0 | +2 | +4 | +5 | Strong lows and sparkle |
| Vocal | −3 | −2 | +1 | +3 | +4 | +2 | 0 | Voices stand out |
| Bass Boost | +7 | +6 | +3 | 0 | 0 | 0 | 0 | More low end |

**How Custom works**

- **Custom** is the user's own hand-tuned curve. The first time, it starts as Normal.
- Selecting a built-in preset moves the sliders to it with a short animation, with no gap in the sound.
- If the user then moves any slider, the tile switches to **Custom**, and the current curve becomes the saved Custom curve.
- Tapping **Custom** later brings back the last hand-tuned curve.
- **Reset** (in the drop-down menu) sets all bands, the preamp and the three switches back to defaults.

**Saved presets (P1):** "Save current as preset…" in the drop-down stores the current seven band values and the preamp under a name (up to 24 characters, no blanks or duplicates), up to 10 presets, with rename and delete. Built-in presets cannot be changed. The three switches are not part of a preset.

**Bass Boost preset and Bass Boost switch:** the **preset** moves the sliders. The **switch** adds an extra low-end boost on top of whatever the sliders are doing (5.4). They can be used together.

### 5.4 The three switches

| Switch | What it does |
| --- | --- |
| Bass Boost | Adds a fixed low-shelf boost of about +5 dB around 90 Hz for extra weight, on top of the sliders |
| Virtualizer | Widens the stereo image for a roomier, "surround-like" sound. It widens the stereo sides only above about 200 Hz, so bass stays centred. It has no effect on mono songs. It is meant mainly for headphones |
| Loudness | A fixed equal-loudness curve: a gentle lift at the low end and at the top (about +4 dB low shelf near 100 Hz, about +3 dB high shelf near 8 kHz) so music sounds fuller at low volume |

All three are off by default, work only while the equalizer is on, and are remembered. Their strengths are fixed in this release (strength sliders are a later idea). Headroom (6.2, EN6) is calculated with them included.

### 5.5 Opening the app and changing songs

- The state is restored before the first song plays, so there is never a moment of unequalized sound at the start.
- Changing songs, skipping, seeking, pausing, speed or pitch changes, and switching between Original, Vocals and Instrumental never reset the equalizer.

## 6. Functional Requirements

Priority: **P0** = must ship, **P1** = should ship, **P2** = nice to have.

### 6.1 Controls and presets

| ID | Requirement | Priority |
| --- | --- | --- |
| EQ1 | An Equalizer button on Now Playing and an Equalizer row in Settings open the screen in 5.1 | P0 |
| EQ2 | The On/Off switch bypasses everything: bands, preamp and the three switches. Switching is instant, with no click or gap. Controls are visibly disabled while off | P0 |
| EQ3 | Seven band sliders (60 Hz to 14 kHz), −12 to +12 dB in 0.5 dB steps, each announcing its frequency and value | P0 |
| EQ4 | Bass, Mids and Highs group labels over the sliders as in 5.2 | P1 |
| EQ5 | The preset tile grid with the nine built-in presets and Custom, one tap to apply, active tile highlighted | P0 |
| EQ6 | The preset drop-down mirrors the selected tile, lists built-in and saved presets, and contains Save current as preset and Reset | P0 |
| EQ7 | Custom behaviour as in 5.3: moving a slider switches to Custom and stores the curve; tapping Custom restores it | P0 |
| EQ8 | Preamp slider from −12 to +12 dB with its value shown | P0 |
| EQ9 | Bass Boost, Virtualizer and Loudness switches as in 5.4 | P0 |
| EQ10 | Reset restores Normal, Custom to Normal, preamp 0 dB, and all three switches off | P0 |
| EQ11 | Save, rename and delete up to 10 saved presets, with name validation | P1 |
| EQ12 | A live frequency curve behind the sliders, updating as they move | P1 |
| EQ13 | Double-tap a slider to return it to the current preset's value | P2 |

### 6.2 Audio engine

| ID | Requirement | Priority |
| --- | --- | --- |
| EN1 | An equalizer audio processor sits in the player's audio chain with the Lofi processor, so it affects all playback, including Vocals only and Instrumental only | P0 |
| EN2 | Chain order: Lofi first, then the equalizer, then Media3's own speed and pitch processing. Lofi mode and the equalizer can be used together | P0 |
| EN3 | Inside the equalizer, the order is: seven-band filters, Bass Boost shelf, Loudness shelves, Virtualizer, Preamp, then limiter | P0 |
| EN4 | Works with 16-bit and float PCM, mono and stereo, and any sample rate Media3 delivers (for example 44.1, 48 and 96 kHz). Filter coefficients are recalculated when the format changes. Frequencies above 45% of the sample rate are clamped to keep filters stable | P0 |
| EN5 | Each channel has its own filter state, with identical filters on both channels so the stereo image does not shift (except what Virtualizer intentionally changes) | P0 |
| EN6 | **Automatic headroom:** a gain reduction equal to the largest positive combined boost from bands, Bass Boost and Loudness, so boosts do not clip | P0 |
| EN7 | A soft limiter near 0 dBFS removes remaining clipping from overlapping boosts, a high Preamp setting or very loud tracks. It does not change the sound below its threshold | P0 |
| EN8 | No zipper noise or clicks: band gains, preamp and switches change smoothly over about 20 ms. Coefficient updates are safe across threads | P0 |
| EN9 | When the equalizer is off, or all bands are 0 dB, preamp is 0 dB and all switches are off, the processor passes audio through untouched and uses no extra processing | P0 |
| EN10 | Changing any control does not flush or reconfigure the audio sink and does not interrupt playback | P0 |
| EN11 | The Virtualizer keeps mono compatibility: a mono sum of the output does not lose bass or change level noticeably, and mono sources pass through unchanged | P0 |
| EN12 | Extra processing cost is small enough that screen-off playback does not noticeably raise battery use, and there are no glitches at 2× speed or during a separation job | P0 |

### 6.3 Persistence and state

| ID | Requirement | Priority |
| --- | --- | --- |
| ST1 | Store in DataStore: on/off, the seven gains, selected preset (or Custom), the Custom curve, preamp, the three switches and saved presets | P0 |
| ST2 | Load the saved state in the playback service before playback starts, not only in the UI, so the sound is correct when the app starts from a notification, headset button or widget | P0 |
| ST3 | The service and the UI share one source of truth, so a change on screen is heard immediately and is the same after reopening the app | P0 |
| ST4 | A damaged or missing state falls back to Normal with everything off, without a crash | P0 |
| ST5 | Backup: include or exclude the equalizer settings consistently with the app's other settings (follow the repo's existing backup rules) | P2 |

### 6.4 Interaction with other features

| ID | Requirement | Priority |
| --- | --- | --- |
| IN1 | Lofi mode and the equalizer work together; the screen shows a small "Lofi mode is on" note when relevant | P0 |
| IN2 | Speed and pitch changes (including Lofi's speed change) do not change the equalizer's frequency response | P0 |
| IN3 | The Trim and ringtone preview, Sing Along recording and saved clips are not equalized (7.3) | P0 |
| IN4 | Another app taking audio focus (a call, navigation voice) does not affect the state; it resumes unchanged | P0 |
| IN5 | The metronome click is not equalized | P1 |
| IN6 | Works the same for Free, Trial and Pro, and signed-in users | P0 |

## 7. Engine Details

### 7.1 Filters

- 60 Hz: low shelf. 150 Hz to 6 kHz: peaking filters with a Q of about 1.1 (the bands are about 1.3 octaves apart; tune by ear). 14 kHz: high shelf.
- Bass Boost and Loudness use additional shelf filters.
- All filters are biquads (RBJ cookbook), run in float in a cascade, extending the existing `Biquad` class with shelf types.
- 16-bit input is converted to float, processed, and clipped back safely.

### 7.2 Virtualizer

- The stereo signal is split into mid (sum) and side (difference) parts. The side part above about 200 Hz is raised by a fixed amount (starting at about +4 dB, tuned by ear) and the mid part is kept as is, then recombined.
- It never adds delay or reverb, so it cannot add echo or blur.
- Mono and near-mono songs are unchanged.

### 7.3 Where it is not applied

The equalizer is a **listening** setting. It is not applied to:

- Clips and ringtones saved from the Trim editor
- Separated stem files stored on the device
- Sing Along recordings

This keeps saved files identical to their sources. "Apply equalizer to saved clips" can be added as an option later.

## 8. Non-Functional Requirements

- **Sound quality:** With the equalizer off or Normal, output is bit-identical to the input. With it on, the response at each band centre is within ±0.5 dB of the setting, and the noise floor does not rise.
- **Latency:** No audible added latency. Changes are heard within about 50 ms.
- **Performance:** Measure on a low-end phone (for example 3 GB RAM) at 2× speed with the screen off, and compare CPU use with the equalizer off.
- **Reliability:** No crash or silence on unusual formats (mono, 96 kHz FLAC) or when the audio route changes (Bluetooth connects or disconnects, headphones unplugged).
- **Accessibility:** Sliders have content descriptions, announce their frequency and value in dB, and can be changed in steps with accessibility actions, not only by dragging. Touch targets are at least 48 dp. Preset selection and on/off state are not shown by colour alone. Text scales with the system font size.
- **Design:** Follows the concept (dark purple theme, vertical sliders with glowing handles, icon tiles); also works in the app's light theme if the app has one, with enough contrast for the sliders and scale labels.
- **Offline:** Fully offline.
- **Compatibility:** Android 8.0 and above.
- **APK size:** Negligible. No new libraries or assets beyond the preset icons.

## 9. User Flows

1. **Quick preset:** Now Playing → Equalizer → tap Rock → go back. The sound changes at once and stays for every song.
2. **Tune by hand:** Equalizer → drag the 60 and 150 sliders up → the tile switches to Custom → adjust 2.5K and 6K → the curve is kept as Custom.
3. **Fuller sound at low volume:** Turn on Loudness. For headphones, also turn on Virtualizer.
4. **More bass:** Pick Bass Boost (preset), or turn on the Bass Boost switch, or both.
5. **Compare:** Toggle the On/Off switch while a song plays.
6. **Reset:** Drop-down → Reset.
7. **Save a favourite:** Drop-down → Save current as preset → name it.
8. **Start from a notification:** Open a song from the notification after a restart; it plays with the saved equalizer from the first second.

## 10. Screens

- Equalizer screen (5.1)
- Save-as-preset dialog and rename / delete actions in the drop-down
- Equalizer button states on Now Playing (off, on)

## 11. Testing and Success Criteria

**Automated**

- **Filter tests:** a sine sweep at each band, at ±12 dB, measures within ±0.5 dB at the centre; flat when all gains are 0.
- **Bypass test:** with the equalizer off, or Normal and everything off, output equals input bit for bit, for 16-bit and float.
- **Format tests:** mono, stereo, 44.1, 48 and 96 kHz, 16-bit and float, and mid-stream format changes.
- **Headroom tests:** a full-scale test signal with the largest preset boosts plus Bass Boost and Loudness never exceeds 0 dBFS.
- **Virtualizer tests:** mono input unchanged; bass (below 150 Hz) not widened; mono sum of stereo output keeps level within a small tolerance.
- **Smoothing test:** a gain or switch change mid-stream produces no discontinuity above a small threshold.
- **State and preset tests:** the Custom rules in 5.3, preset values within ±12 dB, name validation, the 10-preset limit, saved-state restore, and fallback to Normal on damaged state.

**Manual**

- Listen to every preset on at least three songs of different genres, on speaker, wired headphones and Bluetooth, and tune the presets, band Q, Virtualizer amount, Bass Boost and Loudness amounts by ear.
- Drag sliders quickly and switch presets while playing, listening for clicks.
- Check combined use with Lofi mode, Vocals only, Instrumental only, 0.5× and 2× speed, and a running separation job.
- Check on a low-end and a mid-range phone, and at least one Samsung and one Xiaomi-class device.
- Check screen-off playback, and starting from a notification after a restart.
- Check with TalkBack and the largest font size.

**Success**

- No audible artefacts in the checks above.
- The Equalizer is reachable from the player in two taps.
- No crash or ANR related to the equalizer in a week of testing.

## 12. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Boosts cause distortion on loud tracks | Automatic headroom, soft limiter, headroom test |
| Perceived volume drops because of headroom | Accepted for clean sound; the Preamp slider lets users raise it, protected by the limiter |
| High Preamp plus boosts causes audible limiting | The limiter is soft; consider a warning colour on the Preamp slider above +6 dB |
| Clicks while dragging sliders | Smoothed changes, no sink reconfiguration |
| Different results on different phones | Own engine instead of vendor equalizers |
| Virtualizer sounds artificial or hurts mono compatibility | Mid/side widening only above about 200 Hz, mono tests, fixed mild strength, off by default |
| Loudness and Bass Boost sound too strong or muddy | Fixed gentle amounts, tuned by ear, included in headroom |
| Presets sound wrong on some music or headphones | Tune by ear, Custom and saved presets always available |
| Extra processing hurts battery or glitches on low-end phones | Bypass when neutral, low-end performance test, allocation-free audio thread code |
| UI and playback service disagree | One shared state holder, loaded before playback |
| Bass Boost preset and Bass Boost switch confuse users | Clear labels; the switch is described as "extra bass on top" in a short helper line |

## 13. Milestones

| Phase | Scope |
| --- | --- |
| E1: Engine | Shelf and peaking filters, equalizer processor in the chain with Lofi, headroom and limiter, smoothing, filter and format tests |
| E2: Extras | Bass Boost, Loudness and Virtualizer, preamp, tests |
| E3: State | Shared equalizer state, DataStore persistence, loading in the playback service before first playback |
| E4: UI | The concept screen: header and switch, drop-down, seven sliders, preset tiles, preamp, switches, accessibility |
| E5: Presets and Custom | Custom curve behaviour, saved presets with save, rename and delete, live frequency curve |
| E6: Polish and test | Tune presets and effect amounts by ear, device testing, performance check, About and changelog entries |

**Later ideas (not in this release):** per-song or per-playlist settings, separate settings for speaker, wired and Bluetooth outputs, strength sliders for the three switches, more bands, a real-time spectrum, loudness that follows the system volume, applying the equalizer to saved clips, dragging a group label to move Bass, Mids or Highs together.

## 14. Decisions to Confirm

- **Free for everyone,** or Pro only? This PRD assumes Free.
- **Virtualizer, Loudness and Bass Boost** are built in this PRD as our own fixed-strength effects, as shown in the concept. Confirm you want all three in this release, or only the equalizer and Preamp first.
- **Presets:** the nine in the concept plus Custom. Treble Boost and Acoustic from the earlier draft are removed.
- **No separate Simple (3-slider) mode:** the seven sliders with group labels replace it.
- **Not applied to saved clips and Sing Along recordings** in this release: confirm.