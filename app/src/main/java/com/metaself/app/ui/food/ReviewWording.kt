package com.metaself.app.ui.food

import com.metaself.app.domain.ai.Figure
import com.metaself.app.domain.ai.Suggestion
import com.metaself.app.domain.food.FoodForm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The words a review's answer is drawn in, under the button (D54 §9.4, §11): what each group would
 * change, why, and the line the review ends in.
 */
object ReviewWording {

    /**
     * What a group's suggestion would change, as one line under the verdict (D54 §11): "Per 100 g:
     * Protein 7.15 → 10 · Fat 8.45 → 7", old to new, or — for a group the form did not know —
     * "Per bowl: Filled: 60 kcal · P 4 · C 9 · F 1". [per] is "100 g" or the unit's name. Figures
     * are written as the editor's boxes write them ([FoodForm.shown]), so the old figure reads as
     * the box does.
     */
    fun changes(per: String, suggestion: Suggestion): String {
        val what = if (suggestion.filled) {
            with(suggestion.nutrients) {
                "Filled: ${FoodForm.shown(kcal)} kcal · P ${FoodForm.shown(proteinG)} · " +
                    "C ${FoodForm.shown(carbsG)} · F ${FoodForm.shown(fatG)}"
            }
        } else {
            suggestion.changes.joinToString(" · ") { change ->
                "${name(change.figure)} ${FoodForm.shown(change.from)} → ${FoodForm.shown(change.to)}"
            }
        }
        return "Per $per: $what"
    }

    /**
     * Why, for one group: its reasons in the order its figures came, each said once — two linked
     * changes are often explained in one sentence (§8.2). Null when it gave none.
     */
    fun reasons(suggestion: Suggestion): String? {
        val said = if (suggestion.filled) listOfNotNull(suggestion.reason) else suggestion.changes.map { it.reason }
        return said.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            .joinToString(" ").takeIf { it.isNotEmpty() }
    }

    private fun name(figure: Figure): String = when (figure) {
        Figure.KCAL -> "Calories"
        Figure.PROTEIN -> "Protein"
        Figure.CARBS -> "Carbs"
        Figure.FAT -> "Fat"
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
