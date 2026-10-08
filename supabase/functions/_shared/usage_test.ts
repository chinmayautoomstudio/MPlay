import { assertEquals } from "jsr:@std/assert@1";
import { MAX_JOBS, parseFinishBody, parseReserveBody } from "./usage.ts";

const ref = (i: number) => `00000000-0000-4000-8000-${i.toString().padStart(12, "0")}`;

Deno.test("A reserve body keeps the order and trims song names", () => {
  assertEquals(
    parseReserveBody({ jobs: [{ jobRef: ref(2), songRef: "  Song  " }, { jobRef: ref(1) }] }),
    [{ jobRef: ref(2), songRef: "Song" }, { jobRef: ref(1), songRef: null }],
  );
  assertEquals(parseReserveBody({ jobs: [{ jobRef: ref(1), songRef: "x".repeat(300) }] })![0].songRef!.length, 200);
});

Deno.test("Malformed reserve bodies are rejected", () => {
  assertEquals(parseReserveBody(null), null);
  assertEquals(parseReserveBody({ jobs: [] }), null);
  assertEquals(parseReserveBody({ jobs: [{ jobRef: "not-a-uuid" }] }), null);
  assertEquals(parseReserveBody({ jobs: [{ jobRef: "ABCDEF00-0000-4000-8000-000000000001" }] }), null);
  assertEquals(parseReserveBody({ jobs: [{ jobRef: ref(1) }, { jobRef: ref(1) }] }), null);
  assertEquals(parseReserveBody({ jobs: Array.from({ length: MAX_JOBS + 1 }, (_, i) => ({ jobRef: ref(i) })) }), null);
});

Deno.test("A finish body needs a UUID and a known outcome", () => {
  assertEquals(parseFinishBody({ jobRef: ref(1), outcome: "completed" }), { jobRef: ref(1), outcome: "completed" });
  assertEquals(parseFinishBody({ jobRef: ref(1), outcome: "released" }), { jobRef: ref(1), outcome: "released" });
  assertEquals(parseFinishBody({ jobRef: ref(1), outcome: "denied" }), null);
  assertEquals(parseFinishBody({ jobRef: "x", outcome: "completed" }), null);
  assertEquals(parseFinishBody(undefined), null);
});
