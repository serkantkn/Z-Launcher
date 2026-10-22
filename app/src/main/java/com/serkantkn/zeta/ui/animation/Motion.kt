package com.serkantkn.zeta.ui.animation

import android.content.ContentResolver
import android.provider.Settings
import android.view.View
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.PausableMonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.compositionContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * How long Zeta's motion takes against the durations written in the code: every animation - the
 * turnstile, the hinges, the flights, the tiles turning over - runs at this share of its written
 * length. One number, so the whole interface quickens or slows together.
 */
const val MOTION_SPEED = 0.8f

/**
 * [MOTION_SPEED] on top of the phone's own animator duration scale (developer options), which a
 * person who slowed or switched off animations still gets.
 */
private class ZetaMotionScale(private val resolver: ContentResolver) : MotionDurationScale {
    override val scaleFactor: Float
        get() = MOTION_SPEED * runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
}

/**
 * Gives the Compose content under this view (an activity's decor view, an overlay's ComposeView)
 * a recomposer whose animations run at [MOTION_SPEED]. Compose reads an animation's speed from
 * the coroutine it runs in, and every effect and remembered scope inherits the recomposer's, so
 * this is the one place that reaches them all. The frame clock stops while [lifecycle] is stopped,
 * as the default recomposer's does: nothing animates behind a screen nobody sees.
 *
 * Call before `setContent`.
 */
fun View.installZetaMotion(lifecycle: Lifecycle) {
    val ui = AndroidUiDispatcher.CurrentThread
    val clock = ui[MonotonicFrameClock]?.let { PausableMonotonicFrameClock(it) }
    val context = ui + ZetaMotionScale(context.contentResolver) + (clock ?: EmptyCoroutineContext)
    val recomposer = Recomposer(context)
    val scope = CoroutineScope(context)
    compositionContext = recomposer
    if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
        recomposer.cancel()
        return
    }
    lifecycle.addObserver(LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> clock?.resume()
            Lifecycle.Event.ON_STOP -> clock?.pause()
            Lifecycle.Event.ON_DESTROY -> {
                recomposer.cancel()
                scope.cancel()
            }
            else -> Unit
        }
    })
    scope.launch(start = CoroutineStart.UNDISPATCHED) { recomposer.runRecomposeAndApplyChanges() }
}

/**
 * A pause that keeps time with the animations around it - waiting for a turn to finish before the
 * next begins - and so runs at the same speed they do. Not for clocks and timeouts: those keep
 * real time.
 */
suspend fun motionDelay(millis: Long) {
    val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    delay((millis * scale).toLong())
}
