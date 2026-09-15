package com.serkantkn.zunelauncher.ui.screens.onboarding

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.ReleaseNotes
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.util.FirstRunAction
import com.serkantkn.zunelauncher.util.OnboardingConditions
import com.serkantkn.zunelauncher.util.OnboardingStep
import com.serkantkn.zunelauncher.util.ReleaseNote
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.firstRunAction
import com.serkantkn.zunelauncher.util.onboardingSteps
import com.serkantkn.zunelauncher.util.unseenReleaseNotes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the launcher is showing over itself, if anything. */
sealed interface FirstRunScreen {
    data object None : FirstRunScreen

    data class Tour(val steps: List<OnboardingStep>, val index: Int) : FirstRunScreen {
        val step: OnboardingStep get() = steps.getOrElse(index) { OnboardingStep.DONE }
        val isFirst: Boolean get() = index == 0
        val isLast: Boolean get() = index >= steps.lastIndex
    }

    data class WhatsNew(val notes: List<ReleaseNote>) : FirstRunScreen
}

/**
 * The tour, and the note about what changed.
 *
 * Both are decided once, when the launcher starts, and then stay put: a tour that rearranges its
 * own pages while somebody is walking through them would be a strange thing to be inside.
 */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = application.appContainer.settingsDataStore

    private val _screen = MutableStateFlow<FirstRunScreen>(FirstRunScreen.None)
    val screen: StateFlow<FirstRunScreen> = _screen.asStateFlow()

    init {
        decideWhatToShow()
    }

    private fun decideWhatToShow() {
        viewModelScope.launch {
            // Read before anything else writes a default, or every installation looks used.
            val hasExistingData = runCatching { settings.hasExistingData() }.getOrDefault(true)
            val completed = runCatching { settings.onboardingCompleted.first() }.getOrDefault(true)
            val lastSeen = runCatching { settings.lastSeenVersion.first() }.getOrDefault(currentVersion())
            val unseen = unseenReleaseNotes(ReleaseNotes.ALL, lastSeen, currentVersion())

            val action = firstRunAction(
                onboardingCompleted = completed,
                hasExistingData = hasExistingData,
                lastSeenVersion = lastSeen,
                currentVersion = currentVersion(),
                hasUnseenNotes = unseen.isNotEmpty()
            )

            // Worth a line: it explains something the person sees, and there is no other way to
            // tell afterwards why they were or were not shown anything.
            ZuneLog.d(TAG, "first run: completed=$completed existing=$hasExistingData lastSeen=$lastSeen action=$action")
            when (action) {
                FirstRunAction.SHOW_ONBOARDING -> startTour()
                FirstRunAction.SHOW_WHATS_NEW -> _screen.value = FirstRunScreen.WhatsNew(unseen)
                FirstRunAction.NOTHING -> {
                    _screen.value = FirstRunScreen.None
                    // An installation from before any of this was recorded is caught up quietly,
                    // so the question is asked once and never again.
                    if (!completed) {
                        settings.setOnboardingCompleted(true)
                        settings.setLastSeenVersion(currentVersion())
                    }
                }
            }
        }
    }

    /** Opens the tour by hand, from settings. */
    fun startTour() {
        _screen.value = FirstRunScreen.Tour(steps = onboardingSteps(conditions()), index = 0)
    }

    private fun conditions(): OnboardingConditions {
        val context = getApplication<Application>()
        return OnboardingConditions(
            isDefaultLauncher = isDefaultLauncher(),
            hasPermissionsToAsk = missingPermissions(context).isNotEmpty()
        )
    }

    /** Whether the home button already opens this launcher. */
    fun isDefaultLauncher(): Boolean = runCatching {
        val context = getApplication<Application>()
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        resolved?.activityInfo?.packageName == context.packageName
    }.getOrDefault(false)

    fun next() {
        val tour = _screen.value as? FirstRunScreen.Tour ?: return
        if (tour.isLast) finish() else _screen.value = tour.copy(index = tour.index + 1)
    }

    fun back() {
        val tour = _screen.value as? FirstRunScreen.Tour ?: return
        if (!tour.isFirst) _screen.value = tour.copy(index = tour.index - 1)
    }

    /** Ends the tour, whether it was walked through or skipped. */
    fun finish() {
        viewModelScope.launch {
            runCatching {
                settings.setOnboardingCompleted(true)
                // Somebody who has just installed this version does not need to be told what
                // changed in it.
                settings.setLastSeenVersion(currentVersion())
            }.onFailure { ZuneLog.w(TAG, "the tour could not be marked as taken", it) }

            _screen.value = FirstRunScreen.None
        }
    }

    /** Closes the note about what changed, and remembers that it was read. */
    fun dismissWhatsNew() {
        viewModelScope.launch {
            runCatching { settings.setLastSeenVersion(currentVersion()) }
            _screen.value = FirstRunScreen.None
        }
    }

    private fun currentVersion(): Int = runCatching {
        val context = getApplication<Application>()
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }.getOrDefault(1)

    private companion object {
        const val TAG = "OnboardingViewModel"
    }
}

/**
 * The handful worth asking for up front.
 *
 * Everything else the launcher can ask for - photos, music, files, the calendar - belongs to a
 * hub, and every one of those hubs already asks when it is opened. Asking for all of it on the
 * first screen would be a wall of dialogs before anybody has seen anything.
 */
fun missingPermissions(context: android.content.Context): List<OnboardingPermission> =
    OnboardingPermission.entries.filter { permission ->
        permission.appliesHere() && ContextCompat.checkSelfPermission(
            context,
            permission.manifestName
        ) != PackageManager.PERMISSION_GRANTED
    }

/** One thing the start screen itself needs, and the plain reason for it. */
enum class OnboardingPermission(
    val manifestName: String,
    val titleRes: Int,
    val reasonRes: Int,
    private val minimumSdk: Int = 1
) {
    NOTIFICATIONS(
        android.Manifest.permission.POST_NOTIFICATIONS,
        R.string.onboarding_perm_notifications,
        R.string.onboarding_perm_notifications_why,
        Build.VERSION_CODES.TIRAMISU
    ),
    LOCATION(
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        R.string.onboarding_perm_location,
        R.string.onboarding_perm_location_why
    ),
    CONTACTS(
        android.Manifest.permission.READ_CONTACTS,
        R.string.onboarding_perm_contacts,
        R.string.onboarding_perm_contacts_why
    );

    /** Whether this phone is new enough to have the permission at all. */
    fun appliesHere(): Boolean = Build.VERSION.SDK_INT >= minimumSdk
}
