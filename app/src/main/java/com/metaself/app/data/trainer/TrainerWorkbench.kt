package com.metaself.app.data.trainer

import com.metaself.app.data.ai.TrainerPrompt
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.time.Today
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.TrainerPath
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.WorkbenchReply
import com.metaself.app.domain.trainer.WorkbenchSender
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * D106: the trainer's four paths, tried under other instructions on the real record. The request is the
 * real ask's — built by [AskTheTrainer]'s own question builders and request — and this class **only
 * reads**: no review is saved first, no answer is stored, kept, ticked or offered. Only when the page asks.
 */
class TrainerWorkbench @Inject constructor(
    private val ask: AskTheTrainer,
    private val store: TrainerStore,
    private val record: MovementRecord,
    private val today: Today,
    private val sender: WorkbenchSender,
) {

    /** What each path's real screen asks. */
    sealed interface Inputs {
        data class Feedback(val workoutId: Long) : Inputs
        data class Plan(val answers: PlanAnswers) : Inputs
        data class Evaluate(val ask: ProgrammeAsk) : Inputs
        data class Adjust(val words: String) : Inputs
    }

    /** What [send] came to: the reply, or why nothing was sent. */
    sealed interface Run {
        data class Replied(val reply: WorkbenchReply) : Run

        /** Adjust with no plan running: nothing was sent. */
        data object NotRunning : Run

        /** The session left the record: nothing was sent. */
        data object SessionGone : Run
    }

    /** The sessions feedback can be asked about: the visible, counted ones of the 42 days a request carries, newest first. */
    suspend fun sessions(): List<Workout> {
        val day = today().toEpochDay()
        return record.observeWorkouts(TrainerRequest.firstDay(day), day).first()
            .filter { !it.hidden && it.counted }
            .sortedByDescending { it.startedAtMillis }
    }

    /** Whether a weekly plan runs right now. Reads only. */
    suspend fun planRuns(): Boolean = ask.running() != null

    /** The system message [path] sends today, exactly. */
    fun appInstructions(path: TrainerPath): String = TrainerPrompt.instructions(path)

    /** The request the real ask would send for [inputs]; null when adjusting with no plan, or the session is gone. */
    suspend fun request(inputs: Inputs): TrainerRequest? = when (inputs) {
        is Inputs.Feedback -> store.workout(inputs.workoutId)?.let { workout ->
            // The stored review supplies felt, words and its plan; unlike the real ask, it is never saved first.
            val review = store.reviewOf(inputs.workoutId)
                ?: TrainerReview(workoutId = inputs.workoutId, planId = null, felt = null, words = null)
            ask.request(ask.feedbackQuestion(workout, review), exceptWorkoutId = inputs.workoutId)
        }
        is Inputs.Plan -> ask.request(ask.planQuestion(inputs.answers))
        is Inputs.Evaluate -> ask.request(ask.evaluateQuestion(inputs.ask))
        is Inputs.Adjust -> ask.adjustQuestion(inputs.words)?.let { ask.request(it) }
    }

    /** Sends [inputs]' real request with [system] as the whole system message. Throws only as the stores' reads do. */
    suspend fun send(inputs: Inputs, system: String): Run {
        val request = request(inputs) ?: return if (inputs is Inputs.Adjust) Run.NotRunning else Run.SessionGone
        return Run.Replied(sender.send(system, request))
    }
}
