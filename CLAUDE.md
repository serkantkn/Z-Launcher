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

The two flavours share one codebase; what is Pro's goes through `util/Pro.kt` (`Pro.unlocked`,
`Pro.tell`, `Pro.orTell`) and, in settings, `ui/components/ProLock.kt` (`ProLocked { }`).

- A Pro setting is gated where it is read (the DataStore flow returns the free default), never
  deleted: the stored value waits for Pro.
- Nothing of the user's is locked away: the free build may not make a new locked note or add to
  the reading list, but what exists still opens, reads and can be removed.
- The README's "Ücretsiz ve Pro" section and the site list what is Pro's; keep them in step.

## Build and test

- Build: `./gradlew :app:assemblePremiumDebug`
- APK: `app/build/outputs/apk/premium/debug/app-premium-debug.apk`
- Unit tests: `./gradlew :app:testPremiumDebugUnitTest`
