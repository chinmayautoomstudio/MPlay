// POST { jobRef, outcome: "completed" | "released" } -> { usage } (PRD US3). A completed job counts as one use
// in the week it finished; a cancelled or failed one gives its reservation back.
import { adminClient, caller, finishSeparation, json, readJson } from "../_shared/server.ts";
import { parseFinishBody } from "../_shared/usage.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const admin = adminClient();
  const user = await caller(admin, req);
  if (!user) return json({ error: "unauthorized" }, 401);

  const body = parseFinishBody(await readJson(req));
  if (!body) return json({ error: "bad_request" }, 400);

  try {
    return json(await finishSeparation(admin, user.id, body.jobRef, body.outcome));
  } catch (e) {
    console.error("finish_separation failed", e);
    return json({ error: "server_error" }, 500);
  }
});
