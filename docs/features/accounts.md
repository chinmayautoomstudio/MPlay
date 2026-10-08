# Accounts and Google sign-in

> Status: Unreleased | Added in: next version after 3.1 | Last updated: 2026-10-08

## Summary

MP3 Studio requires a Google account (PRD v3.2, AU1-AU11). On first launch, and after logging out, the app shows only the sign-in screen with "Continue with Google"; there is no guest mode and no email or password. While signed out, nothing plays: the widget, the media notification, Bluetooth and headset buttons do nothing. The Profile tab, whose icon is the account photo, shows the photo, name and email. Its Edit page has the name field and Delete account, and Log out is at the bottom of the Profile screen.

## Key files

Paths are relative to `app/src/main/java/com/autoomstudio/mp3studio/`.

| File | Role |
|---|---|
| `data/account/AuthRepository.kt` | Single source of truth: `state: StateFlow<AuthState>`, `awaitReady()`, sign-in, `signOut()`, `deleteAccount()`, `verifyAccount()`, `reduce()`. |
| `data/account/AccountDeletion.kt` | `AccountDeletionBackend` and `SupabaseAccountDeletionBackend` (calls the `delete-account` Edge Function), `DeletionError`, `deletionErrorOf()`. |
| `data/account/AuthState.kt` | `AuthState` (`Loading`, `SignedOut`, `SignedIn`) and `AccountUser`. |
| `data/account/AuthBackend.kt`, `SupabaseAuthBackend.kt` | What the repository needs from the server, and the supabase-kt implementation (`SessionStatus` mapping, `IDToken` sign-in, `profiles.disabled` check). |
| `data/account/SecureSessionStore.kt` | supabase-kt `SessionManager` that stores the session in DataStore `auth_session`, encrypted with Tink AES-256-GCM under an Android Keystore key. |
| `data/account/GoogleSignIn.kt` | Credential Manager `GetSignInWithGoogleOption` with the hashed nonce; returns the Google ID token. |
| `data/account/Nonce.kt` | Raw nonce (32 random bytes, URL-safe Base64) and its SHA-256 hex. |
| `data/account/AuthErrors.kt` | `AuthError` and the mapping from exceptions and GoTrue codes. |
| `data/account/Profile.kt`, `ProfileRepository.kt` | The user's `profiles` row; `refresh()`, `updateDisplayName()`, `clear()`. |
| `ui/auth/SignInScreen.kt`, `AuthViewModel.kt` | Sign-in screen (logo, title, Google button, legal links), error snackbar, foreground account check. |
| `ui/auth/SignInBackdrop.kt` | Sign-in background (`SignInBackdrop`), animated hero (`SignInHero`) and the screen's palette (`SignInColors`). |
| `ui/auth/SignedInViewModelScope.kt` | `ViewModelStore` for signed-in screens, cleared on sign-out. |
| `ui/settings/ProfileScreen.kt` | Profile header (photo, name, email, Edit), Admin card, Log out ([settings](settings-and-about.md)). |
| `ui/settings/EditProfileScreen.kt`, `AccountViewModel.kt` | Edit page (photo, name field, Delete account), `Avatar`, log-out and delete-account dialogs. |
| `di/AuthEffects.kt` | App-wide reactions to sign-in and sign-out (separation, metronome, sing-along, profile, widget). |
| `playback/SignedInMediaButtonReceiver.kt` | Media button receiver that doesn't start playback while signed out. |

## How it works

### Startup gate

1. `MPlayApp.onCreate` (main process) calls `authRepository.start()`, which collects the supabase-kt session status. The state is `Loading` until the saved session has been read.
2. `MainActivity` keeps the splash screen until the theme is loaded and the state isn't `Loading`.
3. `SignedOut` shows `SignInScreen`. `SignedIn` provides a `ViewModelStoreOwner` from `SignedInViewModelScope.storeFor(userId)` as `LocalViewModelStoreOwner` around `MPlayRoot`, so the playback, library and other screen ViewModels belong to that user. Signing out (or another user signing in) clears the store; rotation keeps it.

### Sign-in screen

`SignInScreen` is always dark: `MainActivity` passes `forceDark` to `MPlayAppTheme` while signed out, which also gives light status bar icons. From top to bottom:
- the M mark (`ic_mp3studio_mark`) with a pulsing glow;
- "MP3 Studio" with a gradient "Studio";
- the tagline `signin_tagline`;
- the hero;
- the white "Continue with Google" pill (`ic_google_g`), which shows a spinner and "Signing in…" while `signingIn`;
- the legal text, where Terms of Service and Privacy Policy are `LinkAnnotation.Clickable` links that open `about_terms_url` and `about_privacy_url`.

`SignInHero` draws equalizer bars, four wave layers, sparkles and floating notes on one canvas. All of them run off a single 12-second looping time value that is read only in the draw phase, so the hero redraws each frame without recomposing. Elements fade and slide in one after another on first show (`entrance`), the logo floats and the button glow breathes. When system animations are off (`rememberAnimationsEnabled()`), the screen appears at once with a still frame.

### Signing in

`AuthViewModel.signInWithGoogle(activity)`:

1. `Nonce.generate()`; Google gets `Nonce.sha256Hex(raw)`, Supabase gets the raw value.
2. `GoogleSignIn.requestIdToken()` shows the Google account sheet (`GetSignInWithGoogleOption` with `GOOGLE_WEB_CLIENT_ID`).
3. `auth.signInWith(IDToken) { provider = Google; nonce = raw }`. On the server, the `handle_new_user` trigger creates the profile on first sign-in (see [backend](../backend.md)). If an Admin invited that email (`admin_invites`), the new account starts as Admin and the invite is used up ([admin](admin.md)).
4. `verifyAccount()` signs out again if the profile is disabled.

Errors map to `AuthError` and show as a snackbar (`auth_error_*` strings); `AuthViewModel` also logs the exception (`Log.w`, tag `AuthViewModel`). A cancelled sheet shows nothing. Play services reports a failed account check as a cancellation with the message `[16] Account reauth failed.`; `AuthErrors.classify` turns that into `AuthError.AccountUnavailable` ("Google couldn't confirm this account on this phone...") so it isn't silent. Typical causes: no Android OAuth client for this package and signing key, the account isn't a test user while the consent screen is in Testing, or a work account that still needs setup on the device.

### Staying signed in

- supabase-kt refreshes the token automatically (`alwaysAutoRefresh`). A refresh that fails while offline keeps the user signed in with the stored session's user (`AuthRepository.reduce`), so the app works offline.
- `MainActivity.onStart` calls `AuthViewModel.verifyAccount()`: it refreshes the session and reads `profiles.disabled`. Codes `user_banned` (disabled) and `session_not_found`, `session_expired`, `refresh_token_not_found`, `refresh_token_already_used`, `user_not_found` (revoked) sign the user out with a message. Network errors are ignored. It then refreshes the profile and the plan ([plans](plans.md)).

### Signed out (PRD AU4)

- `PlaybackService` rejects every controller and refuses resumption while signed out, and stops and clears the player on sign-out, keeping the saved queue ([playback](playback.md)).
- `SignedInMediaButtonReceiver` doesn't start the service for headset or widget play buttons.
- The widget shows "Sign in to MP3 Studio" without controls ([widget](widget.md)).
- `AuthEffects`, after a real sign-out (not on a cold start without a session): cancels a sing-along session, stops the metronome, clears the profile and the cached plan (`EntitlementsRepository.clear()`, PRD AU6) and calls `SeparationController.onSignedOut()`, which cancels the WorkManager job so the song returns to the queue ([vocal separation](vocal-separation.md)). On sign-in it refreshes the plan and calls `onSignedIn()` to resume the queue. It refreshes the widget on both.
- `TrimEditorActivity` finishes itself on sign-out.

### Profile, edit, log out and delete

The Profile tab icon (`ProfileTabIcon`) and the `ProfileScreen` header show the photo (`Avatar`: Coil, with a person icon as placeholder), name and email. Below them, the subscription card opens Profile > Plans ([plans](plans.md)), and admins get an Admin card that opens the [Admin](admin.md) screens. The profile row wins; the Google account details cover it until it loads. Edit opens `EditProfileScreen`, where Save updates `profiles.display_name` (max 80 characters, trimmed) and shows an error under the field if it fails. Log out (the last card on the Profile screen) asks for confirmation, then runs `AuthRepository.signOut()` on the application scope (local sign-out falls back to clearing the session if the server can't be reached).

### Delete account (PRD AU9, PR4)

Delete account (at the bottom of the Edit page, in the error color) opens a dialog that says what is deleted (account, plan, trial, AI Vocal Separator history; no second trial on signing up again) and what stays on the phone (music, playlists, stems, recordings). Delete is enabled only after typing `DELETE`.

1. `AccountViewModel.deleteAccount()` runs on the application scope and calls `AuthRepository.deleteAccount()`.
2. `SupabaseAccountDeletionBackend` posts `{"confirm": "DELETE"}` to the `delete-account` Edge Function, which calls `delete_account()` for the user in the token. That deletes the `auth.users` row; the foreign keys delete the profile, subscriptions, trial and usage, and keep `trial_claims` and `payment_events` without the user ID ([backend](../backend.md)).
3. On success `AuthBackend.signOutLocally()` clears the session on the phone only (the server no longer knows it), the state becomes `SignedOut`, `AuthEffects` clears the profile and plan as for a log-out, and `UsageReporter.forget()` drops the account's pending usage reports.
4. Refusals keep the user signed in and show in the dialog: `LastAdmin` ("You're the only admin..."), `ActiveSubscription` (a renewing paid subscription, from M4), `Offline`, `Other`.

## Data and persistence

- DataStore `auth_session`, key `session`: the supabase-kt `UserSession` as JSON, encrypted with Tink (associated data `mp3studio.auth.session`) and stored as Base64. If it can't be decrypted it is deleted and the user signs in again.
- SharedPreferences `auth_keyset_prefs`: the Tink keyset `auth_keyset`, wrapped by the Keystore key `mp3studio_auth_master_key`.
- Both are excluded from cloud backup and device transfer (`backup_rules.xml`, `data_extraction_rules.xml`), since the Keystore key can't move with them.
- No Room tables. Server tables are in [backend](../backend.md).

### Build configuration

`app/build.gradle.kts` reads `local.properties` (gitignored) into `BuildConfig`:

| Property | `BuildConfig` field |
|---|---|
| `supabase.url` | `SUPABASE_URL` |
| `supabase.key` | `SUPABASE_KEY` (anon key) |
| `google.webClientId` | `GOOGLE_WEB_CLIENT_ID` (OAuth Web client ID, also configured in GoTrue) |

`check<Variant>BackendConfig` fails release builds when any is blank and warns in debug. Without them the sign-in button shows "Sign-in isn't set up in this build of MP3 Studio." Google Cloud setup, in the same project as the Web client:

- An **Android** OAuth client per signing key, all with package `com.autoomstudio.mp3studio`: the debug keystore, the release keystore, and the Play App Signing key once the app is on Play. The app never uses these IDs; they authorise the package and key to get tokens for the Web client.
- While the consent screen (Audience) is External and in Testing, every account that signs in must be listed under Test users. Publish to Production before release.

## Manifest, permissions and notifications

- `INTERNET` and `ACCESS_NETWORK_STATE`. `USE_BIOMETRIC` and `USE_FINGERPRINT`, merged in by `androidx.credentials`, are removed with `tools:node="remove"`.
- `android:networkSecurityConfig="@xml/network_security_config"` blocks cleartext traffic.
- `playback.SignedInMediaButtonReceiver` replaces Media3's `MediaButtonReceiver` in the manifest.
- No new notifications.

## Tests

- `data/account/AuthErrorsTest.kt`: GoTrue code mapping, exception classification (including wrapped causes and `[16]` reauth failures versus real cancels), which errors end the session.
- `data/account/NonceTest.kt`: SHA-256 hex, nonce length and alphabet.
- `data/account/AuthRepositoryTest.kt`: `reduce()` (offline refresh keeps the user), `verifyAccount()` with a fake backend (disabled, revoked, offline, active), `deleteAccount()` (local sign-out only on success, refusal keeps the user).
- `data/account/AccountDeletionTest.kt`: refusal codes from 409 bodies, other statuses, network failures.
- `supabase/tests/abuse_test.sql`: `delete_account()` removes the user's rows, keeps trial claims and payments, refuses the last Admin and renewing subscriptions; no second trial after deleting.
- Checked live on 2026-10-08: the last-admin refusal in the app, and a full deletion through the Edge Function with a throwaway user.
- `supabase/tests/rls_test.sql`: server-side access rules ([backend](../backend.md#testing-rls)).
- No tests for `SecureSessionStore` (needs the Keystore), the Credential Manager flow, the screens or `AuthEffects`.

## Known limitations and TODOs

- Requires Google Play services; phones without them can't sign in.
- The terms URL (`about_terms_url`, `https://autoomstudio.com/terms`) is a placeholder until the page exists.
- The launcher icon is still the MPlay icon.
- `ic_mp3studio_mark.webp` is a stand-in cut out of `ic_mplay_logo_dark`; replace it with the final transparent M mark (same file name).
- If GoTrue still allows email or phone sign-up, someone could create an account through the API; disable them on the server ([backend](../backend.md#auth-settings)).
- An account disabled by an Admin (Disable account on the Admin user page) is noticed on the next foreground check, not instantly.
- Deleting doesn't revoke the Google grant; signing in again with the same Google account creates a new, empty account (without a trial).
- A deleted account's access token stays valid until it expires (up to an hour) but every call fails, because the user no longer exists.
- MP3 Studio uses a new application ID (`com.autoomstudio.mp3studio`), so old MPlay (`com.autoomstudio.mplay`) installs don't upgrade in place and their data isn't migrated (decided in M6; see [architecture](../architecture.md)).

## Change history

| Date | Commit | Change |
|---|---|---|
| 2026-10-07 | - | Mandatory Google sign-in, encrypted session store, signed-out blocking, Account card (M1). |
| 2026-10-08 | - | Package is now `com.autoomstudio.mp3studio` (Android OAuth clients must use it). `[16] Account reauth failed` shows `AccountUnavailable` instead of failing silently; sign-in failures are logged. |
| 2026-10-08 | - | M2: sign-in and foreground checks refresh the plan, sign-out clears it, Account card has a Plan row. |
| 2026-10-08 | - | M5: invited emails become Admin on first sign-in; Admin row on the Account card for admins. |
| 2026-10-08 | - | M6: Delete account (dialog, `delete-account` function, local sign-out), deletion tests; old MPlay installs are not migrated. |
| 2026-10-08 | - | Redesigned, always-dark sign-in screen with animated hero, glowing logo and Google pill button (`SignInBackdrop.kt`). |
| 2026-10-08 | - | Account card replaced by the Profile tab (photo as tab icon) and an Edit page; Delete account moved into Edit, Log out below About. |
