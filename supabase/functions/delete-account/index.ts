// POST { confirm: "DELETE" } -> { result: "deleted" } (PRD AU9, PR4). Deletes the caller's own account and its
// server-side data; trial hashes and payment records are kept without the user id.
import { DELETE_REFUSALS, isDeleteConfirmed } from "../_shared/account.ts";
import { adminClient, caller, json, readJson } from "../_shared/server.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const admin = adminClient();
  const user = await caller(admin, req);
  if (!user) return json({ error: "unauthorized" }, 401);
  if (!isDeleteConfirmed(await readJson(req))) return json({ error: "bad_request" }, 400);

  const { data, error } = await admin.rpc("delete_account", { p_user: user.id });
  if (error) {
    console.error("delete_account failed", error);
    return json({ error: "server_error" }, 500);
  }
  const code = (data as { error?: string }).error;
  if (code === "not_found") return json({ error: code }, 404);
  if (code && DELETE_REFUSALS.includes(code)) return json({ error: code }, 409);
  if (code) return json({ error: "server_error" }, 500);
  return json(data);
});
