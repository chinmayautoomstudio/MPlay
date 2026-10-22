import { assertEquals, assert } from "jsr:@std/assert@1";
import {
  buildCashfreeOrderPayload,
  cashfreeConfig,
  cashfreeCustomerId,
  cashfreeHost,
  cashfreePhone,
  cashfreeSignature,
  checkoutPageHtml,
  checkoutPageUrl,
  findTxnId,
  isCashfreeUrl,
  orderToVerified,
  parseCashfreeOrder,
  sameSignature,
  validSessionId,
} from "./cashfree.ts";

const config = {
  env: "sandbox" as const,
  clientId: "TEST",
  clientSecret: "secret",
  returnUrl: "https://securedbmp3.autoomstudio.com/functions/v1/cashfree-return",
  notifyUrl: "https://securedbmp3.autoomstudio.com/functions/v1/cashfree-webhook",
  checkoutUrl: "https://securedbmp3.autoomstudio.com/functions/v1/cashfree-checkout",
};

const txn = "MPABC1234567890DEF12";
const userId = "b4207f4f-713e-46d3-b852-2e176f1529dc";
const session = "session_Ij0wOIlTkgOwbdHmO0fuueVT";

Deno.test("cashfreeConfig needs both keys", () => {
  assertEquals(cashfreeConfig({}), null);
  assertEquals(cashfreeConfig({ CASHFREE_CLIENT_ID: "id" }), null);
  const ok = cashfreeConfig({ CASHFREE_CLIENT_ID: "id", CASHFREE_CLIENT_SECRET: "sec" });
  assertEquals(ok?.env, "sandbox");
  assertEquals(ok?.checkoutUrl.endsWith("/cashfree-checkout"), true);
  assertEquals(cashfreeConfig({ CASHFREE_CLIENT_ID: "id", CASHFREE_CLIENT_SECRET: "sec", CASHFREE_ENV: "live" })?.env, "live");
});

Deno.test("Hosts follow CASHFREE_ENV", () => {
  assertEquals(cashfreeHost("sandbox"), "https://sandbox.cashfree.com/pg");
  assertEquals(cashfreeHost("live"), "https://api.cashfree.com/pg");
});

Deno.test("An order payload uses our txn as order_id and {order_id} in return_url", () => {
  const p = buildCashfreeOrderPayload({
    txnId: txn,
    amountPaise: 9900,
    currency: "INR",
    customer: { name: "A", email: "a@b.c", phone: "+919876543210" },
    customerId: userId,
    purpose: "MP3 Studio Pro, 1 month",
  }, config);
  assertEquals(p.order_id, txn);
  assertEquals(p.order_amount, 99);
  assertEquals(p.order_currency, "INR");
  const customer = p.customer_details as { customer_id: string; customer_phone: string };
  assertEquals(customer.customer_id, "b4207f4f713e46d3b8522e176f1529dc");
  assertEquals(customer.customer_phone, "9876543210");
  const meta = p.order_meta as { return_url: string; notify_url: string };
  assert(meta.return_url.includes("txn={order_id}"));
  assert(!meta.return_url.includes(`txn=${txn}`));
  assertEquals(meta.notify_url, config.notifyUrl);
});

Deno.test("cashfreePhone keeps + for international and uses 10 digits for India", () => {
  assertEquals(cashfreePhone("+1 415 555 1212"), "+14155551212");
  assertEquals(cashfreePhone("+919876543210"), "9876543210");
  assertEquals(cashfreePhone("9876543210"), "9876543210");
});

Deno.test("cashfreeCustomerId strips hyphens", () => {
  assertEquals(cashfreeCustomerId(userId), "b4207f4f713e46d3b8522e176f1529dc");
});

Deno.test("isCashfreeUrl includes our checkout launcher", () => {
  assertEquals(isCashfreeUrl("https://payments.cashfree.com/links/x"), true);
  assertEquals(isCashfreeUrl("https://sandbox.cashfree.com/pg/view/x"), true);
  assertEquals(isCashfreeUrl("https://securedbmp3.autoomstudio.com/functions/v1/cashfree-checkout?session=s"), true);
  assertEquals(isCashfreeUrl("https://u.payu.in/x"), false);
  assertEquals(isCashfreeUrl(null), false);
});

Deno.test("findTxnId walks a webhook body", () => {
  assertEquals(findTxnId({ data: { order: { order_id: txn } } }), txn);
  assertEquals(findTxnId({ type: "PAYMENT_SUCCESS_WEBHOOK" }), null);
});

Deno.test("parseCashfreeOrder and PAID maps to success", () => {
  const order = parseCashfreeOrder({
    order_id: txn,
    cf_order_id: "cf1",
    payment_session_id: session,
    order_status: "PAID",
    order_amount: 1.99,
    order_currency: "USD",
  });
  assertEquals(order?.session, session);
  const verified = orderToVerified(txn, order!);
  assertEquals(verified.status, "success");
  assertEquals(verified.amountPaise, 199);
  assertEquals(verified.currency, "USD");
});

Deno.test("Webhook signature is HMAC-SHA256 of timestamp+body", async () => {
  const sig = await cashfreeSignature("100", "{\"a\":1}", "secret");
  assert(sig.length > 10);
  assertEquals(sameSignature(sig, sig), true);
  assertEquals(sameSignature(sig, "nope"), false);
});

Deno.test("validSessionId and checkout page", () => {
  assertEquals(validSessionId(session), session);
  assertEquals(validSessionId("nope"), null);
  const html = checkoutPageHtml({ session, mode: "sandbox" });
  assert(html.includes(session));
  assert(html.includes('mode: "sandbox"'));
  const url = checkoutPageUrl(config, session, txn);
  assert(url.includes("cashfree-checkout"));
  assert(url.includes(`txn=${txn}`));
});

Deno.test("CashfreeClient creates and checks an order against a fake host", async () => {
  const { CashfreeClient } = await import("./cashfree.ts");
  const calls: { url: string; method?: string }[] = [];
  const http: typeof fetch = async (input, init) => {
    const url = String(input);
    calls.push({ url, method: init?.method });
    if (url.endsWith("/orders") && init?.method === "POST") {
      return new Response(JSON.stringify({
        order_id: txn,
        cf_order_id: "cf1",
        payment_session_id: session,
        order_status: "ACTIVE",
        order_amount: 99,
        order_currency: "INR",
      }), { status: 200 });
    }
    return new Response(JSON.stringify({
      order_id: txn,
      cf_order_id: "cf1",
      payment_session_id: session,
      order_status: "PAID",
      order_amount: 99,
      order_currency: "INR",
    }), { status: 200 });
  };
  const client = new CashfreeClient(config, http);
  const created = await client.createOrder({
    txnId: txn,
    amountPaise: 9900,
    currency: "INR",
    customer: { name: "A", email: "a@b.c", phone: "9876543210" },
    customerId: userId,
    purpose: "MP3 Studio Pro, 1 month",
  });
  assert(created.url.includes("cashfree-checkout"));
  assert(created.url.includes(session));
  const paid = await client.checkOrder(txn);
  assertEquals(paid.status, "success");
  assertEquals(paid.amountPaise, 9900);
  assert(calls[0].url.includes("sandbox.cashfree.com/pg/orders"));
});
