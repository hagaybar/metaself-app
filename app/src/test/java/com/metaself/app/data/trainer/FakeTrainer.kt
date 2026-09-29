package com.metaself.app.data.trainer

import com.metaself.app.domain.trainer.EvaluationAndPlan
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.Trainer
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.WeeksPlan

/**
 * A [Trainer] answering from scripts: each call takes the next of its scripted list, in order, and
 * every request is kept in [asked]. A call with nothing scripted fails the test.
 */
class FakeTrainer : Trainer {
    val plans = mutableListOf<TrainerReply<SessionPlan>>()
    val feedback = mutableListOf<TrainerReply<Feedback>>()
    val evaluations = mutableListOf<TrainerReply<EvaluationAndPlan>>()
    val adjustments = mutableListOf<TrainerReply<WeeksPlan>>()
    val asked = mutableListOf<TrainerRequest>()

    override suspend fun suggest(request: TrainerRequest): TrainerReply<SessionPlan> {
        asked += request
        return plans.removeAt(0)
    }

    override suspend fun feedback(request: TrainerRequest): TrainerReply<Feedback> {
        asked += request
        return feedback.removeAt(0)
    }

    override suspend fun evaluate(request: TrainerRequest): TrainerReply<EvaluationAndPlan> {
        asked += request
        return evaluations.removeAt(0)
    }

    override suspend fun adjust(request: TrainerRequest): TrainerReply<WeeksPlan> {
        asked += request
        return adjustments.removeAt(0)
    }
}
