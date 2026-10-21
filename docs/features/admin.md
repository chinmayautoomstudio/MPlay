# Admin

> Status: Unreleased | Added in: Unreleased | Last updated: 2026-10-21

## Summary

Admin operations run on the server and are meant for the website dashboard ([web Admin PRD](../../MP3-Studio-PRD-Web-Admin-Dashboard.md)). The Android app has no Admin screens, no Admin card on Profile, and no Admin background worker or notifications. An Admin Google account in the app is a normal user: playback, plans and gates work the same. Privileged actions still go through `assert_admin()` and the `admin` Edge Function.

The server still lets an enabled Admin look up users, change roles, disable accounts, grant or remove Pro, add other admins (by email, as an invite if they have not signed in yet), read the audit log and activity feed, list PayU payments, re-check a payment, refund it, cancel or revoke a subscription, and remove a logged-in device. Every change is checked and logged in Postgres.

## Key files

Paths are relative to `supabase/` unless they start with `app/`.

| File | Role |
|---|---|
| `functions/admin/index.ts`, `functions/_shared/admin.ts` | The `admin` Edge Function and its request parser. |
| `migrations/20261010000000_admin.sql` | `admin_invites`, invite handling in `handle_new_user()`, `admin_*` functions. |
| `migrations/20261012000000_admin_activity.sql` | `admin_activity()` for the Activity feed. |
| `migrations/20261015000000_billing_admin.sql` | `admin_list_payments()`, `admin_billing_health()`, `admin_billing_action()`, payments in `admin_user_detail()`. |
| `migrations/20261016000000_admin_revoke_subscription.sql` | `revoke_subscription` admin action. |
| `migrations/20261022000000_user_devices.sql` | `user_devices`, `admin_remove_device()`, devices on `admin_user_detail()`. |
| `seed/first_admin.sql` | First Admin, run by hand. |
| `app/src/main/java/com/autoomstudio/mp3studio/di/AuthEffects.kt` | On startup, cancels leftover WorkManager work named `"admin-activity"` from older installs. |

The Android packages `data/admin/` and `ui/admin/` are gone.

## How it works

- **Who can call it.** The `admin` Edge Function verifies the user token and calls `admin_*` with `p_actor` set to the caller. Each function starts with `assert_admin(p_actor)`, which refuses non-admins and disabled admins (403 `forbidden`). See [backend.md](../backend.md) for the action list.
- **App.** The app does not call `admin`. `Entitlements` no longer maps `role` to `isAdmin`. Profile has no Admin card. `AuthEffects` only cancels the old `"admin-activity"` unique work so an upgrade does not keep polling.
- **Adding admins.** `admin_add_admin` takes an email. If that email already has an account it is promoted (or reported as already an admin). Otherwise it is saved in `admin_invites`; when that Google account first signs in, `handle_new_user()` makes it an admin and removes the invite. Pending invites can be revoked.
- **Granting Pro.** Writes a `subscriptions` row with `provider = 'admin'`, `provider_ref = 'admin:<user id>'`, `status = 'active'`, `payment_status = 'granted'` and the chosen end date (end of that day, local time). Changing the end date updates the same row. Removing Pro marks it `expired` now. The user's plan follows from the normal entitlement rules ([plans](plans.md)). The app still shows that Pro after the next entitlements refresh.
- **Deleted accounts.** When a user deletes their account ([accounts](accounts.md#delete-account-prd-au9-pr4)), the audit log shows "A user deleted their account" by "the account owner". The last enabled Admin cannot delete their account (`account_delete_last_admin`).
- **Refusals.** 409 with a code: `last_admin` (demoting or disabling the last enabled admin), `self` (disabling yourself), `invalid_email`, `invalid_date`. Unknown users are 404. Billing: 409 `not_refundable` and `not_subscribed`, 502 `payu_refused`, 503 `payu_unavailable`.
- **Payments.** `admin_list_payments`, `admin_billing_health` and `admin_billing_action` (`reverify`, `refund`, `cancel_subscription`, `revoke_subscription`) stay on the server. Refunds are full remaining amount unless a later website change adds partial refunds. See [payments](payments.md).

## Data and persistence

- On the phone: no Admin DataStore keys. Older installs may still have unused `admin_activity_seen_at`, `admin_notifications_enabled` and `admin_activity_notified_at` in `app_settings`; they are ignored.
- Server: `admin_invites`, `admin_audit_log` (actions `set_role`, `disable`, `enable`, `invite_admin`, `revoke_invite`, `invite_accepted`, `grant_pro`, `revoke_pro`, `reverify_payment`, `refund_requested`, `cancel_subscription`, `revoke_subscription`, `remove_device`, and `account_deleted` written by `delete_account()`), `subscriptions` rows with provider `admin`, `user_devices`, and the billing tables (`payments`, `webhook_log`, `job_runs`). See [backend.md](../backend.md).

## Manifest, permissions and notifications

No Admin notification channel. `POST_NOTIFICATIONS` remains for playback, separation, metronome and sing-along.

## Tests

- `supabase/tests/admin_test.sql` (pgTAP): non-admin and disabled-admin refusals, last admin and self rules, promote and step down, invites and the sign-up trigger, Pro grant and removal, search, audit rows, `admin_activity`, devices on `admin_user_detail`, `admin_remove_device`, no access for `authenticated`.
- `supabase/tests/billing_flow_test.sql` (pgTAP): admin payment list, billing health and billing action refusals; revoke.
- `supabase/functions/_shared/admin_test.ts` (Deno): request parsing and validation.
- Android `AdminViewModelTest` and `AdminActivityCheckTest` were removed with the in-app screens.

## Known limitations and TODOs

- An invite only matches the exact email (case-insensitive); a different alias of the same Google account is not matched.
- Refunds from the current Admin API are the whole remaining amount; partial refunds are a website / server follow-up ([web Admin PRD](../../MP3-Studio-PRD-Web-Admin-Dashboard.md)).
- Billing health only reports; retrying a flagged webhook is a website follow-up.
- The Activity feed is computed live from `profiles`, `subscriptions` and the audit log: a subscription that later expires drops out of it.

## Change history

| Date | Commit | Change |
|---|---|---|
| 2026-10-21 | | Removed the in-app Admin screens, `data/admin/`, `ui/admin/`, Admin DataStore keys, `AdminActivityWorker` and phone notifications. Server `admin` functions stay for the website dashboard. `AuthEffects` cancels leftover `"admin-activity"` work. |
| 2026-10-21 | | `admin_user_detail` includes `devices`; `removeDevice` / `admin_remove_device` logs a user out of a chosen phone. |
| 2026-10-10 | | The plan card shows whether a PayU subscription is monthly or yearly. |
| 2026-10-08 | | Admin screens, invites, Pro grants and audit log (PRD v3.2 M5). |
| 2026-10-08 | | M6: `account_deleted` audit entries; the store review account gets Pro from the Admin screens ([store review](../store-review.md)). |
| 2026-10-08 | | Dashboard redesign and the Activity bell (`admin_activity()`, `activity` action, `admin_activity_seen_at`). |
| 2026-10-10 | | PayU billing: Payments page with billing health, Re-check with PayU, Refund, Cancel subscription. |
| 2026-10-10 | | Revoke subscription on a user's page. |
| 2026-10-10 | | Pull-to-refresh on every Admin page and a 30 s auto-refresh of the visible page. |
| 2026-10-10 | | Phone notifications for new Activity events. |
| 2026-10-10 | | User page redesigned as a card-based User Profile. |
| 2026-10-10 | | Removed the Cancel subscription button from the User Profile; Revoke subscription remains. |
