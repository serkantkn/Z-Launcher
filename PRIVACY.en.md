# Z Launcher — Privacy Policy

Last updated: 16 September 2026 · Version 1.0

## In short

Z Launcher has no server. None of your data reaches us, because there is nowhere for it to
go. There is no advertising network in the app, no analytics, no crash reporting and no
account. Everything it reads — your contacts, your messages, your pictures, your notes —
stays on your phone.

The only things that leave are the smallest pieces needed to do something you asked for:
approximate location for the weather, a track name for lyrics, what you type for search
suggestions. Each one is listed below.

## What stays on the phone

None of this leaves your device. Every permission is asked for only when the part of the app
that needs it is used; refuse one and that part stops working, the rest carries on.

| What | Why |
|---|---|
| Contacts | The People hub, the name of whoever is calling, people pinned to Start |
| Call log and phone state | The Phone hub, missed-call notifications, voicemail |
| SMS / MMS | The Messaging hub, message previews on the live tile |
| Calendar | The Calendar hub and the agenda tile |
| Photos, video, music, files | The Pictures, Music and Files hubs |
| Camera and microphone | The Camera hub, voice notes, dictation in the keyboard |
| Notifications | Counts and previews on live tiles |
| Installed apps and usage stats | The app list, and which apps you use most |
| What you type on the keyboard | Word prediction and correction |

About the keyboard in particular: **nothing is learned from password fields**, the "do not
learn from this" flag apps set (private browsing, one-time codes) is honoured, and a clip a
password manager has marked sensitive is never written to the clipboard history.

## What leaves the phone

Each of these is optional and happens only when you use that feature. Nothing carries your
identity, your device id or an advertising id.

**Weather — open-meteo.com**
Your approximate location (latitude/longitude), or the name of a city you searched for. No
account, nothing identifying.

**Lyrics and album art — lrclib.net, musicbrainz.org, coverartarchive.org, itunes.apple.com**
Only if you switched it on in settings. What is sent is the title, artist and album of the
track playing. Your file is never uploaded.

**Search suggestions — the search engine you chose (Google, Bing, DuckDuckGo, Yandex)**
What you type in the browser is sent as you type, to suggest completions. It can be switched
off in settings, and then nothing is sent.

**Email — the servers you enter**
The app connects straight to your IMAP/SMTP server. Nothing sits in between, and there is no
server of ours.

**Google Drive — googleapis.com**
Only if you connect a Drive account in the Files hub. Authorisation happens in Google's own
screen; the token is kept encrypted on your phone.

**The browser**
Sites you visit are between you and them. The app does not sit in the middle and does not
report anywhere.

## Where it is kept

All of it stays in the app's own private storage, on your phone.

- **Email passwords and cloud tokens**: in a vault, AES-GCM, under a key held by the Android
  Keystore. The key never leaves the phone and is never backed up.
- **Locked notes**: the body is encrypted the same way. Even with root access to the phone,
  a locked note cannot be read.
- **Browsing history and downloads**: in a file of their own, excluded from backup.

## Backup

If Android's backup and device-transfer are on, the app's **appearance settings and Start
screen layout** are included. These are deliberately excluded and never leave the phone:
email accounts and cached messages, notes and their attachments, the keyboard's learned words
and clipboard history, cloud accounts, calendar entries, browsing history, the location you
watch the weather for, and the password vault.

## Children

The app is not directed at children and collects no age information.

## Deleting your data

Uninstalling removes everything the app kept. To clear it without uninstalling: Android
Settings → Apps → Z Launcher → Storage → Clear data. There is no account or record of yours
on our side to delete, because none is ever created.

## Changes

If this policy changes, the date above is updated and the change is mentioned in the app's
"what's new" screen.

## Contact

Questions: **<put your contact email here>**
