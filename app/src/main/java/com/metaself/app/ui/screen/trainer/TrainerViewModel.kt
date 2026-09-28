package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.TrainerStore
import com.metaself.app.domain.trainer.TrainerHome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The Trainer screen (D85), observed: a review saved or a plan kept elsewhere shows at once. It only
 * reads; nothing here asks the trainer (D84).
 *
 * Today is read again each time the screen comes to the front ([lookedAt]), so a screen left open past
 * midnight moves its last three days with the calendar, as the Movement screen does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrainerViewModel @Inject constructor(
    record: MovementRecord,
    store: TrainerStore,
    private val today: Today,
    now: Now,
    problems: ProblemLog,
) : ViewModel() {

    data class State(val home: TrainerHome? = null, val today: Long = 0, val unreadable: Boolean = false)

    private val calendarToday = MutableStateFlow(today().toEpochDay())

    val state: StateFlow<State> = calendarToday
        .flatMapLatest { day ->
            combine(
                record.observeWorkouts(day - (TrainerHome.WAITING_DAYS - 1), day),
                store.observeReviewedWorkouts(),
                store.observeReviews(),
                store.observeKeptPlan(),
            ) { recent, reviewed, reviews, kept ->
                State(TrainerHome.of(day, now(), recent, reviewed, reviews, kept), day)
            }
                .catch { failure ->
                    problems.record(PROBLEM_KIND, failure.message ?: failure::class.java.simpleName)
                    emit(State(today = day, unreadable = true))
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State(today = calendarToday.value))

    /** The screen came to the front: on a new day, the last three days are read again from it. */
    fun lookedAt() {
        calendarToday.value = today().toEpochDay()
    }

    companion object {
        const val PROBLEM_KIND = "trainer"
    }
}
