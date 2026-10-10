import { sameHash } from "./payu.ts";

export const JOB_ACTIONS = ["webhooks", "sweep", "renewals", "expiry", "notices"] as const;
export type JobAction = typeof JOB_ACTIONS[number];

export function parseJobAction(body: unknown): JobAction | null {
  const action = (body as Record<string, unknown> | null)?.action;
  return typeof action === "string" && (JOB_ACTIONS as readonly string[]).includes(action) ? action as JobAction : null;
}

/** The cron call must carry the job secret from Vault; compared in constant time. */
export function jobAuthorized(header: string | null, secret: string | undefined): boolean {
  return !!secret && secret.length >= 32 && !!header && sameHash(header, secret);
}
