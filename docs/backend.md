# Backend (Supabase)

> Last updated: 2026-10-10

MP3 Studio uses a self-hosted Supabase project for accounts, plans, trials, AI Vocal Separator usage and PayU payments. Music, playlists, stems and recordings never leave the phone; only the title of a song sent for separation is stored, in `ai_usage.song_ref`. The app talks to Supabase Auth (GoTrue) and PostgREST with the public anon key; everything privileged runs server-side as the service role.

## Project

| Item | Value |
|---|---|
| URL | `https://securedbmp3.autoomstudio.com` (self-hosted, Postgres 15) |
| Repo folder | [`supabase/`](../supabase) |
| Config | [`supabase/config.toml`](../supabase/config.toml) (`project_id = "mp3-studio"`) |
| Migrations | [`supabase/migrations/`](../supabase/migrations) |
| SQL tests | [`supabase/tests/`](../supabase/tests) (pgTAP: `rls_test.sql`, `plans_test.sql`, `usage_test.sql`, `admin_test.sql`, `abuse_test.sql`, `payments_test.sql`, `billing_flow_test.sql`) |
| Edge Functions | [`supabase/functions/`](../supabase/functions) (`entitlements`, `claim-trial`, `reserve-separation`, `finish-separation`, `admin`, `delete-account`, and for billing `start-checkout`, `payment-status`, `payu-webhook`, `payu-return`, `subscription`, `billing-jobs`) |
| First Admin | [`supabase/seed/first_admin.sql`](../supabase/seed/first_admin.sql) (run by hand, not seeded) |

The app reads the URL and anon key from `local.properties` (`supabase.url`, `supabase.key`) together with `google.webClientId`; see [accounts](features/accounts.md#build-configuration).

## Auth settings

Sign-in is Google only (PRD AU2). `config.toml` turns off email and phone sign-up and enables `[auth.external.google]` with `skip_nonce_check = false`, so the ID token's nonce must match the one the app sends.

On the self-hosted server, `config.toml` isn't applied automatically. Set these in the GoTrue environment as well:

- `GOTRUE_EXTERNAL_GOOGLE_ENABLED=true`, `GOTRUE_EXTERNAL_GOOGLE_CLIENT_ID=<Web client ID>` (the same ID as `google.webClientId`), `GOTRUE_EXTERNAL_GOOGLE_SECRET=<Web client secret>`.
- `GOTRUE_EXTERNAL_EMAIL_ENABLED=false` and `GOTRUE_EXTERNAL_PHONE_ENABLED=false`. As of 2026-10-07 `/auth/v1/settings` still reports email and phone as enabled.

To disable an account, an Admin uses Disable account on the user's Admin page, which sets `profiles.disabled = true` (the app signs out on the next foreground check). Banning the user in GoTrue also works: the refresh fails with `user_banned`.

## Tables

All in schema `public`, all with Row Level Security. `anon` has no access to any of them.

| Table | Purpose | App (`authenticated`) access | Filled by |
|---|---|---|---|
| `profiles` | One row per user: `display_name` (max 80), `email`, `avatar_url`, `role` (`app_role`: `user`/`admin`), `disabled`, `phone` (10-digit Indian mobile for PayU, written only by `begin_checkout()`), `created_at`. | Select own row; update only `display_name` and `avatar_url` (column grants). | Trigger `on_auth_user_created` |
| `subscriptions` | Pro subscriptions: `provider` `payu` or `admin`, `status` `pending`/`active`/`past_due`/`cancelled`/`expired`/`failed`, billing dates, and for PayU the autopay fields (`autopay_status` `on`/`off`/`not_set`/`revoked`, `mandate_ref`, `mandate_start`, `mandate_end`, `billing_anchor_day`, `grace_end`, `mandate_cancel_requested_at`, `mandate_cancelled_at`, `pre_debit_for`, `pre_debit_sent_at`, `renewal_attempts`). At most one live (`pending`, `active`, `past_due`) PayU row per user. Admin grants use `provider_ref = 'admin:<user id>'` and `payment_status = 'granted'`. | Select own rows. | Billing functions, `admin_grant_pro()` |
| `payments` | One row per PayU payment attempt: `txn_id` (ours, also PayU's invoice number), `kind` `first`/`renewal`/`replace`, `status` `created`/`pending`/`success`/`failed`/`cancelled`/`refunded`/`partially_refunded`/`disputed`, `si` (autopay link), `amount_paise`, link URL and expiry, `payu_ref`, `mandate_ref`, `method`, `failure_reason`, period, `resolve_until`, refund and dispute fields. No card, UPI or bank details. `user_id` is cleared on account deletion. | Select own rows (payment history). | Billing functions |
| `webhook_log` | Every PayU webhook, saved (redacted) before processing, with `status` `received`/`processed`/`failed`/`ignored`, retry time and `flagged` after 5 failed tries. | None. | `payu-webhook`, `billing-jobs` |
| `job_runs` | One row per `billing-jobs` run: job, start and end, processed, failed, skipped, error. | None. | `billing-jobs` |
| `trials` | 30-day trial per user. | Select own row. | `claim_trial()` |
| `trial_claims` | Email and device hashes that already used a trial; `user_id` is cleared on account deletion, so the claim survives. | None. | `claim_trial()` |
| `payment_events` | Billing timeline (payment succeeded or failed, refund, dispute, mandate, expiry...), unique per `(provider, provider_event_id)`; `provider` is `payu`, `txn_id` links the payment. | None. | `billing_event()` in the billing functions |
| `ai_usage` | One row per AI Vocal Separator request, unique per `(user_id, job_ref)`: `status` `reserved`, `completed`, `released` or `denied`, `song_ref` (song title, max 200), `reserved_at`, `completed_at`, `week_start` (IST Monday; moved to the completion week on `completed`). | Select own rows. | `reserve_separation()`, `finish_separation()` |
| `admin_audit_log` | Admin actions (`set_role`, `disable`, `enable`, `invite_admin`, `revoke_invite`, `invite_accepted`, `grant_pro`, `revoke_pro`) with actor, target and `details`. | None. | `admin_*` functions, `handle_new_user()` |
| `admin_invites` | Emails (lowercase, trimmed) invited as Admin before they have an account, with `invited_by`. | None. | `admin_add_admin()` |

Functions:

- `handle_new_user()` (security definer, `search_path = ''`): inserts the profile with the email, `full_name`/`name` (cut to 80) and `avatar_url`/`picture` from the Google metadata. `role` is `admin` when the email has a row in `admin_invites` (the invite is deleted and an `invite_accepted` audit row written), otherwise `user`. Not executable by API roles.
- `is_admin()` (security definer): true when the caller's profile is an enabled Admin. Executable by `authenticated` only.
- `compute_entitlements(p_user)` (security definer, `service_role` only): `{plan, role, disabled, trial, subscription, billing, pendingPayment, lastPayment, hasPhone, serverTime}`. Pro when `is_pro_sub()` holds for a subscription: `active` before `expires_at` (one more day when autopay is on, so the hourly debit can run), `cancelled` before `expires_at`, `past_due` before `grace_end`. Otherwise Trial while `trials.ends_at` is in the future, otherwise Free. Returns null when there is no profile. `plan_of()` uses the same rule.
- `claim_trial(p_user, p_email_hash, p_device_hash)` (security definer, `service_role` only): per-user advisory lock; `existing` when the user has a trial, `denied` with reason `account` (disabled), `email` or `device` (hash already in `trial_claims`), otherwise inserts the claim and a 30-day trial and returns `granted`.
- `usage_week_start(ts)` (immutable): the Monday, India Standard Time, of the week containing `ts`. The weekly reset needs no scheduled job (US8).
- `usage_summary(p_user)` (`service_role` only): `{limit: 10, used, reserved, remaining, weekStart, resetsAt, unlimited}`. `used` counts this week's `completed` rows; `reserved` counts `reserved` rows younger than 48 hours (older ones stop holding a use); `unlimited` is true on Trial and Pro.
- `reserve_separation(p_user, p_jobs)` (`service_role` only): per-user advisory lock, so parallel requests can't pass the limit (US5). `{"error": "account"}` for a missing or disabled profile. Known `jobRef`s get their earlier answer. Trial and Pro get every job as `reserved`; Free gets up to `remaining` in the given order and the rest are stored as `denied`. Returns `{granted, denied, usage}`.
- `finish_separation(p_user, p_job_ref, p_outcome)` (`service_role` only): `completed` or `released` for a `reserved` row; a `jobRef` with no row (Trial or Pro queued offline) gets a row with the outcome. Repeats change nothing. Returns `{usage}`.
- Admin functions (M5, security definer, `service_role` only, all take `p_actor` from the verified token and start with `assert_admin(p_actor)`, which raises `42501` unless the actor is an enabled Admin). Refusals come back as `{"error": code}`. Role and disable changes take the advisory lock `admin:roles` so two admins can't remove each other at once. Every change writes `admin_audit_log` through `admin_log()`.
  - `admin_overview`: user counts by plan, disabled and admins, plus this week's completed, reserved and denied separations.
  - `admin_list_users(p_query, p_filter, p_limit, p_offset)`: email or name search, filter `all`/`pro`/`trial`/`free`/`disabled`/`admin`; `{total, users}`.
  - `admin_user_detail(p_user)`: profile, entitlements, usage, 8 weeks of totals, last 50 jobs, subscriptions (with the PayU autopay, grace, mandate end and cancel-pending fields), last 50 payment events (with `txnId`) and the user's `payments`.
  - Billing (migration `20261015000000_billing_admin.sql`): `admin_list_payments(p_query, p_filter, p_limit, p_offset)` (email, transaction ID or PayU reference; filter `all`/`success`/`failed`/`pending`/`refunded`/`disputed`/`past_due`/`cancelled`), `admin_billing_health()` (last run and overdue flag per job, flagged and failed webhooks, unconfirmed mandate cancellations, payment events to review in the last 30 days), `admin_billing_action(p_action, p_user, p_txn)` (`reverify`, `refund`, `cancel_subscription`, `revoke_subscription`: checks and audit-logs the action before the Edge Function calls PayU; `not_found`, `not_refundable`, `not_subscribed`). Revoke (migration `20261016000000_admin_revoke_subscription.sql`) picks the user's PayU subscription that still gives Pro (`active`, `past_due` or `cancelled` with time left); `revoke_subscription(p_sub)` (service role only) then expires it now, stops renewals, marks a live mandate for cancellation (`request_mandate_cancel`) and writes a `subscription_revoked` payment event. Nothing is refunded.
  - `admin_usage`: 8 weeks of totals and this week's top 20 users.
  - `admin_set_role`, `admin_set_disabled`: refuse `last_admin` (the last enabled Admin) and, for disable, `self`.
  - `admin_add_admin(p_email)`: `promoted`, `already_admin` or `invited` (stored in `admin_invites`); `invalid_email`. `admin_list_admins`, `admin_revoke_invite`.
  - `admin_grant_pro(p_user, p_until)`: upserts the admin subscription; `invalid_date` unless the end is in the future and at most 5 years away. `admin_revoke_pro` expires it now.
  - `admin_audit(p_limit, p_before)`: entries newest first, paged by id.
  - `admin_activity(p_limit)` (migration `20261012000000_admin_activity.sql`, Edge Function action `activity` with optional `limit` 1-100, default 50): `{events}` newest first, read only. Each event has `type`, `at`, `userId`, `email`, `name`, `provider`, `plan`: `signup` (one per `profiles` row), `subscribed` (active `subscriptions`, with provider and plan) and `deleted` (`account_deleted` audit rows, plan from `details`, no user or email).
  - Helpers `plan_of(p_user)` (same rule as `compute_entitlements`) and `admin_log()`.
- `delete_account(p_user)` (M6, security definer, `service_role` only): takes the `admin:roles` lock; `{"error": ...}` `not_found`, `last_admin` (the last enabled Admin) or `mandate_active` (a PayU subscription whose autopay is still `on`; the `delete-account` function cancels mandates with PayU before calling it). Otherwise it cancels unpaid payment links, clears link URLs, writes an `account_deleted` audit row (details `{role, plan}`, no email) and deletes the `auth.users` row. The foreign keys cascade to `profiles`, `subscriptions`, `trials` and `ai_usage`, and set `trial_claims.user_id`, `payment_events.user_id`, `payments.user_id`, `admin_audit_log.actor_id` and `admin_invites.invited_by` to null, so deleting and signing up again gives no new trial and payment records are kept (PR4). Returns `{"result": "deleted"}`.

## Edge Functions

Deno functions in `supabase/functions/`, called by the app with the user's access token. All of them verify the caller with `auth.getUser(jwt)` and use the service role from the functions container's environment.

| Function | Request | Response |
|---|---|---|
| `entitlements` | `POST {"deviceId": "<64 hex>"}` (device ID optional) | `compute_entitlements()` plus `trialClaim` and `usage` (`usage_summary()`). Claims the trial first when the user has no trial row. `401` without a valid token, `403 account_disabled`, `404 no_profile`, `500 server_error`. |
| `claim-trial` | `POST {"deviceId": "<64 hex>"}` | `{"result": "granted" \| "existing" \| "denied" \| "unavailable", "reason"?}`; `503` when unavailable. |
| `reserve-separation` | `POST {"jobs": [{"jobRef": "<uuid>", "songRef": "..."}]}`, 1 to 50 distinct lowercase UUIDs | `reserve_separation()`: `{granted, denied, usage}`. `400 bad_request`, `403 account_disabled`. |
| `finish-separation` | `POST {"jobRef": "<uuid>", "outcome": "completed" \| "released"}` | `{usage}`. `400 bad_request`. |
| `delete-account` | `POST {"confirm": "DELETE"}` (exact word) | Cancels live PayU mandates first, then `{"result": "deleted"}`. `400 bad_request` (wrong body), `404 not_found`, `409 {"error": "last_admin" \| "mandate_cancel_failed"}`. |
| `admin` | `POST {"action": ..., ...}`: `overview`, `usage`, `admins`, `users {query, filter, limit, offset}`, `user {userId}`, `setRole {userId, role}`, `setDisabled {userId, disabled}`, `addAdmin {email}`, `revokeInvite {email}`, `grantPro {userId, until}` (ISO time), `revokePro {userId}`, `audit {limit, before}`, `activity {limit}`, `payments {query, filter, limit, offset}`, `billingHealth`, `reverifyPayment {txnId}`, `refundPayment {txnId}`, `cancelSubscription {userId}`, `revokeSubscription {userId}` | The matching `admin_*` result; refund `{"result": "refund_requested"}`, cancel `{"result": "cancelled" \| "cancel_pending"}`, revoke `{"result": "revoked" \| "revoke_pending"}` (pending: Pro ended but PayU hasn't confirmed the mandate cancellation; the `renewals` job retries it; revoking without a mandate doesn't need PayU). `400 bad_request`, `403 forbidden` (not an enabled Admin), `404 not_found`, `409 {"error": "last_admin" \| "self" \| "invalid_email" \| "invalid_date" \| "not_refundable" \| "not_subscribed"}`, `502 payu_refused`, `503 payu_unavailable`. |
| `start-checkout` | `POST {"phone"?: "..."}` | `{url, txnId, mode}` for a new PayU payment link. `400 invalid_phone`, `403 account_disabled`, `409 {"error": "phone_required" \| "payment_in_progress" \| "already_subscribed" \| "mandate_update_pending"}`, `429 rate_limited`, `503 payu_unavailable`. |
| `payment-status` | `POST {"txnId"?: "..."}` (without one: all the caller's open payments are checked and the latest payment is answered) | Checks with PayU, then `payment_summary()`: `{txnId, status, kind, failureReason, resolveUntil, amountPaise, completedAt, plan}`. `404 not_found`. |
| `subscription` | `POST {"action": "cancel"}` | `{"result": "cancelled", "expiresAt"}` or `{"result": "cancel_pending"}`. `409 not_subscribed`. |
| `payu-webhook` | PayU's server notification (form or JSON, max 64 KB), no JWT | `{"result": "received"}`; `500` only when the event couldn't be saved (PayU retries). |
| `payu-return` | PayU's success and failure URLs (`GET` or `POST`), no JWT | `200` HTML return page (`_shared/return_page.ts`, strict CSP, `no-store`) with an intent link to the app's `https://<host of PAYU_RETURN_PAGE>/pay/return?result=...&txn=...`. Reads only `txn` (or `udf1`, then `txnid`, from a POST); changes nothing. |
| `billing-jobs` | `POST {"action": "webhooks" \| "sweep" \| "renewals" \| "expiry" \| "notices"}` with header `x-billing-job-secret`, no JWT | `{action, processed, failed, skipped}`; `401` with a wrong secret, `500 job_failed`. |

`payu-webhook`, `payu-return` and `billing-jobs` have `verify_jwt = false` in `config.toml`: PayU and pg_cron can't send a user token. They are safe to expose because the webhook only triggers a status check with PayU, the return page only links back to the app, and the jobs need the shared secret.

`_shared/trial.ts` normalizes the email from the token (lowercase, `+alias` removed, Gmail dots removed, `googlemail.com` as `gmail.com`) and HMAC-SHA256s it and the device ID with `TRIAL_HASH_PEPPER`. Without the pepper the claim returns `unavailable` and the user stays on Free; `entitlements` still answers.

Deploying on the self-hosted server:

1. Copy `supabase/functions/_shared`, `entitlements`, `claim-trial`, `reserve-separation`, `finish-separation`, `admin` and `delete-account` into the stack's `volumes/functions/` (next to `main` and `hello`). Copy `_shared` again whenever it changes.
2. Add `TRIAL_HASH_PEPPER` (a long random secret, for example `openssl rand -hex 32`) to the `functions` service environment in `docker-compose.yml` / `.env`. Never change it afterwards: existing claims would stop matching.
3. `docker compose up -d --force-recreate functions`.
4. Check: `POST /functions/v1/entitlements` with the anon `apikey` header but no user token returns `401 {"error":"unauthorized"}`.

The live stack is the Coolify service `faifqncpzdrvhsf6zbvzwhvb` on `54.163.241.161` (SSH as `root`), so the paths are `/data/coolify/services/faifqncpzdrvhsf6zbvzwhvb/volumes/functions/` and that folder's `docker-compose.yml` and `.env`. The service is `supabase-edge-functions`. Restart only that container: `docker compose --project-name faifqncpzdrvhsf6zbvzwhvb -f docker-compose.yml --env-file .env up -d --no-deps --force-recreate supabase-edge-functions`. Coolify's compose file gives the container the whole `.env` (`env_file`), so every secret (`TRIAL_HASH_PEPPER`, the `PAYU_*` values, `BILLING_JOBS_SECRET`) only needs a line in `.env`. Coolify regenerates `.env` from its UI variables whenever the service is saved or redeployed there, so **every secret must also be added in the Coolify UI with the same value**. On 2026-10-10 a save from the UI dropped `TRIAL_HASH_PEPPER`, which had only been in `.env`. The old value was lost, so a new one was generated. Only the 4 existing trial claims, all from accounts that still exist, were hashed with the old pepper.

Deployed on 2026-10-08: all four functions, with a checked trial claim, reserve, complete, partial grant, release and limit from the app. `admin` was deployed the same day and checked from the app (overview, last-admin refusal, invite and revoke, Pro grant and removal, audit log). `delete-account` too: last-admin refusal from the app, and a full deletion with a throwaway user (wrong word 400, delete 200, rows gone, claim and audit kept, a second call 401).

Tests: `deno test supabase/functions/_shared/` (`trial_test.ts`: normalization, device ID validation, hashing; `usage_test.ts`: reserve and finish body validation; `admin_test.ts`: admin request parsing, including billing actions; `account_test.ts`: the delete confirmation; `payu_test.ts`: SHA-512 command and reverse hashes, India-time dates, link payload, PayU response parsers, config, and the client against a fake `fetch` (token reuse, signed commands, the salt never sent, payment-link lookups); `checkout_test.ts`: phone, checkout and status requests, job secret check; `webhook_test.ts`: form and JSON bodies, redaction, classification, payment-link postbacks). On 2026-10-10 all 33 tests passed in a throwaway `denoland/deno:alpine` container on the server.

## Billing (PayU)

Pro payments through PayU Payment Links ([payments](features/payments.md), [payments PRD](../MP3-Studio-PRD-Payments-PayU-Payment-Link.md)). PayU credentials exist only as Edge Function secrets; the app never sees them and never calls PayU. No result is trusted from the app, a redirect or a webhook alone: every payment is confirmed with PayU's status API (`verify_payment`) before `apply_payment_result()` changes anything. Payment-link payments run under a txnid PayU picks (ours is in `udf1`), so `PayuClient.checkPayment` finds it from the webhook or the link's transactions API; the PayU client needs both OAuth scopes `create_payment_links` and `read_payment_links`.

Main SQL functions (all security definer, `search_path = ''`, `service_role` only):

- Checkout (`20261013000000_payu_billing.sql`): `billing_price_paise()` (9900), `is_pro_sub()`, `next_billing_date(anchor, from)` (the same day next month in India time, or that month's last day), `billing_event()`, `begin_checkout(p_user, p_phone, p_mode)` (refusals, rate limit, saves the phone, creates the `payments` row and a `pending` subscription), `attach_payment_link()`, `fail_checkout()`.
- Verification (`20261013100000_payu_verification.sql`): `apply_payment_result(p_txn, p_result)` (idempotent; activates or extends the subscription, records the mandate, fails or keeps a pending payment until `resolve_until`, flags amount or mandate mismatches), `payments_to_check()`, `payment_summary()`, `log_webhook()`, `finish_webhook()`.
- Jobs (`20261014000000_billing_jobs.sql`): `record_job_run()`, `claim_open_payments()`, `claim_webhooks()`, `claim_pre_debits()`, `mark_pre_debit()`, `claim_renewals()`, `fail_renewal()`, `expire_subscriptions()`, `claim_notices()`, `request_mandate_cancel()`, `confirm_mandate_cancelled()`, `begin_cancel()`, `claim_mandate_cancels()`, `live_mandates()`, `subscription_by_mandate()`, `run_billing_job()`. Claims use `FOR UPDATE SKIP LOCKED` and every step is idempotent, so a job may run twice or in parallel.
- Refunds and disputes (`20261015000000_billing_admin.sql`): `record_refund_request()`, `claim_pending_refunds()`, `apply_refund_result()` (a full refund of the payment for the current period expires the subscription), `apply_dispute()` (`open` stops renewals, `won` restores, `lost` counts as a full refund), `payment_by_ref()`.

Scheduled jobs (pg_cron, through `run_billing_job()`, which posts to `billing-jobs` with pg_net):

| Cron job | Schedule (UTC) | Action |
|---|---|---|
| `billing-webhooks` | every 5 minutes | Retry unprocessed webhooks with backoff; flag after 5 tries. |
| `billing-sweep` | every 15 minutes | Check expired links and pending payments with PayU; check pending refunds. |
| `billing-renewals` | hourly at :07 | Retry unconfirmed mandate cancellations; send pre-debit notices; start due debits (autopay mode only). |
| `billing-expiry` | hourly at :37 | Period ends, grace ends, missed debits (`expire_subscriptions()`). |
| `billing-notices` | daily 03:30 | Billing emails (renewal failed, grace ending, expired, autopay ending); only logged until an email provider is chosen. |

`run_billing_job()` reads the URL and secret from Supabase Vault and does nothing (with a warning) until both exist.

Edge Function secrets (functions service environment, like `TRIAL_HASH_PEPPER`):

| Variable | Meaning |
|---|---|
| `PAYU_ENV` | `live` for production; anything else uses PayU's test hosts. |
| `PAYU_CLIENT_ID`, `PAYU_CLIENT_SECRET`, `PAYU_MERCHANT_ID` | Payment Links API (OAuth client credentials, merchant ID header). |
| `PAYU_KEY`, `PAYU_SALT` | Merchant key and salt for signed `postservice.php` commands (status, debit, pre-debit, mandate cancel, refund) and the reverse hash. |
| `PAYU_SUCCESS_URL`, `PAYU_FAILURE_URL` | `.../functions/v1/payu-return?result=success` and `?result=failure`. |
| `PAYU_RETURN_PAGE` | The app's return link, `https://<pay.returnHost>/pay/return`; `payu-return` builds its intent link from the host. |
| `PAYU_BILLING_MODE` | `manual` for one-off links and manual renewal; anything else is autopay. |
| `BILLING_JOBS_SECRET` | The secret `billing-jobs` expects in `x-billing-job-secret`; the same value as the Vault secret `billing_jobs_secret`. |

When any of the seven required PayU variables is missing, `entitlements` returns `paymentsEnabled: false`, the app shows "Payments aren't available yet.", and checkout answers `503 payu_unavailable`.

Vault secrets (SQL editor): `select vault.create_secret('https://securedbmp3.autoomstudio.com/functions/v1', 'billing_functions_url');` and `select vault.create_secret('<random>', 'billing_jobs_secret');`.

Deployed on 2026-10-10 in PayU **test** mode with `PAYU_BILLING_MODE=manual`:
- the four billing migrations were applied with `psql` and recorded under their repo versions;
- the Vault secrets were created and the five cron jobs scheduled;
- `_shared`, the six billing functions and the updated `entitlements`, `admin` and `delete-account` were deployed, with a backup of the previous functions in `/root/functions-backup-20261010.tgz`.

Smoke-checked:
- `entitlements` and `start-checkout` return 401 without a token;
- `payu-return` returned the return page (a 303 at the time; it serves the page itself since the later fix);
- `payu-webhook` returns 400 for a bad body;
- `billing-jobs` returns 401 for a wrong secret and 200 for `sweep` and `expiry`;
- the first pg_cron runs (`webhooks`, `sweep`) got HTTP 200 through pg_net and wrote `job_runs`;
- no secret values appeared in the container logs;
- the PayU test OAuth token was accepted.

Remaining for production, in order: switch the five PayU values to the live ones with `PAYU_ENV=live`; optionally publish `web/.well-known/assetlinks.json` (with the real signing fingerprints) on `pay.returnHost` so plain `/pay/return` links open the app too; set PayU's webhook URL to `.../functions/v1/payu-webhook`; run a sandbox payment end to end. PayU endpoint paths and field names follow its documentation as of 2026-10 and must be checked against the sandbox first.

## Usage reports (admin)

Every separation request and outcome is an `ai_usage` row (US9), shown in the in-app Admin screens ([admin](features/admin.md)). Per-user weekly totals from the SQL editor:

```sql
select u.email, a.week_start,
       count(*) filter (where a.status = 'completed') as completed,
       count(*) filter (where a.status = 'reserved') as reserved,
       count(*) filter (where a.status = 'released') as released,
       count(*) filter (where a.status = 'denied') as denied
from public.ai_usage a
join auth.users u on u.id = a.user_id
group by u.email, a.week_start
order by a.week_start desc, completed desc;
```

Supabase's security advisor reports "RLS enabled, no policy" for `trial_claims`, `payment_events`, `admin_audit_log`, `webhook_log` and `job_runs`. That is intended: only the service role uses them.

## Migrations

| Version | File | Change |
|---|---|---|
| `20261007000000` | `20261007000000_foundation.sql` | Enums, all tables above, RLS, grants, profile trigger, `is_admin()`. |
| `20261008000000` | `20261008000000_plans.sql` | `compute_entitlements()`, `claim_trial()` (M2). |
| `20261009000000` | `20261009000000_usage.sql` | `ai_usage.status` allows `denied`; `usage_week_start()`, `usage_summary()`, `reserve_separation()`, `finish_separation()` (M3). |
| `20261010000000` | `20261010000000_admin.sql` | `admin_invites`; `subscriptions.provider` allows `admin`; `handle_new_user()` accepts invites; `assert_admin()`, `plan_of()`, `admin_log()` and the `admin_*` functions (M5). |
| `20261011000000` | `20261011000000_account_deletion.sql` | `delete_account()` (M6). |
| `20261012000000` | `20261012000000_admin_activity.sql` | `admin_activity()`. |
| `20261013000000` | `20261013000000_payu_billing.sql` | PayU replaces Razorpay and Play (`provider` `payu`/`admin`, `halted` rows become `past_due`); subscription autopay columns; `payments`, `webhook_log`; `profiles.phone`; `payment_events.txn_id`; `is_pro_sub()`, `begin_checkout()` and the other checkout functions; `compute_entitlements()` and `plan_of()` rewritten. |
| `20261013100000` | `20261013100000_payu_verification.sql` | `apply_payment_result()`, `payment_summary()`, webhook log functions. |
| `20261014000000` | `20261014000000_billing_jobs.sql` | `pg_cron`, `pg_net`; `job_runs`; renewal, expiry, cancellation and mandate functions; `delete_account()` refuses `mandate_active`; the five cron jobs. |
| `20261015000000` | `20261015000000_billing_admin.sql` | Admin payments, billing health and billing actions; refunds and disputes; `admin_user_detail()` with payments. |
| `20261016000000` | `20261016000000_admin_revoke_subscription.sql` | `admin_billing_action` accepts `revoke_subscription`; `revoke_subscription()` ends PayU Pro now. |

The live database was migrated on 2026-10-07 through the Supabase MCP `apply_migration` tool with the name `foundation`, so its recorded version is the apply time rather than `20261007000000`. `plans` and `usage` were applied the same way on 2026-10-08, and `admin`, `account_deletion` and `admin_activity` too. Before running `supabase db push` against it, run `supabase migration repair --status applied 20261007000000 20261008000000 20261009000000 20261010000000 20261011000000 20261012000000` so the CLI doesn't apply the files twice.

The four billing migrations were applied on 2026-10-10 with `psql` as `supabase_admin`, each file in one transaction together with its `supabase_migrations.schema_migrations` row. They are recorded under their repo versions (`20261013000000`, `20261013100000`, `20261014000000`, `20261015000000`), so they need no repair. `20261016000000_admin_revoke_subscription` was applied the same way later that day.

## Testing RLS, quotas and abuse

`supabase/tests/rls_test.sql` checks the trigger, that a user sees only their own profile, can rename themselves but not change `role` or `disabled`, that `is_admin()` is false for normal users, that writes to plan, trial and usage tables fail, that the service-only tables are unreadable, and that `anon` sees nothing.

`supabase/tests/abuse_test.sql` (M6, 20 checks): no second trial after deleting the account (same email or same phone), account deletion rows and refusals, releasing a completed job doesn't give the use back, a replayed job ref doesn't open a slot, another user can't release your job, a disabled account gains nothing, and a catalog sweep: `is_admin()` is the only security definer function `authenticated` can call and `anon` can call none.

`supabase/tests/concurrency_check.sh` (M6): a throwaway Free user sends 20 reservations from 20 parallel database sessions; at most 10 may be granted (the per-user advisory lock). The user is deleted afterwards.

`supabase/tests/payments_test.sql` (constraints, the Pro rule with autopay and grace, billing dates, checkout refusals and the rate limit) and `billing_flow_test.sql` (first payment with autopay, pre-debit and renewal, failed renewal, grace, cancellation and expiry, tampered and unresolved attempts, manual mode, refunds and disputes, the webhook log, admin billing, account deletion keeping payment records). `rls_test.sql` checks that a user reads only their own payments, can't change them and can't read `webhook_log`; the `abuse_test.sql` catalog sweep covers the new security definer functions.

Run everything against the live database with `.\supabase\tests\run_live.ps1` (SSH as `root`): it uploads the files, runs each `*_test.sql` with `psql` as `supabase_admin` in the `supabase-db-faifqncpzdrvhsf6zbvzwhvb` container (pgTAP 1.2.0 is available; each file is one transaction that is rolled back) and then the concurrency check, and fails on any `not ok`. `-Pending <migration files>` runs migrations that aren't applied live yet inside each test's transaction, so they are rolled back too: `.\supabase\tests\run_live.ps1 -Pending 20261013000000_payu_billing.sql,20261013100000_payu_verification.sql,20261014000000_billing_jobs.sql,20261015000000_billing_admin.sql`. Against a local stack, `supabase test db` runs the SQL files. Last live run on 2026-10-10 with the four billing migrations pending: all 190 checks passed.

## First Admin

Sign in once with the Google account, then run `supabase/seed/first_admin.sql` (with that email) in the SQL editor. Later Admins are added from the in-app Admins page (Settings > Admin > Admins), by email; an email that hasn't signed in yet is stored as an invite.

`supabase/tests/admin_test.sql` (pgTAP, 25 checks) covers the admin functions: refusals for users and disabled admins, last admin and self rules, invites and the trigger, Pro grants, search and audit rows.

## Change history

| Date | Commit | Change |
|---|---|---|
| 2026-10-07 | - | Project folder, foundation migration, RLS tests, first-Admin snippet; live database migrated. |
| 2026-10-08 | - | M2: `plans` migration (applied live, checks run in a rolled-back block), `entitlements` and `claim-trial` Edge Functions, `plans_test.sql`. |
| 2026-10-08 | - | M3: `usage` migration (applied live, checks run in a rolled-back block), `reserve-separation` and `finish-separation`, `usage` in the `entitlements` answer, `usage_test.sql`, admin usage SQL. |
| 2026-10-08 | - | M5: `admin` migration (applied live, checks run in a rolled-back block), `admin` Edge Function, `admin_test.sql`, `admin_test.ts`. |
| 2026-10-08 | - | M6: `account_deletion` migration (applied live), `delete-account` Edge Function, `abuse_test.sql`, `concurrency_check.sh`, `run_live.ps1`; `usage_test.sql` counts denied rows per test user so it passes on a database with real data. |
| 2026-10-08 | - | Edge Functions deployed over SSH to the Coolify stack, `TRIAL_HASH_PEPPER` set; Coolify deploy notes. |
| 2026-10-10 | - | PayU billing: migrations `payu_billing`, `payu_verification`, `billing_jobs`, `billing_admin` (tested live in rolled-back transactions, not applied); functions `start-checkout`, `payment-status`, `payu-webhook`, `payu-return`, `subscription`, `billing-jobs`; billing actions in `admin`; `entitlements` returns `billingMode` and `paymentsEnabled`; `delete-account` cancels mandates; `payments_test.sql`, `billing_flow_test.sql`, new Deno tests; `run_live.ps1 -Pending`. |
| 2026-10-10 | - | Billing deployed live in PayU test mode (manual billing): migrations applied, Vault secrets, cron jobs, functions; `BILLING_JOBS_SECRET` and a regenerated `TRIAL_HASH_PEPPER` in `.env`; smoke checks passed. |
| 2026-10-10 | - | Payment-link verification fix deployed (`_shared`, `payu-webhook`; backups `*.bak-20261010073023`): lookups by PayU's own txnid, webhook hash mismatch only flagged. The first test payment's webhook was re-queued and granted Pro. |
| 2026-10-10 | - | Admin revoke subscription: migration `20261016000000_admin_revoke_subscription` (pgTAP checks passed live with it pending, then applied), `revokeSubscription` in the `admin` function (deployed; backups `*.bak-20261010100130`), new `billing_flow_test.sql` and `admin_test.ts` cases. |
| 2026-10-10 | - | `payu-return` serves the return page (`_shared/return_page.ts`, `return_page_test.ts`) with an intent link to the app instead of a 303 to autoomstudio.com; POSTs prefer `udf1`. Deployed (backups `*.bak-20261010101029`) and checked live. |
