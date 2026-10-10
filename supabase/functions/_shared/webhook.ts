// Incoming PayU webhooks are notifications only (SV2): they are parsed just enough to know which payment, refund,
// dispute or mandate to re-check with PayU. Card, UPI and bank details are dropped before the body is stored (EV2,
// EV3).

export type WebhookKind = "payment" | "refund" | "dispute" | "mandate" | "unknown";

export type WebhookEvent = {
  kind: WebhookKind;
  txnId: string | null;
  payuRef: string | null;
  mandateRef: string | null;
  status: string | null;
  disputeState: "open" | "won" | "lost" | null;
  eventKey: string | null;
};

/** Fields never stored: card, UPI and bank account data and the hash. */
const SENSITIVE = /card|cvv|vpa|upi_?id|account|ifsc|bank_ref|name_on|hash|cardnum|issuing|token/i;

/** The body as a flat object, from form encoding or JSON. Null when it can't be read. */
export function parseWebhookBody(contentType: string | null, text: string): Record<string, unknown> | null {
  if (!text) return null;
  if (contentType?.includes("application/json") || text.trimStart().startsWith("{")) {
    try {
      const parsed = JSON.parse(text);
      return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed as Record<string, unknown> : null;
    } catch {
      return null;
    }
  }
  const out: Record<string, unknown> = {};
  for (const [key, value] of new URLSearchParams(text)) out[key] = value;
  return Object.keys(out).length > 0 ? out : null;
}

/** A copy of the body without sensitive fields, safe for the restricted webhook log. */
export function redact(body: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(body)) {
    if (SENSITIVE.test(key)) continue;
    out[key] = value && typeof value === "object" && !Array.isArray(value)
      ? redact(value as Record<string, unknown>)
      : value;
  }
  return out;
}

function str(value: unknown): string | null {
  return typeof value === "string" && value.length > 0 ? value : typeof value === "number" ? String(value) : null;
}

/** Which kind of event this is and what it refers to. */
export function classifyWebhook(body: Record<string, unknown>): WebhookEvent {
  const lower = Object.fromEntries(Object.entries(body).map(([k, v]) => [k.toLowerCase(), v]));
  const txnId = str(lower.txnid) ?? str(lower.invoicenumber) ?? str(lower.merchanttransactionid) ?? str(lower.udf1);
  const payuRef = str(lower.mihpayid) ?? str(lower.payuid) ?? str(lower.payu_id);
  const mandateRef = str(lower.authpayuid) ?? str(lower.mandateid) ?? str(lower.umrn);
  const status = str(lower.status)?.toLowerCase() ?? null;
  const type = (str(lower.event) ?? str(lower.eventtype) ?? str(lower.notificationtype) ?? str(lower.action) ?? "")
    .toLowerCase();

  let kind: WebhookKind = "unknown";
  let disputeState: WebhookEvent["disputeState"] = null;
  if (type.includes("chargeback") || type.includes("dispute") || str(lower.chargeback_status)) {
    kind = "dispute";
    const s = (str(lower.chargeback_status) ?? status ?? "").toLowerCase();
    disputeState = /won|reversed|closed_merchant/.test(s) ? "won" : /lost|debited|accepted/.test(s) ? "lost" : "open";
  } else if (type.includes("refund") || str(lower.refund_id)) {
    kind = "refund";
  } else if (type.includes("mandate") || type.includes("revok") || (mandateRef && !txnId)) {
    kind = "mandate";
  } else if (txnId) {
    kind = "payment";
  }

  const id = [kind, txnId ?? payuRef ?? mandateRef ?? "", status ?? "", disputeState ?? "", str(lower.refund_id) ?? ""]
    .join(":");
  return {
    kind,
    txnId,
    payuRef,
    mandateRef,
    status,
    disputeState,
    eventKey: kind === "unknown" && !txnId && !payuRef ? null : id,
  };
}
