package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.ai.AiSettings
import com.metaself.app.data.ai.AiSettingsStore
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.trainer.AskTheTrainer
import com.metaself.app.di.ApplicationScope
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.NextInPlan
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.PlannedTick
import com.metaself.app.domain.trainer.SessionSlot
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.domain.trainer.Wish
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
 * D86: the form, one ask, the suggestion, Keep and Ask again. The trainer is asked only from [ask] —
 * a tap — never from `init` (D84). Opened with [WEEK] and [SESSION] (Get the steps, D123) it is for that planned
 * session; otherwise for the running plan's next open one (D96); kept, its steps belong to it (D122).
 *
 * The ask, and the storing of its suggestion, runs in [outliving]: leaving the screen mid-request does
 * not throw away a paid answer. While it is asked the form cannot be changed.
 */
@HiltViewModel
class PlanSessionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val ask: AskTheTrainer,
    settings: AiSettingsStore,
    private val problems: ProblemLog,
    @ApplicationScope private val outliving: CoroutineScope,
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

    /**
     * @property next the planned session the form is for: the one Get the steps named ([chosen]), else the running
     *   plan's next open one (D96); the form was pre-filled from it. [slot] is its place (D122).
     */
    data class State(
        val form: Form = Form(),
        val asking: Boolean = false,
        val failure: EstimateResult? = null,
        val shown: TrainerPlan? = null,
        val kept: Boolean = false,
        val ceiling: Int = AiSettings.DEFAULT_CEILING,
        val refused: ActionRefused? = null,
        val next: PlannedTick? = null,
        val slot: SessionSlot? = null,
        val chosen: Boolean = false,
    ) {
        val canAsk: Boolean get() = form.answers() != null && !asking
    }

    /** D123: the place Get the steps named; null opened from anywhere else. */
    private val named: Pair<Int, Int>? = run {
        val week = savedState.get<Int>(WEEK) ?: NONE
        val session = savedState.get<Int>(SESSION) ?: NONE
        if (week >= 0 && session >= 0) week to session else null
    }

    private val local = MutableStateFlow(State(chosen = named != null))

    val state: StateFlow<State> = combine(local, settings.settings) { s, ai -> s.copy(ceiling = ai.dailyCeiling) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        // D96, D122: the planned session the form is for is read once; the form is pre-filled from it while
        // untouched. A convenience: a failed read leaves the form empty and for no planned session (and is logged).
        guarded(problems, onRefused = {}) {
            val planned = if (named != null) ask.plannedAt(named.first, named.second) else ask.nextFor()
            planned ?: return@guarded
            local.update { now ->
                val filled = if (now.form != Form() || now.asking) {
                    now.form
                } else {
                    Form(time = NextInPlan.time(planned.tick.session.minutes), wish = NextInPlan.wish(planned.tick.session.effort))
                }
                now.copy(next = planned.tick, slot = planned.slot, form = filled)
            }
        }
    }

    fun change(form: Form) = local.update { if (it.asking) it else it.copy(form = form, failure = null) }

    fun ask() {
        val answers = local.value.form.answers() ?: return
        if (local.value.asking) return
        local.update { it.copy(asking = true, failure = null, refused = null) }
        outlived(
            outliving,
            problems,
            onRefused = { local.update { it.copy(asking = false, refused = ActionRefused.NOTHING_CHANGED) } },
            // Opened for a named session, the ask is for it (a stale place attaches to nothing); otherwise the
            // trainer's own reading of the next open session at the moment of asking.
            work = { ask.suggest(answers, local.value.slot.takeIf { local.value.chosen }) },
        ) { outcome ->
            when (outcome) {
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

    /** Back to the form, the answers kept (D86). */
    fun askAgain() = local.update { it.copy(shown = null, kept = false, failure = null, refused = null) }

    companion object {
        /** D123: the navigation arguments naming a planned session's place; [NONE] names none. */
        const val WEEK = "week"
        const val SESSION = "session"
        const val NONE = -1
    }
}
