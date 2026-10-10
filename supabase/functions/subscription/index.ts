// POST { action: "cancel" } -> { result: "cancelled", expiresAt } or { result: "cancel_pending" } (payments PRD
// CN1-CN4). Cancels the caller's PayU standing instruction; Pro stays until the paid period ends. When PayU doesn't
// confirm, renewals are already stopped and the billing-jobs function retries the cancellation (the app shows
// "Cancellation in progress"). 409 {"error": "not_subscribed"}.
import { confirmRevoked, payuFromEnv, rpc } from "../_shared/billing.ts";
import { adminClient, caller, json, readJson } from "../_shared/server.ts";

type BeginCancel = {
  result?: string;
  error?: string;
  expiresAt?: string;
  subscriptionId?: string;
  mandateRef?: string;
  method?: string | null;
};

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const admin = adminClient();
  const user = await caller(admin, req);
  if (!user) return json({ error: "unauthorized" }, 401);

  const body = (await readJson(req) ?? {}) as Record<string, unknown>;
  if (body.action !== "cancel") return json({ error: "bad_request" }, 400);

  try {
    const begun = await rpc<BeginCancel>(admin, "begin_cancel", { p_user: user.id });
    if (begun.error) return json({ error: begun.error }, 409);
    if (begun.result === "cancelled") return json({ result: "cancelled", expiresAt: begun.expiresAt });

    const env = payuFromEnv();
    const confirmed = env && begun.subscriptionId && begun.mandateRef
      ? await confirmRevoked(admin, env.payu, begun.subscriptionId, begun.mandateRef, begun.method ?? null)
      : false;
    if (!confirmed) return json({ result: "cancel_pending" });
    const ent = await rpc<{ billing?: { expiresAt?: string } }>(admin, "compute_entitlements", { p_user: user.id });
    return json({ result: "cancelled", expiresAt: ent?.billing?.expiresAt ?? null });
  } catch (e) {
    console.error("subscription cancel failed", e instanceof Error ? e.message : e);
    return json({ error: "server_error" }, 500);
  }
});