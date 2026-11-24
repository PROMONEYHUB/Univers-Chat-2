package com.example.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Lightweight iOS Taptic Engine-inspired haptic feedback helper backed by Android View haptics.
 * Uses system View.performHapticFeedback (no VIBRATE permission required and respects user settings).
 */
class IosHaptics(private val view: View) {

    /** Crisp light tap for buttons, cards, and navigation actions. */
    fun lightTap() {
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            HapticFeedbackConstants.CONTEXT_CLICK
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
        view.performHapticFeedback(code)
    }

    /** Subtle detent tick for segmented tabs, dock navigation, reactions, polls, and swipe thresholds. */
    fun selectionTick() {
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            HapticFeedbackConstants.TEXT_HANDLE_MOVE
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(code)
    }

    /** Medium tactile bump for long-press actions, destructive confirmations, and swipe-to-delete. */
    fun impactMedium() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Affirmative pulse when sending messages, sharing media/polls, or saving changes. */
    fun success() {
        val code = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> HapticFeedbackConstants.CONFIRM
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> HapticFeedbackConstants.CONTEXT_CLICK
            else -> HapticFeedbackConstants.KEYBOARD_TAP
        }
        view.performHapticFeedback(code)
    }
}

@Composable
fun rememberIosHaptics(): IosHaptics {
    val view = LocalView.current
    return remember(view) { IosHaptics(view) }
}
