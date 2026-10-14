package com.metaself.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.metaself.app.R
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.settings.MeasureWording
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing
import java.time.ZoneId

/**
 * Settings' Recent problems page (D79): what has gone wrong, to copy or clear; and, for now (D152), the
 * switch that measures the app for three days and the button that downloads what it measured.
 */
@Composable
fun ProblemsSettingsPage(
    state: SettingsUiState,
    onCopyProblems: () -> Unit,
    onClearProblems: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    measure: MeasureViewModel.State = MeasureViewModel.State(),
    onMeasure: (Boolean) -> Unit = {},
    onDownloadDiagnostics: () -> Unit = {},
    zone: ZoneId = ZoneId.systemDefault(),
) {
    MetaSelfScreen(
        title = stringResource(SettingsPage.PROBLEMS.title),
        modifier = modifier,
        onBack = onBack,
    ) {
        // --- what has gone wrong ---

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Text(
                text = stringResource(R.string.settings_problems_note),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )

            if (state.problems.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_problems_none),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                state.problems.forEach { line ->
                    Text(text = line, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                    TextButton(onClick = onCopyProblems) {
                        Text(stringResource(R.string.settings_problems_copy))
                    }
                    TextButton(onClick = onClearProblems) {
                        Text(stringResource(R.string.settings_problems_clear))
                    }
                }
            }
        }

        // --- D152 (temporary): measuring the app ---

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    Text(MeasureWording.TITLE, style = MaterialTheme.typography.titleMedium)
                    Text(MeasureWording.status(measure.untilMillis, zone), style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = measure.untilMillis != null, onCheckedChange = onMeasure)
            }
            Text(MeasureWording.WHAT, style = MaterialTheme.typography.bodySmall, color = MetaSelfInk.two)
            OutlinedButton(onClick = onDownloadDiagnostics) { Text(MeasureWording.DOWNLOAD) }
            Text(MeasureWording.DOWNLOAD_NOTE, style = MaterialTheme.typography.bodySmall, color = MetaSelfInk.two)
            measure.saved?.let { saved ->
                Text(
                    if (saved) MeasureWording.SAVED else MeasureWording.NOT_SAVED,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (saved) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
