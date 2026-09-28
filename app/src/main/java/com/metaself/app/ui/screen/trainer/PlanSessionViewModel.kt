package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.ai.AiSettings
import com.metaself.app.data.ai.AiSettingsStore
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.trainer.AskTheTrainer
import com.metaself.app.data.trainer.TrainerStore
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.domain.trainer.Wish
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
 * D86: the form, one ask, the suggestion, Keep and Ask again. The trainer is asked only from [ask] —
 * a tap — never from `init` (D84).
 */
@HiltViewModel
class PlanSessionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val ask: AskTheTrainer,
    private val store: TrainerStore,
    settings: AiSettingsStore,
    private val problems: ProblemLog,
) : ViewModel() {

    /** The form's four rows and its words; a row is null until answered. */
    data class Form(
        val activity: PlanActivity? = null,
        val time: TimeAvailable? = null,
        val feeling: Feeling? = null,
        val wish: Wish? = null,
        val words: String = "",
    ) {
        /** The answers, once all four rows are answered. */
        fun answers(): PlanAnswers? {
            return PlanAnswers(activity ?: return null, time ?: return null, feeling ?: return null, wish ?: return null, words)
        }
    }

    data class State(
        val form: Form = Form(),
        val asking: Boolean = false,
        val failure: EstimateResult? = null,
        val shown: TrainerPlan? = null,
        val kept: Boolean = false,
        val ceiling: Int = AiSettings.DEFAULT_CEILING,
        val refused: ActionRefused? = null,
    ) {
        val canAsk: Boolean get() = form.answers() != null && !asking
    }

    private val local = MutableStateFlow(State())

    val state: StateFlow<State> = combine(local, settings.settings) { s, ai -> s.copy(ceiling = ai.dailyCeiling) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    init {
        if (savedState.get<String>(SHOW) == KEPT) {
            guarded(problems, onRefused = { local.update { it.copy(refused = ActionRefused.COULD_NOT_OPEN) } }) {
                store.keptPlan()?.let { kept -> local.update { it.copy(shown = kept, kept = true) } }
            }
        }
    }

    fun change(form: Form) = local.update { it.copy(form = form, failure = null) }

    fun ask() {
        val answers = local.value.form.answers() ?: return
        if (local.value.asking) return
        local.update { it.copy(asking = true, failure = null, refused = null) }
        guarded(problems, onRefused = { local.update { it.copy(asking = false, refused = ActionRefused.NOTHING_CHANGED) } }) {
            when (val outcome = ask.suggest(answers)) {
                is AskTheTrainer.Suggested.Planned -> local.update { it.copy(asking = false, shown = outcome.plan, kept = false) }
                is AskTheTrainer.Suggested.Failed -> local.update { it.copy(asking = false, failure = outcome.failure) }
            }
        }
    }

    fun keep() {
        val plan = local.value.shown ?: return
        local.update { it.copy(refused = null) }
        guarded(problems, onRefused = { local.update { it.copy(refused = ActionRefused.NOTHING_CHANGED) } }) {
            ask.keep(plan.id)
            local.update { it.copy(kept = true) }
        }
    }

    /** Back to the form, the answers kept (D86); from a kept plan opened on its own, the form is empty. */
    fun askAgain() = local.update { it.copy(shown = null, kept = false, failure = null, refused = null) }

    companion object {
        /** The navigation argument: [KEPT] opens on the kept plan (design question 19); anything else on the form. */
        const val SHOW = "show"
        const val FORM = "form"
        const val KEPT = "kept"
    }
}
