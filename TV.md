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

Known gaps: back from a hub's page the focus lands on the hub's heading, not on the row that
opened the page; text fields need checking with the TV's on-screen keyboard.

## Still to do

1. **Which hubs.** Videos, music, pictures, files, weather, news, clock and settings stay; phone,
   messaging, camera, the action center, the volume bar and the keyboard are left out on a TV.
2. **The Start's live content**: what was last watched in the launcher's own player, the album
   playing, the weather; other apps' "continue watching" is not readable by a launcher.
3. **Being Home.** Google TV keeps the Home button for its own launcher; on those, Z Launcher is an
   app in the row unless the user makes it Home by adb. Older Android TV boxes let it be chosen.
4. **The phone link**: pairing, discovery (NSD), the encrypted channel, the corner notice (needs
   "display over other apps" on the TV), the settings on both sides.
5. **A TV tour**, settings for the TV, and the TV quality checklist Play applies.
