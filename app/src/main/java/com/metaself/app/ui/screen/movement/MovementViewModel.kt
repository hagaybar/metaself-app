package com.metaself.app.ui.screen.movement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.day.MealRepository
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.time.Today
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.movement.MovementWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * This week from the stored health record and the meal log (D73–D75), and which day is open.
 *
 * Everything is observed, so a copy of the health record, or a meal logged, while the screen is open
 * shows at once. Eaten is read through [MealRepository.observeDay] — the day screen's own read — and
 * summed by `DayTotals`, so the two screens cannot disagree about a day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MovementViewModel @Inject constructor(
    private val record: MovementRecord,
    private val meals: MealRepository,
    private val today: Today,
    private val problems: ProblemLog,
) : ViewModel() {

    private val calendarToday = MutableStateFlow(today().toEpochDay())

    /** Today starts open (D73). */
    private val openDay = MutableStateFlow<Long?>(calendarToday.value)

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

    val state: StateFlow<MovementUiState> = combine(week, openDay) { built, open ->
        if (built == null) {
            MovementUiState(unreadable = true, openDay = open)
        } else {
            MovementUiState(week = built, openDay = open)
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

    private fun mealsOn(days: List<Long>): Flow<Map<Long, List<Meal>>> =
        combine(days.map { day -> meals.observeDay(day).map { day to it } }) { pairs -> pairs.toMap() }

    companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val PROBLEM_KIND = "movement"
    }
}
