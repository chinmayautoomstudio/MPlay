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
| Edge Functions | [`supabase/functions/`](../supabase/functions) (`entitlements`, `claim-trial`, `reserve-separation`, `finish-separation`) |
| First Admin | [`supabase/seed/first_admin.sql`](../supabase/seed/first_admin.sql) (run by hand, not seeded) |

The app reads the URL and anon key from `local.properties` (`supabase.url`, `supabase.key`) together with `google.webClientId`; see [accounts](features/accounts.md#build-configuration).

## Auth settings

Sign-in is Google only (PRD AU2). `config.toml` turns off email and phone sign-up and enables `[auth.external.google]` with `skip_nonce_check = false`, so the ID token's nonce must match the one the app sends.

On the self-hosted server, `config.toml` isn't applied automatically. Set these in the GoTrue environment as well:

- `GOTRUE_EXTERNAL_GOOGLE_ENABLED=true`, `GOTRUE_EXTERNAL_GOOGLE_CLIENT_ID=<Web client ID>` (the same ID as `google.webClientId`), `GOTRUE_EXTERNAL_GOOGLE_SECRET=<Web client secret>`.
- `GOTRUE_EXTERNAL_EMAIL_ENABLED=false` and `GOTRUE_EXTERNAL_PHONE_ENABLED=false`. As of 2026-10-07 `/auth/v1/settings` still reports email and phone as enabled.

To disable an account, an Admin sets `profiles.disabled = true` (the app signs out on the next foreground check). Banning the user in GoTrue also works: the refresh fails with `user_banned`.

## Tables

All in schema `public`, all with Row Level Security. `anon` has no access to any of them.

| Table | Purpose | App (`authenticated`) access | Filled by |
|---|---|---|---|
| `profiles` | One row per user: `display_name` (max 80), `email`, `avatar_url`, `role` (`app_role`: `user`/`admin`), `disabled`, `created_at`. | Select own row; update only `display_name` and `avatar_url` (column grants). | Trigger `on_auth_user_created` |
| `subscriptions` | Pro subscriptions (`provider` `razorpay`/`play`, status, billing dates). | Select own rows. | Server functions (M4) |
| `trials` | 30-day trial per user. | Select own row. | `claim_trial()` |
| `trial_claims` | Email and device hashes that already used a trial; `user_id` is cleared on account deletion, so the claim survives. | None. | `claim_trial()` |
| `payment_events` | Raw provider webhooks, unique per `(provider, provider_event_id)`. | None. | Server functions (M4) |
| `ai_usage` | One row per AI Vocal Separator request, unique per `(user_id, job_ref)`: `status` `reserved`, `completed`, `released` or `denied`, `song_ref` (song title, max 200), `reserved_at`, `completed_at`, `week_start` (IST Monday; moved to the completion week on `completed`). | Select own rows. | `reserve_separation()`, `finish_separation()` |
| `admin_audit_log` | Admin actions. | None. | Server functions (M5) |

Functions:

- `handle_new_user()` (security definer, `search_path = ''`): inserts the profile with `role = 'user'`, the email, `full_name`/`name` (cut to 80) and `avatar_url`/`picture` from the Google metadata. Not executable by API roles.
- `is_admin()` (security definer): true when the caller's profile is an enabled Admin. Executable by `authenticated` only.
- `compute_entitlements(p_user)` (security definer, `service_role` only): `{plan, role, disabled, trial, subscription, serverTime}`. Pro for a `pro` subscription that is `active`, or `cancelled` with `expires_at` in the future; Trial while `trials.ends_at` is in the future; otherwise Free. Returns null when there is no profile.
- `claim_trial(p_user, p_email_hash, p_device_hash)` (security definer, `service_role` only): per-user advisory lock; `existing` when the user has a trial, `denied` with reason `account` (disabled), `email` or `device` (hash already in `trial_claims`), otherwise inserts the claim and a 30-day trial and returns `granted`.
- `usage_week_start(ts)` (immutable): the Monday, India Standard Time, of the week containing `ts`. The weekly reset needs no scheduled job (US8).
- `usage_summary(p_user)` (`service_role` only): `{limit: 10, used, reserved, remaining, weekStart, resetsAt, unlimited}`. `used` counts this week's `completed` rows; `reserved` counts `reserved` rows younger than 48 hours (older ones stop holding a use); `unlimited` is true on Trial and Pro.
- `reserve_separation(p_user, p_jobs)` (`service_role` only): per-user advisory lock, so parallel requests can't pass the limit (US5). `{"error": "account"}` for a missing or disabled profile. Known `jobRef`s get their earlier answer. Trial and Pro get every job as `reserved`; Free gets up to `remaining` in the given order and the rest are stored as `denied`. Returns `{granted, denied, usage}`.
- `finish_separation(p_user, p_job_ref, p_outcome)` (`service_role` only): `completed` or `released` for a `reserved` row; a `jobRef` with no row (Trial or Pro queued offline) gets a row with the outcome. Repeats change nothing. Returns `{usage}`.

## Edge Functions

Deno functions in `supabase/functions/`, called by the app with the user's access token. All of them verify the caller with `auth.getUser(jwt)` and use the service role from the functions container's environment.

| Function | Request | Response |
|---|---|---|
| `entitlements` | `POST {"deviceId": "<64 hex>"}` (device ID optional) | `compute_entitlements()` plus `trialClaim` and `usage` (`usage_summary()`). Claims the trial first when the user has no trial row. `401` without a valid token, `403 account_disabled`, `404 no_profile`, `500 server_error`. |
| `claim-trial` | `POST {"deviceId": "<64 hex>"}` | `{"result": "granted" \| "existing" \| "denied" \| "unavailable", "reason"?}`; `503` when unavailable. |
| `reserve-separation` | `POST {"jobs": [{"jobRef": "<uuid>", "songRef": "..."}]}`, 1 to 50 distinct lowercase UUIDs | `reserve_separation()`: `{granted, denied, usage}`. `400 bad_request`, `403 account_disabled`. |
| `finish-separation` | `POST {"jobRef": "<uuid>", "outcome": "completed" \| "released"}` | `{usage}`. `400 bad_request`. |

`_shared/trial.ts` normalizes the email from the token (lowercase, `+alias` removed, Gmail dots removed, `googlemail.com` as `gmail.com`) and HMAC-SHA256s it and the device ID with `TRIAL_HASH_PEPPER`. Without the pepper the claim returns `unavailable` and the user stays on Free; `entitlements` still answers.

Deploying on the self-hosted server:

1. Copy `supabase/functions/_shared`, `entitlements`, `claim-trial`, `reserve-separation` and `finish-separation` into the stack's `volumes/functions/` (next to `main` and `hello`). Copy `_shared` again whenever it changes.
2. Add `TRIAL_HASH_PEPPER` (a long random secret, for example `openssl rand -hex 32`) to the `functions` service environment in `docker-compose.yml` / `.env`. Never change it afterwards: existing claims would stop matching.
3. `docker compose up -d --force-recreate functions`.
4. Check: `POST /functions/v1/entitlements` without a token returns `401 {"error":"unauthorized"}`.

Tests: `deno test supabase/functions/_shared/` (`trial_test.ts`: normalization, device ID validation, hashing; `usage_test.ts`: reserve and finish body validation).

## Usage reports (admin)

Every separation request and outcome is an `ai_usage` row (US9); the in-app Admin screens come in M5. Until then, per-user weekly totals from the SQL editor:

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

The live database was migrated on 2026-10-07 through the Supabase MCP `apply_migration` tool with the name `foundation`, so its recorded version is the apply time rather than `20261007000000`. `plans` and `usage` were applied the same way on 2026-10-08. Before running `supabase db push` against it, run `supabase migration repair --status applied 20261007000000 20261008000000 20261009000000` so the CLI doesn't apply the files twice.

## Testing RLS

`supabase/tests/rls_test.sql` checks the trigger, that a user sees only their own profile, can rename themselves but not change `role` or `disabled`, that `is_admin()` is false for normal users, that writes to plan, trial and usage tables fail, that the service-only tables are unreadable, and that `anon` sees nothing. Run it with `supabase test db` against a local stack. The same checks were run once against the live database inside a transaction that was rolled back.

## First Admin

Sign in once with the Google account, then run `supabase/seed/first_admin.sql` (with that email) in the SQL editor. Later Admins are promoted from the in-app Admin screens (M5).

## Change history

| Date | Commit | Change |
|---|---|---|
| 2026-10-07 | - | Project folder, foundation migration, RLS tests, first-Admin snippet; live database migrated. |
| 2026-10-08 | - | M2: `plans` migration (applied live, checks run in a rolled-back block), `entitlements` and `claim-trial` Edge Functions, `plans_test.sql`. |
| 2026-10-08 | - | M3: `usage` migration (applied live, checks run in a rolled-back block), `reserve-separation` and `finish-separation`, `usage` in the `entitlements` answer, `usage_test.sql`, admin usage SQL. |
