/**
 * One trial per person: the same Gmail address with dots or a +alias, or the same phone, can't claim twice
 * (PRD TR5). Only peppered hashes are stored, so `trial_claims` never holds an email or device ID.
 */

const GMAIL_DOMAINS = new Set(["gmail.com", "googlemail.com"]);

/** Lowercase; drops any `+alias`; for Gmail also drops dots and treats googlemail.com as gmail.com. */
export function normalizeEmail(email: string): string {
  const trimmed = email.trim().toLowerCase();
  const at = trimmed.lastIndexOf("@");
  if (at <= 0 || at === trimmed.length - 1) return trimmed;
  let local = trimmed.slice(0, at);
  let domain = trimmed.slice(at + 1);
  const plus = local.indexOf("+");
  if (plus >= 0) local = local.slice(0, plus);
  if (GMAIL_DOMAINS.has(domain)) {
    domain = "gmail.com";
    local = local.replaceAll(".", "");
  }
  return `${local}@${domain}`;
}

/** The device ID the app sends: a hex SHA-256 of its ANDROID_ID. Anything else is ignored. */
export function validDeviceId(value: unknown): string | null {
  return typeof value === "string" && /^[0-9a-f]{64}$/.test(value) ? value : null;
}

/** HMAC-SHA256 with the server-side pepper, as lowercase hex. */
export async function pepperedHash(value: string, pepper: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(pepper),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const mac = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(value));
  return Array.from(new Uint8Array(mac), (b) => b.toString(16).padStart(2, "0")).join("");
}
