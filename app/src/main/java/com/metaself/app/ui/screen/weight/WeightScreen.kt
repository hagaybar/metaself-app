package com.metaself.app.ui.screen.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.day.DayWording
import com.metaself.app.ui.goal.GoalWording
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.weight.WeightWording
import java.time.LocalDate

/**
 * The weight trend, every reading behind it, and one way to add or correct one.
 *
 * There is no separate edit: a weight is one number, and re-stating it for a day IS the whole of
 * changing it. Logging against a day that already has a reading replaces it, and the screen says so
 * before it happens rather than after.
 */
/**
 * The weight trend, every reading behind it, and a way in to logging one.
 *
 * This screen LOOKS; it does not take input. Logging opens an editor and comes back, so that saving
 * ends somewhere other than the form that saved it — which is the rule here, and what the meal
 * screens have always done.
 *
 * [justLogged] is the confirmation of the last save, shown on return. Without it, saving a weight
 * and landing back on a list is ambiguous: the new row is there, but nothing SAYS the save worked.
 */
@Composable
fun WeightScreen(
    state: WeightUiState,
    todayEpochDay: Long,
    justLogged: String?,
    onAdd: () -> Unit,
    onEdit: (WeightReading) -> Unit,
    onDelete: (Long) -> Unit,
    /** Whether a reading deleted here is still waiting to be put back. The view model counts. */
    canUndo: Boolean = false,
    onUndoDelete: () -> Unit = {},
    onRange: (ChartRange) -> Unit,
    onOpenChart: () -> Unit,
    onChangeGoal: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.ofEpochDay(todayEpochDay)

    MetaSelfScreen(
        title = stringResource(R.string.weight_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        if (justLogged != null) {
            Text(
                text = justLogged,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        WeightWording.trend(state.trend)?.let { trend ->
            // The trend is the largest figure on this screen and is a number the owner has never
            // typed. Unlabelled it reads as the app getting the weight wrong rather than smoothing
            // it.
            Text(
                text = stringResource(R.string.weight_trend_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = trend, style = MaterialTheme.typography.headlineLarge)
            Text(
                text = stringResource(R.string.weight_trend_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
        }

        WeightWording.change(state.trend)?.let { change ->
            Text(text = change, style = MaterialTheme.typography.bodyLarge)
        }

        WeightWording.latestReading(state.trend, todayEpochDay)?.let { latest ->
            Text(text = latest, style = MaterialTheme.typography.bodyLarge)
        }

        // The goal, above the chart that draws it. "To go" is the number he came for; the
        // projection underneath it is a division and says so.
        GoalWording.arrived(state.progress)?.let { arrived ->
            Text(
                text = arrived,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        GoalWording.toGo(state.progress)?.let { toGo ->
            Text(text = toGo, style = MaterialTheme.typography.titleMedium)
        }

        GoalWording.projection(state.progress)?.let { projection ->
            Text(
                text = projection,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        GoalWording.done(state.progress)?.let { done ->
            Text(
                text = done,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The goal weight and the weekly rate are named above and set in the profile editor, so the
        // way there is here (public issue #11). Drawn with no goal weight too: that editor is also
        // where one is set.
        TextButton(onClick = onChangeGoal) {
            Text(stringResource(R.string.weight_change_goal))
        }

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
                onOpen = onOpenChart,
            ) {
                Text(
                    text = stringResource(R.string.weight_trend_caption),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.weight_add))
        }

        // Above the history, not under it: the list is as long as his record is, and a way back
        // drawn below it is a way back he cannot see (the same defect as issue #37's).
        if (canUndo) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.today_deleted),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onUndoDelete) {
                    Text(stringResource(R.string.today_undo))
                }
            }
        }

        if (state.readings.isNotEmpty()) {
            Text(
                text = stringResource(R.string.weight_history),
                style = MaterialTheme.typography.titleMedium,
            )

            state.readings.sortedByDescending { it.epochDay }.forEach { reading ->
                ReadingRow(
                    reading = reading,
                    today = today,
                    onEdit = { onEdit(reading) },
                    onDelete = { onDelete(reading.epochDay) },
                )
            }
        }
    }
}

@Composable
private fun ReadingRow(
    reading: WeightReading,
    today: LocalDate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // A row tall enough to hit, with Delete set apart from Edit rather than 8dp from it. The
        // two were the same size, the same colour and a thumb's width apart on a row shorter than
        // the 48dp Android calls a safe target — on the one screen where the wrong one of them
        // moves the trend, the goal projection and the daily target at once.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
        ) {
            Text(
                text = "${DayWording.label(LocalDate.ofEpochDay(reading.epochDay), today)} — " +
                    WeightWording.reading(reading),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onEdit,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.weight_edit),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(modifier = Modifier.width(Spacing.Related))
            TextButton(
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.weight_delete),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        HorizontalDivider()
    }
}
