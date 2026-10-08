# Admin

> Status: Unreleased | Added in: Unreleased | Last updated: 2026-10-08

## Summary

Admins get an Admin row on the Settings Account card. It opens screens to look up users, see each user's plan, trial, subscriptions, payments and AI Vocal Separator usage, change roles, disable accounts, grant or remove Pro with an end date, add other admins (by email, as an invite if they haven't signed in yet), and read the audit log. Every change is checked and logged on the server; the app only decides whether to offer the screens.

## Key files

Paths are relative to `app/src/main/java/com/autoomstudio/mp3studio/` unless they start with `app/` or `supabase/`.

| File | Role |
|---|---|
| `data/admin/AdminModels.kt` | DTOs for every admin response, `UserFilter`, `AddAdminResult`, `AdminError`, `AdminException`. |
| `data/admin/AdminBackend.kt` | `AdminBackend` interface and `SupabaseAdminBackend`, which calls the `admin` Edge Function with an `action`; `errorOf()` maps HTTP status and refusal codes to `AdminError`. `PAGE_SIZE = 50`. |
| `data/plan/Entitlements.kt`, `data/plan/EntitlementsBackend.kt` | `Entitlements.isAdmin`, from `role == "admin"` in the `entitlements` response. |
| `ui/admin/AdminViewModel.kt` | Page back stack (`AdminPage`: Home, User, Admins, Usage, Audit), loading state per page, debounced user search, actions, one-shot `AdminMessage`s, `closed` when access is lost. |
| `ui/admin/AdminScreen.kt` | Host (back handling, messages) and the home page: overview counts, links, search, filter chips, user list with paging. |
| `ui/admin/AdminUserScreen.kt` | User detail and actions, confirm dialogs, `GrantProDialog` (1 month, 3 months, 1 year or a picked date up to 5 years ahead). |
| `ui/admin/AdminListScreens.kt` | Admins and invites (`AddAdminDialog`), weekly usage with top users, audit log. |
| `ui/admin/AdminCommon.kt` | Error and message texts, date formatting, shared loading and error blocks. |
| `ui/settings/AccountSection.kt`, `ui/main/MainScreen.kt` | Admin row (shown when `PlanUiState.isAdmin`) and `SettingsPage.Admin`. |
| `supabase/migrations/20261010000000_admin.sql` | `admin_invites`, invite handling in `handle_new_user()`, `admin_*` functions. |
| `supabase/functions/admin/index.ts`, `supabase/functions/_shared/admin.ts` | The `admin` Edge Function and its request parser. |

## How it works

- **Who sees it.** The Admin row shows when the cached entitlements say `isAdmin`. That flag only hides or shows the screens; every call is checked again on the server.
- **Calls.** `SupabaseAdminBackend` sends `POST /functions/v1/admin` with `{"action": ..., ...}`. The function verifies the user token, validates the body (`parseAdminRequest`), and calls the matching `admin_*` Postgres function with `p_actor` set to the caller. Each function starts with `assert_admin(p_actor)`, which refuses non-admins and disabled admins (403 `forbidden`). See [backend.md](../backend.md) for the action list.
- **Refusals.** Some requests are refused with 409 and a code: `last_admin` (demoting or disabling the last enabled admin), `self` (disabling yourself), `invalid_email`, `invalid_date`. Unknown users are 404. The app shows a snackbar for each (`AdminError`).
- **Losing access.** If a call returns 403 (you were demoted or disabled elsewhere), `AdminViewModel` shows "Admin access was removed", refreshes your plan and closes the screens.
- **Acting on yourself.** Changing your own role or Pro refreshes your own entitlements so Settings and gates update immediately. The Disable button is hidden on your own detail page.
- **Adding admins.** Admins page > Add admin takes an email. If that email already has an account it is promoted (or reported as already an admin). Otherwise it is saved in `admin_invites`; when that Google account first signs in, `handle_new_user()` makes it an admin and removes the invite. Pending invites can be revoked.
- **Granting Pro.** Writes a `subscriptions` row with `provider = 'admin'`, `provider_ref = 'admin:<user id>'`, `status = 'active'`, `payment_status = 'granted'` and the chosen end date (end of that day, local time). Changing the end date updates the same row. Removing Pro marks it `expired` now. The user's plan follows from the normal entitlement rules ([plans](plans.md)).
- **Deleted accounts.** When a user deletes their account ([accounts](accounts.md#delete-account-prd-au9-pr4)), the audit log shows "A user deleted their account (Free plan)" by "the account owner". The entry has no email and doesn't open a user page, since the account no longer exists. The last enabled Admin can't delete their account.
- **Search and paging.** The search box matches email or name (300 ms debounce) with a filter (All, Pro, Free trial, Free, Disabled, Admins); 50 users per page with "Load more". The audit log pages by entry id.

## Data and persistence

- Nothing is stored on the phone besides the existing `entitlements` cache ([plans](plans.md)).
- Server: `admin_invites`, `admin_audit_log` (actions `set_role`, `disable`, `enable`, `invite_admin`, `revoke_invite`, `invite_accepted`, `grant_pro`, `revoke_pro`, and `account_deleted` written by `delete_account()` when a user deletes their own account), `subscriptions` rows with provider `admin`. See [backend.md](../backend.md).

## Manifest, permissions and notifications

None. Uses the existing network access.

## Tests

- `app/src/test/.../ui/admin/AdminViewModelTest.kt`: initial load, page back stack, debounced search, refusal message, own role change refreshes the plan, 403 closes the screens.
- `app/src/test/.../data/plan/EntitlementsResponseTest.kt`: `role` maps to `isAdmin`.
- `supabase/tests/admin_test.sql` (pgTAP): non-admin and disabled-admin refusals, last admin and self rules, promote and step down, invites and the sign-up trigger, Pro grant and removal, search, audit rows, no access for `authenticated`.
- `supabase/functions/_shared/admin_test.ts` (Deno): request parsing and validation.
- Gap: no UI (Compose) tests.

## Known limitations and TODOs

- An invite only matches the exact email (case-insensitive); a different alias of the same Google account isn't matched.
- Admin Pro grants don't show payment history; payment providers (M4) aren't built yet.
- The Admin row appears only after entitlements refresh (sign-in or app start), so a newly promoted admin may need to reopen the app.

## Change history

| Date | Commit | Change |
|---|---|---|
| 2026-10-08 | | Admin screens, invites, Pro grants and audit log (PRD v3.2 M5). |
| 2026-10-08 | | M6: `account_deleted` audit entries; the store review account gets Pro from the Admin screens ([store review](../store-review.md)). |
