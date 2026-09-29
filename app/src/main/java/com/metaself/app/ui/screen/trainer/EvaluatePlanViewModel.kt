package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.ai.AiSettings
import com.metaself.app.data.ai.AiSettingsStore
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.AskTheTrainer
import com.metaself.app.di.ApplicationScope
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeCalendar
import com.metaself.app.domain.trainer.ProgrammeStatus
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
 * D93, D94: the form, one ask, the evaluation with its plan, Keep and Ask again — or, opened with
 * [RUNNING], the running plan with its ticks and its evaluation (design question 3). The trainer is asked
 * only from [ask], a tap (D84); the ask and the storing of its answer run in [outliving], so leaving the
 * page mid-request does not throw away a paid answer.
 */
@HiltViewModel
class EvaluatePlanViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val ask: AskTheTrainer,
    settings: AiSettingsStore,
    today: Today,
    private val problems: ProblemLog,
    @ApplicationScope private val outliving: CoroutineScope,
) : ViewModel() {

    /** The form's two rows and its words; a row is null until answered. */
    data class Form(val weeks: Int? = null, val perWeek: Int? = null, val words: String = "") {
        fun ask(): ProgrammeAsk? {
            return ProgrammeAsk(weeks ?: return null, perWeek ?: return null, words)
        }
    }

    /**
     * @property shown the answer just arrived: OFFERED until kept.
     * @property onRunning opened on the running plan; [running] is it (null when none runs) and
     *   [evaluation] its chain's.
     */
    data class State(
        val loading: Boolean = false,
        val form: Form = Form(),
        val asking: Boolean = false,
        val failure: EstimateResult? = null,
        val shown: Programme? = null,
        val kept: Boolean = false,
        val onRunning: Boolean = false,
        val running: PlanCard.Running? = null,
        val evaluation: Evaluation? = null,
        val today: Long = 0,
        val ceiling: Int = AiSettings.DEFAULT_CEILING,
        val refused: ActionRefused? = null,
    ) {
        val canAsk: Boolean get() = form.ask() != null && !asking

        /** The Monday keeping the answer today would start it on (D94). */
        val startIfKept: Long get() = shown?.startEpochDay ?: ProgrammeCalendar.startFor(today)
    }

    private val openedOnRunning = savedState.get<String>(SHOW) == RUNNING

    private val local = MutableStateFlow(State(loading = openedOnRunning, onRunning = openedOnRunning, today = today().toEpochDay()))

    val state: StateFlow<State> = combine(local, settings.settings) { s, ai -> s.copy(ceiling = ai.dailyCeiling) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        if (openedOnRunning) {
            guarded(problems, onRefused = { local.update { it.copy(loading = false, refused = ActionRefused.COULD_NOT_OPEN) } }) {
                val running = ask.running()
                val evaluation = running?.let { ask.evaluationOf(it.programme) }
                local.update { it.copy(loading = false, running = running, evaluation = evaluation) }
            }
        }
    }

    fun change(form: Form) = local.update { if (it.asking) it else it.copy(form = form, failure = null) }

    fun ask() {
        val answers = local.value.form.ask() ?: return
        if (local.value.asking) return
        local.update { it.copy(asking = true, failure = null, refused = null) }
        outlived(
            outliving,
            problems,
            onRefused = { local.update { it.copy(asking = false, refused = ActionRefused.NOTHING_CHANGED) } },
            work = { ask.evaluate(answers) },
        ) { outcome ->
            when (outcome) {
                is AskTheTrainer.Evaluated.Offered -> local.update { it.copy(asking = false, shown = outcome.programme, kept = false) }
                is AskTheTrainer.Evaluated.Failed -> local.update { it.copy(asking = false, failure = outcome.failure) }
            }
        }
    }

    fun keep() {
        val shown = local.value.shown ?: return
        if (local.value.kept) return
        local.update { it.copy(refused = null) }
        guarded(problems, onRefused = { local.update { it.copy(refused = ActionRefused.NOTHING_CHANGED) } }) {
            val start = ask.keepProgramme(shown.id)
            local.update { it.copy(kept = true, shown = shown.copy(startEpochDay = start, status = ProgrammeStatus.RUNNING)) }
        }
    }

    /** Back to the form, the answers kept (D94); the answer stays stored, offered. */
    fun askAgain() = local.update { it.copy(shown = null, kept = false, failure = null, refused = null) }

    companion object {
        /** The navigation argument: [RUNNING] opens on the running plan; anything else on the form. */
        const val SHOW = "show"
        const val FORM = "form"
        const val RUNNING = "running"
    }
}
