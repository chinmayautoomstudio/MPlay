// POST { action, ... } -> the result of the matching admin_* SQL function (PRD AD1-AD10). The caller's id comes
// from the verified token and the SQL functions refuse anyone who isn't an enabled Admin, so the app's own
// isAdmin flag only decides whether the Admin screens are shown.
import { adminClient, caller, json, readJson } from "../_shared/server.ts";
import { parseAdminRequest, REFUSALS } from "../_shared/admin.ts";

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
  return json(data);
});
