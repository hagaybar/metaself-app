package com.metaself.app.ui.propose

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.amount.Per
import com.metaself.app.domain.amount.Rate
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.LoggedFrom
import com.metaself.app.domain.portion.Portions

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

        // Read, and nothing in it usable: said as that, naming them, not as not understood.
        is EstimateResult.Unreadable -> if (result.dropped.isNotEmpty()) {
            "None of the items in the answer could be used: ${result.dropped.joinToString(", ")}. " +
                "Type the numbers instead."
        } else {
            "The answer could not be understood. Type the numbers instead."
        }

        is EstimateResult.AmountMissing ->
            "It would not say how much of ${result.items.joinToString(", ")} there was. " +
                "Say the amounts in your description — \"a bowl of stew, about 350 g\" — " +
                "or type the numbers instead."

        is EstimateResult.Proposed ->
            error("a proposal is not a failure")
    }

    /**
     * What an item is worth, as a person would type it: "250 kcal · P 18 · C 0 · F 20" — whole
     * when whole, one decimal otherwise (D53 §6). The basis ("per 100 g: ") is the screen's, from
     * its resources.
     */
    fun worthFigures(rate: Rate): String = with(rate.nutrients) {
        "${Portions.format(kcal)} kcal · P ${Portions.format(proteinG)} · " +
            "C ${Portions.format(carbsG)} · F ${Portions.format(fatG)}"
    }

    /**
     * Why the worth boxes will not do: the food form's own refusal, with the row's basis and the
     * ceilings for it (D53 §6, D42) — "All four per 100 g (at most 1000 kcal, and 110 g of …)" or
     * "All four per bun (at most 5000 kcal, …)". There is no "leave them all empty": a row has to
     * be worth something to be logged.
     */
    fun worthRefused(per: Per, unit: String): String {
        val basis = when (per) {
            Per.HUNDRED -> "100 $unit"
            Per.ONE -> unit
        }
        return FoodForm.allFour(basis, per.kcalMost, per.macroMost) + "."
    }

    /** What a row will log, in the whole numbers that go on the day. */
    fun rowFigures(numbers: LoggedFrom.Numbers): String =
        "${numbers.kcal} kcal · P ${numbers.proteinG} · C ${numbers.carbsG} · F ${numbers.fatG}"
}
