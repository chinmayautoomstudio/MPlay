// PayU server notifications (payments PRD SV2, SV7, SC5). Public by necessity and harmless: the event is saved to
// webhook_log first (an error makes PayU retry), then handled by re-checking with PayU's APIs. A payment callback's
// reverse hash is checked as a first filter; a forged event at worst causes a status check. Always answers quickly;
// failed processing is retried by the billing-jobs `webhooks` action.
import { payuFromEnv, processWebhook, rpc } from "../_shared/billing.ts";
import { reverseHash, sameHash } from "../_shared/payu.ts";
import { adminClient, json } from "../_shared/server.ts";
import { classifyWebhook, parseWebhookBody, redact } from "../_shared/webhook.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const text = await req.text().catch(() => "");
  if (text.length > 64_000) return json({ error: "too_large" }, 413);
  const body = parseWebhookBody(req.headers.get("content-type"), text);
  if (!body) return json({ error: "bad_request" }, 400);

  const env = payuFromEnv();
  const event = classifyWebhook(body);
  let suspicious = false;
  if (env && typeof body.hash === "string" && typeof body.txnid === "string") {
    const expected = await reverseHash(body as Record<string, string>, env.config.key, env.config.salt);
    suspicious = !sameHash(expected, body.hash);
  }

  const admin = adminClient();
  let logged: { id: number; duplicate: boolean };
  try {
    logged = await rpc(admin, "log_webhook", {
      p_key: event.eventKey,
      p_body: { ...redact(body), _hashMismatch: suspicious || undefined },
    });
  } catch (e) {
    console.error("webhook not saved", e instanceof Error ? e.message : e);
    return json({ error: "server_error" }, 500);
  }
  if (logged.duplicate || suspicious || !env) {
    if (suspicious) {
      await rpc(admin, "finish_webhook", { p_id: logged.id, p_status: "ignored", p_error: "hash_mismatch" })
        .catch(() => null);
    }
    return json({ result: "received" });
  }

  try {
    const outcome = await processWebhook(admin, env.payu, body);
    await rpc(admin, "finish_webhook", { p_id: logged.id, p_status: outcome, p_error: null });
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    console.error(`webhook ${logged.id} processing failed: ${message}`);
    await rpc(admin, "finish_webhook", { p_id: logged.id, p_status: "failed", p_error: message }).catch(() => null);
  }
  return json({ result: "received" });
});
