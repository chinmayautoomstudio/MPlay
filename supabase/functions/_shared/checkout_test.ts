import { assertEquals } from "jsr:@std/assert@1";
import { normalizePhone, parseCheckoutRequest, parseStatusRequest } from "./checkout.ts";
import { jobAuthorized, parseJobAction } from "./jobs.ts";

Deno.test("Indian mobile numbers are normalized and validated", () => {
  assertEquals(normalizePhone("98765 43210"), "9876543210");
  assertEquals(normalizePhone("+91 98765-43210"), "9876543210");
  assertEquals(normalizePhone("919876543210"), "9876543210");
  assertEquals(normalizePhone("09876543210"), "9876543210");
  assertEquals(normalizePhone("5876543210"), null);
  assertEquals(normalizePhone("98765"), null);
  assertEquals(normalizePhone(9876543210), null);
});

Deno.test("Checkout and status requests", () => {
  assertEquals(parseCheckoutRequest({}), { phone: null });
  assertEquals(parseCheckoutRequest(null), { phone: null });
  assertEquals(parseCheckoutRequest({ phone: "+919876543210" }), { phone: "9876543210" });
  assertEquals(parseCheckoutRequest({ phone: "12" }), null);
  assertEquals(parseCheckoutRequest({ amount: 1, phone: "9876543210" }), { phone: "9876543210" });
  assertEquals(parseStatusRequest({}), { txnId: null });
  assertEquals(parseStatusRequest({ txnId: "MPABC" }), { txnId: "MPABC" });
  assertEquals(parseStatusRequest({ txnId: "MP'; drop" }), null);
  assertEquals(parseStatusRequest({ txnId: "x".repeat(26) }), null);
});

Deno.test("Billing jobs need a known action and the job secret", () => {
  assertEquals(parseJobAction({ action: "sweep" }), "sweep");
  assertEquals(parseJobAction({ action: "drop" }), null);
  assertEquals(parseJobAction(null), null);
  const secret = "s".repeat(40);
  assertEquals(jobAuthorized(secret, secret), true);
  assertEquals(jobAuthorized("wrong", secret), false);
  assertEquals(jobAuthorized(null, secret), false);
  assertEquals(jobAuthorized("short", "short"), false, "A short secret is refused");
  assertEquals(jobAuthorized(secret, undefined), false);
});
