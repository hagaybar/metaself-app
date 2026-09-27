package com.metaself.app.ui.screen.movement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.day.MealRepository
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.health.TypedWorkouts
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutDraft
import com.metaself.app.domain.movement.WorkoutSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import java.time.ZoneId
import javax.inject.Inject

/**
 * This week from the stored health record and the meal log (D73–D75), and which day is open.
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
class MovementViewModel @Inject constructor(
    private val record: MovementRecord,
    private val meals: MealRepository,
    private val today: Today,
    private val problems: ProblemLog,
    private val typed: TypedWorkouts,
    private val profiles: ProfileRepository,
    private val now: Now,
) : ViewModel() {

    private val calendarToday = MutableStateFlow(today().toEpochDay())

    /** Today starts open (D73). */
    private val openDay = MutableStateFlow<Long?>(calendarToday.value)

    /** The log-a-workout sheet, when it is open (D76). */
    private val sheet = MutableStateFlow<WorkoutSheetState?>(null)

    /**
     * Every typed workout deleted on this screen, oldest first. Undo takes the most recent, which is
     * the order they left in — the shape the weight screen and the record screen use for theirs.
     */
    private val undoable = ArrayDeque<Workout>()

    /** Whether Undo is offered, and whether the last one failed. */
    private val undo = MutableStateFlow(UndoState())

    /** Every sheet this screen has opened gets the next one of these (see [WorkoutSheetState.token]). */
    private var nextSheetToken = 0L

    /** Null means the read failed; the failure is logged where it happened, below. */
    private val week: Flow<MovementWeek?> = calendarToday.flatMapLatest { day ->
        val monday = MovementWeek.mondayOf(day)
        val built: Flow<MovementWeek?> = combine(
            // Five weeks: this one, and the four the foot of the screen sums (D74).
            record.observeDays(monday - 7L * MovementWeek.PREVIOUS_WEEKS, day),
            record.observeWorkouts(monday, day),
            mealsOn((monday..day).toList()),
        ) { days, workouts, mealsByDay -> MovementWeek.of(day, days, workouts, mealsByDay) }
        built
    }
        .catch { failure ->
            problems.record(PROBLEM_KIND, failure.message ?: failure::class.java.simpleName)
            emit(null)
        }

    val state: StateFlow<MovementUiState> = combine(week, openDay, sheet, undo) { built, open, sheetNow, undoNow ->
        if (built == null) {
            MovementUiState(unreadable = true, openDay = open, sheet = sheetNow)
        } else {
            MovementUiState(
                week = built,
                openDay = open,
                sheet = sheetNow,
                canUndo = undoNow.offered,
                undoFailed = undoNow.failed,
            )
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = MovementUiState(),
        )

    /** A day's row was tapped: open it, closing any other — or close it, if it was the open one. */
    fun toggle(epochDay: Long) {
        openDay.update { open -> if (open == epochDay) null else epochDay }
    }

    /**
     * The screen came to the front. A screen left open past midnight moves to the new day, and opens
     * it, as the day pager does; on the same day nothing changes.
     */
    fun lookedAt() {
        val now = today().toEpochDay()
        if (now == calendarToday.value) return
        openDay.value = now
        calendarToday.value = now
    }

    /** "Log a workout" (D76): an empty sheet, for the open day — today when none is open. */
    fun logWorkout() {
        val day = openDay.value ?: calendarToday.value
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
     * figures, effort, note and hidden flag. Through [TypedWorkouts.log], so it comes back under a new
     * id — nothing else refers to a typed workout's id — and its day's summary is worked out again in
     * the same transaction.
     *
     * Cleared as it is used, so a second press does not put it back twice. A restore that throws puts
     * the receipt back, so Undo is still there to try again, and the screen says it failed (D8).
     */
    fun undoDelete() {
        val workout = undoable.removeLastOrNull() ?: return
        undo.value = UndoState(offered = undoable.isNotEmpty())
        viewModelScope.launch {
            try {
                typed.log(workout)
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

    private fun mealsOn(days: List<Long>): Flow<Map<Long, List<Meal>>> =
        combine(days.map { day -> meals.observeDay(day).map { day to it } }) { pairs -> pairs.toMap() }

    companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val PROBLEM_KIND = "movement"
    }
}
