package com.metaself.app.ui.theme

import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * What the hand feels, and when (public issue #16).
 *
 * Both go through Compose's `LocalHapticFeedback`, which calls `View.performHapticFeedback`. That
 * needs no `VIBRATE` permission, and it obeys the phone's own touch-feedback setting — so someone
 * who has turned vibration off for touches gets none from this app either.
 *
 * **Not gated by the day or by "Remove animations".** A haptic is not motion, and the past being
 * still is about what the screen does, not about whether the owner's own press is acknowledged.
 */
object Feel {

    /** A long press that starts a choice, or takes a whole meal into one: firm, unmistakable. */
    val Thump: HapticFeedbackType = HapticFeedbackType.LongPress

    /**
     * A row ticked or unticked while choosing: light. Android's text-handle tick, the lightest
     * feel Compose offers here. It exists from Android 8.1 (API 27); on 8.0 the phone does nothing.
     */
    val Tick: HapticFeedbackType = HapticFeedbackType.TextHandleMove
}
