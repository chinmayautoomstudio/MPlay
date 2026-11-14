alter table public.email_templates add column if not exists body text;

-- Dark MP3 Studio branded templates (SendGrid HTML). Seed both body and body_html.
insert into public.email_templates (key, subject, body_html, body, version, updated_at)
values
(
    'welcome',
    'Welcome to MP3 Studio',
    $html$<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="x-apple-disable-message-reformatting">
  <meta name="color-scheme" content="dark">
  <meta name="supported-color-schemes" content="dark">
  <title>Welcome to MP3 Studio</title>
  <style>
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

  </style>
</head>
<body>
  <div style="display:none;font-size:1px;color:#070710;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">Welcome to MP3 Studio! Your 30-day Pro trial has started.</div>
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
                    <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="58" class="hero-logo" style="display:block;width:58px;max-width:58px;height:auto;border:0;">
                  </td>
                  <td valign="middle" align="left">
                    <h1 class="hero-title" style="font-size:27px;line-height:1.2;color:#ffffff;margin:0;">Welcome to<br><span style="color:#c084fc;">MP3 Studio!</span></h1>
                  </td>
                </tr>
              </table>
              <p class="hero-subtitle" style="font-size:14px;line-height:1.5;color:#c4c4d4;margin:10px 0 0;">Play. Personalize. Feel the Vibe.<br>Your music journey starts here.</p>
            </td>
          </tr>

          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">Thanks for joining <strong style="color:#ffffff;">MP3 Studio!</strong> Your account is ready, and your <strong style="color:#ffffff;">30-day Pro trial</strong> has started (ends {{trial_ends}}).</p>
              <p style="margin:0 0 11px;text-align:justify;">Whether you're relaxing with LoFi beats, singing along, or experimenting with audio, MP3 Studio brings your music experience to a whole new level.</p>
              <p class="welcome-heading" style="color:#ffffff;font-weight:bold;margin:0 0 7px;">Here's what you can explore:</p>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">🎧</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title" style="margin:0;font-size:15px;line-height:1.35;color:#c084fc;">Ad-Free Music Player</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">Enjoy your favourite music without unwanted advertisements or interruptions.</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">🌙</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">LoFi Mode</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">Transform your listening experience with relaxing LoFi effects, warm vibes, and soothing sounds.</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">🎤</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title" style="margin:0;font-size:15px;line-height:1.35;color:#c084fc;">AI Vocal Remover</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">Separate vocals and instrumentals from your favourite tracks with AI-powered audio tools.</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">🥁</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Metronome &amp; BPM Detection</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">Improve your musical timing with the built-in metronome and automatically detect your tracks' BPM.</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">🎙️</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title" style="margin:0;font-size:15px;line-height:1.35;color:#c084fc;">Sing Along</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">Sing over your favourite songs, record your performance, and save your creativity.</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">✂️</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Audio Cutter &amp; Ringtone Maker</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">Trim your favourite audio tracks and create personalised ringtones from the music you love.</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <tr>
            <td align="center" class="cta-section" style="padding:22px 18px 23px;">
              <h2 class="cta-title" style="font-size:21px;line-height:1.25;color:#ffffff;margin:0 0 8px;">Ready to Feel the Vibe?</h2>
              <p class="cta-description" style="font-size:14px;line-height:1.5;color:#c7c7d5;margin:0 0 16px;">Your favourite music is waiting.<br>Let's make every beat count!</p>
              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td align="center" style="border-radius:30px;background-color:#9333ea;background-image:linear-gradient(90deg,#06b6d4,#8b5cf6,#ec4899);">
                    <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="cta-button" style="display:inline-block;padding:12px 27px;font-size:14px;font-weight:bold;line-height:1.35;color:#ffffff;text-decoration:none;border-radius:30px;background-color:#9333ea;">Open MP3 Studio →</a>
                  </td>
                </tr>
              </table>
              <p class="cta-note" style="font-size:12px;line-height:1.5;color:#8b8ba3;margin:13px 0 0;">Made for music lovers. Built for your vibe.</p>
            </td>
          </tr>
          <tr>
            <td align="center" class="footer-section" style="padding:14px 16px;background-color:#080810;border-top:1px solid #29213f;">
              <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="43" class="footer-logo" style="display:block;width:43px;height:auto;margin:0 auto 7px;">
              <p class="footer-text" style="font-size:11px;line-height:1.55;color:#888899;margin:0;">
                Play. Personalize. Feel the Vibe.<br>
                2026 AUTOOM STUDIO (OPC) PRIVATE LIMITED.<br>
                All rights reserved.
              </p>
              <p style="font-size:11px;margin:9px 0 0;">
                <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="footer-link" style="color:#22d3ee;text-decoration:none;">Visit MP3 Studio</a>
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>$html$,
    $html$<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="x-apple-disable-message-reformatting">
  <meta name="color-scheme" content="dark">
  <meta name="supported-color-schemes" content="dark">
  <title>Welcome to MP3 Studio</title>
  <style>
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

  </style>
</head>
<body>
  <div style="display:none;font-size:1px;color:#070710;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">Welcome to MP3 Studio! Your 30-day Pro trial has started.</div>
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
                    <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="58" class="hero-logo" style="display:block;width:58px;max-width:58px;height:auto;border:0;">
                  </td>
                  <td valign="middle" align="left">
                    <h1 class="hero-title" style="font-size:27px;line-height:1.2;color:#ffffff;margin:0;">Welcome to<br><span style="color:#c084fc;">MP3 Studio!</span></h1>
                  </td>
                </tr>
              </table>
              <p class="hero-subtitle" style="font-size:14px;line-height:1.5;color:#c4c4d4;margin:10px 0 0;">Play. Personalize. Feel the Vibe.<br>Your music journey starts here.</p>
            </td>
          </tr>

          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">Thanks for joining <strong style="color:#ffffff;">MP3 Studio!</strong> Your account is ready, and your <strong style="color:#ffffff;">30-day Pro trial</strong> has started (ends {{trial_ends}}).</p>
              <p style="margin:0 0 11px;text-align:justify;">Whether you're relaxing with LoFi beats, singing along, or experimenting with audio, MP3 Studio brings your music experience to a whole new level.</p>
              <p class="welcome-heading" style="color:#ffffff;font-weight:bold;margin:0 0 7px;">Here's what you can explore:</p>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">🎧</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title" style="margin:0;font-size:15px;line-height:1.35;color:#c084fc;">Ad-Free Music Player</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">Enjoy your favourite music without unwanted advertisements or interruptions.</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">🌙</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">LoFi Mode</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">Transform your listening experience with relaxing LoFi effects, warm vibes, and soothing sounds.</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">🎤</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title" style="margin:0;font-size:15px;line-height:1.35;color:#c084fc;">AI Vocal Remover</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">Separate vocals and instrumentals from your favourite tracks with AI-powered audio tools.</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">🥁</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Metronome &amp; BPM Detection</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">Improve your musical timing with the built-in metronome and automatically detect your tracks' BPM.</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">🎙️</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title" style="margin:0;font-size:15px;line-height:1.35;color:#c084fc;">Sing Along</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">Sing over your favourite songs, record your performance, and save your creativity.</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">✂️</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Audio Cutter &amp; Ringtone Maker</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">Trim your favourite audio tracks and create personalised ringtones from the music you love.</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>

          <tr>
            <td align="center" class="cta-section" style="padding:22px 18px 23px;">
              <h2 class="cta-title" style="font-size:21px;line-height:1.25;color:#ffffff;margin:0 0 8px;">Ready to Feel the Vibe?</h2>
              <p class="cta-description" style="font-size:14px;line-height:1.5;color:#c7c7d5;margin:0 0 16px;">Your favourite music is waiting.<br>Let's make every beat count!</p>
              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td align="center" style="border-radius:30px;background-color:#9333ea;background-image:linear-gradient(90deg,#06b6d4,#8b5cf6,#ec4899);">
                    <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="cta-button" style="display:inline-block;padding:12px 27px;font-size:14px;font-weight:bold;line-height:1.35;color:#ffffff;text-decoration:none;border-radius:30px;background-color:#9333ea;">Open MP3 Studio →</a>
                  </td>
                </tr>
              </table>
              <p class="cta-note" style="font-size:12px;line-height:1.5;color:#8b8ba3;margin:13px 0 0;">Made for music lovers. Built for your vibe.</p>
            </td>
          </tr>
          <tr>
            <td align="center" class="footer-section" style="padding:14px 16px;background-color:#080810;border-top:1px solid #29213f;">
              <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="43" class="footer-logo" style="display:block;width:43px;height:auto;margin:0 auto 7px;">
              <p class="footer-text" style="font-size:11px;line-height:1.55;color:#888899;margin:0;">
                Play. Personalize. Feel the Vibe.<br>
                2026 AUTOOM STUDIO (OPC) PRIVATE LIMITED.<br>
                All rights reserved.
              </p>
              <p style="font-size:11px;margin:9px 0 0;">
                <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="footer-link" style="color:#22d3ee;text-decoration:none;">Visit MP3 Studio</a>
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>$html$,
    1,
    now()
),
(
    'payment_receipt',
    'Payment Receipt',
    $html$<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="x-apple-disable-message-reformatting">
  <meta name="color-scheme" content="dark">
  <meta name="supported-color-schemes" content="dark">
  <title>Payment Receipt</title>
  <style>
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

  </style>
</head>
<body>
  <div style="display:none;font-size:1px;color:#070710;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">Your MP3 Studio Pro payment receipt.</div>
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
                    <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="58" class="hero-logo" style="display:block;width:58px;max-width:58px;height:auto;border:0;">
                  </td>
                  <td valign="middle" align="left">
                    <h1 class="hero-title" style="font-size:27px;line-height:1.2;color:#ffffff;margin:0;">Payment<br><span style="color:#c084fc;">receipt</span></h1>
                  </td>
                </tr>
              </table>
              <p class="hero-subtitle" style="font-size:14px;line-height:1.5;color:#c4c4d4;margin:10px 0 0;">Thanks for supporting MP3 Studio Pro.</p>
            </td>
          </tr>

          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">We received your payment for <strong style="color:#ffffff;">MP3 Studio Pro</strong>. This is a payment receipt, not a GST tax invoice.</p>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">💰</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Amount</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{amount}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">📅</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title" style="margin:0;font-size:15px;line-height:1.35;color:#c084fc;">Date</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{date}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">🧾</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Transaction</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{txn_id}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">💳</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title" style="margin:0;font-size:15px;line-height:1.35;color:#c084fc;">Method</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{method}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">⭐</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Plan</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{plan}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">✉️</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title" style="margin:0;font-size:15px;line-height:1.35;color:#c084fc;">Email</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{email}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td align="center" class="cta-section" style="padding:22px 18px 23px;">
              <h2 class="cta-title" style="font-size:21px;line-height:1.25;color:#ffffff;margin:0 0 8px;">Open your account</h2>
              <p class="cta-description" style="font-size:14px;line-height:1.5;color:#c7c7d5;margin:0 0 16px;">View Plans and payment history in the app.</p>
              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td align="center" style="border-radius:30px;background-color:#9333ea;background-image:linear-gradient(90deg,#06b6d4,#8b5cf6,#ec4899);">
                    <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="cta-button" style="display:inline-block;padding:12px 27px;font-size:14px;font-weight:bold;line-height:1.35;color:#ffffff;text-decoration:none;border-radius:30px;background-color:#9333ea;">View in app →</a>
                  </td>
                </tr>
              </table>
              <p class="cta-note" style="font-size:12px;line-height:1.5;color:#8b8ba3;margin:13px 0 0;">Made for music lovers. Built for your vibe.</p>
            </td>
          </tr>
          <tr>
            <td align="center" class="footer-section" style="padding:14px 16px;background-color:#080810;border-top:1px solid #29213f;">
              <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="43" class="footer-logo" style="display:block;width:43px;height:auto;margin:0 auto 7px;">
              <p class="footer-text" style="font-size:11px;line-height:1.55;color:#888899;margin:0;">
                Play. Personalize. Feel the Vibe.<br>
                2026 AUTOOM STUDIO (OPC) PRIVATE LIMITED.<br>
                All rights reserved.
              </p>
              <p style="font-size:11px;margin:9px 0 0;">
                <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="footer-link" style="color:#22d3ee;text-decoration:none;">Visit MP3 Studio</a>
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>$html$,
    $html$<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="x-apple-disable-message-reformatting">
  <meta name="color-scheme" content="dark">
  <meta name="supported-color-schemes" content="dark">
  <title>Payment Receipt</title>
  <style>
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

  </style>
</head>
<body>
  <div style="display:none;font-size:1px;color:#070710;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">Your MP3 Studio Pro payment receipt.</div>
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
                    <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="58" class="hero-logo" style="display:block;width:58px;max-width:58px;height:auto;border:0;">
                  </td>
                  <td valign="middle" align="left">
                    <h1 class="hero-title" style="font-size:27px;line-height:1.2;color:#ffffff;margin:0;">Payment<br><span style="color:#c084fc;">receipt</span></h1>
                  </td>
                </tr>
              </table>
              <p class="hero-subtitle" style="font-size:14px;line-height:1.5;color:#c4c4d4;margin:10px 0 0;">Thanks for supporting MP3 Studio Pro.</p>
            </td>
          </tr>

          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">We received your payment for <strong style="color:#ffffff;">MP3 Studio Pro</strong>. This is a payment receipt, not a GST tax invoice.</p>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">💰</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Amount</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{amount}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">📅</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title" style="margin:0;font-size:15px;line-height:1.35;color:#c084fc;">Date</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{date}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">🧾</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Transaction</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{txn_id}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">💳</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title" style="margin:0;font-size:15px;line-height:1.35;color:#c084fc;">Method</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{method}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">⭐</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Plan</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{plan}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">✉️</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title" style="margin:0;font-size:15px;line-height:1.35;color:#c084fc;">Email</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{email}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td align="center" class="cta-section" style="padding:22px 18px 23px;">
              <h2 class="cta-title" style="font-size:21px;line-height:1.25;color:#ffffff;margin:0 0 8px;">Open your account</h2>
              <p class="cta-description" style="font-size:14px;line-height:1.5;color:#c7c7d5;margin:0 0 16px;">View Plans and payment history in the app.</p>
              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td align="center" style="border-radius:30px;background-color:#9333ea;background-image:linear-gradient(90deg,#06b6d4,#8b5cf6,#ec4899);">
                    <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="cta-button" style="display:inline-block;padding:12px 27px;font-size:14px;font-weight:bold;line-height:1.35;color:#ffffff;text-decoration:none;border-radius:30px;background-color:#9333ea;">View in app →</a>
                  </td>
                </tr>
              </table>
              <p class="cta-note" style="font-size:12px;line-height:1.5;color:#8b8ba3;margin:13px 0 0;">Made for music lovers. Built for your vibe.</p>
            </td>
          </tr>
          <tr>
            <td align="center" class="footer-section" style="padding:14px 16px;background-color:#080810;border-top:1px solid #29213f;">
              <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="43" class="footer-logo" style="display:block;width:43px;height:auto;margin:0 auto 7px;">
              <p class="footer-text" style="font-size:11px;line-height:1.55;color:#888899;margin:0;">
                Play. Personalize. Feel the Vibe.<br>
                2026 AUTOOM STUDIO (OPC) PRIVATE LIMITED.<br>
                All rights reserved.
              </p>
              <p style="font-size:11px;margin:9px 0 0;">
                <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="footer-link" style="color:#22d3ee;text-decoration:none;">Visit MP3 Studio</a>
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>$html$,
    1,
    now()
),
(
    'pre_debit',
    'Upcoming MP3 Studio Pro Debit',
    $html$<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="x-apple-disable-message-reformatting">
  <meta name="color-scheme" content="dark">
  <meta name="supported-color-schemes" content="dark">
  <title>Upcoming MP3 Studio Pro Debit</title>
  <style>
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

  </style>
</head>
<body>
  <div style="display:none;font-size:1px;color:#070710;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">Your MP3 Studio Pro AutoPay debit is coming up.</div>
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
                    <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="58" class="hero-logo" style="display:block;width:58px;max-width:58px;height:auto;border:0;">
                  </td>
                  <td valign="middle" align="left">
                    <h1 class="hero-title" style="font-size:27px;line-height:1.2;color:#ffffff;margin:0;">Upcoming<br><span style="color:#c084fc;">AutoPay debit</span></h1>
                  </td>
                </tr>
              </table>
              <p class="hero-subtitle" style="font-size:14px;line-height:1.5;color:#c4c4d4;margin:10px 0 0;">A heads-up before your next renewal.</p>
            </td>
          </tr>

          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">Your MP3 Studio Pro subscription will renew automatically. No action is needed if you want to keep Pro. To stop renewals, cancel AutoPay in the app under Settings → Plans.</p>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">💰</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Amount</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{amount}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">📅</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title" style="margin:0;font-size:15px;line-height:1.35;color:#c084fc;">Scheduled date</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{debit_date}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td align="center" class="cta-section" style="padding:22px 18px 23px;">
              <h2 class="cta-title" style="font-size:21px;line-height:1.25;color:#ffffff;margin:0 0 8px;">Manage subscription</h2>
              <p class="cta-description" style="font-size:14px;line-height:1.5;color:#c7c7d5;margin:0 0 16px;">Review AutoPay and billing in the app.</p>
              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td align="center" style="border-radius:30px;background-color:#9333ea;background-image:linear-gradient(90deg,#06b6d4,#8b5cf6,#ec4899);">
                    <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="cta-button" style="display:inline-block;padding:12px 27px;font-size:14px;font-weight:bold;line-height:1.35;color:#ffffff;text-decoration:none;border-radius:30px;background-color:#9333ea;">Manage subscription →</a>
                  </td>
                </tr>
              </table>
              <p class="cta-note" style="font-size:12px;line-height:1.5;color:#8b8ba3;margin:13px 0 0;">Made for music lovers. Built for your vibe.</p>
            </td>
          </tr>
          <tr>
            <td align="center" class="footer-section" style="padding:14px 16px;background-color:#080810;border-top:1px solid #29213f;">
              <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="43" class="footer-logo" style="display:block;width:43px;height:auto;margin:0 auto 7px;">
              <p class="footer-text" style="font-size:11px;line-height:1.55;color:#888899;margin:0;">
                Play. Personalize. Feel the Vibe.<br>
                2026 AUTOOM STUDIO (OPC) PRIVATE LIMITED.<br>
                All rights reserved.
              </p>
              <p style="font-size:11px;margin:9px 0 0;">
                <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="footer-link" style="color:#22d3ee;text-decoration:none;">Visit MP3 Studio</a>
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>$html$,
    $html$<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="x-apple-disable-message-reformatting">
  <meta name="color-scheme" content="dark">
  <meta name="supported-color-schemes" content="dark">
  <title>Upcoming MP3 Studio Pro Debit</title>
  <style>
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

  </style>
</head>
<body>
  <div style="display:none;font-size:1px;color:#070710;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">Your MP3 Studio Pro AutoPay debit is coming up.</div>
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
                    <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="58" class="hero-logo" style="display:block;width:58px;max-width:58px;height:auto;border:0;">
                  </td>
                  <td valign="middle" align="left">
                    <h1 class="hero-title" style="font-size:27px;line-height:1.2;color:#ffffff;margin:0;">Upcoming<br><span style="color:#c084fc;">AutoPay debit</span></h1>
                  </td>
                </tr>
              </table>
              <p class="hero-subtitle" style="font-size:14px;line-height:1.5;color:#c4c4d4;margin:10px 0 0;">A heads-up before your next renewal.</p>
            </td>
          </tr>

          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">Your MP3 Studio Pro subscription will renew automatically. No action is needed if you want to keep Pro. To stop renewals, cancel AutoPay in the app under Settings → Plans.</p>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">💰</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Amount</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{amount}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">📅</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title" style="margin:0;font-size:15px;line-height:1.35;color:#c084fc;">Scheduled date</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{debit_date}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td align="center" class="cta-section" style="padding:22px 18px 23px;">
              <h2 class="cta-title" style="font-size:21px;line-height:1.25;color:#ffffff;margin:0 0 8px;">Manage subscription</h2>
              <p class="cta-description" style="font-size:14px;line-height:1.5;color:#c7c7d5;margin:0 0 16px;">Review AutoPay and billing in the app.</p>
              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td align="center" style="border-radius:30px;background-color:#9333ea;background-image:linear-gradient(90deg,#06b6d4,#8b5cf6,#ec4899);">
                    <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="cta-button" style="display:inline-block;padding:12px 27px;font-size:14px;font-weight:bold;line-height:1.35;color:#ffffff;text-decoration:none;border-radius:30px;background-color:#9333ea;">Manage subscription →</a>
                  </td>
                </tr>
              </table>
              <p class="cta-note" style="font-size:12px;line-height:1.5;color:#8b8ba3;margin:13px 0 0;">Made for music lovers. Built for your vibe.</p>
            </td>
          </tr>
          <tr>
            <td align="center" class="footer-section" style="padding:14px 16px;background-color:#080810;border-top:1px solid #29213f;">
              <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="43" class="footer-logo" style="display:block;width:43px;height:auto;margin:0 auto 7px;">
              <p class="footer-text" style="font-size:11px;line-height:1.55;color:#888899;margin:0;">
                Play. Personalize. Feel the Vibe.<br>
                2026 AUTOOM STUDIO (OPC) PRIVATE LIMITED.<br>
                All rights reserved.
              </p>
              <p style="font-size:11px;margin:9px 0 0;">
                <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="footer-link" style="color:#22d3ee;text-decoration:none;">Visit MP3 Studio</a>
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>$html$,
    1,
    now()
),
(
    'renewal_failed',
    'Your MP3 Studio Pro Renewal Failed',
    $html$<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="x-apple-disable-message-reformatting">
  <meta name="color-scheme" content="dark">
  <meta name="supported-color-schemes" content="dark">
  <title>Your MP3 Studio Pro Renewal Failed</title>
  <style>
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

  </style>
</head>
<body>
  <div style="display:none;font-size:1px;color:#070710;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">We could not renew MP3 Studio Pro. Fix payment to keep access.</div>
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
                    <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="58" class="hero-logo" style="display:block;width:58px;max-width:58px;height:auto;border:0;">
                  </td>
                  <td valign="middle" align="left">
                    <h1 class="hero-title" style="font-size:27px;line-height:1.2;color:#ffffff;margin:0;">Renewal<br><span style="color:#c084fc;">failed</span></h1>
                  </td>
                </tr>
              </table>
              <p class="hero-subtitle" style="font-size:14px;line-height:1.5;color:#c4c4d4;margin:10px 0 0;">Update your payment to keep Pro uninterrupted.</p>
            </td>
          </tr>

          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">We could not renew your MP3 Studio Pro subscription. You still have Pro during a short grace period. Open the app and tap <strong style="color:#ffffff;">Fix payment</strong> on Plans to update your payment method.</p>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">⏳</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Grace until</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{date}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td align="center" class="cta-section" style="padding:22px 18px 23px;">
              <h2 class="cta-title" style="font-size:21px;line-height:1.25;color:#ffffff;margin:0 0 8px;">Fix your payment</h2>
              <p class="cta-description" style="font-size:14px;line-height:1.5;color:#c7c7d5;margin:0 0 16px;">Keep Pro by updating your payment method in the app.</p>
              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td align="center" style="border-radius:30px;background-color:#9333ea;background-image:linear-gradient(90deg,#06b6d4,#8b5cf6,#ec4899);">
                    <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="cta-button" style="display:inline-block;padding:12px 27px;font-size:14px;font-weight:bold;line-height:1.35;color:#ffffff;text-decoration:none;border-radius:30px;background-color:#9333ea;">Fix payment →</a>
                  </td>
                </tr>
              </table>
              <p class="cta-note" style="font-size:12px;line-height:1.5;color:#8b8ba3;margin:13px 0 0;">Made for music lovers. Built for your vibe.</p>
            </td>
          </tr>
          <tr>
            <td align="center" class="footer-section" style="padding:14px 16px;background-color:#080810;border-top:1px solid #29213f;">
              <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="43" class="footer-logo" style="display:block;width:43px;height:auto;margin:0 auto 7px;">
              <p class="footer-text" style="font-size:11px;line-height:1.55;color:#888899;margin:0;">
                Play. Personalize. Feel the Vibe.<br>
                2026 AUTOOM STUDIO (OPC) PRIVATE LIMITED.<br>
                All rights reserved.
              </p>
              <p style="font-size:11px;margin:9px 0 0;">
                <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="footer-link" style="color:#22d3ee;text-decoration:none;">Visit MP3 Studio</a>
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>$html$,
    $html$<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="x-apple-disable-message-reformatting">
  <meta name="color-scheme" content="dark">
  <meta name="supported-color-schemes" content="dark">
  <title>Your MP3 Studio Pro Renewal Failed</title>
  <style>
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

  </style>
</head>
<body>
  <div style="display:none;font-size:1px;color:#070710;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">We could not renew MP3 Studio Pro. Fix payment to keep access.</div>
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
                    <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="58" class="hero-logo" style="display:block;width:58px;max-width:58px;height:auto;border:0;">
                  </td>
                  <td valign="middle" align="left">
                    <h1 class="hero-title" style="font-size:27px;line-height:1.2;color:#ffffff;margin:0;">Renewal<br><span style="color:#c084fc;">failed</span></h1>
                  </td>
                </tr>
              </table>
              <p class="hero-subtitle" style="font-size:14px;line-height:1.5;color:#c4c4d4;margin:10px 0 0;">Update your payment to keep Pro uninterrupted.</p>
            </td>
          </tr>

          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">We could not renew your MP3 Studio Pro subscription. You still have Pro during a short grace period. Open the app and tap <strong style="color:#ffffff;">Fix payment</strong> on Plans to update your payment method.</p>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">⏳</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Grace until</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{date}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td align="center" class="cta-section" style="padding:22px 18px 23px;">
              <h2 class="cta-title" style="font-size:21px;line-height:1.25;color:#ffffff;margin:0 0 8px;">Fix your payment</h2>
              <p class="cta-description" style="font-size:14px;line-height:1.5;color:#c7c7d5;margin:0 0 16px;">Keep Pro by updating your payment method in the app.</p>
              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td align="center" style="border-radius:30px;background-color:#9333ea;background-image:linear-gradient(90deg,#06b6d4,#8b5cf6,#ec4899);">
                    <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="cta-button" style="display:inline-block;padding:12px 27px;font-size:14px;font-weight:bold;line-height:1.35;color:#ffffff;text-decoration:none;border-radius:30px;background-color:#9333ea;">Fix payment →</a>
                  </td>
                </tr>
              </table>
              <p class="cta-note" style="font-size:12px;line-height:1.5;color:#8b8ba3;margin:13px 0 0;">Made for music lovers. Built for your vibe.</p>
            </td>
          </tr>
          <tr>
            <td align="center" class="footer-section" style="padding:14px 16px;background-color:#080810;border-top:1px solid #29213f;">
              <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="43" class="footer-logo" style="display:block;width:43px;height:auto;margin:0 auto 7px;">
              <p class="footer-text" style="font-size:11px;line-height:1.55;color:#888899;margin:0;">
                Play. Personalize. Feel the Vibe.<br>
                2026 AUTOOM STUDIO (OPC) PRIVATE LIMITED.<br>
                All rights reserved.
              </p>
              <p style="font-size:11px;margin:9px 0 0;">
                <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="footer-link" style="color:#22d3ee;text-decoration:none;">Visit MP3 Studio</a>
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>$html$,
    1,
    now()
),
(
    'grace_ending',
    'MP3 Studio Pro Ends Tomorrow Unless You Update Your Payment',
    $html$<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="x-apple-disable-message-reformatting">
  <meta name="color-scheme" content="dark">
  <meta name="supported-color-schemes" content="dark">
  <title>MP3 Studio Pro Ends Tomorrow Unless You Update Your Payment</title>
  <style>
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

  </style>
</head>
<body>
  <div style="display:none;font-size:1px;color:#070710;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">Pro grace period ends tomorrow — update payment to keep access.</div>
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
                    <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="58" class="hero-logo" style="display:block;width:58px;max-width:58px;height:auto;border:0;">
                  </td>
                  <td valign="middle" align="left">
                    <h1 class="hero-title" style="font-size:27px;line-height:1.2;color:#ffffff;margin:0;">Grace period<br><span style="color:#c084fc;">ending</span></h1>
                  </td>
                </tr>
              </table>
              <p class="hero-subtitle" style="font-size:14px;line-height:1.5;color:#c4c4d4;margin:10px 0 0;">One day left to keep your Pro access.</p>
            </td>
          </tr>

          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">Your MP3 Studio Pro grace period ends tomorrow. Update your payment now to avoid losing Pro access.</p>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">⏰</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Ends</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{date}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td align="center" class="cta-section" style="padding:22px 18px 23px;">
              <h2 class="cta-title" style="font-size:21px;line-height:1.25;color:#ffffff;margin:0 0 8px;">Update payment</h2>
              <p class="cta-description" style="font-size:14px;line-height:1.5;color:#c7c7d5;margin:0 0 16px;">Don't lose Pro — fix billing in the app now.</p>
              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td align="center" style="border-radius:30px;background-color:#9333ea;background-image:linear-gradient(90deg,#06b6d4,#8b5cf6,#ec4899);">
                    <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="cta-button" style="display:inline-block;padding:12px 27px;font-size:14px;font-weight:bold;line-height:1.35;color:#ffffff;text-decoration:none;border-radius:30px;background-color:#9333ea;">Update payment →</a>
                  </td>
                </tr>
              </table>
              <p class="cta-note" style="font-size:12px;line-height:1.5;color:#8b8ba3;margin:13px 0 0;">Made for music lovers. Built for your vibe.</p>
            </td>
          </tr>
          <tr>
            <td align="center" class="footer-section" style="padding:14px 16px;background-color:#080810;border-top:1px solid #29213f;">
              <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="43" class="footer-logo" style="display:block;width:43px;height:auto;margin:0 auto 7px;">
              <p class="footer-text" style="font-size:11px;line-height:1.55;color:#888899;margin:0;">
                Play. Personalize. Feel the Vibe.<br>
                2026 AUTOOM STUDIO (OPC) PRIVATE LIMITED.<br>
                All rights reserved.
              </p>
              <p style="font-size:11px;margin:9px 0 0;">
                <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="footer-link" style="color:#22d3ee;text-decoration:none;">Visit MP3 Studio</a>
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>$html$,
    $html$<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="x-apple-disable-message-reformatting">
  <meta name="color-scheme" content="dark">
  <meta name="supported-color-schemes" content="dark">
  <title>MP3 Studio Pro Ends Tomorrow Unless You Update Your Payment</title>
  <style>
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

  </style>
</head>
<body>
  <div style="display:none;font-size:1px;color:#070710;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">Pro grace period ends tomorrow — update payment to keep access.</div>
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
                    <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="58" class="hero-logo" style="display:block;width:58px;max-width:58px;height:auto;border:0;">
                  </td>
                  <td valign="middle" align="left">
                    <h1 class="hero-title" style="font-size:27px;line-height:1.2;color:#ffffff;margin:0;">Grace period<br><span style="color:#c084fc;">ending</span></h1>
                  </td>
                </tr>
              </table>
              <p class="hero-subtitle" style="font-size:14px;line-height:1.5;color:#c4c4d4;margin:10px 0 0;">One day left to keep your Pro access.</p>
            </td>
          </tr>

          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">Your MP3 Studio Pro grace period ends tomorrow. Update your payment now to avoid losing Pro access.</p>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">⏰</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Ends</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{date}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td align="center" class="cta-section" style="padding:22px 18px 23px;">
              <h2 class="cta-title" style="font-size:21px;line-height:1.25;color:#ffffff;margin:0 0 8px;">Update payment</h2>
              <p class="cta-description" style="font-size:14px;line-height:1.5;color:#c7c7d5;margin:0 0 16px;">Don't lose Pro — fix billing in the app now.</p>
              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td align="center" style="border-radius:30px;background-color:#9333ea;background-image:linear-gradient(90deg,#06b6d4,#8b5cf6,#ec4899);">
                    <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="cta-button" style="display:inline-block;padding:12px 27px;font-size:14px;font-weight:bold;line-height:1.35;color:#ffffff;text-decoration:none;border-radius:30px;background-color:#9333ea;">Update payment →</a>
                  </td>
                </tr>
              </table>
              <p class="cta-note" style="font-size:12px;line-height:1.5;color:#8b8ba3;margin:13px 0 0;">Made for music lovers. Built for your vibe.</p>
            </td>
          </tr>
          <tr>
            <td align="center" class="footer-section" style="padding:14px 16px;background-color:#080810;border-top:1px solid #29213f;">
              <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="43" class="footer-logo" style="display:block;width:43px;height:auto;margin:0 auto 7px;">
              <p class="footer-text" style="font-size:11px;line-height:1.55;color:#888899;margin:0;">
                Play. Personalize. Feel the Vibe.<br>
                2026 AUTOOM STUDIO (OPC) PRIVATE LIMITED.<br>
                All rights reserved.
              </p>
              <p style="font-size:11px;margin:9px 0 0;">
                <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="footer-link" style="color:#22d3ee;text-decoration:none;">Visit MP3 Studio</a>
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>$html$,
    1,
    now()
),
(
    'expired',
    'Your MP3 Studio Pro Subscription Has Ended',
    $html$<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="x-apple-disable-message-reformatting">
  <meta name="color-scheme" content="dark">
  <meta name="supported-color-schemes" content="dark">
  <title>Your MP3 Studio Pro Subscription Has Ended</title>
  <style>
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

  </style>
</head>
<body>
  <div style="display:none;font-size:1px;color:#070710;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">Your MP3 Studio Pro subscription has ended. Renew anytime.</div>
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
                    <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="58" class="hero-logo" style="display:block;width:58px;max-width:58px;height:auto;border:0;">
                  </td>
                  <td valign="middle" align="left">
                    <h1 class="hero-title" style="font-size:27px;line-height:1.2;color:#ffffff;margin:0;">Pro has<br><span style="color:#c084fc;">ended</span></h1>
                  </td>
                </tr>
              </table>
              <p class="hero-subtitle" style="font-size:14px;line-height:1.5;color:#c4c4d4;margin:10px 0 0;">Come back whenever you're ready.</p>
            </td>
          </tr>

          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">Your MP3 Studio Pro subscription has ended. You can renew anytime from Plans in the app.</p>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">📅</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Ended</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{date}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td align="center" class="cta-section" style="padding:22px 18px 23px;">
              <h2 class="cta-title" style="font-size:21px;line-height:1.25;color:#ffffff;margin:0 0 8px;">Renew Pro</h2>
              <p class="cta-description" style="font-size:14px;line-height:1.5;color:#c7c7d5;margin:0 0 16px;">Pick Monthly or Yearly and get back to creating.</p>
              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td align="center" style="border-radius:30px;background-color:#9333ea;background-image:linear-gradient(90deg,#06b6d4,#8b5cf6,#ec4899);">
                    <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="cta-button" style="display:inline-block;padding:12px 27px;font-size:14px;font-weight:bold;line-height:1.35;color:#ffffff;text-decoration:none;border-radius:30px;background-color:#9333ea;">Renew Pro →</a>
                  </td>
                </tr>
              </table>
              <p class="cta-note" style="font-size:12px;line-height:1.5;color:#8b8ba3;margin:13px 0 0;">Made for music lovers. Built for your vibe.</p>
            </td>
          </tr>
          <tr>
            <td align="center" class="footer-section" style="padding:14px 16px;background-color:#080810;border-top:1px solid #29213f;">
              <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="43" class="footer-logo" style="display:block;width:43px;height:auto;margin:0 auto 7px;">
              <p class="footer-text" style="font-size:11px;line-height:1.55;color:#888899;margin:0;">
                Play. Personalize. Feel the Vibe.<br>
                2026 AUTOOM STUDIO (OPC) PRIVATE LIMITED.<br>
                All rights reserved.
              </p>
              <p style="font-size:11px;margin:9px 0 0;">
                <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="footer-link" style="color:#22d3ee;text-decoration:none;">Visit MP3 Studio</a>
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>$html$,
    $html$<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="x-apple-disable-message-reformatting">
  <meta name="color-scheme" content="dark">
  <meta name="supported-color-schemes" content="dark">
  <title>Your MP3 Studio Pro Subscription Has Ended</title>
  <style>
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

  </style>
</head>
<body>
  <div style="display:none;font-size:1px;color:#070710;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">Your MP3 Studio Pro subscription has ended. Renew anytime.</div>
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
                    <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="58" class="hero-logo" style="display:block;width:58px;max-width:58px;height:auto;border:0;">
                  </td>
                  <td valign="middle" align="left">
                    <h1 class="hero-title" style="font-size:27px;line-height:1.2;color:#ffffff;margin:0;">Pro has<br><span style="color:#c084fc;">ended</span></h1>
                  </td>
                </tr>
              </table>
              <p class="hero-subtitle" style="font-size:14px;line-height:1.5;color:#c4c4d4;margin:10px 0 0;">Come back whenever you're ready.</p>
            </td>
          </tr>

          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">Your MP3 Studio Pro subscription has ended. You can renew anytime from Plans in the app.</p>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">📅</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Ended</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{date}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td align="center" class="cta-section" style="padding:22px 18px 23px;">
              <h2 class="cta-title" style="font-size:21px;line-height:1.25;color:#ffffff;margin:0 0 8px;">Renew Pro</h2>
              <p class="cta-description" style="font-size:14px;line-height:1.5;color:#c7c7d5;margin:0 0 16px;">Pick Monthly or Yearly and get back to creating.</p>
              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td align="center" style="border-radius:30px;background-color:#9333ea;background-image:linear-gradient(90deg,#06b6d4,#8b5cf6,#ec4899);">
                    <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="cta-button" style="display:inline-block;padding:12px 27px;font-size:14px;font-weight:bold;line-height:1.35;color:#ffffff;text-decoration:none;border-radius:30px;background-color:#9333ea;">Renew Pro →</a>
                  </td>
                </tr>
              </table>
              <p class="cta-note" style="font-size:12px;line-height:1.5;color:#8b8ba3;margin:13px 0 0;">Made for music lovers. Built for your vibe.</p>
            </td>
          </tr>
          <tr>
            <td align="center" class="footer-section" style="padding:14px 16px;background-color:#080810;border-top:1px solid #29213f;">
              <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="43" class="footer-logo" style="display:block;width:43px;height:auto;margin:0 auto 7px;">
              <p class="footer-text" style="font-size:11px;line-height:1.55;color:#888899;margin:0;">
                Play. Personalize. Feel the Vibe.<br>
                2026 AUTOOM STUDIO (OPC) PRIVATE LIMITED.<br>
                All rights reserved.
              </p>
              <p style="font-size:11px;margin:9px 0 0;">
                <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="footer-link" style="color:#22d3ee;text-decoration:none;">Visit MP3 Studio</a>
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>$html$,
    1,
    now()
),
(
    'mandate_ending',
    'Renew AutoPay to Keep MP3 Studio Pro',
    $html$<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="x-apple-disable-message-reformatting">
  <meta name="color-scheme" content="dark">
  <meta name="supported-color-schemes" content="dark">
  <title>Renew AutoPay to Keep MP3 Studio Pro</title>
  <style>
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

  </style>
</head>
<body>
  <div style="display:none;font-size:1px;color:#070710;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">Your AutoPay mandate is ending soon — renew to keep Pro renewals going.</div>
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
                    <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="58" class="hero-logo" style="display:block;width:58px;max-width:58px;height:auto;border:0;">
                  </td>
                  <td valign="middle" align="left">
                    <h1 class="hero-title" style="font-size:27px;line-height:1.2;color:#ffffff;margin:0;">AutoPay mandate<br><span style="color:#c084fc;">ending</span></h1>
                  </td>
                </tr>
              </table>
              <p class="hero-subtitle" style="font-size:14px;line-height:1.5;color:#c4c4d4;margin:10px 0 0;">Renew AutoPay for uninterrupted renewals.</p>
            </td>
          </tr>

          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">Your AutoPay mandate for MP3 Studio Pro is ending soon. Renew AutoPay in the app so upcoming renewals are not interrupted.</p>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">🔐</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Mandate ends around</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{date}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td align="center" class="cta-section" style="padding:22px 18px 23px;">
              <h2 class="cta-title" style="font-size:21px;line-height:1.25;color:#ffffff;margin:0 0 8px;">Renew AutoPay</h2>
              <p class="cta-description" style="font-size:14px;line-height:1.5;color:#c7c7d5;margin:0 0 16px;">Set up AutoPay again in Settings → Plans.</p>
              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td align="center" style="border-radius:30px;background-color:#9333ea;background-image:linear-gradient(90deg,#06b6d4,#8b5cf6,#ec4899);">
                    <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="cta-button" style="display:inline-block;padding:12px 27px;font-size:14px;font-weight:bold;line-height:1.35;color:#ffffff;text-decoration:none;border-radius:30px;background-color:#9333ea;">Renew AutoPay →</a>
                  </td>
                </tr>
              </table>
              <p class="cta-note" style="font-size:12px;line-height:1.5;color:#8b8ba3;margin:13px 0 0;">Made for music lovers. Built for your vibe.</p>
            </td>
          </tr>
          <tr>
            <td align="center" class="footer-section" style="padding:14px 16px;background-color:#080810;border-top:1px solid #29213f;">
              <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="43" class="footer-logo" style="display:block;width:43px;height:auto;margin:0 auto 7px;">
              <p class="footer-text" style="font-size:11px;line-height:1.55;color:#888899;margin:0;">
                Play. Personalize. Feel the Vibe.<br>
                2026 AUTOOM STUDIO (OPC) PRIVATE LIMITED.<br>
                All rights reserved.
              </p>
              <p style="font-size:11px;margin:9px 0 0;">
                <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="footer-link" style="color:#22d3ee;text-decoration:none;">Visit MP3 Studio</a>
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>$html$,
    $html$<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="x-apple-disable-message-reformatting">
  <meta name="color-scheme" content="dark">
  <meta name="supported-color-schemes" content="dark">
  <title>Renew AutoPay to Keep MP3 Studio Pro</title>
  <style>
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

  </style>
</head>
<body>
  <div style="display:none;font-size:1px;color:#070710;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">Your AutoPay mandate is ending soon — renew to keep Pro renewals going.</div>
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
                    <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="58" class="hero-logo" style="display:block;width:58px;max-width:58px;height:auto;border:0;">
                  </td>
                  <td valign="middle" align="left">
                    <h1 class="hero-title" style="font-size:27px;line-height:1.2;color:#ffffff;margin:0;">AutoPay mandate<br><span style="color:#c084fc;">ending</span></h1>
                  </td>
                </tr>
              </table>
              <p class="hero-subtitle" style="font-size:14px;line-height:1.5;color:#c4c4d4;margin:10px 0 0;">Renew AutoPay for uninterrupted renewals.</p>
            </td>
          </tr>

          <tr>
            <td class="welcome-section" style="padding:17px 24px 5px;font-size:14px;line-height:1.6;color:#c7c7d5;">
              <p style="margin:0 0 11px;">Hey <strong style="color:#ffffff;">{{first_name}}</strong>,</p>
              <p style="margin:0 0 11px;text-align:justify;">Your AutoPay mandate for MP3 Studio Pro is ending soon. Renew AutoPay in the app so upcoming renewals are not interrupted.</p>
            </td>
          </tr>

          <tr>
            <td class="feature-wrapper" style="padding:4px 20px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="feature-card" style="width:100%;background-color:#151525;border:1px solid #30234c;border-radius:9px;">
                <tr>
                  <td class="feature-icon-cell" width="35" valign="top" style="width:35px;padding:10px 0 0 11px;font-size:21px;line-height:1.2;">🔐</td>
                  <td class="feature-content" valign="top" style="padding:10px 11px 10px 7px;">
                    <h3 class="feature-title cyan-title" style="margin:0;font-size:15px;line-height:1.35;color:#22d3ee;">Mandate ends around</h3>
                    <p class="feature-description" style="margin:4px 0 0;font-size:13px;line-height:1.5;color:#c7c7d5;">{{date}}</p>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td align="center" class="cta-section" style="padding:22px 18px 23px;">
              <h2 class="cta-title" style="font-size:21px;line-height:1.25;color:#ffffff;margin:0 0 8px;">Renew AutoPay</h2>
              <p class="cta-description" style="font-size:14px;line-height:1.5;color:#c7c7d5;margin:0 0 16px;">Set up AutoPay again in Settings → Plans.</p>
              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td align="center" style="border-radius:30px;background-color:#9333ea;background-image:linear-gradient(90deg,#06b6d4,#8b5cf6,#ec4899);">
                    <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="cta-button" style="display:inline-block;padding:12px 27px;font-size:14px;font-weight:bold;line-height:1.35;color:#ffffff;text-decoration:none;border-radius:30px;background-color:#9333ea;">Renew AutoPay →</a>
                  </td>
                </tr>
              </table>
              <p class="cta-note" style="font-size:12px;line-height:1.5;color:#8b8ba3;margin:13px 0 0;">Made for music lovers. Built for your vibe.</p>
            </td>
          </tr>
          <tr>
            <td align="center" class="footer-section" style="padding:14px 16px;background-color:#080810;border-top:1px solid #29213f;">
              <img src="https://securedb-mailify.autoomstudio.in/storage/v1/object/public/email-template-images/b7447d81-d155-49f5-8859-bda0123094c7/eb47fb2c-67bc-49cc-acb4-a2a0a54099ab/1793944283064-mp3_studio-logo-transparent.png" alt="MP3 Studio" width="43" class="footer-logo" style="display:block;width:43px;height:auto;margin:0 auto 7px;">
              <p class="footer-text" style="font-size:11px;line-height:1.55;color:#888899;margin:0;">
                Play. Personalize. Feel the Vibe.<br>
                2026 AUTOOM STUDIO (OPC) PRIVATE LIMITED.<br>
                All rights reserved.
              </p>
              <p style="font-size:11px;margin:9px 0 0;">
                <a href="https://play.google.com/store/apps/details?id=com.autoomstudio.mp3studio" class="footer-link" style="color:#22d3ee;text-decoration:none;">Visit MP3 Studio</a>
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>$html$,
    1,
    now()
)
on conflict (key) do update set
    subject = excluded.subject,
    body_html = excluded.body_html,
    body = excluded.body,
    version = public.email_templates.version + 1,
    updated_at = now();


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

