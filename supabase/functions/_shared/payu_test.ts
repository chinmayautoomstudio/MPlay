import { assert, assertEquals } from "jsr:@std/assert@1";
import {
  billingMode,
  buildLinkPayload,
  commandHash,
  istDate,
  istDateTime,
  parseLinkResponse,
  parseMandateStatus,
  parseRefundStatus,
  parseVerifyResponse,
  PayuClient,
  type PayuConfig,
  payuConfig,
  reverseHash,
  sameHash,
  sha512Hex,
} from "./payu.ts";

const config: PayuConfig = {
  env: "test",
  clientId: "client",
  clientSecret: "secret",
  merchantId: "8000",
  key: "KEY",
  salt: "SALT",
  mode: "autopay",
  successUrl: "https://example.com/ok",
  failureUrl: "https://example.com/no",
};

const linkRequest = {
  txnId: "MPABC123",
  amountPaise: 9900,
  expiresAt: new Date("2027-01-31T06:30:00Z"),
  periodEnd: new Date("2027-02-28T04:30:00Z"),
  customer: { name: "Asha", email: "asha@example.com", phone: "9876543210" },
  mode: "autopay" as const,
  successUrl: "https://example.com/ok",
  failureUrl: "https://example.com/no",
};

Deno.test("SHA-512 and the command hash", async () => {
  assertEquals(
    await sha512Hex("abc"),
    "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
  );
  assertEquals(await commandHash("K", "verify_payment", "T1", "S"), await sha512Hex("K|verify_payment|T1|S"));
});

Deno.test("Reverse hash follows PayU's field order", async () => {
  const fields = { status: "success", txnid: "T1", amount: "99.00", productinfo: "Pro", firstname: "A", email: "a@b.c", udf1: "T1" };
  assertEquals(
    await reverseHash(fields, "K", "S"),
    await sha512Hex("S|success||||||||||T1|a@b.c|A|Pro|99.00|T1|K"),
  );
  assertEquals(
    await reverseHash({ ...fields, additionalCharges: "2.00" }, "K", "S"),
    await sha512Hex("2.00|S|success||||||||||T1|a@b.c|A|Pro|99.00|T1|K"),
  );
  assert(sameHash("ABcd", "abcd"));
  assert(!sameHash("abcd", "abce"));
  assert(!sameHash("abc", "abcd"));
});

Deno.test("Dates are sent in India time", () => {
  assertEquals(istDate(new Date("2027-01-31T20:00:00Z")), "2027-02-01");
  assertEquals(istDateTime(new Date("2027-01-31T06:30:00Z")), "2027-01-31 12:00:00");
});

Deno.test("An autopay link: fixed ₹99, one payment, no PayU messages, first debit one cycle later", () => {
  const p = buildLinkPayload(linkRequest);
  assertEquals(p.invoiceNumber, "MPABC123");
  assertEquals(p.subAmount, 99);
  assertEquals(p.isPartialPaymentAllowed, false);
  assertEquals(p.isAmountFilledByCustomer, false);
  assertEquals(p.maxPaymentsAllowed, 1);
  assertEquals(p.viaEmail, false);
  assertEquals(p.viaSms, false);
  assertEquals(p.expiryDate, "2027-01-31 12:00:00");
  assertEquals(p.source, "si_payment_link");
  assertEquals(p.siDetails, {
    billingAmount: "99.00",
    billingCurrency: "INR",
    billingCycle: "MONTHLY",
    billingInterval: 1,
    paymentStartDate: "2027-02-28",
    paymentEndDate: "2032-02-28",
  });
});

Deno.test("A manual-renewal link has no standing instruction", () => {
  const p = buildLinkPayload({ ...linkRequest, mode: "manual" });
  assertEquals(p.siDetails, undefined);
  assertEquals(p.source, "API");
});

Deno.test("Link answers", () => {
  assertEquals(parseLinkResponse({ status: 0, result: { paymentLink: "https://u.payu.in/x", invoiceNumber: "MP1" } }), {
    url: "https://u.payu.in/x",
    ref: "MP1",
  });
  assertEquals(parseLinkResponse({ status: -1, message: "duplicate invoice" }), null);
  assertEquals(parseLinkResponse({ result: { paymentLink: "http://insecure" } }), null);
});

Deno.test("verify_payment answers", () => {
  const ok = parseVerifyResponse("T1", {
    status: 1,
    transaction_details: {
      T1: { mihpayid: "403993", status: "success", unmappedstatus: "captured", transaction_amount: "99.00", txnid: "T1", mode: "upi", si_details: { authpayuid: "403993" } },
    },
  });
  assertEquals(ok, { status: "success", txnId: "T1", payuRef: "403993", amountPaise: 9900, method: "UPI", mandateRef: "403993", reason: undefined });

  const failed = parseVerifyResponse("T2", {
    transaction_details: { T2: { mihpayid: "1", status: "failure", unmappedstatus: "failed", amt: "99", error_Message: "Bank declined" } },
  });
  assertEquals(failed.status, "failure");
  assertEquals(failed.reason, "Bank declined");

  assertEquals(parseVerifyResponse("T3", { transaction_details: { T3: { mihpayid: "Not Found", status: "Not Found" } } }).status, "not_found");
  assertEquals(parseVerifyResponse("T4", {}).status, "not_found");
  const odd = parseVerifyResponse("T5", { transaction_details: { T5: { mihpayid: "2", status: "success", unmappedstatus: "auth" } } });
  assertEquals(odd.status, "pending", "An unclear success is not trusted as paid");
});

Deno.test("Mandate and refund status answers", () => {
  assertEquals(parseMandateStatus({ status: 1, result: { status: "active" } }), "active");
  assertEquals(parseMandateStatus({ status: 1, mandate_status: "REVOKED" }), "inactive");
  assertEquals(parseMandateStatus({ status: 0, msg: "error" }), "unknown");
  assertEquals(parseRefundStatus({ transaction_details: { R1: { "1": { status: "success", amount: "99.00" } } } }), {
    state: "success",
    amountPaise: 9900,
  });
  assertEquals(parseRefundStatus({ transaction_details: { R1: { "1": { status: "queued" } } } }).state, "pending");
});

Deno.test("Config needs every credential; mode defaults to autopay", () => {
  const env = {
    PAYU_CLIENT_ID: "a",
    PAYU_CLIENT_SECRET: "b",
    PAYU_MERCHANT_ID: "c",
    PAYU_KEY: "d",
    PAYU_SALT: "e",
    PAYU_SUCCESS_URL: "https://x/ok",
    PAYU_FAILURE_URL: "https://x/no",
  };
  assertEquals(payuConfig(env)?.mode, "autopay");
  assertEquals(payuConfig(env)?.env, "test");
  assertEquals(payuConfig({ ...env, PAYU_ENV: "live", PAYU_BILLING_MODE: "manual" })?.env, "live");
  assertEquals(payuConfig({ ...env, PAYU_SALT: undefined }), null);
  assertEquals(billingMode("manual"), "manual");
  assertEquals(billingMode("MANUAL"), "autopay");
});

Deno.test("The client gets one token, creates the link and signs commands", async () => {
  const calls: { url: string; body: string; headers: Headers }[] = [];
  const fakeFetch = (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input);
    calls.push({ url, body: String(init?.body ?? ""), headers: new Headers(init?.headers) });
    if (url.endsWith("/oauth/token")) return Promise.resolve(Response.json({ access_token: "tok", expires_in: 3600 }));
    if (url.endsWith("/payment-links")) {
      return Promise.resolve(Response.json({ status: 0, result: { paymentLink: "https://u.payu.in/l", invoiceNumber: "MPABC123" } }));
    }
    return Promise.resolve(Response.json({ status: 1, transaction_details: { MPABC123: { mihpayid: "9", status: "pending" } } }));
  };
  const client = new PayuClient(config, fakeFetch as typeof fetch);
  assertEquals(await client.createLink(linkRequest), { url: "https://u.payu.in/l", ref: "MPABC123" });
  await client.createLink(linkRequest);
  assertEquals(calls.filter((c) => c.url.endsWith("/oauth/token")).length, 1, "The token is reused");
  assertEquals(calls[1].headers.get("Authorization"), "Bearer tok");
  assertEquals(calls[1].headers.get("merchantId"), "8000");
  assert(calls[1].url.startsWith("https://uatoneapi.payu.in"));

  assertEquals((await client.verifyPayment("MPABC123")).status, "pending");
  const form = new URLSearchParams(calls.at(-1)!.body);
  assertEquals(form.get("command"), "verify_payment");
  assertEquals(form.get("hash"), await commandHash("KEY", "verify_payment", "MPABC123", "SALT"));
  assert(!calls.at(-1)!.body.includes("SALT"), "The salt is never sent");
});
