package com.metaself.app.ui.screen.movement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.day.MealRepository
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.health.WorkoutFileImporter
import com.metaself.app.data.health.TypedWorkouts
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.TrainerReviews
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.movement.ImportOutcome
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutDraft
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.trainer.TrainerReview
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
 * write that saves it. A typed workout reaches the week through the record it is written to, like any
 * other workout. A typed workout deleted here can be put back with Undo, the app's rule for a thing it
 * can rebuild (the weight screen's `WeightViewModel.delete`).
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
    ) : this(record, meals, today, problems, typed, profiles, now, files, Dispatchers.IO, trainer)

    private val calendarToday = MutableStateFlow(today().toEpochDay())

    /** Today starts open (D73). */
    private val openDay = MutableStateFlow<Long?>(calendarToday.value)

    /** The Monday of the week shown; null is this week, whichever week that is by now (D83). */
    private val shownMonday = MutableStateFlow<Long?>(null)

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

    /** Every sheet this screen has opened gets the next one of these (see [WorkoutSheetState.token]). */
    private var nextSheetToken = 0L

    /** The week shown and the record's earliest day, which bounds how far back ‹ goes (D83). */
    private data class Read(val week: MovementWeek, val earliest: Long?, val reviews: Map<Long, TrainerReview>?)

    /**
     * Null means the read failed; the failure is logged where it happened, below. The earliest day
     * is part of the same read, so it fails the way the rest does (D8).
     */
    private val week: Flow<Read?> = combine(calendarToday, shownMonday) { day, chosen -> day to chosen }
        .flatMapLatest { (day, chosen) ->
            val monday = chosen ?: MovementWeek.mondayOf(day)
            // This week stops at today; an earlier one is all seven days.
            val last = if (chosen == null) day else monday + 6
            val built: Flow<Read?> = combine(
                // Five weeks: the one shown, and the four the foot of the screen sums (D74).
                record.observeDays(monday - 7L * MovementWeek.PREVIOUS_WEEKS, last),
                record.observeWorkouts(monday, last),
                mealsOn((monday..last).toList()),
                record.observeEarliestDay(),
                reviews(),
            ) { days, workouts, mealsByDay, earliest, reviews ->
                Read(MovementWeek.of(day, days, workouts, mealsByDay, monday), earliest, reviews?.associateBy { it.workoutId })
            }
            built
        }
        .catch { failure ->
            problems.record(PROBLEM_KIND, failure.message ?: failure::class.java.simpleName)
            emit(null)
        }

    val state: StateFlow<MovementUiState> = combine(week, openDay, sheet, undo, fileImport) { read, open, sheetNow, undoNow, file ->
        if (read == null) {
            MovementUiState(unreadable = true, openDay = open, sheet = sheetNow, fileImport = file)
        } else {
            val built = read.week
            MovementUiState(
                week = built,
                canGoEarlier = read.earliest != null && MovementWeek.mondayOf(read.earliest) < built.monday,
                canGoLater = !built.isCurrent,
                // D76 on this week; on an earlier one only an open day will do (D83).
                logDay = open ?: built.days.firstOrNull()?.epochDay?.takeIf { built.isCurrent },
                openDay = open,
                sheet = sheetNow,
                canUndo = undoNow.offered,
                undoFailed = undoNow.failed,
                fileImport = file,
                reviews = read.reviews,
            )
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = MovementUiState(),
        )

    /** A workout file was picked or shared (D82): read it and fill in, or say why not. */
    fun importFile(uri: String) = fileStep { files.import(uri) }

    /** "Add it as a workout", for the file that matched nothing. */
    fun addFromFile() {
        val file = (fileImport.value?.outcome as? ImportOutcome.NoMatch)?.file ?: return
        fileStep { files.add(file) }
    }

    /** One of several matching workouts was chosen for the file. */
    fun chooseForFile(id: Long) {
        val file = (fileImport.value?.outcome as? ImportOutcome.Several)?.file ?: return
        fileStep { files.choose(file, id) }
    }

    /** The file's line was dismissed. */
    fun dismissFile() {
        fileImport.value = null
    }

    /**
     * One step at a time; the importer never throws (D8), so the step always lands. Every outcome is
     * also written to the problem log as kind [IMPORT_KIND] (D82, amended), so an import can be
     * confirmed after the line on screen has been dismissed. The write is moved off the caller's own
     * dispatcher (Main) the same way `HealthRecordSync.note` does, since the log writes a file (D8).
     */
    private fun fileStep(step: suspend () -> ImportOutcome) {
        if (fileImport.value?.working == true) return
        fileImport.value = FileImportState(outcome = fileImport.value?.outcome, working = true)
        viewModelScope.launch {
            val outcome = step()
            val logged = WorkoutFileWording.logLine(outcome, today().toEpochDay())
            withContext(ioDispatcher) { problems.record(IMPORT_KIND, logged) }
            fileImport.value = FileImportState(outcome = outcome)
        }
    }

    /** A day's row was tapped: open it, closing any other — or close it, if it was the open one. */
    fun toggle(epochDay: Long) {
        openDay.update { open -> if (open == epochDay) null else epochDay }
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

    /** A typed workout's line was tapped: the sheet, filled. A synced workout is not editable here (D76). */
    fun openWorkout(workout: Workout) {
        if (workout.source != WorkoutSource.TYPED) return
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
            undo.value = UndoState(offered = true)
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
        undo.value = UndoState(offered = undoable.isNotEmpty())
        viewModelScope.launch {
            try {
                typed.restore(workout)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                problems.record(PROBLEM_KIND, "workout not put back: " + (failure.message ?: failure::class.java.simpleName))
                undoable.addLast(workout)
                undo.value = UndoState(offered = true, failed = true)
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

    private data class UndoState(val offered: Boolean = false, val failed: Boolean = false)

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

        /** A workout file's outcome, success or not (D82, amended). */
        const val IMPORT_KIND = "import"
    }
}
