// POST { phone?, interval? } -> { url, txnId } (payments PRD CK2, CK3, CK13). Creates the PayU payment link for one
// Pro payment attempt; interval is "month" (default) or "year". The amount is fixed on the server and the app only
// gets the link URL and our transaction ID.
// 409 {"error": phone_required | payment_in_progress | already_subscribed | switch_not_yet | mandate_update_pending},
// 400 invalid_phone | invalid_interval, 403 account_disabled, 429 rate_limited, 503 payu_unavailable.
import { payuFromEnv, startCheckout } from "../_shared/billing.ts";
import { CHECKOUT_REFUSALS, parseCheckoutRequest } from "../_shared/checkout.ts";
import { adminClient, caller, json, readJson } from "../_shared/server.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const admin = adminClient();
  const user = await caller(admin, req);
  if (!user) return json({ error: "unauthorized" }, 401);

  const request = parseCheckoutRequest(await readJson(req));
  if ("error" in request) return json({ error: request.error }, 400);
  const env = payuFromEnv();
  if (!env) return json({ error: "payu_unavailable" }, 503);

  try {
    const outcome = await startCheckout(admin, env.payu, env.config, user.id, request.phone, request.interval);
    if (outcome.ok) return json({ url: outcome.url, txnId: outcome.txnId, mode: env.config.mode });
    const status = CHECKOUT_REFUSALS[outcome.error] ?? 503;
    return json({ error: outcome.error, txnId: outcome.txnId }, status);
  } catch (e) {
    console.error("start-checkout failed", e instanceof Error ? e.message : e);
    return json({ error: "server_error" }, 500);
  }
});
