// Cashfree server notifications for International Payment Links. Public by necessity: the event is saved to
// webhook_log first (an error makes Cashfree retry), then handled by re-checking the link with Cashfree's API.
// The HMAC (x-webhook-signature over timestamp + raw body, keyed with CASHFREE_CLIENT_SECRET) is checked and a
// mismatch is flagged, but the event is still handled because a forged event at worst causes a status check.
import { cashfreeFromEnv, processCashfreeWebhook, rpc } from "../_shared/billing.ts";
import { cashfreeConfig, cashfreeSignature, findTxnId, sameSignature } from "../_shared/cashfree.ts";
import { adminClient, json } from "../_shared/server.ts";

Deno.serve(async (req) => {
  if (req.method === "GET") return json({ result: "ok" });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const text = await req.text().catch(() => "");
  if (text.length > 64_000) return json({ error: "too_large" }, 413);
  let body: Record<string, unknown>;
  try {
    body = text ? JSON.parse(text) as Record<string, unknown> : {};
  } catch {
    return json({ error: "bad_request" }, 400);
  }

  const config = cashfreeConfig();
  const timestamp = req.headers.get("x-webhook-timestamp") ?? "";
  const given = req.headers.get("x-webhook-signature") ?? "";
  let suspicious = false;
  if (config && timestamp && given) {
    const expected = await cashfreeSignature(timestamp, text, config.clientSecret);
    suspicious = !sameSignature(expected, given);
  } else if (config) {
    suspicious = true;
  }

  const type = typeof body.type === "string" ? body.type : "cashfree";
  const txnId = findTxnId(body) ?? "unknown";
  const admin = adminClient();
  let logged: { id: number; duplicate: boolean };
  try {
    logged = await rpc(admin, "log_webhook", {
      p_key: `cf:${type}:${txnId}`,
      p_body: { ...body, _gateway: "cashfree", _sigMismatch: suspicious || undefined },
    });
  } catch (e) {
    console.error("cashfree webhook not saved", e instanceof Error ? e.message : e);
    return json({ error: "server_error" }, 500);
  }

  const cashfree = cashfreeFromEnv();
  if (logged.duplicate || !cashfree) return json({ result: "received" });
  if (suspicious) console.warn(`cashfree webhook ${logged.id} signature mismatch; checking with Cashfree anyway`);

  try {
    const outcome = await processCashfreeWebhook(admin, cashfree, body);
    await rpc(admin, "finish_webhook", { p_id: logged.id, p_status: outcome, p_error: null });
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    console.error(`cashfree webhook ${logged.id} processing failed: ${message}`);
    await rpc(admin, "finish_webhook", { p_id: logged.id, p_status: "failed", p_error: message }).catch(() => null);
  }
  return json({ result: "received" });
});
