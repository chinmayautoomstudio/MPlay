// Billing emails (payments PRD 5.3): renewal failed, grace ending, expired, autopay ending. No email provider is
// chosen yet (PRD section 14), so the default notifier only logs that a notice was due, without the address.

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
  renewal_failed: "Your MP3 Studio Pro renewal failed",
  grace_ending: "MP3 Studio Pro ends tomorrow unless you fix your payment",
  expired: "Your MP3 Studio Pro has ended",
  mandate_ending: "Renew autopay to keep MP3 Studio Pro",
};
