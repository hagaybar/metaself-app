package com.metaself.app.ui.screen.movement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.movement.FileWorkout
import com.metaself.app.domain.movement.FilledLater
import com.metaself.app.domain.movement.ImportOutcome
import com.metaself.app.domain.movement.WaitingLine
import com.metaself.app.domain.movement.MovementDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutDraft
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.domain.trainer.MovementSpan
import com.metaself.app.domain.trainer.SpanBar
import com.metaself.app.domain.trainer.SpanMeasure
import com.metaself.app.ui.movement.MovementSpanWording
import com.metaself.app.ui.movement.MovementWeekWording
import com.metaself.app.ui.movement.WorkoutFileWording
import com.metaself.app.ui.screen.day.UndoRow
import com.metaself.app.ui.trainer.TrainerWording
import com.metaself.app.ui.theme.Spacing
import java.time.ZoneId

/**
 * Your movement (D73–D75, D130, D131): the week's minutes moving as the one large figure, its sessions, km
 * and average movement calories beneath it, a short row per day — today first — and the last four weeks
 * at the foot.
 *
 * "Log a workout" sits at the bottom edge, where it cannot scroll away (D75, D76); a typed workout's
 * line in an open day opens it to be changed. A synced workout is not editable here.
 *
 * A typed workout deleted from the sheet can be put back: the record screen's "Deleted · Undo" line
 * ([UndoRow]), pinned under the title bar. The record screen pins it to the bottom edge so that no
 * list can push it below the fold; here the bottom edge is the floating button's, and the top edge
 * answers the same objection.
 *
 * ‹ and › beside the kicker step a week back and forward (D83): ‹ only while the record holds
 * something earlier, › only on an earlier week. An earlier week lists all seven days, Sunday first,
 * none open; there "Log a workout" needs a day opened to log onto, and says so above the rows.
 *
 * It is the Trainer's detail page (D130): reached from the Trainer and from Today's step bar, its back
 * arrow returns to whichever it was opened from, and its title bar has no Trainer button. Under each
 * session in an open day, typed or synced, one button says where its review stands — "How did it go?", "Get feedback" or "See
 * feedback" (plan design question 7) — and opens that session's review.
 *
 * A session other workouts also recorded (D92) is one line, with "also recorded by 2 more" under it and
 * "These are two sessions", which parts it; Undo is then offered under the title bar, and a session
 * parted from one it still overlaps offers "Put back together".
 *
 * D135–D137: under the title bar, Week · 12 weeks · Year. Week is the page above with the Trainer's strip for the week
 * as its chart; 12 weeks and Year are one chart each, whose bars open a week or a month's weeks. A sideways swipe does
 * what the arrows do ([onEarlierWeek] and [onLaterWeek] step one span, whichever is shown). "Log a workout" is on Week.
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
    onWaitForBand: () -> Unit = {},
    onAddWaiting: (FileWorkout) -> Unit = {},
    onChooseForWaiting: (FileWorkout, Long) -> Unit = { _, _ -> },
    onDropWaiting: (FileWorkout) -> Unit = {},
    onEarlierWeek: () -> Unit = {},
    onLaterWeek: () -> Unit = {},
    onReview: (Long) -> Unit = {},
    onSplit: (Workout) -> Unit = {},
    onUndoSplit: () -> Unit = {},
    onPutBack: (Workout) -> Unit = {},
    onSpan: (MovementSpan) -> Unit = {},
    onMeasure: (SpanMeasure) -> Unit = {},
    onBar: (SpanBar) -> Unit = {},
) {
    val onWeek = state.week != null
    val readable = onWeek || state.chart != null
    val canLog = state.logDay != null
    // D135: a sideways swipe does what the arrows do. Read through the latest state, so the gesture handler
    // installed once never acts on an arrow that has since gone.
    val earlier by rememberUpdatedState(if (state.canGoEarlier) onEarlierWeek else null)
    val later by rememberUpdatedState(if (state.canGoLater) onLaterWeek else null)
    MetaSelfScreen(
        title = stringResource(R.string.movement_title),
        modifier = modifier.pointerInput(Unit) {
            val threshold = SWIPE_DISTANCE.toPx()
            var dragged = 0f
            detectHorizontalDragGestures(
                onDragStart = { dragged = 0f },
                onDragEnd = {
                    when {
                        dragged > threshold -> earlier?.invoke()
                        dragged < -threshold -> later?.invoke()
                    }
                },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    dragged += amount
                },
            )
        },
        onBack = onBack,
        belowBar = {
            if (state.canUndo) UndoLine(failed = state.undoFailed, onUndo = onUndoDelete)
            if (state.canUndoSplit) SplitUndoLine(onUndo = onUndoSplit)
        },
        hasFloatingButton = onWeek,
        floatingActionButton = { if (onWeek) LogWorkoutButton(onClick = onLogWorkout, enabled = canLog) },
    ) {
        SpanSwitch(state.span, onSpan)
        val week = state.week
        val chart = state.chart
        when {
            state.unreadable -> Text(
                text = stringResource(R.string.movement_unreadable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            chart != null -> {
                SpanArrows(
                    text = MovementSpanWording.spanLine(chart),
                    canGoEarlier = state.canGoEarlier,
                    canGoLater = state.canGoLater,
                    earlierSaid = MovementSpanWording.earlierSaid(chart.span),
                    laterSaid = MovementSpanWording.laterSaid(chart.span),
                    onEarlier = onEarlierWeek,
                    onLater = onLaterWeek,
                )
                ChartSpan(chart, state.measure, onMeasure, onBar)
            }

            week == null -> Unit

            else -> {
                Headline(
                    week = week,
                    usual = state.usualWeek,
                    canGoEarlier = state.canGoEarlier,
                    canGoLater = state.canGoLater,
                    onEarlier = onEarlierWeek,
                    onLater = onLaterWeek,
                )

                state.strip?.let { WeekChartCard(it) }

                if (!canLog) {
                    // D83: why the button at the foot is greyed, drawn where the day to open is.
                    Text(
                        text = stringResource(R.string.movement_open_a_day),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                WaitingFileLines(
                    lines = state.waiting,
                    filled = state.filledLater,
                    today = week.today,
                    enabled = state.fileImport?.working != true,
                    onAdd = onAddWaiting,
                    onChoose = onChooseForWaiting,
                    onDrop = onDropWaiting,
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    week.days.forEach { day ->
                        DayRow(
                            day = day,
                            today = week.today,
                            open = day.epochDay == state.openDay,
                            onToggle = { onToggleDay(day.epochDay) },
                            onOpenWorkout = onOpenWorkout,
                            reviews = state.reviews,
                            onReview = onReview,
                            onSplit = onSplit,
                            onPutBack = onPutBack,
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
                onWait = onWaitForBand,
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
    onWait: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val idle = file?.working != true
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        TextButton(onClick = onChooseFile, enabled = idle) { Text(stringResource(R.string.movement_import_file)) }
        // No early return in here: a labelled return out of an inline composable after emitting left the
        // composer's groups unbalanced (Compose compiler 1.5.9) and crashed the next import. Branch
        // instead; InlineComposableReturnGuardTest keeps it out.
        val outcome = file?.outcome
        when {
            file == null -> Unit
            outcome == null -> Text(stringResource(R.string.movement_file_reading), style = MaterialTheme.typography.bodyMedium)
            else -> {
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
                    // D91: the day's sessions of the file's kind first, then adding it under its own line.
                    is ImportOutcome.NoMatch -> {
                        outcome.sameKind.forEach { workout ->
                            TextButton(onClick = { onChoose(workout.id) }, enabled = idle) {
                                Text(WorkoutFileWording.choice(workout, zone))
                            }
                        }
                        if (outcome.sameKind.isNotEmpty()) {
                            // D138: the file waits for the band's own session rather than filling one of these.
                            TextButton(onClick = onWait, enabled = idle) { Text(stringResource(R.string.movement_file_wait)) }
                            Text(WorkoutFileWording.OR_ADD, style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = onAdd, enabled = idle) {
                            Text(stringResource(R.string.movement_file_add))
                        }
                    }
                    // D138: kept waiting; it can still be added at once.
                    is ImportOutcome.Waiting -> TextButton(onClick = onAdd, enabled = idle) {
                        Text(stringResource(R.string.movement_file_add_now))
                    }
                    is ImportOutcome.Several -> outcome.choices.forEach { workout ->
                        TextButton(onClick = { onChoose(workout.id) }, enabled = idle) {
                            Text(WorkoutFileWording.choice(workout, zone))
                        }
                    }
                    else -> Unit
                }
                val dismiss = if (outcome is ImportOutcome.Waiting) R.string.movement_file_ok else R.string.movement_file_done
                TextButton(onClick = onDismiss, enabled = idle) { Text(stringResource(dismiss)) }
            }
        }
    }
}

/**
 * D138: above the day rows, the files waiting for the band's session — one line each, at most [WAITING_SHOWN], then
 * "and N more" — each with "Add it as a workout" and "Drop it", and the sessions it matches when there are several.
 * Above them, once, the files filled in the background since the page was last open.
 */
@Composable
private fun WaitingFileLines(
    lines: List<WaitingLine>,
    filled: List<FilledLater>,
    today: Long,
    enabled: Boolean,
    onAdd: (FileWorkout) -> Unit,
    onChoose: (FileWorkout, Long) -> Unit,
    onDrop: (FileWorkout) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    if (filled.isNotEmpty() || lines.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            filled.forEach { note ->
                Text(WorkoutFileWording.filledLater(note, today), style = MaterialTheme.typography.bodyMedium)
            }
            lines.take(WAITING_SHOWN).forEach { line ->
                val file = line.waiting.file
                Text(WorkoutFileWording.waiting(file, today), style = MaterialTheme.typography.bodyMedium)
                line.choices.forEach { workout ->
                    TextButton(onClick = { onChoose(file, workout.id) }, enabled = enabled) {
                        Text(WorkoutFileWording.choice(workout, zone))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                    TextButton(onClick = { onAdd(file) }, enabled = enabled) { Text(stringResource(R.string.movement_file_add)) }
                    TextButton(onClick = { onDrop(file) }, enabled = enabled) { Text(stringResource(R.string.movement_waiting_drop)) }
                }
            }
            if (lines.size > WAITING_SHOWN) {
                Text(
                    WorkoutFileWording.andMore(lines.size - WAITING_SHOWN),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** D138: how many waiting files have a line of their own. */
private const val WAITING_SHOWN = 3

/**
 * The way back from "These are two sessions" (D92, decided after review): pinned where the delete's
 * Undo is, for the same reason — the bottom edge is "Log a workout"'s, and an Undo nobody can see is a
 * split that cannot be undone.
 */
@Composable
private fun SplitUndoLine(onUndo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = Spacing.Tight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(R.string.movement_split_done), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onUndo) { Text(stringResource(R.string.today_undo)) }
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

/**
 * D74, D131, D137: the kicker, the one large figure — the week's minutes — and the line beneath it, ending with the
 * week against the usual one. The kicker sits between ‹ and › (D83).
 */
@Composable
private fun Headline(
    week: MovementWeek,
    usual: Double?,
    canGoEarlier: Boolean,
    canGoLater: Boolean,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        SpanArrows(
            text = MovementWeekWording.kicker(week.monday, week.thisMonday, week.today),
            canGoEarlier = canGoEarlier,
            canGoLater = canGoLater,
            earlierSaid = stringResource(R.string.movement_week_earlier),
            laterSaid = stringResource(R.string.movement_week_later),
            onEarlier = onEarlier,
            onLater = onLater,
        )
        // The one number this screen exists to answer, in the app's display face (D48): the week's minutes
        // moving, counted as the Trainer's verdict counts them (D131).
        Text(
            text = MovementWeekWording.minutes(week),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        MovementSpanWording.weekLine(week, usual)?.let { line ->
            Text(text = line, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** D135: how far a sideways drag must go to move a span — further than a scroll's wobble. */
private val SWIPE_DISTANCE = 64.dp

/** Material's own figure for disabled content. */
private const val DISABLED_ALPHA = 0.38f

/**
 * "Log a workout" (D76), drawn as the screen's floating button so it never scrolls away. Named on the
 * button itself, as the day's `AddSomethingButton` is: the words drawn inside it never reached the
 * accessibility tree.
 *
 * Disabled on an earlier week with no day open (D83). Material 3's floating button has no `enabled`
 * in this Compose version, so it is greyed by hand, tells a screen reader it is disabled, and a press
 * does nothing.
 */
@Composable
internal fun LogWorkoutButton(onClick: () -> Unit, enabled: Boolean = true) {
    val said = stringResource(R.string.movement_log_workout)
    val colours = MaterialTheme.colorScheme
    ExtendedFloatingActionButton(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.semantics {
            contentDescription = said
            if (!enabled) disabled()
        },
        containerColor = if (enabled) colours.primaryContainer else colours.surfaceVariant,
        contentColor = if (enabled) colours.onPrimaryContainer else colours.onSurface.copy(alpha = DISABLED_ALPHA),
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
private fun DayRow(
    day: MovementDay,
    today: Long,
    open: Boolean,
    onToggle: () -> Unit,
    onOpenWorkout: (Workout) -> Unit,
    reviews: Map<Long, TrainerReview>?,
    onReview: (Long) -> Unit,
    onSplit: (Workout) -> Unit,
    onPutBack: (Workout) -> Unit,
) {
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
                text = MovementWeekWording.dayHeading(day.epochDay, today),
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
            val details = MovementWeekWording.detailRows(day, ZoneId.systemDefault())
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
                        val session = line.workout
                        // D92: who else recorded it, and the way to part it — only when others did.
                        val alsoRecorded = session?.let(MovementWeekWording::alsoRecorded)
                        if (session != null && alsoRecorded != null) {
                            Text(
                                text = alsoRecorded,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { onSplit(session) }) { Text(stringResource(R.string.movement_split_session)) }
                        }
                        // D92, decided after review: a session parted from one it still overlaps can be put back.
                        if (session != null && session.splits.isNotEmpty()) {
                            TextButton(onClick = { onPutBack(session) }) { Text(stringResource(R.string.movement_put_back)) }
                        }
                        // Where this session's review stands, and the way to it (D87, design question 7);
                        // none while the reviews cannot be read, rather than a wrong one. A review on
                        // another witness of the session (D92) opens as that witness's own.
                        if (session != null && reviews != null) {
                            val review = reviews[session.id]
                            TextButton(onClick = { onReview(review?.workoutId ?: session.id) }) { Text(TrainerWording.rowAction(review)) }
                        }
                    }
                }
            }
        }
    }
}
