// Billing emails (payments PRD 5.3): renewal failed, grace ending, expired, autopay ending.
// SendGridNotifier sends via app-owned templates; LogNotifier remains for tests / missing key fallback.

import { firstName, formatEmailDate, sendTemplatedEmail, type TemplateKey } from "./email.ts";
import type { SupabaseClient } from "./supabase.ts";

export type Notice = {
  type: "renewal_failed" | "grace_ending" | "expired" | "mandate_ending";
  userId: string;
  email: string | null;
  name: string | null;
  date: string | null;
};

export interface Notifier {
  send(notice: Notice): Promise<boolean>;
}

export class LogNotifier implements Notifier {
  send(notice: Notice): Promise<boolean> {
    console.log(`billing notice ${notice.type} for user ${notice.userId}`);
    return Promise.resolve(true);
  }
}

export const NOTICE_SUBJECTS: Record<Notice["type"], string> = {
  renewal_failed: "Your MP3 Studio Pro Renewal Failed",
  grace_ending: "MP3 Studio Pro Ends Tomorrow Unless You Update Your Payment",
  expired: "Your MP3 Studio Pro Subscription Has Ended",
  mandate_ending: "Renew AutoPay to Keep MP3 Studio Pro",
};

const NOTICE_TEMPLATE: Record<Notice["type"], TemplateKey> = {
  renewal_failed: "renewal_failed",
  grace_ending: "grace_ending",
  expired: "expired",
  mandate_ending: "mandate_ending",
};

export class SendGridNotifier implements Notifier {
  constructor(private readonly admin: SupabaseClient) {}

  async send(notice: Notice): Promise<boolean> {
    if (!notice.email) {
      console.error(`billing notice ${notice.type} missing email for ${notice.userId}`);
      return false;
    }
    const date = formatEmailDate(notice.date);
    const name = notice.name?.trim() || notice.email.split("@")[0] || "there";
    const result = await sendTemplatedEmail(this.admin, {
      templateKey: NOTICE_TEMPLATE[notice.type],
      to: notice.email,
      userId: notice.userId,
      vars: {
        name,
        first_name: firstName(name),
        date: date || "soon",
        email: notice.email,
      },
      dedupeKey: `notice:${notice.type}:${notice.userId}:${notice.date ?? ""}`,
    });
    return result.ok;
  }
}
