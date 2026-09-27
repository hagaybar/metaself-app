package com.metaself.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.metaself.app.R
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.health.BandReportWording
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/**
 * "What the band sends" (D80): what has arrived in the stored health record over the last 30 days,
 * kind by kind, then the workouts, then the daily summary's coverage, then what Health Connect cannot
 * carry, then Copy as text. Reads nothing and writes nothing itself; [notAllowed] comes from Settings.
 */
@Composable
fun BandReportPage(
    state: BandReportUiState,
    notAllowed: Set<HealthKind>,
    onCopy: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(
        title = stringResource(R.string.settings_band_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        val report = state.report
        when {
            state.unreadable -> Text(
                text = stringResource(R.string.settings_band_unreadable),
                style = MaterialTheme.typography.bodyMedium,
            )

            report != null -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                Text(
                    text = BandReportWording.window(report, state.today),
                    style = MaterialTheme.typography.bodySmall,
                    color = MetaSelfInk.two,
                )

                Heading(stringResource(R.string.settings_band_kinds))
                report.kinds.forEach { kind ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                        Text(kind.kind.displayName, style = MaterialTheme.typography.bodyMedium)
                        Small(BandReportWording.kindLine(kind, notAllowed, state.labels))
                        BandReportWording.kindDates(kind, state.today)?.let { Small(it) }
                    }
                }

                Heading(stringResource(R.string.settings_band_workouts))
                BandReportWording.workoutLines(report.workouts).forEach { Small(it) }

                Heading(stringResource(R.string.settings_band_days))
                BandReportWording.dayLines(report).forEach { Small(it) }

                Text(BandReportWording.NOT_SHARED, style = MaterialTheme.typography.bodySmall)

                TextButton(onClick = onCopy) { Text(stringResource(R.string.settings_band_copy)) }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun Small(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MetaSelfInk.two)
}
