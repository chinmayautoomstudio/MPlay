// Cashfree Orders + hosted Checkout for International payments. Sandbox on this merchant is INR-only; live can use USD.
// Credentials are Edge Function secrets (CASHFREE_ENV, CASHFREE_CLIENT_ID, CASHFREE_CLIENT_SECRET).
import type { BillingCurrency } from "./geo.ts";
import { paise, rupees, type Customer, type VerifiedPayment } from "./payu.ts";

export type CashfreeConfig = {
  env: "sandbox" | "live";
  clientId: string;
  clientSecret: string;
  returnUrl: string;
  notifyUrl: string;
  checkoutUrl: string;
};

export function cashfreeConfig(env: Record<string, string | undefined> = Deno.env.toObject()): CashfreeConfig | null {
  if (!env.CASHFREE_CLIENT_ID || !env.CASHFREE_CLIENT_SECRET) return null;
  const live = env.CASHFREE_ENV === "live";
  const host = "https://securedbmp3.autoomstudio.com/functions/v1";
  return {
    env: live ? "live" : "sandbox",
    clientId: env.CASHFREE_CLIENT_ID,
    clientSecret: env.CASHFREE_CLIENT_SECRET,
    returnUrl: env.CASHFREE_RETURN_URL?.trim() || `${host}/cashfree-return`,
    notifyUrl: env.CASHFREE_NOTIFY_URL?.trim() || `${host}/cashfree-webhook`,
    checkoutUrl: env.CASHFREE_CHECKOUT_URL?.trim() || `${host}/cashfree-checkout`,
  };
}

export function cashfreeHost(env: CashfreeConfig["env"]): string {
  return env === "live" ? "https://api.cashfree.com/pg" : "https://sandbox.cashfree.com/pg";
}

/** Cashfree hosts, or our cashfree-checkout launcher (payment-status must not send those to PayU). */
export function isCashfreeUrl(url: string | null | undefined): boolean {
  if (!url) return false;
  try {
    const parsed = new URL(url);
    if (parsed.hostname.endsWith("cashfree.com") || parsed.hostname.endsWith("cashfree.dev")) return true;
    return parsed.pathname.includes("/cashfree-checkout");
  } catch {
    return false;
  }
}

export type CashfreeOrderRequest = {
  txnId: string;
  amountPaise: number;
  currency: BillingCurrency;
  customer: Customer;
  customerId: string;
  purpose: string;
};

export function cashfreeCustomerId(userId: string): string {
  const compact = userId.replace(/-/g, "");
  return compact.slice(0, 50) || "user";
}

export function buildCashfreeOrderPayload(req: CashfreeOrderRequest, config: CashfreeConfig): Record<string, unknown> {
  const sep = config.returnUrl.includes("?") ? "&" : "?";
  return {
    order_id: req.txnId,
    order_amount: Number(rupees(req.amountPaise)),
    order_currency: req.currency,
    order_note: req.purpose,
    customer_details: {
      customer_id: cashfreeCustomerId(req.customerId),
      customer_name: req.customer.name,
      customer_email: req.customer.email,
      customer_phone: cashfreePhone(req.customer.phone),
    },
    order_meta: {
      return_url: `${config.returnUrl}${sep}txn={order_id}`,
      notify_url: config.notifyUrl,
    },
  };
}

export function checkoutPageUrl(config: CashfreeConfig, session: string, txnId: string): string {
  const url = new URL(config.checkoutUrl);
  url.searchParams.set("session", session);
  url.searchParams.set("txn", txnId);
  return url.toString();
}

/** Indian 10-digit without country code; other numbers keep E.164 with '+'. */
export function cashfreePhone(phone: string): string {
  const compact = phone.replace(/[\s\-()]/g, "");
  if (/^\+91\d{10}$/.test(compact)) return compact.slice(3);
  if (/^91\d{10}$/.test(compact)) return compact.slice(2);
  return compact;
}

const TXN = /^MP[A-Z0-9]{20}$/;
const SESSION = /^session_[A-Za-z0-9_-]{10,280}$/;

export function validSessionId(value: string | null): string | null {
  return value && SESSION.test(value) ? value : null;
}

export function findTxnId(value: unknown): string | null {
  if (typeof value === "string") return TXN.test(value) ? value : null;
  if (Array.isArray(value)) {
    for (const item of value) {
      const found = findTxnId(item);
      if (found) return found;
    }
    return null;
  }
  if (value && typeof value === "object") {
    for (const item of Object.values(value as Record<string, unknown>)) {
      const found = findTxnId(item);
      if (found) return found;
    }
  }
  return null;
}

export function parseCashfreeOrder(body: unknown): {
  orderId: string;
  session?: string;
  ref: string;
  status: string;
  amountPaise: number | null;
  currency?: string;
} | null {
  const b = (body ?? {}) as Record<string, unknown>;
  if (typeof b.order_id !== "string") return null;
  return {
    orderId: b.order_id,
    session: typeof b.payment_session_id === "string" ? b.payment_session_id : undefined,
    ref: b.cf_order_id == null ? "" : String(b.cf_order_id),
    status: String(b.order_status ?? "").toUpperCase(),
    amountPaise: paise(b.order_amount),
    currency: typeof b.order_currency === "string" ? b.order_currency.toUpperCase() : undefined,
  };
}

export function orderToVerified(txnId: string, order: { status: string; amountPaise: number | null; currency?: string; ref: string }): VerifiedPayment {
  const paid = order.status === "PAID";
  const failed = order.status === "EXPIRED" || order.status === "TERMINATED";
  return {
    status: paid ? "success" : failed ? "failure" : "pending",
    txnId,
    payuRef: order.ref || undefined,
    amountPaise: order.amountPaise ?? undefined,
    currency: order.currency,
    reason: failed ? order.status.toLowerCase() : undefined,
  };
}

export async function cashfreeSignature(
  timestamp: string,
  rawBody: string,
  secret: string,
): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(`${timestamp}${rawBody}`));
  const bytes = new Uint8Array(sig);
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary);
}

export function sameSignature(expected: string, given: string): boolean {
  if (expected.length !== given.length) return false;
  let diff = 0;
  for (let i = 0; i < expected.length; i++) diff |= expected.charCodeAt(i) ^ given.charCodeAt(i);
  return diff === 0;
}

function escapeJs(value: string): string {
  return value.replace(/[\\'"]/g, (c) => `\\${c}`).replace(/</g, "\\x3c");
}

export const CHECKOUT_PAGE_CSP =
  "default-src 'none'; script-src 'unsafe-inline' https://sdk.cashfree.com; " +
  "connect-src https://sandbox.cashfree.com https://api.cashfree.com https://sdk.cashfree.com https://*.cashfree.com; " +
  "style-src 'unsafe-inline'; frame-src https://sandbox.cashfree.com https://payments.cashfree.com https://*.cashfree.com; " +
  "img-src data: https:; base-uri 'none'; form-action https:; frame-ancestors 'none'";

/** Launcher the Android Custom Tab opens; it starts Cashfree's JS Checkout. */
export function checkoutPageHtml(opts: { session: string; mode: "sandbox" | "live" }): string {
  const session = escapeJs(opts.session);
  const mode = opts.mode === "live" ? "production" : "sandbox";
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>MP3 Studio payment</title>
<script src="https://sdk.cashfree.com/js/v3/cashfree.js"></script>
<style>
body { font-family: system-ui, sans-serif; margin: 0; min-height: 100vh; display: flex; align-items: center;
       justify-content: center; background: #121212; color: #f2f2f2; text-align: center; }
p { color: #bdbdbd; }
</style>
</head>
<body>
<p>Opening checkout…</p>
<script>
(function () {
  var cashfree = Cashfree({ mode: "${mode}" });
  cashfree.checkout({ paymentSessionId: "${session}", redirectTarget: "_self" });
})();
</script>
</body>
</html>
`;
}

export class CashfreeClient {
  constructor(private readonly config: CashfreeConfig, private readonly http: typeof fetch = fetch) {}

  private headers(): HeadersInit {
    return {
      "Content-Type": "application/json",
      "x-api-version": "2025-01-01",
      "x-client-id": this.config.clientId,
      "x-client-secret": this.config.clientSecret,
    };
  }

  async createOrder(req: CashfreeOrderRequest): Promise<{ url: string; ref: string }> {
    const response = await this.http(`${cashfreeHost(this.config.env)}/orders`, {
      method: "POST",
      headers: this.headers(),
      body: JSON.stringify(buildCashfreeOrderPayload(req, this.config)),
    });
    const body = await response.json().catch(() => null);
    const order = response.ok ? parseCashfreeOrder(body) : null;
    if (!order?.session) {
      const reason = body && typeof body === "object" && "message" in body
        ? String((body as { message: unknown }).message)
        : `${response.status}`;
      throw new Error(`Cashfree order creation failed (${reason})`);
    }
    return { url: checkoutPageUrl(this.config, order.session, req.txnId), ref: order.ref };
  }

  async checkOrder(txnId: string): Promise<VerifiedPayment> {
    const response = await this.http(`${cashfreeHost(this.config.env)}/orders/${encodeURIComponent(txnId)}`, {
      headers: this.headers(),
    });
    if (response.status === 404) return { status: "not_found", txnId };
    const body = await response.json().catch(() => null);
    const order = parseCashfreeOrder(body);
    if (!response.ok || !order) return { status: "pending", txnId };
    return orderToVerified(txnId, order);
  }
}
