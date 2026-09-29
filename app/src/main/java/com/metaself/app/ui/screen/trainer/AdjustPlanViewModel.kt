package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.ai.AiSettings
import com.metaself.app.data.ai.AiSettingsStore
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.AskTheTrainer
import com.metaself.app.di.ApplicationScope
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.domain.trainer.Programme
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
 * D97: the running plan's counts, the owner's words, one ask, and the new version with Keep this version
 * and Keep the old one; or Stop this plan, asked first. The trainer is asked only from [adjust], a tap
 * (D84), in [outliving]. [State.finished] closes the page.
 */
@HiltViewModel
class AdjustPlanViewModel @Inject constructor(
    private val ask: AskTheTrainer,
    settings: AiSettingsStore,
    today: Today,
    private val problems: ProblemLog,
    @ApplicationScope private val outliving: CoroutineScope,
) : ViewModel() {

    /**
     * @property writing Keep this version or Stop it was tapped and its write has not finished (and,
     *   once it has, the page is closing): every further tap that would write is ignored, so a second
     *   tap cannot refuse the first one's work as "nothing changed".
     */
    data class State(
        val loading: Boolean = true,
        val running: PlanCard.Running? = null,
        val words: String = "",
        val asking: Boolean = false,
        val failure: EstimateResult? = null,
        val shown: Programme? = null,
        val confirmStop: Boolean = false,
        val finished: Boolean = false,
        val today: Long = 0,
        val ceiling: Int = AiSettings.DEFAULT_CEILING,
        val refused: ActionRefused? = null,
        val writing: Boolean = false,
    ) {
        val canAsk: Boolean get() = running != null && !asking && shown == null && !writing

        /** Stop this plan is not offered while an adjustment is being asked for, or a write is under way. */
        val canStop: Boolean get() = running != null && !asking && !writing
    }

    private val local = MutableStateFlow(State(today = today().toEpochDay()))

    val state: StateFlow<State> = combine(local, settings.settings) { s, ai -> s.copy(ceiling = ai.dailyCeiling) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        guarded(problems, onRefused = { local.update { it.copy(loading = false, refused = ActionRefused.COULD_NOT_OPEN) } }) {
            val running = ask.running()
            local.update { it.copy(loading = false, running = running) }
        }
    }

    fun words(words: String) = local.update { if (it.asking) it else it.copy(words = words, failure = null) }

    fun adjust() {
        if (!local.value.canAsk) return
        val words = local.value.words
        local.update { it.copy(asking = true, failure = null, refused = null) }
        outlived(
            outliving,
            problems,
            onRefused = { local.update { it.copy(asking = false, refused = ActionRefused.NOTHING_CHANGED) } },
            work = { ask.adjust(words) },
        ) { outcome ->
            when (outcome) {
                is AskTheTrainer.Adjusted.Offered -> local.update { it.copy(asking = false, shown = outcome.programme) }
                is AskTheTrainer.Adjusted.Failed -> local.update { it.copy(asking = false, failure = outcome.failure) }
                AskTheTrainer.Adjusted.NotRunning -> local.update { it.copy(asking = false, running = null) }
            }
        }
    }

    fun keepNew() {
        val shown = local.value.shown ?: return
        if (local.value.writing) return
        local.update { it.copy(refused = null, writing = true) }
        guarded(problems, onRefused = { local.update { it.copy(writing = false, refused = ActionRefused.NOTHING_CHANGED) } }) {
            ask.keepAdjusted(shown)
            local.update { it.copy(finished = true) }
        }
    }

    /** Design question 6: nothing more is stored; the new version stays offered. */
    fun keepOld() = local.update { if (it.writing) it else it.copy(finished = true) }

    fun askStop() = local.update { if (it.canStop) it.copy(confirmStop = true) else it }

    fun cancelStop() = local.update { it.copy(confirmStop = false) }

    fun confirmStop() {
        val id = local.value.running?.programme?.id ?: return
        if (!local.value.canStop) return
        local.update { it.copy(confirmStop = false, refused = null, writing = true) }
        guarded(problems, onRefused = { local.update { it.copy(writing = false, refused = ActionRefused.NOTHING_CHANGED) } }) {
            ask.stop(id)
            local.update { it.copy(finished = true) }
        }
    }
}
