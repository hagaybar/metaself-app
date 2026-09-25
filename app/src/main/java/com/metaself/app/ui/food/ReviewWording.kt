package com.metaself.app.ui.food

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The words a review's answer is drawn in (D54 §9.4, §12): the line the review ends in, and the
 * page's own reason for clearing a weight held per millilitre.
 */
object ReviewWording {

    /**
     * The reason the page gives its own suggestion to clear a weight, when the review moves a food
     * away from the millilitre (D54 §12.4): a weight stated per millilitre is not one per [unit].
     */
    fun weightPerMillilitre(unit: String): String =
        "A weight per millilitre is not a weight per ${unit.trim()}."

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
