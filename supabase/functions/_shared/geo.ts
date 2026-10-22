/** Country from the request IP. Missing, private or failed lookups are India so a broken proxy keeps INR. */
export type BillingRegion = "in" | "intl";
export type BillingCurrency = "INR" | "USD";

export const PRICES_IN = { month: 9900, year: 99900 } as const;
/** Cashfree sandbox cannot settle USD, so International stays on India prices until CASHFREE_ENV=live. */
export const PRICES_INTL = { month: 9900, year: 99900 } as const;
export const PRICES_INTL_USD = { month: 199, year: 1999 } as const;

export function isCashfreeLive(env: Record<string, string | undefined> = Deno.env.toObject()): boolean {
  return env.CASHFREE_ENV === "live";
}

const cache = new Map<string, { country: string | null; until: number }>();
const CACHE_MS = 10 * 60 * 1000;

/** India is always INR. International is INR in Cashfree sandbox and USD when CASHFREE_ENV=live. */
export function currencyOf(
  region: BillingRegion,
  env: Record<string, string | undefined> = Deno.env.toObject(),
): BillingCurrency {
  return region === "intl" && isCashfreeLive(env) ? "USD" : "INR";
}

export function pricesOf(
  region: BillingRegion,
  env: Record<string, string | undefined> = Deno.env.toObject(),
): { month: number; year: number } {
  if (region !== "intl") return { ...PRICES_IN };
  return isCashfreeLive(env) ? { ...PRICES_INTL_USD } : { ...PRICES_INTL };
}

/** First public address in X-Forwarded-For, else X-Real-IP / CF-Connecting-IP. */
export function clientIp(headers: Headers): string | null {
  const forwarded = headers.get("x-forwarded-for");
  if (forwarded) {
    for (const part of forwarded.split(",")) {
      const ip = unwrap(part);
      if (ip && isPublicIp(ip)) return ip;
    }
  }
  for (const name of ["x-real-ip", "cf-connecting-ip"]) {
    const ip = unwrap(headers.get(name));
    if (ip && isPublicIp(ip)) return ip;
  }
  return null;
}

function unwrap(value: string | null): string | null {
  if (!value) return null;
  const ip = value.trim().replace(/^\[|\]$/g, "");
  return ip === "" ? null : ip;
}

export function isPublicIp(ip: string): boolean {
  if (/^(\d{1,3}\.){3}\d{1,3}$/.test(ip)) {
    const parts = ip.split(".").map(Number);
    if (parts.some((n) => n > 255)) return false;
    const [a, b] = parts;
    if (a === 0 || a === 10 || a === 127) return false;
    if (a === 169 && b === 254) return false;
    if (a === 172 && b >= 16 && b <= 31) return false;
    if (a === 192 && b === 168) return false;
    if (a === 100 && b >= 64 && b <= 127) return false;
    return true;
  }
  if (!ip.includes(":")) return false;
  const lower = ip.toLowerCase();
  if (lower === "::1") return false;
  if (lower.startsWith("fe80:") || lower.startsWith("fc") || lower.startsWith("fd")) return false;
  return true;
}

type Fetch = typeof fetch;

export async function lookupCountry(ip: string, http: Fetch = fetch): Promise<string | null> {
  const now = Date.now();
  const hit = cache.get(ip);
  if (hit && hit.until > now) return hit.country;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 2000);
  try {
    const response = await http(`https://ipwho.is/${encodeURIComponent(ip)}?fields=success,country_code`, {
      signal: controller.signal,
    });
    const body = await response.json().catch(() => null) as { success?: boolean; country_code?: string } | null;
    const country = body?.success && typeof body.country_code === "string" && body.country_code.length === 2
      ? body.country_code.toUpperCase()
      : null;
    cache.set(ip, { country, until: now + CACHE_MS });
    return country;
  } catch {
    cache.set(ip, { country: null, until: now + 30_000 });
    return null;
  } finally {
    clearTimeout(timer);
  }
}

/** India when the country is IN, or when the IP or lookup is missing. */
export async function detectRegion(headers: Headers, http: Fetch = fetch): Promise<BillingRegion> {
  const ip = clientIp(headers);
  if (!ip) return "in";
  const country = await lookupCountry(ip, http);
  return country && country !== "IN" ? "intl" : "in";
}

export function parseRegion(value: unknown): BillingRegion | null | { error: "invalid_region" } {
  if (value === undefined || value === null || value === "") return null;
  if (value === "in" || value === "intl") return value;
  return { error: "invalid_region" };
}
