// PayU's success and failure URLs (payments PRD section 4). PayU may POST the result form to them, so this answers
// with the return page itself (_shared/return_page.ts), which opens MP3 Studio through an intent link to the app's
// return link (PAYU_RETURN_PAGE). It reads only our transaction ID and which URL was hit, and changes nothing (SC7):
// the app then asks payment-status, which checks with PayU.
import { validTxnId } from "../_shared/checkout.ts";
import { RETURN_PAGE_CSP, returnPageHtml } from "../_shared/return_page.ts";

Deno.serve(async (req) => {
  if (req.method !== "GET" && req.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const appLink = Deno.env.get("PAYU_RETURN_PAGE");
  if (!appLink) return new Response("Not configured", { status: 503 });

  const url = new URL(req.url);
  let txn = validTxnId(url.searchParams.get("txn"));
  if (!txn && req.method === "POST") {
    // Payment-link postbacks carry PayU's own txnid; ours is in udf1.
    const form = new URLSearchParams(await req.text().catch(() => ""));
    txn = validTxnId(form.get("udf1")) ?? validTxnId(form.get("txnid"));
  }
  const html = returnPageHtml({ success: url.searchParams.get("result") === "success", txnId: txn, appLink });
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
