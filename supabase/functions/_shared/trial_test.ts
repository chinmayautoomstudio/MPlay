import { assertEquals, assertNotEquals } from "jsr:@std/assert@1";
import { envOrVault, pickSecret } from "./secrets.ts";
import { normalizeEmail, pepperedHash, validDeviceId } from "./trial.ts";

Deno.test("Gmail dots, plus aliases and case collapse to one address", () => {
  const expected = "johnsmith@gmail.com";
  assertEquals(normalizeEmail("John.Smith@gmail.com"), expected);
  assertEquals(normalizeEmail("j.o.h.n.s.m.i.t.h+trial2@Gmail.com"), expected);
  assertEquals(normalizeEmail("  johnsmith@googlemail.com "), expected);
});

Deno.test("Other domains keep their dots but lose the plus alias", () => {
  assertEquals(normalizeEmail("Chinmay.Nayak+test@autoomstudio.com"), "chinmay.nayak@autoomstudio.com");
  assertNotEquals(normalizeEmail("a.b@example.com"), normalizeEmail("ab@example.com"));
});

Deno.test("Malformed addresses are only lowercased", () => {
  assertEquals(normalizeEmail("NoAtSign"), "noatsign");
  assertEquals(normalizeEmail("@gmail.com"), "@gmail.com");
});

Deno.test("Only a 64-character lowercase hex device ID is accepted", () => {
  const id = "a".repeat(64);
  assertEquals(validDeviceId(id), id);
  assertEquals(validDeviceId("A".repeat(64)), null);
  assertEquals(validDeviceId("abc"), null);
  assertEquals(validDeviceId(42), null);
  assertEquals(validDeviceId(undefined), null);
});

Deno.test("Env secrets win; Vault is used when the env var is empty", () => {
  assertEquals(pickSecret("from-env", "from-vault"), "from-env");
  assertEquals(pickSecret("", "from-vault"), "from-vault");
  assertEquals(pickSecret("  ", "from-vault"), "from-vault");
  assertEquals(pickSecret(undefined, "from-vault"), "from-vault");
  assertEquals(pickSecret(undefined, ""), undefined);
});

Deno.test("envOrVault does not call Vault when the env var is set", async () => {
  let vaultCalls = 0;
  const value = await envOrVault("pepper", async () => {
    vaultCalls++;
    return "from-vault";
  });
  assertEquals(value, "pepper");
  assertEquals(vaultCalls, 0);
});

Deno.test("envOrVault uses Vault when the env var is empty", async () => {
  const value = await envOrVault("  ", async () => "from-vault");
  assertEquals(value, "from-vault");
});

Deno.test("Hashes depend on the pepper and are stable", async () => {
  const one = await pepperedHash("johnsmith@gmail.com", "pepper-1");
  assertEquals(one, await pepperedHash("johnsmith@gmail.com", "pepper-1"));
  assertNotEquals(one, await pepperedHash("johnsmith@gmail.com", "pepper-2"));
  assertEquals(one.length, 64);
});
