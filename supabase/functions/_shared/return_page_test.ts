import { assert, assertEquals, assertStringIncludes } from "jsr:@std/assert@1";
import { appIntent, returnPageHtml } from "./return_page.ts";

const LINK = "https://autoomstudio.com/pay/return";

Deno.test("The intent opens the app's return link with the result and transaction", () => {
  assertEquals(
    appIntent(LINK, true, "MP0485CD36413940ADB185"),
    "intent://autoomstudio.com/pay/return?result=success&txn=MP0485CD36413940ADB185" +
      "#Intent;scheme=https;package=com.autoomstudio.mp3studio;end",
  );
  assertEquals(
    appIntent(LINK, false, null),
    "intent://autoomstudio.com/pay/return?result=failure#Intent;scheme=https;package=com.autoomstudio.mp3studio;end",
  );
});

Deno.test("Success and failure pages say what happened and link back to the app", () => {
  const ok = returnPageHtml({ success: true, txnId: "MPABC", appLink: LINK });
  assertStringIncludes(ok, "Payment received");
  assertStringIncludes(ok, 'href="intent://autoomstudio.com/pay/return?result=success&#38;txn=MPABC#Intent;');
  assertStringIncludes(returnPageHtml({ success: false, txnId: null, appLink: LINK }), "Payment not completed");
});

Deno.test("Nothing from the request reaches the page unescaped", () => {
  const html = returnPageHtml({ success: true, txnId: '"><script>x</script>', appLink: LINK });
  assert(!html.includes("<script>x"));
  assert(!html.includes('"><'));
});
