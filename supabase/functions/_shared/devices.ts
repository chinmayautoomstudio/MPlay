import { validDeviceId } from "./trial.ts";

export const MAX_DEVICE_NAME = 80;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export type DeviceAction = "register" | "verify" | "list" | "remove";

export type DeviceCall = {
  action: DeviceAction;
  deviceId: string | null;
  deviceName: string;
  replaceDeviceId: string | null;
};

function deviceName(value: unknown): string {
  const raw = typeof value === "string" ? value.trim() : "";
  return raw.slice(0, MAX_DEVICE_NAME) || "Android device";
}

/** `{ action, deviceId, deviceName?, replaceDeviceId? }`; null when the action or an id is malformed. */
export function parseDeviceRequest(body: unknown): DeviceCall | null {
  const b = (body ?? {}) as Record<string, unknown>;
  const action = b.action;
  if (action !== "register" && action !== "verify" && action !== "list" && action !== "remove") {
    return null;
  }
  const deviceId = validDeviceId(b.deviceId);
  if (b.replaceDeviceId != null) {
    const replaceDeviceId = validDeviceId(b.replaceDeviceId);
    if (!replaceDeviceId) return null;
    if (!deviceId) return null;
    return { action, deviceId, deviceName: deviceName(b.deviceName), replaceDeviceId };
  }
  if ((action === "register" || action === "verify" || action === "remove") && !deviceId) return null;
  return { action, deviceId, deviceName: deviceName(b.deviceName), replaceDeviceId: null };
}

/** GoTrue puts the session UUID in the access-token `session_id` claim. */
export function sessionIdFromRequest(req: Request): string | null {
  const token = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
  if (!token) return null;
  const part = token.split(".")[1];
  if (!part) return null;
  const padded = part.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (part.length % 4)) % 4);
  try {
    const payload = JSON.parse(atob(padded)) as { session_id?: unknown };
    const id = payload.session_id;
    return typeof id === "string" && UUID.test(id) ? id.toLowerCase() : null;
  } catch {
    return null;
  }
}
