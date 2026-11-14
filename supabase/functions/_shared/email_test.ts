import { assert, assertEquals } from "jsr:@std/assert@1";
import {
  escapeHtml,
  firstName,
  formatEmailDate,
  formatMoney,
  renderTemplate,
  sendTemplatedEmail,
  sendViaSendGrid,
} from "./email.ts";
import type { SupabaseClient } from "./supabase.ts";

Deno.test("escapeHtml and renderTemplate", () => {
  assertEquals(escapeHtml(`a<b>&"c"`), "a&lt;b&gt;&amp;&quot;c&quot;");
  assertEquals(
    renderTemplate("Hi {{name}}, pay {{amount}}", { name: "<x>", amount: "₹99" }),
    "Hi &lt;x&gt;, pay ₹99",
  );
  assertEquals(renderTemplate("Hello {{missing}}!", {}), "Hello !");
});

Deno.test("formatMoney and formatEmailDate", () => {
  assertEquals(formatMoney(9900, "INR"), "₹99");
  assertEquals(formatMoney(199, "USD"), "$1.99");
  assertEquals(formatMoney(99900, "INR"), "₹999");
  assert(formatEmailDate("2026-11-14T10:00:00Z").length > 0);
  assertEquals(formatEmailDate(null), "");
});

Deno.test("firstName", () => {
  assertEquals(firstName("Chinmay Nayak"), "Chinmay");
  assertEquals(firstName("  Asha  "), "Asha");
  assertEquals(firstName(""), "there");
  assertEquals(firstName(null), "there");
});

Deno.test("sendViaSendGrid posts the mail payload", async () => {
  let seen: Request | null = null;
  const fakeFetch: typeof fetch = async (input, init) => {
    seen = new Request(input, init);
    return new Response(null, { status: 202, headers: { "x-message-id": "sg-1" } });
  };
  const result = await sendViaSendGrid(
    {
      to: "asha@example.com",
      from: "MP3 Studio <noreply@mp3studio.in>",
      subject: "Payment Receipt",
      html: "<p>ok</p>",
    },
    "SG.TEST",
    fakeFetch,
  );
  assertEquals(result, { ok: true, messageId: "sg-1" });
  assert(seen);
  assertEquals(seen.method, "POST");
  assertEquals(seen.headers.get("Authorization"), "Bearer SG.TEST");
  const body = JSON.parse(await seen.text());
  assertEquals(body.personalizations[0].to[0].email, "asha@example.com");
  assertEquals(body.from.email, "noreply@mp3studio.in");
  assertEquals(body.from.name, "MP3 Studio");
  assertEquals(body.subject, "Payment Receipt");
});

Deno.test("sendViaSendGrid reports provider errors", async () => {
  const fakeFetch: typeof fetch = async () => new Response("nope", { status: 401 });
  const result = await sendViaSendGrid(
    { to: "a@b.co", from: "noreply@mp3studio.in", subject: "x", html: "y" },
    "bad",
    fakeFetch,
  );
  assertEquals(result.ok, false);
  if (!result.ok) assert(result.error.includes("sendgrid_401"));
});

type Row = Record<string, unknown>;

function fakeAdmin(opts: {
  outbox?: Row[];
  template?: Row | null;
  apiKeyMissing?: boolean;
}): SupabaseClient {
  const outbox = opts.outbox ?? [];
  const tables: Record<string, Row[]> = {
    email_outbox: outbox,
    email_templates: opts.template ? [opts.template] : [],
  };
  const client = {
    from(table: string) {
      const rows = tables[table] ?? [];
      let filters: Array<(r: Row) => boolean> = [];
      let pendingInsert: Row | null = null;
      let pendingUpdate: Row | null = null;
      const api = {
        select(_cols?: string) {
          return api;
        },
        insert(row: Row) {
          pendingInsert = { ...row, id: rows.length + 1 };
          return api;
        },
        update(row: Row) {
          pendingUpdate = row;
          return api;
        },
        eq(col: string, value: unknown) {
          filters.push((r) => r[col] === value);
          return api;
        },
        async maybeSingle() {
          if (pendingInsert) {
            const conflict = rows.find((r) =>
              pendingInsert!.dedupe_key && r.dedupe_key === pendingInsert!.dedupe_key
            );
            if (conflict) {
              return { data: null, error: { message: "duplicate key" } };
            }
            rows.push(pendingInsert);
            const id = pendingInsert.id;
            pendingInsert = null;
            return { data: { id }, error: null };
          }
          if (pendingUpdate) {
            const match = rows.find((r) => filters.every((f) => f(r)));
            if (match) Object.assign(match, pendingUpdate);
            pendingUpdate = null;
            return { data: match ?? null, error: null };
          }
          const match = rows.find((r) => filters.every((f) => f(r))) ?? null;
          return { data: match, error: null };
        },
      };
      return api;
    },
  };
  if (opts.apiKeyMissing) Deno.env.delete("SENDGRID_API_KEY");
  return client as unknown as SupabaseClient;
}

Deno.test("sendTemplatedEmail skips when already sent", async () => {
  Deno.env.set("SENDGRID_API_KEY", "SG.TEST");
  const admin = fakeAdmin({
    outbox: [{ id: 9, dedupe_key: "receipt:T1", status: "sent" }],
    template: { key: "payment_receipt", subject: "Payment Receipt", body_html: "<p>{{amount}}</p>", body: "" },
  });
  const result = await sendTemplatedEmail(admin, {
    templateKey: "payment_receipt",
    to: "asha@example.com",
    vars: { amount: "₹99" },
    dedupeKey: "receipt:T1",
  });
  assertEquals(result, { ok: true, skipped: true, outboxId: 9 });
});

Deno.test("sendTemplatedEmail fails without API key", async () => {
  Deno.env.delete("SENDGRID_API_KEY");
  const admin = fakeAdmin({
    template: { key: "welcome", subject: "Welcome to MP3 Studio", body_html: "<p>Hi {{name}}</p>", body: "" },
  });
  const result = await sendTemplatedEmail(admin, {
    templateKey: "welcome",
    to: "asha@example.com",
    vars: { name: "Asha" },
    dedupeKey: "welcome:u1",
  }, async () => new Response(null, { status: 202 }));
  assertEquals(result.ok, false);
  assertEquals(result.error, "provider_unconfigured");
});

Deno.test("sendTemplatedEmail falls back to body when body_html empty", async () => {
  Deno.env.set("SENDGRID_API_KEY", "SG.TEST");
  const outbox: Row[] = [];
  const admin = fakeAdmin({
    outbox,
    template: { key: "welcome", subject: "Hi {{first_name}}", body_html: "", body: "<p>{{first_name}}</p>" },
  });
  let htmlSent = "";
  const result = await sendTemplatedEmail(admin, {
    templateKey: "welcome",
    to: "asha@example.com",
    vars: { first_name: "Asha" },
    dedupeKey: "welcome:body-fallback",
  }, async (_input, init) => {
    htmlSent = JSON.parse(String(init?.body)).content[0].value;
    return new Response(null, { status: 202, headers: { "x-message-id": "m-body" } });
  });
  assertEquals(result.ok, true);
  assertEquals(htmlSent, "<p>Asha</p>");
});

Deno.test("sendTemplatedEmail sends and marks outbox sent", async () => {
  Deno.env.set("SENDGRID_API_KEY", "SG.TEST");
  const outbox: Row[] = [];
  const admin = fakeAdmin({
    outbox,
    template: { key: "welcome", subject: "Welcome {{name}}", body_html: "<p>{{name}}</p>", body: "" },
  });
  const result = await sendTemplatedEmail(admin, {
    templateKey: "welcome",
    to: "asha@example.com",
    userId: "u1",
    vars: { name: "Asha" },
    dedupeKey: "welcome:u1",
  }, async () => new Response(null, { status: 202, headers: { "x-message-id": "m-9" } }));
  assertEquals(result.ok, true);
  assertEquals(outbox[0].status, "sent");
  assertEquals(outbox[0].provider_id, "m-9");
});
