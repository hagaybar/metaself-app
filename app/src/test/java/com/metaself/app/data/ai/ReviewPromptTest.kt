package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.HeldGroup
import com.metaself.app.domain.ai.HeldWeight
import com.metaself.app.domain.ai.ReviewProcess
import com.metaself.app.domain.ai.ReviewRequest
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.AcceptedGroup
import com.metaself.app.domain.food.FactGroup
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.GramsPerUnit
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.PerUnit
import com.metaself.app.domain.food.Provenance
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/**
 * What a review sends (D54 §2) — the twin of [EstimatePromptTest]'s *NOTHING about the person using
 * the app is sent*. Every food and figure here is the spec's invented Oat biscuit or is invented.
 */
class ReviewPromptTest {

    // --- One food, and nothing else ---------------------------------------------------------------

    @Test
    fun `the user message holds exactly the seven fields, in the spec's shape`() {
        val sent = userMessage(ReviewPrompt.requestBody("a-model", request()))

        assertThat(sent.keys).containsExactly(
            "process", "name", "brand", "per_100g", "unit_name", "per_unit", "grams_per_unit",
        )
        assertThat(sent["process"]!!.jsonPrimitive.content).isEqualTo("existing_food")
        assertThat(sent["name"]!!.jsonPrimitive.content).isEqualTo("Oat biscuit")
        assertThat(sent["brand"]!!.jsonPrimitive.content).isEqualTo("")
        assertThat(sent["unit_name"]!!.jsonPrimitive.content).isEqualTo("biscuit")

        val per100g = sent["per_100g"]!!.jsonObject
        assertThat(per100g.keys).containsExactly(
            "kcal", "protein_g", "carbs_g", "fat_g", "source", "confidence",
        )
        assertThat(per100g["kcal"]!!.jsonPrimitive.content.toDouble()).isEqualTo(480.0)
        assertThat(per100g["fat_g"]!!.jsonPrimitive.content.toDouble()).isEqualTo(22.0)
        assertThat(per100g["source"]!!.jsonPrimitive.content).isEqualTo("LABEL")
        assertThat(per100g["confidence"]).isEqualTo(JsonNull)

        val perUnit = sent["per_unit"]!!.jsonObject
        assertThat(perUnit["source"]!!.jsonPrimitive.content).isEqualTo("TYPED")
        assertThat(perUnit["fat_g"]!!.jsonPrimitive.content.toDouble()).isEqualTo(1.0)

        val weight = sent["grams_per_unit"]!!.jsonObject
        assertThat(weight.keys).containsExactly("grams", "source")
        assertThat(weight["grams"]!!.jsonPrimitive.content.toDouble()).isEqualTo(18.0)
        assertThat(weight["source"]!!.jsonPrimitive.content).isEqualTo("TYPED")
    }

    @Test
    fun `a group that is not known is sent as null, and the unit name even with no per-one figures`() {
        val sent = userMessage(
            ReviewPrompt.requestBody(
                "a-model",
                ReviewRequest(
                    process = ReviewProcess.NEW_FOOD,
                    name = "Lentil soup",
                    brand = "",
                    per100g = null,
                    unitName = "bowl",
                    perUnit = null,
                    gramsPerUnit = null,
                ),
            ),
        )

        assertThat(sent["process"]!!.jsonPrimitive.content).isEqualTo("new_food")
        assertThat(sent["per_100g"]).isEqualTo(JsonNull)
        assertThat(sent["per_unit"]).isEqualTo(JsonNull)
        assertThat(sent["grams_per_unit"]).isEqualTo(JsonNull)
        assertThat(sent["unit_name"]!!.jsonPrimitive.content).isEqualTo("bowl")
    }

    @Test
    fun `an estimate is sent with its confidence`() {
        val sent = userMessage(
            ReviewPrompt.requestBody(
                "a-model",
                request(perUnit = HeldGroup(BISCUIT, Source.AI_ESTIMATE, Confidence.MEDIUM)),
            ),
        )

        val perUnit = sent["per_unit"]!!.jsonObject
        assertThat(perUnit["source"]!!.jsonPrimitive.content).isEqualTo("AI_ESTIMATE")
        assertThat(perUnit["confidence"]!!.jsonPrimitive.content).isEqualTo("MEDIUM")
    }

    @Test
    fun `sources are sent as the four names, and one this version cannot read as UNKNOWN`() {
        val sentAs = Source.values().associateWith { source ->
            val confidence = Confidence.LOW.takeIf { source == Source.AI_ESTIMATE }
            userMessage(
                ReviewPrompt.requestBody(
                    "a-model",
                    request(per100g = HeldGroup(OAT_100G, source, confidence)),
                ),
            )["per_100g"]!!.jsonObject["source"]!!.jsonPrimitive.content
        }

        assertThat(sentAs).containsExactly(
            Source.TYPED, "TYPED",
            Source.AI_ESTIMATE, "AI_ESTIMATE",
            Source.REPEATED, "REPEATED",
            Source.LABEL, "LABEL",
            Source.UNRECOGNISED, "UNKNOWN",
        )
    }

    /**
     * Built the way an editor builds it: from a stored food that has an alias, a barcode, an id and
     * dates, through the form. None of those may reach the body.
     */
    @Test
    fun `nothing but the one food's name, brand and figures is sent`() {
        val food = Food(
            id = 987_654,
            name = "Oat biscuit",
            alsoKnownAs = listOf("Round oat cookie"),
            barcode = "7290000000017",
            facts = FoodFacts(
                per100g = PerHundredGrams(OAT_100G, Provenance(Source.LABEL, setAtMillis = SET_AT)),
                perUnit = PerUnit("biscuit", BISCUIT, Provenance(Source.TYPED, setAtMillis = SET_AT)),
                gramsPerUnit = GramsPerUnit(18.0, Provenance(Source.TYPED, setAtMillis = SET_AT)),
            ),
            createdAtMillis = CREATED_AT,
            updatedAtMillis = UPDATED_AT,
        )
        val request = ReviewRequest.of(
            ReviewProcess.EXISTING_FOOD,
            FoodForm.of(food),
            food.facts,
            accepted = emptyMap(),
        )

        val body = ReviewPrompt.requestBody("a-model", request)

        assertThat(body).contains("Oat biscuit")
        listOf(
            "Round oat cookie", "7290000000017", "987654",
            SET_AT.toString(), CREATED_AT.toString(), UPDATED_AT.toString(),
        ).forEach { assertThat(body).doesNotContain(it) }
        assertThat(userMessage(body)["per_100g"]!!.jsonObject["source"]!!.jsonPrimitive.content)
            .isEqualTo("LABEL")
    }

    /**
     * The request's only inputs are the model's name and one [ReviewRequest], and a request holds
     * one food's seven fields — no list, no id, no date, no second food. A new field, or a second
     * way to build the body, fails here before it can leave the phone.
     */
    @Test
    fun `the body is built from one request, and a request holds one food and nothing else`() {
        val build: (String, ReviewRequest) -> String = ReviewPrompt::requestBody

        val ways = ReviewPrompt::class.java.declaredMethods.filter { it.name == "requestBody" }
        assertThat(ways).hasSize(1)
        assertThat(ways.single().parameterTypes.toList())
            .containsExactly(String::class.java, ReviewRequest::class.java).inOrder()

        assertThat(fieldsOf(ReviewRequest::class.java)).containsExactly(
            "process", "name", "brand", "per100g", "unitName", "perUnit", "gramsPerUnit",
        )
        assertThat(fieldsOf(HeldGroup::class.java)).containsExactly("nutrients", "source", "confidence")
        assertThat(fieldsOf(HeldWeight::class.java)).containsExactly("grams", "source")
        assertThat(fieldsOf(Nutrients::class.java))
            .containsExactly("kcal", "proteinG", "carbsG", "fatG")

        // What the editors build it from: one form, the one stored food's facts (not the food, so
        // not its aliases, barcode, id or dates), and what was accepted in this editing session.
        val from = ReviewRequest.Companion::class.java.declaredMethods.filter { it.name == "of" }
        assertThat(from).hasSize(1)
        assertThat(from.single().parameterTypes.toList()).containsExactly(
            ReviewProcess::class.java, FoodForm::class.java, FoodFacts::class.java,
            Map::class.java,
        ).inOrder()
        assertThat(build("a-model", request())).contains("Oat biscuit")
    }

    @Test
    fun `NOTHING about the person using the app is sent`() {
        // D16 as amended by D54. If this fails because the prompt's own wording used one of these
        // words, rephrase the prompt. Do not weaken the assertion.
        val body = ReviewPrompt.requestBody("a-model", request()).lowercase()

        listOf(
            "weight", "kg", "target", "profile", "age", "height", "male", "female",
            "trend", "history", "goal", "deficit", "bmi", "kilograms", "date", "eaten",
        ).forEach { forbidden ->
            val asAWord = Regex("\\b" + Regex.escape(forbidden) + "\\b")
            assertThat(asAWord.containsMatchIn(body)).isFalse()
        }
    }

    // --- Built from the form ---------------------------------------------------------------------

    @Test
    fun `the request is the form as it stands, each group with the origin Save would give it`() {
        val stored = FoodFacts(
            per100g = PerHundredGrams(OAT_100G, Provenance(Source.LABEL, setAtMillis = SET_AT)),
            perUnit = PerUnit("biscuit", BISCUIT, Provenance(Source.TYPED, setAtMillis = SET_AT)),
        )
        val form = FoodForm(
            name = " Oat biscuit ",
            brand = "N/A",
            kcalPer100g = "480", proteinPer100g = "7", carbsPer100g = "62", fatPer100g = "22",
            unitName = "biscuit",
            kcalPerUnit = "90", proteinPerUnit = "1", carbsPerUnit = "12", fatPerUnit = "4",
            gramsPerUnit = "18",
        )

        val request = ReviewRequest.of(
            ReviewProcess.EXISTING_FOOD,
            form,
            stored,
            accepted = mapOf(FactGroup.PER_UNIT to AcceptedGroup(Confidence.MEDIUM)),
        )

        assertThat(request.name).isEqualTo("Oat biscuit")
        assertThat(request.brand).isEqualTo("")
        assertThat(request.per100g).isEqualTo(HeldGroup(OAT_100G, Source.LABEL, null))
        assertThat(request.unitName).isEqualTo("biscuit")
        assertThat(request.perUnit)
            .isEqualTo(HeldGroup(Nutrients(90.0, 1.0, 12.0, 4.0), Source.AI_ESTIMATE, Confidence.MEDIUM))
        assertThat(request.gramsPerUnit).isEqualTo(HeldWeight(18.0, Source.TYPED))
    }

    @Test
    fun `a half-typed group is not sent, and a named unit is sent with no figures`() {
        val form = FoodForm(
            name = "Lentil soup",
            brand = "",
            kcalPer100g = "60", proteinPer100g = "4",
            unitName = "bowl",
        )

        val request = ReviewRequest.of(ReviewProcess.NEW_FOOD, form, null, emptyMap())

        assertThat(request.per100g).isNull()
        assertThat(request.perUnit).isNull()
        assertThat(request.unitName).isEqualTo("bowl")
        assertThat(request.gramsPerUnit).isNull()
        assertThat(request.process).isEqualTo(ReviewProcess.NEW_FOOD)
    }

    @Test
    fun `a real brand is sent as typed`() {
        val request = ReviewRequest.of(
            ReviewProcess.NEW_FOOD,
            FoodForm(name = "Oat biscuit", brand = " Northmill "),
            null,
            emptyMap(),
        )

        assertThat(request.brand).isEqualTo("Northmill")
    }

    // --- The instructions ------------------------------------------------------------------------

    @Test
    fun `a label is kept unless impossible, and every change needs a reason`() {
        val body = ReviewPrompt.requestBody("a-model", request())

        assertThat(body).contains("LABEL")
        assertThat(body).contains("internally impossible")
        assertThat(body).contains("4 kcal per gram of protein or carbohydrate, 9 per gram of fat")
        assertThat(body).contains("For each figure you change, give a short reason")
    }

    @Test
    fun `it never states what a piece weighs and never names a unit`() {
        val body = ReviewPrompt.requestBody("a-model", request())

        assertThat(body).contains("Never state what one piece weighs")
        assertThat(body).contains("never name a unit")
    }

    @Test
    fun `one number per figure, at temperature 0, in the language of the name`() {
        val body = ReviewPrompt.requestBody("a-model", request())
        val parsed = Json.parseToJsonElement(body).jsonObject

        assertThat(body).contains("never a range")
        assertThat(body).contains("language of the food's name")
        assertThat(parsed["temperature"]!!.jsonPrimitive.content).isEqualTo("0")
        assertThat(parsed["model"]!!.jsonPrimitive.content).isEqualTo("a-model")
    }

    /**
     * D54 §9.2: with both groups and what one weighs, the model is told the two groups must agree
     * through that weight, and what to do when they do not. Without all three there is nothing to
     * check them against, and the rule is not sent.
     */
    @Test
    fun `the two groups are cross-checked through what one weighs, only when all three are held`() {
        val rule = "should equal the figures per 100 g times grams_per_unit / 100"

        val all = systemMessage(ReviewPrompt.requestBody("a-model", request()))
        assertThat(all).contains(rule)
        assertThat(all).contains("which group you believe and why")
        assertThat(all).contains("propose the corrected figures")
        assertThat(all).contains("flag it in the note")
        // The existing rule stands beside it.
        assertThat(all).contains("Never state what one piece weighs")

        listOf(
            request(per100g = null),
            request(perUnit = null),
            request().copy(gramsPerUnit = null),
        ).forEach { without ->
            val system = systemMessage(ReviewPrompt.requestBody("a-model", without))
            assertThat(system).doesNotContain(rule)
            assertThat(system).contains("Never state what one piece weighs")
        }
    }

    /** D54 §9.3: every reply ends in a verdict, asked for in the instructions and the schema. */
    @Test
    fun `the note is asked for as a one-sentence verdict, never empty`() {
        val body = ReviewPrompt.requestBody("a-model", request())
        val system = systemMessage(body)

        assertThat(system).contains("what you concluded, in one sentence")
        assertThat(system).contains("Never leave the note empty")
        assertThat(system).doesNotContain("or leave it empty")

        val note = Json.parseToJsonElement(body).jsonObject["response_format"]!!.jsonObject
            .getValue("json_schema").jsonObject.getValue("schema").jsonObject
            .getValue("properties").jsonObject.getValue("note").jsonObject
        assertThat(note["type"]!!.jsonPrimitive.content).isEqualTo("string")
        assertThat(note["description"]!!.jsonPrimitive.content)
            .isEqualTo("What you concluded, in one sentence. Never empty.")
    }

    @Test
    fun `Hebrew survives being sent`() {
        val body = ReviewPrompt.requestBody("a-model", request(name = "עוגיית שיבולת שועל"))

        assertThat(userMessage(body)["name"]!!.jsonPrimitive.content).isEqualTo("עוגיית שיבולת שועל")
    }

    // --- The reply's shape (§3) ------------------------------------------------------------------

    @Test
    fun `the reply is pinned by a strict schema, every field required, and carries no weight`() {
        val format = Json.parseToJsonElement(ReviewPrompt.requestBody("a-model", request()))
            .jsonObject["response_format"]!!.jsonObject
        assertThat(format["type"]!!.jsonPrimitive.content).isEqualTo("json_schema")
        val jsonSchema = format["json_schema"]!!.jsonObject
        assertThat(jsonSchema["strict"]!!.jsonPrimitive.content).isEqualTo("true")
        val schema = jsonSchema["schema"]!!.jsonObject

        assertThat(schema["type"]!!.jsonPrimitive.content).isEqualTo("object")
        assertThat(schema["additionalProperties"]!!.jsonPrimitive.content).isEqualTo("false")
        assertThat(schema["properties"]!!.jsonObject.keys)
            .containsExactly("per_100g", "per_unit", "note")
        assertThat(requiredOf(schema)).containsExactly("per_100g", "per_unit", "note")

        listOf("per_100g", "per_unit").forEach { group ->
            val options = schema["properties"]!!.jsonObject[group]!!.jsonObject["anyOf"]!!.jsonArray
            assertThat(options).hasSize(2)
            assertThat(options[1].jsonObject["type"]!!.jsonPrimitive.content).isEqualTo("null")

            val suggestion = options[0].jsonObject
            val fields = listOf(
                "kcal", "protein_g", "carbs_g", "fat_g",
                "kcal_reason", "protein_reason", "carbs_reason", "fat_reason", "confidence",
            )
            assertThat(suggestion["additionalProperties"]!!.jsonPrimitive.content).isEqualTo("false")
            assertThat(suggestion["properties"]!!.jsonObject.keys).containsExactlyElementsIn(fields)
            assertThat(requiredOf(suggestion)).containsExactlyElementsIn(fields)
            val properties = suggestion["properties"]!!.jsonObject
            listOf("kcal", "protein_g", "carbs_g", "fat_g").forEach {
                assertThat(properties[it]!!.jsonObject["type"]!!.jsonPrimitive.content)
                    .isEqualTo("number")
            }
            assertThat(
                properties["confidence"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content },
            ).containsExactly("LOW", "MEDIUM", "HIGH")
        }

        // No weight, no unit name, anywhere in what can come back (§3: by construction).
        val everyName = propertyNames(schema)
        assertThat(everyName.none { "gram" in it || "weigh" in it || "unit_name" in it }).isTrue()
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private fun request(
        name: String = "Oat biscuit",
        per100g: HeldGroup? = HeldGroup(OAT_100G, Source.LABEL, null),
        perUnit: HeldGroup? = HeldGroup(BISCUIT, Source.TYPED, null),
    ) = ReviewRequest(
        process = ReviewProcess.EXISTING_FOOD,
        name = name,
        brand = "",
        per100g = per100g,
        unitName = "biscuit",
        perUnit = perUnit,
        gramsPerUnit = HeldWeight(18.0, Source.TYPED),
    )

    private fun userMessage(body: String): JsonObject {
        val messages = Json.parseToJsonElement(body).jsonObject["messages"]!!.jsonArray
        assertThat(messages.map { it.jsonObject["role"]!!.jsonPrimitive.content })
            .containsExactly("system", "user").inOrder()
        return Json.parseToJsonElement(messages[1].jsonObject["content"]!!.jsonPrimitive.content)
            .jsonObject
    }

    private fun systemMessage(body: String): String =
        Json.parseToJsonElement(body).jsonObject["messages"]!!.jsonArray[0].jsonObject["content"]!!
            .jsonPrimitive.content

    private fun requiredOf(schema: JsonObject): List<String> =
        schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }

    private fun propertyNames(schema: JsonObject): List<String> {
        val here = schema["properties"]?.jsonObject ?: JsonObject(emptyMap())
        val options = schema["anyOf"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        return here.keys.toList() +
            here.values.flatMap { propertyNames(it.jsonObject) } +
            options.flatMap { propertyNames(it) }
    }

    /** The declared instance fields, which for these data classes are exactly their properties. */
    private fun fieldsOf(type: Class<*>): List<String> =
        type.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }

    private companion object {
        // Invented: the spec's Oat biscuit (D54 §2).
        val OAT_100G = Nutrients(480.0, 7.0, 62.0, 22.0)
        val BISCUIT = Nutrients(90.0, 1.0, 12.0, 1.0)

        // Invented instants, distinctive enough that no figure could contain them by accident.
        const val SET_AT = 1_790_123_456_789L
        const val CREATED_AT = 1_780_111_222_333L
        const val UPDATED_AT = 1_785_444_555_666L
    }
}
