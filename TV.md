# Zeta on Android TV

Part of the app on `main` since 2026-10-20, when the user put the TV interface into the Play plans:
the same package goes to phones, tablets and Android TV (Play: `PLAY.md`, section 6). Until then
it was kept on a branch of its own (`tv`), out of every release.

## Decided

- **Two designs, chosen in settings.** (2026-10-19: the A board and C list of the draft gone,
  Windows 7's Media Center only; 2026-10-20: Vista's added; later that day Windows 7's replaced
  by the Xbox 360's dashboard and Vista's renamed "Media Center".) Start and every hub are Media Center's; the phone's Start setting, wallpaper, hub
  background, tiles and icons are not offered on a TV, only the accent colour (no light theme: Media Center is blue and white) and type and motion.
- **Worked with a remote.** Every tile and word takes focus; the focused one grows a little and
  gains a white frame. The edges keep the 5% a TV may crop (48 × 27 dp).
- **Phone and messages through the phone.** A phone running Zeta can tell a paired TV on the
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
  the home role given to Zeta, Home still opened `launcherx`).
- So on a TV the accessibility service catches the remote's Home and opens Zeta with the
  Home intent: from another app it comes forward, inside it a hub is put away. Both halves of
  the press are kept, so a long press opens Zeta too, not Google TV's panel.
- On a TV the service is listed as "Zeta Home button" with its own description
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
  launcher, same keys, 61 ms. The TV is slow; Zeta is a little quicker than its own home.
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
- The Home key made a second Zeta: a HOME intent to an app that is not the TV's home starts
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
- The sounds play (the TV's audio service lists Zeta's SoundPool starting at each press).
  "Turn off TV" and "sleep" were not pressed: they would have turned the TV off.

## The phone link (2026-10-20)

A phone running Zeta tells a paired TV on the same Wi-Fi who is calling or writing; the TV
shows it in its bottom right corner, over whatever is on. No server.

- `data/link/`: `LinkProtocol` (length-prefixed JSON over one short TCP connection, the pairing
  handshake, sealed notices), `LinkCrypto` (P-256 ECDH, HKDF with the 6-digit code mixed in, a
  proof from each side, AES-GCM; keys at rest wrapped by the Android Keystore), `LinkStore`
  (own id, peers, switches; SharedPreferences), `TvLink` (listens on 52741, advertised by NSD as
  `_zeta._tcp`; one try per code; drops old or replayed notices), `PhoneLink` (finds TVs by
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

## The Xbox 360 dashboard in place of Windows 7 (2026-10-20)

The user asked to rebuild the Windows 7 design as the Xbox 360's, Start and hubs, "exactly as it
was", and to call Vista's "Media Center". Chosen era: the Metro dashboard of 2011-2013, studied
frame by frame from two videos (IGN's 2012 tour, a 10-minute walkthrough).

- `ui/tv/Xbox.kt`: the ground (measured greys, dark top to light middle, drawn once), the green
  (#009A00, lit #2FB22F), the button legend (Ⓐ select Ⓑ back, commands as further buttons),
  `XboxHub` (name top right, ways in under it at the left, the page on a light panel - the ground
  is darkest where pages begin), `XboxPage` (settings pages: name white top left, page on white),
  `XboxDialogPanel`/`XboxPanel` (white panel in the middle), `XboxSplash` (ribbon and sphere -
  no X, no "XBOX 360": Microsoft's), `XboxClock`.
- `ui/tv/XboxStart.kt`: Start - sections home, tv + movies, music, apps, settings along the top;
  tile blocks laid out in the dashboard's own pixels (home: three down the left, a large one,
  two at the right, three along the foot); neighbours faint at the edges; spatial navigation,
  over the edge into the next section, up onto the words; "my apps" as tall white cards (the
  dashboard's "my pins"); "turn off" as a white menu with a grey pane.
- The Xbox design uses the light theme (dark words), Selawik and its own sounds (`xbox_*.wav`,
  made here). The focus frame is a lift: shadow, green edge. The phone notice is a dark band at
  the bottom in the middle. Windows 7's Start, strips and glow were removed.
- Tried on the Google TV emulator: every section, my apps, settings tiles, turn off, music,
  pictures (with its panel), clock, settings and its pages, switching to Media Center and back,
  the phone notice (paired from a script speaking the link's protocol).
- Then on the Mi TV (woken over the network): Start, every section, my apps (11 apps), pictures,
  files, settings, the notice. Frames while the remote moves: 10 ms at the median, none when idle.
  Found and fixed: up from the large tile went to a small tile beside it instead of the section
  words (only tiles in line count now); a dialog's words were white on the Xbox's white panel
  (dialogs are dark now, as the guide's were); after a dialog took its row away the focus fell on
  the first section word, not the page's. Over other apps the notice needs the Home key service,
  which was off on the TV: it showed only inside the launcher, as the settings say.

## Xbox: the guide, pins, recent, the spotlight, details (2026-10-21)

- `ui/tv/XboxShelf.kt`: `XboxItem` (an app, a hub or the film left part way, by key "app:<pkg>",
  "hub:<place>", "film:continue"; opening one counts it as recent), `XboxShelf` (pins in the
  user's order, recent newest first; SharedPreferences), `XboxGuide` (open or not).
- Home: "my pins" and "recent" down the left with pictures; the large tile is a spotlight turning
  every 7 s through the film left part way, the album playing, the newest photo, the weather and
  the top headline; the foot holds the last two things opened. A press acts on release; held,
  it opens the thing's details page: open, pin to home / unpin, move to front, app info,
  uninstall - or resume and start over for a film.
- `ui/tv/XboxGuidePanel.kt`: the guide - upright tabs (apps, media, settings), a white list with
  the chosen line green. Menu key opens and closes it anywhere in the launcher; Home held for
  half a second (the accessibility service) brings the launcher forward with the guide up.
- TV sounds and settings are a TV's alone now (`TvDesign.onTv`): a phone's keyboard arrows could
  have ticked.
- Tried on the TV emulator: spotlight turns, recent fills, pin/unpin from details (the tile's
  word follows), pins page, guide from Start and from a hub (to pins, to pictures), Home held
  (gRPC key 102) and short. Not tried: the film details (no film on the emulator), a real remote.

## Xbox: search, notices, show / sort, Ⓧ and Ⓨ (2026-10-21)

- `ui/tv/XboxSearch.kt`: search, kept behind home's left edge (a grey tab peeks out; Left from
  the first section, or "search" at the top of the guide). The TV's keyboard types into a white
  box; 250 ms after the last letter it finds apps and hubs by name (those starting with it
  first), then MediaStore albums, songs and videos (12 of each, 40 in all; left out without
  permission). Down goes to the result cards, Up back to the box.
- Cards pages (my apps, my pins, recent) have "⌄ show" and "⌄ sort" at the top, as the
  dashboard's pages did: Up from the cards onto them, a white dropdown with the chosen line green.
  Apps: all / pinned; by name, install date, last used. Pins: all / apps / hubs; your order or by
  name. Recent: all / apps / hubs; newest or by name. Reset each time a page opens; an empty
  result under a filter says so rather than "nothing pinned".
- Ⓧ (gamepad X, the remote's blue key) opens the details of the card or tile the remote is on;
  Ⓨ (gamepad Y, yellow) pins or unpins, and on the pins page moves to front. The legends say so.
- Notices: `LinkNotice.Kind.SYSTEM` and `TvLinkNotices.showSystem` carry the TV's own news through
  the phone-link notice window, with its own icon: pinned / unpinned, and (`TvAppNews`) a TV
  app installed or updated.
- The guide also opens on a gamepad's guide button (BUTTON_MODE).
- Tried on the TV emulator: search (keyboard, a song opening the music hub), sort and show on all
  three pages, Ⓧ and Ⓨ by key 186 / 185, the pin notice and an install notice. Not tried: a real
  gamepad.

## Xbox: visualizer, themes, screensaver, profile, system settings (2026-10-21)

- `ui/tv/TvIdle.kt`: `TvIdle` sees every key first (`MainActivity.dispatchKeyEvent`); while the
  launcher is resumed `TvIdleScreen` (in `LauncherScreen`, under a phone's notice) puts up, after
  the remote rests, the music's own screen (two minutes, while a song plays) or the screensaver
  (`TvSaver`: off / dim with a wandering clock / the TV's pictures, 5-30 minutes). The key that
  wakes it does nothing else; media and volume keys pass. `TvIdleHold` keeps it away (the photo
  viewer's slide show). `TvSleep`: the sleep timer - pause by media key, sleep through the
  accessibility service.
- The visualizer: the cover large and bobbing, the song, the artist and how far in, the cover
  blurred behind (scaled to 12 px and drawn large: no RenderEffect needed) and the artist's name
  huge and faint drifting across. Also from the guide's media tab.
- `LauncherPlayback` (data/service/LocalMediaController.kt): the launcher's own player's state for
  those outside the music hub. The system's session list needs notification access, which a TV
  is seldom given, so the guide, the visualizer and Start's music tile showed nothing of the
  launcher's own music before.
- `XboxTheme` (Xbox.kt): grey, night blue, purple, orange, black, or a picture under a veil. All
  but grey are dark: `Theme.kt` turns the words white, and `xboxPane` makes hubs' panes, the
  Windows 8 bar and menus dark glass to match.
- `ui/tv/XboxSystem.kt`: the system settings page (settings section, and the guide's settings tab)
  - a white list, a grey pane with the choices; theme (with a grid of the TV's pictures),
  screensaver, its delay, visualizer, sleep timer, and the TV's display, sound, network,
  accessories, date, storage, about pages. And `XboxProfile`: the TV's name and the paired phone
  beside a square picture in the top right, reached by Up from the section words; it opens the
  phone link settings.
- The launcher's settings (TV design group) also choose the screensaver and the visualizer, for
  Media Center.
- Tried on the TV emulator: every theme line, the picture grid, hubs on a dark theme (music,
  settings, pictures, news, files), the visualizer from the guide with the launcher's music, the
  pictures and dim screensavers (delay set to a minute through the prefs), the woken key swallowed,
  the sleep timer, Display & Sound opened from "display". Not tried: a cover's blur (no covers on
  the emulator), a real TV.
- Found while testing: the emulator's keyboard, left up when the app was stopped with the search
  box open, kept eating the arrows until it was stopped. An emulator state, not the launcher's.

## An outside review (Gemini, 2026-10-21)

Gemini 4 Argon read the TV / Xbox diff (Antigravity CLI, plan mode, read only) and reported 14
findings. Checked one by one against the code, four were real and are fixed:

- A held centre press on a tile with no details (pins, recent, shuffle, the settings actions, the
  profile) was eaten: `hold()` now says whether it opened anything, and the press stays a press.
- A details page whose "move to front" went once the thing was first, and a page of cards
  shrinking under the remote, left the remote on nothing: `commandAt` / `cardAt` are kept in range.
- The guide key in the video player opened the guide unseen in the launcher behind it: it now
  counts only while the launcher is resumed.
- A dialog is a window of its own and its keys never passed `MainActivity`: `tvIdleKeys()` lets
  them count as the remote in use and wake the screen.

The rest did not hold: code that does not exist (a UDP socket in the phone link, an RSS reader in
`TvAppNews`, visualizer bars), the woken key's release said to slip through (it is swallowed), the
screensaver said to run behind other apps (it waits for `TvIdle.resumed`), a hard-coded channel
name that is not there. Two were left as debatable: the music hub not turning to "albums" when an
album is opened from search on a TV, and a slide show asked for before the pictures can be read
waiting until they can.

### Second round: Gemini on the emulator (2026-10-21)

Gemini drove the TV emulator through eleven scenarios, through `emu.sh` alone (the emulator only,
a few keys, screenshots, the app's log; Home and Power refused), allowed in
`~/.gemini/antigravity-cli/settings.json`. The long run lost its Google token every hour and was
resumed by `--conversation`. What it found and what came of it:

- Fixed: search now folds Turkish letters and other marks ("parca" finds "Parça"; the media store's
  LIKE cannot, so rows are matched in Kotlin - `XboxSearchFoldTest`); the music hub's "now playing"
  on a TV stands cover beside details, as it did not fit the pane's height; the files hub's
  permission title was black on a dark theme and its button under the bar; the cover was read as a
  stream, which newer media stores refuse - `tvArtwork` asks for the album's thumbnail; home showed
  "music" twice; the guide said "playing" over paused music; Ⓧ and Ⓨ are told only where they act
  and Ⓨ says "unpin" on a pinned thing; search opens empty.
- Left: the hubs' command legends overflow on the right and show their menu commands as blank
  grey discs; a news card's source line and the clock's fourth city are cut at the pane's foot
  (the list scrolls); the pictures hub has two "select"s; media3's notification logs the same
  cover error; a handled wallpaper permission warning in the log.

## Still to do

1. **A TV tour**, settings for the TV, and the TV quality checklist Play applies.

## Into Play (2026-10-20)

- `tv` merged into `main`; the manifest's TV entries (leanback and touchscreen not required,
  `LEANBACK_LAUNCHER`, banner) now ship to everyone.
- The phone link no longer asks for the call log or SMS: Play allows them only to the default
  phone and SMS apps. Without those roles it reads calls and texts from the phone and SMS apps'
  notifications (`PhoneLinkCalls`, `PhoneLinkAppMessages`), asking only for phone state, contacts
  and notification access. Tried on the emulator with both permissions revoked: an SMS came through
  with its sender's name; a call with a name only when the phone app posts an incoming-call
  notification (Google's and Samsung's do; the old Zeta dialer on the emulator does not).
- The TV's accessibility description and disclosure name all three of its uses (Home key, the
  shutdown panel's power menu and sleep, the phone notice over other apps).
- A hub whose words come late (Pictures) now gets the remote on them, not on its first link.
- Store: `store/tv-banner.png`, `store/screenshots-tv/`, TV text in `STORE.md`; README, site and
  privacy policy (1.4) describe the TV and the phone link.

