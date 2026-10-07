# MP3 Studio: Product Requirements Document (v3.2)

**Features:** App rename, Google login, Free and Pro plans, 30-day trial, usage limits, payments, in-app Admin **Product:** MP3 Studio (formerly MPlay), an Android music player and audio toolkit **Platform:** Android (native Kotlin, Jetpack Compose), Supabase backend **Version:** 3.2 **Status:** Draft

---

## 1. Overview

v3.2 turns the app into **MP3 Studio**, a product with user accounts and two plans:

- **Accounts:** Signing in is mandatory. Users must sign in with Google before they can use any part of the app, including music playback; there is no way to skip it. Google is the only sign-in method (no email and password). Sessions persist.
- **Plans:** A **Free** plan and a **Pro** plan at **₹99 per month**.
- **Trial:** Every new user gets a **30-day free trial** of the premium features.
- **Limits:** Free users can use the AI Vocal Separator for **10 songs per week**. BPM Detector and Sing Along need Pro (or the trial).
- **Admin:** Users with the Admin role get Admin screens inside the app to manage users, subscriptions, trials and usage.
- **Backend:** Supabase provides authentication, the database, role-based access and server-side enforcement of plans and limits.

Music, recordings and all audio processing stay on the phone. Only account, subscription and usage data is stored on the server. Audio files are never uploaded.

## 2. Problem Statement

The app has grown from a music player into a set of powerful tools, including an on-device AI vocal separator. Heavy features carry real cost to maintain and need a sustainable business model. To offer a free tier and a paid tier, the app needs accounts, plan management, reliable payments, usage limits that cannot be bypassed from the UI, and a way for the owner to manage users.

## 3. Goals and Non-Goals

**Goals**

- Rename the product to "MP3 Studio" everywhere users see it.
- Require a Google account to use the app, with secure sign-in and persistent sessions.
- Give each user a clear plan (Free, Pro or Trial), with feature access decided by the server.
- Track AI Vocal Separator usage and enforce the weekly limit on the server.
- Activate Pro only after a verified payment, and return users to Free automatically when it ends.
- Prevent trivial repeat trials through multiple accounts.
- Let Admins manage users, roles, subscriptions and usage from inside the app.
- Support both direct-APK and Google Play distribution over time without rewriting the plan logic.

**Non-Goals (v3.2)**

- Ads of any kind (the app stays ad-free).
- Using the app without signing in (no guest mode).
- Email and password sign-in, or any sign-in method other than Google.
- Uploading, streaming or storing users' music or recordings on a server.
- A separate web admin dashboard (Admin lives inside the app for now).
- Plans beyond Free and Pro, yearly billing, coupons or referral rewards.
- Social features, public profiles or sharing between users.

## 4. Target Users

- **Users (Free, Trial, Pro):** People with a Google account who play their own music and want tools such as trimming, ringtones, a metronome and vocal separation.
- **Admins:** The owner and authorized team members who manage accounts and subscriptions.

## 5. Platform and Technical Constraints

| Area | Decision |
| --- | --- |
| Backend | Supabase: Auth, Postgres with Row Level Security, Edge Functions for anything that needs secrets or the service role |
| Sign-in method | Google only, using the Android Credential Manager and the Google ID token passed to Supabase. Email and password sign-in is disabled in Supabase |
| Session storage | Persistent session kept in secure storage on the device |
| Permissions | INTERNET and network state are now required. Cleartext (non-HTTPS) traffic is disabled. The previous "no INTERNET permission" build check is replaced by a check that only the expected permissions are present |
| Server is the source of truth | Plan, trial, subscription state and usage limits are decided and validated by the backend. The app only displays and caches them |
| Entitlement cache | The app caches the user's entitlements so it works briefly offline (see section 6.3) |
| Distribution | Both direct APK and Google Play are planned. Payments sit behind a provider interface: Razorpay (or UPI subscriptions) for the direct APK, Google Play Billing for the Play build. Two build flavors by distribution channel |
| Payment verification | Payment results are confirmed on the server (Razorpay webhooks, Google Play purchase verification and real-time notifications). The app never trusts a client-side "payment successful" message |
| Secrets | The Supabase service role key, payment secrets and webhook secrets exist only in server-side functions, never in the app |
| App ID | The application ID stays the same so existing installs upgrade in place and keep their data. Only the display name changes |
| Time zone | Server times in UTC; dates shown to users in their local time; weekly reset boundary defined in India Standard Time |

## 6. Functional Requirements

Priority: **P0** = must ship, **P1** = should ship, **P2** = nice to have.

### 6.1 Rename to MP3 Studio

| ID | Requirement | Priority |
| --- | --- | --- |
| RN1 | Show "MP3 Studio" as the app name in the launcher, title bars, splash screen, notifications, widget, About screen, sign-in screen and store metadata | P0 |
| RN2 | Replace the MPlay wordmark and logo with MP3 Studio branding (design assets supplied by Autoom Studio) | P0 |
| RN3 | New saved files (clips, recordings, stems) use "MP3 Studio" folder names; existing files in older folders remain visible in the library | P1 |
| RN4 | Update the About text and privacy note to describe accounts and server-stored account data (see PR1) | P0 |

### 6.2 Authentication

| ID | Requirement | Priority |
| --- | --- | --- |
| AU1 | Sign-in is mandatory. Until the user signs in, the app shows only the sign-in screen; there is no skip or "continue without account" option, and no part of the app (including music playback) is usable | P0 |
| AU2 | Google is the only sign-in method. The sign-in screen has a single "Continue with Google" button. The first Google sign-in creates the account | P0 |
| AU3 | Google accounts count as verified, so no separate email verification step is needed | P0 |
| AU4 | While signed out, the home-screen widget, media notification and headset buttons cannot start playback; the widget shows "Sign in to MP3 Studio" and opens the sign-in screen | P0 |
| AU5 | The sign-in screen links to the privacy policy and terms of service, and briefly explains why an account is needed | P0 |
| AU6 | Log out, which stops playback, clears the session and cached entitlements, and returns to the sign-in screen. It does not delete music, playlists, stems or recordings on the device | P0 |
| AU7 | Persistent sign-in: users stay signed in across restarts until they log out. Once signed in, the app opens and plays music offline | P0 |
| AU8 | Clear, friendly error messages (sign-in cancelled, no Google account on the device, Google Play services missing or outdated, no network, account disabled) | P0 |
| AU9 | Account deletion from inside the app, which removes the user's server-side data | P0 (required for the Play build) |
| AU10 | Show profile name, email and photo from the Google account; allow editing the display name | P1 |
| AU11 | A disabled account is signed out at the next online check, returned to the sign-in screen and cannot sign in again | P0 |

### 6.3 Roles, Plans and Access Control

| ID | Requirement | Priority |
| --- | --- | --- |
| RL1 | Two roles: User and Admin. A new account is always a User | P0 |
| RL2 | Roles can be changed only by an Admin, through a server function, never by the user's own request | P0 |
| PL1 | Two plans: Free and Pro (₹99 per month) | P0 |
| PL2 | The server computes each user's entitlements (Free, Trial or Pro) and returns them to the app | P0 |
| PL3 | The app checks entitlements before opening a restricted feature and shows an upgrade prompt when locked | P0 |
| PL4 | Entitlements are cached on the device. Pro and Trial access keeps working offline for a grace window (proposed: 7 days since the last successful online check); after that, premium features lock until the app goes online | P0 |
| PL5 | Free users must be online to start an AI separation, because the weekly limit is checked on the server | P0 |
| PL6 | Plans screen showing what each plan includes, the user's current plan, renewal or expiry date, and trial days remaining | P0 |

**Feature access** (all features need a signed-in account)

| Feature | Free | Pro | 30-Day Trial |
| --- | --- | --- | --- |
| Music player (library, search and sort, playlists, queue, shuffle, repeat, sleep timer, duplicate hiding, widget) | Yes | Yes | Yes |
| MP3 Cutter | Yes | Yes | Yes |
| Ringtone Maker | Yes | Yes | Yes |
| Metronome (manual BPM, tap tempo, time signature) | Yes | Yes | Yes |
| Lofi mode | Yes | Yes | Yes |
| Playing already-separated Instrumental and Vocals only | Yes | Yes | Yes |
| AI Vocal Separator | 10 songs per week | Unlimited | Unlimited |
| BPM Detector (including time signature detection) | No | Yes | Yes |
| Sing Along | No | Yes | Yes |
| Ads | None | None | None |

Existing results stay usable: stems already separated remain playable after the trial ends, and previously detected BPM values are kept. Only new use of a locked feature is blocked.

### 6.4 30-Day Free Trial

| ID | Requirement | Priority |
| --- | --- | --- |
| TR1 | First-time users get a 30-day trial of AI Vocal Separator (unlimited), Sing Along and BPM Detector | P0 |
| TR2 | The trial starts automatically at the user's first Google sign-in and is recorded with start and end dates | P0 |
| TR3 | When the trial ends, the user returns to the Free plan: separator limited to 10 songs per week, Sing Along and BPM Detector locked | P0 |
| TR4 | A user can receive the trial only once. Record whether the trial was used | P0 |
| TR5 | Block repeat trials through new accounts: one trial per normalized email (ignoring Gmail dots and plus aliases) and per device, checked on the server | P0 |
| TR6 | Show trial days remaining in the app, with a reminder banner in the last 3 days | P1 |
| TR7 | A user who buys Pro during the trial gets Pro immediately; the remaining trial time is not carried over | P1 |

### 6.5 AI Vocal Separator Usage Limit

| ID | Requirement | Priority |
| --- | --- | --- |
| US1 | Free users get 10 separations per week. Pro and Trial users are unlimited | P0 |
| US2 | The app asks the server for permission before a job starts (a reservation). The server grants it only if the user has quota left | P0 |
| US3 | A job counts as one use only when it finishes successfully. Cancelled or failed jobs release the reservation and do not count | P0 |
| US4 | Playing a song that is already separated does not count | P0 |
| US5 | The limit is checked on the server, so changing the app UI or clearing app data cannot grant extra uses. Parallel requests cannot exceed the limit | P0 |
| US6 | Show usage in the app, for example "7/10 songs used this week, 3 remaining", with the reset day | P0 |
| US7 | When the limit is reached, show a clear message with the reset date and an upgrade option, and do not start the job | P0 |
| US8 | The weekly count resets automatically at the start of each week (proposed: Monday 00:00 India Standard Time) with no manual action and no scheduled job needed | P0 |
| US9 | Every separation request and outcome is recorded for admin reporting | P1 |

Example: **AI Vocal Separator, 7/10 songs used this week. 3 songs remaining.**

### 6.6 Payments and Subscription

| ID | Requirement | Priority |
| --- | --- | --- |
| PY1 | Pro costs ₹99 per month | P0 |
| PY2 | Payments go through a provider interface with two implementations: Razorpay (or UPI subscriptions) for the direct APK, and Google Play Billing for the Play build | P0 |
| PY3 | Pro access is activated only after the server confirms a successful payment | P0 |
| PY4 | Record these events: payment initiated, successful, failed; subscription activated, renewed, cancelled, expired | P0 |
| PY5 | When a subscription expires or a renewal fails (after any grace period set by the provider), the user returns to the Free plan automatically | P0 |
| PY6 | Store subscription start date, expiry date, next billing date, last payment date, payment status and cancellation status for each user | P0 |
| PY7 | Cancelling stops renewal and keeps Pro until the paid period ends | P0 |
| PY8 | Users can cancel or manage the subscription from inside the app (a link to the Play subscription page on the Play build) | P1 |
| PY9 | "Restore purchase" for users who reinstall or switch phones | P1 |
| PY10 | Payment webhooks and notifications are idempotent: repeats never double-activate or double-count | P0 |
| PY11 | Show the payment and billing status in the Plans screen | P1 |

### 6.7 Admin (inside the app)

| ID | Requirement | Priority |
| --- | --- | --- |
| AD1 | Admin screens appear only for users whose server-side role is Admin | P0 |
| AD2 | User list with search and filters: Pro, Free, Trial, disabled | P0 |
| AD3 | User detail: name, email, role, plan, subscription status and dates, trial status and dates, payment status, AI separator usage | P0 |
| AD4 | Overview counts: total users, Pro users, Free users, users in trial | P1 |
| AD5 | AI Vocal Separator usage view per user and overall | P0 |
| AD6 | Change a user's role (User or Admin); the last remaining Admin cannot be demoted | P0 |
| AD7 | Disable or enable a user account | P0 |
| AD8 | View payment and subscription events | P0 |
| AD9 | Every admin action is checked on the server and written to an audit log (who, what, when) | P0 |
| AD10 | Admins cannot disable their own account | P1 |
| AD11 | The first Admin is created directly in the database, never through the app | P0 |

### 6.8 Security

| ID | Requirement | Priority |
| --- | --- | --- |
| SE1 | Row Level Security is enabled on every table | P0 |
| SE2 | Users can read only their own profile, subscription, trial and usage rows | P0 |
| SE3 | Users cannot change their own role, plan, trial, subscription or usage values. They can edit only safe profile fields such as name and photo | P0 |
| SE4 | Admin data and actions are reachable only through server checks that confirm the Admin role | P0 |
| SE5 | Plan and quota checks run on the backend, never only in the app | P0 |
| SE6 | The service role key and payment secrets are never shipped in the app | P0 |
| SE7 | All traffic uses HTTPS. Webhook signatures are verified. Google ID tokens are verified by Supabase, with a nonce to prevent replay | P0 |
| SE8 | Entitlements delivered to the app are signed or short-lived, so a stale or tampered cache can be rejected | P1 |
| SE9 | Rate limits on sign-in, reservation and trial claim endpoints | P1 |

### 6.9 Privacy and Compliance

| ID | Requirement | Priority |
| --- | --- | --- |
| PR1 | Update the privacy note: the app has no ads; music and recordings stay on the phone and are never uploaded; account, subscription and usage data is stored securely on our servers | P0 |
| PR2 | Provide a privacy policy and terms of service, linked from the sign-in screen and the About screen | P0 |
| PR3 | Collect only the data needed: name, email and photo from the Google account, role, plan, trial, subscription, payment status and usage counts. No audio, no contacts, no location | P0 |
| PR4 | Account deletion removes server-side data except records the law requires to keep (such as payment records) | P0 |
| PR5 | Provide a Google test account for store reviewers (required because sign-in is needed to open the app) | P1 |

I am not a lawyer; confirm privacy and payment rules (including India's data protection law and recurring-payment rules) with a qualified adviser before release.

### 6.10 Existing Feature Review

The existing app was reviewed against this plan. Everything below is kept and mapped to a plan; nothing is removed. Every feature now needs a signed-in account.

| Existing feature | Plan |
| --- | --- |
| Music player, library, search and sort, playlists, queue, shuffle and repeat | Free |
| Duplicate hiding, sleep timer, home-screen widget | Free |
| Trim and save, Ringtone maker | Free |
| Metronome (manual BPM, tap tempo, time signature, player control) | Free |
| Lofi mode | Free |
| AI Vocal Separator and Instrumental / Vocals only playback | Free with 10 songs per week; unlimited for Pro and Trial |
| Separation estimates and notices, stem cache and export | Free |
| BPM detection and time signature detection | Pro and Trial |
| Sing Along (recording and mixing) | Pro and Trial |
| About screen | Free (text updated) |

New screens from this update: the sign-in screen is shown to signed-out users; the Plans screen is available to every signed-in user; the Admin screens need the Admin role.

## 7. Backend Data Model (product level)

| Table | Purpose |
| --- | --- |
| profiles | User ID, name, email, photo, role, disabled flag, created date |
| subscriptions | Plan, status, provider, provider reference, start date, expiry date, next billing date, last payment date, payment status, cancel-at-period-end |
| trials | Trial status, start date, end date, whether it was used |
| trial_claims | Normalized email hash and device hash with unique rules to block repeat trials |
| payment_events | Every payment and subscription event with type, provider, time and reference (no card data stored) |
| ai_usage | One row per separation: user, song reference, reserved time, completed time, status, week |
| admin_audit_log | Admin actions: who, what, target user, when |

**Server functions:** claim trial, get entitlements, reserve separation, complete or release separation, create payment or verify purchase, payment webhook and Play notification handlers, admin set role, admin disable or enable account, admin user and usage queries.

## 8. Non-Functional Requirements

- **Security:** RLS everywhere; secrets only on the server; entitlement and quota decisions made server-side; residual risk acknowledged below.
- **Reliability:** The first sign-in needs a network connection. After that, the app opens and plays music offline. Network failures show clear messages and never corrupt local data. Payment handling is idempotent.
- **Performance:** Entitlement checks use the cache and add no visible delay; Google sign-in completes within a few seconds on a normal connection.
- **Privacy:** No audio leaves the device; data collection is limited to what is listed in PR3.
- **Compatibility:** Android 8.0 and above. Google Play services and a Google account are required, because Google is the only sign-in method; devices without Play services cannot use the app.
- **Accessibility:** Sign-in, plans and admin screens support font scaling, content descriptions and large touch targets.
- **Currency:** Prices shown in rupees (₹99 per month).

**Residual risk:** AI separation, BPM detection and Sing Along run on the phone. A determined user who modifies the app could bypass local checks. Server-validated quotas, signed entitlements and (on Play) device integrity checks reduce this but cannot remove it entirely.

## 9. User Flows

1. **First launch:** Open app → sign-in screen → Continue with Google → pick a Google account → account created and trial starts → main app with a trial banner.
2. **Returning user:** Open app → already signed in → entitlements refreshed in the background → main app.
3. **Log out:** Settings → Log out → playback stops, session cleared → sign-in screen.
4. **Separate a song (Free):** Separate vocals → server checks quota → time notice → job runs → counts as one use when finished → "7/10 used this week".
5. **Limit reached:** Separate vocals → "Weekly limit reached, resets Monday" → upgrade option.
6. **Locked feature:** Tap Sing Along or Detect BPM on Free → upgrade sheet showing Pro benefits and ₹99 per month.
7. **Upgrade:** Plans → Go Pro → payment → server confirms → Pro activated.
8. **Expiry or failed renewal:** Subscription ends → server updates → app returns to Free automatically.
9. **Admin:** Admin → Users → search or filter → open a user → change role or disable account → action recorded in the audit log.
10. **Delete account:** Settings → Delete account → confirm → data removed → signed out to the sign-in screen.

## 10. Screens

- Sign-in (single "Continue with Google" button, privacy policy and terms links)
- Profile and account (edit name, log out, delete account)
- Plans and subscription (plan comparison, status, trial days, billing dates, usage)
- Upgrade and payment
- Usage indicator for AI Vocal Separator and limit-reached message
- Locked feature prompts
- Admin: user list, user detail, overview, usage, payment events, audit log
- About (updated with MP3 Studio branding and privacy note)

## 11. Success Metrics

- Every row of the feature access table is verified by tests for Free, Trial, Pro, expired and disabled users.
- Sign-in tests prove that a signed-out user cannot reach any screen except sign-in, and that the widget, notification and headset buttons cannot start playback while signed out.
- Row Level Security tests prove a normal user cannot read other users' data or change their own role, plan, trial or usage.
- Quota tests prove that 20 parallel separation requests at 9 of 10 used grant exactly one.
- Trial abuse tests prove a second account from the same email alias or device does not get a trial.
- Payment tests prove that duplicate webhooks never double-activate Pro, and that expiry and failed payments return the user to Free.
- Offline tests prove Pro and Trial features work within the grace window and lock after it.
- Admin tests prove non-admins cannot reach any admin function, and every admin action is logged.

## 12. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Google Play generally requires Play Billing for digital subscriptions | Use Play Billing in the Play flavor and Razorpay only in the direct flavor; confirm current policy before release |
| Recurring payment rules for UPI and cards in India | Use the provider's subscription product and confirm rules with the provider |
| Users get repeat trials with new accounts | Server-side checks on normalized email and device; accept that a determined user with several Google accounts can still evade |
| Client-side checks bypassed in a modified app | Server quotas, signed short-lived entitlements, device integrity checks on Play |
| Mandatory login frustrates users, and existing users face a new login wall even for playback | One-tap Google sign-in, clear explanation on the sign-in screen, keep all local data intact, offline use after sign-in |
| Users without Google Play services or a Google account cannot use the app at all | Accept for v3.2; show a clear message on such devices; revisit other sign-in methods in future scope |
| Users locked out when offline or when the server is down | Persistent session so signed-in users keep playing offline; offline grace window for cached Pro and Trial access; clear messages |
| Free users cannot separate offline because quota needs the server | Explain in the UI; keep all other free features fully offline |
| Admin tools expose sensitive data | Role checks on the server, audit log, least-privilege access, protected first admin |
| Supabase or Google sign-in outage | New sign-ins fail with a clear message; existing sessions and cached entitlements keep working; retries; idempotent handlers |
| Privacy and legal obligations of collecting accounts and payments | Privacy policy and terms, account deletion, minimal data, adviser review |
| Google sign-in misconfiguration (signing keys differ per distribution) | Register the signing certificates of every build and store signing key in the Google and Supabase setup |
| Webhook delays make payment look unconfirmed | Show "Confirming payment" state and refresh entitlements when confirmed |

## 13. Milestones

| Phase | Scope |
| --- | --- |
| M1: Foundation | Rename to MP3 Studio, Supabase project, data model, RLS, mandatory Google sign-in, persistent sessions, sign-in gate for the app, widget and media controls, privacy note update |
| M2: Plans and gating | Profiles and roles, entitlements, feature gates and upgrade prompts, trial with abuse checks, plans screen |
| M3: Usage limit | Reserve and complete separation functions, weekly limit, usage display, limit-reached messages, admin usage records |
| M4: Payments (direct APK) | Provider interface, Razorpay subscription, webhooks, activation, expiry and downgrade, cancel and restore |
| M5: Admin | In-app admin screens, server admin functions, audit log |
| M6: Hardening | RLS and quota tests, offline grace, abuse tests, migration of existing installs, account deletion, store review credentials |
| M7: Play Billing | Play flavor, purchase verification and notifications, Play policy checks (when the Play release is scheduled) |

## 14. Future Scope

Yearly plan, coupons and referral rewards, a web admin dashboard, trial-ending push reminders, family or device-limit rules, more Pro features, promotional pricing, and additional sign-in methods for devices without Google.

## 15. Decisions

- **Distribution and payments:** Both direct APK and Google Play are planned. Razorpay (or UPI subscriptions) is built first for the direct APK; Play Billing follows when the Play release is scheduled. The plan logic is shared.
- **Login:** Mandatory and cannot be skipped; nothing in the app works while signed out, including playback, the widget and media controls. Google is the only sign-in method. After sign-in, the app works offline using the saved session and cached entitlements.
- **Admin:** Admin screens live inside the app. A web dashboard is deferred.
- **Pricing:** Pro is ₹99 per month.
- **Free separator limit:** 10 songs per week, counted on completion and enforced by the server.
- **Trial:** 30 days, once per user, starting at the first Google sign-in.
- **App ID:** Unchanged, so existing installs upgrade in place.
- **Proposed defaults to confirm:** 7-day offline grace window; weekly reset on Monday 00:00 India Standard Time; Free users must be online to start a separation; already-separated stems and detected BPM values stay usable after the trial.
