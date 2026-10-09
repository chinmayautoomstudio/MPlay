# Settings, About, theme and shared components

> Status: Shipped | Added in: 1.0 (About in 3.1, Profile tab unreleased) | Last updated: 2026-10-09

## Summary

The last bottom tab is Profile, and its icon is the account photo. The Profile screen shows the photo, name, email and an Edit button ([accounts](accounts.md)), a subscription card, an Admin card (admins only), then cards for Appearance (System/Light/Dark, dynamic colour on Android 12+), Library (hide and review duplicates), Vocal Separation, Background Playback (battery optimisation) and About, and finally Log out. Each card opens its own page. About MP3 Studio shows the version, Autoom Studio links, a privacy note, the privacy policy and terms links, and open-source licenses. The UI uses the "Neon Midnight" purple palette, or wallpaper colours.

## Key files

Paths are relative to `app/src/main/java/com/autoomstudio/mp3studio/`.

| File | Role |
|---|---|
| `data/settings/AppSettings.kt` | DataStore `app_settings`; `ThemeMode`, `ThemeSettings`, `SeparationSettings`; all app-wide keys. |
| `ui/settings/SettingsViewModel.kt` | `theme: StateFlow<ThemeSettings?>` (null until loaded), theme setters, `claimNotificationPrompt()`. |
| `ui/settings/ProfileScreen.kt` | Profile tab: header, identity row, subscription card, category cards, Log out; `ProfileTabIcon` for the bottom bar. |
| `ui/settings/SettingsPages.kt` | Sub-pages `AppearanceSettingsScreen`, `LibrarySettingsScreen`, `SeparationSettingsScreen`, `PlaybackSettingsScreen`, and the shared `SubPageHeader`. |
| `ui/settings/EditProfileScreen.kt`, `AccountViewModel.kt` | Edit page (photo, name, Delete account), shared `Avatar` and `LogOutDialog`; documented in [accounts](accounts.md). |
| `ui/settings/AboutScreen.kt` | Logos, version (no build number), website, email, privacy note, privacy policy, terms, licenses. |
| `ui/settings/LicensesScreen.kt` | Hand-maintained license list and detail view. |
| `ui/theme/Color.kt`, `Theme.kt`, `Type.kt`, `Motion.kt` | Palette, `MPlayAppTheme`, typography, motion durations and transitions. |
| `ui/components/*` | Shared components: `ArtworkImage`, `ComingSoon`, `Interactions` (`pressBounce`, `popOnChange`), `LoadingIndicator`, `MPlayTopBar`, `MPlayWordmark`, `NowPlayingBars`, `SelectionTopBar`, `ThemedLottie`, `LofiWaveIcon`, `MetronomeIcons`. |

## How it works

- `MainScreen` keeps `ProfilePage` (`Main`, `EditProfile`, `Appearance`, `Library`, `Duplicates`, `Separation`, `SeparationQueue`, `Playback`, `About`, `Licenses`, `Plans`, `Admin`). Sub-pages go back to `Main`, except Duplicates (back to Library), the separation queue (back to Separation) and Licenses (back to About). `MPlayTopBar` is hidden on `Main`, because the Profile screen draws its own back button and "Profile" title. That back button returns to the tab that was open before Profile.
- The subscription card shows "MP3 Studio" plus the plan name (Free, Trial or Pro). Below that it shows the days left in the trial, or the days until the Pro subscription expires or renews; Free users see an upgrade hint. Tapping it opens Plans. The Profile cards use theme colours, so they follow light and dark mode. The layout is compact so the whole screen fits above the mini player and bottom bar on a typical phone: the back button and title share one row, the avatar is 56dp, category rows are about 60dp tall, and Edit is a small pencil-only icon button (content description "Edit").
- `MPlayAppTheme` decides dark/light from the setting (or system), or dark when `forceDark` is set (`MainActivity` sets it while signed out, so the sign-in screen is always dark), runs edge-to-edge with matching bar icons, and uses dynamic colour on API 31+ when enabled, otherwise `NeonDarkColors` / `NeonLightColors`.
- `MainActivity` keeps the splash screen until the theme has loaded.
- The battery item re-checks `isIgnoringBatteryOptimizations` on resume and opens system settings.
- About picks logos by background luminance (the in-app theme doesn't change resource night mode). Links: `https://autoomstudio.com/`, `connect@autoomstudio.com`, `https://autoomstudio.com/privacy-policy`, `https://autoomstudio.com/terms` (`about_terms_url`). Long press copies a link. The privacy note (`about_privacy_note`) says there are no ads, music and recordings stay on the phone, and the Google account details, plan and usage are stored on the backend.
- `MPlayWordmark` draws "MP3" in the neon gradient followed by " Studio".
- Licenses: HT-Demucs (MIT), ONNX Runtime (MIT), AndroidX, Material Icons, Kotlin, Guava, Coil, Lottie (Apache 2.0).
- `ThemedLottie` recolours animations to the theme; `rememberAnimationsEnabled()` respects the system animation scale.
- Motion: `MotionShort/Medium/Long` = 180/300/450 ms, `fadeThrough()`, `sharedAxisX()`.

## Data and persistence

All keys in DataStore `app_settings`:

| Key | Type | Default | Owner |
|---|---|---|---|
| `theme_mode` | String | `System` | Theme |
| `dynamic_color` | Boolean | `false` | Theme |
| `notification_prompt_shown` | Boolean | `false` | Notification permission prompt |
| `last_sleep_minutes` | Int | none | [Playback](playback.md) |
| `lofi_enabled` | Boolean | `false` | [Lofi](lofi-mode.md) |
| `hide_duplicates` | Boolean | `true` | [Duplicates](duplicates.md) |
| `stem_mode` | String | `Original` | [Separation](vocal-separation.md) |
| `separation_charging_only` | Boolean | `false` | Separation |
| `separation_pause_low_battery` | Boolean | `true` | Separation |
| `separation_notice_hidden` | Boolean | `false` | Separation |
| `separation_speed_factor` | Float | none | Separation time estimate |
| `metronome_bpm` | Int | 120 (20-300) | [Metronome](metronome.md) |
| `metronome_beats` | Int | 4 (1-12) | Metronome |
| `metronome_unit` | Int | 4 (4 or 8) | Metronome |
| `metronome_accent` | Boolean | `true` | Metronome |
| `metronome_sound` | String | `Classic` | Metronome |
| `metronome_volume` | Float | 0.8 (0-1) | Metronome |
| `singalong_note_seen` | Boolean | `false` | [Sing-along](sing-along.md) |
| `admin_activity_seen_at` | Long | 0 (epoch ms) | [Admin](admin.md) Activity bell |

Other DataStores: `playback_session`, `library_prefs`, `widget_state`, and `auth_session` (key `session`: the Supabase session, encrypted with Tink, excluded from backup; see [accounts](accounts.md)). The Tink keyset lives in SharedPreferences `auth_keyset_prefs`. DataStore `entitlements` (key `entitlements_json`: the last plan answer from the server, cleared on sign-out; see [plans](plans.md)).

## Manifest, permissions and notifications

Settings pages are Compose screens inside `MainActivity`. `POST_NOTIFICATIONS` is requested once, tracked by `notification_prompt_shown`. The app now uses `INTERNET` and `ACCESS_NETWORK_STATE` for sign-in (see [architecture](../architecture.md#permissions-summary)); the About and permission texts no longer claim the app is offline.

## Tests

- `data/settings/ThemeSettingsTest.kt`: defaults, stored values, unknown-mode fallback.
- No tests for `AppSettings` itself, `MetronomeSettings.of`, or the screens.

## Known limitations and TODOs

- The licenses list is maintained by hand.
- `rememberAnimationsEnabled()` doesn't react to live changes.
- In `e13b2f7` the key `separation_notice_shown` was replaced by `separation_notice_hidden` without a migration, so old values are ignored.

## Change history

| Date | Commit | Change |
|---|---|---|
| 2026-10-02 | `5685cb3`, `ee66e5a` | Settings, theme and shared components. |
| 2026-10-02 | `3039fba` | Splash screen theme colours. |
| 2026-10-03 | `99970fe` | Component animations (`pressBounce`, Lottie). |
| 2026-10-05 | `55e39e6` | Separation settings section. |
| 2026-10-06 | `26863d9` | Licenses screen. |
| 2026-10-06 | `40a4c10` | About screen (version 3.1). |
| 2026-10-06 | `e13b2f7` | Keys `separation_notice_hidden`, `separation_speed_factor`, `metronome_*`, `singalong_note_seen`; `MetronomeIcons`. |
| 2026-10-07 | - | Renamed to MP3 Studio (strings, wordmark). Account card at the top of Settings, Terms link and new privacy text in About, DataStore `auth_session`. |
| 2026-10-08 | - | Plan row on the Account card, Settings > Plans page, DataStore `entitlements` ([plans](plans.md)). |
| 2026-10-08 | - | `MPlayAppTheme(forceDark)` for the always-dark sign-in screen ([accounts](accounts.md)). |
| 2026-10-08 | - | Settings tab replaced by the Profile tab (photo as the tab icon), Profile screen with category cards and separate settings pages; `SettingsScreen.kt` removed. |
| 2026-10-08 | - | Compact Profile layout (one-row header, smaller avatar, cards and text) and a pencil-only Edit button. |
| 2026-10-08 | - | Key `admin_activity_seen_at` for the Admin Activity bell ([admin](admin.md)). |
| 2026-10-08 | - | `ProfileTabIcon` uses the same 24dp slot as other tab icons (ring drawn outside), so the "Profile" label lines up. |
| 2026-10-09 | - | About shows only "Version x.y.z"; the build number is hidden (3.2.1). |
