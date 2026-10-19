# Z Launcher on Android TV

This branch (`tv`) only. Nothing here goes into the GitHub beta or the Play release until it is
decided otherwise; releases are built from `main`, and `main` is merged into this branch, not the
other way round.

## Decided

- **Two Starts, as on the phone.** The setting that picks the Windows Phone board or the Zune list
  picks, on a TV:
  - **A, the board** (`HomeScreenLayout.WINDOWS_PHONE`): the Xbox 360's - section names along the
    top, large tiles running off to the right.
  - **C, the list** (`HomeScreenLayout.ZUNE`): Windows Media Center's and the Zune's - huge words
    down the left, what the chosen one holds beside it.
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
- `ui/tv/TvStartScreen.kt`: both Starts, the clock, the TV's own apps by their banners
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

## Still to do

1. **The phone link**: pairing, discovery (NSD), the encrypted channel, the corner notice (needs
   "display over other apps" on the TV), the settings on both sides.
2. **A TV tour**, settings for the TV, and the TV quality checklist Play applies.
