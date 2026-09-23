package com.metaself.app.ui.screen.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.metaself.app.ui.ActionRefused
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
 *
 * [failed] is the opposite answer, and takes the confirmation's place: the editor has already come
 * back by the time a save fails, so without this the screen would say it was logged.
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
    /** The last action here that threw rather than finishing. The view model holds it. */
    failed: ActionRefused? = null,
    onDismissFailure: () -> Unit = {},
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
        // D48's grouping: the frame spaces its children a section apart, so anything that belongs
        // together is one child here, spaced inside by the step that says how closely.
        if (failed != null) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                Text(
                    text = stringResource(failed.sentence),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onDismissFailure) {
                    Text(stringResource(R.string.action_refused_dismiss))
                }
            }
        } else if (justLogged != null) {
            Text(
                text = justLogged,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // The trend and what the readings say beside it: one block. Only drawn when there is
        // something in it, because an empty child still takes the frame's gap.
        val trend = WeightWording.trend(state.trend)
        val change = WeightWording.change(state.trend)
        val latest = WeightWording.latestReading(state.trend, todayEpochDay)
        if (trend != null || change != null || latest != null) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                trend?.let {
                    // The trend is the largest figure on this screen and is a number the owner has
                    // never typed. Unlabelled it reads as the app getting the weight wrong rather
                    // than smoothing it. Label, figure and explainer are one thing.
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                        Text(
                            text = stringResource(R.string.weight_trend_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(text = it, style = MaterialTheme.typography.headlineLarge)
                        Text(
                            text = stringResource(R.string.weight_trend_explainer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MetaSelfInk.two,
                        )
                    }
                }
                change?.let { Text(text = it, style = MaterialTheme.typography.bodyLarge) }
                latest?.let { Text(text = it, style = MaterialTheme.typography.bodyLarge) }
            }
        }

        // The goal, above the chart that draws it. "To go" is the number he came for; the
        // projection underneath it is a division and says so. One block, with the way to change it.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
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

            GoalWording.projection(state.forecast)?.let { projection ->
                Text(
                    text = projection,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // The rate he is ACTUALLY managing, directly under the one he chose, so the two are
            // read together (D47). Same style as the projection above it: neither outranks the
            // other.
            GoalWording.measured(state.forecast)?.let { measured ->
                Text(
                    text = measured,
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

            // The goal weight and the weekly rate are named above and set in the profile editor,
            // so the way there is here (public issue #11). Drawn with no goal weight too: that
            // editor is also where one is set.
            TextButton(onClick = onChangeGoal) {
                Text(stringResource(R.string.weight_change_goal))
            }
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
                // A caption, so on the caption step: it was drawn in full ink, level with the trend.
                Text(
                    text = stringResource(R.string.weight_trend_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        // The history is one list: its heading a step above it, and the rows touching, divided by
        // their own hairlines. They were a section apart each, as if every reading were a subject
        // of its own.
        if (state.readings.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                Text(
                    text = stringResource(R.string.weight_history),
                    style = MaterialTheme.typography.titleMedium,
                )
                Column {
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
            val day = DayWording.label(LocalDate.ofEpochDay(reading.epochDay), today)
            Text(
                text = "$day — " + WeightWording.reading(reading),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            // Icons, not the words "Edit" and "Delete" (#14): the words cost the row more width
            // than the reading got. Each says aloud which day it acts on, so a list of readings is
            // not a list of controls all called Delete (public issue #3). Delete is not red — red is
            // for a refusal and a field that is wrong (D48) — and stays set apart by the extra gap.
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.weight_edit_reading, day),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(Spacing.Related))
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.weight_delete_reading, day),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
    }
}
