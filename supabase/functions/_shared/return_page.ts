// The page PayU's browser lands on after checkout (payments PRD CK5), served by payu-return itself so it doesn't
// depend on a website hosting it. On Android it opens MP3 Studio through an intent link that names the app's
// package, which reaches MainActivity's /pay/return intent filter without App Link verification. The page decides
// nothing: the app asks payment-status, which checks the payment with PayU.

export const APP_PACKAGE = "com.autoomstudio.mp3studio";

const PATH = "/pay/return";

/** Content-Security-Policy for the page: only its own inline style and script, nothing loaded, nothing posted. */
export const RETURN_PAGE_CSP =
  "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; " +
  "frame-ancestors 'none'";

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
}

/**
 * Intent link to the app's return link `https://<host>/pay/return?result=..&txn=..`, where host comes from appLink
 * (PAYU_RETURN_PAGE). txnId must already be validated.
 */
export function appIntent(appLink: string, success: boolean, txnId: string | null): string {
  const query = new URLSearchParams({ result: success ? "success" : "failure" });
  if (txnId) query.set("txn", txnId);
  return `intent://${new URL(appLink).host}${PATH}?${query}#Intent;scheme=https;package=${APP_PACKAGE};end`;
}

/** The return page. txnId must already be validated (`validTxnId`); everything is escaped anyway. */
export function returnPageHtml(opts: { success: boolean; txnId: string | null; appLink: string }): string {
  const intent = escapeHtml(appIntent(opts.appLink, opts.success, opts.txnId));
  const title = opts.success ? "Payment received" : "Payment not completed";
  const note = opts.success
    ? "Return to MP3 Studio to see your payment. We confirm it with PayU before turning on Pro."
    : "Nothing was charged for this attempt. Return to MP3 Studio to try again.";
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>MP3 Studio payment</title>
<style>
body { font-family: system-ui, sans-serif; margin: 0; min-height: 100vh; display: flex; align-items: center;
       justify-content: center; background: #121212; color: #f2f2f2; text-align: center; }
main { max-width: 420px; padding: 32px; }
h1 { font-size: 1.4rem; margin: 0 0 12px; }
p { color: #bdbdbd; line-height: 1.5; }
a.button { display: none; margin-top: 16px; padding: 14px 24px; border-radius: 24px; background: #8ab4f8;
           color: #121212; text-decoration: none; font-weight: 600; }
</style>
</head>
<body>
<main>
<h1>${escapeHtml(title)}</h1>
<p>${escapeHtml(note)}</p>
<a class="button" id="open" href="${intent}">Return to MP3 Studio</a>
</main>
<script>
(function () {
  if (!/Android/i.test(navigator.userAgent)) return;
  var open = document.getElementById("open");
  open.style.display = "inline-block";
  location.href = open.href;
})();
</script>
</body>
</html>
`;
}
