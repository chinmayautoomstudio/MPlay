import { assertEquals } from "jsr:@std/assert@1";
import { PAGE_MAX, parseAdminRequest } from "./admin.ts";

const id = "aaaaaaaa-0000-4000-8000-000000000001";

Deno.test("Read actions map to their SQL functions with defaults", () => {
  assertEquals(parseAdminRequest({ action: "overview" }), { fn: "admin_overview", args: {} });
  assertEquals(parseAdminRequest({ action: "users" }), {
    fn: "admin_list_users",
    args: { p_query: "", p_filter: "all", p_limit: 50, p_offset: 0 },
  });
  assertEquals(parseAdminRequest({ action: "users", query: " ann ", filter: "pro", offset: 50 })!.args, {
    p_query: "ann",
    p_filter: "pro",
    p_limit: 50,
    p_offset: 50,
  });
  assertEquals(parseAdminRequest({ action: "user", userId: id.toUpperCase() }), {
    fn: "admin_user_detail",
    args: { p_user: id },
  });
  assertEquals(parseAdminRequest({ action: "audit" })!.args, { p_limit: 50, p_before: null });
  assertEquals(parseAdminRequest({ action: "audit", before: 42 })!.args, { p_limit: 50, p_before: 42 });
  assertEquals(parseAdminRequest({ action: "activity" }), { fn: "admin_activity", args: { p_limit: 50 } });
  assertEquals(parseAdminRequest({ action: "activity", limit: 10 })!.args, { p_limit: 10 });
});

Deno.test("Write actions validate their fields", () => {
  assertEquals(parseAdminRequest({ action: "setRole", userId: id, role: "admin" })!.args, {
    p_user: id,
    p_role: "admin",
  });
  assertEquals(parseAdminRequest({ action: "setRole", userId: id, role: "owner" }), null);
  assertEquals(parseAdminRequest({ action: "setDisabled", userId: id, disabled: "yes" }), null);
  assertEquals(parseAdminRequest({ action: "addAdmin", email: " A@Example.com " })!.args, { p_email: "a@example.com" });
  assertEquals(parseAdminRequest({ action: "addAdmin", email: "nobody" }), null);
  assertEquals(
    parseAdminRequest({ action: "grantPro", userId: id, until: "2026-11-08T00:00:00Z" })!.args,
    { p_user: id, p_until: "2026-11-08T00:00:00.000Z" },
  );
  assertEquals(parseAdminRequest({ action: "grantPro", userId: id, until: "soon" }), null);
});

Deno.test("Billing actions", () => {
  assertEquals(parseAdminRequest({ action: "payments", query: " MP1 ", filter: "refunded" }), {
    fn: "admin_list_payments",
    args: { p_query: "MP1", p_filter: "refunded", p_limit: 50, p_offset: 0 },
  });
  assertEquals(parseAdminRequest({ action: "billingHealth" }), { fn: "admin_billing_health", args: {} });
  assertEquals(parseAdminRequest({ action: "refundPayment", txnId: "MPABC" }), {
    fn: "admin_billing_action",
    args: { p_action: "refund", p_user: null, p_txn: "MPABC" },
    payu: "refund",
  });
  assertEquals(parseAdminRequest({ action: "reverifyPayment", txnId: "MPABC" })!.payu, "reverify");
  assertEquals(parseAdminRequest({ action: "cancelSubscription", userId: id })!.args, {
    p_action: "cancel_subscription",
    p_user: id,
    p_txn: null,
  });
  assertEquals(parseAdminRequest({ action: "payments", filter: "stolen" }), null);
  assertEquals(parseAdminRequest({ action: "refundPayment", txnId: "MP-1; x" }), null);
  assertEquals(parseAdminRequest({ action: "cancelSubscription", userId: "1" }), null);
});

Deno.test("Malformed requests are rejected", () => {
  assertEquals(parseAdminRequest(null), null);
  assertEquals(parseAdminRequest({ action: "dropTables" }), null);
  assertEquals(parseAdminRequest({ action: "user", userId: "1" }), null);
  assertEquals(parseAdminRequest({ action: "users", filter: "everyone" }), null);
  assertEquals(parseAdminRequest({ action: "users", limit: PAGE_MAX + 1 }), null);
  assertEquals(parseAdminRequest({ action: "users", offset: -1 }), null);
  assertEquals(parseAdminRequest({ action: "users", query: "x".repeat(101) }), null);
  assertEquals(parseAdminRequest({ action: "activity", limit: 0 }), null);
  assertEquals(parseAdminRequest({ action: "activity", limit: PAGE_MAX + 1 }), null);
});
