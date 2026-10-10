# Payments (PayU Pro subscription)

> Status: Unreleased | Added in: next version after 3.2.3 | Last updated: 2026-10-10

## Summary

Pro costs ₹99 a month or ₹999 a year and is paid through PayU Payment Links ([payments PRD](../../MP3-Studio-PRD-Payments-PayU-Payment-Link.md)). "Go Pro" lets the user pick Monthly or Yearly, asks for an Indian mobile number, opens a PayU-hosted page in a Custom Tab and, on return, waits for the server to confirm the payment with PayU. By default the link registers autopay (a monthly or yearly standing instruction through UPI AutoPay, eNACH or card), and the server debits ₹99 each month or ₹999 each year. With the server flag `PAYU_BILLING_MODE=manual`, links are one-off and the user renews by hand. The app never talks to PayU, never holds PayU credentials and never decides Pro itself: every decision is made by the Supabase backend after a PayU status check.

## Key files

Paths are relative to `app/src/main/java/com/autoomstudio/mp3studio/` unless they start with `supabase/`, `web/` or `app/`.

| File | Role |
|---|---|
| `data/billing/BillingModels.kt` | `BillingError` (server error codes), `BillingException`, `CheckoutLink`, `PaymentState`, `PaymentSummary`, `CancelResult`, `PaymentRecord`. |
| `data/billing/BillingBackend.kt` | `SupabaseBillingBackend`: `start-checkout`, `payment-status` and `subscription` Edge Functions, and payment history from the `payments` table (own rows through RLS, newest 50). Maps HTTP errors to `BillingError`. |
| `data/billing/BillingRepository.kt` | `startCheckout`, `confirm` (polls `payment-status` until settled, then refreshes the plan), `cancel`, `history`, `clear`; `DataStorePendingPaymentStore` keeps the awaited transaction across process death. |
| `data/billing/PhoneNumber.kt` | `PhoneNumber.normalize` (accepts spaces, dashes, `+91`, `91` or a leading 0; same rule as the server) and `TxnId.parse`. |
| `data/plan/Entitlements.kt` | `SubscriptionStatus`, `AutopayStatus`, `BillingInterval` (`Month`/`Year`), `BillingMode`, `BillingState` (with `interval`), `PendingPayment`, `LastPayment`, plus `subscriptionInterval`, `hasPhone`, `billingMode` and `paymentsEnabled` on `Entitlements`. |
| `ui/plans/BillingViewModel.kt` | Checkout sheet state, opening the link, the return and resume checks, cancel autopay. |
| `ui/plans/BillingHost.kt` | Root-level host: opens links in a Custom Tab (`androidx.browser`, https only), handles returns, checks on resume, shows the sheet, result screen and cancel dialog. |
| `ui/plans/CheckoutSheet.kt` | Monthly/Yearly picker (hidden when switching), autopay or manual terms for the chosen interval, the switch note, the +91 phone field, Pay ₹99 or Pay ₹999, links to Terms, Refund policy and Privacy. |
| `ui/plans/PaymentResultScreen.kt` | Full-screen result: confirming, success, failed, link expired, not completed, still processing, couldn't check, no browser. |
| `ui/plans/BillingStatus.kt` | `BillingStatusRules.of(...)`: the notice and buttons the Plans screen shows for each subscription state. |
| `ui/plans/PaymentReturnLink.kt` | Parses `https://<pay.returnHost>/pay/return?txn=...` App Links. |
| `ui/plans/PaymentHistoryScreen.kt` | Settings > Plans > Payment history, with a receipt dialog. |
| `ui/plans/PaymentText.kt` | Rupee and date formatting, error and status text, `pricePaise(interval)` (must match `billing_price_paise`). |
| `ui/admin/AdminPaymentsScreen.kt` | Admin payments list and billing health; see [admin](admin.md). |
| `supabase/functions/start-checkout/`, `payment-status/`, `payu-webhook/`, `payu-return/`, `subscription/`, `billing-jobs/` | Billing Edge Functions; see [backend](../backend.md#billing-payu). |
| `supabase/functions/_shared/payu.ts`, `billing.ts`, `checkout.ts`, `webhook.ts`, `jobs.ts`, `notifier.ts` | PayU client (OAuth Payment Links API and SHA-512-signed `postservice.php` commands), shared flows, request parsing, webhook classification and redaction, job auth, billing notices. |
| `supabase/migrations/20261013000000_payu_billing.sql` to `20261015000000_billing_admin.sql` | Schema, verification, scheduled jobs and admin billing. |
| `supabase/migrations/20261017000000_yearly_plan.sql` | `billing_interval` on subscriptions and payments, `billing_price_paise(interval)`, `billing_period_end()`, the switch rule in `begin_checkout()`, and yearly-aware verification, renewals, entitlements and admin detail. |
| `supabase/functions/_shared/return_page.ts` | The return page `payu-return` serves after checkout, with an intent link back to the app. |
| `web/pay/return/index.html`, `web/.well-known/assetlinks.json` | Unpublished App Link page and Digital Asset Links template, for when `pay.returnHost` serves them. |

## How it works

```mermaid
sequenceDiagram
    participant App
    participant Fn as start-checkout
    participant PayU
    participant Ret as payu-return
    participant St as payment-status
    App->>Fn: phone (first time only)
    Fn->>PayU: create payment link (amount fixed on the server)
    Fn-->>App: url, txnId, mode
    App->>PayU: Custom Tab
    PayU->>Ret: success or failure URL
    Ret-->>App: return page, intent link to https://host/pay/return?txn=...
    App->>St: txnId
    St->>PayU: verify_payment status check
    St-->>App: status, plan
    PayU-->>App: (webhook to payu-webhook, also re-checked with PayU)
```

### Checkout

- Go Pro (Plans screen or upgrade sheet), Fix payment, Set up autopay, Renew and the switch buttons all open `CheckoutSheet` (`CheckoutPurpose`). The phone field shows only while `Entitlements.hasPhone` is false; the server stores the number in `profiles.phone` on the first checkout.
- The sheet starts on the user's current interval (`BillingState.interval`, Monthly for new users) and sends it as `start-checkout {interval: "month" | "year"}`. The server sets the price (`billing_price_paise`: 9900 or 99900 paise) and the period (`billing_period_end`: one month, or the same day next year with 29 Feb falling back to 28 Feb). Each `payments` row and the subscription it pays for carry `billing_interval`; renewals debit the subscription's interval. The yearly link uses `billingCycle: "YEARLY"` in its standing instruction.
- `start-checkout` calls `begin_checkout()`, which refuses with `phone_required`, `invalid_phone`, `payment_in_progress` (an open link exists; a live link is handed out again only for the same interval), `already_subscribed`, `switch_not_yet` or `mandate_update_pending`, and rate-limits attempts. A "replace" checkout while Pro is active is allowed only when autopay isn't `on` (or, in manual mode, within 7 days of expiry).

### Switching between monthly and yearly

A PayU mandate has a fixed cycle and amount, so a switch is a new checkout, not a change to the old mandate. With autopay on, "Switch to yearly" or "Switch to monthly" opens the sheet locked to the other interval (`CheckoutPurpose.SwitchInterval`). `begin_checkout()` answers `mandate_active`, so `startCheckout` cancels the old mandate with PayU and tries again. The new link is a `replace` payment whose period starts when the current paid period ends, so nothing is prorated or lost. A switch opens 31 days before the period end (`SWITCH_WINDOW_MS` in the app); earlier the server answers `switch_not_yet`. In practice monthly to yearly works any time and yearly to monthly in the last month of the year. If the user abandons the new link, Pro stays until the old period end with autopay off, and the Plans screen offers Set up autopay. In manual mode the user just picks the interval when renewing.
- The app saves `(userId, txnId)` in DataStore before opening the link, then opens it in a Custom Tab (any browser if Custom Tabs are missing; `NoBrowser` result if there's none).

### Confirmation

- PayU's success and failure URLs point at `payu-return`, which serves the return page itself (`returnPageHtml`): "Payment received" or "Payment not completed" and a "Return to MP3 Studio" button. On Android the page opens `intent://<host of PAYU_RETURN_PAGE>/pay/return?result=...&txn=...#Intent;scheme=https;package=com.autoomstudio.mp3studio;end` straight away, and the button repeats it. Because the intent names the package, it reaches the `/pay/return` intent filter without App Link verification. `MainActivity.handleIntent` passes the transaction to `BillingViewModel.onReturned`. For a PayU POST the transaction comes from `udf1` (ours) before `txnid`.
- `BillingRepository.confirm` polls `payment-status` every 3 s (`POLL_INTERVAL_MS`) up to 10 times (`VISIBLE_ATTEMPTS`) while the result screen shows "Confirming". `payment-status` asks PayU (`verify_payment`) and applies the answer through `apply_payment_result()`; the app only displays what comes back. A payment PayU still reports as pending is checked until `resolveUntil` before it is failed.
- If the App Link didn't fire (browser closed, link verification missing), `onForeground` checks the stored transaction on every resume. A quiet check only surfaces Success; a return the user was waiting for shows every result.
- PayU runs each payment-link payment under a txnid it picks (for example `938632`) and puts our txnid in `udf1`. `PayuClient.checkPayment` therefore asks `verify_payment` for our txnid first (renewals use it), then for the PayU txnid a webhook named, accepted only when the answer's `udf1` is ours, and then for each transaction on the link (`GET /payment-links/{invoice}/txns`, OAuth scope `read_payment_links`). The answer is reported under our txnid, so `apply_payment_result()` matches it.
- `payu-webhook` saves every PayU notification to `webhook_log` first (secrets and card fields redacted) and then re-checks the payment with PayU. `classifyWebhook` takes our txnid from `udf1` when it looks like ours (`MP` or `RN` plus 20 hex digits). The reverse hash is still computed; a mismatch is logged as `_hashMismatch` but the event is processed anyway, because PayU's status answer decides and payment-link postbacks have failed the documented formula. Failed processing is retried by the `webhooks` job and flagged after 5 tries.

### Plans screen

`BillingStatusRules` turns `Entitlements.billing`, `pendingPayment`, `billingMode` and `paymentsEnabled` into one notice and its buttons:

| Notice | When | Buttons |
|---|---|---|
| Payments aren't available yet | `paymentsEnabled` is false (PayU secrets not set) | none |
| Payment pending | an open payment | Check payment |
| Autopay on (with the ₹99 or ₹999 amount) | active, autopay `on` | Switch to yearly (monthly plans) or Switch to monthly (yearly plans, last 31 days), Cancel autopay |
| Autopay not set | active, autopay `off` or `not_set` | Set up autopay |
| Renew soon | manual mode, within 7 days of expiry | Renew |
| Past due | a renewal debit failed, until `graceEnd` | Fix payment |
| Cancel pending | cancellation sent, PayU hasn't confirmed | none |
| Cancelled | autopay cancelled, Pro until `expiresAt` | Go Pro again |
| Mandate ending | the mandate ends within 30 days | Cancel autopay |
| Expired | the subscription expired | Go Pro again |

Pro granted by an Admin shows no billing notice. The Plans screen also shows the last payment and a Payment history link when there is one. The plan label reads "Pro · Monthly" or "Pro · Yearly" for PayU subscriptions (`PlanUiState.subscriptionInterval`), and history rows say "Pro, yearly renewal" for yearly debits.

### Renewals, expiry and cancellation (server)

Scheduled by pg_cron through `run_billing_job()` and the `billing-jobs` function: `webhooks` every 5 minutes, `sweep` every 15 (expired links, pending payments, pending refunds), `renewals` hourly (unconfirmed mandate cancellations, pre-debit notices, due debits), `expiry` hourly (period ends, grace ends, missed debits) and `notices` daily. In manual mode no debits or pre-debit notices are sent. Each run is recorded in `job_runs`.

Cancel autopay (`subscription` with `{action: "cancel"}`) stops renewals at once and asks PayU to revoke the mandate. Pro stays until the paid period ends. If PayU doesn't confirm, the answer is `cancel_pending` and the `renewals` job retries.

An Admin can also revoke a PayU subscription (`revokeSubscription`, [admin](admin.md)): Pro ends at once (`revoke_subscription()` sets it `expired` now), renewals stop and any mandate is cancelled, retried by the `renewals` job if PayU doesn't confirm. Nothing is refunded.

Deleting the account cancels any live mandate first; if PayU refuses, deletion stops with `mandate_cancel_failed` ("We couldn't cancel your autopay...", [accounts](accounts.md)).

### Refunds and disputes

Only Admins can refund, from the Admin payments list or a user's page, and only a paid payment that has a PayU reference and no refund in progress. The request is recorded (`record_refund_request`) and its outcome is picked up by the `sweep` job or a refund webhook (`apply_refund_result`). A full refund of the payment that pays for the current period expires the subscription at once; a partial refund keeps Pro. Disputes from webhooks set `payments.dispute_status` (`apply_dispute`): `open` stops renewals but keeps Pro, `won` restores the payment, `lost` counts as a full refund.

## Data and persistence

- DataStore `billing`: `pending_payment_user` and `pending_payment_txn`, the transaction being waited for. Cleared when it settles, when the server no longer knows it, and on sign-out.
- DataStore `entitlements` (see [plans](plans.md)): the cached answer now includes `billing`, `pendingPayment`, `lastPayment`, `hasPhone`, `billingMode` and `paymentsEnabled`. A cache written before this change fails to decode and is fetched again. `subscriptionInterval` and `BillingState.interval` have defaults, so caches from before yearly Pro still decode.
- Server columns `subscriptions.billing_interval` and `payments.billing_interval` (`month` or `year`, default `month`).
- Server tables `subscriptions` (PayU columns), `payments`, `webhook_log`, `job_runs`, `payment_events.txn_id` and `profiles.phone`: see [backend](../backend.md#billing-payu). No Room changes.
- The app stores no card, UPI or bank details, and neither does the server: only PayU's references, the method name and the mandate reference.

## Manifest, permissions and notifications

- `MainActivity` has an `android:autoVerify="true"` intent filter for `https://${payReturnHost}/pay/return`. The host comes from `pay.returnHost` in `local.properties` (default `autoomstudio.com`) and is also `BuildConfig.PAY_RETURN_HOST`.
- The App Link verifies only after `web/.well-known/assetlinks.json` is published on that host with the release and debug signing SHA-256 fingerprints (the file in the repo has placeholders). The return page's intent link doesn't need it, and the resume check still confirms payments.
- No new permissions; the PayU page runs in the browser, not in the app.
- Billing emails (renewal failed, grace ending, expired, autopay ending) are only logged for now (`LogNotifier`); no email provider is chosen.

## Tests

- Yearly Pro: `BillingViewModelTest` (Monthly default, the picked interval reaches the backend, a switch locks the other interval and carries the switch date), `BillingStatusRulesTest` (switch buttons and the 31-day window, prices), `EntitlementsResponseTest` (interval parsing, none for admin grants), `BillingRepositoryTest` (`switch_not_yet`, `invalid_interval`), Deno (`YEARLY` link payload, interval parsing) and pgTAP (yearly period and price, yearly first payment and renewal, monthly-to-yearly switch, `switch_not_yet`). The SQL and Deno tests have not been run yet.
- `data/billing/BillingRepositoryTest.kt`: checkout remembers the transaction per user (and nothing on failure), confirm polls until PayU answers then refreshes the plan, an unsettled or offline payment stays remembered, an unknown transaction is forgotten, a return-link transaction doesn't touch the remembered one, cancel refreshes the plan.
- `ui/plans/BillingViewModelTest.kt`: the phone field (asked when missing locally or on the server), phone validation before calling the server, refusals staying on the sheet, opening the link and confirming on return, results following the server, the quiet resume check, offline after returning, cancel results.
- `ui/plans/BillingStatusRulesTest.kt`: the notice and buttons for each state, admin grants, the manual renewal window, and return-link parsing (host, scheme, path, transaction format).
- `data/plan/EntitlementsResponseTest.kt`: billing fields and enums from the `entitlements` JSON.
- `ui/admin/AdminViewModelTest.kt`: admin payments page, refund and cancel messages, refundable rule.
- Deno, `supabase/functions/_shared/`: `payu_test.ts` (hashes, IST dates, link payload, parsers, config, the client against a fake fetch: token reuse, signed commands, the salt never sent; payment-link lookups by `udf1`, the link's transactions and `checkPayment`), `checkout_test.ts` (phone, checkout and status requests, job auth), `webhook_test.ts` (classification including payment-link postbacks, redaction, form and JSON bodies), `admin_test.ts` (billing actions).
- pgTAP: `supabase/tests/payments_test.sql` and `billing_flow_test.sql` (checkout rules, payment results, renewals, expiry, grace, cancellation, refunds, disputes, admin billing), run live with the new migrations rolled back (`run_live.ps1 -Pending`).
- Gaps: no end-to-end test against PayU's sandbox yet; no UI tests for the sheet, result screen or history.

## Known limitations and TODOs

- PayU endpoint paths, command names and field names follow PayU's documentation as of 2026-10 and must be checked against the sandbox before going live (`payu.ts` header).
- Deployed on 2026-10-10 with PayU **test** credentials and `PAYU_BILLING_MODE=manual`; real payments need the live credentials and `PAYU_ENV=live`. Whenever any PayU secret is missing, `paymentsEnabled` is false and the Plans screen shows "Payments aren't available yet." with Go Pro disabled.
- `pay.returnHost` (autoomstudio.com) doesn't serve `/pay/return` or `assetlinks.json`, so a plain `https://autoomstudio.com/pay/return` link opens the company website, not the app. Only the intent link from the return page opens the app. Some browsers may block the automatic open, in which case the user taps the button.
- Billing emails are only logged; GST invoices, partial refunds from the app and a web checkout are out of scope (PRD phase 6).
- Switching interval with autopay on cancels the old mandate before the new link is paid; an abandoned switch leaves autopay off until the user sets it up again. PayU's `YEARLY` billing cycle hasn't been tried in the sandbox yet.
- The prices in the app's strings and `pricePaise` are fixed copies of `billing_price_paise`; changing a price needs both.
- Receipts are in-app only (transaction ID, amount, date, method); there is no PDF.

## Change history

| Date | Commit | Change |
|---|---|---|
| 2026-10-10 | - | Yearly Pro at ₹999: Monthly/Yearly picker, yearly autopay mandate, switching between intervals at the end of the paid period (migration `20261017000000_yearly_plan.sql`, not yet applied). |
| 2026-10-10 | - | After checkout, `payu-return` serves the return page and opens the app through an intent link, instead of redirecting to autoomstudio.com, which showed the company website. |
| 2026-10-10 | - | Admin revoke subscription: ends PayU Pro now and cancels any mandate, without a refund. |
| 2026-10-10 | - | Payment-link payments are verified under PayU's own txnid (from the webhook or the link's transactions); a webhook hash mismatch no longer drops the event. First test payment confirmed. |
| 2026-10-10 | - | Deployed to the live server in PayU test mode with manual renewal. |
| 2026-10-10 | - | PayU Payment Links: checkout with autopay or manual mode, server verification, Plans billing status, cancel autopay, renewals and expiry jobs, refunds and disputes, payment history and receipts, admin payments. |
