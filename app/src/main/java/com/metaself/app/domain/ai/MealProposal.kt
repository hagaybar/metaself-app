package com.metaself.app.domain.ai

import com.metaself.app.domain.amount.ItemToLog
import com.metaself.app.domain.amount.Rate
import com.metaself.app.domain.amount.Worth
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.portion.Portions

/**
 * One thing the model thinks was on the plate: what it is worth, and how much of it there was, apart
 * (D53 §1, §2).
 *
 * [rate] is per 100 g, per 100 ml or per one [unit] — never the total. [amount] and [unit] are the
 * amount he stated, or one natural piece when he stated none; the model is told never to make up
 * grams for a piece. [detail] is everything else it said about the item ("sesame, toasted") and goes
 * into the row's portion words beside the amount, where the assumption is arguable (D5 as amended).
 * [confidence] is about the figures for one piece or 100 of the unit, the size of piece included.
 *
 * Kept beside the row it becomes until he saves, so that the estimate is never lost to anything
 * done to the row.
 */
data class ProposedItem(
    val name: String,
    val detail: String,
    val amount: Double,
    val unit: String,
    val rate: Rate,
    val confidence: Confidence,
) {
    /**
     * The row this starts as: the model's worth at the model's amount, an estimate (D53 §3).
     *
     * The amount goes into the box as the model stated it — "0.25", not the "0.3" the day's
     * one-decimal words would make of it — because a number put in a box he then saves is taken as
     * his, and must be the one that was said.
     */
    fun toItemToLog(): ItemToLog = ItemToLog(
        name = name,
        detail = detail,
        amountText = Portions.inBox(amount),
        unit = unit,
        worth = Worth.Estimated(rate, confidence),
        foodId = null,
    )
}

/**
 * What the model came back with: the components of one meal, and at most one note about its biggest
 * assumption.
 *
 * A list, never a total. A comparison of two models on one real meal showed why: one
 * returned a confident table that had silently left a third of the dish out of it, and a total gives
 * nothing to notice. An omission you can see is a correction; an omission inside one number is a
 * silent error carried for months.
 */
data class MealProposal(
    val items: List<ProposedItem>,
    val note: String?,
    /**
     * The names of items the answer held but that could not be used — a figure missing or past its
     * ceiling, a basis that is none (D53 §2). Said on the screen, because a row that is simply not
     * there is the omission this list-not-a-total exists to make visible. An item dropped with no
     * name has nothing to be called and is not in it.
     */
    val dropped: List<String> = emptyList(),
    /**
     * The model's answer as it came, when any item was dropped — for *Show the model's answer*, so
     * why can be seen (issue #1). Null when every item was used. Shown only: never stored, never
     * written to the problem log.
     */
    val answer: String? = null,
) {
    init {
        require(items.isNotEmpty()) { "a proposal cannot be empty" }
    }
}
