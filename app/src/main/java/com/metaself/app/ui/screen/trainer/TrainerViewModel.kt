package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.AboutMeStore
import com.metaself.app.data.trainer.ProgrammeStore
import com.metaself.app.data.trainer.TrainerStore
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeCalendar
import com.metaself.app.domain.trainer.TrainerHome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The Trainer screen (D85), observed: a review saved or a plan kept elsewhere shows at once, and so
 * does the weekly plan with its ticks (D95). It only reads; nothing here asks the trainer (D84).
 *
 * Today is read again each time the screen comes to the front ([lookedAt]), so a screen left open past
 * midnight moves its last three days with the calendar, as the Movement screen does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrainerViewModel @Inject constructor(
    record: MovementRecord,
    store: TrainerStore,
    programmes: ProgrammeStore,
    aboutMe: AboutMeStore,
    private val today: Today,
    now: Now,
    problems: ProblemLog,
) : ViewModel() {

    /** [aboutMe] is the owner's note (D90), "" when none. */
    data class State(
        val home: TrainerHome? = null,
        val today: Long = 0,
        val unreadable: Boolean = false,
        val aboutMe: String = "",
    )

    private val calendarToday = MutableStateFlow(today().toEpochDay())

    /** D95: the running plan and the record over its weeks, so a session synced mid-plan ticks at once. */
    private val plan: Flow<Pair<Programme?, List<Workout>>> = programmes.observeRunning().flatMapLatest { programme ->
        val start = programme?.startEpochDay
        if (programme == null || start == null) {
            flowOf<Pair<Programme?, List<Workout>>>(programme to emptyList())
        } else {
            record.observeWorkouts(start, ProgrammeCalendar.lastDay(start, programme.ask.weeks)).map { programme to it }
        }
    }

    val state: StateFlow<State> = calendarToday
        .flatMapLatest { day ->
            combine(
                record.observeWorkouts(day - (TrainerHome.WAITING_DAYS - 1), day),
                store.observeReviewedWorkouts(),
                store.observeReviews(),
                store.observeKeptPlan(),
                plan,
            ) { recent, reviewed, reviews, kept, (running, planWorkouts) ->
                State(TrainerHome.of(day, now(), recent, reviewed, reviews, kept, running, planWorkouts), day)
            }
                .catch { failure ->
                    problems.record(PROBLEM_KIND, failure.message ?: failure::class.java.simpleName)
                    emit(State(today = day, unreadable = true))
                }
        }
        .combine(
            aboutMe.note.catch { failure ->
                problems.record(PROBLEM_KIND, "note not read: " + (failure.message ?: failure::class.java.simpleName))
                emit("")
            },
        ) { state, note -> state.copy(aboutMe = note) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State(today = calendarToday.value))

    /** The screen came to the front: on a new day, the last three days are read again from it. */
    fun lookedAt() {
        calendarToday.value = today().toEpochDay()
    }

    companion object {
        const val PROBLEM_KIND = "trainer"
    }
}
