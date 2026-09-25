package com.metaself.app.data.ai

import com.metaself.app.domain.ai.Asked
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * What a conversation about one meal sends (D58 §3, §4). Pure, like [EstimatePrompt].
 *
 * Three requests: the first, which either estimates the meal or asks its first question; a step,
 * which asks the next question or says there is no need; and the final analysis. Each sends the
 * app's instructions, his description, and every question asked so far with his answer — the
 * question in the model's own words, the answer as he tapped or typed it — and nothing else (D16 as
 * amended by D58 §10). How it is sent is the model's [RequestProfile] (D57).
 */
object ConversationPrompt {

    /** The most questions a conversation asks (D58 §2.3). */
    const val MOST_QUESTIONS = 5

    private val QUESTION_RULES = """
        You help work out the calories of one meal from the eater's description. Before estimating,
        you may ask a few short questions, one at a time. Decide whether you need to.

        Rules:
        - Ask only when an answer could change the meal's total by roughly a tenth or more. A single
          plain food (a fruit, a boiled egg), a packaged product with its size, a standard drink: no
          questions. Then set needs_questions to false.
        - Ask about the biggest calorie uncertainty first, in this order unless the meal says
          otherwise:
          1. Added fats: cooking oil, butter, ghee, dressing, mayonnaise, a drizzle at the end —
             whether there was any, and how much.
          2. Portion size: the container or plate and how full it was, how many scoops, pieces or
             servings.
          3. The type of an energy-dense part: which cheese, a cream or a tomato sauce, which
             dressing, whole or skimmed milk, sugar in a drink.
          4. Cooking method: fried, grilled, baked, steamed; breaded or not.
          5. Unmentioned extras that usually come with this dish: bread on the side, a topping, a dip.
        - Never ask what the description or an earlier answer already says, directly or by clear
          implication. Never ask about anything that does not change the calories.
        - Ask what the eater could see or remember, never a measurement. Not "how many grams", but
          the size of the box against a hand, how many spoonfuls, whether the pasta was glossy or dry.
        - Ask about the meal only. The kind of place it came from is a fair question (home-cooked, a
          restaurant, a takeaway counter, a bakery, packaged); its name, where it is, when it was
          eaten, who with, and anything about the eater are never asked.
        - One question at a time, one thing per question, at most about twenty words, in the
          description's language.
        - Give 2 to 5 ready-made answers: concrete, mutually exclusive, each short enough for a
          button, ordered from least to most calories where that makes sense, and always ending with
          an answer meaning "Not sure" in the description's language. The app adds a free-text
          answer itself; do not offer one.
        - total_planned is how many questions you expect to need in all, this one included —
          honestly, and never more than you are allowed. Fewer is better: stop asking when the
          remaining unknowns would change the total by little.
        - When you ask, set needs_questions to true and give the question. When no more questions
          are needed, set needs_questions to false, and give the question with empty text and no
          answers.
    """.trimIndent()

    private val OPENING_EXTRA = """
        You may ask at most $MOST_QUESTIONS questions in all.

        When you ask, leave items empty and the note empty. When no question is needed, set
        total_planned to 0, give the question with empty text and no answers, and estimate the meal now in items and note, by
        these rules:
    """.trimIndent()

    private val FINAL_INSTRUCTIONS = """
        You are a nutritionist working out the calories of one meal the eater described, and the
        answers they gave to your questions. Reconstruct the plate as it was served before you
        estimate.

        How to reason:
        - Start from where it came from: how that kind of place makes and portions this dish — the
          standard serving utensils (ladle, scoop, tongs, spoon), the containers and plate sizes, and
          the usual portion. A home-cooked dish is portioned as a home cook would; a packaged one by
          its package.
        - Account for the cooking: oil absorbed in frying, butter or oil in a pan, sugar in a glaze,
          the fat in a cream sauce. What the method adds belongs to the item it is cooked into.
        - Look for the hidden calories — oil, butter, dressing, sauce, spread, sugar — that the
          description does not mention but that this dish from this kind of place normally has.
          Include each one you believe was there. An added fat or sauce the eater could see or leave
          (a dressing, a drizzle, butter on bread, a dip) is its own item, so it can be corrected; a
          fat cooked into a component is part of that component's figures and is said in its detail.
        - Resolve every ambiguity — which cheese, which sauce, which bread — with the most likely
          choice for this dish and this kind of place, and say which you chose.
        - The answers override your defaults. An answer "Not sure" means: use this kind of place's
          usual practice, and lower the confidence for that item.
        - Before the items, write the plate in "plate": a few short lines going through the source,
          the portions, the cooking and the hidden calories. This is your working.

        Rules for the answer:
        - Each thing on the plate is a separate item. Never return one combined item for the whole
          meal.
        - A drink is one item, however it is made: a cappuccino, tea with milk, a smoothie, juice,
          beer. Do not split it into its ingredients. Give it as a count of its usual serving — 1 cup,
          1 glass, 1 bottle, 1 can. Milk poured over cereal or cooked into a dish is an ingredient of
          that food, not a drink.
        - The name is the plain name of the food, such as "Bread roll" or "Cappuccino". No size,
          brand, cooking or quantity in it.
        - Give each amount in the unit that fits how it was served. For a loose, scooped, ladled or
          plated component — rice, pasta, salad leaves, a stew, grated cheese, a sauce — give the
          amount you worked out in grams, with figures per 100 g, whenever your reconstruction
          supports it (the utensil, the container, the place's usual portion). For a counted piece —
          a roll, an egg, a slice, a bar, a drink — give the count in its own unit with figures per
          one piece; name the piece in the singular. An amount the eater stated is used exactly as
          stated, never converted.
        - Every amount is a number greater than zero with a unit. Always give your best estimate,
          and lower the confidence instead of leaving it out.
        - When the unit is grams or millilitres, write it exactly "g" or "ml", whatever language the
          description is in: never a translation, a plural or an abbreviation of them. Every other
          unit, the word for a piece included, is written in the description's language.
        - The four figures are what the food is worth, not the total: per 100 of the unit when the
          unit is grams or millilitres, with figures_per "100"; per one piece otherwise, with
          figures_per "1".
        - Every figure is a single number. Never a range, and never two numbers joined by a dash.
        - Confidence is LOW, MEDIUM or HIGH, and describes how sure you are about the figures and
          the amount, after the answers.
        - For every item, the detail is one line saying the amount's assumption and why, such as
          "two ladles into a standard takeaway box; cream sauce from the answer". At most about
          fifteen words. No grams in the detail: the amount already says them.
        - Reply in the same language the description was written in, including the item names; only
          "g" and "ml" are always written that way.
        - The note is one short sentence about the biggest uncertainty that remains, or empty.
    """.trimIndent()

    /**
     * The first request: estimate the meal, or ask the first question (D58 §2.2, §3.1). The
     * everyday rules are carried word for word for the meal that needs no question.
     */
    fun opening(
        model: String,
        description: String,
        profile: RequestProfile = RequestProfile.guess(model),
    ): String = ChatRequest.body(
        model,
        profile,
        listOf(
            ChatRequest.Message("system", QUESTION_RULES + "\n\n" + OPENING_EXTRA + "\n\n" + EstimatePrompt.INSTRUCTIONS),
            ChatRequest.Message("user", description),
        ),
        schemaName = "meal_opening",
        schema = OPENING_SCHEMA,
    )

    /**
     * A step: the next question, or none (D58 §2.3). [cap] is the most questions this conversation
     * may ask in all; the app says how many are left in its own sentence, last.
     */
    fun step(
        model: String,
        description: String,
        asked: List<Asked>,
        cap: Int,
        profile: RequestProfile = RequestProfile.guess(model),
    ): String {
        val left = (cap - asked.size).coerceAtLeast(0)
        val messages = conversation(QUESTION_RULES, description, asked) +
            ChatRequest.Message("system", "Questions asked so far: ${asked.size}. You may ask at most $left more.")
        return ChatRequest.body(model, profile, messages, schemaName = "meal_question", schema = STEP_SCHEMA)
    }

    /**
     * The final analysis (D58 §2.4, §3.2). [moreDetail] is his *Ask again* sentence, sent as his
     * own words, as today's describe sends it; [missingAmounts] is D34's second ask, sent as the
     * app's.
     */
    fun final(
        model: String,
        description: String,
        asked: List<Asked>,
        moreDetail: String? = null,
        missingAmounts: List<String> = emptyList(),
        profile: RequestProfile = RequestProfile.guess(model),
    ): String {
        val messages = buildList {
            addAll(conversation(FINAL_INSTRUCTIONS, description, asked))
            if (!moreDetail.isNullOrBlank()) add(ChatRequest.Message("user", "More detail: $moreDetail"))
            if (missingAmounts.isNotEmpty()) {
                add(
                    ChatRequest.Message(
                        "system",
                        "Your previous answer gave no amount for: " + missingAmounts.joinToString(", ") +
                            ". Every item needs a number greater than zero and a unit. Give your " +
                            "best estimate and lower the confidence if you are unsure.",
                    ),
                )
            }
        }
        return ChatRequest.body(model, profile, messages, schemaName = "meal_analysis", schema = FINAL_SCHEMA)
    }

    private fun conversation(instructions: String, description: String, asked: List<Asked>) = buildList {
        add(ChatRequest.Message("system", instructions))
        add(ChatRequest.Message("user", description))
        asked.forEach {
            add(ChatRequest.Message("assistant", it.question))
            add(ChatRequest.Message("user", it.answer))
        }
    }

    private val QUESTION_SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonArray("required") { add("text"); add("options") }
        putJsonObject("properties") {
            putJsonObject("text") { put("type", "string") }
            putJsonObject("options") {
                put("type", "array")
                putJsonObject("items") { put("type", "string") }
            }
        }
    }

    private fun stepSchema(withEstimate: Boolean): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonArray("required") {
            add("needs_questions"); add("total_planned"); add("question")
            if (withEstimate) {
                add("items"); add("note")
            }
        }
        putJsonObject("properties") {
            putJsonObject("needs_questions") { put("type", "boolean") }
            putJsonObject("total_planned") { put("type", "integer") }
            // Never null (D58 §4.1 as amended): empty when there is no question. A model that refused
            // a nullable object would refuse every description, and D57 learns nothing from a
            // refusal of the app's own schema.
            put("question", QUESTION_SCHEMA)
            if (withEstimate) {
                putJsonObject("items") {
                    put("type", "array")
                    put("items", EstimatePrompt.ITEM_SCHEMA)
                }
                putJsonObject("note") { put("type", "string") }
            }
        }
    }

    private val STEP_SCHEMA: JsonObject = stepSchema(withEstimate = false)
    private val OPENING_SCHEMA: JsonObject = stepSchema(withEstimate = true)

    /** `plate` first, so the working is written before the figures (D58 §4.2). */
    private val FINAL_SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonArray("required") { add("plate"); add("items"); add("note") }
        putJsonObject("properties") {
            putJsonObject("plate") { put("type", "string") }
            putJsonObject("items") {
                put("type", "array")
                put("items", EstimatePrompt.ITEM_SCHEMA)
            }
            putJsonObject("note") { put("type", "string") }
        }
    }
}
