package com.metaself.app.data.trainer

import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.Trainer
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.TrainerRequest

/**
 * A [Trainer] answering from scripts: each call takes the next of [plans] or [feedback], in order, and
 * every request is kept in [asked]. A call with nothing scripted fails the test.
 */
class FakeTrainer : Trainer {
    val plans = mutableListOf<TrainerReply<SessionPlan>>()
    val feedback = mutableListOf<TrainerReply<Feedback>>()
    val asked = mutableListOf<TrainerRequest>()

    override suspend fun suggest(request: TrainerRequest): TrainerReply<SessionPlan> {
        asked += request
        return plans.removeAt(0)
    }

    override suspend fun feedback(request: TrainerRequest): TrainerReply<Feedback> {
        asked += request
        return feedback.removeAt(0)
    }
}
