// POST { deviceId } -> the caller's plan, trial, subscription, PayU billing state and separator usage (PRD PL2, US6),
// plus billingMode ("autopay" or "manual") and paymentsEnabled (PayU secrets are set). Claims the 30-day trial the first
// time a user without one asks (TR2), so a new account is on Trial right after its first sign-in.
import { billingMode, payuConfig } from "../_shared/payu.ts";
import { adminClient, caller, claimTrial, entitlements, json, readDeviceId, usageSummary } from "../_shared/server.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const admin = adminClient();
  const user = await caller(admin, req);
  if (!user) return json({ error: "unauthorized" }, 401);

  try {
    let current = await entitlements(admin, user.id);
    if (!current) return json({ error: "no_profile" }, 404);
    if (current.disabled) return json({ error: "account_disabled" }, 403);

    let trialClaim = null;
    if (current.trial === null) {
      trialClaim = await claimTrial(admin, user, await readDeviceId(req));
      if (trialClaim.result === "granted") current = await entitlements(admin, user.id);
    }
    return json({
      ...current,
      trialClaim,
      usage: await usageSummary(admin, user.id),
      billingMode: billingMode(Deno.env.get("PAYU_BILLING_MODE")),
      paymentsEnabled: payuConfig() !== null,
    });
  } catch (e) {
    console.error("entitlements failed", e);
    return json({ error: "server_error" }, 500);
  }
});
