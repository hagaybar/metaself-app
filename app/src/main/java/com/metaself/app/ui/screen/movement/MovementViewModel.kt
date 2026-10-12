package com.metaself.app.ui.screen.movement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.day.MealRepository
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.health.SessionSplits
import com.metaself.app.data.health.WaitingFileMatcher
import com.metaself.app.data.health.WaitingFiles
import com.metaself.app.data.health.WorkoutFileImporter
import com.metaself.app.data.health.TypedWorkouts
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.KeptPlans
import com.metaself.app.data.trainer.TrainerReviews
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.movement.FileWorkout
import com.metaself.app.domain.movement.FilledLater
import com.metaself.app.domain.movement.ImportOutcome
import com.metaself.app.domain.movement.WaitingLine
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.SessionSplit
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutDraft
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.trainer.MovementSpan
import com.metaself.app.domain.trainer.MovementSpans
import com.metaself.app.domain.trainer.SessionReviews
import com.metaself.app.domain.trainer.SpanBar
import com.metaself.app.domain.trainer.SpanChart
import com.metaself.app.domain.trainer.SpanMeasure
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.WeekLedgers
import com.metaself.app.domain.trainer.WeekStrip
import com.metaself.app.ui.movement.WorkoutFileWording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import javax.inject.Inject

/**
 * A week from the stored health record and the meal log (D73–D75) — this one, or an earlier one
 * stepped back to (D83) — and which day is open.
 *
 * The week shown lives here, so leaving the screen by Back discards it and the next visit starts on
 * this week. Coming back from the file picker or another app on the same day keeps it; coming back on
 * a new day returns to this week, with today open.
 *
 * Everything is observed, so a copy of the health record, or a meal logged, while the screen is open
 * shows at once. Eaten is read through [MealRepository.observeDay] — the day screen's own read — and
 * summed by `DayTotals`, so the two screens cannot disagree about a day.
 *
 * It also holds the log-a-workout sheet (D76): which workout it is for, what has been typed, and the
 * write that saves it. Opened from the Trainer's "Log a workout" ([OPEN_LOG], D132), the sheet starts open
 * for today. A typed workout reaches the week through the record it is written to, like any
 * other workout. A typed workout deleted here can be put back with Undo, the app's rule for a thing it
 * can rebuild (the weight screen's `WeightViewModel.delete`).
 *
 * D135–D137: the span shown — Week, 12 weeks or Year — and the day in view, which a span switch keeps. Each span is
 * one read of the record over its days, never one per week; "planned" is counted from the kept plans ([KeptPlans]),
 * read with it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MovementViewModel(
    private val record: MovementRecord,
    private val meals: MealRepository,
    private val today: Today,
    private val problems: ProblemLog,
    private val typed: TypedWorkouts,
    private val profiles: ProfileRepository,
    private val now: Now,
    private val files: WorkoutFileImporter = WorkoutFileImporter.NONE,
    /** Where [fileStep] writes the problem log (a file, D8) — a test supplies its own. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Which sessions have a review, so each says its own button (D85, design question 7). */
    private val trainer: TrainerReviews = TrainerReviews.NONE,
    /** "These are two sessions" (D92). */
    private val splits: SessionSplits = SessionSplits.NONE,
    /** The destination's arguments: [OPEN_LOG] opens the sheet once (D132). */
    private val savedState: SavedStateHandle = SavedStateHandle(),
    /** The kept weekly plans, which "planned" is counted by (D110, D135, D136). */
    private val plans: KeptPlans = KeptPlans.NONE,
    /** D138: the workout files waiting for their session. */
    private val waitingFiles: WaitingFiles = WaitingFiles.NONE,
) : ViewModel() {

    @Inject
    constructor(
        record: MovementRecord,
        meals: MealRepository,
        today: Today,
        problems: ProblemLog,
        typed: TypedWorkouts,
        profiles: ProfileRepository,
        now: Now,
        files: WorkoutFileImporter,
        trainer: TrainerReviews,
        splits: SessionSplits,
        savedState: SavedStateHandle,
        plans: KeptPlans,
        waitingFiles: WaitingFiles,
    ) : this(record, meals, today, problems, typed, profiles, now, files, Dispatchers.IO, trainer, splits, savedState, plans, waitingFiles)

    private val calendarToday = MutableStateFlow(today().toEpochDay())

    /** Today starts open (D73). */
    private val openDay = MutableStateFlow<Long?>(calendarToday.value)

    /** The Monday of the week shown; null is this week, whichever week that is by now (D83). */
    private val shownMonday = MutableStateFlow<Long?>(null)

    /** D135: the span shown; Week each time the page opens. */
    private val span = MutableStateFlow(MovementSpan.WEEK)

    /** D135: the day in view on 12 weeks and Year; null is today, whichever day that is by now. */
    private val chartEnd = MutableStateFlow<Long?>(null)

    /** D136: what the 12-week bars count. */
    private val measure = MutableStateFlow(SpanMeasure.MINUTES)

    /** The log-a-workout sheet, when it is open (D76). */
    private val sheet = MutableStateFlow<WorkoutSheetState?>(null)

    /**
     * Every typed workout deleted on this screen, oldest first. Undo takes the most recent, which is
     * the order they left in — the shape the weight screen and the record screen use for theirs.
     */
    private val undoable = ArrayDeque<Workout>()

    /** Whether Undo is offered, and whether the last one failed. */
    private val undo = MutableStateFlow(UndoState())

    /** The last workout file's import (D82). */
    private val fileImport = MutableStateFlow<FileImportState?>(null)

    /** D138: the files filled in the background since the page was last open — said for this visit. */
    private val filledLater = MutableStateFlow<List<FilledLater>>(emptyList())

    /** D138: the waiting files; a read that fails shows none (the matcher logs its own failures). */
    private val waitingLines: Flow<List<WaitingLine>> = waitingFiles.lines.catch { failure ->
        problems.record(PROBLEM_KIND, "waiting files not read: " + (failure.message ?: failure::class.java.simpleName))
        emit(emptyList())
    }

    /** Every sheet this screen has opened gets the next one of these (see [WorkoutSheetState.token]). */
    private var nextSheetToken = 0L

    /**
     * One span's read (D83, D135): the week shown with its strip and usual week, or the 12-week or Year chart; and
     * whether ‹ and › are drawn.
     */
    private data class Read(
        val week: MovementWeek? = null,
        val reviews: Map<Long, TrainerReview>? = emptyMap(),
        val strip: WeekStrip? = null,
        val usual: Double? = null,
        val chart: SpanChart? = null,
        val canGoEarlier: Boolean = false,
        val canGoLater: Boolean = false,
    )

    private data class Shown(val day: Long, val monday: Long?, val span: MovementSpan, val end: Long?)

    /**
     * Null means the read failed; the failure is logged where it happened, below. The earliest day and the kept
     * plans are part of the same read, so they fail the way the rest does (D8).
     */
    private val week: Flow<Read?> = combine(calendarToday, shownMonday, span, chartEnd) { day, chosen, shownSpan, end -> Shown(day, chosen, shownSpan, end) }
        .flatMapLatest { shown ->
            if (shown.span == MovementSpan.WEEK) weekRead(shown.day, shown.monday) else chartRead(shown.day, shown.span, shown.end ?: shown.day)
        }
        .catch { failure ->
            problems.record(PROBLEM_KIND, failure.message ?: failure::class.java.simpleName)
            emit(null)
        }

    private fun weekRead(day: Long, chosen: Long?): Flow<Read?> {
        val monday = chosen ?: MovementWeek.mondayOf(day)
        // This week stops at today; an earlier one is all seven days.
        val last = if (chosen == null) day else monday + 6
        return combine(
            // Five weeks: the one shown, and the four the foot of the screen sums (D74).
            record.observeDays(monday - 7L * MovementWeek.PREVIOUS_WEEKS, last),
            // The week's sessions, and the four weeks' before it its usual is counted over (D137).
            record.observeWorkouts(monday - 7L * MovementSpans.USUAL_WEEKS, last),
            mealsOn((monday..last).toList()),
            combine(record.observeEarliestDay(), record.observeEarliestMovementDay()) { earliest, start -> earliest to start },
            reviews(),
        ) { days, workouts, mealsByDay, (earliest, start), reviews ->
            val built = MovementWeek.of(day, days, workouts, mealsByDay, monday)
            // D135: the Trainer's strip for this week, counted from the same practice; no "To do" here.
            val practice = WeekLedgers.practiceOf(monday, day, workouts, plans.counted())
            Read(
                week = built,
                // D92: a combined session's review may sit on another of its witnesses.
                reviews = reviews?.let { SessionReviews.bySession(workouts, it) },
                strip = WeekStrip.of(monday, day, workouts, practice).copy(toDo = emptyList()),
                usual = MovementSpans.usualWeek(monday, day, workouts, start),
                canGoEarlier = earliest != null && MovementWeek.mondayOf(earliest) < monday,
                canGoLater = !built.isCurrent,
            )
        }
    }

    /** D136: the span's whole record in one read — its days and sessions, from its first week's Monday. */
    private fun chartRead(day: Long, shownSpan: MovementSpan, end: Long): Flow<Read?> {
        val from = MovementSpans.readFrom(shownSpan, end)
        val to = minOf(MovementSpans.lastDay(shownSpan, end), day)
        return combine(
            record.observeDays(from, to),
            record.observeWorkouts(from, to),
            record.observeEarliestMovementDay(),
        ) { days, workouts, start ->
            val kept = plans.counted()
            val chart = if (shownSpan == MovementSpan.TWELVE_WEEKS) {
                MovementSpans.weeks(end, day, workouts, days, kept, start)
            } else {
                MovementSpans.months(end, day, workouts, days, kept, start)
            }
            Read(
                chart = chart,
                canGoEarlier = MovementSpans.canGoEarlier(shownSpan, end, start),
                canGoLater = !MovementSpans.holdsToday(shownSpan, end, day),
            )
        }
    }

    private data class Choices(
        val file: FileImportState?,
        val span: MovementSpan,
        val measure: SpanMeasure,
        val waiting: List<WaitingLine>,
        val filled: List<FilledLater>,
    )

    val state: StateFlow<MovementUiState> = combine(
        week,
        openDay,
        sheet,
        undo,
        combine(fileImport, span, measure, waitingLines, filledLater) { file, shownSpan, counted, waiting, filled ->
            Choices(file, shownSpan, counted, waiting, filled)
        },
    ) { read, open, sheetNow, undoNow, choices ->
        val file = choices.file
        val built = read?.week
        when {
            read == null -> MovementUiState(unreadable = true, openDay = open, sheet = sheetNow, fileImport = file, span = choices.span, measure = choices.measure)
            built == null -> MovementUiState(
                chart = read.chart,
                span = choices.span,
                measure = choices.measure,
                canGoEarlier = read.canGoEarlier,
                canGoLater = read.canGoLater,
                openDay = open,
                sheet = sheetNow,
                canUndo = undoNow.offered,
                undoFailed = undoNow.failed,
                canUndoSplit = undoNow.split.isNotEmpty(),
                fileImport = file,
                logDay = null,
            )
            else -> MovementUiState(
                week = built,
                canGoEarlier = read.canGoEarlier,
                canGoLater = read.canGoLater,
                // D76 on this week; on an earlier one only an open day will do (D83).
                logDay = open ?: built.days.firstOrNull()?.epochDay?.takeIf { built.isCurrent },
                openDay = open,
                sheet = sheetNow,
                canUndo = undoNow.offered,
                undoFailed = undoNow.failed,
                canUndoSplit = undoNow.split.isNotEmpty(),
                fileImport = file,
                reviews = read.reviews,
                span = choices.span,
                measure = choices.measure,
                strip = read.strip,
                usualWeek = read.usual,
            )
        }.copy(waiting = choices.waiting, filledLater = choices.filled)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = MovementUiState(),
        )

    /** A workout file was picked or shared (D82): read it and fill in, or say why not. */
    fun importFile(uri: String) = fileStep { files.import(uri) }

    /** "Add it as a workout", for the file that matched nothing — waiting or not (D138). */
    fun addFromFile() {
        val file = when (val outcome = fileImport.value?.outcome) {
            is ImportOutcome.NoMatch -> outcome.file
            is ImportOutcome.Waiting -> outcome.file
            else -> null
        } ?: return
        fileStep { files.add(file) }
    }

    /** D138: "None of these — wait for the band", under the sessions of the file's kind (D91). */
    fun waitForBand() {
        val outcome = fileImport.value?.outcome as? ImportOutcome.NoMatch ?: return
        if (outcome.sameKind.isEmpty()) return
        fileStep { files.wait(outcome.file) }
    }

    /** D138: "Add it as a workout" on a waiting file's line; said as an import is. */
    fun addWaiting(file: FileWorkout) = fileStep { files.add(file) }

    /** D138: one of the sessions a waiting file matches, chosen on its line. */
    fun chooseForWaiting(file: FileWorkout, id: Long) = fileStep { files.choose(file, id) }

    /** D138: "Drop it" on a waiting file's line. */
    fun dropWaiting(file: FileWorkout) {
        viewModelScope.launch { waitingFiles.drop(file) }
    }

    /** One of several matching workouts, or of the same-kind sessions offered after no match (D91), was chosen. */
    fun chooseForFile(id: Long) {
        val file = when (val outcome = fileImport.value?.outcome) {
            is ImportOutcome.Several -> outcome.file
            is ImportOutcome.NoMatch -> outcome.file.takeIf { outcome.sameKind.any { it.id == id } }
            else -> null
        } ?: return
        fileStep { files.choose(file, id) }
    }

    /** The file's line was dismissed. */
    fun dismissFile() {
        fileImport.value = null
    }

    /**
     * One step at a time; the importer never throws (D8), so the step always lands. An outcome that is a problem — a
     * refused file, a failed write — is also written to the problem log as kind [IMPORT_KIND]; one that worked or
     * waits is not (D139). The write is moved off the caller's own dispatcher (Main) the same way
     * `HealthRecordSync.note` does, since the log writes a file (D8).
     */
    private fun fileStep(step: suspend () -> ImportOutcome) {
        if (fileImport.value?.working == true) return
        fileImport.value = FileImportState(outcome = fileImport.value?.outcome, working = true)
        viewModelScope.launch {
            val outcome = step()
            WorkoutFileWording.logLine(outcome)?.let { logged ->
                withContext(ioDispatcher) { problems.record(IMPORT_KIND, logged) }
            }
            fileImport.value = FileImportState(outcome = outcome)
        }
    }

    /** A day's row was tapped: open it, closing any other — or close it, if it was the open one. */
    fun toggle(epochDay: Long) {
        openDay.update { open -> if (open == epochDay) null else epochDay }
    }

    /** D135: ‹, or a swipe right — one span back, as the arrow does, and only when it is drawn. */
    fun earlier() {
        val shownSpan = span.value
        if (shownSpan == MovementSpan.WEEK) return earlierWeek()
        if (!state.value.canGoEarlier || state.value.span != shownSpan) return
        chartEnd.value = MovementSpans.earlier(shownSpan, chartEnd.value ?: calendarToday.value)
    }

    /** D135: ›, or a swipe left — one span forward; never past the span holding today. */
    fun later() {
        val shownSpan = span.value
        if (shownSpan == MovementSpan.WEEK) return laterWeek()
        val end = chartEnd.value ?: return
        chartEnd.value = MovementSpans.later(shownSpan, end, calendarToday.value)
    }

    /**
     * D135: the switch. The day in view is kept — Week's Sunday, or the chart's last day — so 12 weeks ends on the week
     * that was shown, and Week shows the last week of the 12. A span reaching today follows the calendar.
     */
    fun chooseSpan(next: MovementSpan) {
        val shownSpan = span.value
        if (next == shownSpan) return
        val end = if (shownSpan == MovementSpan.WEEK) shownMonday.value?.let { it + 6 } else chartEnd.value
        if (next == MovementSpan.WEEK) {
            showWeek(end?.let(MovementWeek::mondayOf))
        } else {
            chartEnd.value = end?.takeUnless { MovementSpans.holdsToday(next, it, calendarToday.value) }
        }
        span.value = next
    }

    /** D136: the 12-week chips. */
    fun chooseMeasure(next: SpanMeasure) {
        measure.value = next
    }

    /**
     * D136: a bar was tapped. A week opens in Week; a month opens 12 weeks ending with its last week. A bar before the
     * record opens nothing.
     */
    fun openBar(bar: SpanBar) {
        if (!bar.recorded) return
        when (span.value) {
            MovementSpan.TWELVE_WEEKS -> {
                showWeek(MovementWeek.mondayOf(bar.first))
                span.value = MovementSpan.WEEK
            }
            MovementSpan.YEAR -> {
                chartEnd.value = bar.last.takeUnless { MovementSpans.holdsToday(MovementSpan.TWELVE_WEEKS, it, calendarToday.value) }
                span.value = MovementSpan.TWELVE_WEEKS
            }
            MovementSpan.WEEK -> Unit
        }
    }

    /** [monday]'s week, none open; this week (null, or today's Monday) with today open, as the page opens. */
    private fun showWeek(monday: Long?) {
        if (monday == null || monday >= MovementWeek.mondayOf(calendarToday.value)) {
            shownMonday.value = null
            openDay.value = calendarToday.value
        } else {
            shownMonday.value = monday
            openDay.value = null
        }
    }

    /**
     * ‹ (D83): the week before, with no day open — only while the record goes back that far, as the
     * last state read it; the arrow is drawn from that same state.
     */
    fun earlierWeek() {
        val now = state.value
        val week = now.week ?: return
        if (!now.canGoEarlier) return
        shownMonday.value = week.monday - 7
        openDay.value = null
    }

    /** › (D83): the week after, with no day open; into this week, today opens. Never past this week. */
    fun laterWeek() {
        val shown = shownMonday.value ?: return
        val next = shown + 7
        if (next >= MovementWeek.mondayOf(calendarToday.value)) {
            shownMonday.value = null
            openDay.value = calendarToday.value
        } else {
            shownMonday.value = next
            openDay.value = null
        }
    }

    /**
     * The screen came to the front. A screen left open past midnight moves to the new day — back to
     * this week if an earlier one was shown (D83) — and opens it, as the day pager does; on the same
     * day nothing changes, so a trip to the file picker keeps the week shown.
     */
    fun lookedAt() {
        val now = today().toEpochDay()
        if (now == calendarToday.value) return
        openDay.value = now
        shownMonday.value = null
        chartEnd.value = null
        calendarToday.value = now
    }

    /**
     * "Log a workout" (D76): an empty sheet, for the open day — today on this week when none is open.
     * On an earlier week with no day open there is no day to log onto, and nothing happens (D83).
     */
    fun logWorkout() {
        // The same rule as the state's logDay, read from its sources so a tap is never judged on a
        // state that has not caught up with the last toggle.
        val day = openDay.value ?: calendarToday.value.takeIf { shownMonday.value == null } ?: return
        val token = nextSheetToken++
        viewModelScope.launch {
            sheet.value = WorkoutSheetState(draft = WorkoutDraft(), epochDay = day, weightKg = weight(), token = token)
        }
    }

    /**
     * A typed workout's line was tapped: the sheet, filled. A synced workout is not editable here (D76).
     * A typed session other workouts also recorded (D92) opens its lead as stored, never the figures
     * the others lent it: those are not the owner's to save over his own.
     */
    fun openWorkout(session: Workout) {
        if (session.source != WorkoutSource.TYPED) return
        val workout = session.asStored
        val token = nextSheetToken++
        viewModelScope.launch {
            sheet.value = WorkoutSheetState(
                draft = WorkoutDraft.from(workout),
                epochDay = workout.epochDay,
                weightKg = weight(),
                editing = workout,
                token = token,
            )
        }
    }

    fun changeDraft(draft: WorkoutDraft) {
        sheet.update { it?.copy(draft = draft, failure = null) }
    }

    fun closeSheet() {
        sheet.value = null
    }

    /**
     * Save: a new workout onto the sheet's day at the clock time now (D76), or the changed one in
     * place, keeping its day, start and hidden flag. Nothing happens while the draft cannot be saved.
     */
    fun saveWorkout() {
        val open = sheet.value ?: return
        if (open.saving) return
        val editing = open.editing
        val workout = if (editing == null) {
            open.draft.toWorkout(
                id = 0,
                epochDay = open.epochDay,
                startedAtMillis = WorkoutDraft.startOn(open.epochDay, now(), ZoneId.systemDefault()),
                weightKg = open.weightKg,
            )
        } else {
            open.draft.toWorkout(editing.id, editing.epochDay, editing.startedAtMillis, open.weightKg)
                ?.copy(hidden = editing.hidden)
        } ?: return
        write(open, WriteFailure.SAVE) {
            if (editing == null) {
                typed.log(workout)
            } else {
                check(typed.change(workout)) { "no typed workout ${workout.id} to change" }
            }
        }
    }

    /**
     * Delete, from the sheet of a typed workout being changed: at once, then Undo is offered (plan
     * design question 8) — the app's rule for a thing it can rebuild, as `WeightViewModel.delete`
     * states it. The receipt is the workout as the sheet opened on it, which is the whole of it: Undo
     * logs it again, and the store works its day out again as it does for any logged workout. Its
     * heart rate is not among what comes back: a typed workout never carries one (D4), whatever
     * readings exist at the time.
     *
     * The receipt is kept only once the delete has happened, so a delete that failed offers no Undo
     * for a workout that is still there.
     */
    fun deleteWorkout() {
        val open = sheet.value ?: return
        val editing = open.editing ?: return
        if (open.saving) return
        write(open, WriteFailure.DELETE) {
            check(typed.delete(editing)) { "no typed workout ${editing.id} to delete" }
            undoable.addLast(editing)
            undo.update { it.copy(offered = true, failed = false) }
        }
    }

    /**
     * Put back the last typed workout deleted here, on its own day, at its own start, with its own
     * figures, effort, note and hidden flag. Through [TypedWorkouts.restore], so it comes back under
     * its own id — a trainer review refers to it by that id (D88), and is its own again — and its day's
     * summary is worked out again in the same transaction.
     *
     * Cleared as it is used, so a second press does not put it back twice. A restore that throws puts
     * the receipt back, so Undo is still there to try again, and the screen says it failed (D8).
     */
    fun undoDelete() {
        val workout = undoable.removeLastOrNull() ?: return
        undo.update { it.copy(offered = undoable.isNotEmpty(), failed = false) }
        viewModelScope.launch {
            try {
                typed.restore(workout)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                problems.record(PROBLEM_KIND, "workout not put back: " + (failure.message ?: failure::class.java.simpleName))
                undoable.addLast(workout)
                undo.update { it.copy(offered = true, failed = true) }
            }
        }
    }

    /**
     * "These are two sessions" (D92): the session's lead is parted from each other witness, for good.
     * The week is observed, so the parted sessions show as soon as the split is stored. A split that
     * fails is logged (D8) and the session stays as it was.
     */
    fun split(session: Workout) {
        viewModelScope.launch {
            try {
                val written = splits.split(session)
                undo.update { it.copy(split = written) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                problems.record(PROBLEM_KIND, "session not split: " + (failure.message ?: failure::class.java.simpleName))
            }
        }
    }

    /**
     * Undo, after "These are two sessions" (D92, decided after review): removes exactly the pairs that
     * split wrote. Cleared as it is used, so a second press does nothing.
     */
    fun undoSplit() {
        val written = undo.value.split
        if (written.isEmpty()) return
        undo.update { it.copy(split = emptyList()) }
        join(written, "split not undone: ")
    }

    /** "Put back together" (D92, decided after review): removes [session]'s splits from what it overlaps. */
    fun putBackTogether(session: Workout) {
        if (session.splits.isEmpty()) return
        undo.update { it.copy(split = emptyList()) }
        join(session.splits, "session not put back together: ")
    }

    /** A join that fails is logged (D8); the sessions stay as they were. */
    private fun join(pairs: List<SessionSplit>, said: String) {
        viewModelScope.launch {
            try {
                splits.join(pairs)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                problems.record(PROBLEM_KIND, said + (failure.message ?: failure::class.java.simpleName))
            }
        }
    }

    /**
     * One write from the sheet. The sheet closes when it lands, and says which write failed when it
     * does not (D8). A store that answers "nothing done" is made to throw by the caller, so it is
     * logged and said like any other failure rather than closing the sheet on nothing.
     *
     * Gated on [open]'s token throughout: every touch of [sheet] here is made only while it still
     * holds that same sheet. A slow write's result lands on nothing once the sheet it started on has
     * been closed or replaced by a later one — it never marks, clears or fails a sheet it did not
     * start from.
     */
    private fun write(open: WorkoutSheetState, kind: WriteFailure, action: suspend () -> Unit) {
        val token = open.token
        sheet.update { current -> if (current?.token == token) current.copy(saving = true, failure = null) else current }
        viewModelScope.launch {
            try {
                action()
                sheet.update { current -> if (current?.token == token) null else current }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val said = if (kind == WriteFailure.DELETE) "workout not deleted: " else "workout not saved: "
                problems.record(PROBLEM_KIND, said + (failure.message ?: failure::class.java.simpleName))
                sheet.update { current -> if (current?.token == token) current.copy(saving = false, failure = kind) else current }
            }
        }
    }

    /**
     * The profile's weight, which the MET estimate is priced on (D76); null with no profile, and null
     * — logged, never thrown (D8) — when the profile cannot be read. The sheet opens either way: it
     * already handles a null weight as "no estimate".
     */
    private suspend fun weight(): Double? = try {
        profiles.profile.first()?.weightKg
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        problems.record(PROBLEM_KIND, "profile not read: " + (failure.message ?: failure::class.java.simpleName))
        null
    }

    init {
        // D138: once per visit — a page rebuilt by a turn of the phone keeps this view model, and its notes.
        viewModelScope.launch { filledLater.value = waitingFiles.opened() }
        // D132: "Log a workout" on the Trainer opens this page with the sheet open for today — once per visit.
        // Marked in the entry's saved state, so the page rebuilt (a turn of the phone) does not open it again.
        if (savedState.get<Boolean>(OPEN_LOG) == true && savedState.get<Boolean>(LOG_OPENED) != true) {
            savedState[LOG_OPENED] = true
            logWorkout()
        }
    }

    /** [split] is what the last split wrote, while its Undo is offered (D92). */
    private data class UndoState(val offered: Boolean = false, val failed: Boolean = false, val split: List<SessionSplit> = emptyList())

    /**
     * The trainer's reviews, for each session's button. The week does not depend on them: a read that
     * fails is logged (D8) and the week is shown with no review buttons (null) rather than not at all
     * — never with every session asking "How did it go?", which would be wrong for one already reviewed.
     */
    private fun reviews(): Flow<List<TrainerReview>?> = trainer.observeReviews().map<List<TrainerReview>, List<TrainerReview>?> { it }.catch { failure ->
        problems.record(PROBLEM_KIND, "reviews not read: " + (failure.message ?: failure::class.java.simpleName))
        emit(null)
    }

    private fun mealsOn(days: List<Long>): Flow<Map<Long, List<Meal>>> =
        combine(days.map { day -> meals.observeDay(day).map { day to it } }) { pairs -> pairs.toMap() }

    companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val PROBLEM_KIND = "movement"

        /** A workout file's outcome that is a problem (D82, D139). */
        const val IMPORT_KIND = WaitingFileMatcher.IMPORT_KIND

        /** D132: the destination's argument — opened from the Trainer's "Log a workout", the sheet opens at once. */
        const val OPEN_LOG = "openLog"

        /** Set once the sheet [OPEN_LOG] asked for has been opened, so it is opened once. */
        private const val LOG_OPENED = "logOpened"
    }
}
