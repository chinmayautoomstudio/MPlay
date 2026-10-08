export const MAX_JOBS = 50;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

export type ReserveJob = { jobRef: string; songRef: string | null };
export type Outcome = "completed" | "released";

function jobRef(value: unknown): string | null {
  return typeof value === "string" && UUID.test(value) ? value : null;
}

/** `{ jobs: [{ jobRef, songRef? }] }` with 1 to MAX_JOBS distinct UUID job refs; null when malformed. */
export function parseReserveBody(body: unknown): ReserveJob[] | null {
  const jobs = (body as { jobs?: unknown })?.jobs;
  if (!Array.isArray(jobs) || jobs.length === 0 || jobs.length > MAX_JOBS) return null;
  const seen = new Set<string>();
  const parsed: ReserveJob[] = [];
  for (const job of jobs) {
    const ref = jobRef(job?.jobRef);
    if (!ref || seen.has(ref)) return null;
    seen.add(ref);
    const song = typeof job?.songRef === "string" ? job.songRef.trim().slice(0, 200) : "";
    parsed.push({ jobRef: ref, songRef: song || null });
  }
  return parsed;
}

/** `{ jobRef, outcome: "completed" | "released" }`; null when malformed. */
export function parseFinishBody(body: unknown): { jobRef: string; outcome: Outcome } | null {
  const ref = jobRef((body as { jobRef?: unknown })?.jobRef);
  const outcome = (body as { outcome?: unknown })?.outcome;
  if (!ref || (outcome !== "completed" && outcome !== "released")) return null;
  return { jobRef: ref, outcome };
}
