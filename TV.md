# Z Launcher on Android TV

This branch (`tv`) only. Nothing here goes into the GitHub beta or the Play release until it is
decided otherwise; releases are built from `main`, and `main` is merged into this branch, not the
other way round.

## Decided

- **Windows Media Center, two of them.** (2026-10-19: the A board and C list of the draft are
  gone; 2026-10-20: Vista's added beside Windows 7's, chosen in settings.) Start and every hub are Media Center's; the phone's Start setting, wallpaper, hub
  background, tiles and icons are not offered on a TV, only the theme and type and motion.
- **Worked with a remote.** Every tile and word takes focus; the focused one grows a little and
  gains a white frame. The edges keep the 5% a TV may crop (48 × 27 dp).
- **Phone and messages through the phone.** A phone running Z Launcher can tell a paired TV on the
  same Wi-Fi who is calling or writing; the TV shows it in a corner. Off and on in settings on
  both; message text hidden on the TV by default. No server: pairing by a code, then the local
  network, encrypted.
- **Testing:** the user's own TV device when it is at hand, otherwise the `Google_TV_1080p`
  emulator (Android 16, Google TV image).

## Done in the draft

- `LocalIsTv` / `isTelevision()` (`ui/theme/Theme.kt`); a TV is not turned upright like a phone.
- `ui/tv/TvStartScreen.kt`: Start, the clock, the TV's own apps by their banners
  (`ui/tv/TvApps.kt`), hubs opened through the usual navigation.
- Manifest: `leanback` and `touchscreen` not required, `LEANBACK_LAUNCHER`, a 320 × 180 banner.
- The touch tour and the action center are left out on a TV.

## Focus in the hubs (done)

- `ui/tv/TvFocusFrame.kt`: one white frame round whatever holds the focus, for the whole
  launcher, the video player and dialogs; read from the view's focused rectangle, so no hub had to
  learn it. Start's tiles and words draw their own (`tvOwnFocus`).
- Empty `clickable {}` used to stop taps (panels, bars, dialogs) took the focus as well; they are
  `swallowTaps()` now.
- A tablet hub's headings take the focus as it opens, and moving onto one turns to its page.
- Start behind a hub, hubs kept out of sight and a hub behind its open page are fenced off from the
  remote (`tvFocusFence`, `pageHingeAway`); a page coming in takes the focus (`pageHingeIn`).
- Closing a hub gives the focus back to the tile or word that opened it.
- Video player: centre plays and pauses, left and right jump ten seconds, up and down show the
  controls (focus on play/pause), Back hides them, media keys work.
- Photo viewer: left and right turn the photo, centre shows the controls.
- `WpSlider`: focusable, the arrows move it a stop.
- A long press of the centre button is a long press (Compose's `combinedClickable` does it).

- Back from a hub's page the focus is walked back to the row that opened it (`tvWalkFocusTo`,
  from where the centre button was pressed).
- Search boxes take the remote as they appear on a TV, and the TV's own keyboard opens; typing
  and Back (keyboard away) work.

## Hubs on a TV (done)

- `HubType.onTelevision`: music + videos, pictures, files, weather, news, clock and settings.
  The rest (phone, messaging, people, camera, mail, notes, calendar, calculator, internet,
  social) do not open on a TV by any route - Start, a notification, a shortcut, another app.
- Start: the clock joined the tiles and the words.
- Settings on a TV: look, hubs (the clock's only), connections, system, about. Notifications,
  display+sound and keyboard are left out, and so are the tour, the permissions list and the
  other hubs' groups.

## Start's live content (done)

- `ui/tv/TvLive.kt`, read again whenever Start is come back to:
  - **continue**: the video last left part way in the launcher's own player (its resume record),
    by its picture, name and how far in; it leads the board as a wide tile and the list as its
    first word, and opens where it was left. Other apps' "continue watching" is not readable by
    a launcher.
  - **pictures**: the newest picture on the TV.
- From the hub's own data: the music playing (name, artist, album art), the weather, the first
  headline, the next alarm.
- A focused tile is no longer cut off at the row's edge.

## Being Home (done)

- Google TV keeps the Home button for its own launcher whatever Android's default is (tried:
  the home role given to Z Launcher, Home still opened `launcherx`).
- So on a TV the accessibility service catches the remote's Home and opens Z Launcher with the
  Home intent: from another app it comes forward, inside it a hub is put away. Both halves of
  the press are kept, so a long press opens Z Launcher too, not Google TV's panel.
- On a TV the service is listed as "Z Launcher Home button" with its own description
  (`values-television`, `values-tr-television`), and never covers a status bar.
- Settings > system on a TV: "home screen" - the service (with its own disclosure, and the
  restricted-settings row for sideloaded installs) and Android's default home screen for older
  Android TV boxes (said plainly not to work on Google TV).
- Dialogs take the focus on a TV as they open (their first stop: the button that leaves things
  be). The phone's "running hubs" balloon is not shown on a TV.
- Testing: the `Google_TV_1080p` AVD needs `hw.keyboard=yes` for the emulator's gRPC keys to
  reach it; evdev 102 arrives as Android's Home.

## Tried on a real TV (2026-10-19)

Xiaomi Mi TV (MiTV_MZTU0, Google TV, Android 14, MediaTek, 1.8 GB, 32-bit, UI at 1920 × 1080).

- Both Starts, the TV apps' banners, the clock and settings hubs and the board work with the
  remote. Found and fixed there:
  - a hub that hands the remote to no heading of its own (files) now gets it at its first stop
    (LauncherScreen, after 0.9 s);
  - the files hub no longer takes the remote's arrows as a keyboard's on a TV;
  - the layout choices are named for a TV ("xbox", Media Center's list), and the Zune glow, which
    a TV's Start does not use, is not offered there;
  - the board leaves no hole when there is no film to continue.
- Speed (release build, warmed): moving along Start 48 ms per frame at the median; Google TV's own
  launcher, same keys, 61 ms. The TV is slow; Z Launcher is a little quicker than its own home.
- Not tried on the TV: anything needing a permission (not granted on the user's devices), and
  the Home button service, which the user turns on.

## Hubs fitted to a TV, and a full pass on the real TV (2026-10-19)

- Type a size up on a TV (x1.15), and the hub's content kept clear of the top edge.
- Tablet hubs' headings: they sit closer on a TV so seven fit; the heading of the page showing
  takes the focus (a hub opened at its videos page stays there); a heading handed the focus back
  does not turn the page - only the remote's arrows do.
- Weather's panorama: its section names take the focus, so the remote moves along it.
- Pictures: on a TV the first page is every picture ("tüm resimler"), the TV having no camera.
- News: "read on" opens the TV's own browser (no internet hub on a TV), or says there is none.
- Words that said "phone" say "TV" on a TV (music, videos, files' cloud).
- Video player: Back with the controls up puts them away (caught in the activity: Back arrives
  as a navigation event there); the controls take the focus as they come up, the screen as they go.
- Photo viewer: the picture is not framed in white (it fills the screen); when a viewer or a
  dialog closes under the focus, the next arrow puts it back on the row that opened it.
- Tried on the Mi TV with permissions granted: Start (both), music, videos and the player,
  pictures and the viewer, weather, news (and the TV browser), clock, files (with its dialog),
  settings. The Home button service cannot be driven by adb (adb keys skip accessibility); the
  user tries it.

## Every hub as Windows Media Center (2026-10-19)

The user asked for every hub on a TV in Media Center's design, with no exception.

- `ui/tv/MediaCenter.kt`: the deep blue ground with its band of light; `MediaCenterHub` - the
  hub's name small at the top left, its pages as one row of words the remote runs along (moving
  along turns the page), the page under it, folders/albums down the left, an open item beside;
  `MediaCenterCommandStrip` - the bar's commands along the foot, icon and name side by side.
- `ZuneHub` draws `MediaCenterHub` on a TV, so music, pictures, settings, clock and news follow;
  weather and files (off the hub standard) call it / its row themselves. `WindowsPhoneBottomBar`
  and `Win8CommandBar` draw the strip on a TV, so sub-screens and the photo viewer do too.
- The hubs' ground on a TV is Media Center's blue (also behind sub-screens); weather's painted
  sky is the phone's. Sub-screen headers take Media Center's margins and pale blue path.
- The focus frame is Media Center's glow: a soft blue halo, a bright edge, a wash of light.
- Fixes found on the TV: the list's commands give way while an item is open beside it; the photo
  viewer keeps the remote in itself, and the arrows go from the picture to its commands.

## Start as Windows Media Center (2026-10-19)

The user asked for Start in Media Center's design too, and for no other design on a TV.

- `MediaCenterStart`: strips down the screen, each a name and a row of tiles beside it -
  "continue" (a film left part way, when there is one), pictures + videos, music, the TV's apps
  by their banners, extras (weather, news, clock), tasks (files, settings, Android settings).
  The strip the remote is in stands a little above the middle, bright and full size; the others
  are smaller and pale. Up and down go from strip to strip, left and right along one.
- The strip the remote is in is known by its name, not its place: "continue" comes in above once
  the videos are read, and the first focus is given once, so a strip arriving late does not pull
  the remote back.
- Look settings on a TV: theme and type and motion only.
- Tried on the Mi TV: moving through every strip, settings opened and closed (the remote back on
  its tile), the pictures hub, "continue" playing from where it stopped, music.

## Start's faults, found on the Mi TV (2026-10-19)

- A remote held down lost presses: the strips were a lazy list, and one not laid out yet gave the
  press nowhere to go. All six are laid out now and slid into place.
- Home on Start let go of the focus and gave it to nothing; Home and Back now go to the first
  strip's first tile, as Google TV's Start does.
- The Home key made a second Z Launcher: a HOME intent to an app that is not the TV's home starts
  one of the home kind beside the one opened from the app list. The service now sends a plain
  intent marked `EXTRA_HOME_KEY`.
- The first focus waited for nothing and went to pictures before "continue" was read.
- Apps put on or taken off while away now show or go when Start comes back.
- A film opened from files (a USB stick) now comes up in "continue", with the frame it stopped at.
- The clock follows the TV's 12/24-hour setting; banners are drawn at the tile's size.

## Vista's Media Center, a second design (2026-10-20)

The user asked for Windows Vista's Media Center beside Windows 7's, chosen in settings (look →
media center), copied from a video of Vista's own (youtu.be/PpDe5JZOYCo), Start and every hub.

- `ui/tv/Vista.kt`: the ground (navy, lit middle, rays drawn once and drifting), the pane of glass
  (`drawVistaGlass`), the orb and clock, Selawik (Microsoft's open Segoe UI twin, OFL, in
  `res/font`, licence in `assets/licenses`), `TvDesign` (the choice and the sounds, read at once
  from SharedPreferences so the first frame is right), `VistaSounds` (made here, `res/raw`).
- `ui/tv/VistaStart.kt`: Start - "start" faint at the left, the strips one above another round and
  round, the glass fixed and the things sliding through it, the shutdown tile and its panel (close,
  TV off and sleep through the accessibility service), the program library, the logo at launch.
  Start takes the remote whole; Back and Home go back to "TV + Movies".
- `ui/tv/VistaHub.kt`: a library - its name huge and faint top right, its commands as links at its
  top left, its ways in with the chosen one between chevrons; `VistaPanel` for choices.
- Everything else follows through `LocalVista`: `MediaCenterHub`, `MediaCenterWord`,
  `MediaCenterCommandStrip`, `MediaCenterBackground`, the focus frame (glass), `MetroSubScreen`
  (Vista's settings page), `ZuneFlipDialog` and `MessagingSheet` (glass in the bottom right),
  settings rows (radio and check box before the words), the theme (Selawik, Vista's blue).
- Hubs open and close with Vista's zoom; the remote ticks, chimes and goes back (can be turned off).
- "play all" under pictures and music: a slide show of every picture, every song shuffled.
- A TV is always dark now, in both designs: a light theme put dark words on Media Center's blue.
- Tried on the emulator (the TV was off): every strip, the program library, shutdown, settings and
  their pages, pictures with viewer and panel, music, weather, news, clock, files, switching both ways.

### On the Mi TV (2026-10-20)

- Every strip, the program library (11 apps, "8 | 11"), pictures, settings and switching work.
- Speed: Start was 109 ms a frame while the remote moved - the ground's four full-screen
  gradients, drawn again every frame (77 ms), and the rays' slow drift, which also kept the TV
  drawing while nothing moved. The ground and rays are now one picture drawn once, and still:
  Vista's Start 20 ms a frame at the median, nothing drawn while idle. Windows 7's ground had the
  same fault; drawn once too, its Start went from 48 to 22 ms.
- A library's first focus went to its first link (the launcher's fallback) when the word was
  asked for while the hub was still zooming in; it is now asked for until a word has it.
- The sounds play (the TV's audio service lists Z Launcher's SoundPool starting at each press).
  "Turn off TV" and "sleep" were not pressed: they would have turned the TV off.

## The phone link (2026-10-20)

A phone running Z Launcher tells a paired TV on the same Wi-Fi who is calling or writing; the TV
shows it in its bottom right corner, over whatever is on. No server.

- `data/link/`: `LinkProtocol` (length-prefixed JSON over one short TCP connection, the pairing
  handshake, sealed notices), `LinkCrypto` (P-256 ECDH, HKDF with the 6-digit code mixed in, a
  proof from each side, AES-GCM; keys at rest wrapped by the Android Keystore), `LinkStore`
  (own id, peers, switches; SharedPreferences), `TvLink` (listens on 52741, advertised by NSD as
  `_zlauncher._tcp`; one try per code; drops old or replayed notices), `PhoneLink` (finds TVs by
  NSD or a typed address, pairs, announces to every paired TV), `PhoneLinkReceivers` (PHONE_STATE
  for ringing/answered/missed, SMS_RECEIVED, and other apps' message notifications through the
  existing notification listener), `TvLinkNotices` (the corner window: accessibility overlay or
  "over other apps"; Start shows it when neither is possible).
- Settings → connections: on a TV "phone link" (show notices, add a phone with a code, the phones
  added); on a phone "TV link" (send or not; calls, SMS, other apps, the message text - off by
  default; add a TV; the TVs added).
- Tried with the phone emulator and the Mi TV: pairing by typed address (the emulator cannot see
  the LAN's mDNS), a wrong code refused and the code spent, SMS without and with text, a call
  ringing (gone when answered), a missed call, a notice over YouTube, removing the phone (its
  next message not shown). Not tried: NSD discovery from a real phone, the Windows 7 look of the
  card, other apps' messages (needs notification access on the phone).

## Still to do

1. **A TV tour**, settings for the TV, and the TV quality checklist Play applies.
