package com.metaself.app.data.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * What leaves the phone.
 *
 * The only place in this app that sends anything anywhere, which is why it is a pure function with
 * its own tests. Decision D16 promises that exactly the meal description leaves and nothing else —
 * not the owner's body, his numbers, his history or the date — and a promise that is not tested is
 * a hope. `NOTHING about the owner is sent` is that test.
 *
 * The reply is pinned with a schema rather than asked for politely. Both models tried while this
 * was designed answered in ranges, volunteered fields nobody had asked for, and one gave a total
 * that silently omitted a third of the meal. A schema is what turns "please" into "this shape or
 * nothing".
 *
 * Since D53 the reply gives what each item is worth (per 100 g, per 100 ml or per one piece) and how
 * much there was, apart, with every field required. An unstated amount is one natural piece, never
 * grams the model made up. What is sent does not change: the words, and nothing else.
 *
 * Grams and millilitres are asked for as exactly "g" and "ml" in every language (issue #1): the
 * phone recognises the usual spellings too (`Portions`), but a unit it does not recognise makes a
 * per-100 worth unusable, and a spelling asked for is one fewer to guess at. A piece keeps the
 * description's own word, since that is what matching compares against his foods (D53 §4, §5).
 */
object EstimatePrompt {

    /**
     * The default model.
     *
     * **Check this against the provider's current list when creating the key.** Model names change
     * faster than this app will be rebuilt, which is why it is a setting and this is only its
     * default.
     */
    const val DEFAULT_MODEL = "gpt-4o-mini"

    /** The everyday estimate's rules; a conversation's first request carries them too (D58 §3.1). */
    internal val INSTRUCTIONS = """
        You estimate the nutrition of a meal from a short description.

        Rules:
        - Break the meal into its parts. Each thing on the plate is a separate item, even when they
          were described together. Never return one combined item for a whole meal.
        - A drink is one item, however it is made: a cappuccino, a latte, tea with milk, a smoothie,
          a milkshake, juice, beer, a cocktail. Do not split it into its ingredients. Give it as a
          count of its usual serving — 1 cup, 1 glass, 1 bottle, 1 can — and if its size matters,
          say the size you assumed in the detail. Milk poured over cereal or cooked into a dish is
          an ingredient of that food, not a drink.
        - The name is the plain name of the food, such as "Hamburger bun" or "Cappuccino". No size,
          brand, cooking or quantity in it. Everything else worth saying about the item goes in the
          detail, such as "sesame, toasted" or "large"; leave the detail empty if there is nothing.
        - For each item, give how much there was as a number greater than zero and a unit. If a unit
          was stated, use that unit and that number: "a 200 g burger" is 200 and "g", "330 ml of
          juice" is 330 and "ml". If none was stated, use the natural piece of the thing: "a bun" is
          1 and "bun", "two slices of pizza" is 2 and "slice", "a cappuccino" is 1 and "cup". Name a
          piece in the singular.
        - When the unit is grams or millilitres, write it exactly "g" or "ml", whatever language
          the description is in: never a translation, a plural or an abbreviation of them. Every
          other unit, the word for a piece included, is written in the description's language.
        - Never convert an amount that was stated.
          Never make up grams or millilitres for an amount that was not stated, not even in the
          detail. The detail may say the size of piece you assumed in words, such as "large",
          never in grams.
        - Always give your best estimate of the amount, even when you are unsure, and lower the
          confidence instead. Never leave the amount at zero or the unit empty.
        - The four figures are what the food is worth, not the total: per 100 of the unit when the
          unit is grams or millilitres, with figures_per "100"; per one piece otherwise, with
          figures_per "1". Figures are per 100 g, per 100 ml or per one piece, never the total.
        - Every figure is a single number. Never a range, and never two numbers joined by a dash.
          If you are unsure, give your best single figure and lower the confidence instead.
        - Confidence is LOW, MEDIUM or HIGH, and describes how sure you are about the figures for
          one piece or for 100 of the unit, including the size of piece you assumed.
        - Reply in the same language the description was written in, including the item names;
          only "g" and "ml" are always written that way.
        - Add at most one short note about the biggest assumption you made. Leave it out if there
          isn't one.
    """.trimIndent()

    /**
     * The request, and — when a previous answer left amounts out — a second instruction naming them.
     *
     * [missingAmounts] is for the one retry D34 asks for. At temperature 0 the same request gets
     * much the same answer, and a reasoning model — sent no temperature ([RequestProfile]) — is no
     * likelier to change its mind unprompted, so asking again unchanged would most likely get the
     * same amountless reply; the correction says which items had none. It is an instruction from the app, sent as the app's own,
     * and never folded into the owner's words the way [moreDetail] is.
     */
    fun requestBody(
        model: String,
        description: String,
        moreDetail: String? = null,
        missingAmounts: List<String> = emptyList(),
        profile: RequestProfile = RequestProfile.guess(model),
    ): String {
        val userText = if (moreDetail.isNullOrBlank()) {
            description
        } else {
            "$description\n\nMore detail: $moreDetail"
        }

        val messages = buildList {
            add(ChatRequest.Message("system", INSTRUCTIONS))
            add(ChatRequest.Message("user", userText))
            if (missingAmounts.isNotEmpty()) {
                add(
                    ChatRequest.Message(
                        "system",
                        "Your previous answer gave no amount for: " +
                            missingAmounts.joinToString(", ") + ". Every item needs a " +
                            "number greater than zero and a unit. Give your best estimate " +
                            "and lower the confidence if you are unsure.",
                    ),
                )
            }
        }
        return ChatRequest.body(model, profile, messages, schemaName = "meal_estimate", schema = SCHEMA)
    }

    /** One item of an answer; a conversation's answers use it exactly (D58 §4). */
    internal val ITEM_SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonArray("required") {
            add("name"); add("detail"); add("amount"); add("unit"); add("figures_per")
            add("kcal"); add("protein_g"); add("carbs_g"); add("fat_g")
            add("confidence")
        }
        putJsonObject("properties") {
            putJsonObject("name") { put("type", "string") }
            putJsonObject("detail") { put("type", "string") }
            putJsonObject("amount") { put("type", "number") }
            putJsonObject("unit") { put("type", "string") }
            putJsonObject("figures_per") {
                put("type", "string")
                putJsonArray("enum") { add("100"); add("1") }
            }
            // Numbers, not integers: a worth keeps its decimals (D53 §1).
            putJsonObject("kcal") { put("type", "number") }
            putJsonObject("protein_g") { put("type", "number") }
            putJsonObject("carbs_g") { put("type", "number") }
            putJsonObject("fat_g") { put("type", "number") }
            putJsonObject("confidence") {
                put("type", "string")
                putJsonArray("enum") { add("LOW"); add("MEDIUM"); add("HIGH") }
            }
        }
    }

    private val SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonArray("required") { add("items"); add("note") }
        putJsonObject("properties") {
            putJsonObject("note") { put("type", "string") }
            putJsonObject("items") {
                put("type", "array")
                put("items", ITEM_SCHEMA)
            }
        }
    }
}
