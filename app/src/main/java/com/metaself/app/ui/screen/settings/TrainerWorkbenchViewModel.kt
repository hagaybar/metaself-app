package com.metaself.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.InstructionFiles
import com.metaself.app.data.trainer.TrainerWorkbench
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.trainer.TrainerPath
import com.metaself.app.domain.trainer.WorkbenchReply
import com.metaself.app.ui.screen.trainer.EvaluatePlanViewModel
import com.metaself.app.ui.screen.trainer.PlanSessionViewModel
import com.metaself.app.ui.trainer.WorkbenchWording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Test the trainer's instructions" (D106), on the page's own back-stack entry: the loaded text lives in
 * [instructions] and goes when the page is left. Nothing is stored; the forms are the trainer screens' own.
 */
@HiltViewModel
class TrainerWorkbenchViewModel @Inject constructor(
    private val workbench: TrainerWorkbench,
    private val files: InstructionFiles,
    private val today: Today,
) : ViewModel() {

    /**
     * @property fileName the loaded file's name; null sends the app's own instructions.
     * @property sent the last request body, verbatim, for Copy what was sent.
     * @property notice a sentence for a run that sent nothing (no plan, session gone, record unreadable).
     */
    data class State(
        val loaded: Boolean = false,
        val today: Long = 0,
        val path: TrainerPath = TrainerPath.FEEDBACK,
        val sessions: List<Workout> = emptyList(),
        val workoutId: Long? = null,
        val plan: PlanSessionViewModel.Form = PlanSessionViewModel.Form(),
        val evaluate: EvaluatePlanViewModel.Form = EvaluatePlanViewModel.PRESET,
        val adjustWords: String = "",
        val planRuns: Boolean = false,
        val fileName: String? = null,
        val fileMessage: String? = null,
        val sending: Boolean = false,
        val reply: String? = null,
        val sent: String? = null,
        val failure: EstimateResult? = null,
        val notice: String? = null,
    ) {
        val inputs: TrainerWorkbench.Inputs?
            get() = when (path) {
                TrainerPath.FEEDBACK -> workoutId?.let { TrainerWorkbench.Inputs.Feedback(it) }
                TrainerPath.PLAN -> plan.answers()?.let { TrainerWorkbench.Inputs.Plan(it) }
                TrainerPath.EVALUATE -> evaluate.ask()?.let { TrainerWorkbench.Inputs.Evaluate(it) }
                TrainerPath.ADJUST -> if (planRuns) TrainerWorkbench.Inputs.Adjust(adjustWords) else null
            }

        val canSend: Boolean get() = loaded && !sending && inputs != null
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** The loaded file's text, for this visit only; null sends the app's own. */
    private var instructions: String? = null

    init {
        viewModelScope.launch { read() }
    }

    private suspend fun read() {
        val sessions = orNull { workbench.sessions() }.orEmpty()
        val runs = orNull { workbench.planRuns() } ?: false
        _state.update { it.copy(loaded = true, today = today().toEpochDay(), sessions = sessions, planRuns = runs) }
    }

    /** A new path clears the last reply, so a reply is never shown under a path that did not ask it. */
    fun pickPath(path: TrainerPath) = _state.update {
        it.copy(path = path, reply = null, sent = null, failure = null, notice = null)
    }

    fun pickSession(workoutId: Long) = _state.update { it.copy(workoutId = workoutId) }
    fun changePlan(form: PlanSessionViewModel.Form) = _state.update { it.copy(plan = form) }
    fun changeEvaluate(form: EvaluatePlanViewModel.Form) = _state.update { it.copy(evaluate = form) }
    fun changeAdjustWords(words: String) = _state.update { it.copy(adjustWords = words) }

    fun load(uri: String) {
        viewModelScope.launch {
            val text = files.read(uri)
            if (text.isNullOrBlank()) {
                _state.update { it.copy(fileMessage = WorkbenchWording.COULD_NOT_READ) }
                return@launch
            }
            instructions = text
            val name = files.nameOf(uri) ?: WorkbenchWording.UNNAMED
            _state.update { it.copy(fileName = name, fileMessage = null) }
        }
    }

    fun useAppOwn() {
        instructions = null
        _state.update { it.copy(fileName = null, fileMessage = null) }
    }

    fun saveAppInstructionsTo(uri: String) {
        val text = workbench.appInstructions(_state.value.path)
        viewModelScope.launch {
            val written = files.write(uri, text)
            _state.update { it.copy(fileMessage = if (written) WorkbenchWording.SAVED else WorkbenchWording.COULD_NOT_WRITE) }
        }
    }

    fun send() {
        val current = _state.value
        if (!current.canSend) return
        val inputs = current.inputs ?: return
        val system = instructions ?: workbench.appInstructions(current.path)
        _state.update { it.copy(sending = true, reply = null, sent = null, failure = null, notice = null) }
        viewModelScope.launch {
            val run = orNull { workbench.send(inputs, system) }
            _state.update { s ->
                // The path changed while this was out: its reply belongs to no path on screen now.
                if (s.path != current.path) return@update s.copy(sending = false)
                when (run) {
                    is TrainerWorkbench.Run.Replied -> when (val reply = run.reply) {
                        is WorkbenchReply.Answered -> s.copy(sending = false, reply = reply.text, sent = reply.sent)
                        is WorkbenchReply.Failed -> s.copy(sending = false, failure = reply.failure, sent = reply.sent)
                    }
                    TrainerWorkbench.Run.NotRunning -> s.copy(sending = false, planRuns = false, notice = WorkbenchWording.NO_PLAN)
                    TrainerWorkbench.Run.SessionGone -> s.copy(sending = false, notice = WorkbenchWording.SESSION_GONE)
                    null -> s.copy(sending = false, notice = WorkbenchWording.RECORD_UNREADABLE)
                }
            }
        }
    }

    /** A store's read that throws is null here; cancellation is never swallowed. */
    private suspend fun <T> orNull(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (unreadable: Exception) {
        null
    }
}
