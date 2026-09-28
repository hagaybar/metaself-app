package com.metaself.app.ui.screen.weight

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.metaself.app.R
import com.metaself.app.domain.weight.TrendPoint
import com.metaself.app.ui.theme.Spacing

/** Whether the phone is on its side. Read from the configuration, so it follows every turn. */
@Composable
internal fun isSideways(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/**
 * The chart with the phone on its side: the plot, the hold-and-drag readout, and the range chips at
 * the end of the readout's line. Nothing else.
 *
 * Sideways, height is what runs out. Upright, the weight screen and the bigger view spend it on a
 * title bar, the goal's sentences, captions and a Close button, and turned sideways what was left
 * for the plot was a strip. So both screens hand the whole screen to this instead, and the status
 * and navigation bars step aside while it shows (a swipe from the edge brings them back for a
 * moment). Turning the phone upright again is the way out, and puts back what was there; the
 * system's back gesture still does what it does on the screen underneath.
 *
 * What is left out is left out on purpose: the goal's "off this view" sentence and the captions
 * explain the upright chart and are one turn away. The empty-range sentence stays, because without
 * it a range with nothing in it would be a blank screen.
 */
@Composable
fun WeightChartSideways(
    trend: List<TrendPoint>,
    range: ChartRange,
    todayEpochDay: Long,
    onRange: (ChartRange) -> Unit,
    modifier: Modifier = Modifier,
    targetKg: Double? = null,
) {
    SystemBarsHidden()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Spacing.Section, vertical = Spacing.Related),
        verticalArrangement = Arrangement.spacedBy(Spacing.Related),
    ) {
        // The same answer WeightChartBlock asks for, keyed the same way.
        val geometry = remember(trend, targetKg, range, todayEpochDay) {
            ChartGeometry.of(
                trend = trend,
                widthPx = 1f,
                heightPx = 1f,
                range = range,
                todayEpochDay = todayEpochDay,
                targetKg = targetKg,
            )
        }

        if (geometry == null) {
            ChartRanges(selected = range, onRange = onRange)
            range.spanPhrase?.let { phrase ->
                Text(
                    text = stringResource(R.string.weight_range_empty, stringResource(phrase)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            WeightChart(
                trend = trend,
                geometry = geometry,
                range = range,
                todayEpochDay = todayEpochDay,
                targetKg = targetKg,
                fillsHeight = true,
                readoutEnd = {
                    ChartRanges(selected = range, onRange = onRange, modifier = Modifier)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Hides the status and navigation bars while this is on screen, and gives them back after. */
@Composable
private fun SystemBarsHidden() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
