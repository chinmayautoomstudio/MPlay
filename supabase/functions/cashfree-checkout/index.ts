// Hosted launcher for International checkout. The app opens this URL in a Custom Tab; the page starts Cashfree's
// JS Checkout with the payment_session_id from Create Order. No JWT: the session id is the credential.
import { validTxnId } from "../_shared/checkout.ts";
import { cashfreeConfig, checkoutPageHtml, CHECKOUT_PAGE_CSP, validSessionId } from "../_shared/cashfree.ts";

Deno.serve((req) => {
  if (req.method !== "GET") return new Response("Method not allowed", { status: 405 });
  const url = new URL(req.url);
  const session = validSessionId(url.searchParams.get("session"));
  const txn = validTxnId(url.searchParams.get("txn"));
  if (!session || !txn) return new Response("Bad request", { status: 400 });
  const config = cashfreeConfig();
  if (!config) return new Response("Not configured", { status: 503 });
  return new Response(checkoutPageHtml({ session, mode: config.env }), {
    status: 200,
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store",
      "Content-Security-Policy": CHECKOUT_PAGE_CSP,
      "Referrer-Policy": "no-referrer",
      "X-Content-Type-Options": "nosniff",
    },
  });
});
