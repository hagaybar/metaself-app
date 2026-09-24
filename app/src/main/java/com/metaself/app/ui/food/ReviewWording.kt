package com.metaself.app.ui.food

import com.metaself.app.domain.ai.Figure
import com.metaself.app.domain.ai.FigureChange
import com.metaself.app.domain.ai.Suggestion
import com.metaself.app.domain.amount.Per
import com.metaself.app.domain.amount.Rate
import com.metaself.app.domain.portion.Portions
import com.metaself.app.ui.propose.ProposalWording
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The lines a review's suggestion is drawn as, under the heading of the group it would change
 * (D54 §4). Figures are written as everywhere else — whole when whole, one decimal otherwise.
 */
object ReviewWording {

    /** "Fat 1 → 4 g — {reason}": one changed figure, from what the box holds, and why. */
    fun change(change: FigureChange): String {
        val (name, unit) = when (change.figure) {
            Figure.KCAL -> "Calories" to "kcal"
            Figure.PROTEIN -> "Protein" to "g"
            Figure.CARBS -> "Carbs" to "g"
            Figure.FAT -> "Fat" to "g"
        }
        return "$name ${Portions.format(change.from)} → ${Portions.format(change.to)} $unit — " +
            change.reason
    }

    /**
     * What is drawn for a group's suggestion: one line per changed figure, or — for a group the
     * form did not know — one line, "Suggested: 60 kcal · P 4 · C 9 · F 1 — {reason}".
     */
    fun lines(suggestion: Suggestion): List<String> =
        if (suggestion.filled) {
            // The basis is the heading above the line, so which one is passed changes nothing drawn.
            val figures = ProposalWording.worthFigures(Rate(suggestion.nutrients, Per.HUNDRED))
            listOf("Suggested: $figures — ${suggestion.reason.orEmpty()}")
        } else {
            suggestion.changes.map(::change)
        }

    /**
     * The line a review ends in (D54 §9.4): what it came to, [said] without a full stop, then the
     * model's [note] after a dash — or a full stop when it gave none.
     */
    fun outcome(said: String, note: String?): String =
        note?.trim()?.takeIf { it.isNotEmpty() }?.let { "$said — $it" } ?: "$said."

    /**
     * The model's answer as **Show the model's answer** draws it (D54 §8.4): pretty-printed when it
     * is JSON, as it came when it is not — a reply that could not be read is shown as it is.
     */
    fun modelAnswer(raw: String): String = runCatching {
        pretty.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(raw))
    }.getOrDefault(raw)

    private val pretty = Json { prettyPrint = true }
}
