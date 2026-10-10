import { assertEquals } from "jsr:@std/assert@1";
import { classifyWebhook, parseWebhookBody, redact } from "./webhook.ts";

Deno.test("Form and JSON bodies are read", () => {
  assertEquals(parseWebhookBody("application/x-www-form-urlencoded", "txnid=MP1&status=success"), {
    txnid: "MP1",
    status: "success",
  });
  assertEquals(parseWebhookBody("application/json", '{"txnid":"MP1"}'), { txnid: "MP1" });
  assertEquals(parseWebhookBody("application/json", "[1]"), null);
  assertEquals(parseWebhookBody(null, ""), null);
});

Deno.test("Card, UPI and bank details and the hash are not stored", () => {
  assertEquals(
    redact({ txnid: "MP1", cardnum: "512345XXXX", vpa: "a@upi", hash: "abc", bank_ref_num: "1", status: "success", nested: { name_on_card: "A", mode: "CC" } }),
    { txnid: "MP1", status: "success", nested: { mode: "CC" } },
  );
});

Deno.test("Webhooks are classified by what they refer to", () => {
  const payment = classifyWebhook({ txnid: "MP1", mihpayid: "403", status: "success" });
  assertEquals(payment.kind, "payment");
  assertEquals(payment.txnId, "MP1");
  assertEquals(payment.eventKey, "payment:MP1:success::");

  assertEquals(classifyWebhook({ mihpayid: "403", refund_id: "R1", status: "success" }).kind, "refund");
  const dispute = classifyWebhook({ event: "chargeback", mihpayid: "403", chargeback_status: "Won" });
  assertEquals([dispute.kind, dispute.disputeState], ["dispute", "won"]);
  assertEquals(classifyWebhook({ event: "chargeback", mihpayid: "403" }).disputeState, "open");
  const mandate = classifyWebhook({ notificationType: "MANDATE_REVOKED", authpayuid: "403" });
  assertEquals([mandate.kind, mandate.mandateRef], ["mandate", "403"]);
  assertEquals(classifyWebhook({ hello: "world" }), {
    kind: "unknown",
    txnId: null,
    payuTxnId: null,
    payuRef: null,
    mandateRef: null,
    status: null,
    disputeState: null,
    eventKey: null,
  });
});

Deno.test("A payment-link postback is matched by our txnid in udf1", () => {
  const event = classifyWebhook({
    txnid: "938632",
    udf1: "MPE6362B8BB4EC4C13A64E",
    mihpayid: "613345778913415662",
    status: "success",
  });
  assertEquals(event.kind, "payment");
  assertEquals(event.txnId, "MPE6362B8BB4EC4C13A64E");
  assertEquals(event.payuTxnId, "938632");
  assertEquals(event.eventKey, "payment:MPE6362B8BB4EC4C13A64E:success::");
  assertEquals(classifyWebhook({ txnid: "938632", udf1: "something", status: "success" }).txnId, "938632");
});
