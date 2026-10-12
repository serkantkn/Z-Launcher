package com.serkantkn.zunelauncher

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps every hub on the hub standard (`ui/components/ZuneHub.kt`): a hub says what it has and
 * ZuneHub lays it out - as a pivot with Windows Phone's bar on a phone, as headings with Windows
 * 8's bar on a tablet. A screen that builds either layout for itself has stepped off the
 * standard, and the two would start to drift apart again; this fails the build when one does.
 */
class HubStandardTest {

    private val screens = File("src/main/java/com/serkantkn/zunelauncher/ui/screens")

    /** The pieces ZuneHub puts together; a screen calling one of these is laying out a hub itself. */
    private val layoutPieces = listOf("ZuneWideNavHub(", "ZuneLoopingPager(", "ZuneWidePanorama(", "ZuneWideHubTitle(")

    /**
     * Screens allowed to lay out their own, each for a reason. Taking a hub off this list is the
     * way it joins the standard; nothing new is added to it.
     */
    private val exceptions = mapOf(
        "weather/WeatherHubScreen.kt" to "still the old panorama on a tablet; joins with its tablet redesign",
        "files/FilesHubScreen.kt" to "a Windows 8 file explorer on a tablet, not a hub of headings",
        "onboarding/OnboardingLessons.kt" to "a copy of a pivot for the lesson on swiping between pages",
        "onboarding/OnboardingTabletLessons.kt" to "a copy of a tablet hub for the lesson on its headings"
    )

    private fun sources(): List<File> =
        screens.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun File.key() = relativeTo(screens).path.replace(File.separatorChar, '/')

    @Test
    fun theScreensAreFound() {
        assertTrue("no screens under ${screens.absolutePath}", sources().size > 20)
    }

    @Test
    fun noScreenLaysOutAHubItself() {
        val offenders = sources()
            .filter { it.key() !in exceptions }
            .mapNotNull { file ->
                val text = file.readText()
                layoutPieces.filter { it in text }.takeIf { it.isNotEmpty() }?.let { "${file.key()}: $it" }
            }
        assertTrue(
            "These screens lay out a hub themselves instead of through ZuneHub:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun aPivotIsOnlyEverAHubsOwnHeader() {
        // A pivot is allowed only as a hub's own phone header handed to ZuneHub (people's giant
        // sliding name, the pictures' zooming album heading).
        val offenders = sources()
            .filter { it.key() !in exceptions }
            .filter { file -> file.readText().let { "ZunePivotTabs(" in it && "ZuneHub(" !in it } }
            .map { it.key() }
        assertTrue(
            "These screens draw a pivot outside ZuneHub:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun theExceptionsStillNeedToBeExceptions() {
        // A hub that has joined the standard comes off the list, so the list never hides one.
        val stale = exceptions.keys.filter { key ->
            val file = File(screens, key)
            !file.exists() || layoutPieces.none { it in file.readText() } && "ZunePivotTabs(" !in file.readText()
        }
        assertTrue("No longer exceptions, take them off the list: $stale", stale.isEmpty())
    }
}
