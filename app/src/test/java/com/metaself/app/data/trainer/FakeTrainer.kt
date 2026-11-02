package com.metaself.app.data.trainer

import com.metaself.app.domain.trainer.AdjustedRest
import com.metaself.app.domain.trainer.BlockAnswer
import com.metaself.app.domain.trainer.EvaluationAndPlan
import com.metaself.app.domain.trainer.FilledWeek
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.GoalSuggestion
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.Trainer
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.WeekTarget
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

    /** D121: the targets each scripted adjustment returns with its weeks, in order; none when empty. */
    val adjustTargets = mutableListOf<List<WeekTarget>>()
    val fills = mutableListOf<TrainerReply<FilledWeek>>()
    val blocks = mutableListOf<TrainerReply<BlockAnswer>>()
    val suggestions = mutableListOf<TrainerReply<List<GoalSuggestion>>>()
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

    override suspend fun adjust(request: TrainerRequest): TrainerReply<AdjustedRest> {
        asked += request
        val targets = adjustTargets.removeFirstOrNull().orEmpty()
        return when (val reply = adjustments.removeAt(0)) {
            is TrainerReply.Answered -> TrainerReply.Answered(AdjustedRest(reply.value, targets), reply.model, reply.weekNote)
            is TrainerReply.Failed -> reply
        }
    }

    override suspend fun fill(request: TrainerRequest): TrainerReply<FilledWeek> {
        asked += request
        return fills.removeAt(0)
    }

    override suspend fun nextBlock(request: TrainerRequest): TrainerReply<BlockAnswer> {
        asked += request
        return blocks.removeAt(0)
    }

    override suspend fun suggestGoals(request: TrainerRequest): TrainerReply<List<GoalSuggestion>> {
        asked += request
        return suggestions.removeAt(0)
    }
}
