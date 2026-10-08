// POST { deviceId } -> { result: "granted" | "existing" | "denied" | "unavailable", reason? } (PRD TR2-TR5).
// The app normally gets its trial through `entitlements`; this is the same step on its own.
import { adminClient, caller, claimTrial, json, readDeviceId } from "../_shared/server.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const admin = adminClient();
  const user = await caller(admin, req);
  if (!user) return json({ error: "unauthorized" }, 401);

  const result = await claimTrial(admin, user, await readDeviceId(req));
  return json(result, result.result === "unavailable" ? 503 : 200);
});
