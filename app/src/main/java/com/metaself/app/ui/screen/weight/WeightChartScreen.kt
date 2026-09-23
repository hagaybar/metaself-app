package com.metaself.app.ui.screen.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.metaself.app.R
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.theme.Spacing

/**
 * The chart on its own, with the whole screen to itself.
 *
 * The same ranges and the same drawing as on the weight screen, given the height the weight screen
 * cannot spare. Turning the phone needs nothing declared: the manifest fixes no orientation, so
 * Android recreates the screen landscape — and because the chosen range is stored rather than
 * remembered in the composition, it survives that recreation.
 *
 * It closes by its own labelled button as well as by the title bar's arrow and the system's own
 * gesture. The arrow is drawn by every screen in the app and carries a description rather than a
 * word; giving a full-screen view a way out that says what it does is worth one button.
 */
@Composable
fun WeightChartScreen(
    state: WeightUiState,
    todayEpochDay: Long,
    onRange: (ChartRange) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(
        title = stringResource(R.string.weight_chart_title),
        modifier = modifier,
        onBack = onBack,
        // The chart takes the height it is given rather than scrolling away from it, which is the
        // whole point of a full-screen view.
        scrolls = false,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Section),
        ) {
            if (state.trend.isEmpty()) {
                Text(
                    text = stringResource(R.string.weight_nothing),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                WeightChartBlock(
                    trend = state.trend,
                    range = state.range,
                    todayEpochDay = todayEpochDay,
                    onRange = onRange,
                    targetKg = state.progress?.targetKg,
                    fillsHeight = true,
                    modifier = Modifier.weight(1f),
                )
            }

            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.weight_chart_close))
            }
        }
    }
}
