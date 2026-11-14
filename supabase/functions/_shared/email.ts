// App-owned templates in Postgres + SendGrid Mail Send API (v3). Outbox rows log delivery for resend.

import type { SupabaseClient } from "./supabase.ts";

export type TemplateKey =
  | "welcome"
  | "payment_receipt"
  | "pre_debit"
  | "renewal_failed"
  | "grace_ending"
  | "expired"
  | "mandate_ending";

export type SendEmailInput = {
  templateKey: TemplateKey | string;
  to: string;
  userId?: string | null;
  txnId?: string | null;
  vars: Record<string, string | null | undefined>;
  dedupeKey: string;
};

export type SendEmailResult = {
  ok: boolean;
  skipped?: boolean;
  outboxId?: number;
  error?: string;
};

const DEFAULT_FROM = "MP3 Studio <noreply@mp3studio.in>";

/** Escape text before inserting into HTML templates. */
export function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

/** Replace `{{key}}` placeholders. Missing vars become empty strings. Values are HTML-escaped. */
export function renderTemplate(template: string, vars: Record<string, string | null | undefined>): string {
  return template.replace(/\{\{\s*([a-zA-Z0-9_]+)\s*\}\}/g, (_match, key: string) => {
    const raw = vars[key];
    if (raw == null) return "";
    return escapeHtml(String(raw));
  });
}

export function formatMoney(amountMinor: number, currency: string): string {
  const major = amountMinor / 100;
  if (currency.toUpperCase() === "USD") {
    return `$${major.toFixed(2)}`;
  }
  if (Number.isInteger(major)) return `₹${major}`;
  return `₹${major.toFixed(2)}`;
}

export function formatEmailDate(iso: string | null | undefined): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString("en-IN", { year: "numeric", month: "short", day: "numeric", timeZone: "Asia/Kolkata" });
}

/** First token of a display name for email greetings (`{{first_name}}`). */
export function firstName(displayName: string | null | undefined): string {
  const trimmed = displayName?.trim() ?? "";
  if (!trimmed) return "there";
  return trimmed.split(/\s+/)[0] || "there";
}

export function emailFromEnv(): string {
  return Deno.env.get("EMAIL_FROM")?.trim() || DEFAULT_FROM;
}

export function sendgridApiKey(): string | undefined {
  const key = Deno.env.get("SENDGRID_API_KEY")?.trim();
  return key || undefined;
}

type FetchLike = typeof fetch;

export type SendGridMail = {
  to: string;
  from: string;
  subject: string;
  html: string;
};

/** POSTs to SendGrid; returns the X-Message-Id when present. */
export async function sendViaSendGrid(
  mail: SendGridMail,
  apiKey: string,
  fetchImpl: FetchLike = fetch,
): Promise<{ ok: true; messageId: string | null } | { ok: false; error: string }> {
  const res = await fetchImpl("https://api.sendgrid.com/v3/mail/send", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      personalizations: [{ to: [{ email: mail.to }] }],
      from: parseFrom(mail.from),
      subject: mail.subject,
      content: [{ type: "text/html", value: mail.html }],
    }),
  });
  if (res.status === 202 || res.status === 200) {
    return { ok: true, messageId: res.headers.get("x-message-id") };
  }
  const body = await res.text().catch(() => "");
  return { ok: false, error: `sendgrid_${res.status}:${body.slice(0, 300)}` };
}

function parseFrom(from: string): { email: string; name?: string } {
  const m = from.match(/^\s*(.+?)\s*<([^>]+)>\s*$/);
  if (m) return { name: m[1].trim(), email: m[2].trim() };
  return { email: from.trim() };
}

/**
 * Inserts an outbox row, renders the Postgres template, sends via SendGrid, and updates status.
 * Skips when `dedupe_key` already has a `sent` row. Missing API key marks failed without throwing.
 */
export async function sendTemplatedEmail(
  admin: SupabaseClient,
  input: SendEmailInput,
  fetchImpl: FetchLike = fetch,
): Promise<SendEmailResult> {
  const to = input.to.trim();
  if (!to.includes("@")) {
    return { ok: false, error: "invalid_email" };
  }

  const { data: existing } = await admin
    .from("email_outbox")
    .select("id, status")
    .eq("dedupe_key", input.dedupeKey)
    .maybeSingle();
  if (existing?.status === "sent") {
    return { ok: true, skipped: true, outboxId: existing.id as number };
  }

  let outboxId = existing?.id as number | undefined;
  if (!outboxId) {
    const { data: inserted, error: insertError } = await admin
      .from("email_outbox")
      .insert({
        template_key: input.templateKey,
        to_email: to,
        user_id: input.userId ?? null,
        txn_id: input.txnId ?? null,
        payload: input.vars,
        status: "queued",
        dedupe_key: input.dedupeKey,
      })
      .select("id")
      .maybeSingle();
    if (insertError) {
      // Race on unique dedupe_key: re-check sent, otherwise treat as failure.
      const { data: raced } = await admin
        .from("email_outbox")
        .select("id, status")
        .eq("dedupe_key", input.dedupeKey)
        .maybeSingle();
      if (raced?.status === "sent") {
        return { ok: true, skipped: true, outboxId: raced.id as number };
      }
      if (!raced?.id) {
        console.error("email_outbox insert failed", insertError);
        return { ok: false, error: insertError.message };
      }
      outboxId = raced.id as number;
    } else {
      outboxId = inserted?.id as number | undefined;
    }
  }

  const { data: template, error: templateError } = await admin
    .from("email_templates")
    .select("subject, body_html, body")
    .eq("key", input.templateKey)
    .maybeSingle();
  const htmlBody = (template?.body_html as string | undefined)?.trim()
    || (template?.body as string | undefined)?.trim()
    || "";
  if (templateError || !template?.subject || !htmlBody) {
    const err = templateError?.message ?? "template_missing";
    await markOutbox(admin, outboxId, "failed", null, err);
    return { ok: false, outboxId, error: err };
  }

  const subject = renderTemplate(template.subject as string, input.vars);
  const html = renderTemplate(htmlBody, input.vars);
  const apiKey = sendgridApiKey();
  if (!apiKey) {
    console.error("SENDGRID_API_KEY is not set");
    await markOutbox(admin, outboxId, "failed", null, "provider_unconfigured");
    return { ok: false, outboxId, error: "provider_unconfigured" };
  }

  const sent = await sendViaSendGrid(
    { to, from: emailFromEnv(), subject, html },
    apiKey,
    fetchImpl,
  );
  if (!sent.ok) {
    console.error("SendGrid send failed", sent.error);
    await markOutbox(admin, outboxId, "failed", null, sent.error);
    return { ok: false, outboxId, error: sent.error };
  }
  await markOutbox(admin, outboxId, "sent", sent.messageId, null);
  return { ok: true, outboxId };
}

async function markOutbox(
  admin: SupabaseClient,
  id: number | undefined,
  status: "sent" | "failed",
  providerId: string | null,
  error: string | null,
): Promise<void> {
  if (id == null) return;
  await admin.from("email_outbox").update({
    status,
    provider_id: providerId,
    error,
    sent_at: status === "sent" ? new Date().toISOString() : null,
  }).eq("id", id).select("id").maybeSingle();
}
