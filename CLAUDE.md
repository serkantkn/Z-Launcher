# Zeta

## Hubs follow one standard

Every hub is laid out by `ZuneHub` (`app/src/main/java/com/serkantkn/zeta/ui/components/ZuneHub.kt`).
A hub supplies its title, headings, pages, bar commands and, on a tablet, what opens in the pane;
`ZuneHub` picks the layout:

- **Phone:** small title, pivot headings, swiped pages, and the Windows Phone bar. The one exception
  is the people hub, which stands as Windows Phone 7's did - a panorama, its name giant across the
  top and each section under its own heading - through `ZuneHub(phonePanorama = true)`; it also
  comes in on "recent" and glides round to "all" (`glideFrom`). No other hub uses either.
- **Tablet:** small title at the top left, headings down the left, the page beside them, an optional
  pane for whatever is open, and the Windows 8 bar.
- **Zune layout, phone:** every hub but the camera - music with its own menu - stands under a
  huge, bold, slightly see-through title across the top that runs off the right edge and slides
  left as the pages turn; touching it goes back. The name touched in the Zune list flies up to
  become it (`ui/animation/ZuneHubFlight.kt`), and when the hub closes the title flies back down
  onto that name (`closeHub`, called from `LauncherScreen`) as the hub fades and the list comes in.
  Weather and files, which lay themselves out, draw it with `ZuneTitledHeader`. Pages opened inside those hubs from
  a name - a conversation, a contact, a group, an album, a music list - do the same and send the
  title back down on closing: mark the name with `Modifier.zuneFlightLabel(id, ...)`, call
  `openPage`/`closePage`, and give the page a `ZuneHubFlightTitle` with the same key. A hub opts in with `ZuneHub(zuneTitleKey = ...)`;
  the people hub then has no panorama and opens on "all".

- **TV:** Android TV and Google TV are Windows Media Center (`ui/tv/`, notes in `TV.md`); `ZuneHub`
  draws `MediaCenterHub`, which is the Xbox 360's (`XboxHub`) or, with Media Center chosen
  (`LocalVista`), Vista's (`VistaHub`).
  Only the hubs marked `HubType.onTelevision` are offered there, and everything works by remote.

When writing a hub:

- Do not call `ZunePivotTabs`, `ZuneWideNavHub`, `ZuneLoopingPager`, `ZuneWidePanorama` or
  `ZuneWideHubTitle` from a screen. Use the `ZuneHub` parameters instead: `phoneHeader`,
  `phoneAboveContent`, `takeover`, `wideNavExtra`, `wideSidePane`, `widePane` and `overlay`.
- Give bar commands as `ZuneHubBar(actions, menu)`. Sub-screens still use `WindowsPhoneBottomBar`,
  which draws itself as the Windows 8 bar on a tablet.
- Anything that has to sit just above the bar asks `bottomBarHeight()`. Do not hard-code 56dp.
- Phone and tablet carry the same features; only the layout differs.

`HubStandardTest` fails the build when a screen lays out a hub itself. Its exception list holds
two hubs that stay off the standard by choice: weather, which keeps its panorama on a tablet (one
sky behind every section side by side), and files, which is a Windows 8 explorer. Nothing new is
added to that list.

## Names the user sees

The product is **Zeta** - an interface suite for phone, tablet and TV, not only a launcher -
package `com.serkantkn.zeta` (`.dev` for `premium`); `R.string.brand_name` is its name whatever the build
(the TV's opening logos and shutdown panels show it with `ZetaMark`). The designs have names of their own. In everything a user can read - the app's strings, the store
text, README, the site, PRIVACY, images and the promo video - they are **Aura** (the Zune layout),
**Mozaik / Mosaic** (Windows Phone tiles), **Panorama** (the tablet's Windows 8 Start), **Konsol /
Console** (the TV's Xbox 360 design) and **Salon / Lounge** (the TV's Media Center design). Zune,
Windows, Windows Phone, Xbox, Media Center, Metro, Vista and Segoe are Microsoft's and appear in none
of it; describe what a thing does ("pop-up card", "its own volume bar") rather than where it came
from. Code names (classes, files, `ZuneHub`, `XboxStart`, `LocalVista`) and developer notes such as
this file and `TV.md` keep the old words. The Fluent UI icons' MIT attribution stays in the README.

## Free and Pro

The `free` flavour is the real app, `com.serkantkn.zeta`, on Play and GitHub; `premium`
is a development build, `com.serkantkn.zeta.dev`, "Zeta Dev". Pro is bought once inside the app through Google Play (`data/billing/ProStore.kt`, product id
`z_launcher_pro`); the `premium` flavour is always Pro. Whether it is Pro can change while running,
so never read `BuildConfig.IS_PREMIUM`: use `util/Pro.kt` - `Pro.unlocked` while drawing (it
recomposes), `Pro.flow` to combine with flows, `Pro.tell`/`Pro.orTell` to send the user to the Pro
page - and, in settings, `ui/components/ProLock.kt` (`ProLocked { }`).

- An install from Google Play is Pro for its first 10 days (`data/billing/ProTrial.kt`); `Pro.unlocked`
  and `Pro.flow` include the trial, `Pro.purchased` does not. Because everyone passes through Pro,
  every Pro setting must be gated where it is read - one that is only locked in its settings page
  stays on after the trial.
- A Pro setting is gated where it is read (the DataStore flow returns the free default), never
  deleted: the stored value waits for Pro.
- Nothing of the user's is locked away: the free build may not make a new locked note or add to
  the reading list, but what exists still opens, reads and can be removed.
- The README's "Ücretsiz ve Pro" section and the site list what is Pro's; keep them in step.

## Build and test

- Build: `./gradlew :app:assemblePremiumDebug`
- APK: `app/build/outputs/apk/premium/debug/app-premium-debug.apk`
- Unit tests: `./gradlew :app:testPremiumDebugUnitTest`
