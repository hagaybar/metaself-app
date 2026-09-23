package com.metaself.app.data.ai

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.MealProposal
import com.metaself.app.domain.ai.ProposedItem
import com.metaself.app.domain.amount.BelievableAmount
import com.metaself.app.domain.amount.Per
import com.metaself.app.domain.amount.Rate
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.portion.Portions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What comes back, read strictly.
 *
 * Two layers: the provider's envelope, and the model's own JSON inside the message. Both are parsed
 * inside `runCatching`, so a malformed body becomes an [EstimateResult.Unreadable] the screen can
 * show rather than an exception crossing the seam.
 *
 * **An item missing a number it needs is DROPPED, not defaulted to zero.** A zero-calorie item on
 * the record is a lie the owner has no way to catch; a missing row is something he can see. The same
 * goes for a figure that is negative or past D42's ceiling for its basis (D53 §2): it used to be
 * clamped to 0, which was a number nobody stated — and for a row that worth would make, at the amount
 * stated, past what a whole item typed by hand may be. If dropping leaves nothing at all, the whole reply
 * is unreadable — which is also what a reply containing a single lumped total amounts to, and it is
 * refused for the same reason.
 */
object EstimateResponse {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): EstimateResult = runCatching {
        val content = json.parseToJsonElement(body)
            .jsonObject["choices"]!!.jsonArray
            .first().jsonObject["message"]!!.jsonObject["content"]!!
            .jsonPrimitive.content

        val payload = json.parseToJsonElement(content).jsonObject
        val items = payload["items"]!!.jsonArray.mapNotNull { it.jsonObject.toItem() }

        // D34: every item comes with an amount, or the reply is one that did not give them.
        // Logged anyway, an item naming a unit and no amount could not join a meal (issue #23).
        val withoutAmount = items.filter { it.amount <= 0.0 || it.unit.isBlank() }

        if (items.isEmpty()) {
            EstimateResult.Unreadable("the reply held no items this app could use")
        } else if (withoutAmount.isNotEmpty()) {
            EstimateResult.AmountMissing(withoutAmount.map { it.name })
        } else {
            EstimateResult.Proposed(
                MealProposal(
                    items = items,
                    note = payload["note"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                ),
            )
        }
    }.getOrElse { EstimateResult.Unreadable("the reply was not in the shape this app asked for") }

    /**
     * One item, or null when it is not in the shape asked for (D53 §2): a figure missing, not a
     * number, negative or past D42's ceiling for its basis; a basis other than per 100 or per one,
     * per 100 of something that is not grams or millilitres, or per one gram or millilitre; a name
     * or a detail missing; a row, at the amount stated, past what a whole item may be.
     *
     * The amount is NOT judged here beyond being read and priced. None, zero or no unit is D34's
     * question and is answered by the caller; one past the amount box's ceiling is kept, so the row
     * arrives with its box refusing it, exactly as if he had typed it.
     */
    private fun JsonObject.toItem(): ProposedItem? {
        val name = this["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return null
        val detail = this["detail"]?.jsonPrimitive?.content ?: return null
        val amount = this["amount"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val unit = this["unit"]?.jsonPrimitive?.content?.trim().orEmpty()

        val per = when (this["figures_per"]?.jsonPrimitive?.content) {
            "100" -> Per.HUNDRED
            "1" -> Per.ONE
            else -> return null
        }
        // Per 100 of a bun is not a basis; costing it would divide a count of buns by 100. Nor is
        // per one gram or millilitre, the other way round: it would multiply a weight by a worth
        // judged against a piece's ceiling — 250 kcal "per 1 g" is 50,000 kcal at 200 g.
        val measured = Portions.isGrams(unit) || Portions.isMillilitres(unit)
        if (per == Per.HUNDRED && unit.isNotEmpty() && !measured) return null
        if (per == Per.ONE && measured) return null

        val (kcalMost, macroMost) = when (per) {
            Per.HUNDRED -> BelievableAmount.KCAL_PER_100G to BelievableAmount.MACRO_PER_100G
            Per.ONE -> BelievableAmount.KCAL_PER_UNIT to BelievableAmount.MACRO_PER_UNIT
        }
        val kcal = figure("kcal", kcalMost) ?: return null
        val protein = figure("protein_g", macroMost) ?: return null
        val carbs = figure("carbs_g", macroMost) ?: return null
        val fat = figure("fat_g", macroMost) ?: return null

        val rate = Rate(Nutrients(kcal, protein, carbs, fat), per)
        if (!believableRow(rate, amount, unit)) return null

        return ProposedItem(
            name = name.trim(),
            detail = detail.trim(),
            amount = amount,
            unit = unit,
            rate = rate,
            confidence = confidenceOf(this["confidence"]?.jsonPrimitive?.content),
        )
    }

    /**
     * Whether the row this item arrives as is one a whole item typed by hand could be — D42's
     * [BelievableAmount.ENTRY_KCAL] and [BelievableAmount.ENTRY_MACRO_G], the bounds *Type the
     * numbers* has. A believable worth times a believable amount can still be past them (5000 kcal
     * a piece, 100 pieces), and such a row is a misread basis, not a meal.
     *
     * Judged only at an amount the box would accept. None is D34's question; one past the box's
     * ceiling arrives with its box refusing it and cannot be saved as it stands (D53 §2), so what
     * it would total is never logged.
     */
    private fun believableRow(rate: Rate, amount: Double, unit: String): Boolean {
        if (amount <= 0.0 || !BelievableAmount.isBelievable(amount, BelievableAmount.amountIn(unit))) {
            return true
        }
        val row = rate.nutrients * (amount / rate.per.divisor)
        val macro = BelievableAmount.ENTRY_MACRO_G.toDouble()
        return BelievableAmount.isBelievable(row.kcal, BelievableAmount.ENTRY_KCAL.toDouble()) &&
            listOf(row.proteinG, row.carbsG, row.fatG).all { BelievableAmount.isBelievable(it, macro) }
    }

    /**
     * A worth figure, or null when it is missing, not a number, negative, or past [most].
     *
     * Judged before a [Nutrients] is built, both because that type refuses a negative by throwing —
     * which would take the whole reply with it rather than the one item — and because a [Rate] is
     * not judged anywhere after this (D42).
     */
    private fun JsonObject.figure(field: String, most: Double): Double? =
        this[field]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?.takeIf { BelievableAmount.isBelievable(it, most) }

    /**
     * Anything unrecognised is read as LOW.
     *
     * Losing a whole estimate because the model said "PROBABLY" would be worse than showing it with
     * a warning. LOW is the safe direction: it shows doubt the owner can dismiss, rather than
     * hiding doubt he cannot.
     */
    private fun confidenceOf(raw: String?): Confidence = when (raw?.uppercase()) {
        "HIGH" -> Confidence.HIGH
        "MEDIUM" -> Confidence.MEDIUM
        else -> Confidence.LOW
    }
}
