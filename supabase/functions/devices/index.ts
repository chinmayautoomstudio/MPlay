// POST { action, deviceId, deviceName?, replaceDeviceId? } -> register, verify, list or remove this
// account's phones. At most two stay signed in; a third must replace one of the two.
import { parseDeviceRequest, sessionIdFromRequest } from "../_shared/devices.ts";
import { adminClient, caller, json, readJson } from "../_shared/server.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const admin = adminClient();
  const user = await caller(admin, req);
  if (!user) return json({ error: "unauthorized" }, 401);

  const call = parseDeviceRequest(await readJson(req));
  if (!call) return json({ error: "bad_request" }, 400);

  const sessionId = sessionIdFromRequest(req);

  try {
    if (call.action === "list") {
      const { data, error } = await admin.rpc("device_list", {
        p_user: user.id,
        p_current_device: call.deviceId,
      });
      if (error) throw error;
      return json({ devices: data });
    }

    if (call.action === "register") {
      const { data, error } = await admin.rpc("register_device", {
        p_user: user.id,
        p_device_id: call.deviceId,
        p_device_name: call.deviceName,
        p_session_id: sessionId,
        p_replace_device_id: call.replaceDeviceId,
      });
      if (error) throw error;
      const result = data as { result?: string; error?: string; devices?: unknown; maxDevices?: number };
      if (result.error === "account_disabled") return json({ error: "account_disabled" }, 403);
      if (result.error === "not_found") return json({ error: "no_profile" }, 404);
      if (result.result === "limit_reached") {
        return json({ error: "device_limit_reached", maxDevices: 2, devices: result.devices }, 409);
      }
      return json(result);
    }

    if (call.action === "verify") {
      const { data, error } = await admin.rpc("verify_device", {
        p_user: user.id,
        p_device_id: call.deviceId,
        p_device_name: call.deviceName,
        p_session_id: sessionId,
      });
      if (error) throw error;
      const result = data as { status?: string; devices?: unknown };
      if (result.status === "disabled") return json({ error: "account_disabled" }, 403);
      if (result.status === "revoked") return json({ error: "device_revoked" }, 401);
      return json({ result: "ok", devices: result.devices });
    }

    const { data, error } = await admin.rpc("remove_device", {
      p_user: user.id,
      p_device_id: call.deviceId,
    });
    if (error) throw error;
    return json(data);
  } catch (e) {
    console.error("devices failed", e);
    return json({ error: "server_error" }, 500);
  }
});
