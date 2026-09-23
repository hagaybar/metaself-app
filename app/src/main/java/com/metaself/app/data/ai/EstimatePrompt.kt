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

    private val INSTRUCTIONS = """
        You estimate the nutrition of a meal from a short description.

        Rules:
        - Break the meal into its parts. Each thing on the plate is a separate item, even when they
          were described together. Never return one combined item for a whole meal.
        - A drink is one item, however it is made: a cappuccino, a latte, tea with milk, a smoothie,
          a milkshake, juice, beer, a cocktail. Do not split it into its ingredients. Give it as a
          count of its usual serving — 1 cup, 1 glass, 1 bottle, 1 can — and if its size matters,
          say the size you assumed in the note. Milk poured over cereal or cooked into a dish is an
          ingredient of that food, not a drink.
        - For each item, state how much of it you assumed, as a number greater than zero and a unit —
          for example 280 and "g", or 1 and "ball". Always give your best estimate, even when you are
          unsure, and lower the confidence instead. Never leave the amount at zero or the unit empty.
        - Every figure is a single number. Never a range, and never two numbers joined by a dash.
          If you are unsure, give your best single figure and lower the confidence instead.
        - Confidence is LOW, MEDIUM or HIGH, and describes how sure you are about the amount more
          than about the food.
        - Reply in the same language the description was written in, including the item names.
        - Add at most one short note about the biggest assumption you made. Leave it out if there
          isn't one.
    """.trimIndent()

    /**
     * The request, and — when a previous answer left amounts out — a second instruction naming them.
     *
     * [missingAmounts] is for the one retry D34 asks for. At temperature 0 the same request
     * gets the same answer, so asking again unchanged would get the same amountless reply; the
     * correction says which items had none. It is an instruction from the app, sent as the app's own,
     * and never folded into the owner's words the way [moreDetail] is.
     */
    fun requestBody(
        model: String,
        description: String,
        moreDetail: String? = null,
        missingAmounts: List<String> = emptyList(),
    ): String {
        val userText = if (moreDetail.isNullOrBlank()) {
            description
        } else {
            "$description\n\nMore detail: $moreDetail"
        }

        return buildJsonObject {
            put("model", model)
            put("temperature", 0)
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", INSTRUCTIONS)
                    },
                )
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", userText)
                    },
                )
                if (missingAmounts.isNotEmpty()) {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put(
                                "content",
                                "Your previous answer gave no amount for: " +
                                    missingAmounts.joinToString(", ") + ". Every item needs a " +
                                    "number greater than zero and a unit. Give your best estimate " +
                                    "and lower the confidence if you are unsure.",
                            )
                        },
                    )
                }
            }
            putJsonObject("response_format") {
                put("type", "json_schema")
                putJsonObject("json_schema") {
                    put("name", "meal_estimate")
                    put("strict", true)
                    put("schema", SCHEMA)
                }
            }
        }.toString()
    }

    private val SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonArray("required") { add("items"); add("note") }
        putJsonObject("properties") {
            putJsonObject("note") { put("type", "string") }
            putJsonObject("items") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    put("additionalProperties", false)
                    putJsonArray("required") {
                        add("name"); add("amount"); add("unit"); add("kcal")
                        add("protein_g"); add("carbs_g"); add("fat_g"); add("confidence")
                    }
                    putJsonObject("properties") {
                        putJsonObject("name") { put("type", "string") }
                        putJsonObject("amount") { put("type", "number") }
                        putJsonObject("unit") { put("type", "string") }
                        putJsonObject("kcal") { put("type", "integer") }
                        putJsonObject("protein_g") { put("type", "integer") }
                        putJsonObject("carbs_g") { put("type", "integer") }
                        putJsonObject("fat_g") { put("type", "integer") }
                        putJsonObject("confidence") {
                            put("type", "string")
                            putJsonArray("enum") { add("LOW"); add("MEDIUM"); add("HIGH") }
                        }
                    }
                }
            }
        }
    }
}
