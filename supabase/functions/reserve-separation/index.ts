// POST { jobs: [{ jobRef, songRef }] } -> { granted, denied, usage } (PRD US2, US5, US7). Reserves one AI Vocal
// Separator use per job before the app queues it. Free users get as many as remain this week; the rest are
// denied and recorded.
import { adminClient, caller, json, readJson, reserveSeparation } from "../_shared/server.ts";
import { parseReserveBody } from "../_shared/usage.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const admin = adminClient();
  const user = await caller(admin, req);
  if (!user) return json({ error: "unauthorized" }, 401);

  const jobs = parseReserveBody(await readJson(req));
  if (!jobs) return json({ error: "bad_request" }, 400);

  try {
    const result = await reserveSeparation(admin, user.id, jobs);
    if (result.error) return json({ error: "account_disabled" }, 403);
    return json(result);
  } catch (e) {
    console.error("reserve_separation failed", e);
    return json({ error: "server_error" }, 500);
  }
});
