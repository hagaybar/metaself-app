package com.metaself.app.ui.propose

import com.metaself.app.domain.ai.EstimateResult

/**
 * What the app says when the model could not help.
 *
 * Every failure says what happened and what to do instead, and none of them apologises. Decision D8
 * is that the failure mode of a habit app is the day it refuses to work, so every one of these ends
 * at the same place: type the numbers.
 */
object ProposalWording {

    fun failure(result: EstimateResult): String = when (result) {
        is EstimateResult.NoKey ->
            "No API key yet. Add one in settings, or type the numbers instead."

        is EstimateResult.CeilingReached ->
            "You have used today's estimates. Type the numbers, or raise the daily limit in " +
                "settings."

        is EstimateResult.Unreachable ->
            "Could not reach the model. Type the numbers instead — your words are still here."

        is EstimateResult.Refused ->
            "The provider refused: ${result.detail}"

        is EstimateResult.Unreadable ->
            "The answer could not be understood. Type the numbers instead."

        is EstimateResult.AmountMissing ->
            "It would not say how much of ${result.items.joinToString(", ")} there was. " +
                "Say the amounts in your description — \"a bowl of stew, about 350 g\" — " +
                "or type the numbers instead."

        is EstimateResult.Proposed ->
            error("a proposal is not a failure")
    }
}
