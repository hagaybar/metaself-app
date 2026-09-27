package com.metaself.app.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.metaself.app.R
import com.metaself.app.data.movement.StepAccess
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.health.HealthRecordWording
import com.metaself.app.ui.movement.MovementWording
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing
import java.time.LocalDate

/**
 * Settings' Movement and health page (D79): what the phone lets the app read, how far the health
 * record reaches, and the way to allow it. The detailed readings' Drive controls are on Backups.
 * Its last row opens "What the band sends" (D80).
 */
@Composable
fun MovementSettingsPage(
    state: SettingsUiState,
    onConnectSteps: () -> Unit,
    onOpenBandReport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(
        title = stringResource(SettingsPage.MOVEMENT.title),
        modifier = modifier,
        onBack = onBack,
    ) {
        // --- steps ---

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Text(
                text = MovementWording.status(
                    access = state.stepAccess,
                    hasNormal = state.hasStepNormal,
                    daysSoFar = state.stepDaysSoFar,
                    earliest = state.earliestStepDay,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            // A session and its energy are two different records. This says which the band writes,
            // because the day screen cannot: a swim earning nothing looks identical whether the energy
            // was never reported or the day simply was not above his usual.
            MovementWording.bandEnergy(
                daysWithEnergy = state.daysWithBandEnergy,
                daysSeen = state.stepDaysSoFar,
            )?.let { band ->
                Text(
                    text = band,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = HealthRecordWording.status(
                    days = state.healthRecord.days,
                    earliest = state.healthRecord.earliest?.let(LocalDate::ofEpochDay),
                    lastCopiedMillis = state.healthRecord.lastCopiedMillis,
                    nowMillis = System.currentTimeMillis(),
                    catchingUp = state.healthRecord.catchingUp,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HealthRecordWording.notAllowed(state.healthRecord.notAllowed)?.let { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HealthRecordWording.historyNotAllowed(state.healthRecord.historyAllowed)?.let { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // The detailed readings' Drive line and the way back to Drive's months moved to
            // Backups, beside the other Drive controls (D79); this points there whenever either
            // would have been drawn here.
            if (state.driveOn || state.healthRecord.days > 0) {
                Text(
                    text = stringResource(R.string.settings_movement_readings_pointer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.stepAccess == StepAccess.NOT_PERMITTED || state.healthRecord.asksToConnect) {
                Button(onClick = onConnectSteps) {
                    Text(stringResource(R.string.settings_steps_connect))
                }
            }
        }

        // --- what has arrived (D80) ---

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenBandReport)
                .padding(vertical = Spacing.Related),
            verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
        ) {
            Text(stringResource(R.string.settings_band_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_band_row),
                style = MaterialTheme.typography.bodyMedium,
                color = MetaSelfInk.two,
            )
        }
    }
}
