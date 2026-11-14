#!/usr/bin/env python3
"""Generate dark MP3 Studio email template SQL seed for the migration."""

from __future__ import annotations

LOGO = (
    "https://securedb-mailify.autoomstudio.in/storage/v1/object/public/"
    "email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/"
    "eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png"
)
PLAY = "https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio"

STYLE = r"""
    body, table, td, p, h1, h2, h3, a { font-family: Arial, Helvetica, sans-serif; }
    body { margin: 0 !important; padding: 0 !important; width: 100% !important; background-color: #070710; -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
    table { border-spacing: 0; border-collapse: collapse; }
    img { display: block; border: 0; outline: none; text-decoration: none; height: auto; line-height: 100%; }
    .email-wrapper { width: 100%; background-color: #070710; padding: 12px 8px; }
    .email-container { width: 100%; max-width: 600px; background-color: #0b0b16; border: 1px solid #29213f; border-radius: 14px; overflow: hidden; }
    .hero-section { padding: 19px 24px 17px; background-color: #15102b; background-image: linear-gradient(135deg, #15102b, #090c1b); }
    .hero-tagline { font-size: 10px; line-height: 1.4; letter-spacing: 2px; color: #22d3ee; font-weight: bold; margin: 0 0 10px; }
    .hero-logo { width: 58px; max-width: 58px; height: auto; }
    .hero-title { font-size: 27px; line-height: 1.2; color: #ffffff; margin: 0; }
    .hero-subtitle { font-size: 14px; line-height: 1.5; color: #c4c4d4; margin: 10px 0 0; }
    .welcome-section { padding: 17px 24px 5px; font-size: 14px; line-height: 1.6; color: #c7c7d5; }
    .welcome-section p { margin: 0 0 11px; }
    .welcome-heading { color: #ffffff; font-weight: bold; margin-bottom: 7px !important; }
    .feature-wrapper { padding: 4px 20px; }
    .feature-card { width: 100%; background-color: #151525; border: 1px solid #30234c; border-radius: 9px; }
    .feature-icon-cell { width: 35px; padding: 10px 0 0 11px; font-size: 21px; line-height: 1.2; vertical-align: top; }
    .feature-content { padding: 10px 11px 10px 7px; vertical-align: top; }
    .feature-title { margin: 0; font-size: 15px; line-height: 1.35; color: #c084fc; }
    .feature-description { margin: 4px 0 0; font-size: 13px; line-height: 1.5; color: #c7c7d5; }
    .cyan-title { color: #22d3ee; }
    .cta-section { padding: 22px 18px 23px; }
    .cta-title { font-size: 21px; line-height: 1.25; color: #ffffff; margin: 0 0 8px; }
    .cta-description { font-size: 14px; line-height: 1.5; color: #c7c7d5; margin: 0 0 16px; }
    .cta-button { display: inline-block; padding: 12px 27px; font-size: 14px; font-weight: bold; line-height: 1.35; color: #ffffff !important; text-decoration: none; border-radius: 30px; background-color: #9333ea; background-image: linear-gradient(90deg, #06b6d4, #8b5cf6, #ec4899); }
    .cta-note { font-size: 12px; line-height: 1.5; color: #8b8ba3; margin: 13px 0 0; }
    .footer-section { padding: 14px 16px; background-color: #080810; border-top: 1px solid #29213f; }
    .footer-logo { width: 43px; height: auto; margin: 0 auto 7px; }
    .footer-text { font-size: 11px; line-height: 1.55; color: #888899; margin: 0; }
    .footer-link { color: #22d3ee; text-decoration: none; }
    @media only screen and (max-width: 480px) {
      .email-wrapper { padding: 5px 3px !important; }
      .email-container { border-radius: 10px !important; }
      .hero-section { padding: 15px 13px 13px !important; }
      .hero-tagline { font-size: 9px !important; letter-spacing: 1.6px !important; margin-bottom: 8px !important; }
      .hero-logo { width: 48px !important; max-width: 48px !important; }
      .hero-title { font-size: 23px !important; line-height: 1.18 !important; }
      .hero-subtitle { font-size: 13px !important; line-height: 1.45 !important; margin-top: 8px !important; }
      .welcome-section { padding: 13px 14px 3px !important; font-size: 13px !important; line-height: 1.55 !important; }
      .welcome-section p { margin-bottom: 9px !important; }
      .feature-wrapper { padding: 3px 5px !important; }
      .feature-icon-cell { width: 30px !important; padding: 9px 0 0 9px !important; font-size: 19px !important; }
      .feature-content { padding: 7px 6px 7px 6px !important; }
      .feature-title { font-size: 14px !important; }
      .feature-description { font-size: 12px !important; }
      .cta-section { padding: 18px 12px 19px !important; }
      .cta-title { font-size: 19px !important; }
      .cta-description { font-size: 13px !important; }
      .cta-button { padding: 11px 23px !important; font-size: 13px !important; }
      .footer-section { padding: 12px 10px !important; }
      .footer-logo { width: 38px !important; }
      .footer-text { font-size: 10px !important; }
    }
"""


def feature(icon: str, title: str, desc: str, cyan: bool = False) -> str:
    color = "#22d3ee" if cyan else "#c084fc"
    cls = ' class="feature-title cyan-title"' if cyan else ' class="feature-title"'
    return f"""
          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">{icon}</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3{cls} style="margin:0;font-size:15px;line-height:1.35;color:{color};">{title}</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{desc}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>"""


def shell(
    *,
    title: str,
    preheader: str,
    hero_title_html: str,
    hero_subtitle: str,
    middle: str,
    cta_title: str,
    cta_desc: str,
    cta_label: str,
    cta_note: str = "Made for music lovers. Built for your vibe.",
) -> str:
    return f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="x-apple-disable-message-reformatting">
  <meta name="color-scheme" content="dark">
  <meta name="supported-color-schemes" content="dark">
  <title>{title}</title>
  <style>{STYLE}
  </style>
</head>
<body>
  <div style="display:none;font-size:1px;color:#070710;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">{preheader}</div>
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="email-wrapper">
    <tr>
      <td align="center">
        <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" class="email-container" style="width:100%;max-width:600px;">
          <tr>
            <td align="center" class="hero-section" style="padding:19px 24px 17px;background-color:#15102b;">
              <p class="hero-tagline" style="font-size:10px;line-height:1.4;letter-spacing:2px;color:#22d3ee;font-weight:bold;margin:0 0 10px;">YOUR MUSIC. YOUR EXPERIENCE.</p>
              <table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center">
                <tr>
                  <td valign="middle" style="padding-right:10px;">
                    <img src="{LOGO}" alt="MP3 Studio" width="58" class="hero-logo" style="display:block;width:58px;max-width:58px;height:auto;border:0;">
                  </td>
                  <td valign="middle" align="left">
                    <h1 class="hero-title" style="font-size:27px;line-height:1.2;color:#ffffff;margin:0;">{hero_title_html}</h1>
                  </td>
                </tr>
              </table>
              <p class="hero-subtitle" style="font-size:14px;line-height:1.5;color:#c4c4d4;margin:10px 0 0;">{hero_subtitle}</p>
            </td>
          </tr>
{middle}
          <tr>
            <td align="center" class="cta-section" style="padding:22px 18px 23px;">
              <h2 class="cta-title" style="font-size:21px;line-height:1.25;color:#ffffff;margin:0 0 8px;">{cta_title}</h2>
              <p class="cta-description" style="font-size:14px;line-height:1.5;color:#c7c7d5;margin:0 0 16px;">{cta_desc}</p>
              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td align="center" style="border-radius:30px;background-color:#9333ea;background-image:linear-gradient(90deg,#06b6d4,#8b5cf6,#ec4899);">
                    <a href="{PLAY}" class="cta-button" style="display:inline-block;padding:12px 27px;font-size:14px;font-weight:bold;line-height:1.35;color:#ffffff;text-decoration:none;border-radius:30px;background-color:#9333ea;">{cta_label}</a>
                  </td>
                </tr>
              </table>
              <p class="cta-note" style="font-size:12px;line-height:1.5;color:#8b8ba3;margin:13px 0 0;">{cta_note}</p>
            </td>
          </tr>
          <tr>
            <td align="center" class="footer-section" style="padding:14px 16px;background-color:#080810;border-top:1px solid #29213f;">
              <img src="{LOGO}" alt="MP3 Studio" width="43" class="footer-logo" style="display:block;width:43px;height:auto;margin:0 auto 7px;">
              <p class="footer-text" style="font-size:11px;line-height:1.55;color:#888899;margin:0;">
                Play. Personalize. Feel the Vibe.<br>
                2026 AUTOOM STUDIO (OPC) PRIVATE LIMITED.<br>
                All rights reserved.
              </p>
              <p style="font-size:11px;margin:9px 0 0;">
                <a href="{PLAY}" class="footer-link" style="color:#22d3ee;text-decoration:none;">Visit MP3 Studio</a>
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>"""


TEMPLATES: list[tuple[str, str, str]] = []

welcome_middle = f"""
          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{{{first_name}}}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">Thanks for joining <strong style="color:#ffffff;">MP3 Studio!</strong> Your account is ready, and your <strong style="color:#ffffff;">30-day Pro trial</strong> has started (ends {{{{trial_ends}}}}).</p>
              <p style="margin:0 0 11px;text-align:justify;">Whether you're relaxing with LoFi beats, singing along, or experimenting with audio, MP3 Studio brings your music experience to a whole new level.</p>
              <p class="welcome-heading" style="color:#ffffff;font-weight:bold;margin:0 0 7px;">Here's what you can explore:</p>
            </td>
          </tr>
{feature("🎧", "Ad-Free Music Player", "Enjoy your favourite music without unwanted advertisements or interruptions.")}
{feature("🌙", "LoFi Mode", "Transform your listening experience with relaxing LoFi effects, warm vibes, and soothing sounds.", cyan=True)}
{feature("🎤", "AI Vocal Remover", "Separate vocals and instrumentals from your favourite tracks with AI-powered audio tools.")}
{feature("🥁", "Metronome &amp; BPM Detection", "Improve your musical timing with the built-in metronome and automatically detect your tracks' BPM.", cyan=True)}
{feature("🎙️", "Sing Along", "Sing over your favourite songs, record your performance, and save your creativity.")}
{feature("✂️", "Audio Cutter &amp; Ringtone Maker", "Trim your favourite audio tracks and create personalised ringtones from the music you love.", cyan=True)}
"""

TEMPLATES.append((
    "welcome",
    "Welcome to MP3 Studio",
    shell(
        title="Welcome to MP3 Studio",
        preheader="Welcome to MP3 Studio! Your 30-day Pro trial has started.",
        hero_title_html='Welcome to<br><span style="color:#c084fc;">MP3 Studio!</span>',
        hero_subtitle="Play. Personalize. Feel the Vibe.<br>Your music journey starts here.",
        middle=welcome_middle,
        cta_title="Ready to Feel the Vibe?",
        cta_desc="Your favourite music is waiting.<br>Let's make every beat count!",
        cta_label="Open MP3 Studio →",
    ),
))

receipt_middle = """
          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">We received your payment for <strong style="color:#ffffff;">MP3 Studio Pro</strong>. This is a payment receipt, not a GST tax invoice.</p>
            </td>
          </tr>
""" + feature("💰", "Amount", "{{amount}}", cyan=True) + feature("📅", "Date", "{{date}}") + feature("🧾", "Transaction", "{{txn_id}}", cyan=True) + feature("💳", "Method", "{{method}}") + feature("⭐", "Plan", "{{plan}}", cyan=True) + feature("✉️", "Email", "{{email}}")

TEMPLATES.append((
    "payment_receipt",
    "Payment Receipt",
    shell(
        title="Payment Receipt",
        preheader="Your MP3 Studio Pro payment receipt.",
        hero_title_html='Payment<br><span style="color:#c084fc;">receipt</span>',
        hero_subtitle="Thanks for supporting MP3 Studio Pro.",
        middle=receipt_middle,
        cta_title="Open your account",
        cta_desc="View Plans and payment history in the app.",
        cta_label="View in app →",
    ),
))

pre_debit_middle = """
          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">Your MP3 Studio Pro subscription will renew automatically. No action is needed if you want to keep Pro. To stop renewals, cancel AutoPay in the app under Settings → Plans.</p>
            </td>
          </tr>
""" + feature("💰", "Amount", "{{amount}}", cyan=True) + feature("📅", "Scheduled date", "{{debit_date}}")

TEMPLATES.append((
    "pre_debit",
    "Upcoming MP3 Studio Pro Debit",
    shell(
        title="Upcoming MP3 Studio Pro Debit",
        preheader="Your MP3 Studio Pro AutoPay debit is coming up.",
        hero_title_html='Upcoming<br><span style="color:#c084fc;">AutoPay debit</span>',
        hero_subtitle="A heads-up before your next renewal.",
        middle=pre_debit_middle,
        cta_title="Manage subscription",
        cta_desc="Review AutoPay and billing in the app.",
        cta_label="Manage subscription →",
    ),
))

renewal_middle = """
          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">We could not renew your MP3 Studio Pro subscription. You still have Pro during a short grace period. Open the app and tap <strong style="color:#ffffff;">Fix payment</strong> on Plans to update your payment method.</p>
            </td>
          </tr>
""" + feature("⏳", "Grace until", "{{date}}", cyan=True)

TEMPLATES.append((
    "renewal_failed",
    "Your MP3 Studio Pro Renewal Failed",
    shell(
        title="Your MP3 Studio Pro Renewal Failed",
        preheader="We could not renew MP3 Studio Pro. Fix payment to keep access.",
        hero_title_html='Renewal<br><span style="color:#c084fc;">failed</span>',
        hero_subtitle="Update your payment to keep Pro uninterrupted.",
        middle=renewal_middle,
        cta_title="Fix your payment",
        cta_desc="Keep Pro by updating your payment method in the app.",
        cta_label="Fix payment →",
    ),
))

grace_middle = """
          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">Your MP3 Studio Pro grace period ends tomorrow. Update your payment now to avoid losing Pro access.</p>
            </td>
          </tr>
""" + feature("⏰", "Ends", "{{date}}", cyan=True)

TEMPLATES.append((
    "grace_ending",
    "MP3 Studio Pro Ends Tomorrow Unless You Update Your Payment",
    shell(
        title="MP3 Studio Pro Ends Tomorrow Unless You Update Your Payment",
        preheader="Pro grace period ends tomorrow — update payment to keep access.",
        hero_title_html='Grace period<br><span style="color:#c084fc;">ending</span>',
        hero_subtitle="One day left to keep your Pro access.",
        middle=grace_middle,
        cta_title="Update payment",
        cta_desc="Don't lose Pro — fix billing in the app now.",
        cta_label="Update payment →",
    ),
))

expired_middle = """
          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">Your MP3 Studio Pro subscription has ended. You can renew anytime from Plans in the app.</p>
            </td>
          </tr>
""" + feature("📅", "Ended", "{{date}}", cyan=True)

TEMPLATES.append((
    "expired",
    "Your MP3 Studio Pro Subscription Has Ended",
    shell(
        title="Your MP3 Studio Pro Subscription Has Ended",
        preheader="Your MP3 Studio Pro subscription has ended. Renew anytime.",
        hero_title_html='Pro has<br><span style="color:#c084fc;">ended</span>',
        hero_subtitle="Come back whenever you're ready.",
        middle=expired_middle,
        cta_title="Renew Pro",
        cta_desc="Pick Monthly or Yearly and get back to creating.",
        cta_label="Renew Pro →",
    ),
))

mandate_middle = """
          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">Your AutoPay mandate for MP3 Studio Pro is ending soon. Renew AutoPay in the app so upcoming renewals are not interrupted.</p>
            </td>
          </tr>
""" + feature("🔐", "Mandate ends around", "{{date}}", cyan=True)

TEMPLATES.append((
    "mandate_ending",
    "Renew AutoPay to Keep MP3 Studio Pro",
    shell(
        title="Renew AutoPay to Keep MP3 Studio Pro",
        preheader="Your AutoPay mandate is ending soon — renew to keep Pro renewals going.",
        hero_title_html='AutoPay mandate<br><span style="color:#c084fc;">ending</span>',
        hero_subtitle="Renew AutoPay for uninterrupted renewals.",
        middle=mandate_middle,
        cta_title="Renew AutoPay",
        cta_desc="Set up AutoPay again in Settings → Plans.",
        cta_label="Renew AutoPay →",
    ),
))


def sql_escape_dollar(html: str) -> str:
    # $html$ ... $html$ — ensure delimiter uniqueness
    if "$html$" in html:
        raise SystemExit("delimiter collision")
    return html


def main() -> None:
    # Ensure body column exists for live cutover
    parts = [
        "alter table public.email_templates add column if not exists body text;",
        "",
        "-- Dark MP3 Studio branded templates (SendGrid HTML). Seed both body and body_html.",
        "insert into public.email_templates (key, subject, body_html, body, version, updated_at)",
        "values",
    ]
    value_rows = []
    for key, subject, html in TEMPLATES:
        html = sql_escape_dollar(html)
        value_rows.append(
            f"(\n    '{key}',\n    '{subject}',\n    $html${html}$html$,\n    $html${html}$html$,\n    1,\n    now()\n)"
        )
    parts.append(",\n".join(value_rows))
    parts.append(
        """on conflict (key) do update set
    subject = excluded.subject,
    body_html = excluded.body_html,
    body = excluded.body,
    version = public.email_templates.version + 1,
    updated_at = now();
"""
    )
    # Sync body when only body_html written on DBs without body in insert (already included)
    parts.append(
        """
do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'email_templates' and column_name = 'body'
    ) then
        update public.email_templates
        set body = body_html
        where nullif(btrim(body_html), '') is not null
          and (body is null or btrim(body) = '' or body is distinct from body_html);
    end if;
end $$;
"""
    )
    print("\n".join(parts))


if __name__ == "__main__":
    main()
