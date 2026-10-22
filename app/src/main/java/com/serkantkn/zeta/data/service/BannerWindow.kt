package com.serkantkn.zeta.data.service

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.WindowCompat
import com.serkantkn.zeta.util.ZuneLog
import kotlin.math.roundToInt

/**
 * The window a pop-up card - a notification, the volume bar - stands in over other apps.
 *
 * A dialog's window rather than a bare view handed to the window manager, because only a Window
 * can blur what is behind it (Window.setBackgroundBlurRadius, Android 12 and later): when the
 * tiles are frosted, the card is frosted over whatever app is open, as far as the phone allows
 * (it may not: an older Android, battery saver, a phone that turns window blurs off). The window
 * is the card's size and place, so the blur is the card's; [setShown] fades it with the card as
 * the card flips in and out or is swiped away.
 *
 * Not focusable unless asked: the app underneath keeps its keyboard and Back, and touches beside
 * the card go to it.
 */
internal class BannerWindow(host: Context, type: Int) {

    private val dialog = Dialog(host, android.R.style.Theme_Translucent_NoTitleBar).apply {
        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }
    private val window = dialog.window!!
    private var blurPx = 0
    private var shown = 0f

    init {
        window.setType(type)
        window.setWindowAnimations(0)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setDimAmount(0f)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        // The card reads the status bar's height itself, as it does in the launcher.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setFocusable(false)
        place(wideWidthPx = null, endPx = 0, topPx = 0)
    }

    /**
     * Where the window stands: across the top on a phone, or - [wideWidthPx] given, on a tablet - a
     * card of that width at the top right, [endPx] from the edge and [topPx] down.
     */
    fun place(wideWidthPx: Int?, endPx: Int, topPx: Int) {
        window.attributes = window.attributes.apply {
            width = wideWidthPx ?: WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = if (wideWidthPx != null) Gravity.TOP or Gravity.END else Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = if (wideWidthPx != null) endPx else 0
            y = if (wideWidthPx != null) topPx else 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    /**
     * Places it as the card [cardWidthDp] wide on a wide screen (a tablet, a phone on its side wide
     * enough) - top right, as the launcher draws it - or across the top otherwise.
     */
    fun placeAsCard(context: Context, cardWidthDp: Int) {
        val config = context.resources.configuration
        val density = context.resources.displayMetrics.density
        if (config.screenWidthDp < WIDE_DP) {
            place(wideWidthPx = null, endPx = 0, topPx = 0)
            return
        }
        val statusBar = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it != 0 }?.let { context.resources.getDimensionPixelSize(it) } ?: 0
        place(
            wideWidthPx = (cardWidthDp * density).roundToInt(),
            endPx = (CARD_MARGIN_DP * density).roundToInt(),
            topPx = statusBar + (CARD_MARGIN_TOP_DP * density).roundToInt()
        )
    }

    fun show(content: View) {
        dialog.setContentView(
            content,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        dialog.show()
        // Showing lays the window out by its theme; it is the card's size again.
        window.attributes = window.attributes.apply { height = WindowManager.LayoutParams.WRAP_CONTENT }
    }

    /** Focusable while something in the card is typed into (a notification's quick reply). */
    fun setFocusable(focusable: Boolean) {
        val flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        window.setFlags(flags, FLAG_MASK)
        if (focusable) window.decorView.requestFocus()
    }

    /** Whether this phone blurs behind windows at all just now. */
    val blursBehind: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && runCatching {
            window.windowManager.isCrossWindowBlurEnabled
        }.getOrDefault(false)

    /** The blur behind the card at full show, in pixels; 0 for none. */
    fun setFrost(px: Int) {
        blurPx = px
        applyBlur()
    }

    /** How much of the card shows, 0-1: the blur goes with it. */
    fun setShown(fraction: Float) {
        shown = fraction.coerceIn(0f, 1f)
        applyBlur()
    }

    private fun applyBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching { window.setBackgroundBlurRadius((blurPx * shown).roundToInt()) }
            .onFailure { ZuneLog.w(TAG, "the window could not be blurred", it) }
    }

    fun dismiss() {
        runCatching { if (dialog.isShowing) dialog.dismiss() }
            .onFailure { ZuneLog.e(TAG, "dismiss failed", it) }
    }

    private companion object {
        const val TAG = "BannerWindow"
        /** As ZuneLauncherTheme tells a wide screen. */
        const val WIDE_DP = 600
        const val CARD_MARGIN_DP = 16
        const val CARD_MARGIN_TOP_DP = 12
        const val FLAG_MASK = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_DIM_BEHIND
    }
}
