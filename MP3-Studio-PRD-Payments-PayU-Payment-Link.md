Markdown · MP3-Studio-PRD-Payments-PayU.md

# MP3 Studio: Payments PRD (PayU Pro Subscription)

**Scope:** Payment and subscription billing only **Product:** MP3 Studio, Android (Kotlin, Jetpack Compose), Supabase backend **Provider:** PayU (India), using PayU Payment Links **Status:** Draft

---

## 1. Overview

Everything else in MP3 Studio (accounts, roles, plans, trial, usage limits, feature gating, Admin) is already built. This document covers the one remaining piece: **letting a user pay for the Pro plan through a PayU Payment Link and keeping their plan correct afterward.**

- **Product sold:** Pro plan, **₹99 per month**, auto-renewing.
- **Provider and method:** PayU only, using **PayU Payment Links**. The server creates a link for each payment attempt, the app opens it, and the user pays on PayU's hosted page. There is no PayU SDK inside the app.
- **Recurring billing:** The link is a PayU recurring (standing-instruction) payment link, so the user approves monthly autopay on the same page. A fallback without autopay is described in section 5.4.
- **Distribution:** Direct APK.
- **Principle:** The server is the source of truth. The app never decides that a payment succeeded, and PayU credentials never leave the server.

## 2. Existing System and Required Changes

The implementation reuses the accounts, plans, trial, usage limits, feature gates and Admin that already exist. **The database and server code are not yet payment-ready for PayU.** They were built for the earlier Razorpay and Google Play plan, so they must be changed first (section 2.1).

| Already exists | Used by payments for |
| --- | --- |
| Entitlements function (Free, Trial, Pro) and the app's feature gates | Granting and removing Pro access (needs changes, see 2.1) |
| Subscription and payment-event tables | Storing billing state and events (need changes, see 2.1) |
| Plans screen, upgrade prompts, locked-feature prompts | Entry points to checkout |
| Admin screens | Viewing payment and subscription status |
| Row Level Security and the service role in Edge Functions | Protecting payment data |
| Account deletion function | Must be changed to handle PayU mandates (see CN6) |

Before writing code, inspect the live schema and functions and compare them with 2.1.

### 2.1 Schema and server changes required

| Area | Current problem | Required change |
| --- | --- | --- |
| `subscriptions.provider` and `payment_events.provider` | `subscriptions` accepts `razorpay`, `play` and `admin`; `payment_events` accepts only `razorpay` and `play` | Allow `payu` in both. Keep `admin` valid on `subscriptions` (Admin grants already use it, see AD5). Stop writing `razorpay` and `play` |
| Subscription states | `past_due` and `failed` do not exist, and an unused `halted` value (Razorpay's name for failed autopay) is allowed | Add `past_due` and `failed`, and remove `halted` (migrate any rows first). Add the autopay fields below |
| `subscriptions.provider_ref` | `not null`, but the mandate reference only exists after payment | Make it nullable, or replace it with a nullable mandate reference column |
| New subscription fields | None | `autopay_status` (`on`, `off`, `not_set`, `revoked`), `mandate_ref`, `mandate_start`, `mandate_end`, `billing_anchor_day`, `grace_end`, `cancel_at_period_end` |
| `compute_entitlements` and `plan_of` | Both hold the same Pro rule, and neither gives Pro during the grace period | Pro while status is `active`, `cancelled` before period end, or `past_due` before the grace end. Change both in one migration, ideally with `plan_of` calling one shared rule, so the app and Admin lists never disagree |
| `delete_account`, the `delete-account` Edge Function and the app | The function checks only `razorpay` and `play` rows, so a PayU subscription would be deleted with the account and its mandate left live. It also refuses with `active_subscription` until the user cancels, and the Edge Function and app treat that as a refusal | Replace with the flow in CN6: the Edge Function cancels the PayU mandate first, and the database deletes only after confirmation. Update the refusal handling in the function and the app |
| One subscription per user | Nothing enforces it; only `unique (provider, provider_ref)` exists | Add a partial unique index allowing one row per user in `pending`, `active` or `past_due`. Admin-granted rows are excluded from checkout checks (AD5). This also helps prevent two live mandates (5.5) |
| `payments` table | Does not exist | Create (section 7) |
| `webhook_log` table | Does not exist | Create (section 7) |
| Payment status values | No refund or dispute values | Add `refunded`, `partially_refunded`, `disputed` (section 6.10) |
| Scheduled jobs | None | Create (section 6.11) |
| User phone number | Not stored (sign-in is Google only) | Add a validated mobile number field to the profile (CK13) |
| App side | The Go Pro button in the upgrade sheet is disabled; the Admin screen has a Razorpay provider label and no PayU one; the Kotlin `subscriptionStatus` is a plain string, so new states would arrive unhandled; the manifest has no https App Link intent filter | Enable Go Pro with the checkout flow, add PayU and `admin` labels, handle every state in 5.1, and add the App Link entry (section 4) |
| Existing tests | `plans_test.sql` and `rls_test.sql` assume the old providers and states | Update them with the migration, and add tests for the new constraints, states and the unique index |

Every change ships as a migration with tests, and existing rows are migrated safely.

## 3. Goals and Non-Goals

**Goals**

- A user can subscribe to Pro for ₹99 per month with a simple, trustworthy checkout.
- Pro activates only after the server verifies the payment with PayU.
- Monthly renewals happen automatically through the PayU standing instruction (autopay).
- Failed, cancelled and expired subscriptions return the user to Free correctly and automatically.
- Every payment event is recorded and visible to Admins.
- Nothing can be double-charged, double-activated or forged from the app.

**Non-Goals**

- Other providers (Razorpay, Google Play Billing, Stripe), and the PayU Android SDK (checkout uses Payment Links instead).
- Yearly plans, coupons, referral credit, or any price other than ₹99 per month.
- Storing card numbers, UPI IDs, CVV or bank details on our servers or in the app. PayU holds these.
- Changes to feature gating, trial rules or usage limits.

## 4. Platform and Technical Decisions

| Area | Decision |
| --- | --- |
| Checkout | PayU Payment Link (PayU's hosted payment page). The server creates one link per payment attempt through PayU's Create Payment Link API, and the app opens the returned URL in a Chrome Custom Tab (default browser as fallback) |
| Link type | Recurring (standing instruction) payment link: `source` set to `si_payment_link` with `siDetails` holding billing amount 99, currency INR, cycle MONTHLY, interval 1, a payment start date and a payment end date. **The ₹99 charged by the link is the first month. The SI payment start date is set one billing cycle after the link is created, so autopay never debits on day one** (verify in test, see section 11). The payment end date is set as described in 5.6 |
| Payment methods | Whatever PayU enables for recurring links on the merchant account. For SI links PayU lists eNACH, credit card, debit card and UPI. The link restricts methods through the enforce-payment-method setting. A free-trial option is not available for UPI, so it is not used |
| API access: two credential sets | (1) **Payment Links API:** client ID, client secret and a short-lived access token (scope to create payment links), plus the merchant ID header. (2) **Recurring debits, status checks, mandate cancellation and refunds:** PayU's merchant-key APIs, which need the **merchant key and salt**, with a SHA-512 request hash generated on the server. Both sets live only in Supabase Edge Function secrets, and PayU may issue separate test and live values for each |
| One link per attempt | The invoice number is our unique transaction ID (PayU rejects a duplicate invoice number, which is a useful safeguard). Maximum payments per link is 1, partial payment is off, the amount is fixed by the server and not editable by the customer, and the link expires after 2 hours (see CK9 for payments still in progress at expiry) |
| Linking a payment to a user | The invoice number maps to our pending payment record. The internal payment ID is also sent in a user-defined field as an extra check, but it is never trusted alone |
| Return to the app (browser redirect) | The link's **success and failure URLs** are only browser redirects. They are https pages on a domain you control (for example on autoomstudio.com) that open the app through an Android App Link, with a "Return to MP3 Studio" button as fallback. They only trigger a status refresh and never activate Pro. App Links need an intent filter with auto-verify in the manifest and an `assetlinks.json` file at `/.well-known/assetlinks.json` on that domain, listing the app's package name and the SHA-256 fingerprint of the signing certificate (release and debug builds differ) |
| Webhooks (server notifications) | **Separate from the redirect URLs above.** The webhook URL points to a Supabase Edge Function and is configured where PayU supports it (the PayU Dashboard, or per link if PayU honours that for the account). A webhook is only a notification, never proof: the handler never acts on its body, and always confirms with PayU's status API before changing state. If PayU provides a signature or reverse hash for webhooks, verify it as an extra filter; a forged webhook can then do no harm, because the status check decides |
| Recurring debits | After the first payment registers the mandate, our server triggers each monthly debit through PayU's recurring-transaction (standing instruction) API using the key and salt credentials, with the pre-debit notification required by PayU and the banks. Our server controls the debit date, not PayU's schedule. Confirm the exact API and rules with PayU |
| Customer messages | PayU is told not to email, SMS or WhatsApp the link (the app is the only channel). Only the customer details PayU requires are sent: name, email and mobile number (see CK13) |
| Environment | PayU test environment for development and QA; production credentials only in the production Supabase project |
| Time and money | Amounts stored in paise as integers. Dates in UTC. Dates shown to the user in local time |
| Transport | HTTPS only |

*Check every PayU endpoint, field name and rule against PayU's current developer documentation during implementation. PayU's own pages are inconsistent in places (for example whether a payment token is single-use, and whether API-created subscription links are available to every merchant account), so confirm those points with PayU support before relying on them.*

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

**Autopay flag.** Alongside the state, `autopay_status` records the mandate: `on`, `off`, `not_set` (first payment succeeded but no mandate was confirmed) or `revoked` (user cancelled it at the bank or UPI app). An `active` subscription with autopay `not_set`, `off` or `revoked` behaves like `cancelled`: Pro until the paid period ends, no renewal attempts, then `expired`. The app shows "Autopay is not active. Set it up to keep Pro" with a button that starts the mandate replacement flow (5.5).

### 5.2 Transitions

1. `none` or `expired` or `failed` → `pending`: user taps Go Pro and checkout begins.
2. `pending` → `active`: server confirms the first payment. If the mandate is also confirmed, autopay is `on`; if not, autopay is `not_set` (see SV4).
3. `pending` → `failed`: PayU reports failure, or the attempt ends without a result (CK9).
4. `active` → `active`: monthly renewal succeeds; period extended by one month.
5. `active` → `past_due`: renewal fails.
6. `past_due` → `active`: a retry succeeds within the grace period, or the user completes "Fix payment" (5.5).
7. `past_due` → `expired`: grace period ends with no success.
8. `active` → `cancelled`: user cancels; mandate cancelled; access continues to the period end.
9. `cancelled` → `expired`: the paid period ends.
10. `cancelled` → `active`: user resubscribes before the period ends, under the mandate replacement rule (5.5).
11. `active` with autopay `on` → `active` with autopay `revoked`: the mandate is revoked at the bank or UPI app; no further renewals; Pro to the period end.
12. Any paid state → `expired` or `cancelled`: a refund or lost dispute (section 6.10).

### 5.3 Rules

- **First charge:** ₹99 is charged at signup (the first month is paid up front). Next billing date is one month later.
- **Trial:** A user who buys Pro during the 30-day trial gets Pro immediately. The remaining trial time is not carried over.
- **Billing period and month-end dates:** The billing anchor day is the day of the month of the first confirmed payment, in India time (Asia/Kolkata). Every later date is calculated from the anchor, not from the previous date. If the month has no such day, use its last day. Example: a first payment on 31 Jan renews on 28 Feb (29 in a leap year), then 31 Mar, 30 Apr and 31 May. Expiry and next billing dates are stored as exact UTC timestamps.
- **Failed-renewal notice:** On a failed renewal the user gets an in-app banner on next open and an email from the server (email provider to be chosen, see section 14). Push notifications are not assumed. Send the email on the first failure, one day before the grace end, and on expiry.
- **Price changes:** The mandate caps each debit at ₹99 (or the higher cap chosen in 5.6). Any future price above the cap requires every subscriber to approve a new mandate; the old price cannot be raised silently. Decide the cap now (section 14).
- **Failed renewal grace (proposed):** 3 days with retries as allowed by PayU, with Pro kept during that time. This replaces the earlier "no grace by default" setting and needs your confirmation.
- **One active subscription per user:** A second checkout while one is active or pending is blocked.
- **Plan changes:** Not applicable (single paid plan).

### 5.4 Fallback: manual renewal (only if autopay is not available)

If PayU cannot enable recurring (SI) payment links for the merchant account, use ordinary one-time payment links instead:

- Each paid ₹99 gives Pro for one month. The user pays a new link each month.
- No mandate, no pre-debit notice and no renewal job. The expiry job still returns users to Free when the paid month ends.
- The app reminds the user 5 days, 2 days and 0 days before expiry with an in-app banner (and a push notification if push is available), with a "Renew now" button that creates a new link.
- The state `past_due` is not used. Everything else (verification, webhooks, cancellation of access, Admin) is the same.

This is a fallback only. The main design is autopay.

### 5.5 Mandate replacement rule (Fix payment, resubscribe, set up autopay)

A user must never have two live mandates. This applies to "Fix payment" on a `past_due` subscription (PS3), resubscribing while `cancelled` (transition 10), and setting up autopay when it is `not_set` or `revoked`.

1. The server first cancels the old mandate with PayU and waits for confirmation, or confirms through a status check that it is already inactive.
2. If the old mandate cannot be confirmed cancelled, no new link is created. The app shows "We are updating your payment setup, try again shortly", the server retries the cancellation, and Admins see it.
3. Only then is a new payment link created. It charges ₹99 and registers the new mandate.
4. **Fix payment from `past_due`:** the paid month starts on the payment date, Pro continues without a gap, the new anchor day is the payment date, and the new mandate's first debit is one cycle later.
5. **Resubscribe while `cancelled`:** the user pays ₹99 now and the paid period is extended by one month beyond the current end date, so no paid time is lost. The new mandate's first debit is on the new end date.
6. The new mandate does not charge until its start date, so the up-front ₹99 and the first autopay debit can never fall on the same day.

### 5.6 Mandate end date and expiry

- The mandate's payment end date is set to the longest tenure PayU and the bank allow for the chosen method. Proposed default: 5 years, to be confirmed with PayU (CP4).
- The server stores `mandate_end`. Thirty days before it, the app shows "Renew autopay to keep Pro", and the user goes through the mandate replacement flow (5.5).
- Mandate end is never allowed to pass silently: the renewal job flags any subscription whose mandate ends before its next billing date.

## 6. Functional Requirements

Priority: **P0** = must ship, **P1** = should ship, **P2** = nice to have.

### 6.1 Checkout (PayU Payment Link)

| ID | Requirement | Priority |
| --- | --- | --- |
| CK1 | "Go Pro" on the Plans screen and in every locked-feature prompt starts checkout. The price (₹99 per month), billing cycle and "renews automatically until cancelled" are shown before the user continues | P0 |
| CK2 | The app asks the server to start a payment. The server creates a pending record with a unique transaction ID, creates the PayU recurring payment link for it, and returns only the link URL and the transaction ID to the app | P0 |
| CK3 | The server rejects the request if the user already has an active subscription or the account is disabled. If a pending payment with a live, unexpired link exists, the server returns that same link instead of creating a second one | P0 |
| CK4 | The app opens the link in a Chrome Custom Tab (default browser as fallback). The user pays on PayU's hosted page and approves the autopay mandate there | P0 |
| CK5 | PayU redirects to the success or failure URL, which opens the app. The redirect is only a hint: the app shows "Confirming payment" and asks the server for the verified result | P0 |
| CK6 | If the user comes back without a redirect (closes the tab, or returns from a UPI app), the app checks the pending payment's status when it resumes | P0 |
| CK7 | Show clear results: success (Pro is active), failed (reason and a retry button), pending (we will update you shortly) | P0 |
| CK8 | A pending payment survives leaving the app; the status is restored from the server when the user returns | P0 |
| CK9 | A link expires after 2 hours. A payment the user has already started when the link expires is not cut off: the sweeper keeps checking its status with PayU every 15 to 30 minutes for 24 hours (up to 3 business days for eNACH, which can take longer to approve) before marking it failed. The app shows "Waiting for your bank" in the meantime. A new attempt always creates a new link, and is blocked while an earlier payment is still being resolved | P0 |
| CK10 | PayU does not send the link to the customer by email, SMS or WhatsApp; the app is the only channel | P1 |
| CK11 | Only the customer details PayU requires are sent (name, email, mobile number), and the privacy policy says so | P0 |
| CK13 | UPI AutoPay, eNACH and pre-debit notices need the customer's mobile number, but sign-in is by Google only. If the profile has no verified-format number, checkout asks for a 10-digit Indian mobile number before creating the link, validates it, saves it to the profile for later renewals, and sends it only to PayU | P0 |
| CK12 | The checkout summary shows links to the terms of service, privacy policy and refund and cancellation policy | P1 |

### 6.2 Server Verification and Activation

| ID | Requirement | Priority |
| --- | --- | --- |
| SV1 | Before changing any state, the server confirms with PayU directly (payment link or transaction status check) that the payment succeeded. A webhook body or a redirect alone is never enough | P0 |
| SV2 | A webhook is a trigger, not proof. If PayU supplies a signature or reverse hash, verify it as a first filter. Whatever the result, state only changes after the status check in SV1 | P0 |
| SV3 | Check that the verified amount, currency and invoice number match the pending record exactly. Mismatches are rejected and logged | P0 |
| SV4 | Confirm that the autopay mandate was registered and store its reference. If the first payment succeeded but no mandate is confirmed, still grant the paid month, set autopay to `not_set` (an `active` subscription that ends at the period end, see 5.1), and prompt the user to set up autopay (5.5) | P0 |
| SV5 | On confirmed success, atomically: mark the payment successful, store the mandate reference, set the subscription to `active`, set start, expiry, next billing and last payment dates, and write the events | P0 |
| SV6 | The same transaction is processed only once, whether it arrives through the app, the webhook, or a retry. The unique invoice number and PayU reference enforce this | P0 |
| SV7 | The webhook handler first saves the raw event to `webhook_log` (status `received`), then returns a success response. If saving fails it returns an error so PayU retries. Processing happens after saving. If processing fails, the row is marked `failed`, and a scheduled job retries it with backoff (for example 5 attempts over 24 hours), then flags it for Admins. Duplicate and unknown events are recorded and ignored | P0 |
| SV8 | If the webhook and the app's return race each other, the result is the same single activation | P0 |
| SV9 | Entitlements refresh immediately after activation, so the app switches to Pro without a restart | P0 |

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
| CN6 | Account deletion first asks the server to cancel any live PayU mandate and waits for confirmation. If the mandate cannot be confirmed cancelled, deletion stops with "We could not cancel your subscription, try again", with no data removed. After confirmation, the account is deleted (this replaces the current behaviour, which refuses deletion until the user cancels). Payment and invoice records needed for tax and dispute purposes are kept in a minimal form, detached from the profile, for the period an adviser confirms (CP4) | P0 |

### 6.5 Plans Screen and Billing Status

| ID | Requirement | Priority |
| --- | --- | --- |
| PS1 | The Plans screen shows current plan, status, start date, next billing date or expiry date, and the last payment date and amount | P0 |
| PS2 | Status banners: "Payment pending", "Renewal failed, please check your payment method", "Cancelled, Pro ends on " | P0 |
| PS3 | A "Fix payment" action for `past_due` that follows the mandate replacement rule in 5.5: the old mandate is cancelled first, then a new link is created | P1 |
| PS4 | Payment history list (date, amount, status, reference) | P1 |
| PS5 | Receipt email or in-app receipt for each successful payment | P1 |

### 6.6 Event Tracking

| ID | Requirement | Priority |
| --- | --- | --- |
| EV1 | Record these events with user, transaction ID, PayU reference, amount, time and result: payment initiated, payment successful, payment failed, subscription activated, subscription renewed, renewal failed, subscription cancelled, subscription expired, mandate registered, mandate revoked, refund issued, dispute opened, dispute closed | P0 |
| EV2 | No card numbers, UPI IDs, CVV or bank account numbers are stored. Store only PayU's reference IDs and masked display info if PayU supplies it | P0 |
| EV3 | Keep raw PayU webhook payloads in a restricted log for troubleshooting, with sensitive fields removed | P1 |

### 6.7 Admin

| ID | Requirement | Priority |
| --- | --- | --- |
| AD1 | Admin user detail shows subscription state, payment status, billing dates and a payment event timeline | P0 |
| AD2 | Admin payment list with filters (successful, failed, pending, past due, cancelled) and search by user or transaction ID | P0 |
| AD3 | Admin can re-verify a pending or disputed payment with PayU (a safe re-check, not a manual override) | P1 |
| AD4 | Admin can cancel a user's subscription on request (logged in the audit log) | P1 |
| AD5 | Admin Pro grants already exist (`admin_grant_pro` and `admin_revoke_pro`, audit-logged, stored as a subscription with provider `admin`) and stay. An admin grant never creates a mandate, link or renewal, is shown as "Granted by admin" in the payment views, and does not block a user from subscribing: if the user pays while an admin grant is active, the paid subscription is created alongside it and Pro lasts as long as either one is current | P1 |

### 6.8 Security and Integrity

| ID | Requirement | Priority |
| --- | --- | --- |
| SC1 | PayU credentials (client ID and secret, access tokens, and the merchant key and salt) exist only as server secrets. The app and repository never contain them | P0 |
| SC2 | Every payment link is created on the server with a fixed amount. The app never calls PayU's APIs and cannot create or change a link or an amount | P0 |
| SC3 | Amount, product and plan are fixed on the server. A request from the app cannot change the price | P0 |
| SC4 | All payment tables are protected by Row Level Security. Users can read only their own rows and cannot write payment or subscription state | P0 |
| SC5 | Edge Functions require an authenticated user, except the PayU webhook, which is public by necessity and therefore harmless: it saves the event and acts only after a server-to-server status check (SV1, SV2). Payment creation is rate-limited | P0 |
| SC6 | Secrets, request hashes and full webhook bodies are kept out of application logs. Raw webhook bodies live only in the restricted `webhook_log` table | P0 |
| SC7 | The success and failure return URLs cannot activate Pro by themselves | P0 |

### 6.9 Compliance and Policy

| ID | Requirement | Priority |
| --- | --- | --- |
| CP1 | Publish a refund and cancellation policy, and link it from checkout, the Plans screen and the About screen | P0 |
| CP2 | Show the autopay terms clearly before payment: amount, monthly frequency, how to cancel | P0 |
| CP3 | Update the privacy policy to mention PayU as the payment processor and what payment data is held | P0 |
| CP4 | Confirm with PayU and a qualified adviser: recurring-payment rules and limits for UPI AutoPay, cards and e-mandate; pre-debit notification requirements; GST treatment and invoicing; whether ₹99 is tax-inclusive | P0 |

I am not a lawyer or tax adviser. Items in CP4 need confirmation from PayU and a qualified professional before launch.

### 6.10 Refunds and Chargebacks

| ID | Requirement | Priority |
| --- | --- | --- |
| RF1 | Payment status values `refunded`, `partially_refunded` and `disputed` exist and are shown in the Admin payment list | P0 |
| RF2 | An Admin can issue a full refund through PayU's refund API, following the published refund policy (CP1). The action is audit-logged and never changes state until PayU confirms the refund | P0 |
| RF3 | When a refund of the current month's payment is confirmed, the mandate is cancelled first, Pro ends immediately, and the subscription becomes `expired` (or `cancelled` with an end date of now) | P0 |
| RF4 | Refund results also arrive by webhook and status check, and are handled idempotently like payments | P0 |
| RF5 | When PayU reports a dispute or chargeback, the payment is marked `disputed`, renewals are paused by cancelling the mandate, Admins are alerted, and Pro stays until the dispute is resolved | P1 |
| RF6 | If a dispute is lost, treat it as a refund (RF3). If it is won, restore the payment status; the user can resubscribe with the normal flow | P1 |
| RF7 | Partial refunds are recorded without changing access unless an Admin also cancels | P2 |

### 6.11 Scheduled Jobs

| ID | Requirement | Priority |
| --- | --- | --- |
| JB1 | Jobs run in Supabase with `pg_cron` calling Edge Functions through `pg_net`. The service credential used by the cron call is kept in Supabase Vault, not in code | P0 |
| JB2 | Jobs: renewals and pre-debit notices (hourly), expiry (hourly), pending-payment and expired-link sweeper (every 15 to 30 minutes), webhook retry (every 5 minutes), failed-renewal emails, mandate-end reminders (daily) | P0 |
| JB3 | Every job is safe to run twice or in parallel: rows are claimed with `FOR UPDATE SKIP LOCKED` and every action is idempotent (RN8) | P0 |
| JB4 | Each run writes a result (processed, failed, skipped) that Admins can see; a job that has not run for twice its interval raises an alert | P1 |

## 7. Data Model (product level)

Extend the existing tables rather than creating duplicates.

| Table | Payment-related content |
| --- | --- |
| subscriptions | State, autopay status, PayU mandate reference (nullable), mandate start and end dates, billing anchor day, plan, start date, expiry date, next billing date, last payment date, payment status, cancel-at-period-end, grace end date |
| payments | One row per payment attempt: user, transaction ID and invoice number (unique), payment link URL and PayU link reference, link expiry, PayU payment reference, amount in paise, currency, status, created and completed times, failure reason, whether it was the first charge or a renewal |
| payment_events | Append-only event log (see EV1) |
| webhook_log | Restricted log of every incoming PayU webhook: raw body, received time, status (`received`, `processed`, `failed`, `ignored`), attempt count, last error (see SV7, EV3) |

Unique constraints on transaction ID and PayU reference support idempotency.

**Server functions:** create payment link (start checkout), check payment status, PayU webhook handler, webhook retry job, expired-link sweeper, renewal job, expiry job, cancel subscription, replace mandate, refund payment, delete account (updated), admin re-verify payment, admin cancel subscription.

## 8. Non-Functional Requirements

- **Correctness over speed:** A payment is never lost, duplicated or applied to the wrong user.
- **Reliability:** If the app is closed, the network drops or PayU is slow, the final state is still correct through the webhook and the status check.
- **Performance:** After a verified payment, the app shows Pro within a few seconds.
- **Security:** Secrets only on the server; webhooks never trusted without a status check; RLS everywhere; no sensitive data in logs.
- **Observability:** Failed verifications, amount or reference mismatches, webhook failures and renewal failures are logged and visible to Admins.
- **Accessibility:** Checkout and billing screens support font scaling, content descriptions and large touch targets.
- **Compatibility:** Android 8.0 and above; Chrome Custom Tabs and Android App Links must work on the supported phones.

## 9. User Flows

1. **Subscribe:** Plans → Go Pro → see price and autopay terms → PayU checkout (UPI intent, card or net banking) → approve the mandate → "Confirming payment" → server verifies → Pro active.
2. **Locked feature:** Tap Sing Along or Detect BPM on Free → upgrade sheet → Go Pro → same as flow 1.
3. **Payment fails:** Checkout fails → clear message → retry. No change in access.
4. **Closed during payment:** User leaves mid-payment → webhook confirms later → next app open shows Pro.
5. **Renewal succeeds:** Pre-debit notice → monthly charge on the billing day → period extended → user notices nothing.
6. **Renewal fails:** Charge fails → banner and email → user taps Fix payment → old mandate cancelled, new link → Pro continues. If not fixed by the grace end, back to Free.
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
- PayU credentials (client ID and secret, tokens, key and salt) do not appear in the APK, the repo, logs or network responses.
- A payment link opened twice, or paid twice, results in one activation and no second charge; an expired link cannot be paid, and a late payment is caught by the expiry check.
- The hosted page and UPI intent work from a Chrome Custom Tab on several phone brands (for example Samsung, Xiaomi, Oppo, Vivo and a Pixel), and returning to the app works with and without the redirect.
- The success and failure URLs open the app correctly and cannot activate Pro on their own.
- One payment link created per attempt, with the invoice number equal to our transaction ID.
- **No day-one double charge:** after the first ₹99 payment, PayU records exactly one debit on that day, and the first autopay debit falls one cycle later.
- Billing dates follow the anchor rule: a 31 Jan start renews on 28 Feb (29 in a leap year), then 31 Mar.
- Fix payment and resubscribe never leave two live mandates; if the old mandate cannot be cancelled, no new link is created.
- A webhook whose processing fails is retried by the job and ends processed; PayU's own retries are not relied on.
- A forged webhook cannot change any state.
- Refund and dispute events change status and access as specified in 6.10.
- Account deletion cancels the mandate first, and is blocked if cancellation cannot be confirmed.
- A payment still in progress when its link expires is resolved by the sweeper, not marked failed early.
- The App Link opens the app from the redirect page on a release build, and the fallback button works when verification fails.
- Autopay `not_set` ends at the paid period end with a clear prompt, and a mandate near its end date triggers the reminder.

## 12. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| PayU does not enable recurring (SI) payment links, or API-created subscription links, for the merchant account, or limits the methods | Apply early and ask PayU to confirm API access for SI links; design around the enabled methods; use the manual renewal fallback in section 5.4 if autopay is not available |
| Recurring payment rules for UPI AutoPay, cards and e-mandate change | Follow PayU's current docs; confirm pre-debit and limit rules before launch |
| Webhook delays or failures | Server-to-server status checks on a schedule and on app return; idempotent handlers |
| Users pay but Pro does not activate | "Confirming payment" state, admin re-verify, payment history, clear support contact in About |
| Users are charged after cancelling | Cancel the mandate on PayU first, retry on failure, and block charges after a confirmed cancellation |
| PayU credentials leak from the app | The app never calls PayU; secrets only in Edge Function secrets; build check that no PayU secret strings exist in the APK |
| UPI intent or the hosted page misbehaves in a Chrome Custom Tab on some phones | Test on several brands early; fall back to the default browser; keep the status check on app resume |
| Payment links can be shared or reused | One link per attempt, one payment per link, short expiry, fixed amount, server-side matching of the invoice number to the user |
| Redirect back to the app fails (no App Link or the browser blocks it) | The app checks status on resume; show a "Return to MP3 Studio" button on the redirect page |
| PayU docs are inconsistent about token reuse and link behavior | Test in PayU's test environment first and confirm with PayU support before launch |
| Refund disputes and chargebacks | Published refund policy, refund and dispute flows in 6.10, event records, PayU dashboard |
| Double charge on day one (up-front ₹99 plus an immediate autopay debit) | SI start date set one cycle ahead; explicit test; our server controls debit dates |
| Two live mandates after Fix payment or resubscribe | Mandate replacement rule (5.5): cancel and confirm first |
| A processing failure loses a webhook | Raw events saved first; retry job; status checks as the source of truth |
| A price change needs every user to approve again | Choose the mandate cap deliberately before launch; plan a re-consent flow for any increase |
| Mandate ends and autopay silently stops | Store the mandate end date; 30-day reminder; renewal job flags it |
| Missing mobile number blocks UPI AutoPay or eNACH | Collect and validate it at checkout (CK13) |
| Direct APK users cannot get Play-style subscription management | In-app cancel and billing screens; clear terms |
| ₹99 tax treatment unclear | Confirm GST and invoicing with an adviser; update the price display if needed |

## 13. Milestones

| Phase | Scope |
| --- | --- |
| P1: PayU and backend setup | PayU merchant account with recurring payment links enabled, both credential sets (client ID and secret; key and salt), test environment, secrets in Supabase, the schema migration in 2.1, access-token handling, create-payment-link function |
| P2: Checkout and return | Go Pro flow, open the link in a Custom Tab, App Link or redirect page, "Confirming payment" state, status check on resume |
| P3: Verification and webhooks | Server-to-server status check, webhook handler, idempotency, expired-link sweeper, activation, billing status on the Plans screen |
| P4: Renewals and cancellation | Scheduled jobs (6.11), renewal and expiry, pre-debit handling, retries and grace period, failed-renewal emails, cancel flow, mandate replacement (5.5), mandate end reminders, account deletion change (manual renewal fallback if needed) |
| P5: Admin, refunds and history | Admin payment list and timeline, re-verify, refunds and disputes (6.10), payment history, receipts |
| P6: Testing and launch | Full test plan including multi-brand phone tests, small live payment test, policy pages, PayU go-live, monitoring |

## 14. Inputs Needed From You

- PayU merchant account approved for **recurring (standing instruction) payment links through the API**, with test and live credentials for both sets: client ID, secret and merchant ID, and the merchant key and salt. Keep them out of chat and the repo.
- Where PayU lets you set the webhook URL (Dashboard or per link), and PayU's webhook signature method, if any.
- The mandate cap per debit (₹99, or higher to allow a future price rise) and the longest mandate tenure PayU allows (proposed 5 years).
- An email service for failed-renewal and receipt emails (for example one already used with Supabase).
- Which payment methods PayU enables for recurring links on your account (UPI AutoPay, cards, eNACH).
- A page on a domain you control (for example on autoomstudio.com) for the success and failure return URLs, the right to publish `/.well-known/assetlinks.json` there, and the SHA-256 fingerprint of the release signing certificate (and the debug one for testing).
- Confirm: the first month is charged at signup, and a 3-day grace period for failed renewals.
- Whether ₹99 includes GST, and whether invoices or receipts are required.
- Refund and cancellation policy text (including who can approve refunds), data-retention period for payment records after account deletion, and a support email or phone shown for payment problems.
- Confirm that you want autopay (recurring link). If you meant simple one-time links with a manual monthly renewal, use the fallback in section 5.4 as the main design.

## 15. Decisions

- **Provider and method:** PayU only, through PayU Payment Links created by the server. No PayU SDK in the app.
- **Billing:** Recurring (standing instruction) payment link for ₹99 per month with autopay; manual renewal links are the fallback.
- **Price:** ₹99 per month, charged up front at signup. The first autopay debit is one cycle later.
- **Webhooks:** Saved first, never trusted alone, reprocessed by a scheduled job if processing fails.
- **Credentials:** Two sets on the server: client ID and secret for links, key and salt for recurring debits, status, cancellation and refunds.
- **Mandates:** Never more than one live mandate per user; replacement cancels the old one first.
- **Scheduling:** `pg_cron` with Edge Functions; all jobs idempotent.
- **Distribution:** Direct APK.
- **Source of truth:** The server verifies every payment with PayU; the app, the redirect and the webhook body alone cannot activate Pro.
- **Link rules:** One link per attempt, one payment per link, fixed amount, short expiry, never sent to the customer by PayU.
- **Cancellation:** Pro continues to the end of the paid period.
- **Scope:** Payments only; existing accounts, trial, limits and Admin are reused unchanged.