package com.metaself.app.ui.screen.movement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.movement.ImportOutcome
import com.metaself.app.domain.movement.MovementDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutDraft
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.movement.MovementWeekWording
import com.metaself.app.ui.movement.WorkoutFileWording
import com.metaself.app.ui.screen.day.UndoRow
import com.metaself.app.ui.theme.Spacing
import java.time.ZoneId

/**
 * This week's movement (D73–D75): the week's distance as the one large figure, the average movement
 * calories and the workouts beneath it, a short row per day — today first — and the last four weeks
 * at the foot.
 *
 * "Log a workout" sits at the bottom edge, where it cannot scroll away (D75, D76); a typed workout's
 * line in an open day opens it to be changed. A synced workout is not editable here.
 *
 * A typed workout deleted from the sheet can be put back: the record screen's "Deleted · Undo" line
 * ([UndoRow]), pinned under the title bar. The record screen pins it to the bottom edge so that no
 * list can push it below the fold; here the bottom edge is the floating button's, and the top edge
 * answers the same objection.
 */
@Composable
fun MovementScreen(
    state: MovementUiState,
    onToggleDay: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onLogWorkout: () -> Unit = {},
    onOpenWorkout: (Workout) -> Unit = {},
    onDraft: (WorkoutDraft) -> Unit = {},
    onSaveWorkout: () -> Unit = {},
    onDeleteWorkout: () -> Unit = {},
    onCloseSheet: () -> Unit = {},
    onUndoDelete: () -> Unit = {},
    onChooseFile: () -> Unit = {},
    onAddFromFile: () -> Unit = {},
    onChooseForFile: (Long) -> Unit = {},
    onDismissFile: () -> Unit = {},
) {
    val readable = state.week != null
    MetaSelfScreen(
        title = stringResource(R.string.movement_title),
        modifier = modifier,
        onBack = onBack,
        belowBar = { if (state.canUndo) UndoLine(failed = state.undoFailed, onUndo = onUndoDelete) },
        hasFloatingButton = readable,
        floatingActionButton = { if (readable) LogWorkoutButton(onClick = onLogWorkout) },
    ) {
        val week = state.week
        when {
            state.unreadable -> Text(
                text = stringResource(R.string.movement_unreadable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            week == null -> Unit

            else -> {
                Headline(week)

                Column(modifier = Modifier.fillMaxWidth()) {
                    week.days.forEach { day ->
                        DayRow(
                            day = day,
                            open = day.epochDay == state.openDay,
                            onToggle = { onToggleDay(day.epochDay) },
                            onOpenWorkout = onOpenWorkout,
                        )
                    }
                }

                MovementWeekWording.lastFourWeeks(week)?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (readable || state.unreadable) {
            WorkoutFileLines(
                file = state.fileImport,
                onChooseFile = onChooseFile,
                onAdd = onAddFromFile,
                onChoose = onChooseForFile,
                onDismiss = onDismissFile,
            )
        }
    }

    state.sheet?.let { sheet ->
        WorkoutSheet(
            sheet = sheet,
            onDraft = onDraft,
            onSave = onSaveWorkout,
            onDelete = onDeleteWorkout,
            onCancel = onCloseSheet,
        )
    }
}

/**
 * A workout file (D82): the way to pick one, and what the last one came to — its line, then "Add it as
 * a workout" when nothing matched, or one button per matching workout, and Done. A step under way
 * leaves the buttons waiting.
 */
@Composable
private fun WorkoutFileLines(
    file: FileImportState?,
    onChooseFile: () -> Unit,
    onAdd: () -> Unit,
    onChoose: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val idle = file?.working != true
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        TextButton(onClick = onChooseFile, enabled = idle) { Text(stringResource(R.string.movement_import_file)) }
        if (file == null) return@Column
        val outcome = file.outcome
        if (outcome == null) {
            Text(stringResource(R.string.movement_file_reading), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        Text(
            text = WorkoutFileWording.line(outcome, zone),
            style = MaterialTheme.typography.bodyMedium,
            color = if (outcome is ImportOutcome.Failed || outcome is ImportOutcome.Refused) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        )
        when (outcome) {
            is ImportOutcome.NoMatch -> TextButton(onClick = onAdd, enabled = idle) {
                Text(stringResource(R.string.movement_file_add))
            }
            is ImportOutcome.Several -> outcome.choices.forEach { workout ->
                TextButton(onClick = { onChoose(workout.id) }, enabled = idle) {
                    Text(WorkoutFileWording.choice(workout, zone))
                }
            }
            else -> Unit
        }
        TextButton(onClick = onDismiss, enabled = idle) { Text(stringResource(R.string.movement_file_done)) }
    }
}

/**
 * The way back from deleting a typed workout: the record screen's own "Deleted · Undo" line, and,
 * when the last Undo failed, a line saying so above it (D8).
 */
@Composable
private fun UndoLine(failed: Boolean, onUndo: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Spacing.Screen, vertical = Spacing.Tight)) {
        if (failed) {
            Text(
                text = stringResource(R.string.movement_undo_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        UndoRow(onUndo = onUndo)
    }
}

/** D74 items 1–4: the kicker, the one large figure, and the two smaller lines beneath it. */
@Composable
private fun Headline(week: MovementWeek) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(
            text = MovementWeekWording.kicker(week.monday),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The one number this screen exists to answer, in the app's display face (D48).
        MovementWeekWording.distance(week)?.let { distance ->
            Text(
                text = distance,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        MovementWeekWording.averageMovement(week)?.let { average ->
            Text(text = average, style = MaterialTheme.typography.bodyLarge)
        }
        MovementWeekWording.workouts(week)?.let { workouts ->
            Text(
                text = workouts,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * "Log a workout" (D76), drawn as the screen's floating button so it never scrolls away. Named on the
 * button itself, as the day's `AddSomethingButton` is: the words drawn inside it never reached the
 * accessibility tree.
 */
@Composable
internal fun LogWorkoutButton(onClick: () -> Unit) {
    val said = stringResource(R.string.movement_log_workout)
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = said },
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text(said) },
    )
}

/**
 * One day (D73). The heading and the one-line summary are one button that says whether it is open;
 * the summary stays whether the day is open or closed. An open day's detail lines sit beneath it,
 * outside the button, so a screen reader reads them one at a time.
 */
@Composable
private fun DayRow(day: MovementDay, open: Boolean, onToggle: () -> Unit, onOpenWorkout: (Workout) -> Unit) {
    val said = stringResource(if (open) R.string.movement_day_open else R.string.movement_day_closed)
    val action = stringResource(if (open) R.string.movement_day_hide else R.string.movement_day_show)
    val change = stringResource(R.string.movement_workout_change)

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = action, onClick = onToggle)
                // The usual 48 of touch; a render here cannot measure it (CLAUDE.md), the phone can.
                .heightIn(min = 48.dp)
                .semantics { stateDescription = said }
                .padding(vertical = Spacing.Related),
            verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
        ) {
            Text(
                text = MovementWeekWording.dayHeading(day.epochDay),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = MovementWeekWording.summaryLine(day),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) {
            val details = MovementWeekWording.detailRows(day)
            if (details.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.Related),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
                ) {
                    details.forEach { line ->
                        // A typed workout's line is a door to change it (D76); a synced one's is not.
                        val typed = line.workout?.takeIf { it.source == WorkoutSource.TYPED }
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = if (typed == null) {
                                Modifier
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button, onClickLabel = change, onClick = { onOpenWorkout(typed) })
                                    // The usual 48 of touch; a render here cannot measure it (CLAUDE.md).
                                    .heightIn(min = 48.dp)
                            },
                        )
                    }
                }
            }
        }
    }
}
