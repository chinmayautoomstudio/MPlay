// POST { action, ... } -> the result of the matching admin_* SQL function (PRD AD1-AD10, payments AD1-AD4, RF2).
// The caller's id comes from the verified token and the SQL functions refuse anyone who isn't an enabled Admin, so
// the app's own isAdmin flag only decides whether the Admin screens are shown. Billing actions are checked and
// audit-logged in SQL first, then call PayU: reverify re-checks a payment or refund, refundPayment asks PayU for a
// full refund (state changes only when PayU confirms it), cancelSubscription cancels the user's mandate.
import { confirmRevoked, payuFromEnv, resolvePayment, resolveRefund, rpc } from "../_shared/billing.ts";
import { type AdminCall, parseAdminRequest, REFUSALS } from "../_shared/admin.ts";
import { adminClient, caller, json, readJson } from "../_shared/server.ts";
import type { SupabaseClient } from "jsr:@supabase/supabase-js@2";

type ActionInfo = {
  txnId?: string;
  si?: boolean;
  status?: string;
  payuRef?: string;
  refundablePaise?: number;
  refundRequestId?: string | null;
  userId?: string;
};

async function payuStep(admin: SupabaseClient, call: AdminCall, info: ActionInfo): Promise<Response> {
  const env = payuFromEnv();
  if (!env) return json({ error: "payu_unavailable" }, 503);

  if (call.payu === "reverify") {
    if (info.refundRequestId) {
      return json({ result: "refund_checked", refund: await resolveRefund(admin, env.payu, info.txnId!, info.refundRequestId) });
    }
    if (info.status === "success" || info.status === "refunded") {
      return json({ result: "unchanged", status: info.status });
    }
    return json(await resolvePayment(admin, env.payu, info.txnId!, info.si ?? false));
  }

  if (call.payu === "refund") {
    const requestId = `RF${crypto.randomUUID().replaceAll("-", "").slice(0, 20)}`;
    const accepted = await env.payu.refund(info.payuRef!, requestId, info.refundablePaise!).catch(() => false);
    if (!accepted) return json({ error: "payu_refused" }, 502);
    await rpc(admin, "record_refund_request", { p_txn: info.txnId, p_request_id: requestId });
    return json({ result: "refund_requested" });
  }

  const begun = await rpc<{ result?: string; error?: string; subscriptionId?: string; mandateRef?: string; method?: string }>(
    admin,
    "begin_cancel",
    { p_user: info.userId },
  );
  if (begun.error) return json({ error: begun.error }, 409);
  if (begun.result === "cancelled") return json({ result: "cancelled" });
  const ok = await confirmRevoked(admin, env.payu, begun.subscriptionId!, begun.mandateRef!, begun.method ?? null);
  return json({ result: ok ? "cancelled" : "cancel_pending" });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const admin = adminClient();
  const user = await caller(admin, req);
  if (!user) return json({ error: "unauthorized" }, 401);

  const call = parseAdminRequest(await readJson(req));
  if (!call) return json({ error: "bad_request" }, 400);

  const { data, error } = await admin.rpc(call.fn, { p_actor: user.id, ...call.args });
  if (error) {
    if (error.code === "42501") return json({ error: "forbidden" }, 403);
    if (error.code === "22023") return json({ error: "bad_request" }, 400);
    console.error(`${call.fn} failed`, error);
    return json({ error: "server_error" }, 500);
  }
  const refusal = (data as { error?: string } | null)?.error;
  if (refusal === "not_found") return json({ error: refusal }, 404);
  if (refusal && REFUSALS.includes(refusal)) return json({ error: refusal }, 409);
  if (!call.payu) return json(data);

  try {
    return await payuStep(admin, call, data as ActionInfo);
  } catch (e) {
    console.error(`admin ${call.payu} failed`, e instanceof Error ? e.message : e);
    return json({ error: "server_error" }, 500);
  }
});
