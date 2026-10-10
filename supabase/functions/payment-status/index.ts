// POST { txnId? } -> the caller's payment after checking it with PayU (payments PRD CK5-CK8, SV1). The app calls this
// when it comes back from checkout (redirect or not); the answer comes from PayU's status API, never from the app.
// Returns payment_summary(): { txnId, status, kind, failureReason, resolveUntil, amountPaise, completedAt, plan }.
import { payuFromEnv, resolveAll, rpc } from "../_shared/billing.ts";
import { parseStatusRequest } from "../_shared/checkout.ts";
import { adminClient, caller, json, readJson } from "../_shared/server.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const admin = adminClient();
  const user = await caller(admin, req);
  if (!user) return json({ error: "unauthorized" }, 401);

  const request = parseStatusRequest(await readJson(req));
  if (!request) return json({ error: "bad_request" }, 400);

  try {
    const env = payuFromEnv();
    if (env) {
      const open = await rpc<{ txnId: string; si: boolean }[]>(admin, "payments_to_check", {
        p_user: user.id,
        p_txn: request.txnId,
        p_min_seconds: 5,
      });
      await resolveAll(admin, env.payu, open);
    }
    const summary = await rpc<{ error?: string }>(admin, "payment_summary", { p_user: user.id, p_txn: request.txnId });
    if (summary.error) return json(summary, 404);
    return json(summary);
  } catch (e) {
    console.error("payment-status failed", e instanceof Error ? e.message : e);
    return json({ error: "server_error" }, 500);
  }
});
