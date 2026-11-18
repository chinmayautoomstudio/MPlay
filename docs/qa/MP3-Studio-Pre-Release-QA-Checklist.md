# MP3 Studio Pre-Release QA Checklist

> Manual release-readiness checklist for Google Play. Fill **Actual Result**, **Status**, **Tester Comments**, and **Bug / Issue Reference** as you execute each case.

**App:** MP3 Studio (`com.autoomstudio.mp3studio`)  
**Doc source of truth:** [Developer docs](../README.md)  
**Out of scope:** Payment system (PayU, Cashfree, checkout, payment history, refunds, billing App Links). Website Admin dashboard.

---

## How to use

1. Record **Build under test**: `versionName` / `versionCode` / commit / track (internal/closed/production).
2. Prepare devices: at least one phone on **minSdk-class (API 26+)** and one on a recent API (34–37). Include a Samsung device if possible (notification shade).
3. Prepare Google accounts: **Free** (trial already used), **Trial** (new email+device), **Pro** (or Admin-granted Pro), plus a third device for the 2-device limit.
4. Seed local music: multiple albums/artists, duplicates, a file in `Download/`, a short &lt;30 s track, and space under `Music/` for clips/stems/recordings.
5. For each row: follow steps, write what you saw in **Actual Result**, set **Status** to Pass / Fail / Blocked / Not Tested, and link bugs.
6. **P0/Critical** failures block release. Re-test failed cases after fixes.
7. Do **not** run real payment checkout as part of this checklist.

### Status values

| Status | Meaning |
| --- | --- |
| Pass | Matches Expected Result |
| Fail | Does not match; file a bug |
| Blocked | Cannot run (env, account, dependency) |
| Not Tested | Not executed yet |

### Severity / Priority

- **Severity:** Critical (crash/data loss/sign-in/playback broken), High, Medium, Low
- **Priority:** P0 (release blocker), P1, P2, P3

---

## Release readiness summary

| Field | Value |
| --- | --- |
| Build (`versionName` / `versionCode`) |  |
| Git commit |  |
| Tester name(s) |  |
| Test start date |  |
| Test end date |  |
| Devices / Android versions |  |
| Free / Trial / Pro accounts used |  |
| Go / No-Go decision |  |
| Decision notes |  |

| Module | Total cases | Pass | Fail | Blocked | Not Tested |
| --- | --- | --- | --- | --- | --- |
| AUTH | 26 |  |  |  | 26 |
| PLAN | 21 |  |  |  | 21 |
| PERM | 9 |  |  |  | 9 |
| NAV | 10 |  |  |  | 10 |
| LIB | 24 |  |  |  | 24 |
| PLAY | 22 |  |  |  | 22 |
| SLEEP | 6 |  |  |  | 6 |
| PLIST | 10 |  |  |  | 10 |
| DUP | 6 |  |  |  | 6 |
| LOFI | 5 |  |  |  | 5 |
| EQ | 6 |  |  |  | 6 |
| SEP | 22 |  |  |  | 22 |
| MET | 13 |  |  |  | 13 |
| SA | 12 |  |  |  | 12 |
| TRIM | 8 |  |  |  | 8 |
| WGT | 5 |  |  |  | 5 |
| SET | 5 |  |  |  | 5 |
| LIFE | 5 |  |  |  | 5 |
| NET | 4 |  |  |  | 4 |
| PERF | 5 |  |  |  | 5 |
| REV | 5 |  |  |  | 5 |
| ERR | 10 |  |  |  | 10 |
| COMPAT | 4 |  |  |  | 4 |
| **TOTAL** | **243** |  |  |  | **243** |

---

## Test cases by module

### 1. Accounts and Google Sign-In (`AUTH`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| AUTH-001 | Accounts / Sign-in | Cold start shows sign-in when signed out | App installed; no saved session (fresh install or previously logged out). | 1. Launch the app from the launcher.<br>2. Wait for the splash to dismiss. | Sign-in screen appears (dark theme) with MP3 Studio logo, tagline, animated hero, white Continue with Google button, and Terms/Privacy links. No guest mode or email/password fields. |  | Not Tested | Critical / P0 |  |  |
| AUTH-002 | Accounts / Sign-in | Continue with Google successful first sign-in | Valid Google test account; network on; OAuth clients configured for this build. | 1. Tap Continue with Google.<br>2. Pick a Google account and complete consent if prompted.<br>3. Wait until the main UI appears. | Button shows Signing in… then main app opens (Library). New account receives Trial (or Free if trial denied). Profile does not briefly show Free while plan is still checking. |  | Not Tested | Critical / P0 |  |  |
| AUTH-003 | Accounts / Sign-in | Cancel Google account sheet | On Sign-in screen. | 1. Tap Continue with Google.<br>2. Dismiss/cancel the account picker without choosing an account. | User remains on Sign-in screen. No error snackbar for a plain cancel. |  | Not Tested | High / P1 |  |  |
| AUTH-004 | Accounts / Sign-in | Sign-in with no network | Airplane mode or Wi‑Fi/data off; signed out. | 1. Tap Continue with Google and complete account pick if the sheet still appears. | Sign-in fails gracefully with an auth error snackbar. App does not crash. User stays signed out. |  | Not Tested | High / P1 |  |  |
| AUTH-005 | Accounts / Sign-in | Terms of Service link opens | On Sign-in screen; network available. | 1. Tap Terms of Service in the legal text. | Browser/Custom Tab opens the Terms URL. App remains usable after returning. |  | Not Tested | Medium / P2 |  |  |
| AUTH-006 | Accounts / Sign-in | Privacy Policy link opens | On Sign-in screen; network available. | 1. Tap Privacy Policy in the legal text. | Browser opens the Privacy Policy URL. |  | Not Tested | Medium / P2 |  |  |
| AUTH-007 | Accounts / Devices | Second phone can sign in (limit 2) | Account already signed in on Phone A; Phone B has the app; network on. | 1. On Phone B, Continue with Google using the same account. | Sign-in succeeds. Profile Logged-in devices shows 2 of 2. Both phones stay signed in. |  | Not Tested | Critical / P0 |  |  |
| AUTH-008 | Accounts / Devices | Third phone shows device limit dialog | Same account already on two devices; third phone ready. | 1. On Phone C, Continue with Google with the same account.<br>2. Observe DeviceLimitDialog. | Dialog lists the two existing devices and asks which to log out. Cancel signs out the temporary session on Phone C. |  | Not Tested | Critical / P0 |  |  |
| AUTH-009 | Accounts / Devices | Replace a device from the limit dialog | DeviceLimitDialog showing on Phone C. | 1. Choose one of the two devices to log out.<br>2. Confirm.<br>3. Open the app on the replaced phone and bring it to foreground. | Phone C signs in successfully. Replaced phone is signed out on next foreground check with an appropriate message. |  | Not Tested | Critical / P0 |  |  |
| AUTH-010 | Accounts / Profile | Profile shows photo, name, email | Signed in with a Google account that has a photo and name. | 1. Open the Profile tab (bottom bar avatar). | Header shows account photo (or person placeholder), display name, and email. Bottom-bar Profile icon uses the account photo. |  | Not Tested | High / P1 |  |  |
| AUTH-011 | Accounts / Profile | Edit display name and save | Signed in; network on. | 1. Profile → Edit (pencil).<br>2. Change name to a valid string ≤80 characters.<br>3. Tap Save.<br>4. Return to Profile. | Name updates on Edit and Profile. No error under the field. |  | Not Tested | High / P1 |  |  |
| AUTH-012 | Accounts / Profile | Display name over 80 characters rejected or truncated per UI | On Edit Profile. | 1. Enter more than 80 characters in the name field.<br>2. Attempt Save. | UI prevents or rejects invalid length; error shown if save fails. Profile not left in a broken state. |  | Not Tested | Medium / P2 |  |  |
| AUTH-013 | Accounts / Profile | Log out confirmation and effect | Signed in; music optionally playing. | 1. Profile → Log out.<br>2. Confirm in the dialog. | App returns to Sign-in. Playback stops and notification clears. Widget shows Sign in to MP3 Studio. Device slot freed on server. |  | Not Tested | Critical / P0 |  |  |
| AUTH-014 | Accounts / Profile | Cancel log out | Signed in on Profile. | 1. Tap Log out.<br>2. Cancel the confirmation dialog. | User remains signed in. Playback (if any) continues. |  | Not Tested | Medium / P2 |  |  |
| AUTH-015 | Accounts / Devices | Remote log out of other device from Profile | Account on two devices; signed in on Phone A. | 1. On Phone A Profile, open Logged-in devices.<br>2. Tap Log out on the other device and confirm.<br>3. Foreground the app on Phone B. | Phone B is signed out with a revoked-device style message. Phone A still signed in; devices shows 1 of 2. |  | Not Tested | High / P1 |  |  |
| AUTH-016 | Accounts / Delete | Delete account requires typing DELETE | Signed in; on Edit Profile. | 1. Tap Delete account.<br>2. Leave confirmation field empty or type wrong text.<br>3. Observe Delete button.<br>4. Type DELETE exactly. | Delete stays disabled until DELETE is typed. Dialog explains what is deleted (account, plan, trial, AI history) and what stays on the phone (music, playlists, stems, recordings). |  | Not Tested | High / P1 |  |  |
| AUTH-017 | Accounts / Delete | Successful account deletion | Test account safe to delete; network on; Delete dialog with DELETE typed. (Skip if only admin account.) | 1. Confirm Delete.<br>2. Wait for completion. | Session cleared; Sign-in shown. Local music/playlists/stems remain on device. Same email cannot claim another trial later. |  | Not Tested | Critical / P0 |  |  |
| AUTH-018 | Accounts / Delete | Delete account while offline | Airplane mode; Delete dialog ready. | 1. Confirm Delete. | Deletion fails with Offline (or equivalent). User stays signed in. |  | Not Tested | High / P1 |  |  |
| AUTH-019 | Accounts / Session | App relaunch keeps session | Signed in; kill app from recents then relaunch, or force-stop and open again. | 1. Force-stop or swipe away and relaunch.<br>2. Observe after splash. | User lands in main UI still signed in (not Sign-in), unless session was revoked. |  | Not Tested | Critical / P0 |  |  |
| AUTH-020 | Accounts / Signed out media | No playback while signed out | Signed out; headset or media notification may still exist from before. | 1. From signed out, try widget play, headset play/pause, and any leftover media controls. | Nothing starts playing. Widget shows Sign in to MP3 Studio without transport controls. |  | Not Tested | Critical / P0 |  |  |
| AUTH-021 | Accounts / Session | Foreground verify after remote revoke | Signed in; another device or admin revoked this device (or sign out remotely). | 1. Bring app to foreground on revoked device. | User is signed out with a clear message. Sign-in screen shown. |  | Not Tested | High / P1 |  |  |
| AUTH-022 | Accounts / Sign-in | Sign-in screen respects reduced motion | System animations disabled (Developer options / remove animations). | 1. Launch signed-out app. | Sign-in appears at once with a still hero frame; no crash. |  | Not Tested | Low / P3 |  |  |
| AUTH-023 | Accounts / Sign-in | Returning user gets existing trial claim not a new trial | Email that already used trial; sign in on allowed device. | 1. Complete Google sign-in. | Sign-in succeeds. Plan is Free or Pro as entitled — not a fresh 30-day trial. Profile shows correct plan after entitlements load. |  | Not Tested | High / P1 |  |  |
| AUTH-024 | Accounts / Profile | Logged-in devices card labels This device | Signed in on one or two devices. | 1. Open Profile → Logged-in devices. | Current phone marked This device without a Log out action on itself; other phone (if any) has Log out. |  | Not Tested | Medium / P2 |  |  |
| AUTH-025 | Accounts / Session | Splash waits for auth Loading then shows correct gate | Cold start with valid saved session. | 1. Force-stop and launch.<br>2. Watch splash → UI. | Splash remains until theme loaded and AuthState leaves Loading. Lands on main UI if session valid, else Sign-in. Never stuck on splash forever (8 s Loading timeout). |  | Not Tested | High / P1 |  |  |
| AUTH-026 | Accounts / Delete | Last admin cannot delete (if applicable) | Account is the only Admin on the project (staging only). | 1. Attempt Delete account with DELETE typed. | Refusal LastAdmin (or equivalent). User remains signed in. |  | Not Tested | Medium / P2 |  |  |

### 2. Plans, Trial and Feature Gates (`PLAN`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PLAN-001 | Plans / Display | Profile subscription card shows plan | Signed in; entitlements loaded. | 1. Open Profile.<br>2. Read the subscription card. | Card shows Free, Free trial with days left, or Pro. Tapping opens Plans. While still checking, shows Checking your plan… not Free. |  | Not Tested | High / P1 |  |  |
| PLAN-002 | Plans / Display | Plans screen layout for Free or Trial | Signed-in Free or Trial user. | 1. Profile → subscription card / Plans.<br>2. Review title, Your subscription, Free and Pro cards, Compare Plans. | Title Upgrade to Pro (or Your plan on Pro). Plan label/dates and separator usage (Free) visible. Compare Plans lists feature rows. Payment checkout is out of scope for this release QA — do not complete purchases. |  | Not Tested | High / P1 |  |  |
| PLAN-003 | Plans / Display | Plans Refresh reloads entitlements | On Plans; network on. | 1. Tap Refresh. | Plan data refreshes without crash. Stale warning clears if online check succeeds. |  | Not Tested | Medium / P2 |  |  |
| PLAN-004 | Plans / Gates | Free: Detect BPM shows lock and upgrade sheet | Free plan (not Trial/Pro); a song playing; no cached tempo for that song. | 1. Open Metronome tab or Now Playing metronome sheet.<br>2. Tap Detect BPM. | Detect shows a lock. Upgrade sheet appears instead of running detection. Cached tempos from earlier plans still may fill in without unlocking new detection. |  | Not Tested | Critical / P0 |  |  |
| PLAN-005 | Plans / Gates | Trial or Pro: Detect BPM runs | Trial or Pro; song playing. | 1. Tap Detect BPM. | Detection runs; BPM (and possibly meter) applied. No upgrade sheet. |  | Not Tested | Critical / P0 |  |  |
| PLAN-006 | Plans / Gates | Free: Sing Along chip opens upgrade sheet | Free plan; song on Now Playing (stems optional). | 1. Tap Sing Along on Now Playing. | Lock icon visible; UpgradeSheet opens. Setup sheet does not open. |  | Not Tested | Critical / P0 |  |  |
| PLAN-007 | Plans / Gates | Trial or Pro: Sing Along opens setup | Trial/Pro; instrumental stem available for current song. | 1. Tap Sing Along. | Sing-along setup sheet opens (not upgrade). |  | Not Tested | Critical / P0 |  |  |
| PLAN-008 | Plans / Separator limit | Free weekly separator usage shown | Free user; some separations used this week. | 1. Open Profile → Vocal Separation and/or Plans and/or separation queue header. | Usage text like N/10 songs used this week with remaining count and reset day (Monday IST). |  | Not Tested | High / P1 |  |  |
| PLAN-009 | Plans / Separator limit | Free hitting weekly limit opens upgrade | Free user at 10/10 for the week; network on. | 1. Try to separate another new song. | LimitReached: Upgrade sheet Weekly limit reached with reset day. Song not queued (or partial queue messaging if multi-select). |  | Not Tested | Critical / P0 |  |  |
| PLAN-010 | Plans / Separator limit | Trial/Pro unlimited separations | Trial or Pro; eligible device. | 1. Queue several separations beyond 10. | Jobs queue without weekly limit upgrade. Usage limit UI not restricting. |  | Not Tested | High / P1 |  |  |
| PLAN-011 | Plans / Upgrade sheet | See plans from upgrade sheet | Upgrade sheet visible from a locked feature. | 1. Tap See plans. | Now Playing sheets close if open; player collapses; Profile → Plans opens. |  | Not Tested | High / P1 |  |  |
| PLAN-012 | Plans / Trial banner | Last 3 days of trial shows banner | Account in last 3 days of trial (or test clock/data). | 1. Open main UI. | Dismissible trial banner above content opens Plans when tapped. |  | Not Tested | High / P1 |  |  |
| PLAN-013 | Plans / Trial banner | Dismiss trial banner until process restart | Trial banner visible. | 1. Dismiss banner.<br>2. Navigate around without killing process.<br>3. Force-stop and relaunch. | Banner stays hidden until process restart; may reappear after relaunch if still in last 3 days. |  | Not Tested | Medium / P2 |  |  |
| PLAN-014 | Plans / Offline | Ended trial locks offline using server time | Trial ended; go offline before next online refresh if needed; cache still present. | 1. Enable airplane mode.<br>2. Try Sing Along / Detect BPM. | Features locked as Free once trial end is reached vs server time. Offline grace does not keep expired Trial forever. |  | Not Tested | High / P1 |  |  |
| PLAN-015 | Plans / Offline | Stale cache (>7 days offline) forces Free | Had Trial/Pro cache; device offline >7 days since last successful check (or clock advanced). | 1. Stay offline with old cache.<br>2. Open app and try gated features. | Effective plan treated as Free; upgrade messaging may mention being offline too long. Going online and foregrounding restores real plan after refresh. |  | Not Tested | High / P1 |  |  |
| PLAN-016 | Plans / Sign-out | Sign-out clears cached plan | Signed in with a known plan. | 1. Log out.<br>2. Sign in with a different Free account (or same after clear). | Previous account entitlements do not unlock features for the new session. |  | Not Tested | Critical / P0 |  |  |
| PLAN-017 | Plans / Display | Payments unavailable observation only | On Plans screen (any plan). | 1. Note Go Pro / buy controls if shown as unavailable or disabled. | Record observation only. Do not run checkout, PayU, Cashfree, payment history, or refund flows in this checklist. |  | Not Tested | Low / P3 |  |  |
| PLAN-018 | Plans / Gates | Saved sing-along recordings playable on Free | Device has a prior MP3 Studio Recording; account is Free. | 1. Find the recording in Library.<br>2. Play it. | Recording plays. Creating a new sing-along remains locked. |  | Not Tested | High / P1 |  |  |
| PLAN-019 | Plans / Gates | Playing existing stems does not consume Free quota | Free user with stems already on disk; usage e.g. 3/10. | 1. Note usage.<br>2. Switch Original/Instrumental/Vocals and listen.<br>3. Re-check usage. | Usage count unchanged. Only new/retry enqueue reserves quota. |  | Not Tested | High / P1 |  |  |
| PLAN-020 | Plans / Trial | Clock turned back behind last check shows Free | Had Trial cache; set device clock earlier than last successful check (lab only). | 1. Open app offline with clock rolled back.<br>2. Try gated feature. | Effective plan Free until online refresh. Restore clock afterward. |  | Not Tested | Medium / P2 |  |  |
| PLAN-021 | Plans / Admin grant | Admin-granted Pro appears after refresh | Admin granted Pro on server for test account. | 1. Foreground app or open Plans → Refresh. | Plan shows Pro without requiring payment. Feature locks clear. |  | Not Tested | High / P1 |  |  |

### 3. Permissions (`PERM`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PERM-001 | Permissions / Audio | First launch audio permission rationale | Fresh install or audio permission not granted; signed in. | 1. Sign in if needed.<br>2. Observe permission flow. | Permission rationale / system dialog for music/audio access appears. Denying keeps NoPermission library state with a way to request again. |  | Not Tested | Critical / P0 |  |  |
| PERM-002 | Permissions / Audio | Grant audio permission loads library | Audio permission denied previously. | 1. Grant READ_MEDIA_AUDIO / storage as prompted.<br>2. Return to Library. | Songs/Albums/Artists populate (or Empty if no music). Not stuck on NoPermission. |  | Not Tested | Critical / P0 |  |  |
| PERM-003 | Permissions / Notifications | POST_NOTIFICATIONS prompt on Android 13+ | Android 13+; fresh install; notification_prompt not yet shown. | 1. Sign in and reach main UI. | Notification permission requested once. Accepting allows media/separation/metronome/recording notifications later. |  | Not Tested | High / P1 |  |  |
| PERM-004 | Permissions / Notifications | Deny notifications still allows playback in-app | Android 13+; deny POST_NOTIFICATIONS. | 1. Play a song in-app. | In-app playback works. System media notification may be missing/limited. App does not crash. |  | Not Tested | High / P1 |  |  |
| PERM-005 | Permissions / Microphone | Sing-along requests RECORD_AUDIO | Trial/Pro; stems ready; mic permission not granted. | 1. Start Sing Along flow. | Mic permission prompt appears. Deny blocks recording with clear failure; Grant allows prepare/record. |  | Not Tested | Critical / P0 |  |  |
| PERM-006 | Permissions / Write settings | Ringtone WRITE_SETTINGS flow | Signed in; song available. | 1. Song menu → Set as ringtone.<br>2. If prompted, open system Write settings and allow.<br>3. Return and complete. | App opens manage-write-settings when needed and retries on resume. Ringtone can be set after grant. |  | Not Tested | High / P1 |  |  |
| PERM-007 | Permissions / Battery | Background playback battery optimization page | Signed in. | 1. Profile → Background Playback (or Playback settings).<br>2. Open battery optimization settings if offered.<br>3. Return to app. | Page reflects whether app is ignoring battery optimizations; re-checks on resume. |  | Not Tested | Medium / P2 |  |  |
| PERM-008 | Permissions / Revoke | Revoke audio permission while signed in | Library previously loaded. | 1. In system Settings, revoke music/storage permission for MP3 Studio.<br>2. Return to app. | Library shows NoPermission / rationale again; no crash. Playback of previously loaded items may fail gracefully. |  | Not Tested | High / P1 |  |  |
| PERM-009 | Permissions / Mic revoke | Revoke mic during sing-along eligibility | Trial/Pro; mic previously granted. | 1. Revoke RECORD_AUDIO in system settings.<br>2. Start Sing Along. | App re-requests or fails clearly; no silent hang on RecordingService. |  | Not Tested | High / P1 |  |  |

### 4. Navigation and Deep Links (`NAV`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| NAV-001 | Navigation / Bottom bar | Switch Library, Playlists, Metronome, Profile | Signed in. | 1. Tap each bottom destination in turn. | Each tab shows its screen. Profile icon is account photo. Selecting Profile resets to Profile main (not last sub-page). |  | Not Tested | Critical / P0 |  |  |
| NAV-002 | Navigation / Profile | Open and back from each Profile sub-page | Signed in on Profile. | 1. Open Appearance, Library, Vocal Separation, Background Playback, Equalizer, About, Plans, Edit Profile one at a time.<br>2. Use back/header back each time. | Each sub-page opens and returns correctly. Duplicates/Exclude folders back to Library settings; Separation queue back to Separation; Licenses back to About. No Payment History testing required. |  | Not Tested | High / P1 |  |  |
| NAV-003 | Navigation / Player | Expand mini player to Now Playing | A song is loaded (playing or paused). | 1. Tap or drag mini player up to expand.<br>2. Drag/back to collapse. | Full Now Playing appears with artwork, seek, transport, feature tiles. Collapse returns to mini player over content. |  | Not Tested | Critical / P0 |  |  |
| NAV-004 | Navigation / Back | Back stack: selection → player → screen | Library with multi-select active and player expanded. | 1. Press system Back repeatedly. | Back collapses player first or clears selection per layered back handling, then leaves detail screens, without exiting unexpectedly until root. |  | Not Tested | High / P1 |  |  |
| NAV-005 | Navigation / Deep link | Media notification opens Now Playing | Playing; media notification visible. | 1. Tap the media notification (content, not just a button). | App opens with Now Playing expanded (EXTRA_OPEN_NOW_PLAYING). |  | Not Tested | High / P1 |  |  |
| NAV-006 | Navigation / Deep link | Separation notification opens queue | A separation job running or queued; notification shown. | 1. Tap the separation notification body. | App opens Separation queue (ACTION_OPEN_QUEUE). |  | Not Tested | High / P1 |  |  |
| NAV-007 | Navigation / Deep link | Metronome notification opens metronome | Metronome running with notification. | 1. Tap metronome notification content. | App opens with metronome UI (EXTRA_OPEN_METRONOME). |  | Not Tested | Medium / P2 |  |  |
| NAV-008 | Navigation / Library | Album/artist detail back to tabs | Library has albums/artists. | 1. Open an album.<br>2. Back.<br>3. Open an artist.<br>4. Back. | Detail stacks on tabs; back returns to Songs/Albums/Artists pager state. |  | Not Tested | High / P1 |  |  |
| NAV-009 | Navigation / Profile | Bottom bar Profile resets sub-page | Open Profile → About. | 1. Switch to Library tab.<br>2. Tap Profile again. | Profile main menu shown (not About). |  | Not Tested | Medium / P2 |  |  |
| NAV-010 | Navigation / Player | Mini player hidden when nothing loaded | Fresh session with empty queue after sign-in (or clear queue). | 1. Observe Library with no now playing. | No mini player / hasNowPlaying false. Playing a song shows it. |  | Not Tested | Medium / P2 |  |  |

### 5. Music Library (`LIB`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| LIB-001 | Library / Tabs | Songs, Albums, Artists swipe and tap | Signed in; audio permission granted; music on device. | 1. On Library, tap Albums, Artists, Songs.<br>2. Swipe left/right between tabs. | HorizontalPager + tabs switch correctly; selected tab remembered across brief navigation. |  | Not Tested | Critical / P0 |  |  |
| LIB-002 | Library / Empty | Empty library state | Device with no eligible music files (or all excluded). | 1. Open Library. | Empty state shown (not infinite loading). Pull-to-refresh available. |  | Not Tested | High / P1 |  |  |
| LIB-003 | Library / Search | Search filters songs | Library with Content state. | 1. Enter a unique song title fragment.<br>2. Clear search. | Results filter within ~250 ms debounce. Clearing restores full list. Album/artist details still resolve full albums during search. |  | Not Tested | High / P1 |  |  |
| LIB-004 | Library / Search | Search no matches | Library Content. | 1. Search a nonsense string. | Empty results UI; no crash. |  | Not Tested | Medium / P2 |  |  |
| LIB-005 | Library / Sort | All sort orders | Library with several songs. | 1. Cycle sort: Title; Artist then title; Album then track; Date added; Duration. | List order matches each mode. Unknown Artist sorts last where applicable. Preference persists after relaunch. |  | Not Tested | High / P1 |  |  |
| LIB-006 | Library / Album detail | Play all and Shuffle from album | Album with multiple tracks. | 1. Open album.<br>2. Play all.<br>3. Open again and Shuffle. | Queue starts with album tracks in order / shuffled. Now Playing updates. |  | Not Tested | High / P1 |  |  |
| LIB-007 | Library / Artist detail | Play all and Shuffle from artist | Artist with multiple songs. | 1. Open artist → Play all / Shuffle. | Playback starts from that artist’s songs. |  | Not Tested | High / P1 |  |  |
| LIB-008 | Library / Song menu | Song overflow actions present | Any song row. | 1. Open song menu (⋮). | Menu includes play-related actions, Add to playlist, Cut and save, Set as ringtone, separate vocals / stems, Song info, Delete as applicable. |  | Not Tested | High / P1 |  |  |
| LIB-009 | Library / Song info | Song info dialog | Song menu open. | 1. Tap Song info. | Dialog shows metadata (title, artist, album, duration, etc.). Dismiss works. |  | Not Tested | Medium / P2 |  |  |
| LIB-010 | Library / Selection | Long-press multi-select and bulk actions | Library Songs tab. | 1. Long-press a song.<br>2. Tap others to toggle.<br>3. Use bulk Add to playlist / Play next / Add to queue / Delete / Separate. | Selection mode with count; bulk actions apply to selected. Back/close clears selection. |  | Not Tested | High / P1 |  |  |
| LIB-011 | Library / Delete | Delete song Android 11+ system dialog | Android 11+; song not needed. | 1. Delete via menu or selection.<br>2. Confirm system delete request. | Song removed from library, queue, and playlists. Snackbar reports result. |  | Not Tested | High / P1 |  |  |
| LIB-012 | Library / Delete | Cancel delete | Delete confirmation / system dialog shown. | 1. Cancel. | Song remains in library. |  | Not Tested | Medium / P2 |  |  |
| LIB-013 | Library / Scan | Pull-to-refresh rescans folders | Add a new MP3 to Music/Download then return to app. | 1. Pull to refresh Library.<br>2. Wait up to ~15 s. | New eligible file appears (duration ≥30 s unless under Clips/Recordings folders). |  | Not Tested | High / P1 |  |  |
| LIB-014 | Library / Duration filter | Files under 30 s hidden except clips/recordings | Device has a short &lt;30 s track outside special folders, and a clip in Music/MP3 Studio Clips. | 1. Refresh library.<br>2. Search both. | Short non-clip file hidden. Clip/recording under MP3 Studio folders appears despite short length. |  | Not Tested | Medium / P2 |  |  |
| LIB-015 | Library / Exclude folders | Exclude a folder hides its songs | Music in more than one folder. | 1. Profile → Library → Exclude folders.<br>2. Toggle a folder off.<br>3. Return to Library. | Songs from that folder disappear from library and restore logic without requiring full rescan wait forever. |  | Not Tested | High / P1 |  |  |
| LIB-016 | Library / Exclude folders | Re-include folder restores songs | A folder currently excluded. | 1. Turn the folder switch back on. | Songs reappear. |  | Not Tested | High / P1 |  |  |
| LIB-017 | Library / Album art | Missing art shows app logo | Song/album without cover (or Download/ without embedded art). | 1. View list and Now Playing. | App logo placeholder used; no broken-image crash. Notification also has a large icon. |  | Not Tested | Medium / P2 |  |  |
| LIB-018 | Library / Various Artists | Album with multiple artists | Compilation album in library. | 1. Open Albums tab. | Album shows Various Artists when applicable. |  | Not Tested | Low / P3 |  |  |
| LIB-019 | Library / Foreground | Return to app triggers rescan if stale | Signed in; leave app &gt;30 s; add a file externally. | 1. Return to foreground. | Library refresh/scan runs; new file can appear without manual pull. |  | Not Tested | Medium / P2 |  |  |
| LIB-020 | Library / Play | Tap song starts playback | Songs tab Content. | 1. Tap a song row (not the menu). | Playback starts that song; mini player appears with title/artist. |  | Not Tested | Critical / P0 |  |  |
| LIB-021 | Library / Unknown | Missing tags show Unknown Artist/Album | File with empty artist/album tags in library. | 1. Find the song in list and detail groupings. | Displays Unknown Artist / Unknown Album; title falls back to file name if needed. |  | Not Tested | Medium / P2 |  |  |
| LIB-022 | Library / Recordings path | Public Recordings/ folder songs appear | Eligible recording under public Recordings/ (API 12+ IS_RECORDING or path). | 1. Refresh library and search. | Recording appears when eligibility rules match. |  | Not Tested | Medium / P2 |  |  |
| LIB-023 | Library / Selection | Remove from playlist bulk action in playlist context | Inside a playlist detail with selection. | 1. Multi-select songs.<br>2. Remove from playlist. | Selected entries removed from playlist only; still in library. |  | Not Tested | High / P1 |  |  |
| LIB-024 | Library / Delete legacy | Delete on Android 8–10 path | API 26–29 device if available; disposable song. | 1. Delete song; grant WRITE_EXTERNAL_STORAGE if asked; confirm in-app dialog. | Song deleted via contentResolver path; snackbar OK. |  | Not Tested | High / P1 |  |  |

### 6. Playback, Queue, Shuffle, Repeat, System Controls (`PLAY`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PLAY-001 | Playback / Transport | Play, pause, next, previous | Queue with ≥2 songs. | 1. Play from library.<br>2. Pause / play on mini and Now Playing.<br>3. Next.<br>4. Previous after &gt;3 s into track (restart) and near start (previous track). | Transport works on mini and full player. Previous after 3 s restarts current; near start goes to previous track. previousTrack swipe always changes song. |  | Not Tested | Critical / P0 |  |  |
| PLAY-002 | Playback / Seek | Seek bar and scrub | Song playing. | 1. Drag Now Playing seek bar.<br>2. Scrub on mini player strip. | Position updates; audio seeks; time labels update. |  | Not Tested | Critical / P0 |  |  |
| PLAY-003 | Playback / Swipe skip | Swipe album art / mini for next/prev | Queue with next and previous; Now Playing expanded. | 1. Swipe art left (next) and right (previous).<br>2. Repeat on mini player row. | Song changes with haptic; art/title animate. Vertical drag still expands/collapses. At ends, drag shows resistance only. |  | Not Tested | High / P1 |  |  |
| PLAY-004 | Playback / Shuffle | Toggle shuffle | Queue of several songs. | 1. Enable shuffle on Now Playing.<br>2. Skip several tracks.<br>3. Disable shuffle. | Order randomizes when on; mode persists across kill/relaunch with session. |  | Not Tested | High / P1 |  |  |
| PLAY-005 | Playback / Repeat | Cycle Off → All → One | Queue with ≥2 songs. | 1. Cycle repeat mode three times.<br>2. On Repeat One, let song end.<br>3. On Repeat All, let last song end. | Modes cycle Off, All, One. One loops current; All continues queue; Off stops at end as designed. |  | Not Tested | High / P1 |  |  |
| PLAY-006 | Playback / Up Next | Open queue sheet, jump, reorder, remove | Now Playing with ≥3 upcoming items. | 1. Swipe up / tap Up Next handle.<br>2. Tap a later song to jump.<br>3. Drag reorder.<br>4. Remove an item. | Sheet expands/collapses; jump plays selected; reorder persists; remove drops from queue. Back peeks then collapses Now Playing. |  | Not Tested | Critical / P0 |  |  |
| PLAY-007 | Playback / Queue | Play next and Add to queue from song menu | Something already playing. | 1. On another song: Play next.<br>2. On another: Add to queue.<br>3. Inspect Up Next. | Play next inserts after current; Add to queue appends. Playback of current uninterrupted. |  | Not Tested | High / P1 |  |  |
| PLAY-008 | Playback / Session | Queue survives process death | Playing mid-song with shuffle/repeat set. | 1. Note song, position, modes.<br>2. Force-stop app.<br>3. Relaunch and expand player (do not require auto-play). | Saved queue restored paused at approximate position with shuffle/repeat. Missing songs skipped per restore rules. |  | Not Tested | Critical / P0 |  |  |
| PLAY-009 | Playback / Audio focus | Pause for other audio / call | Music playing. | 1. Start another media app or place/receive a call (emulator: another player). | MP3 Studio pauses or ducks per focus. Resuming after call behaves reasonably. |  | Not Tested | High / P1 |  |  |
| PLAY-010 | Playback / Becoming noisy | Unplug headphones pauses | Playing over wired headset. | 1. Unplug headphones. | Playback pauses (handleAudioBecomingNoisy). |  | Not Tested | High / P1 |  |  |
| PLAY-011 | Playback / Background | Play with screen off | Notifications allowed; signed in. | 1. Play music.<br>2. Turn screen off for 1–2 minutes. | Music continues; media notification remains controllable. |  | Not Tested | Critical / P0 |  |  |
| PLAY-012 | Playback / Notification | Notification play/pause/next/prev | Playing; shade notification visible. | 1. Use notification transport controls including on Samsung shade if available. | Controls work while signed in. Queue cannot be changed from untrusted controllers. |  | Not Tested | Critical / P0 |  |  |
| PLAY-013 | Playback / Lock screen | Lock screen media controls | Playing; lock device. | 1. Use lock-screen media controls. | Play/pause/skip work; artwork/title show when provided. |  | Not Tested | Critical / P0 |  |  |
| PLAY-014 | Playback / Headset | Bluetooth/wired headset buttons | Signed in; headset connected; queue saved or playing. | 1. Press headset play/pause and skip if supported. | Controls work while signed in. While signed out they do nothing. |  | Not Tested | High / P1 |  |  |
| PLAY-015 | Playback / Unplayable | Corrupt/unreadable file skipped | Include a corrupt audio file in library if possible. | 1. Play a queue containing the bad file. | Player skips/removes broken item without crashing entire session. Stem failure falls back to original when applicable. |  | Not Tested | High / P1 |  |  |
| PLAY-016 | Playback / Recents | Swipe app from recents stops music | Music playing with notification. | 1. Swipe MP3 Studio from recents. | Playback pauses/stops; notification dismissed; service stops. Queue saved for later resume. Metronome/sing-along/separation also stop/pause. |  | Not Tested | Critical / P0 |  |  |
| PLAY-017 | Playback / Feature tiles | Now Playing tiles visible | Now Playing expanded. | 1. Locate Lofi, Equalizer, Metronome, Separator, Sing-along tiles. | All five feature tiles present and tappable (gates may show locks on Free). |  | Not Tested | Medium / P2 |  |  |
| PLAY-018 | Playback / Accessibility | TalkBack next/previous on art and mini | TalkBack on. | 1. Focus album art / mini player.<br>2. Use custom actions Next song / Previous song. | Actions announced and change tracks. |  | Not Tested | Medium / P2 |  |  |
| PLAY-019 | Playback / Duplicate in queue | Same song twice in Up Next | Add the same song to queue twice. | 1. Open Up Next.<br>2. Reorder/remove one instance. | Both rows visible; removing one leaves the other. No Lazy list identity glitches. |  | Not Tested | Medium / P2 |  |  |
| PLAY-020 | Playback / Sign-out mid-play | Sign-out stops and clears player | Music playing. | 1. Log out. | Player stops/clears; notification gone; queue DataStore kept for next sign-in resume. |  | Not Tested | Critical / P0 |  |  |
| PLAY-021 | Playback / Controllers | Other apps cannot hijack queue | Signed in; playing (lab: third-party media controller if available). | 1. Attempt control from an untrusted media controller app. | Untrusted controllers rejected. Trusted system/notification transport-only (no queue edits). |  | Not Tested | Medium / P2 |  |  |
| PLAY-022 | Playback / Artwork notification | Notification shows cover or logo | Play song with art and one without. | 1. Compare media notification large icons. | Art when available; app logo fallback when not. Download/ embedded art may appear via AlbumArtLoader. |  | Not Tested | Medium / P2 |  |  |

### 7. Sleep Timer (`SLEEP`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| SLEEP-001 | Sleep timer / Presets | Set 15/30/45/60 minute timer | Music playing; open sleep timer sheet. | 1. Choose preset 15 (or 30).<br>2. Observe remaining chip. | Timer starts; remaining time shown. Default preference remembered as last_sleep_minutes. |  | Not Tested | High / P1 |  |  |
| SLEEP-002 | Sleep timer / Slider | Custom minutes 1–180 | Sleep timer sheet open. | 1. Set slider to 1 minute.<br>2. Wait for expiry (or set short and observe).<br>3. Also try 180. | Values clamp to 1–180. At expiry, volume fades over last 30 s then pauses; queue kept. |  | Not Tested | High / P1 |  |  |
| SLEEP-003 | Sleep timer / End of song | End of song mode | Playing a short remaining song; optionally with lofi on (0.85x). | 1. Set End of song sleep timer.<br>2. Let track finish. | Playback pauses at end of current item; accounts for playback speed when lofi is on. |  | Not Tested | High / P1 |  |  |
| SLEEP-004 | Sleep timer / Extend | Extend by 10 minutes | Active timed sleep timer. | 1. Tap extend. | End time moves +10 minutes. |  | Not Tested | Medium / P2 |  |  |
| SLEEP-005 | Sleep timer / Cancel | Cancel sleep timer | Active sleep timer. | 1. Cancel from sheet. | Timer cleared; music continues without fade. |  | Not Tested | Medium / P2 |  |  |
| SLEEP-006 | Sleep timer / Empty queue | Auto-cancel when queue empties | Sleep timer on; clear/remove all queue items. | 1. Remove remaining queue until empty. | Sleep timer auto-cancels. |  | Not Tested | Medium / P2 |  |  |

### 8. Playlists (`PLIST`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PLIST-001 | Playlists / Create | Create playlist from FAB | Playlists tab. | 1. Tap New playlist.<br>2. Enter a unique name.<br>3. Confirm. | Playlist appears in list. Empty name rejected. |  | Not Tested | Critical / P0 |  |  |
| PLIST-002 | Playlists / Add | Add song from song menu | Playlist exists; song not in it. | 1. Song menu → Add to playlist.<br>2. Pick playlist. | Snackbar Added (or AlreadyIn). Song appears in playlist detail. |  | Not Tested | High / P1 |  |  |
| PLIST-003 | Playlists / Add | Add multiple via selection | Multi-select several songs. | 1. Bulk Add to playlist. | All selected added once; duplicates skipped. |  | Not Tested | High / P1 |  |  |
| PLIST-004 | Playlists / Duplicate add | Add same song again | Song already in playlist. | 1. Add to same playlist again. | AlreadyIn message; still only one entry. |  | Not Tested | Medium / P2 |  |  |
| PLIST-005 | Playlists / Rename | Rename playlist | Playlist exists. | 1. Rename from list actions.<br>2. Confirm. | New name shown everywhere. |  | Not Tested | High / P1 |  |  |
| PLIST-006 | Playlists / Delete | Delete playlist | Playlist with songs. | 1. Delete playlist and confirm. | Playlist and its entries gone. Songs remain in library. |  | Not Tested | High / P1 |  |  |
| PLIST-007 | Playlists / Reorder | Drag reorder songs | Playlist with ≥3 available songs. | 1. Open detail.<br>2. Drag to reorder.<br>3. Leave and re-open. | Order persists. TalkBack Move up/down if tested. |  | Not Tested | High / P1 |  |  |
| PLIST-008 | Playlists / Unavailable | Missing file shows unavailable | Playlist referencing a deleted/moved file ID. | 1. Open playlist detail. | Unavailable count shown; missing rows marked; not silently dropped. File return restores if same MediaStore ID. |  | Not Tested | High / P1 |  |  |
| PLIST-009 | Playlists / Play | Play playlist | Playlist with songs. | 1. Play from playlist detail. | Queue loads playlist order and starts playback. |  | Not Tested | High / P1 |  |  |
| PLIST-010 | Playlists / Artwork | Playlist artwork from first song | Playlist with songs that have art. | 1. View playlist list. | Artwork uses first song’s album art or logo fallback. |  | Not Tested | Low / P3 |  |  |

### 9. Duplicate Detection (`DUP`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DUP-001 | Duplicates / Hide | Hide duplicates default | Library contains two near-identical copies (same title/artist/duration or identical bytes). | 1. Ensure Hide duplicates is on (Profile → Library).<br>2. Browse Songs. | Only the kept (best quality) copy is listed. |  | Not Tested | High / P1 |  |  |
| DUP-002 | Duplicates / Toggle | Show duplicates when hide off | Known duplicate group; hide on. | 1. Turn Hide duplicates off.<br>2. Browse library. | Both copies visible. |  | Not Tested | High / P1 |  |  |
| DUP-003 | Duplicates / Review | Review duplicates screen | Duplicate groups detected. | 1. Profile → Library → Review duplicates. | Groups listed with Kept/Hidden/Restored, quality, duration. Radio keep works. |  | Not Tested | High / P1 |  |  |
| DUP-004 | Duplicates / Keep | Change kept copy | On Review duplicates. | 1. Select a different copy as Keep.<br>2. Return to library with hide on. | Newly kept copy is the visible one; playlists resolve hidden IDs to kept canonical. |  | Not Tested | High / P1 |  |  |
| DUP-005 | Duplicates / Restore | Restore a hidden copy | Review screen on a group. | 1. Restore a hidden copy.<br>2. Hide again. | Restored copy appears in library while restored; hiding again conceals it. |  | Not Tested | Medium / P2 |  |  |
| DUP-006 | Duplicates / Delete extras | Delete hidden duplicates | Review group with extras; files disposable. | 1. Delete extras and confirm system/in-app delete. | Hidden copies deleted from device; kept remains. |  | Not Tested | High / P1 |  |  |

### 10. Lofi Mode (`LOFI`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| LOFI-001 | Lofi / Toggle | Enable lofi from Now Playing | Song playing. | 1. Tap Lofi tile.<br>2. Listen. | Sound becomes lofi (slower ~0.85x, filtered, hiss). Crossfade without clicks. Tile indicates on. |  | Not Tested | High / P1 |  |  |
| LOFI-002 | Lofi / Toggle | Disable lofi | Lofi on. | 1. Tap Lofi again. | Returns to normal speed/timbre smoothly. |  | Not Tested | High / P1 |  |  |
| LOFI-003 | Lofi / Persistence | Lofi remembered after relaunch | Lofi enabled; force-stop and relaunch; play again. | 1. Relaunch and play. | Lofi still enabled from app_settings. |  | Not Tested | Medium / P2 |  |  |
| LOFI-004 | Lofi / Seek | Seek while lofi on | Lofi playing. | 1. Seek mid-track. | No harsh fade-in restart; effect already settled. |  | Not Tested | Medium / P2 |  |  |
| LOFI-005 | Lofi / With EQ | Lofi then equalizer chain | Lofi on; EQ enabled with audible preset. | 1. Play and toggle EQ. | Both effects audible; no crash. EQ after lofi in chain. |  | Not Tested | Medium / P2 |  |  |

### 11. Equalizer (`EQ`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| EQ-001 | Equalizer / Entry | Open from Now Playing and Profile | Signed in. | 1. Now Playing → Equalizer tile.<br>2. Back; Profile → Equalizer. | Equalizer screen opens both ways; Now Playing collapses when opening from tile. |  | Not Tested | High / P1 |  |  |
| EQ-002 | Equalizer / Enable | Master switch on/off | On Equalizer; music playing. | 1. Enable EQ.<br>2. Disable. | Tone changes when on; flat/bypass when off. equalizer_active reflected on Now Playing tile when non-flat. |  | Not Tested | High / P1 |  |  |
| EQ-003 | Equalizer / Bands | Adjust band sliders | EQ on. | 1. Move several of the 7 bands.<br>2. Listen. | Gains apply in real time; preset becomes Custom as designed. |  | Not Tested | High / P1 |  |  |
| EQ-004 | Equalizer / Presets | Select built-in presets | EQ on. | 1. Pick multiple genre presets from grid/dropdown. | Bands jump to preset curves; audio matches. |  | Not Tested | High / P1 |  |  |
| EQ-005 | Equalizer / Effects | Bass Boost, Virtualizer, Loudness, Preamp | EQ on. | 1. Toggle Bass Boost, Virtualizer, Loudness.<br>2. Move preamp −12…+12. | Each switch changes sound; no harsh clipping thanks to limiter/headroom. Settings persist after relaunch. |  | Not Tested | High / P1 |  |  |
| EQ-006 | Equalizer / Scope | EQ not on trim/stems/sing-along files | EQ heavily boosted; export a clip or play a raw stem file in another app if available. | 1. Save a clip via Cut and save.<br>2. Play clip (EQ may still apply in-app player — note: processor applies to player sink). Document that exported files themselves are not EQ-baked. | Exported M4A is dry (no baked EQ). In-app playback of any file still goes through player EQ when enabled — expected. |  | Not Tested | Medium / P2 |  |  |

### 12. AI Vocal Remover / Stems (`SEP`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| SEP-001 | Vocal separation / Enqueue | Separate from song menu | Signed in; eligible device (64-bit, ≥5 GiB RAM, model present); Trial/Pro or Free with remaining quota. | 1. Song menu → separate vocals.<br>2. Dismiss/confirm time notice if shown.<br>3. Open queue. | Job queued; progress pill and/or notification appear; status progresses to ready. |  | Not Tested | Critical / P0 |  |  |
| SEP-002 | Vocal separation / Notice | Don’t show again on time notice | Notice not hidden yet. | 1. Start separation.<br>2. Check Don’t show again on SeparationNoticeDialog.<br>3. Confirm.<br>4. Separate another song. | First shows estimate dialog; subsequent skips notice. |  | Not Tested | Medium / P2 |  |  |
| SEP-003 | Vocal separation / Model missing | ModelMissingDialog | Debug build without bundled model or cleared model (if testable). | 1. Request separation. | ModelMissingDialog with import path; no silent fail. |  | Not Tested | High / P1 |  |  |
| SEP-004 | Vocal separation / Multi-select | Separate multiple songs | Select 3 eligible songs; Free with ≥3 remaining or Pro. | 1. Bulk Separate. | All granted jobs queue in order; processed one at a time. |  | Not Tested | High / P1 |  |  |
| SEP-005 | Vocal separation / Now Playing | Separator chip/tile | Current song not yet separated. | 1. Use Separator from Now Playing. | Same enqueue flow as menu. |  | Not Tested | High / P1 |  |  |
| SEP-006 | Vocal separation / Progress | Notification cancel | Job actively processing. | 1. From notification, tap Cancel. | Job cancels; usage reservation released on Free; song can be retried later. |  | Not Tested | High / P1 |  |  |
| SEP-007 | Vocal separation / Stem mode | Switch Original / Instrumental / Vocals | Stem set ready for current song. | 1. On Now Playing use StemModeSelector.<br>2. Seek, pause, resume while switching. | Audio switches stem without losing position. Mode persists in settings. |  | Not Tested | Critical / P0 |  |  |
| SEP-008 | Vocal separation / Export | Export stems to Music/MP3 Studio Stems | Ready stems. | 1. From separation UI, export instrumental and/or vocals. | Files appear under Music/MP3 Studio Stems; playable externally. |  | Not Tested | High / P1 |  |  |
| SEP-009 | Vocal separation / Free partial | Multi-select exceeds remaining quota | Free with 2 remaining; select 4 songs. | 1. Bulk separate. | PartiallyQueued snackbar (e.g. Queued 2… skipped…); See plans action. Upgrade if none remaining. |  | Not Tested | High / P1 |  |  |
| SEP-010 | Vocal separation / Offline Free | NeedsInternet when Free offline | Free plan; airplane mode. | 1. Try to separate a new song. | NeedsInternet (or equivalent); job not reserved. Cached Trial/Pro within grace may still queue. |  | Not Tested | High / P1 |  |  |
| SEP-011 | Vocal separation / Storage | Insufficient free space | Fill storage until &lt;1 GiB free if feasible. | 1. Request separation. | Clear storage error; no crash. |  | Not Tested | Medium / P2 |  |  |
| SEP-012 | Vocal separation / Recents | Swipe recents pauses separation | Separation running. | 1. Swipe app from recents.<br>2. Relaunch. | Separation pauses; song returns to queue. Relaunch/resume continues queue. |  | Not Tested | Critical / P0 |  |  |
| SEP-013 | Vocal separation / Sign-out | Sign-out cancels separation work | Job queued/running. | 1. Log out. | Work cancelled; song returns to queue behavior per AuthEffects; controls stop. |  | Not Tested | High / P1 |  |  |
| SEP-014 | Vocal separation / Settings | Charging only / pause low battery | Profile → Vocal Separation settings. | 1. Enable charging only; unplug; queue a job.<br>2. Enable pause on low battery with low battery simulator if available. | Processing waits/pauses per toggles; resumes when conditions OK. |  | Not Tested | Medium / P2 |  |  |
| SEP-015 | Vocal separation / Retry | Retry failed job | A failed separation job in queue. | 1. Tap Retry. | New reservation (Free); job runs again. |  | Not Tested | High / P1 |  |  |
| SEP-016 | Vocal separation / Process | Playback survives separator process issues | Music playing; separation running in :separator process. | 1. Play continuously during separation.<br>2. If possible, stop separator process only (advanced). | Main playback should not die when separator process crashes; failure surfaces on job. |  | Not Tested | High / P1 |  |  |
| SEP-017 | Vocal separation / Already done | Re-separate already completed song | Song already has stem set. | 1. Request separation again. | Skipped as already done/active; no duplicate waste of quota. |  | Not Tested | Medium / P2 |  |  |
| SEP-018 | Vocal separation / Queue UI | Processing and ready lists | Mix of queued, running, ready jobs. | 1. Open Profile → Vocal Separation → queue screen. | Processing and ready sections accurate; tap ready song can play with stems. |  | Not Tested | High / P1 |  |  |
| SEP-019 | Vocal separation / Sing-along entry | Separate from sing-along sheet | Trial/Pro; no stems; open Sing Along. | 1. Choose separate first from the sheet. | Separation enqueue starts for that song; after ready, sing-along can proceed. |  | Not Tested | High / P1 |  |  |
| SEP-020 | Vocal separation / Thermal | Long job on warm device | Separate a long track; device warm. | 1. Let job run; watch for pause/slowdown messaging if any. | Job completes or pauses safely for heat; no main-app crash; estimate may update via speed factor. |  | Not Tested | Medium / P2 |  |  |
| SEP-021 | Vocal separation / Eligibility | Unsupported low-RAM / 32-bit device messaging | If a low-RAM or unsupported ABI device is available (release is arm64). | 1. Attempt separation. | Clear unsupported reason; cannot enqueue. |  | Not Tested | Medium / P2 |  |  |
| SEP-022 | Vocal separation / Progress pill | On-screen progress pill | Job running; main UI visible. | 1. Observe floating SeparationProgressPill.<br>2. Tap if it navigates to queue. | Pill shows progress; interaction opens queue as designed. |  | Not Tested | Medium / P2 |  |  |

### 13. Metronome and BPM Detection (`MET`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| MET-001 | Metronome / Tab | Start and stop metronome | Metronome tab. | 1. Tap Start.<br>2. Tap Stop. | Clicks audible; dial pulses; notification with Stop appears while running. Music can play simultaneously without ducking. |  | Not Tested | Critical / P0 |  |  |
| MET-002 | Metronome / BPM | Set BPM via dial, slider, +/- | Metronome tab. | 1. Change BPM with +/−, slider, and tap number to type.<br>2. Try 20 and 300. | BPM clamps 20–300; click rate matches. Settings persist after debounce. |  | Not Tested | High / P1 |  |  |
| MET-003 | Metronome / Time signature | Presets and custom beats | Metronome tab. | 1. Select 2/4, 3/4, 4/4, 6/8.<br>2. Open Custom; set beats 1–12. | Accent pattern matches; custom applies. |  | Not Tested | High / P1 |  |  |
| MET-004 | Metronome / Sound | Classic, WoodBlock, SoftBeep and mute | Metronome running. | 1. Open Settings sheet; switch sounds and volume.<br>2. Tap Sound mute control. | Click timbre/volume change. Mute keeps beat/visuals without click; not persisted. |  | Not Tested | Medium / P2 |  |  |
| MET-005 | Metronome / Accent | Accent first beat toggle | Metronome running 4/4. | 1. Toggle Accent. | First beat accented when on. |  | Not Tested | Medium / P2 |  |  |
| MET-006 | Metronome / Sheet | Open from Now Playing | Song playing. | 1. Tap Metronome tile.<br>2. Back closes sheet. | MetronomeSheet with detection UI; works same as tab extras. |  | Not Tested | High / P1 |  |  |
| MET-007 | Metronome / Detect | Detect disabled without song | Nothing playing. | 1. View Detect BPM. | Disabled with Play a song to detect its BPM. |  | Not Tested | Medium / P2 |  |  |
| MET-008 | Metronome / Detect | Successful detect with ÷2 / ×2 | Trial/Pro; rhythmic song playing. | 1. Detect BPM.<br>2. Use ÷2 and ×2. | BPM set; confidence shown; half/double scale BPM. Meter card may appear. |  | Not Tested | High / P1 |  |  |
| MET-009 | Metronome / Meter | Apply low-confidence meter pill and Undo | Detection returns Low confidence meter suggestion. | 1. Tap Use it? if shown.<br>2. Undo snackbar action if Medium/High auto-apply. | Signature applies/restores as documented. |  | Not Tested | Medium / P2 |  |  |
| MET-010 | Metronome / Sync | Sync with song | Detection done; song playing. | 1. Enable Sync with song.<br>2. Pause song.<br>3. Seek. | Clicks align to beats; silent while paused; re-align after seek. |  | Not Tested | High / P1 |  |  |
| MET-011 | Metronome / Notification | Stop from notification | Metronome running; screen off optional. | 1. Tap Stop on notification. | Metronome stops; notification clears. |  | Not Tested | High / P1 |  |  |
| MET-012 | Metronome / Mini | Mini player metronome indicator | Metronome running; mini player visible. | 1. Observe mini player. | Metronome indicator visible. |  | Not Tested | Low / P3 |  |  |
| MET-013 | Metronome / Cache | Cached tempo on Free without new detect | Song previously detected on Trial; now Free. | 1. Open metronome for that song without tapping Detect if cache auto-fills.<br>2. Ensure new Detect still locked. | Cached tempo may display; new analysis locked. |  | Not Tested | Medium / P2 |  |  |

### 14. Sing-Along Recording (`SA`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| SA-001 | Sing-along / Gate | Free blocked | Free plan. | 1. Tap Sing Along. | Upgrade sheet; lock icon. |  | Not Tested | Critical / P0 |  |  |
| SA-002 | Sing-along / Stems | No instrumental offers separate first | Trial/Pro; song without stems. | 1. Open Sing Along setup. | Failure/offer to separate first (NoStems). Cannot record without instrumental. |  | Not Tested | High / P1 |  |  |
| SA-003 | Sing-along / Happy path | Record, review, save | Trial/Pro; instrumental ready; mic granted; headphones recommended. | 1. Start with count-in on.<br>2. Sing a few seconds.<br>3. Stop.<br>4. Adjust voice/music/timing.<br>5. Save with a name. | Flow Preparing→CountIn→Recording→Review→Saving→Saved. File in Music/MP3 Studio Recordings with logo cover; appears in library. |  | Not Tested | Critical / P0 |  |  |
| SA-004 | Sing-along / Count-in | Count-in off starts immediately | Setup sheet; disable count-in if option present. | 1. Start take. | Goes to Recording without 3-beat count-in (or as UI specifies). |  | Not Tested | Medium / P2 |  |  |
| SA-005 | Sing-along / Too short | Take under 1 second fails | Ready to record. | 1. Start and stop immediately (&lt;1 s). | TooShort failure; can retake. |  | Not Tested | Medium / P2 |  |  |
| SA-006 | Sing-along / Retake | Retake from review | In Review after a take. | 1. Retake.<br>2. Complete another take. | Reuses same start position; previous take replaced for session. |  | Not Tested | High / P1 |  |  |
| SA-007 | Sing-along / Mix | Live preview sliders | In Review. | 1. Move voice gain, instrumental gain, offset ±300 ms. | Preview updates live; soft limiter prevents extreme clipping. |  | Not Tested | High / P1 |  |  |
| SA-008 | Sing-along / Interrupt | Pause/focus loss ends take to Review | Recording in progress. | 1. Trigger pause (call, unplug, or sleep timer) ~400 ms. | Take ends to Review (or Failed if too short); playback/lofi/stem restored. |  | Not Tested | High / P1 |  |  |
| SA-009 | Sing-along / Notification | RecordingService Stop | During Preparing/Recording. | 1. Use Stop on mic foreground notification. | Recording stops safely; session ends appropriately. |  | Not Tested | High / P1 |  |  |
| SA-010 | Sing-along / Lofi restore | Lofi restored after session | Lofi was on before sing-along. | 1. Complete or close sing-along. | Lofi and previous stem mode restored. |  | Not Tested | Medium / P2 |  |  |
| SA-011 | Sing-along / Sign-out | Sign-out cancels session | Sing-along active. | 1. Log out from another path if possible or finish then log out mid-prepare. | Session cancelled; TrimEditor/sing-along cleaned; Sign-in shown. |  | Not Tested | High / P1 |  |  |
| SA-012 | Sing-along / Storage | Not enough space to save | Very low storage. | 1. Try Save. | Save fails with storage error; returns to Review. |  | Not Tested | Medium / P2 |  |  |

### 15. Audio Trimming and Ringtones (`TRIM`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TRIM-001 | Clips / Cut and save | Export clip with custom name | Signed in; song available. | 1. Song menu → Cut and save.<br>2. Adjust handles; preview loops.<br>3. Save with name. | TrimEditor opens full screen. Clip saved to Music/MP3 Studio Clips as M4A with logo cover; appears in library. Undo offered. |  | Not Tested | Critical / P0 |  |  |
| TRIM-002 | Clips / Handles | Nudge 0.1 s and min length 1 s | In trim editor Cut mode. | 1. Drag handles.<br>2. Use nudge buttons.<br>3. Try range &lt;1 s. | Range clamps to minimum 1000 ms; nudges 100 ms. |  | Not Tested | High / P1 |  |  |
| TRIM-003 | Clips / Ringtone | Set as ringtone / notification / alarm | WRITE_SETTINGS grantable; disposable defaults OK. | 1. Set as ringtone.<br>2. Pick Ringtone, then repeat for Notification and Alarm if desired.<br>3. Undo. | Defaults to first 30 s selection; warning if &gt;40 s. System default changes; Undo restores previous URI. |  | Not Tested | Critical / P0 |  |  |
| TRIM-004 | Clips / Permission denied | Ringtone without write settings | Deny WRITE_SETTINGS. | 1. Complete ringtone flow without granting. | Snackbar with link to sound settings; no crash. |  | Not Tested | High / P1 |  |  |
| TRIM-005 | Clips / Preview focus | Trim preview pauses main playback | Main player playing; open trim. | 1. Start preview in editor. | Main playback pauses (preview takes audio focus). Preview stops on ON_STOP. |  | Not Tested | Medium / P2 |  |  |
| TRIM-006 | Clips / Sign-out | TrimEditor finishes on sign-out | Trim editor open; sign out from notification path if possible — or open trim then sign out via ADB/account on another device foreground. | 1. Sign out while TrimEditorActivity is open. | Trim activity finishes. |  | Not Tested | Medium / P2 |  |  |
| TRIM-007 | Clips / Cancel export | Leave editor without saving | In trim editor after adjusting range. | 1. Back/close without save. | No clip written; return to main UI. |  | Not Tested | Medium / P2 |  |  |
| TRIM-008 | Clips / Storage | Not enough storage | Low free space. | 1. Attempt save. | NotEnoughStorage (or mapped TrimError) shown. |  | Not Tested | Medium / P2 |  |  |

### 16. Home Screen Widget (`WGT`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| WGT-001 | Widget / Signed in | Add widget and control playback | Signed in; place widget on home screen. | 1. Play a song in app.<br>2. Use widget play/pause, next, previous.<br>3. Tap widget body. | Widget shows title/artist (and art when wide). Controls work. Tap opens Now Playing. |  | Not Tested | Critical / P0 |  |  |
| WGT-002 | Widget / Sizes | SMALL / MEDIUM / WIDE layouts | Resize widget if launcher allows. | 1. Resize across ~110, 180, 250 dp widths. | SMALL: buttons; MEDIUM+: title/artist; WIDE: artwork. |  | Not Tested | Medium / P2 |  |  |
| WGT-003 | Widget / Signed out | Signed-out content | Widget added; log out. | 1. Observe widget.<br>2. Tap it. | Shows Sign in to MP3 Studio without controls; tap opens sign-in. |  | Not Tested | Critical / P0 |  |  |
| WGT-004 | Widget / Resume | Play from widget with saved session | Signed in; queue previously saved; app not in foreground. | 1. Tap play on widget. | Service starts and resumes saved session while signed in. |  | Not Tested | High / P1 |  |  |
| WGT-005 | Widget / Idle | Empty queue skip | Signed in; no queue. | 1. Tap next on widget. | Skip does nothing harmful; no crash. |  | Not Tested | Low / P3 |  |  |

### 17. Settings, About and Appearance (`SET`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| SET-001 | Settings / Appearance | Theme System / Light / Dark | Profile → Appearance. | 1. Switch System, Light, Dark. | UI theme updates immediately; persists after relaunch. |  | Not Tested | High / P1 |  |  |
| SET-002 | Settings / Appearance | Dynamic colour Android 12+ | API 31+. | 1. Toggle dynamic colour. | Palette follows wallpaper when on; Neon Midnight when off. |  | Not Tested | Medium / P2 |  |  |
| SET-003 | Settings / About | About links and version | Profile → About. | 1. Verify versionName shown (no need for build number).<br>2. Open website, email, privacy, terms.<br>3. Long-press a link to copy if supported. | Links open correctly; privacy note mentions no ads and local music. Version matches build under test. |  | Not Tested | High / P1 |  |  |
| SET-004 | Settings / Licenses | Open-source licenses list | About → Licenses. | 1. Open several license entries. | HT-Demucs, ONNX, AndroidX, fonts, etc. readable; back to About works. |  | Not Tested | Low / P3 |  |  |
| SET-005 | Settings / Library | Hide duplicates toggle from Library settings | Profile → Library. | 1. Toggle Hide duplicates. | Matches DUP cases; setting persists. |  | Not Tested | Medium / P2 |  |  |

### 18. Android Lifecycle and Background (`LIFE`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| LIFE-001 | Lifecycle / Rotation | Rotate during playback and sheets | Playing; open Up Next or Metronome sheet. | 1. Rotate device several times. | State preserved via ViewModel/saveable; no duplicate players; no crash. |  | Not Tested | High / P1 |  |  |
| LIFE-002 | Lifecycle / Background | Home then return | Playing. | 1. Press Home 30 s.<br>2. Return via launcher/recents. | Music continues (unless OEM killed). UI restores signed-in state; verifyAccount runs. |  | Not Tested | High / P1 |  |  |
| LIFE-003 | Lifecycle / Recents swipe | All foreground work stops | Simultaneously: music, metronome, sing-along prep or record if safe, separation running. | 1. Swipe app from recents. | Music stops; metronome stops; sing-along cancelled; separation paused. No orphaned notifications continuing work. |  | Not Tested | Critical / P0 |  |  |
| LIFE-004 | Lifecycle / Back vs swipe | Back from root does not equal task removal | On Library root with nothing expanded. | 1. Press Back to leave app (activity finishes).<br>2. If music was playing, note behavior vs recents swipe. | Back finishing activity must not be treated like recents swipe incorrectly for separation resume rules; document observed behavior. Recents swipe is the stop-everything path. |  | Not Tested | Medium / P2 |  |  |
| LIFE-005 | Lifecycle / Multi-user | SignedInViewModelScope clears on user switch | Sign out and sign in as different Google user. | 1. Note first user’s queue/settings in memory.<br>2. Switch account. | Per-user ViewModels cleared; no leaking previous user’s in-memory player controller state into the new user (local files may still be on device). |  | Not Tested | High / P1 |  |  |

### 19. Network and Offline (`NET`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| NET-001 | Network / Offline use | Stay signed in offline | Signed in; airplane mode. | 1. Browse library, play local music, use lofi/EQ/playlists. | Local features work. Token refresh failure keeps stored session user signed in. |  | Not Tested | Critical / P0 |  |  |
| NET-002 | Network / Foreground | Ignored transient network errors on verify | Flaky network; signed in. | 1. Toggle airplane quickly while foregrounding. | User not spuriously signed out on generic network errors. |  | Not Tested | High / P1 |  |  |
| NET-003 | Network / Entitlements | Failed entitlements keeps cache | Had valid plan cache; go offline; open Plans Refresh. | 1. Refresh while offline. | Cache retained; UI may show stale warning; not wiped to empty incorrectly. |  | Not Tested | High / P1 |  |  |
| NET-004 | Network / Cleartext | No cleartext HTTP to backend | Proxy/mitm optional; normal use. | 1. Use sign-in and plan refresh. | Traffic HTTPS only (network_security_config). No cleartext backend calls. |  | Not Tested | Medium / P2 |  |  |

### 20. Performance, Memory and Battery (`PERF`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PERF-001 | Performance / Library | Scroll large library | Library with hundreds/thousands of songs if available. | 1. Fast-fling Songs, Albums, Artists.<br>2. Expand mini player while scrolling. | No multi-second freezes or ANR. Art loads without blocking scroll indefinitely. |  | Not Tested | High / P1 |  |  |
| PERF-002 | Performance / Playback | Long session 30+ minutes | Charging optional. | 1. Play continuous queue 30+ minutes with screen off. | No unexpected stop (aside from OEM battery). No severe audio glitches. |  | Not Tested | High / P1 |  |  |
| PERF-003 | Performance / Separation | Memory during separation + playback | Start separation while playing other music. | 1. Monitor for OOM crashes of main app. | Main process survives; separator isolated. Device may thermal-throttle but should recover. |  | Not Tested | High / P1 |  |  |
| PERF-004 | Performance / Battery | Battery drain observation | Play 20 minutes screen off; note battery %. | 1. Record start/end battery.<br>2. Compare roughly to another music app if possible. | No extreme drain (subjective). WAKE_LOCK usage expected for playback. |  | Not Tested | Medium / P2 |  |  |
| PERF-005 | Performance / Startup | Cold start to interactive | Force-stop; launch. | 1. Time splash until Library interactive. | Splash waits for theme + auth ready; should dismiss within a few seconds (auth Loading timeout 8 s worst case). |  | Not Tested | High / P1 |  |  |

### 21. In-App Rating Prompt (`REV`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| REV-001 | In-app review / Feature threshold | Soft prompt after 3 feature uses | Fresh install prefs (or clear review DataStore keys); signed in; eligible plan for features. | 1. Perform three counted actions among: successful separation enqueue; turn Lofi on; open ringtone maker; start sing-along; successful BPM detect.<br>2. Ensure no blocking overlays. | Soft rating dialog appears with stars Lottie, Rate and Not now. Stars not individually tappable. |  | Not Tested | High / P1 |  |  |
| REV-002 | In-app review / Idle | Soft prompt after 10 minutes idle | Signed in; eligible; no completed/dismissed cooldown. | 1. Leave UI idle 10+ minutes without interaction (or test build with shortened clock if available). | Soft prompt may show once per idle stretch when eligible. |  | Not Tested | Medium / P2 |  |  |
| REV-003 | In-app review / Not now | Dismiss cooldown 10 days | Soft prompt visible. | 1. Tap Not now.<br>2. Trigger feature uses again same day. | Prompt hidden; does not reappear until 10-day cooldown (unless process/test override). |  | Not Tested | High / P1 |  |  |
| REV-004 | In-app review / Rate | Rate opens Play listing | Soft prompt visible; device with Play Store. | 1. Tap Rate. | In-App Review may or may not show UI; Play Store listing for com.autoomstudio.mp3studio always opens afterward. Soft prompt never again on that install. |  | Not Tested | High / P1 |  |  |
| REV-005 | In-app review / Overlay gate | No prompt over blocking UI | Eligible to show; open expanded Now Playing or sing-along overlay or separation notice. | 1. Trigger eligibility while overlay up. | Dialog not shown until overlays clear. |  | Not Tested | Medium / P2 |  |  |

### 22. Cross-Cutting Errors and Recovery (`ERR`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ERR-001 | Error handling / Missing queue song | Deleted file in saved session | Save session including a song; delete file from device; relaunch. | 1. Restore playback session. | Restore skips missing IDs; plays next survivor; no crash. |  | Not Tested | High / P1 |  |  |
| ERR-002 | Error handling / Rapid tap | Spam play/pause and navigation | Signed in. | 1. Rapidly tap play/pause, tabs, and expand/collapse player. | No crash; UI remains consistent. |  | Not Tested | Medium / P2 |  |  |
| ERR-003 | Error handling / During separation kill | Force-stop mid separation | Job running. | 1. Force-stop app.<br>2. Relaunch. | Job recovers/reconciles to a sane queued/failed/paused state; no corrupt half-committed stems played as ready. |  | Not Tested | High / P1 |  |  |
| ERR-004 | Error handling / Auth misconfig | Build without Google client (debug) | Only if a build intentionally lacks google.webClientId. | 1. Open sign-in. | Button indicates Sign-in isn’t set up… rather than crashing. |  | Not Tested | Low / P3 |  |  |
| ERR-005 | Error handling / Share interrupt | Incoming call during sing-along record | Recording. | 1. Simulate call / focus loss. | Take ends cleanly to Review/Failed; mic service stops; no stuck notification. |  | Not Tested | High / P1 |  |  |
| ERR-006 | Error handling / OEM battery | App killed by battery saver | Enable aggressive battery saver; play music background. | 1. Observe whether OEM stops playback.<br>2. Use Profile battery optimization guidance. | Document OEM behavior; ignoring optimizations should improve continuity. Not necessarily a bug if OEM kills anyway. |  | Not Tested | Medium / P2 |  |  |
| ERR-007 | Error handling / Waveform fail | Trim editor unsupported/corrupt file | Corrupt or exotic codec file. | 1. Open Cut and save on that file. | Waveform failure disables editing with error; no crash. |  | Not Tested | Medium / P2 |  |  |
| ERR-008 | Error handling / Playlist name | Invalid playlist name | Playlists tab. | 1. Create playlist with empty name or whitespace only. | Validation rejects; no empty-named playlist created. |  | Not Tested | Medium / P2 |  |  |
| ERR-009 | Error handling / Concurrent features | Metronome + lofi + EQ + stems together | Trial/Pro; stems ready. | 1. Enable lofi, EQ, stem Instrumental, metronome sync, play. | Audio remains stable; stopping one feature does not break others. |  | Not Tested | High / P1 |  |  |
| ERR-010 | Error handling / Locale RTL | RTL language smoke (if available) | Device language Arabic/Hebrew. | 1. Open Library, Now Playing, Profile. | Layouts mirror reasonably; no clipped critical controls; playback still works. |  | Not Tested | Low / P3 |  |  |

### 23. Device Compatibility (`COMPAT`)

| Test Case ID | Module / Feature | Test Scenario | Pre-conditions | Step-by-Step Test Instructions | Expected Result | Actual Result | Status (Pass / Fail / Blocked / Not Tested) | Severity / Priority | Tester Comments | Bug / Issue Reference |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| COMPAT-001 | Device compatibility / minSdk | Install and run on API 26–28 device | Physical/emulator API 26–28. | 1. Install release/debug build.<br>2. Sign in, grant storage, play a song, open Profile. | App runs; legacy storage paths work for save/delete as applicable. |  | Not Tested | Critical / P0 |  |  |
| COMPAT-002 | Device compatibility / modern | Install and run on API 34–37 | Device/emulator API 34+. | 1. Sign in; grant READ_MEDIA_AUDIO and notifications.<br>2. Exercise playback and one studio feature. | Permissions model works; foreground service types OK; no startup crash. |  | Not Tested | Critical / P0 |  |  |
| COMPAT-003 | Device compatibility / Tablet or fold | Large screen smoke | Tablet or unfolded foldable if available. | 1. Use Library + Now Playing + Profile. | UI usable; no permanently unreachable controls. |  | Not Tested | Low / P3 |  |  |
| COMPAT-004 | Device compatibility / Dark light | System dark/light follow | Appearance set to System. | 1. Toggle system dark mode. | App follows; sign-in remains force-dark when signed out. |  | Not Tested | Medium / P2 |  |  |

---

## Sign-off

| Role | Name | Date | Signature / ack |
| --- | --- | --- | --- |
| QA lead |  |  |  |
| Engineering |  |  |  |
| Product |  |  |  |

