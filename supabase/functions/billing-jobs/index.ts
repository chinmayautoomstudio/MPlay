// Scheduled billing work (payments PRD 6.11), called by pg_cron through pg_net with the `x-billing-job-secret`
// header (the secret lives in Supabase Vault and in BILLING_JOBS_SECRET). POST { action }:
//   webhooks  every 5 minutes: webhooks not processed yet, retried with backoff and flagged after 5 tries (SV7)
//   sweep     every 15 minutes: expired links and pending payments checked with PayU (CK9), pending refunds (RF4)
//   renewals  hourly: unconfirmed mandate cancellations (CN4), pre-debit notices (RN2), due debits (RN1, RN4)
//   expiry    hourly: period ends, grace ends, missed debits (RN5, RN6)
//   notices   daily: billing emails (5.3)
// Every run is recorded in job_runs (JB4). In manual mode (PAYU_BILLING_MODE=manual) no debits or notices are sent.
import { confirmRevoked, payuFromEnv, processWebhook, resolveAll, resolveRefund, rpc } from "../_shared/billing.ts";
import { jobAuthorized, type JobAction, parseJobAction } from "../_shared/jobs.ts";
import { LogNotifier, type Notice } from "../_shared/notifier.ts";
import type { PayuClient } from "../_shared/payu.ts";
import { adminClient, json, readJson } from "../_shared/server.ts";
import type { SupabaseClient } from "jsr:@supabase/supabase-js@2";

type Counts = { processed: number; failed: number; skipped: number };

async function webhooks(admin: SupabaseClient, payu: PayuClient): Promise<Counts> {
  const counts = { processed: 0, failed: 0, skipped: 0 };
  const rows = await rpc<{ id: number; body: Record<string, unknown> }[]>(admin, "claim_webhooks", { p_limit: 50 });
  for (const row of rows) {
    try {
      const outcome = await processWebhook(admin, payu, row.body);
      await rpc(admin, "finish_webhook", { p_id: row.id, p_status: outcome, p_error: null });
      if (outcome === "processed") counts.processed++;
      else counts.skipped++;
    } catch (e) {
      counts.failed++;
      await rpc(admin, "finish_webhook", {
        p_id: row.id,
        p_status: "failed",
        p_error: e instanceof Error ? e.message : String(e),
      }).catch(() => null);
    }
  }
  return counts;
}

async function sweep(admin: SupabaseClient, payu: PayuClient): Promise<Counts> {
  const open = await rpc<{ txnId: string; si: boolean }[]>(admin, "claim_open_payments", { p_limit: 100 });
  const result = await resolveAll(admin, payu, open);
  const refunds = await rpc<{ txnId: string; refundRequestId: string }[]>(admin, "claim_pending_refunds", {
    p_limit: 50,
  });
  for (const refund of refunds) {
    try {
      await resolveRefund(admin, payu, refund.txnId, refund.refundRequestId);
      result.processed++;
    } catch (e) {
      result.failed++;
      console.error(`refund check ${refund.txnId} failed: ${e instanceof Error ? e.message : e}`);
    }
  }
  return { ...result, skipped: 0 };
}

async function renewals(admin: SupabaseClient, payu: PayuClient, manual: boolean): Promise<Counts> {
  const counts = { processed: 0, failed: 0, skipped: 0 };

  const cancels = await rpc<{ subscriptionId: string; mandateRef: string; method: string | null }[]>(
    admin,
    "claim_mandate_cancels",
    { p_limit: 50 },
  );
  for (const c of cancels) {
    if (await confirmRevoked(admin, payu, c.subscriptionId, c.mandateRef, c.method)) counts.processed++;
    else counts.failed++;
  }
  if (manual) return counts;

  type PreDebit = { subscriptionId: string; mandateRef: string; debitDate: string; amountPaise: number };
  for (const notice of await rpc<PreDebit[]>(admin, "claim_pre_debits", { p_limit: 100 })) {
    const sent = await payu.preDebit(notice.mandateRef, new Date(notice.debitDate), notice.amountPaise)
      .catch(() => false);
    await rpc(admin, "mark_pre_debit", { p_sub: notice.subscriptionId, p_sent: sent });
    if (sent) counts.processed++;
    else counts.failed++;
  }

  type Renewal = {
    txnId: string;
    mandateRef: string;
    amountPaise: number;
    customer: { name: string; email: string; phone: string };
  };
  for (const renewal of await rpc<Renewal[]>(admin, "claim_renewals", { p_limit: 50 })) {
    const started = await payu.debit(renewal.mandateRef, renewal.txnId, renewal.amountPaise, renewal.customer)
      .catch(() => false);
    if (started) {
      counts.processed++;
    } else {
      counts.failed++;
      await rpc(admin, "fail_renewal", { p_txn: renewal.txnId, p_reason: "debit_refused" });
    }
  }
  return counts;
}

async function notices(admin: SupabaseClient): Promise<Counts> {
  const notifier = new LogNotifier();
  const counts = { processed: 0, failed: 0, skipped: 0 };
  for (const notice of await rpc<Notice[]>(admin, "claim_notices", { p_limit: 200 })) {
    if (await notifier.send(notice).catch(() => false)) counts.processed++;
    else counts.failed++;
  }
  return counts;
}

async function run(action: JobAction, admin: SupabaseClient): Promise<Counts> {
  if (action === "expiry") {
    const r = await rpc<{ expired: number; pastDue: number; failed: number }>(admin, "expire_subscriptions", {});
    return { processed: r.expired + r.pastDue + r.failed, failed: 0, skipped: 0 };
  }
  if (action === "notices") return await notices(admin);
  const env = payuFromEnv();
  if (!env) throw new Error("PayU secrets are not set");
  if (action === "webhooks") return await webhooks(admin, env.payu);
  if (action === "sweep") return await sweep(admin, env.payu);
  return await renewals(admin, env.payu, env.config.mode === "manual");
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  if (!jobAuthorized(req.headers.get("x-billing-job-secret"), Deno.env.get("BILLING_JOBS_SECRET"))) {
    return json({ error: "unauthorized" }, 401);
  }
  const action = parseJobAction(await readJson(req));
  if (!action) return json({ error: "bad_request" }, 400);

  const admin = adminClient();
  const started = new Date().toISOString();
  try {
    const counts = await run(action, admin);
    await rpc(admin, "record_job_run", {
      p_job: action,
      p_started: started,
      p_processed: counts.processed,
      p_failed: counts.failed,
      p_skipped: counts.skipped,
      p_error: null,
    });
    return json({ action, ...counts });
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    console.error(`billing job ${action} failed: ${message}`);
    await rpc(admin, "record_job_run", {
      p_job: action,
      p_started: started,
      p_processed: 0,
      p_failed: 1,
      p_skipped: 0,
      p_error: message,
    }).catch(() => null);
    return json({ error: "job_failed" }, 500);
  }
});
