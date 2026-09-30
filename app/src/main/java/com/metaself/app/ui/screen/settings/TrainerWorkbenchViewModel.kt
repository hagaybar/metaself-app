package com.metaself.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.diagnostics.ProblemLog
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Test the trainer's instructions" (D106), on the page's own back-stack entry: the loaded text lives in
 * [State.instructions] and goes when the page is left. Nothing is stored; the forms are the trainer screens' own.
 */
@HiltViewModel
class TrainerWorkbenchViewModel @Inject constructor(
    private val workbench: TrainerWorkbench,
    private val files: InstructionFiles,
    private val today: Today,
    private val problems: ProblemLog,
) : ViewModel() {

    /**
     * @property unreadable the sessions, or whether a plan runs, could not be read on opening: the list and
     *   the plan line may be wrong, and the page says so ([WorkbenchWording.RECORD_NOT_READ]).
     * @property fileName the loaded file's name; null sends the app's own instructions.
     * @property instructions the loaded file's text, for this visit only and never stored; null sends the
     *   app's own. Set together with [fileName], in one update, so the "will send" line is always what Send sends.
     * @property sent the last request body, verbatim, for Copy what was sent.
     * @property notice a sentence for a run that sent nothing (no plan, session gone, record unreadable).
     */
    data class State(
        val loaded: Boolean = false,
        val unreadable: Boolean = false,
        val today: Long = 0,
        val path: TrainerPath = TrainerPath.FEEDBACK,
        val sessions: List<Workout> = emptyList(),
        val workoutId: Long? = null,
        val plan: PlanSessionViewModel.Form = PlanSessionViewModel.Form(),
        val evaluate: EvaluatePlanViewModel.Form = EvaluatePlanViewModel.PRESET,
        val adjustWords: String = "",
        val planRuns: Boolean = false,
        val fileName: String? = null,
        val instructions: String? = null,
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

    /** The file being read; a newer load, or Use the app's own, cancels it. */
    private var loadJob: Job? = null

    /** The request out now; a new path cancels it, so its reply is neither shown nor waited on. */
    private var sendJob: Job? = null

    init {
        viewModelScope.launch { read() }
    }

    private suspend fun read() {
        val sessions = orNull("sessions not read") { workbench.sessions() }
        val runs = orNull("plan not read") { workbench.planRuns() }
        _state.update {
            it.copy(
                loaded = true,
                unreadable = sessions == null || runs == null,
                today = today().toEpochDay(),
                sessions = sessions.orEmpty(),
                planRuns = runs ?: false,
            )
        }
    }

    /** A new path drops the request out and clears the last reply, so a reply is never shown under a path that did not ask it. */
    fun pickPath(path: TrainerPath) {
        sendJob?.cancel()
        sendJob = null
        _state.update { it.copy(path = path, sending = false, reply = null, sent = null, failure = null, notice = null) }
    }

    fun pickSession(workoutId: Long) = _state.update { it.copy(workoutId = workoutId) }
    fun changePlan(form: PlanSessionViewModel.Form) = _state.update { it.copy(plan = form) }
    fun changeEvaluate(form: EvaluatePlanViewModel.Form) = _state.update { it.copy(evaluate = form) }
    fun changeAdjustWords(words: String) = _state.update { it.copy(adjustWords = words) }

    /** Reads the file and its name, then names it and takes its text in one update; a file that cannot be read changes nothing. */
    fun load(uri: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val text = files.read(uri)
            if (text.isNullOrBlank()) {
                _state.update { it.copy(fileMessage = WorkbenchWording.COULD_NOT_READ) }
                return@launch
            }
            val name = files.nameOf(uri) ?: WorkbenchWording.UNNAMED
            _state.update { it.copy(instructions = text, fileName = name, fileMessage = null) }
        }
    }

    fun useAppOwn() {
        loadJob?.cancel()
        loadJob = null
        _state.update { it.copy(instructions = null, fileName = null, fileMessage = null) }
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
        val system = current.instructions ?: workbench.appInstructions(current.path)
        _state.update { it.copy(sending = true, reply = null, sent = null, failure = null, notice = null) }
        sendJob = viewModelScope.launch {
            // Only building the request reads the record, so only that is caught; the sender's failures come back as its reply.
            val built = runCatchingRead("request not built") { workbench.request(inputs) }
            if (built.isFailure) {
                _state.update { it.copy(sending = false, notice = WorkbenchWording.RECORD_UNREADABLE) }
                return@launch
            }
            val request = built.getOrNull()
            if (request == null) {
                _state.update {
                    when (workbench.nothingSent(inputs)) {
                        TrainerWorkbench.Run.NotRunning -> it.copy(sending = false, planRuns = false, notice = WorkbenchWording.NO_PLAN)
                        else -> it.copy(sending = false, notice = WorkbenchWording.SESSION_GONE)
                    }
                }
                return@launch
            }
            val reply = workbench.send(request, system)
            _state.update {
                when (reply) {
                    is WorkbenchReply.Answered -> it.copy(sending = false, reply = reply.text, sent = reply.sent)
                    is WorkbenchReply.Failed -> it.copy(sending = false, failure = reply.failure, sent = reply.sent)
                }
            }
        }
    }

    /** A read of the record that throws is null here, and written down by kind; cancellation is never swallowed. */
    private suspend fun <T : Any> orNull(what: String, block: suspend () -> T): T? = runCatchingRead(what, block).getOrNull()

    /**
     * A read of the record, its failure written to the problem log under [PROBLEM_KIND] as [what] and the
     * exception's class — never a message, which could carry the owner's words. Cancellation is let through.
     */
    private suspend fun <T> runCatchingRead(what: String, block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (unreadable: Exception) {
        runCatching { problems.record(kind = PROBLEM_KIND, detail = "$what: ${unreadable::class.java.name}") }
        Result.failure(unreadable)
    }

    companion object {
        const val PROBLEM_KIND = "trainer workbench"
    }
}
