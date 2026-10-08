import { createClient, type SupabaseClient, type User } from "jsr:@supabase/supabase-js@2";
import { normalizeEmail, pepperedHash, validDeviceId } from "./trial.ts";
import type { Outcome, ReserveJob } from "./usage.ts";

/** Service-role client. The key is in the functions container's environment and never leaves the server. */
export function adminClient(): SupabaseClient {
  return createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** The signed-in user behind the request's access token, checked by GoTrue; null when missing or invalid. */
export async function caller(admin: SupabaseClient, req: Request): Promise<User | null> {
  const token = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
  if (!token) return null;
  const { data, error } = await admin.auth.getUser(token);
  return error ? null : data.user;
}

export async function readDeviceId(req: Request): Promise<string | null> {
  try {
    const body = await req.json();
    return validDeviceId(body?.deviceId);
  } catch {
    return null;
  }
}

export type ClaimResult = { result: "granted" | "existing" | "denied" | "unavailable"; reason?: string };

/** Claims the 30-day trial using the email from the verified token, never one from the request. */
export async function claimTrial(
  admin: SupabaseClient,
  user: User,
  deviceId: string | null,
): Promise<ClaimResult> {
  const pepper = Deno.env.get("TRIAL_HASH_PEPPER");
  if (!pepper || !user.email) {
    console.error(pepper ? "User has no email" : "TRIAL_HASH_PEPPER is not set");
    return { result: "unavailable" };
  }
  const emailHash = await pepperedHash(normalizeEmail(user.email), pepper);
  const deviceHash = deviceId ? await pepperedHash(deviceId, pepper) : null;
  const { data, error } = await admin.rpc("claim_trial", {
    p_user: user.id,
    p_email_hash: emailHash,
    p_device_hash: deviceHash,
  });
  if (error) {
    console.error("claim_trial failed", error);
    return { result: "unavailable" };
  }
  return data as ClaimResult;
}

export async function entitlements(admin: SupabaseClient, userId: string) {
  const { data, error } = await admin.rpc("compute_entitlements", { p_user: userId });
  if (error) throw error;
  return data as { disabled: boolean; trial: unknown } | null;
}

export async function usageSummary(admin: SupabaseClient, userId: string): Promise<unknown> {
  const { data, error } = await admin.rpc("usage_summary", { p_user: userId });
  if (error) throw error;
  return data;
}

export async function reserveSeparation(admin: SupabaseClient, userId: string, jobs: ReserveJob[]) {
  const { data, error } = await admin.rpc("reserve_separation", { p_user: userId, p_jobs: jobs });
  if (error) throw error;
  return data as { error?: string; granted?: string[]; denied?: string[]; usage?: unknown };
}

export async function finishSeparation(admin: SupabaseClient, userId: string, jobRef: string, outcome: Outcome) {
  const { data, error } = await admin.rpc("finish_separation", {
    p_user: userId,
    p_job_ref: jobRef,
    p_outcome: outcome,
  });
  if (error) throw error;
  return data as { usage: unknown };
}

export async function readJson(req: Request): Promise<unknown> {
  try {
    return await req.json();
  } catch {
    return null;
  }
}
