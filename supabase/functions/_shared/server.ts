import { firstName, formatEmailDate, sendTemplatedEmail } from "./email.ts";
import { envOrVault, vaultSecret } from "./secrets.ts";
import { createClient, type SupabaseClient, type User } from "./supabase.ts";
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
  const pepper = await envOrVault(
    Deno.env.get("TRIAL_HASH_PEPPER"),
    () => vaultSecret(admin, "trial_hash_pepper"),
  );
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
  const claim = data as ClaimResult;
  if (claim.result === "granted" && user.email) {
    void sendWelcomeEmail(admin, user).catch((e) => console.error("welcome email failed", e));
  }
  return claim;
}

async function sendWelcomeEmail(admin: SupabaseClient, user: User): Promise<void> {
  const { data: profile } = await admin.from("profiles").select("display_name, email").eq("id", user.id)
    .maybeSingle();
  const { data: trial } = await admin.from("trials").select("ends_at").eq("user_id", user.id).maybeSingle();
  const email = (profile?.email as string | undefined) || user.email!;
  const name = (profile?.display_name as string | undefined)?.trim()
    || email.split("@")[0]
    || "there";
  await sendTemplatedEmail(admin, {
    templateKey: "welcome",
    to: email,
    userId: user.id,
    vars: {
      name,
      first_name: firstName(name),
      email,
      trial_ends: formatEmailDate(trial?.ends_at as string | undefined) || "in 30 days",
    },
    dedupeKey: `welcome:${user.id}`,
  });
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
