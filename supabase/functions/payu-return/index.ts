// PayU's success and failure URLs (payments PRD section 4). PayU may POST the result form to them, which a static
// page can't take, so this answers with a 303 redirect to the App Link page (PAYU_RETURN_PAGE) carrying only our
// transaction ID and which URL was hit. It reads nothing else and changes nothing (SC7): the app then asks
// payment-status, which checks with PayU.
import { validTxnId } from "../_shared/checkout.ts";

Deno.serve(async (req) => {
  if (req.method !== "GET" && req.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const page = Deno.env.get("PAYU_RETURN_PAGE");
  if (!page) return new Response("Not configured", { status: 503 });

  const url = new URL(req.url);
  let txn = validTxnId(url.searchParams.get("txn"));
  if (!txn && req.method === "POST") {
    const form = new URLSearchParams(await req.text().catch(() => ""));
    txn = validTxnId(form.get("txnid")) ?? validTxnId(form.get("udf1"));
  }
  const target = new URL(page);
  target.searchParams.set("result", url.searchParams.get("result") === "success" ? "success" : "failure");
  if (txn) target.searchParams.set("txn", txn);
  return new Response(null, { status: 303, headers: { Location: target.toString(), "Cache-Control": "no-store" } });
});
