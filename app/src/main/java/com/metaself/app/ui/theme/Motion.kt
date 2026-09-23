package com.metaself.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * When the app moves, and for how long (public issue #16).
 *
 * **Today moves, the past is still.** A day that is still going answers what was just done to it:
 * the figure rolls to its new value, the rule fills. A day that is over is a record, and a record
 * arrives motionless — the same reasoning as D14, which never colours or nags the past.
 *
 * **Nothing moves when the system says not to.** Android's "Remove animations" accessibility switch
 * sets the animator duration scale to 0, and this app reads that as "still", everywhere.
 *
 * Motion is short and settles without overshoot: a figure that bounced past its value on the way
 * to it would, for a frame, be showing a number that is not the day's.
 */
object Motion {

    /** How long a pressed row takes to sink, and to come back. */
    const val PRESS_MILLIS = 100

    /** How long a figure or a rule takes to arrive at a new value. */
    const val SETTLE_MILLIS = 300

    /**
     * How far a pressed row gives under the finger: to 98% of its size. A chosen value, not a
     * measurement — enough to be seen under a thumb, not enough to move the words beside it.
     */
    const val PRESSED_SCALE = 0.98f

    /** Material's standard easing: quick away, gentle arrival, no overshoot. */
    val Easing = FastOutSlowInEasing

    /**
     * Whether the system allows motion at all, from Android's animator duration scale.
     *
     * 0 is "Remove animations". Anything that is not a positive number is treated the same way: a
     * setting that cannot be read as a speed is not permission to move.
     */
    fun systemAllows(animatorScale: Float): Boolean = animatorScale > 0f

    /** Whether something drawn for a day moves: only today, and only when the system allows it. */
    fun moves(isToday: Boolean, systemAllows: Boolean): Boolean = isToday && systemAllows
}

/**
 * Whether what is drawn here may move — [Motion.systemAllows] at the top of the app, narrowed by
 * [Motion.moves] on a screen that shows one day.
 *
 * **Still by default.** A preview, a render test, or any composition the app itself did not set up
 * gets no motion, so nothing that forgets to ask can start moving on its own.
 */
val LocalMoves = staticCompositionLocalOf { false }

/**
 * Provides [LocalMoves] for the whole app from Android's animator duration scale.
 *
 * Read again every time the app comes to the front, so turning "Remove animations" on in Settings
 * and coming back takes effect without restarting the app.
 */
@Composable
fun ProvideSystemMotion(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var allows by remember { mutableStateOf(Motion.systemAllows(animatorScale(context))) }
    LifecycleResumeEffect(context) {
        allows = Motion.systemAllows(animatorScale(context))
        onPauseOrDispose { }
    }
    CompositionLocalProvider(LocalMoves provides allows, content = content)
}

/** 1 when the setting has never been touched, which is Android's own default. */
private fun animatorScale(context: Context): Float =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

/**
 * A row gives a little under the finger while it is pressed, and comes back when let go (#16).
 *
 * The press is read from [source], which must be the same one handed to the row's `clickable`, so
 * the give and the ripple are one press. Only where [LocalMoves] allows: on a past day, or with
 * animations removed, the row keeps its size and the ripple alone answers the press.
 */
fun Modifier.givesUnderPress(source: InteractionSource): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && LocalMoves.current) Motion.PRESSED_SCALE else 1f,
        animationSpec = tween(durationMillis = Motion.PRESS_MILLIS, easing = Motion.Easing),
        label = "press",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
