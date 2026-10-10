// PayU client for Pro billing (payments PRD section 4). Two credential sets, both Edge Function secrets only (SC1):
// the Payment Links API (client ID and secret, OAuth access token, merchant ID header) and the merchant-key APIs
// (`postservice.php` commands signed with SHA-512 over key and salt) for status checks, recurring debits, mandate
// cancellation and refunds. Endpoint paths, command names and field names follow PayU's developer documentation as
// of 2026-10; verify with PayU before going live (PRD section 4 note).
import type { BillingInterval } from "./checkout.ts";

export type BillingMode = "autopay" | "manual";

export type PayuConfig = {
  env: "test" | "live";
  clientId: string;
  clientSecret: string;
  merchantId: string;
  key: string;
  salt: string;
  mode: BillingMode;
  successUrl: string;
  failureUrl: string;
};

const HOSTS = {
  test: {
    accounts: "https://uat-accounts.payu.in",
    links: "https://uatoneapi.payu.in",
    info: "https://test.payu.in",
  },
  live: {
    accounts: "https://accounts.payu.in",
    links: "https://oneapi.payu.in",
    info: "https://info.payu.in",
  },
};

/** Autopay unless PAYU_BILLING_MODE is exactly "manual" (fallback in PRD 5.4). */
export function billingMode(value: string | undefined): BillingMode {
  return value === "manual" ? "manual" : "autopay";
}

/** The PayU settings from the environment; null when any credential is missing. */
export function payuConfig(env: Record<string, string | undefined> = Deno.env.toObject()): PayuConfig | null {
  const required = [
    "PAYU_CLIENT_ID",
    "PAYU_CLIENT_SECRET",
    "PAYU_MERCHANT_ID",
    "PAYU_KEY",
    "PAYU_SALT",
    "PAYU_SUCCESS_URL",
    "PAYU_FAILURE_URL",
  ];
  if (required.some((name) => !env[name])) return null;
  return {
    env: env.PAYU_ENV === "live" ? "live" : "test",
    clientId: env.PAYU_CLIENT_ID!,
    clientSecret: env.PAYU_CLIENT_SECRET!,
    merchantId: env.PAYU_MERCHANT_ID!,
    key: env.PAYU_KEY!,
    salt: env.PAYU_SALT!,
    mode: billingMode(env.PAYU_BILLING_MODE),
    successUrl: env.PAYU_SUCCESS_URL!,
    failureUrl: env.PAYU_FAILURE_URL!,
  };
}

export async function sha512Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-512", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}

/** Hash for a `postservice.php` command: sha512(key|command|var1|salt). */
export function commandHash(key: string, command: string, var1: string, salt: string): Promise<string> {
  return sha512Hex(`${key}|${command}|${var1}|${salt}`);
}

/**
 * Reverse hash PayU puts on payment callbacks and webhooks:
 * sha512([additionalCharges|]salt|status||||||udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key).
 */
export function reverseHash(fields: Record<string, string | undefined>, key: string, salt: string): Promise<string> {
  const f = (name: string) => fields[name] ?? "";
  const parts = [
    salt,
    f("status"),
    "",
    "",
    "",
    "",
    "",
    f("udf5"),
    f("udf4"),
    f("udf3"),
    f("udf2"),
    f("udf1"),
    f("email"),
    f("firstname"),
    f("productinfo"),
    f("amount"),
    f("txnid"),
    key,
  ];
  if (fields.additionalCharges) parts.unshift(fields.additionalCharges);
  return sha512Hex(parts.join("|"));
}

/** Constant-time comparison of two hex digests. */
export function sameHash(a: string, b: string): boolean {
  const x = a.toLowerCase();
  const y = b.toLowerCase();
  if (x.length !== y.length) return false;
  let diff = 0;
  for (let i = 0; i < x.length; i++) diff |= x.charCodeAt(i) ^ y.charCodeAt(i);
  return diff === 0;
}

const IST_OFFSET_MS = 330 * 60 * 1000;

function istParts(time: Date): string[] {
  const iso = new Date(time.getTime() + IST_OFFSET_MS).toISOString();
  return [iso.slice(0, 10), iso.slice(11, 19)];
}

/** yyyy-MM-dd in India time. */
export function istDate(time: Date): string {
  return istParts(time)[0];
}

/** yyyy-MM-dd HH:mm:ss in India time. */
export function istDateTime(time: Date): string {
  return istParts(time).join(" ");
}

export function rupees(paise: number): string {
  return (paise / 100).toFixed(2);
}

export function paise(amount: unknown): number | null {
  const value = typeof amount === "number" ? amount : typeof amount === "string" ? Number(amount) : NaN;
  return Number.isFinite(value) ? Math.round(value * 100) : null;
}

export type Customer = { name: string; email: string; phone: string };

export type LinkRequest = {
  txnId: string;
  amountPaise: number;
  expiresAt: Date;
  /** End of the period being paid for; the standing instruction's first debit date (PRD 4: never on day one). */
  periodEnd: Date;
  customer: Customer;
  mode: BillingMode;
  /** What one payment buys and how often the standing instruction debits; "month" when not given. */
  interval?: BillingInterval;
  successUrl: string;
  failureUrl: string;
  /** Mandate tenure (PRD 5.6, proposed 5 years). */
  mandateYears?: number;
};

/**
 * Body for PayU's Create Payment Link API. One payment per link, fixed amount, no partial payment, PayU sends
 * nothing to the customer (CK10), and in autopay mode a monthly or yearly standing instruction for the same amount.
 */
export function buildLinkPayload(req: LinkRequest): Record<string, unknown> {
  const yearly = req.interval === "year";
  const mandateEnd = new Date(req.periodEnd);
  mandateEnd.setUTCFullYear(mandateEnd.getUTCFullYear() + (req.mandateYears ?? 5));
  const payload: Record<string, unknown> = {
    invoiceNumber: req.txnId,
    subAmount: Number(rupees(req.amountPaise)),
    currency: "INR",
    description: yearly ? "MP3 Studio Pro, 1 year" : "MP3 Studio Pro, 1 month",
    source: req.mode === "autopay" ? "si_payment_link" : "API",
    isPartialPaymentAllowed: false,
    isAmountFilledByCustomer: false,
    maxPaymentsAllowed: 1,
    expiryDate: istDateTime(req.expiresAt),
    successURL: req.successUrl,
    failureURL: req.failureUrl,
    viaEmail: false,
    viaSms: false,
    viaWhatsapp: false,
    customer: { name: req.customer.name, email: req.customer.email, phone: req.customer.phone },
    udf: { udf1: req.txnId },
  };
  if (req.mode === "autopay") {
    payload.siDetails = {
      billingAmount: rupees(req.amountPaise),
      billingCurrency: "INR",
      billingCycle: yearly ? "YEARLY" : "MONTHLY",
      billingInterval: 1,
      paymentStartDate: istDate(req.periodEnd),
      paymentEndDate: istDate(mandateEnd),
    };
  }
  return payload;
}

/** The link URL and reference from a Create Payment Link answer; null when PayU refused. */
export function parseLinkResponse(body: unknown): { url: string; ref: string | null } | null {
  const b = (body ?? {}) as Record<string, unknown>;
  const result = (b.result ?? {}) as Record<string, unknown>;
  const url = result.paymentLink ?? b.paymentLink;
  if (typeof url !== "string" || !url.startsWith("https://")) return null;
  const ref = result.invoiceNumber ?? result.id ?? null;
  return { url, ref: ref == null ? null : String(ref) };
}

export type VerifiedStatus = "success" | "failure" | "pending" | "not_found";

/** What PayU's verify_payment says about one transaction, in the shape apply_payment_result takes. */
export type VerifiedPayment = {
  status: VerifiedStatus;
  txnId: string;
  payuRef?: string;
  amountPaise?: number;
  method?: string;
  mandateRef?: string;
  reason?: string;
};

/** Reads verify_payment's `transaction_details` for txnId. Unknown statuses count as pending, never as success. */
export function parseVerifyResponse(txnId: string, body: unknown): VerifiedPayment {
  const b = (body ?? {}) as Record<string, unknown>;
  const details = (b.transaction_details ?? {}) as Record<string, Record<string, unknown>>;
  const t = details[txnId];
  if (!t || t.mihpayid === "Not Found" || String(t.status ?? "").toLowerCase() === "not found") {
    return { status: "not_found", txnId };
  }
  const raw = String(t.status ?? "").toLowerCase();
  const unmapped = String(t.unmappedstatus ?? "").toLowerCase();
  let status: VerifiedStatus;
  if (raw === "success" && (unmapped === "" || unmapped === "captured" || unmapped === "success")) status = "success";
  else if (raw === "failure" || raw === "failed" || unmapped === "usercancelled" || unmapped === "bounced" ||
    unmapped === "dropped" || unmapped === "failed") status = "failure";
  else status = "pending";

  const amount = paise(t.transaction_amount ?? t.amt);
  const si = (t.si_details ?? t.siDetails) as Record<string, unknown> | undefined;
  const mandate = si ? (si.authpayuid ?? si.authPayuId ?? t.mihpayid) : undefined;
  return {
    status,
    txnId: String(t.txnid ?? txnId),
    payuRef: t.mihpayid == null ? undefined : String(t.mihpayid),
    amountPaise: amount ?? undefined,
    method: t.mode == null ? undefined : String(t.mode).toUpperCase(),
    mandateRef: mandate == null ? undefined : String(mandate),
    reason: status === "failure" ? String(t.error_Message ?? t.field9 ?? unmapped ?? "failed").slice(0, 200) : undefined,
  };
}

/**
 * verify_payment's answer for PayU's own txnid of a payment-link payment, reported under our txnId. PayU runs each
 * link payment under a txnid it picks and carries ours in udf1; when udf1 is present it must be ours, and with
 * requireUdf1 it must be present.
 */
export function parseLinkVerify(txnId: string, payuTxnId: string, body: unknown, requireUdf1: boolean): VerifiedPayment {
  const b = (body ?? {}) as Record<string, unknown>;
  const t = ((b.transaction_details ?? {}) as Record<string, Record<string, unknown>>)[payuTxnId];
  const udf1 = t?.udf1 == null ? "" : String(t.udf1);
  if (udf1 !== txnId && (requireUdf1 || udf1 !== "")) return { status: "not_found", txnId };
  return { ...parseVerifyResponse(payuTxnId, body), txnId };
}

/** PayU's txnids for the payments made on one payment link, successful ones first. Empty when PayU has none. */
export function parseLinkTransactions(body: unknown): string[] {
  const result = ((body ?? {}) as Record<string, unknown>).result as Record<string, unknown> | null | undefined;
  const rows = Array.isArray(result?.data) ? result!.data as Record<string, unknown>[] : [];
  const success = (r: Record<string, unknown>) => String(r.status ?? "").toLowerCase() === "success" ? 1 : 0;
  const ids = [...rows].sort((a, b) => success(b) - success(a))
    .map((r) => r.merchantReferenceId == null ? "" : String(r.merchantReferenceId))
    .filter((id) => id !== "");
  return [...new Set(ids)];
}

const STATUS_RANK: Record<VerifiedStatus, number> = { not_found: 0, failure: 1, pending: 2, success: 3 };

/** "active", "inactive" or "unknown" from a mandate status answer. */
export function parseMandateStatus(body: unknown): "active" | "inactive" | "unknown" {
  const text = JSON.stringify(body ?? {}).toLowerCase();
  if (/"(status|mandate_status|mandatestatus)"\s*:\s*"(revoked|cancelled|canceled|inactive|expired|paused)"/.test(text)) {
    return "inactive";
  }
  if (/"(status|mandate_status|mandatestatus)"\s*:\s*"(active|live)"/.test(text)) return "active";
  return "unknown";
}

/** True when a postservice answer says the request was accepted (`status: 1`). */
export function accepted(body: unknown): boolean {
  const status = (body as Record<string, unknown> | null)?.status;
  return status === 1 || status === "1" || status === "success";
}

export type RefundState = "success" | "pending" | "failure" | "unknown";

/** Refund state from check_action_status for one request ID. */
export function parseRefundStatus(body: unknown): { state: RefundState; amountPaise?: number } {
  const text = JSON.stringify(body ?? {});
  const status = /"status"\s*:\s*"(success|pending|failure|failed|queued)"/i.exec(text)?.[1]?.toLowerCase();
  const amount = /"amount"\s*:\s*"?([0-9.]+)"?/i.exec(text)?.[1];
  const state: RefundState = status === "success"
    ? "success"
    : status === "pending" || status === "queued"
    ? "pending"
    : status === "failure" || status === "failed"
    ? "failure"
    : "unknown";
  return { state, amountPaise: amount ? paise(amount) ?? undefined : undefined };
}

type Fetch = typeof fetch;

/** Server-to-server PayU calls. Never logs secrets, hashes or full bodies (SC6). */
export class PayuClient {
  private readonly tokens = new Map<string, { value: string; until: number }>();

  constructor(private readonly config: PayuConfig, private readonly http: Fetch = fetch) {}

  private get hosts() {
    return HOSTS[this.config.env];
  }

  private async accessToken(scope = "create_payment_links"): Promise<string> {
    const cached = this.tokens.get(scope);
    if (cached && cached.until > Date.now() + 60_000) return cached.value;
    const response = await this.http(`${this.hosts.accounts}/oauth/token`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        client_id: this.config.clientId,
        client_secret: this.config.clientSecret,
        grant_type: "client_credentials",
        scope,
      }),
    });
    const body = await response.json().catch(() => null) as { access_token?: string; expires_in?: number } | null;
    if (!response.ok || !body?.access_token) throw new Error(`PayU token request failed (${response.status})`);
    this.tokens.set(scope, { value: body.access_token, until: Date.now() + (body.expires_in ?? 600) * 1000 });
    return body.access_token;
  }

  async createLink(req: LinkRequest): Promise<{ url: string; ref: string | null }> {
    const response = await this.http(`${this.hosts.links}/payment-links`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${await this.accessToken()}`,
        merchantId: this.config.merchantId,
      },
      body: JSON.stringify(buildLinkPayload(req)),
    });
    const body = await response.json().catch(() => null);
    const link = response.ok ? parseLinkResponse(body) : null;
    if (!link) throw new Error(`PayU link creation failed (${response.status})`);
    return link;
  }

  /** A signed `postservice.php` command. var1 is a string or an object sent as JSON. */
  async command(command: string, var1: string | Record<string, unknown>, extra: Record<string, string> = {}) {
    const v1 = typeof var1 === "string" ? var1 : JSON.stringify(var1);
    const form = new URLSearchParams({
      key: this.config.key,
      command,
      var1: v1,
      hash: await commandHash(this.config.key, command, v1, this.config.salt),
      ...extra,
    });
    const response = await this.http(`${this.hosts.info}/merchant/postservice.php?form=2`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: form,
    });
    if (!response.ok) throw new Error(`PayU ${command} failed (${response.status})`);
    return await response.json().catch(() => null) as unknown;
  }

  async verifyPayment(txnId: string): Promise<VerifiedPayment> {
    return parseVerifyResponse(txnId, await this.command("verify_payment", txnId));
  }

  /** PayU's txnids for the payments made on the link whose invoice number is txnId (last 45 days). */
  async linkTransactions(txnId: string): Promise<string[]> {
    const day = 86_400_000;
    const query = new URLSearchParams({
      pageSize: "20",
      dateFrom: istDate(new Date(Date.now() - 45 * day)),
      dateTo: istDate(new Date(Date.now() + day)),
    });
    const response = await this.http(
      `${this.hosts.links}/payment-links/${encodeURIComponent(txnId)}/txns?${query}`,
      {
        headers: {
          Authorization: `Bearer ${await this.accessToken("read_payment_links")}`,
          merchantId: this.config.merchantId,
        },
      },
    );
    const body = await response.json().catch(() => null);
    if (response.status === 404) return [];
    if (!response.ok) throw new Error(`PayU link transactions failed (${response.status})`);
    return parseLinkTransactions(body);
  }

  /**
   * Status of our transaction txnId. Renewals run under our txnid; a payment-link payment runs under PayU's own, so
   * when PayU doesn't know ours this tries the txnid a webhook named (payuTxnId), then the link's transactions.
   */
  async checkPayment(txnId: string, payuTxnId?: string | null): Promise<VerifiedPayment> {
    let best = await this.verifyPayment(txnId);
    if (best.status !== "not_found") return best;
    if (payuTxnId && payuTxnId !== txnId) {
      best = parseLinkVerify(txnId, payuTxnId, await this.command("verify_payment", payuTxnId), true);
      if (best.status === "success") return best;
    }
    for (const id of (await this.linkTransactions(txnId)).slice(0, 5)) {
      const verified = parseLinkVerify(txnId, id, await this.command("verify_payment", id), false);
      if (verified.status === "success") return verified;
      if (STATUS_RANK[verified.status] > STATUS_RANK[best.status]) best = verified;
    }
    return best;
  }

  async mandateStatus(mandateRef: string): Promise<"active" | "inactive" | "unknown"> {
    return parseMandateStatus(
      await this.command("check_mandate_status", { authPayuId: mandateRef, requestId: crypto.randomUUID() }),
    );
  }

  /** Cancels a standing instruction. UPI AutoPay mandates use PayU's UPI command. True when PayU accepted it. */
  async revokeMandate(mandateRef: string, method: string | null): Promise<boolean> {
    const command = method?.toUpperCase().includes("UPI") ? "upi_mandate_revoke" : "mandate_revoke";
    return accepted(await this.command(command, { authPayuId: mandateRef, requestId: crypto.randomUUID() }));
  }

  /** Pre-debit notification for the next recurring debit (RN2). */
  async preDebit(mandateRef: string, debitDate: Date, amountPaise: number): Promise<boolean> {
    return accepted(
      await this.command("pre_debit_SI", {
        authPayuId: mandateRef,
        requestId: crypto.randomUUID(),
        debitDate: istDate(debitDate),
        amount: rupees(amountPaise),
      }),
    );
  }

  /** Starts a recurring debit for txnId against the mandate. The result is confirmed later with verify_payment. */
  async debit(mandateRef: string, txnId: string, amountPaise: number, customer: Customer): Promise<boolean> {
    return accepted(
      await this.command("si_transaction", {
        authpayuid: mandateRef,
        invoiceDisplayNumber: txnId,
        amount: Number(rupees(amountPaise)),
        txnid: txnId,
        phone: customer.phone,
        email: customer.email,
        udf1: txnId,
      }),
    );
  }

  /** Full or partial refund of a captured payment. requestId is our unique refund token. */
  async refund(payuRef: string, requestId: string, amountPaise: number): Promise<boolean> {
    return accepted(
      await this.command("cancel_refund_transaction", payuRef, { var2: requestId, var3: rupees(amountPaise) }),
    );
  }

  async refundStatus(requestId: string) {
    return parseRefundStatus(await this.command("check_action_status", requestId));
  }
}
