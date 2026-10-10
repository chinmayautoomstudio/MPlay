/** A 10-digit Indian mobile number from what the user typed: spaces, dashes, +91, 91 or a leading 0 are dropped. */
export function normalizePhone(value: unknown): string | null {
  if (typeof value !== "string") return null;
  let digits = value.replace(/[\s\-()]/g, "");
  if (digits.startsWith("+91")) digits = digits.slice(3);
  else if (digits.length === 12 && digits.startsWith("91")) digits = digits.slice(2);
  else if (digits.length === 11 && digits.startsWith("0")) digits = digits.slice(1);
  return /^[6-9][0-9]{9}$/.test(digits) ? digits : null;
}

export type BillingInterval = "month" | "year";

export type CheckoutRequest = { phone: string | null; interval: BillingInterval };

/**
 * `{ phone?, interval? }` from the app. Missing phone is fine (the profile may have one); a malformed one is not.
 * The interval defaults to "month" for app versions that don't send it.
 */
export function parseCheckoutRequest(body: unknown): CheckoutRequest | { error: "invalid_phone" | "invalid_interval" } {
  const b = (body ?? {}) as Record<string, unknown>;
  let interval: BillingInterval = "month";
  if (b.interval !== undefined && b.interval !== null) {
    if (b.interval !== "month" && b.interval !== "year") return { error: "invalid_interval" };
    interval = b.interval;
  }
  if (b.phone === undefined || b.phone === null || b.phone === "") return { phone: null, interval };
  const phone = normalizePhone(b.phone);
  return phone ? { phone, interval } : { error: "invalid_phone" };
}

const TXN = /^[A-Za-z0-9]{1,25}$/;

/** `{ txnId? }` for payment-status; null when txnId is present but malformed. */
export function parseStatusRequest(body: unknown): { txnId: string | null } | null {
  const b = (body ?? {}) as Record<string, unknown>;
  if (b.txnId === undefined || b.txnId === null) return { txnId: null };
  return typeof b.txnId === "string" && TXN.test(b.txnId) ? { txnId: b.txnId } : null;
}

export function validTxnId(value: unknown): string | null {
  return typeof value === "string" && TXN.test(value) ? value : null;
}

/** HTTP status for a begin_checkout refusal the app explains. */
export const CHECKOUT_REFUSALS: Record<string, number> = {
  not_found: 404,
  account_disabled: 403,
  invalid_phone: 400,
  phone_required: 409,
  payment_in_progress: 409,
  rate_limited: 429,
  already_subscribed: 409,
  switch_not_yet: 409,
  mandate_update_pending: 409,
};
