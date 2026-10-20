# Z Launcher

## Hubs follow one standard

Every hub is laid out by `ZuneHub` (`app/src/main/java/com/serkantkn/zunelauncher/ui/components/ZuneHub.kt`).
A hub supplies its title, headings, pages, bar commands and, on a tablet, what opens in the pane;
`ZuneHub` picks the layout:

- **Phone:** small title, pivot headings, swiped pages, and the Windows Phone bar. The one exception
  is the people hub, which stands as Windows Phone 7's did - a panorama, its name giant across the
  top and each section under its own heading - through `ZuneHub(phonePanorama = true)`; it also
  comes in on "recent" and glides round to "all" (`glideFrom`). No other hub uses either.
- **Tablet:** small title at the top left, headings down the left, the page beside them, an optional
  pane for whatever is open, and the Windows 8 bar.
- **Zune layout, phone:** the main hubs - phone, people, messaging, pictures, and music with its
  own menu - stand under a huge, bold, slightly see-through title across the top that runs off the
  right edge and slides left as the pages turn; touching it goes back. The name touched in the Zune
  list flies up to become it (`ui/animation/ZuneHubFlight.kt`). Pages opened inside those hubs from
  a name - a conversation, a contact, a group, an album, a music list - do the same and send the
  title back down on closing: mark the name with `Modifier.zuneFlightLabel(id, ...)`, call
  `openPage`/`closePage`, and give the page a `ZuneHubFlightTitle` with the same key. A hub opts in with `ZuneHub(zuneTitleKey = ...)`;
  the people hub then has no panorama and opens on "all".

- **TV:** Android TV and Google TV are Windows Media Center (`ui/tv/`, notes in `TV.md`); `ZuneHub`
  draws `MediaCenterHub`, which is Windows 7's or, with Vista chosen (`LocalVista`), `VistaHub`.
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

## Free and Pro

The `free` flavour is the real app, `com.serkantkn.zunelauncher`, on Play and GitHub; `premium`
is a development build, `com.serkantkn.zunelauncher.dev`, "Z Launcher Dev". Pro is bought once inside the app through Google Play (`data/billing/ProStore.kt`, product id
`z_launcher_pro`); the `premium` flavour is always Pro. Whether it is Pro can change while running,
so never read `BuildConfig.IS_PREMIUM`: use `util/Pro.kt` - `Pro.unlocked` while drawing (it
recomposes), `Pro.flow` to combine with flows, `Pro.tell`/`Pro.orTell` to send the user to the Pro
page - and, in settings, `ui/components/ProLock.kt` (`ProLocked { }`).

- A Pro setting is gated where it is read (the DataStore flow returns the free default), never
  deleted: the stored value waits for Pro.
- Nothing of the user's is locked away: the free build may not make a new locked note or add to
  the reading list, but what exists still opens, reads and can be removed.
- The README's "Ücretsiz ve Pro" section and the site list what is Pro's; keep them in step.

## Build and test

- Build: `./gradlew :app:assemblePremiumDebug`
- APK: `app/build/outputs/apk/premium/debug/app-premium-debug.apk`
- Unit tests: `./gradlew :app:testPremiumDebugUnitTest`
