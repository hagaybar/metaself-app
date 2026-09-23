package com.metaself.app.data.ai

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.MealProposal
import com.metaself.app.domain.ai.ProposedItem
import com.metaself.app.domain.day.Confidence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What comes back, read strictly.
 *
 * Two layers: the provider's envelope, and the model's own JSON inside the message. Both are parsed
 * inside `runCatching`, so a malformed body becomes an [EstimateResult.Unreadable] the screen can
 * show rather than an exception crossing the seam.
 *
 * **An item missing a number it needs is DROPPED, not defaulted to zero.** A zero-calorie item on
 * the record is a lie the owner has no way to catch; a missing row is something he can see. If
 * dropping leaves nothing at all, the whole reply is unreadable — which is also what a reply
 * containing a single lumped total amounts to, and it is refused for the same reason.
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
        val withoutAmount = items.filter { it.portionAmount <= 0.0 || it.portionUnit.isBlank() }

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

    private fun JsonObject.toItem(): ProposedItem? {
        val name = this["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return null
        val kcal = this["kcal"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val protein = this["protein_g"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val carbs = this["carbs_g"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val fat = this["fat_g"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null

        val amount = this["amount"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val unit = this["unit"]?.jsonPrimitive?.content.orEmpty()

        return ProposedItem(
            name = name,
            portion = portionWords(amount, unit),
            portionAmount = amount,
            portionUnit = unit,
            kcal = maxOf(0, kcal),
            proteinG = maxOf(0, protein),
            carbsG = maxOf(0, carbs),
            fatG = maxOf(0, fat),
            confidence = confidenceOf(this["confidence"]?.jsonPrimitive?.content),
        )
    }

    /** An amount the model could not put a number to says so, rather than pretending to a zero. */
    private fun portionWords(amount: Double, unit: String): String = when {
        amount <= 0.0 || unit.isBlank() -> "amount not stated"
        amount % 1.0 == 0.0 -> "${amount.roundToInt()} $unit"
        else -> String.format(Locale.US, "%.1f %s", amount, unit)
    }

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
