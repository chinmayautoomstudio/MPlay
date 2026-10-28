// Billing steps shared by the checkout, status, webhook, subscription, jobs, admin and delete-account functions.
// PayU (India) or Cashfree (International) decides what happened; the SQL functions decide what that means for
// the subscription (payments PRD 6.2).
import type { SupabaseClient } from "./supabase.ts";
import { CashfreeClient, cashfreeConfig, findTxnId, isCashfreeUrl } from "./cashfree.ts";
import type { BillingInterval } from "./checkout.ts";
import type { BillingCurrency, BillingRegion } from "./geo.ts";
import { type Customer, PayuClient, type PayuConfig, payuConfig, type VerifiedPayment } from "./payu.ts";
import { classifyWebhook, type WebhookEvent } from "./webhook.ts";

/** The PayU client from the Edge Function secrets; null (logged) when they are not set. */
export function payuFromEnv(): { config: PayuConfig; payu: PayuClient } | null {
  const config = payuConfig();
  if (!config) {
    console.error("PayU secrets are not set");
    return null;
  }
  return { config, payu: new PayuClient(config) };
}

/** The Cashfree client from the Edge Function secrets; null (logged) when they are not set. */
export function cashfreeFromEnv(): CashfreeClient | null {
  const config = cashfreeConfig();
  if (!config) {
    console.error("Cashfree secrets are not set");
    return null;
  }
  return new CashfreeClient(config);
}

export async function rpc<T = Record<string, unknown>>(
  admin: SupabaseClient,
  fn: string,
  args: Record<string, unknown>,
): Promise<T> {
  const { data, error } = await admin.rpc(fn, args);
  if (error) throw new Error(`${fn} failed: ${error.code ?? ""} ${error.message}`);
  return data as T;
}

function message(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

/**
 * Verifies one transaction with PayU or Cashfree (from the stored link URL) and applies the answer (SV1, SV4).
 * payuTxnId is PayU's own txnid when a webhook named one. Returns apply_payment_result's answer.
 */
export async function resolvePayment(
  admin: SupabaseClient,
  payu: PayuClient,
  txnId: string,
  si: boolean,
  payuTxnId: string | null = null,
  cashfree: CashfreeClient | null = null,
): Promise<Record<string, unknown>> {
  const { data } = await admin.from("payments").select("link_url").eq("txn_id", txnId).maybeSingle();
  if (isCashfreeUrl(data?.link_url)) {
    if (!cashfree) throw new Error("Cashfree secrets are not set");
    const verified = await cashfree.checkOrder(txnId);
    return await rpc(admin, "apply_payment_result", { p_txn: txnId, p_result: verified });
  }
  let verified: VerifiedPayment = await payu.checkPayment(txnId, payuTxnId);
  // The first payment of an autopay link registers the mandate; confirm it when the status answer doesn't say.
  if (si && verified.status === "success" && !verified.mandateRef && verified.payuRef) {
    const state = await payu.mandateStatus(verified.payuRef).catch(() => "unknown" as const);
    if (state === "active") verified = { ...verified, mandateRef: verified.payuRef };
  }
  return await rpc(admin, "apply_payment_result", { p_txn: txnId, p_result: verified });
}

/** Resolves each {txnId, si} and counts the outcomes. Errors are logged without bodies and counted as failed. */
export async function resolveAll(
  admin: SupabaseClient,
  payu: PayuClient,
  rows: { txnId: string; si: boolean }[],
  cashfree: CashfreeClient | null = null,
): Promise<{ processed: number; failed: number }> {
  let processed = 0;
  let failed = 0;
  for (const row of rows) {
    try {
      await resolvePayment(admin, payu, row.txnId, row.si, null, cashfree);
      processed++;
    } catch (e) {
      failed++;
      console.error(`payment check ${row.txnId} failed: ${message(e)}`);
    }
  }
  return { processed, failed };
}

/**
 * Cancels the live mandate on a subscription with PayU and records the confirmation (CN2, 5.5). When PayU doesn't
 * confirm, the request stays recorded and the jobs retry it (CN4). True when the mandate is confirmed gone.
 */
export async function cancelMandate(
  admin: SupabaseClient,
  payu: PayuClient,
  subscriptionId: string,
  userCancel: boolean,
): Promise<boolean> {
  const request = await rpc<{ result?: string; error?: string; mandateRef?: string; method?: string | null }>(
    admin,
    "request_mandate_cancel",
    { p_sub: subscriptionId, p_user_cancel: userCancel },
  );
  if (request.result === "no_mandate") return true;
  if (request.error || !request.mandateRef) return false;
  return await confirmRevoked(admin, payu, subscriptionId, request.mandateRef, request.method ?? null);
}

/** Asks PayU to revoke, then checks the mandate status if the answer wasn't a clear yes. */
export async function confirmRevoked(
  admin: SupabaseClient,
  payu: PayuClient,
  subscriptionId: string,
  mandateRef: string,
  method: string | null,
): Promise<boolean> {
  let gone = false;
  try {
    gone = await payu.revokeMandate(mandateRef, method);
  } catch (e) {
    console.error(`mandate revoke for ${subscriptionId} failed: ${message(e)}`);
  }
  if (!gone) {
    gone = (await payu.mandateStatus(mandateRef).catch(() => "unknown")) === "inactive";
  }
  if (gone) await rpc(admin, "confirm_mandate_cancelled", { p_sub: subscriptionId, p_revoked: false });
  return gone;
}

export type CheckoutOutcome =
  | { ok: true; url: string; txnId: string }
  | { ok: false; error: string; txnId?: string };

type BeginResult = {
  result?: "created" | "existing";
  error?: string;
  txnId?: string;
  url?: string;
  subscriptionId?: string;
  amountPaise?: number;
  currency?: BillingCurrency;
  linkExpiresAt?: string;
  periodEnd?: string;
  customer?: Customer;
};

export type CheckoutProviders = {
  cashfree?: CashfreeClient | null;
  region?: BillingRegion;
};

/**
 * Starts checkout for the user (CK2, CK3, CK9, 5.5): first settles their earlier attempts so an abandoned
 * expired link doesn't block a new one, cancels an old mandate when the new link replaces it, then creates
 * a PayU link (India) or a Cashfree order (International).
 */
export async function startCheckout(
  admin: SupabaseClient,
  payu: PayuClient,
  config: PayuConfig,
  userId: string,
  phone: string | null,
  interval: BillingInterval,
  currency: BillingCurrency = "INR",
  extras: CheckoutProviders = {},
): Promise<CheckoutOutcome> {
  const gateway = extras.region === "intl" ? "cashfree" : "payu";
  if (gateway === "cashfree" && !extras.cashfree) return { ok: false, error: "cashfree_unavailable" };

  const open = await rpc<{ txnId: string; si: boolean }[]>(admin, "payments_to_check", {
    p_user: userId,
    p_txn: null,
    p_min_seconds: 10,
  });
  await resolveAll(admin, payu, open, extras.cashfree ?? null);

  const mode = gateway === "cashfree" || currency === "USD" ? "manual" : config.mode;
  const begin = () =>
    rpc<BeginResult>(admin, "begin_checkout", {
      p_user: userId,
      p_phone: phone,
      p_mode: mode,
      p_interval: interval,
      p_currency: currency,
    });
  let started = await begin();
  if (started.error === "mandate_active" && started.subscriptionId) {
    if (!await cancelMandate(admin, payu, started.subscriptionId, false)) {
      return { ok: false, error: "mandate_update_pending" };
    }
    started = await begin();
  }
  if (started.error) return { ok: false, error: started.error, txnId: started.txnId };
  if (started.result === "existing" && started.url) {
    const reusedWrongGateway = gateway === "cashfree" ? !isCashfreeUrl(started.url) : isCashfreeUrl(started.url);
    if (reusedWrongGateway && started.txnId) {
      await rpc(admin, "fail_checkout", { p_txn: started.txnId, p_reason: "wrong_gateway_retry" });
      started = await begin();
      if (started.error) return { ok: false, error: started.error, txnId: started.txnId };
    } else {
      return { ok: true, url: started.url, txnId: started.txnId! };
    }
  }

  const txnId = started.txnId!;
  try {
    const link = gateway === "cashfree"
      ? await extras.cashfree!.createOrder({
        txnId,
        amountPaise: started.amountPaise!,
        currency: started.currency ?? currency,
        customer: started.customer!,
        customerId: userId,
        purpose: interval === "year" ? "MP3 Studio Pro, 1 year" : "MP3 Studio Pro, 1 month",
      })
      : await payu.createLink({
        txnId,
        amountPaise: started.amountPaise!,
        expiresAt: new Date(started.linkExpiresAt!),
        periodEnd: new Date(started.periodEnd!),
        customer: started.customer!,
        mode,
        interval,
        currency: started.currency ?? currency,
        successUrl: withQuery(config.successUrl, txnId),
        failureUrl: withQuery(config.failureUrl, txnId),
      });
    await rpc(admin, "attach_payment_link", { p_txn: txnId, p_url: link.url, p_link_ref: link.ref });
    return { ok: true, url: link.url, txnId };
  } catch (e) {
    console.error(`payment link for ${txnId} failed: ${message(e)}`);
    await rpc(admin, "fail_checkout", { p_txn: txnId, p_reason: "link_error" }).catch(() => null);
    return { ok: false, error: gateway === "cashfree" ? "cashfree_unavailable" : "payu_unavailable" };
  }
}

function withQuery(url: string, txnId: string): string {
  const u = new URL(url);
  u.searchParams.set("txn", txnId);
  return u.toString();
}

/**
 * Settles a refund PayU has been asked for (RF2-RF4). A full refund of the current month cancels the mandate first
 * (RF3), then ends Pro.
 */
export async function resolveRefund(
  admin: SupabaseClient,
  payu: PayuClient,
  txnId: string,
  requestId: string,
): Promise<string> {
  const refund = await payu.refundStatus(requestId);
  if (refund.state === "pending" || refund.state === "unknown") return refund.state;
  if (refund.state === "success") {
    const { data } = await admin.from("payments").select("subscription_id, amount_paise").eq("txn_id", txnId)
      .maybeSingle();
    const full = !refund.amountPaise || (data && refund.amountPaise >= data.amount_paise);
    if (full && data?.subscription_id) await cancelMandate(admin, payu, data.subscription_id, true);
  }
  await rpc(admin, "apply_refund_result", {
    p_txn: txnId,
    p_state: refund.state === "success" ? "success" : "failure",
    p_amount_paise: refund.amountPaise ?? null,
  });
  return refund.state;
}

/**
 * Handles one stored webhook (SV2, SV7, RN7, RF4-RF6). Nothing in the body is trusted: each kind is re-checked with
 * PayU (payment status, refund status, mandate status) before anything changes. Returns "processed" or "ignored".
 */
export async function processWebhook(
  admin: SupabaseClient,
  payu: PayuClient,
  body: Record<string, unknown>,
): Promise<"processed" | "ignored"> {
  const event: WebhookEvent = classifyWebhook(body);
  let txnId = event.txnId;
  if (!txnId && event.payuRef) txnId = await rpc<string | null>(admin, "payment_by_ref", { p_ref: event.payuRef });

  switch (event.kind) {
    case "payment": {
      if (!txnId) return "ignored";
      const { data } = await admin.from("payments").select("si").eq("txn_id", txnId).maybeSingle();
      if (!data) return "ignored";
      await resolvePayment(admin, payu, txnId, data.si, event.payuTxnId);
      return "processed";
    }
    case "refund": {
      if (!txnId) return "ignored";
      const { data } = await admin.from("payments").select("refund_request_id").eq("txn_id", txnId).maybeSingle();
      if (!data?.refund_request_id) return "ignored";
      await resolveRefund(admin, payu, txnId, data.refund_request_id);
      return "processed";
    }
    case "dispute": {
      // PayU's dispute notifications are checked against the payment: it must exist and have been paid.
      if (!txnId || !event.disputeState) return "ignored";
      const verified = await payu.checkPayment(txnId, event.payuTxnId);
      if (verified.status !== "success") return "ignored";
      const result = await rpc<{ subscriptionId?: string }>(admin, "apply_dispute", {
        p_txn: txnId,
        p_state: event.disputeState,
      });
      // Renewals pause while a dispute is open or lost (RF5).
      if (event.disputeState !== "won" && result.subscriptionId) {
        await cancelMandate(admin, payu, result.subscriptionId, true);
      }
      return "processed";
    }
    case "mandate": {
      if (!event.mandateRef) return "ignored";
      const subscriptionId = await rpc<string | null>(admin, "subscription_by_mandate", { p_mandate: event.mandateRef });
      if (!subscriptionId) return "ignored";
      if (await payu.mandateStatus(event.mandateRef) !== "inactive") return "ignored";
      await rpc(admin, "confirm_mandate_cancelled", { p_sub: subscriptionId, p_revoked: true });
      return "processed";
    }
    default:
      return "ignored";
  }
}

/**
 * Handles a Cashfree webhook. The body is only used to find our txn id; the payment is re-checked with Cashfree
 * before apply_payment_result runs.
 */
export async function processCashfreeWebhook(
  admin: SupabaseClient,
  cashfree: CashfreeClient,
  body: unknown,
): Promise<"processed" | "ignored"> {
  const txnId = findTxnId(body);
  if (!txnId) return "ignored";
  const { data } = await admin.from("payments").select("link_url").eq("txn_id", txnId).maybeSingle();
  if (!data || !isCashfreeUrl(data.link_url)) return "ignored";
  const verified = await cashfree.checkOrder(txnId);
  if (verified.status === "pending" || verified.status === "not_found") return "ignored";
  await rpc(admin, "apply_payment_result", { p_txn: txnId, p_result: verified });
  return "processed";
}
