package com.metaself.app.ui.screen.propose

import androidx.lifecycle.SavedStateHandle
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.FoodRepository
import com.metaself.app.domain.ai.Asked
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.MealConversationAsker
import com.metaself.app.domain.ai.MealEstimator
import com.metaself.app.domain.ai.StepResult

/**
 * The view model as the tests written before D58 built it: a meal that needs no question, answered
 * by [estimator] in one request. The conversation's own tests pass a [MealConversationAsker].
 */
fun ProposalViewModel(
    estimator: MealEstimator,
    problems: ProblemLog,
    foods: FoodRepository,
    savedState: SavedStateHandle = SavedStateHandle(),
): ProposalViewModel = ProposalViewModel(estimator, problems, foods, savedState, EstimatingAsker(estimator))

/** A conversation that never asks: the first request is the estimate, as today's describe was. */
class EstimatingAsker(private val estimator: MealEstimator) : MealConversationAsker {
    override suspend fun open(description: String): StepResult =
        StepResult.Estimate(estimator.estimate(description))

    override suspend fun next(description: String, asked: List<Asked>, cap: Int): StepResult =
        StepResult.Enough

    override suspend fun finish(
        description: String,
        asked: List<Asked>,
        moreDetail: String?,
        deep: Boolean,
    ): EstimateResult = estimator.estimate(description, moreDetail)

    override suspend fun remainingToday(): Int = Int.MAX_VALUE
}

/** A screen drawn where no conversation is under test: every stage's actions do nothing. */
val NO_CONVERSATION = com.metaself.app.ui.screen.propose.ConversationActions(
    onAcceptQuestions = {},
    onBestGuess = {},
    onAnswer = {},
    onEnough = {},
    onStepBack = { false },
    onRetry = {},
    onBestGuessSoFar = {},
)
