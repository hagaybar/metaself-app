package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.ai.AiSettings
import com.metaself.app.data.ai.AiSettingsStore
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.AskTheTrainer
import com.metaself.app.data.trainer.TrainerStore
import com.metaself.app.di.ApplicationScope
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanMatch
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.guarded
import com.metaself.app.ui.outlived
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * D87 for one session: its figures, the matched plan, the felt effort and words, then feedback. The
 * trainer is asked only from [saveAndAsk] — a tap — never from `init` (D84). The felt effort is the
 * review's own; it never overwrites the workout's effort (D77).
 *
 * A save, and the feedback it asks for, runs in [outliving]: leaving the screen mid-request does not
 * throw away a paid answer — it is stored all the same, and shown if the screen is still open. While a
 * save is under way nothing on the form can be changed, so what is saved is what is on screen.
 */
@HiltViewModel
class ReviewSessionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val ask: AskTheTrainer,
    private val store: TrainerStore,
    settings: AiSettingsStore,
    today: Today,
    private val problems: ProblemLog,
    @ApplicationScope private val outliving: CoroutineScope,
) : ViewModel() {

    /**
     * @property gone the session is not in the record: at opening, or it left while the form was open —
     *   then [saved] is also true, because the words were kept anyway (D88).
     * @property askingTrainer the save under way also asks for feedback; false for Just save.
     */
    data class State(
        val workout: Workout? = null,
        val plan: TrainerPlan? = null,
        val felt: Felt? = null,
        val words: String = "",
        val working: Boolean = false,
        val askingTrainer: Boolean = false,
        val saved: Boolean = false,
        val feedback: Feedback? = null,
        val failure: EstimateResult? = null,
        val gone: Boolean = false,
        val today: Long = 0,
        val ceiling: Int = AiSettings.DEFAULT_CEILING,
        val refused: ActionRefused? = null,
    ) {
        val canSave: Boolean get() = workout != null && !gone && !working && (felt != null || words.isNotBlank())
    }

    private val workoutId: Long = requireNotNull(savedState.get<Long>(WORKOUT_ID)) { "a review needs its session" }
    private val local = MutableStateFlow(State(today = today().toEpochDay()))

    val state: StateFlow<State> = combine(local, settings.settings) { s, ai -> s.copy(ceiling = ai.dailyCeiling) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        guarded(problems, onRefused = { local.update { it.copy(refused = ActionRefused.COULD_NOT_OPEN) } }) {
            val workout = store.workout(workoutId)
            if (workout == null) {
                local.update { it.copy(gone = true) }
            } else {
                val review = store.reviewOf(workoutId)
                // A review keeps the plan it was saved with, kept or not; a new one is offered the kept plan (D87).
                val plan = if (review != null) {
                    review.planId?.let { store.plans(listOf(it))[it] }
                } else {
                    PlanMatch.forSession(store.keptPlan(), workout)
                }
                local.update {
                    it.copy(workout = workout, plan = plan, felt = review?.felt, words = review?.words.orEmpty(), feedback = review?.feedback)
                }
            }
        }
    }

    fun feel(felt: Felt) = edit { it.copy(felt = felt) }

    fun words(words: String) = edit { it.copy(words = words) }

    fun notThisPlan() = edit { it.copy(plan = null) }

    /**
     * A change to the form: what was said about the last save — saved, or saved without feedback — no
     * longer holds for what is on screen, so it goes. Nothing changes while a save is under way.
     */
    private fun edit(change: (State) -> State) = local.update { now ->
        if (now.working) now else change(now).copy(saved = false, failure = null, refused = null)
    }

    fun justSave() = save(withFeedback = false)

    fun saveAndAsk() = save(withFeedback = true)

    private fun save(withFeedback: Boolean) {
        val now = local.value
        if (!now.canSave) return
        local.update { it.copy(working = true, askingTrainer = withFeedback, failure = null, refused = null) }
        outlived(
            outliving,
            problems,
            onRefused = { local.update { it.copy(working = false, askingTrainer = false, refused = ActionRefused.MAYBE_PARTIAL) } },
            work = { ask.save(workoutId, now.felt, now.words, now.plan?.id, withFeedback) },
        ) { outcome ->
            local.update { it.copy(working = false, askingTrainer = false, saved = true) }
            when (outcome) {
                is AskTheTrainer.Reviewed.Saved -> Unit
                is AskTheTrainer.Reviewed.WithFeedback -> local.update { it.copy(feedback = outcome.review.feedback) }
                is AskTheTrainer.Reviewed.NoFeedback -> local.update { it.copy(failure = outcome.failure) }
                is AskTheTrainer.Reviewed.SessionGone -> local.update { it.copy(gone = true) }
            }
        }
    }

    companion object {
        const val WORKOUT_ID = "workoutId"
    }
}
