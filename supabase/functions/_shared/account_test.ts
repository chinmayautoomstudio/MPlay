import { assertEquals } from "jsr:@std/assert@1";
import { isDeleteConfirmed } from "./account.ts";

Deno.test("Deleting needs the exact confirmation word", () => {
  assertEquals(isDeleteConfirmed({ confirm: "DELETE" }), true);
  assertEquals(isDeleteConfirmed({ confirm: "delete" }), false);
  assertEquals(isDeleteConfirmed({ confirm: " DELETE" }), false);
  assertEquals(isDeleteConfirmed({}), false);
  assertEquals(isDeleteConfirmed(null), false);
  assertEquals(isDeleteConfirmed("DELETE"), false);
});
