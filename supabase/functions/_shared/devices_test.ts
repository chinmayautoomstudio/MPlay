import { assertEquals } from "jsr:@std/assert@1";
import { MAX_DEVICE_NAME, parseDeviceRequest, sessionIdFromRequest } from "./devices.ts";

const id = "a".repeat(64);
const other = "b".repeat(64);
const session = "aaaaaaaa-0000-4000-8000-000000000001";

function jwtWith(payload: Record<string, unknown>): string {
  const json = JSON.stringify(payload);
  const b64 = btoa(json).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return `hdr.${b64}.sig`;
}

Deno.test("Register, verify and remove need a device id", () => {
  assertEquals(parseDeviceRequest({ action: "register", deviceId: id, deviceName: " Pixel 8 " }), {
    action: "register",
    deviceId: id,
    deviceName: "Pixel 8",
    replaceDeviceId: null,
  });
  assertEquals(parseDeviceRequest({ action: "verify", deviceId: id }), {
    action: "verify",
    deviceId: id,
    deviceName: "Android device",
    replaceDeviceId: null,
  });
  assertEquals(parseDeviceRequest({ action: "remove", deviceId: id }), {
    action: "remove",
    deviceId: id,
    deviceName: "Android device",
    replaceDeviceId: null,
  });
  assertEquals(parseDeviceRequest({ action: "list" }), {
    action: "list",
    deviceId: null,
    deviceName: "Android device",
    replaceDeviceId: null,
  });
});

Deno.test("A replacement id must be a 64-character hex device id", () => {
  assertEquals(
    parseDeviceRequest({ action: "register", deviceId: id, replaceDeviceId: other })?.replaceDeviceId,
    other,
  );
  assertEquals(parseDeviceRequest({ action: "register", deviceId: id, replaceDeviceId: "nope" }), null);
  assertEquals(parseDeviceRequest({ action: "register", deviceName: "Phone" }), null);
  assertEquals(parseDeviceRequest({ action: "drop" }), null);
  assertEquals(parseDeviceRequest(null), null);
});

Deno.test("Device names are trimmed and capped", () => {
  const long = "x".repeat(MAX_DEVICE_NAME + 20);
  assertEquals(parseDeviceRequest({ action: "list", deviceName: long })!.deviceName.length, MAX_DEVICE_NAME);
  assertEquals(parseDeviceRequest({ action: "list", deviceName: "   " })!.deviceName, "Android device");
});

Deno.test("The session id comes from the JWT claim", () => {
  const req = new Request("https://example.com", {
    headers: { Authorization: `Bearer ${jwtWith({ session_id: session })}` },
  });
  assertEquals(sessionIdFromRequest(req), session);
  assertEquals(sessionIdFromRequest(new Request("https://example.com")), null);
  const bad = new Request("https://example.com", {
    headers: { Authorization: `Bearer ${jwtWith({ session_id: "not-a-uuid" })}` },
  });
  assertEquals(sessionIdFromRequest(bad), null);
});
