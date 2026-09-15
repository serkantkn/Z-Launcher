package com.serkantkn.zunelauncher.util

/**
 * What a person is shown the first time the launcher opens, and what they are told after it has
 * been updated.
 *
 * The decisions are here, apart from Compose and Android, because they are the sort of thing that
 * is easy to get subtly wrong and impossible to notice: showing somebody a tour they have already
 * taken, or worse, showing a brand new user a list of things that changed in a version they never
 * used. Both are one boolean away, and both can be tested.
 */

/**
 * The pages of the tour, in the order they are shown.
 *
 * Language and the look come before the greeting on purpose: everything after them is then in the
 * language and the colour somebody chose, so the tour itself is the first thing to show that the
 * choice took.
 */
enum class OnboardingStep {
    LANGUAGE,
    THEME,
    WELCOME,
    DEFAULT_LAUNCHER,
    PERMISSIONS,
    GESTURE_SWIPE,
    GESTURE_TILE,
    GESTURE_PIVOT,
    DONE;

    /** Whether this page is a lesson, which is played on a full-size copy of the launcher. */
    val isLesson: Boolean
        get() = this == GESTURE_SWIPE || this == GESTURE_TILE || this == GESTURE_PIVOT
}

/** What the phone already is, so the tour can leave out what is already settled. */
data class OnboardingConditions(
    val isDefaultLauncher: Boolean = false,
    /** Whether anything is still worth asking for; the hubs ask for the rest themselves. */
    val hasPermissionsToAsk: Boolean = true
)

/**
 * The pages worth showing.
 *
 * A step that has nothing to do is left out rather than shown as already done: a page that says
 * "this is already set up, press next" is a page that should not have been there.
 */
fun onboardingSteps(conditions: OnboardingConditions): List<OnboardingStep> = buildList {
    add(OnboardingStep.LANGUAGE)
    add(OnboardingStep.THEME)
    add(OnboardingStep.WELCOME)
    if (!conditions.isDefaultLauncher) add(OnboardingStep.DEFAULT_LAUNCHER)
    if (conditions.hasPermissionsToAsk) add(OnboardingStep.PERMISSIONS)
    // Three lessons rather than one page of instructions: the only way anybody learns a gesture
    // is by making it.
    add(OnboardingStep.GESTURE_SWIPE)
    add(OnboardingStep.GESTURE_TILE)
    add(OnboardingStep.GESTURE_PIVOT)
    add(OnboardingStep.DONE)
}

// -- What happens on the very first run of a build ---------------------------

/** What the launcher should do when it starts. */
enum class FirstRunAction {
    /** A new installation: take them through the tour. */
    SHOW_ONBOARDING,

    /** An existing installation that has just been updated: tell them what changed. */
    SHOW_WHATS_NEW,

    /** Nothing to say. */
    NOTHING
}

/**
 * Which of the three it is.
 *
 * Two awkward cases, and they pull in opposite directions.
 *
 * The first is an installation that predates any of this being recorded: no flag saying the tour
 * was taken, but the launcher plainly has been used. Treating that as new would drag somebody who
 * has used it for months through a beginner's tour, so the question asked is not "is the flag set"
 * but "is there anything here at all" - [hasExistingData].
 *
 * The second is a tour that was interrupted. Making the launcher the default home app restarts it,
 * and by then the tour has written a setting or two, so "is there anything here" would say used
 * and abandon somebody in the middle. [onboardingStarted] settles that: once the tour has begun it
 * is finished or skipped, never quietly dropped.
 */
fun firstRunAction(
    onboardingCompleted: Boolean,
    onboardingStarted: Boolean,
    hasExistingData: Boolean,
    lastSeenVersion: Int,
    currentVersion: Int,
    hasUnseenNotes: Boolean
): FirstRunAction = when {
    // Already been through it: the only thing left to say is what changed.
    onboardingCompleted ->
        if (lastSeenVersion < currentVersion && hasUnseenNotes) {
            FirstRunAction.SHOW_WHATS_NEW
        } else {
            FirstRunAction.NOTHING
        }

    // Halfway through, and something restarted the launcher. Pick it back up.
    onboardingStarted -> FirstRunAction.SHOW_ONBOARDING

    // Never toured, and nothing here: a fresh installation.
    !hasExistingData -> FirstRunAction.SHOW_ONBOARDING

    // Never toured, but plainly used: an installation from before any of this existed. It is not
    // shown the tour, and it is not shown a pile of notes for versions it already lived through.
    else -> FirstRunAction.NOTHING
}

// -- What changed ------------------------------------------------------------

/**
 * One version's worth of changes.
 *
 * The text is held as string resource ids rather than strings so the notes are translated like
 * everything else; nothing here needs to know what they say.
 */
data class ReleaseNote(
    val version: Int,
    val titleRes: Int,
    val lineRes: List<Int>
)

/**
 * The notes somebody has not read yet, newest first.
 *
 * Versions at or below [lastSeenVersion] are gone for good - a person who has read them once does
 * not want them again - and anything claiming to be from the future is ignored rather than shown,
 * since that only happens when a build has been rolled back.
 */
fun unseenReleaseNotes(
    notes: List<ReleaseNote>,
    lastSeenVersion: Int,
    currentVersion: Int
): List<ReleaseNote> = notes
    .filter { it.version > lastSeenVersion && it.version <= currentVersion }
    .sortedByDescending { it.version }
