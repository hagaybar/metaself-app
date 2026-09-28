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
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanMatch
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.guarded
import dagger.hilt.android.lifecycle.HiltViewModel
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
 */
@HiltViewModel
class ReviewSessionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val ask: AskTheTrainer,
    private val store: TrainerStore,
    settings: AiSettingsStore,
    today: Today,
    private val problems: ProblemLog,
) : ViewModel() {

    /**
     * @property gone the session is not in the record: at opening, or it left while the form was open —
     *   then [saved] is also true, because the words were kept anyway (D88).
     */
    data class State(
        val workout: Workout? = null,
        val plan: TrainerPlan? = null,
        val felt: Felt? = null,
        val words: String = "",
        val working: Boolean = false,
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

    fun feel(felt: Felt) = local.update { it.copy(felt = felt, saved = false, refused = null) }

    fun words(words: String) = local.update { it.copy(words = words, saved = false, refused = null) }

    fun notThisPlan() = local.update { it.copy(plan = null, saved = false) }

    fun justSave() = save(withFeedback = false)

    fun saveAndAsk() = save(withFeedback = true)

    private fun save(withFeedback: Boolean) {
        val now = local.value
        if (!now.canSave) return
        local.update { it.copy(working = true, failure = null, refused = null) }
        guarded(problems, onRefused = { local.update { it.copy(working = false, refused = ActionRefused.MAYBE_PARTIAL) } }) {
            when (val outcome = ask.save(workoutId, now.felt, now.words, now.plan?.id, withFeedback)) {
                is AskTheTrainer.Reviewed.Saved -> local.update { it.copy(working = false, saved = true) }
                is AskTheTrainer.Reviewed.WithFeedback ->
                    local.update { it.copy(working = false, saved = true, feedback = outcome.review.feedback) }
                is AskTheTrainer.Reviewed.NoFeedback ->
                    local.update { it.copy(working = false, saved = true, failure = outcome.failure) }
                is AskTheTrainer.Reviewed.SessionGone ->
                    local.update { it.copy(working = false, saved = true, gone = true) }
            }
        }
    }

    companion object {
        const val WORKOUT_ID = "workoutId"
    }
}
