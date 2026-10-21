import type { SupabaseClient } from "jsr:@supabase/supabase-js@2";

export type AppSecretName = "trial_hash_pepper" | "billing_jobs_secret";

/** First non-empty candidate; used so Vault can stand in when Coolify drops the env var. */
export function pickSecret(...candidates: Array<string | null | undefined>): string | undefined {
  for (const value of candidates) {
    const trimmed = value?.trim();
    if (trimmed) return trimmed;
  }
  return undefined;
}

/** Reads an allow-listed Vault secret through `app_secret`. */
export async function vaultSecret(admin: SupabaseClient, name: AppSecretName): Promise<string | undefined> {
  const { data, error } = await admin.rpc("app_secret", { p_name: name });
  if (error) {
    console.error("app_secret failed", name, error);
    return undefined;
  }
  return typeof data === "string" && data.trim() ? data.trim() : undefined;
}
