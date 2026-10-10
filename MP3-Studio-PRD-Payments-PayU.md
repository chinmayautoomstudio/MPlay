Markdown · MP3-Studio-PRD-Payments-PayU.md

# MP3 Studio: Payments PRD (PayU Pro Subscription)

**Scope:** Payment and subscription billing only **Product:** MP3 Studio, Android (Kotlin, Jetpack Compose), Supabase backend **Provider:** PayU (India) **Status:** Draft

---

## 1. Overview

Everything else in MP3 Studio (accounts, roles, plans, trial, usage limits, feature gating, Admin) is already built. This document covers the one remaining piece: **letting a user pay for the Pro plan through PayU and keeping their plan correct afterward.**

- **Product sold:** Pro plan, **₹99 per month**, auto-renewing.
- **Provider:** PayU only. No Razorpay, no Google Play Billing.
- **Distribution:** Direct APK.
- **Principle:** The server is the source of truth. The app never decides that a payment succeeded, and the PayU merchant key and salt never leave the server.

## 2. Existing System (do not rebuild)

The implementation should reuse what already exists. Before writing code, inspect the current Supabase schema and app code, then connect to them.

| Already exists | Used by payments for |
| --- | --- |
| Entitlements function (Free, Trial, Pro) and the app's feature gates | Granting and removing Pro access |
| Subscription and payment fields (plan, status, start and expiry dates, next billing date, last payment date, payment status, cancellation status) | Storing billing state |
| Payment events table | Recording every payment event |
| Plans screen, upgrade prompts, locked-feature prompts | Entry points to checkout |
| Admin screens | Viewing payment and subscription status |
| Row Level Security and the service role in Edge Functions | Protecting payment data |

If a needed field or table is missing, add it with a migration instead of duplicating an existing one.

## 3. Goals and Non-Goals

**Goals**

- A user can subscribe to Pro for ₹99 per month with a simple, trustworthy checkout.
- Pro activates only after the server verifies the payment with PayU.
- Monthly renewals happen automatically through the PayU standing instruction (autopay).
- Failed, cancelled and expired subscriptions return the user to Free correctly and automatically.
- Every payment event is recorded and visible to Admins.
- Nothing can be double-charged, double-activated or forged from the app.

**Non-Goals**

- Other providers (Razorpay, Google Play Billing, Stripe).
- Yearly plans, coupons, referral credit, or any price other than ₹99 per month.
- Storing card numbers, UPI IDs, CVV or bank details on our servers or in the app. PayU holds these.
- Changes to feature gating, trial rules or usage limits.

## 4. Platform and Technical Decisions

| Area | Decision |
| --- | --- |
| Checkout | PayU Android SDK (UPI SDK or Checkout Pro) launched from the Plans screen. The final choice depends on the payment methods enabled for the merchant account |
| Recurring billing | PayU standing instruction (SI) set up at the first payment, with a ₹99 monthly cycle and a maximum billing amount as required by PayU |
| Payment methods | Whatever PayU enables for recurring on the merchant account (expected: UPI AutoPay, cards, net banking e-mandate). The UI offers only the methods the merchant account supports |
| UPI | UPI collect is disabled by NPCI. Use UPI intent (the SDK's built-in smart intent) for UPI |
| Where secrets live | PayU merchant key and salt are stored only as Supabase Edge Function secrets. They are never in the app, the repo or the logs |
| Hash generation | The request hash is generated only on the server, following PayU's published formula for SI registration transactions. The app receives the finished parameters and the hash, never the salt |
| Server verification | After checkout, the server (1) validates PayU's response using the reverse hash, and (2) confirms the transaction status with PayU server-to-server. A client-side "success" is only a hint to refresh |
| Webhooks | A PayU webhook Edge Function receives payment and mandate events, verifies the hash, and updates state. It is idempotent |
| Environment | PayU test environment for development and QA; production keys only in the production Supabase project |
| Time and money | Amounts stored in paise as integers. Dates in UTC. Dates shown to the user in local time |
| Transport | HTTPS only |

*Check each PayU parameter and API name against PayU's current developer documentation during implementation. This PRD describes the behavior, not the exact field names.*

## 5. Subscription Lifecycle

### 5.1 States

| State | Meaning | Access |
| --- | --- | --- |
| `none` | Never subscribed | Free (or Trial) |
| `pending` | Checkout started, no confirmed result yet | Free (or Trial) |
| `active` | Paid and current | Pro |
| `past_due` | A renewal failed; retries are in progress | Pro until the grace period ends |
| `cancelled` | User cancelled; paid period not over | Pro until the period end |
| `expired` | Paid period ended, or renewal failed past the grace period | Free |
| `failed` | The first payment failed | Free (or Trial) |

### 5.2 Transitions

1. `none` or `expired` or `failed` → `pending`: user taps Go Pro and checkout begins.
2. `pending` → `active`: server confirms the first payment and the mandate is registered.
3. `pending` → `failed`: PayU reports failure, or the attempt times out.
4. `active` → `active`: monthly renewal succeeds; period extended by one month.
5. `active` → `past_due`: renewal fails.
6. `past_due` → `active`: a retry succeeds within the grace period.
7. `past_due` → `expired`: grace period ends with no success.
8. `active` → `cancelled`: user cancels; autopay stops; access continues to the period end.
9. `cancelled` → `expired`: the paid period ends.
10. `cancelled` → `active`: user resubscribes before the period ends (new mandate, no double charge).

### 5.3 Rules

- **First charge:** ₹99 is charged at signup (the first month is paid up front). Next billing date is one month later.
- **Trial:** A user who buys Pro during the 30-day trial gets Pro immediately. The remaining trial time is not carried over.
- **Billing period:** Each paid month is one calendar month from the activation or renewal date. Expiry and next billing dates are stored as exact timestamps.
- **Failed renewal grace (proposed):** 3 days with retries as allowed by PayU, with Pro kept during that time. This replaces the earlier "no grace by default" setting and needs your confirmation.
- **One active subscription per user:** A second checkout while one is active or pending is blocked.
- **Plan changes:** Not applicable (single paid plan).

## 6. Functional Requirements

Priority: **P0** = must ship, **P1** = should ship, **P2** = nice to have.

### 6.1 Checkout

| ID | Requirement | Priority |
| --- | --- | --- |
| CK1 | "Go Pro" on the Plans screen and in every locked-feature prompt opens checkout. The price (₹99 per month), billing cycle and "renews automatically until cancelled" are shown before payment | P0 |
| CK2 | The app asks the server to start a payment. The server creates a `pending` record with a unique transaction ID, and returns the PayU parameters and server-generated hash | P0 |
| CK3 | The server rejects the request if the user already has an active or pending subscription, or if the account is disabled | P0 |
| CK4 | The app launches the PayU checkout with the returned parameters, including the standing-instruction (autopay) details | P0 |
| CK5 | After checkout returns, the app shows "Confirming payment" and polls the server for the verified result. It does not unlock Pro on its own | P0 |
| CK6 | Show clear results: success (Pro is active), failed (reason, retry button), pending (we will update you shortly) | P0 |
| CK7 | The user can leave the app and come back during a pending payment; the status is restored from the server | P0 |
| CK8 | Pending payments with no result after a set time (proposed: 30 minutes) are checked with PayU and then marked failed or confirmed | P0 |
| CK9 | The checkout screen shows links to the terms of service, privacy policy and refund and cancellation policy | P1 |

### 6.2 Server Verification and Activation

| ID | Requirement | Priority |
| --- | --- | --- |
| SV1 | Verify PayU's response with the reverse hash, and verify the transaction with PayU's server-to-server API before changing any state | P0 |
| SV2 | Check that the verified amount, currency and transaction ID match the pending record exactly. Mismatches are rejected and logged | P0 |
| SV3 | On confirmed success, atomically: mark the payment successful, store the mandate reference, set the subscription to `active`, set start, expiry, next billing and last payment dates, and write the events | P0 |
| SV4 | The same transaction can be processed only once, whether it arrives through the app, the webhook, or a retry | P0 |
| SV5 | The webhook handler verifies the hash, ignores unknown or duplicate events, and always returns a quick success response so PayU does not keep retrying | P0 |
| SV6 | If the webhook and the app return race each other, the result is the same single activation | P0 |
| SV7 | Entitlements refresh immediately after activation, so the app switches to Pro without a restart | P0 |

### 6.3 Renewals

| ID | Requirement | Priority |
| --- | --- | --- |
| RN1 | A scheduled server job finds subscriptions whose next billing date is near and starts the renewal through PayU's recurring-charge flow | P0 |
| RN2 | Follow PayU and bank rules for the pre-debit notification before each recurring debit, and for the required notice period (confirm exact rules with PayU) | P0 |
| RN3 | On renewal success: extend the period by one month, update last payment and next billing dates, and record "subscription renewed" | P0 |
| RN4 | On renewal failure: set `past_due`, record the failure, notify the user, and retry within the grace period as PayU allows | P0 |
| RN5 | When the grace period ends without success: set `expired`, return the user to Free, and record "subscription expired" | P0 |
| RN6 | A scheduled job also expires subscriptions whose paid period has ended (including cancelled ones), so access never depends on an app launch | P0 |
| RN7 | If the user revokes the mandate in their bank or UPI app, the webhook or the next renewal failure is detected and handled as a failed renewal | P1 |
| RN8 | Renewals are idempotent: running the job twice in the same period never charges or extends twice | P0 |

### 6.4 Cancellation

| ID | Requirement | Priority |
| --- | --- | --- |
| CN1 | "Cancel subscription" in the Plans screen, with a confirmation that states the date Pro will end | P0 |
| CN2 | Cancelling calls the server, which cancels the PayU standing instruction and sets the state to `cancelled` | P0 |
| CN3 | Pro stays active until the paid period ends, then the user returns to Free | P0 |
| CN4 | If PayU fails to cancel the mandate, the server retries and the app shows "Cancellation in progress". No further charge is allowed after a confirmed user cancellation | P0 |
| CN5 | The user can resubscribe after cancelling | P1 |
| CN6 | Account deletion cancels any active mandate first | P0 |

### 6.5 Plans Screen and Billing Status

| ID | Requirement | Priority |
| --- | --- | --- |
| PS1 | The Plans screen shows current plan, status, start date, next billing date or expiry date, and the last payment date and amount | P0 |
| PS2 | Status banners: "Payment pending", "Renewal failed, please check your payment method", "Cancelled, Pro ends on " | P0 |
| PS3 | A "Fix payment" action for `past_due` that lets the user pay again through checkout | P1 |
| PS4 | Payment history list (date, amount, status, reference) | P1 |
| PS5 | Receipt email or in-app receipt for each successful payment | P1 |

### 6.6 Event Tracking

| ID | Requirement | Priority |
| --- | --- | --- |
| EV1 | Record these events with user, transaction ID, PayU reference, amount, time and result: payment initiated, payment successful, payment failed, subscription activated, subscription renewed, renewal failed, subscription cancelled, subscription expired, mandate registered, mandate revoked | P0 |
| EV2 | No card numbers, UPI IDs, CVV or bank account numbers are stored. Store only PayU's reference IDs and masked display info if PayU supplies it | P0 |
| EV3 | Keep raw PayU webhook payloads in a restricted log for troubleshooting, with sensitive fields removed | P1 |

### 6.7 Admin

| ID | Requirement | Priority |
| --- | --- | --- |
| AD1 | Admin user detail shows subscription state, payment status, billing dates and a payment event timeline | P0 |
| AD2 | Admin payment list with filters (successful, failed, pending, past due, cancelled) and search by user or transaction ID | P0 |
| AD3 | Admin can re-verify a pending or disputed payment with PayU (a safe re-check, not a manual override) | P1 |
| AD4 | Admin can cancel a user's subscription on request (logged in the audit log) | P1 |
| AD5 | Manually granting or removing Pro without payment is not allowed in v1 of payments, unless an audit-logged admin grant is added later | P1 |

### 6.8 Security and Integrity

| ID | Requirement | Priority |
| --- | --- | --- |
| SC1 | The merchant key and salt exist only as server secrets. The app and repository never contain them | P0 |
| SC2 | Every hash is generated on the server. The app cannot create or alter a payment amount | P0 |
| SC3 | Amount, product and plan are fixed on the server. A request from the app cannot change the price | P0 |
| SC4 | All payment tables are protected by Row Level Security. Users can read only their own rows and cannot write payment or subscription state | P0 |
| SC5 | Edge Functions require an authenticated user (except the PayU webhook, which is authenticated by its hash) and rate-limit payment creation | P0 |
| SC6 | All secrets, hashes and full webhook bodies are kept out of application logs | P0 |
| SC7 | The success and failure return URLs cannot activate Pro by themselves | P0 |

### 6.9 Compliance and Policy

| ID | Requirement | Priority |
| --- | --- | --- |
| CP1 | Publish a refund and cancellation policy, and link it from checkout, the Plans screen and the About screen | P0 |
| CP2 | Show the autopay terms clearly before payment: amount, monthly frequency, how to cancel | P0 |
| CP3 | Update the privacy policy to mention PayU as the payment processor and what payment data is held | P0 |
| CP4 | Confirm with PayU and a qualified adviser: recurring-payment rules and limits for UPI AutoPay, cards and e-mandate; pre-debit notification requirements; GST treatment and invoicing; whether ₹99 is tax-inclusive | P0 |

I am not a lawyer or tax adviser. Items in CP4 need confirmation from PayU and a qualified professional before launch.

## 7. Data Model (product level)

Extend the existing tables rather than creating duplicates.

| Table | Payment-related content |
| --- | --- |
| subscriptions | State, PayU mandate reference, plan, start date, expiry date, next billing date, last payment date, payment status, cancel-at-period-end, grace end date |
| payments | One row per payment attempt: user, transaction ID (unique), PayU reference, amount in paise, currency, status, created and completed times, failure reason, whether it was the first charge or a renewal |
| payment_events | Append-only event log (see EV1) |
| webhook_log | Restricted log of incoming PayU webhooks with processing result (see EV3) |

Unique constraints on transaction ID and PayU reference support idempotency.

**Server functions:** create payment (start checkout), verify payment, PayU webhook handler, renewal job, expiry job, cancel subscription, admin re-verify payment, admin cancel subscription.

## 8. Non-Functional Requirements

- **Correctness over speed:** A payment is never lost, duplicated or applied to the wrong user.
- **Reliability:** If the app is closed, the network drops or PayU is slow, the final state is still correct through the webhook and the status check.
- **Performance:** After a verified payment, the app shows Pro within a few seconds.
- **Security:** Secrets only on the server; hashes verified; RLS everywhere; no sensitive data in logs.
- **Observability:** Failed verifications, hash mismatches, webhook failures and renewal failures are logged and visible to Admins.
- **Accessibility:** Checkout and billing screens support font scaling, content descriptions and large touch targets.
- **Compatibility:** Android 8.0 and above; the PayU SDK's minimum requirements must be checked.

## 9. User Flows

1. **Subscribe:** Plans → Go Pro → see price and autopay terms → PayU checkout (UPI intent, card or net banking) → approve the mandate → "Confirming payment" → server verifies → Pro active.
2. **Locked feature:** Tap Sing Along or Detect BPM on Free → upgrade sheet → Go Pro → same as flow 1.
3. **Payment fails:** Checkout fails → clear message → retry. No change in access.
4. **Closed during payment:** User leaves mid-payment → webhook confirms later → next app open shows Pro.
5. **Renewal succeeds:** Pre-debit notice → monthly charge → period extended → user notices nothing.
6. **Renewal fails:** Charge fails → "Renewal failed" banner → user fixes payment → Pro continues. If not fixed by the grace end, back to Free.
7. **Cancel:** Plans → Cancel → confirm with end date → Pro until the period ends → Free.
8. **Admin check:** Admin → user → payment timeline → re-verify a pending payment if needed.

## 10. Screens

- Plans and subscription (status, dates, cancel, fix payment)
- Pro checkout summary (price, autopay terms, policy links)
- Payment result (success, failed, pending)
- Payment history
- Admin payment list and payment detail timeline

## 11. Success Metrics and Test Plan

Run in PayU test mode first, then a small live test with real ₹99 payments.

- First payment with each enabled method (UPI intent, card, net banking) activates Pro exactly once.
- A tampered amount or a forged success callback from the app does not activate Pro.
- Duplicate webhooks and a webhook racing the app return produce one activation and one payment record.
- Closing the app mid-payment still ends in the correct state.
- Renewal runs once per period; running the job twice does not double-charge.
- Failed renewal moves to `past_due`, retries, and ends in `expired` and Free if unresolved.
- Cancelling stops further charges and keeps Pro to the period end.
- Revoking the mandate in the bank or UPI app is handled as a failed renewal.
- A normal user cannot read other users' payments or change their own subscription state; an Admin can see them.
- The PayU salt does not appear in the APK, the repo, logs or network responses.

## 12. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| PayU does not approve or enable recurring (SI) for the merchant account, or limits the methods | Apply early; design the UI around the enabled methods; fall back to a manual monthly payment flow only if autopay is not available (decide before building) |
| Recurring payment rules for UPI AutoPay, cards and e-mandate change | Follow PayU's current docs; confirm pre-debit and limit rules before launch |
| Webhook delays or failures | Server-to-server status checks on a schedule and on app return; idempotent handlers |
| Users pay but Pro does not activate | "Confirming payment" state, admin re-verify, payment history, clear support contact in About |
| Users are charged after cancelling | Cancel the mandate on PayU first, retry on failure, and block charges after a confirmed cancellation |
| Salt or hash logic leaks from the app | Server-only hashing; secrets in Edge Function secrets; build check that no PayU secret strings exist in the APK |
| Refund disputes and chargebacks | Published refund policy, event records, PayU dashboard for disputes |
| Direct APK users cannot get Play-style subscription management | In-app cancel and billing screens; clear terms |
| ₹99 tax treatment unclear | Confirm GST and invoicing with an adviser; update the price display if needed |

## 13. Milestones

| Phase | Scope |
| --- | --- |
| P1: Merchant and backend setup | PayU merchant account with recurring enabled, test keys, secrets in Supabase, payment and webhook tables, server-side hash, create-payment function |
| P2: Checkout and verification | PayU SDK in the app, checkout with autopay consent, reverse-hash and server-to-server verification, activation, "Confirming payment" flow |
| P3: Webhooks and state | Webhook handler, idempotency, pending-payment sweeper, billing status on the Plans screen |
| P4: Renewals and cancellation | Renewal and expiry jobs, pre-debit handling, retries and grace period, cancel flow, mandate revocation handling |
| P5: Admin and history | Admin payment list and timeline, re-verify, payment history, receipts |
| P6: Testing and launch | Full test plan, small live payment test, policy pages, PayU go-live, monitoring |

## 14. Inputs Needed From You

- PayU merchant account approved for recurring payments, with test and live key and salt (kept out of chat and the repo).
- Which payment methods PayU enables for recurring on your account.
- Confirm: first month charged at signup, and a 3-day grace period for failed renewals.
- Whether ₹99 includes GST, and whether invoices or receipts are required.
- Refund and cancellation policy text, and a support email or phone shown for payment problems.

## 15. Decisions

- **Provider:** PayU only, with standing-instruction autopay.
- **Price:** ₹99 per month, charged up front at signup.
- **Distribution:** Direct APK.
- **Source of truth:** The server verifies every payment; the app and return URLs cannot activate Pro.
- **Cancellation:** Pro continues to the end of the paid period.
- **Scope:** Payments only; existing accounts, trial, limits and Admin are reused unchanged.