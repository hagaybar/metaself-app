package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.AboutMeStore
import com.metaself.app.data.trainer.AskTheTrainer
import com.metaself.app.data.trainer.ProgrammeStore
import com.metaself.app.data.trainer.TrainerStore
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.domain.trainer.PlanCounting
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeCalendar
import com.metaself.app.domain.trainer.Programmes
import com.metaself.app.domain.trainer.TrainerHome
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.guarded
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
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * The Trainer screen (D85), observed: a review saved or a plan kept elsewhere shows at once, and so
 * does the weekly plan with its ticks (D95). Its one write is the owner's answer about a shorter session
 * (D105); nothing here asks the trainer (D84).
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
    private val problems: ProblemLog,
    private val ask: AskTheTrainer,
) : ViewModel() {

    /**
     * [aboutMe] is the owner's note (D90), "" when none. [answering]: the sessions whose answer to "count it
     * for this?" is being written (D105); [refused] says an answer could not be stored.
     */
    data class State(
        val home: TrainerHome? = null,
        val today: Long = 0,
        val unreadable: Boolean = false,
        val aboutMe: String = "",
        val answering: Set<Long> = emptySet(),
        val refused: ActionRefused? = null,
    )

    /**
     * [writing]: answers being written. [answered]: answers written, whose row stays unpressable while the
     * card still shows it (until the stored answer reaches the card). [refused] is said only while the card
     * is still [refusedOn], the card it was refused against.
     */
    private data class Answering(
        val writing: Set<Long> = emptySet(),
        val answered: Set<Long> = emptySet(),
        val refused: ActionRefused? = null,
        val refusedOn: PlanCard? = null,
    )

    private val answering = MutableStateFlow(Answering())

    private val calendarToday = MutableStateFlow(today().toEpochDay())

    /** The running plan, the record over its weeks, and what it counts from (D105). */
    private data class RunningPlan(val programme: Programme?, val workouts: List<Workout>, val counting: PlanCounting?)

    /** D95: the running plan and the record over its weeks, so a session synced mid-plan ticks at once. */
    private val plan: Flow<RunningPlan> = programmes.observeRunning().flatMapLatest { programme ->
        val start = programme?.startEpochDay
        if (programme == null || start == null) {
            flowOf(RunningPlan(programme, emptyList(), null))
        } else {
            // D105: counted from the chain's first keep, with the answers stored under it — an answer shows at once.
            val root = Programmes.rootOf(programme, programmes.all())
            combine(
                record.observeWorkouts(start, ProgrammeCalendar.lastDay(start, programme.ask.weeks)),
                programmes.observeConfirmations(root.id),
            ) { workouts, answers -> RunningPlan(programme, workouts, PlanCounting(root.id, root.createdAtMillis, answers)) }
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
            ) { recent, reviewed, reviews, kept, running ->
                State(TrainerHome.of(day, now(), recent, reviewed, reviews, kept, running.programme, running.workouts, running.counting), day)
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
        .combine(answering) { state, now ->
            val card = state.home?.plan
            val shown = (card as? PlanCard.Running)?.let { running ->
                running.progress.weeks.getOrNull(running.weekIndex)?.ticks.orEmpty().mapNotNull { it.candidate?.id }.toSet()
            }.orEmpty()
            state.copy(
                answering = now.writing + (now.answered intersect shown),
                refused = now.refused.takeIf { card == now.refusedOn },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State(today = calendarToday.value))

    /** The screen came to the front: on a new day, the last three days are read again from it. */
    fun lookedAt() {
        calendarToday.value = today().toEpochDay()
    }

    /**
     * D105: the owner's answer to "count it for this?" about [workoutId]. A second tap while the first is
     * being written, or after it was written and before the card has caught up, does nothing; a failure is
     * said and logged (D8), and the card is left as it was — the sentence goes when the card changes.
     */
    fun answer(workoutId: Long, confirmed: Boolean) {
        val now = answering.value
        if (workoutId in now.writing || workoutId in now.answered) return
        answering.update { it.copy(writing = it.writing + workoutId, refused = null, refusedOn = null) }
        guarded(
            problems,
            onRefused = {
                val card = state.value.home?.plan
                answering.update { it.copy(writing = it.writing - workoutId, refused = ActionRefused.NOTHING_CHANGED, refusedOn = card) }
            },
        ) {
            ask.answerCandidate(workoutId, confirmed)
            answering.update { it.copy(writing = it.writing - workoutId, answered = it.answered + workoutId) }
        }
    }

    companion object {
        const val PROBLEM_KIND = "trainer"
    }
}
