# Store review account (PRD PR5)

> Last updated: 2026-10-08

MP3 Studio can't be opened without a Google sign-in (PRD AU1), so store reviewers need a Google account that can sign in and reach every feature. Never commit the account's password; share it only in the store console's review notes or app access section.

## One-time setup

1. **Create the account.** A dedicated Google account, for example `mp3studio.review@gmail.com`, with 2-step verification off (reviewers can't receive codes). Use a strong password stored in the team password manager.
2. **Allow it to sign in.** While the Google consent screen (Google Cloud > Google Auth Platform > Audience) is External and in Testing, add the account under Test users. Once the consent screen is published to Production, any account can sign in and this step isn't needed.
3. **Sign in once** on any phone with the build under review. This creates the profile and claims the 30-day trial (if that phone and email haven't had one).
4. **Give it Pro.** As an Admin: Settings > Admin > search the review email > Grant Pro > 1 year. The account then has every feature for the whole review and any follow-up reviews. Check the audit log shows the grant. Renew it before it ends.
5. **Optional: show the Free plan.** Reviewers who want to see the limits can be given a second account without Pro (it gets Trial for 30 days and Free after that).

Don't make the review account an Admin.

## Notes for reviewers

Paste this (with the email and password) into the review notes:

> Sign in with the Google account below using "Continue with Google". Sign-in is required because plans and usage limits are tied to the account; music and recordings never leave the phone.
>
> The account has Pro, so every feature is unlocked:
> - Library, playlists, playback, widget: grant audio access when asked; any audio files on the device appear in Library.
> - AI Vocal Separator: Now Playing > AI Vocal Separator, or a song's menu (three dots) > Separate vocals. Processing runs on the phone and can take several minutes.
> - Metronome and BPM detection: Metronome tab, or Now Playing > Metronome > Detect BPM.
> - Sing Along: Now Playing > Sing along (needs microphone access, and a song with separated vocals).
> - Clips and ringtones: a song's menu > Set as ringtone, which opens the clip editor.
> - Plans: Settings > Plan. Payments aren't enabled yet, so "Go Pro" is disabled.
> - Delete account: Settings > Delete account > type DELETE. This deletes the review account; please don't use it unless you're testing deletion, or tell us so we can recreate it.

## If the account is deleted or locked

- **Deleted in review:** sign in again with the same Google account. It gets a new, empty profile but no second trial (trial claims are kept), so grant Pro again from the Admin screens.
- **Disabled by mistake:** Settings > Admin > the account > Enable account.
- **Google blocks the sign-in** (suspicious activity): sign in once from a normal browser on the same network to clear it, then retry.

See [accounts](features/accounts.md) for sign-in setup and [admin](features/admin.md) for the Admin screens.
