// Cashfree order return_url. Serves the same return page as payu-return so the browser opens MP3 Studio
// through an intent link (PAYU_RETURN_PAGE). Reads only our transaction ID and a display result; changes nothing.
// The app then asks payment-status, which checks the order with Cashfree.
import { validTxnId } from "../_shared/checkout.ts";
import { RETURN_PAGE_CSP, returnPageHtml } from "../_shared/return_page.ts";

Deno.serve(async (req) => {
  if (req.method !== "GET" && req.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const appLink = Deno.env.get("PAYU_RETURN_PAGE");
  if (!appLink) return new Response("Not configured", { status: 503 });

  const url = new URL(req.url);
  let txn = validTxnId(url.searchParams.get("txn")) ??
    validTxnId(url.searchParams.get("order_id")) ??
    validTxnId(url.searchParams.get("link_id"));
  if (!txn && req.method === "POST") {
    const text = await req.text().catch(() => "");
    const form = new URLSearchParams(text);
    txn = validTxnId(form.get("txn")) ?? validTxnId(form.get("link_id"));
    if (!txn) {
      try {
        const body = JSON.parse(text) as Record<string, unknown>;
        txn = validTxnId(body.txn) ?? validTxnId(body.link_id);
      } catch { /* display-only page */ }
    }
  }
  const status = (url.searchParams.get("link_status") ?? url.searchParams.get("status") ?? "").toUpperCase();
  const success = status === "PAID" || url.searchParams.get("result") === "success";
  const html = returnPageHtml({ success, txnId: txn, appLink });
  return new Response(html, {
    status: 200,
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store",
      "Content-Security-Policy": RETURN_PAGE_CSP,
      "Referrer-Policy": "no-referrer",
      "X-Content-Type-Options": "nosniff",
    },
  });
});
