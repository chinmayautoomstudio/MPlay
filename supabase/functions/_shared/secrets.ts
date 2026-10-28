import type { SupabaseClient } from "./supabase.ts";

export type AppSecretName = "trial_hash_pepper" | "billing_jobs_secret";

/** First non-empty candidate; used so Vault can stand in when Coolify drops the env var. */
export function pickSecret(...candidates: Array<string | null | undefined>): string | undefined {
  for (const value of candidates) {
    const trimmed = value?.trim();
    if (trimmed) return trimmed;
  }
  return undefined;
}

/**
 * Env first, Vault only if env is empty. Do not `await vaultSecret(...)` as a `pickSecret`
 * argument: JS evaluates both args, so that always hits Vault on a cold isolate.
 */
export async function envOrVault(
  envValue: string | null | undefined,
  fromVault: () => Promise<string | undefined>,
): Promise<string | undefined> {
  const fromEnv = pickSecret(envValue);
  if (fromEnv) return fromEnv;
  return await fromVault();
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
