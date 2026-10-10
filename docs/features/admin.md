# Admin

> Status: Unreleased | Added in: Unreleased | Last updated: 2026-10-10

## Summary

Admins get an Admin card on the Profile screen, below Subscription. It opens the Admin dashboard, with screens to look up users, see each user's plan, trial, subscriptions, payments and AI Vocal Separator usage, change roles, disable accounts, grant or remove Pro with an end date, add other admins (by email, as an invite if they haven't signed in yet), read the audit log, and follow an Activity feed of new sign-ups, subscriptions and deleted accounts. A Payments page lists PayU payments with billing health, and lets admins re-check a payment with PayU, refund it, or cancel a user's subscription ([payments](payments.md)). Every change is checked and logged on the server; the app only decides whether to offer the screens.

## Key files

Paths are relative to `app/src/main/java/com/autoomstudio/mp3studio/` unless they start with `app/` or `supabase/`.

| File | Role |
|---|---|
| `data/admin/AdminModels.kt` | DTOs for every admin response, `UserFilter`, `AddAdminResult`, `AdminError`, `AdminException`. |
| `data/admin/AdminBackend.kt` | `AdminBackend` interface and `SupabaseAdminBackend`, which calls the `admin` Edge Function with an `action`; `errorOf()` maps HTTP status and refusal codes to `AdminError`. `PAGE_SIZE = 50`. |
| `data/plan/Entitlements.kt`, `data/plan/EntitlementsBackend.kt` | `Entitlements.isAdmin`, from `role == "admin"` in the `entitlements` response. |
| `ui/admin/AdminViewModel.kt` | Page back stack (`AdminPage`: Home, User, Admins, Usage, Audit, Activity, Payments), loading state per page, `refresh()` and `refreshing` for pull-to-refresh, `autoRefresh()` and `AUTO_REFRESH_MILLIS`, debounced user search, payment search and filter (`PaymentListState`), billing health, actions, one-shot `AdminMessage`s (including `Billing(result)`), `closed` when access is lost, `activity` and `unread` for the bell. |
| `ui/admin/AdminPaymentsScreen.kt` | Payments page: billing health card (last run of each job, overdue jobs, flagged and failed webhooks, unconfirmed mandate cancellations, payments to review), search by email, transaction ID or PayU reference, filter chips, `AdminPaymentItem` (Re-check with PayU, Refund, Open user), `RefundDialog`. |
| `ui/admin/AdminScreen.kt` | Host (back handling, messages, the `PullToRefreshBox` around every page, the auto-refresh loop) and the dashboard: wordmark header with the Admin chip and bell, stats card, shortcut cards, Users card (pill search, filter chips, `AdminUserRowItem` with the "..." menu and its confirm dialog, paging). |
| `ui/admin/AdminUserScreen.kt` | User detail and actions, confirm dialogs, `GrantProDialog` (1 month, 3 months, 1 year or a picked date up to 5 years ahead); PayU subscription details, the user's payments and Cancel subscription. |
| `ui/admin/AdminListScreens.kt` | Admins and invites (`AddAdminDialog`), weekly usage with top users, audit log, `AdminActivityScreen`. |
| `ui/admin/AdminCommon.kt` | Error and message texts, date formatting, shared loading and error blocks, `AdminCard`, `IconTile`, `AdminColors`. |
| `ui/settings/ProfileScreen.kt`, `ui/main/MainScreen.kt` | Admin card (shown when `PlanUiState.isAdmin`) and `ProfilePage.Admin`; the app top bar is hidden on the Admin screens. |
| `data/settings/AppSettings.kt` | `adminActivitySeenAt` (key `admin_activity_seen_at`). |
| `supabase/migrations/20261010000000_admin.sql` | `admin_invites`, invite handling in `handle_new_user()`, `admin_*` functions. |
| `supabase/migrations/20261012000000_admin_activity.sql` | `admin_activity()` for the Activity feed. |
| `supabase/migrations/20261015000000_billing_admin.sql` | `admin_list_payments()`, `admin_billing_health()`, `admin_billing_action()`, payments in `admin_user_detail()`. |
| `supabase/functions/admin/index.ts`, `supabase/functions/_shared/admin.ts` | The `admin` Edge Function and its request parser. |

## How it works

- **Dashboard.** The home page draws its own header (MP3 Studio wordmark, Admin chip, bell) instead of the app top bar; system Back leaves it. Below the title are a stats card (Total, Pro, Free trial, Free, Disabled, Admins, and this week's separations, which opens Usage), shortcut cards for Admins, Usage and Audit log, and the Users card. Each user row has an initials avatar with a green (enabled) or grey (disabled) dot, the plan, this week's separations, Admin and Disabled badges, and a "..." menu: View details, Make or Remove admin and Disable (both confirmed) or Enable. Your own row has no Disable.
- **Activity bell.** The dashboard loads `activity` (the newest 50 events) with the overview. `unread` counts events newer than `admin_activity_seen_at` and shows as a red badge. Opening Activity saves the newest event time as seen. Sign-up and subscription rows open the user; deleted-account rows don't. If the `admin` Edge Function hasn't been redeployed with the `activity` action yet, the call fails quietly and no badge shows.
- **Who sees it.** The Admin card shows when the cached entitlements say `isAdmin`. That flag only hides or shows the screens; every call is checked again on the server.
- **Calls.** `SupabaseAdminBackend` sends `POST /functions/v1/admin` with `{"action": ..., ...}`. The function verifies the user token, validates the body (`parseAdminRequest`), and calls the matching `admin_*` Postgres function with `p_actor` set to the caller. Each function starts with `assert_admin(p_actor)`, which refuses non-admins and disabled admins (403 `forbidden`). See [backend.md](../backend.md) for the action list.
- **Refusals.** Some requests are refused with 409 and a code: `last_admin` (demoting or disabling the last enabled admin), `self` (disabling yourself), `invalid_email`, `invalid_date`. Unknown users are 404. The app shows a snackbar for each (`AdminError`).
- **Losing access.** If a call returns 403 (you were demoted or disabled elsewhere), `AdminViewModel` shows "Admin access was removed", refreshes your plan and closes the screens.
- **Acting on yourself.** Changing your own role or Pro refreshes your own entitlements so Settings and gates update immediately. The Disable button is hidden on your own detail page.
- **Adding admins.** Admins page > Add admin takes an email. If that email already has an account it is promoted (or reported as already an admin). Otherwise it is saved in `admin_invites`; when that Google account first signs in, `handle_new_user()` makes it an admin and removes the invite. Pending invites can be revoked.
- **Granting Pro.** Writes a `subscriptions` row with `provider = 'admin'`, `provider_ref = 'admin:<user id>'`, `status = 'active'`, `payment_status = 'granted'` and the chosen end date (end of that day, local time). Changing the end date updates the same row. Removing Pro marks it `expired` now. The user's plan follows from the normal entitlement rules ([plans](plans.md)).
- **Deleted accounts.** When a user deletes their account ([accounts](accounts.md#delete-account-prd-au9-pr4)), the audit log shows "A user deleted their account (Free plan)" by "the account owner". The entry has no email and doesn't open a user page, since the account no longer exists. The last enabled Admin can't delete their account.
- **Refreshing.** Every Admin page can be pulled down to fetch it again (`AdminViewModel.refresh()`); the indicator stays until every part of the page has answered, the page's own spinners stay hidden, and errors show as usual. While the Admin screen is on screen and the app is in the foreground, the visible page also refreshes itself every 30 s (`AUTO_REFRESH_MILLIS`, `autoRefresh()`) without any indicator; a failed auto-refresh keeps the data already shown. Auto-refresh is skipped while an action or a pull is running, and leaves alone the Users, Payments and Audit lists once "Load more" has been used, so the scroll position holds.
- **Search and paging.** The search box matches email or name (300 ms debounce) with a filter (All, Pro, Free trial, Free, Disabled, Admins); 50 users per page with "Load more". The audit log pages by entry id.
- **Payments.** The Payments shortcut on the dashboard opens the payment list (filters All, Paid, Failed, Pending, Refunded, Disputed, Past due, Cancelled; 50 per page) under a billing health card. Each payment shows the user, amount, status, kind, method, PayU reference and any refund in progress. "Re-check with PayU" (`reverifyPayment`) re-runs the status check and applies the result. "Refund" (confirmed) asks PayU to refund what hasn't been refunded yet; it is offered only for a paid payment with a PayU reference and no refund in progress (`AdminPaymentRow.refundable`), and the result arrives later through the `sweep` job or a webhook. A user's page shows the same list for that user, their PayU subscription (autopay status, grace end, mandate end, cancellation pending) and "Cancel subscription", which stops renewals and revokes the mandate; Pro stays until the paid period ends. "Revoke subscription" (confirmed) ends PayU Pro now: it shows while a PayU subscription still gives Pro (`active`, `past_due`, or `cancelled` with time left; `AdminUserDetail.revocableSubscription`), expires it, stops renewals and cancels any live mandate with PayU. Nothing is refunded (use Refund on the payment). If PayU doesn't confirm the mandate cancellation, Pro is still gone and the `renewals` job retries it ("revoke pending"). Admin-granted Pro keeps its own Remove Pro. Cancelling or revoking your own subscription refreshes your plan.
- **Billing refusals.** 409 `not_refundable` and `not_subscribed`, 502 `payu_refused` (PayU said no) and 503 `payu_unavailable` (PayU secrets not set or PayU unreachable).

## Data and persistence

- On the phone: the existing `entitlements` cache ([plans](plans.md)) and the DataStore key `admin_activity_seen_at` (Long, epoch ms of the newest Activity event seen; [settings](settings-and-about.md)).
- Server: `admin_invites`, `admin_audit_log` (actions `set_role`, `disable`, `enable`, `invite_admin`, `revoke_invite`, `invite_accepted`, `grant_pro`, `revoke_pro`, `reverify_payment`, `refund_requested`, `cancel_subscription`, `revoke_subscription`, and `account_deleted` written by `delete_account()` when a user deletes their own account), `subscriptions` rows with provider `admin`, and the billing tables (`payments`, `webhook_log`, `job_runs`). See [backend.md](../backend.md).

## Manifest, permissions and notifications

None. Uses the existing network access.

## Tests

- `app/src/test/.../ui/admin/AdminViewModelTest.kt`: initial load, page back stack, debounced search, pull-to-refresh indicator and errors, quiet auto-refresh that keeps data on failure, refusal message, own role change refreshes the plan, 403 closes the screens, Activity unread count cleared by opening the feed, Payments page loads health and filtered payments, refund, cancel and revoke results and refusals, the refundable and revocable rules.
- `supabase/tests/billing_flow_test.sql` (pgTAP): admin payment list, billing health and billing action refusals; revoke (non-admin refused, `not_subscribed`, an active subscription with a mandate and a cancelled one both end now, revoking twice changes nothing).
- `app/src/test/.../data/plan/EntitlementsResponseTest.kt`: `role` maps to `isAdmin`.
- `supabase/tests/admin_test.sql` (pgTAP): non-admin and disabled-admin refusals, last admin and self rules, promote and step down, invites and the sign-up trigger, Pro grant and removal, search, audit rows, `admin_activity` (refusal, sign-up, subscription and deleted events, newest first), no access for `authenticated`.
- `supabase/functions/_shared/admin_test.ts` (Deno): request parsing and validation, including `activity` limits.
- Gap: no UI (Compose) tests.

## Known limitations and TODOs

- An invite only matches the exact email (case-insensitive); a different alias of the same Google account isn't matched.
- Refunds are always for the whole remaining amount; partial refunds are done in the PayU dashboard and picked up from the refund webhook.
- Billing health only reports; there is no button to retry a flagged webhook from the app.
- The Activity feed is computed live from `profiles`, `subscriptions` and the audit log: a subscription that later expires drops out of it, and unread is tracked per phone, not per admin.
- The Admin card appears only after entitlements refresh (sign-in or app start), so a newly promoted admin may need to reopen the app.

## Change history

| Date | Commit | Change |
|---|---|---|
| 2026-10-08 | | Admin screens, invites, Pro grants and audit log (PRD v3.2 M5). |
| 2026-10-08 | | M6: `account_deleted` audit entries; the store review account gets Pro from the Admin screens ([store review](../store-review.md)). |
| 2026-10-08 | | Dashboard redesign (wordmark header, stats card, shortcut cards, Users card with "..." menu) and the Activity bell (`admin_activity()`, `activity` action, `admin_activity_seen_at`). |
| 2026-10-10 | | PayU billing: Payments page with billing health, Re-check with PayU, Refund, Cancel subscription; PayU subscription details and payments on the user page; provider labels are now Admin and PayU only. |
| 2026-10-10 | | Revoke subscription on a user's page: ends PayU Pro now, cancels any mandate, no refund; audit labels for cancel and revoke. |
| 2026-10-10 | | Pull-to-refresh on every Admin page and a 30 s auto-refresh of the visible page while it is on screen. |
