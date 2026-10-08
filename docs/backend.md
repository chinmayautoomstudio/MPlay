# Backend (Supabase)

> Last updated: 2026-10-08

MP3 Studio uses a self-hosted Supabase project for accounts, plans, trials and AI Vocal Separator usage, and later payments. Music, playlists, stems and recordings never leave the phone; only the title of a song sent for separation is stored, in `ai_usage.song_ref`. The app talks to Supabase Auth (GoTrue) and PostgREST with the public anon key; everything privileged runs server-side as the service role.

## Project

| Item | Value |
|---|---|
| URL | `https://securedbmp3.autoomstudio.com` (self-hosted, Postgres 15) |
| Repo folder | [`supabase/`](../supabase) |
| Config | [`supabase/config.toml`](../supabase/config.toml) (`project_id = "mp3-studio"`) |
| Migrations | [`supabase/migrations/`](../supabase/migrations) |
| RLS tests | [`supabase/tests/rls_test.sql`](../supabase/tests/rls_test.sql) (pgTAP, 17 checks), [`supabase/tests/plans_test.sql`](../supabase/tests/plans_test.sql) (pgTAP, 16 checks), [`supabase/tests/usage_test.sql`](../supabase/tests/usage_test.sql) (pgTAP, 17 checks) |
| Edge Functions | [`supabase/functions/`](../supabase/functions) (`entitlements`, `claim-trial`, `reserve-separation`, `finish-separation`, `admin`, `delete-account`) |
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
| `profiles` | One row per user: `display_name` (max 80), `email`, `avatar_url`, `role` (`app_role`: `user`/`admin`), `disabled`, `created_at`. | Select own row; update only `display_name` and `avatar_url` (column grants). | Trigger `on_auth_user_created` |
| `subscriptions` | Pro subscriptions (`provider` `razorpay`/`play`/`admin`, status, billing dates). Admin grants use `provider_ref = 'admin:<user id>'` and `payment_status = 'granted'`. | Select own rows. | Server functions (M4), `admin_grant_pro()` |
| `trials` | 30-day trial per user. | Select own row. | `claim_trial()` |
| `trial_claims` | Email and device hashes that already used a trial; `user_id` is cleared on account deletion, so the claim survives. | None. | `claim_trial()` |
| `payment_events` | Raw provider webhooks, unique per `(provider, provider_event_id)`. | None. | Server functions (M4) |
| `ai_usage` | One row per AI Vocal Separator request, unique per `(user_id, job_ref)`: `status` `reserved`, `completed`, `released` or `denied`, `song_ref` (song title, max 200), `reserved_at`, `completed_at`, `week_start` (IST Monday; moved to the completion week on `completed`). | Select own rows. | `reserve_separation()`, `finish_separation()` |
| `admin_audit_log` | Admin actions (`set_role`, `disable`, `enable`, `invite_admin`, `revoke_invite`, `invite_accepted`, `grant_pro`, `revoke_pro`) with actor, target and `details`. | None. | `admin_*` functions, `handle_new_user()` |
| `admin_invites` | Emails (lowercase, trimmed) invited as Admin before they have an account, with `invited_by`. | None. | `admin_add_admin()` |

Functions:

- `handle_new_user()` (security definer, `search_path = ''`): inserts the profile with the email, `full_name`/`name` (cut to 80) and `avatar_url`/`picture` from the Google metadata. `role` is `admin` when the email has a row in `admin_invites` (the invite is deleted and an `invite_accepted` audit row written), otherwise `user`. Not executable by API roles.
- `is_admin()` (security definer): true when the caller's profile is an enabled Admin. Executable by `authenticated` only.
- `compute_entitlements(p_user)` (security definer, `service_role` only): `{plan, role, disabled, trial, subscription, serverTime}`. Pro for a `pro` subscription that is `active`, or `cancelled` with `expires_at` in the future; Trial while `trials.ends_at` is in the future; otherwise Free. Returns null when there is no profile.
- `claim_trial(p_user, p_email_hash, p_device_hash)` (security definer, `service_role` only): per-user advisory lock; `existing` when the user has a trial, `denied` with reason `account` (disabled), `email` or `device` (hash already in `trial_claims`), otherwise inserts the claim and a 30-day trial and returns `granted`.
- `usage_week_start(ts)` (immutable): the Monday, India Standard Time, of the week containing `ts`. The weekly reset needs no scheduled job (US8).
- `usage_summary(p_user)` (`service_role` only): `{limit: 10, used, reserved, remaining, weekStart, resetsAt, unlimited}`. `used` counts this week's `completed` rows; `reserved` counts `reserved` rows younger than 48 hours (older ones stop holding a use); `unlimited` is true on Trial and Pro.
- `reserve_separation(p_user, p_jobs)` (`service_role` only): per-user advisory lock, so parallel requests can't pass the limit (US5). `{"error": "account"}` for a missing or disabled profile. Known `jobRef`s get their earlier answer. Trial and Pro get every job as `reserved`; Free gets up to `remaining` in the given order and the rest are stored as `denied`. Returns `{granted, denied, usage}`.
- `finish_separation(p_user, p_job_ref, p_outcome)` (`service_role` only): `completed` or `released` for a `reserved` row; a `jobRef` with no row (Trial or Pro queued offline) gets a row with the outcome. Repeats change nothing. Returns `{usage}`.
- Admin functions (M5, security definer, `service_role` only, all take `p_actor` from the verified token and start with `assert_admin(p_actor)`, which raises `42501` unless the actor is an enabled Admin). Refusals come back as `{"error": code}`. Role and disable changes take the advisory lock `admin:roles` so two admins can't remove each other at once. Every change writes `admin_audit_log` through `admin_log()`.
  - `admin_overview`: user counts by plan, disabled and admins, plus this week's completed, reserved and denied separations.
  - `admin_list_users(p_query, p_filter, p_limit, p_offset)`: email or name search, filter `all`/`pro`/`trial`/`free`/`disabled`/`admin`; `{total, users}`.
  - `admin_user_detail(p_user)`: profile, entitlements, usage, 8 weeks of totals, last 50 jobs, subscriptions, last 50 payment events.
  - `admin_usage`: 8 weeks of totals and this week's top 20 users.
  - `admin_set_role`, `admin_set_disabled`: refuse `last_admin` (the last enabled Admin) and, for disable, `self`.
  - `admin_add_admin(p_email)`: `promoted`, `already_admin` or `invited` (stored in `admin_invites`); `invalid_email`. `admin_list_admins`, `admin_revoke_invite`.
  - `admin_grant_pro(p_user, p_until)`: upserts the admin subscription; `invalid_date` unless the end is in the future and at most 5 years away. `admin_revoke_pro` expires it now.
  - `admin_audit(p_limit, p_before)`: entries newest first, paged by id.
  - Helpers `plan_of(p_user)` (same rule as `compute_entitlements`) and `admin_log()`.
- `delete_account(p_user)` (M6, security definer, `service_role` only): takes the `admin:roles` lock; `{"error": ...}` `not_found`, `last_admin` (the last enabled Admin) or `active_subscription` (an `active` `razorpay`/`play` subscription without `cancel_at_period_end`); otherwise writes an `account_deleted` audit row (details `{role, plan}`, no email) and deletes the `auth.users` row. The foreign keys cascade to `profiles`, `subscriptions`, `trials` and `ai_usage`, and set `trial_claims.user_id`, `payment_events.user_id`, `admin_audit_log.actor_id` and `admin_invites.invited_by` to null, so deleting and signing up again gives no new trial and payment records are kept (PR4). Returns `{"result": "deleted"}`.

## Edge Functions

Deno functions in `supabase/functions/`, called by the app with the user's access token. All of them verify the caller with `auth.getUser(jwt)` and use the service role from the functions container's environment.

| Function | Request | Response |
|---|---|---|
| `entitlements` | `POST {"deviceId": "<64 hex>"}` (device ID optional) | `compute_entitlements()` plus `trialClaim` and `usage` (`usage_summary()`). Claims the trial first when the user has no trial row. `401` without a valid token, `403 account_disabled`, `404 no_profile`, `500 server_error`. |
| `claim-trial` | `POST {"deviceId": "<64 hex>"}` | `{"result": "granted" \| "existing" \| "denied" \| "unavailable", "reason"?}`; `503` when unavailable. |
| `reserve-separation` | `POST {"jobs": [{"jobRef": "<uuid>", "songRef": "..."}]}`, 1 to 50 distinct lowercase UUIDs | `reserve_separation()`: `{granted, denied, usage}`. `400 bad_request`, `403 account_disabled`. |
| `finish-separation` | `POST {"jobRef": "<uuid>", "outcome": "completed" \| "released"}` | `{usage}`. `400 bad_request`. |
| `delete-account` | `POST {"confirm": "DELETE"}` (exact word) | `{"result": "deleted"}`. `400 bad_request` (wrong body), `404 not_found`, `409 {"error": "last_admin" \| "active_subscription"}`. |
| `admin` | `POST {"action": ..., ...}`: `overview`, `usage`, `admins`, `users {query, filter, limit, offset}`, `user {userId}`, `setRole {userId, role}`, `setDisabled {userId, disabled}`, `addAdmin {email}`, `revokeInvite {email}`, `grantPro {userId, until}` (ISO time), `revokePro {userId}`, `audit {limit, before}` | The matching `admin_*` result. `400 bad_request`, `403 forbidden` (not an enabled Admin), `404 not_found`, `409 {"error": "last_admin" \| "self" \| "invalid_email" \| "invalid_date"}`. |

`_shared/trial.ts` normalizes the email from the token (lowercase, `+alias` removed, Gmail dots removed, `googlemail.com` as `gmail.com`) and HMAC-SHA256s it and the device ID with `TRIAL_HASH_PEPPER`. Without the pepper the claim returns `unavailable` and the user stays on Free; `entitlements` still answers.

Deploying on the self-hosted server:

1. Copy `supabase/functions/_shared`, `entitlements`, `claim-trial`, `reserve-separation`, `finish-separation`, `admin` and `delete-account` into the stack's `volumes/functions/` (next to `main` and `hello`). Copy `_shared` again whenever it changes.
2. Add `TRIAL_HASH_PEPPER` (a long random secret, for example `openssl rand -hex 32`) to the `functions` service environment in `docker-compose.yml` / `.env`. Never change it afterwards: existing claims would stop matching.
3. `docker compose up -d --force-recreate functions`.
4. Check: `POST /functions/v1/entitlements` with the anon `apikey` header but no user token returns `401 {"error":"unauthorized"}`.

The live stack is the Coolify service `faifqncpzdrvhsf6zbvzwhvb` on `54.163.241.161` (SSH as `root`), so the paths are `/data/coolify/services/faifqncpzdrvhsf6zbvzwhvb/volumes/functions/` and that folder's `docker-compose.yml` and `.env`. The service is `supabase-edge-functions`. Restart only that container: `docker compose --project-name faifqncpzdrvhsf6zbvzwhvb -f docker-compose.yml --env-file .env up -d --no-deps --force-recreate supabase-edge-functions`. The pepper is in `.env` and passed through `TRIAL_HASH_PEPPER: '${TRIAL_HASH_PEPPER}'` in the compose file. Coolify rewrites both files when the service is saved or redeployed from its UI, so add the same variable there (same value, copied from `.env`) before doing that.

Deployed on 2026-10-08: all four functions, with a checked trial claim, reserve, complete, partial grant, release and limit from the app. `admin` was deployed the same day and checked from the app (overview, last-admin refusal, invite and revoke, Pro grant and removal, audit log). `delete-account` too: last-admin refusal from the app, and a full deletion with a throwaway user (wrong word 400, delete 200, rows gone, claim and audit kept, a second call 401).

Tests: `deno test supabase/functions/_shared/` (`trial_test.ts`: normalization, device ID validation, hashing; `usage_test.ts`: reserve and finish body validation; `admin_test.ts`: admin request parsing; `account_test.ts`: the delete confirmation).

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

Supabase's security advisor reports "RLS enabled, no policy" for `trial_claims`, `payment_events` and `admin_audit_log`. That is intended: only the service role uses them.

## Migrations

| Version | File | Change |
|---|---|---|
| `20261007000000` | `20261007000000_foundation.sql` | Enums, all tables above, RLS, grants, profile trigger, `is_admin()`. |
| `20261008000000` | `20261008000000_plans.sql` | `compute_entitlements()`, `claim_trial()` (M2). |
| `20261009000000` | `20261009000000_usage.sql` | `ai_usage.status` allows `denied`; `usage_week_start()`, `usage_summary()`, `reserve_separation()`, `finish_separation()` (M3). |
| `20261010000000` | `20261010000000_admin.sql` | `admin_invites`; `subscriptions.provider` allows `admin`; `handle_new_user()` accepts invites; `assert_admin()`, `plan_of()`, `admin_log()` and the `admin_*` functions (M5). |
| `20261011000000` | `20261011000000_account_deletion.sql` | `delete_account()` (M6). |

The live database was migrated on 2026-10-07 through the Supabase MCP `apply_migration` tool with the name `foundation`, so its recorded version is the apply time rather than `20261007000000`. `plans` and `usage` were applied the same way on 2026-10-08, and `admin` and `account_deletion` too. Before running `supabase db push` against it, run `supabase migration repair --status applied 20261007000000 20261008000000 20261009000000 20261010000000 20261011000000` so the CLI doesn't apply the files twice.

## Testing RLS, quotas and abuse

`supabase/tests/rls_test.sql` checks the trigger, that a user sees only their own profile, can rename themselves but not change `role` or `disabled`, that `is_admin()` is false for normal users, that writes to plan, trial and usage tables fail, that the service-only tables are unreadable, and that `anon` sees nothing.

`supabase/tests/abuse_test.sql` (M6, 20 checks): no second trial after deleting the account (same email or same phone), account deletion rows and refusals, releasing a completed job doesn't give the use back, a replayed job ref doesn't open a slot, another user can't release your job, a disabled account gains nothing, and a catalog sweep: `is_admin()` is the only security definer function `authenticated` can call and `anon` can call none.

`supabase/tests/concurrency_check.sh` (M6): a throwaway Free user sends 20 reservations from 20 parallel database sessions; at most 10 may be granted (the per-user advisory lock). The user is deleted afterwards.

Run everything against the live database with `.\supabase\tests\run_live.ps1` (SSH as `root`): it uploads the files, runs each `*_test.sql` with `psql` in the `supabase-db-faifqncpzdrvhsf6zbvzwhvb` container (pgTAP 1.2.0 is available; each file is one transaction that is rolled back) and then the concurrency check, and fails on any `not ok`. Against a local stack, `supabase test db` runs the SQL files. Last live run on 2026-10-08: all 95 checks and the concurrency check passed (10 granted, 10 denied).

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
