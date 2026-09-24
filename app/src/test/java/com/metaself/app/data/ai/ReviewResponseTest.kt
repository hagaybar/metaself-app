package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.Figure
import com.metaself.app.domain.ai.FigureChange
import com.metaself.app.domain.ai.FoodReview
import com.metaself.app.domain.ai.HeldGroup
import com.metaself.app.domain.ai.HeldWeight
import com.metaself.app.domain.ai.ReviewProcess
import com.metaself.app.domain.ai.ReviewRequest
import com.metaself.app.domain.ai.ReviewResult
import com.metaself.app.domain.ai.Suggestion
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.FactGroup
import com.metaself.app.domain.food.Nutrients
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

/**
 * What a review's answer is read as (D54 §3). Every food and figure here is one of the spec's
 * invented examples, or is invented.
 */
class ReviewResponseTest {

    // --- The spec's worked example ---------------------------------------------------------------

    @Test
    fun `a label kept exactly is no suggestion, and one changed figure is one change`() {
        val review = proposed(
            ReviewResponse.parse(
                reply(
                    per100g = group(480, 7, 62, 22, confidence = "HIGH"),
                    perUnit = group(
                        90, 1, 12, 4,
                        fatReason = "18 g of a food with 22 g of fat per 100 g holds about 4 g",
                        confidence = "MEDIUM",
                    ),
                ),
                OAT_BISCUIT,
            ),
        )

        assertThat(review.per100g).isNull()
        assertThat(review.perUnit).isEqualTo(
            Suggestion(
                nutrients = Nutrients(90.0, 1.0, 12.0, 4.0),
                confidence = Confidence.MEDIUM,
                filled = false,
                changes = listOf(
                    FigureChange(
                        Figure.FAT, 1.0, 4.0,
                        "18 g of a food with 22 g of fat per 100 g holds about 4 g",
                    ),
                ),
                reason = null,
                keptFrom = Source.TYPED,
            ),
        )
        assertThat(review.setAside).isEmpty()
        assertThat(review.note).isNull()
    }

    @Test
    fun `a figure the model echoes is kept exactly as held, not as the model wrote it`() {
        val request = OAT_BISCUIT.copy(
            per100g = HeldGroup(Nutrients(480.0, 7.0, 62.0, 3.25), Source.LABEL, null),
        )

        val review = proposed(
            ReviewResponse.parse(
                reply(
                    per100g = group(480, 7, 62, "3.2500000001", confidence = "HIGH"),
                    perUnit = null,
                ),
                request,
            ),
        )

        assertThat(review.per100g).isNull()
    }

    @Test
    fun `a changed figure is rounded to one decimal, and the ones kept stay exactly as held`() {
        val request = OAT_BISCUIT.copy(
            per100g = HeldGroup(Nutrients(480.0, 7.25, 62.0, 22.0), Source.TYPED, null),
        )

        val review = proposed(
            ReviewResponse.parse(
                reply(
                    per100g = group(
                        "471.26", "7.25", 62, 22,
                        kcalReason = "the macros give about 471 kcal", confidence = "MEDIUM",
                    ),
                    perUnit = null,
                ),
                request,
            ),
        )

        val suggestion = review.per100g!!
        assertThat(suggestion.nutrients).isEqualTo(Nutrients(471.3, 7.25, 62.0, 22.0))
        assertThat(suggestion.changes)
            .containsExactly(FigureChange(Figure.KCAL, 480.0, 471.3, "the macros give about 471 kcal"))
    }

    @Test
    fun `a figure that differs only below the first decimal is not a change`() {
        val request = OAT_BISCUIT.copy(
            per100g = HeldGroup(Nutrients(480.0, 7.0, 62.0, 22.0), Source.TYPED, null),
        )

        val review = proposed(
            ReviewResponse.parse(
                reply(per100g = group(480, 7, "62.04", 22, carbsReason = "rounding"), perUnit = null),
                request,
            ),
        )

        assertThat(review.per100g).isNull()
    }

    /**
     * A scanned food's per-100 g figures are a serving's scaled, so they are stored as long
     * doubles — here an invented 70 g serving of 6 g protein, 9 g carbohydrate and 4 g fat. A model
     * echoing them back rounds them, to two decimals or one, and gives no reason, because it
     * changed nothing. That is kept, not a change without a reason (D54 as amended 2026-09-24).
     */
    @Test
    fun `a held figure the model echoes rounded is kept, not a change without a reason`() {
        val request = OAT_BISCUIT.copy(
            per100g = HeldGroup(
                Nutrients(100.0, 8.571428571428571, 12.857142857142858, 5.714285714285714),
                Source.LABEL,
                null,
            ),
            perUnit = HeldGroup(Nutrients(95.0, 4.25, 6.3, 3.35), Source.LABEL, null),
        )

        val result = ReviewResponse.parse(
            reply(
                per100g = group(100, "8.57", "12.9", "5.7", confidence = "HIGH"),
                perUnit = group(95, "4.3", "6.3", "3.4", confidence = "HIGH"),
            ),
            request,
        )

        assertThat(proposed(result)).isEqualTo(FoodReview(null, null, null, emptyList()))
    }

    /** The spec's invented impossible label: 120 kcal against macros worth about 370. */
    @Test
    fun `a label can be contradicted, with its reason`() {
        val request = OAT_BISCUIT.copy(
            per100g = HeldGroup(Nutrients(120.0, 30.0, 40.0, 10.0), Source.LABEL, null),
        )

        val review = proposed(
            ReviewResponse.parse(
                reply(
                    per100g = group(
                        370, 30, 40, 10,
                        kcalReason = "the macros alone give about 370 kcal", confidence = "MEDIUM",
                    ),
                    perUnit = null,
                ),
                request,
            ),
        )

        assertThat(review.per100g!!.changes).containsExactly(
            FigureChange(Figure.KCAL, 120.0, 370.0, "the macros alone give about 370 kcal"),
        )
    }

    /**
     * D54 §5 as amended 2026-09-24: an accepted group is labelled by its weakest member, so the
     * suggestion has to say where the figures it kept came from. None kept, nothing to say.
     */
    @Test
    fun `a suggestion says where the figures it kept came from, and nothing when it kept none`() {
        val request = OAT_BISCUIT.copy(
            per100g = HeldGroup(Nutrients(480.0, 7.0, 62.0, 22.0), Source.REPEATED, null),
        )

        val oneChanged = proposed(
            ReviewResponse.parse(
                reply(
                    per100g = group(470, 7, 62, 22, kcalReason = "A reason.", confidence = "LOW"),
                    perUnit = null,
                ),
                request,
            ),
        )
        val allChanged = proposed(
            ReviewResponse.parse(
                reply(
                    per100g = group(
                        470, 8, 60, 21,
                        kcalReason = "A reason.", proteinReason = "A reason.",
                        carbsReason = "A reason.", fatReason = "A reason.", confidence = "LOW",
                    ),
                    perUnit = null,
                ),
                request,
            ),
        )

        assertThat(oneChanged.per100g!!.keptFrom).isEqualTo(Source.REPEATED)
        assertThat(allChanged.per100g!!.keptFrom).isNull()
    }

    // --- Fills -----------------------------------------------------------------------------------

    @Test
    fun `a group the form did not know is filled, with one reason for the group`() {
        val review = proposed(
            ReviewResponse.parse(
                reply(
                    per100g = group(60, 4, 9, 1, kcalReason = "a typical lentil soup", confidence = "LOW"),
                    perUnit = group(
                        180, 12, 27, 3,
                        kcalReason = "a bowl of about 300 g", confidence = "LOW",
                    ),
                ),
                LENTIL_SOUP,
            ),
        )

        assertThat(review.per100g).isEqualTo(
            Suggestion(
                nutrients = Nutrients(60.0, 4.0, 9.0, 1.0),
                confidence = Confidence.LOW,
                filled = true,
                changes = emptyList(),
                reason = "a typical lentil soup",
            ),
        )
        assertThat(review.perUnit!!.nutrients).isEqualTo(Nutrients(180.0, 12.0, 27.0, 3.0))
        assertThat(review.perUnit!!.filled).isTrue()
    }

    @Test
    fun `a fill's reason may sit on any figure`() {
        val review = proposed(
            ReviewResponse.parse(
                reply(per100g = group(60, 4, 9, 1, fatReason = "a typical lentil soup"), perUnit = null),
                LENTIL_SOUP,
            ),
        )

        assertThat(review.per100g!!.reason).isEqualTo("a typical lentil soup")
    }

    @Test
    fun `a fill's figures are rounded to one decimal`() {
        val review = proposed(
            ReviewResponse.parse(
                reply(
                    per100g = group("60.44", "4.06", 9, 1, kcalReason = "typical"),
                    perUnit = null,
                ),
                LENTIL_SOUP,
            ),
        )

        assertThat(review.per100g!!.nutrients).isEqualTo(Nutrients(60.4, 4.1, 9.0, 1.0))
    }

    @Test
    fun `a fill with no reason at all is set aside`() {
        val result = ReviewResponse.parse(
            reply(
                per100g = group(60, 4, 9, 1),
                perUnit = group(180, 12, 27, 3, kcalReason = "a bowl of about 300 g"),
            ),
            LENTIL_SOUP,
        )

        val review = proposed(result)
        assertThat(review.per100g).isNull()
        assertThat(review.perUnit).isNotNull()
        assertThat(review.setAside).containsExactly(FactGroup.PER_100G)
    }

    // --- Set aside, whole, never repaired --------------------------------------------------------

    @Test
    fun `a change with a blank reason sets that group aside and keeps the other`() {
        val review = proposed(
            ReviewResponse.parse(
                reply(
                    per100g = group(500, 7, 62, 22, kcalReason = "  ", confidence = "HIGH"),
                    perUnit = group(90, 1, 12, 4, fatReason = "about 4 g in 18 g", confidence = "MEDIUM"),
                ),
                OAT_BISCUIT,
            ),
        )

        assertThat(review.per100g).isNull()
        assertThat(review.perUnit!!.changes.single().figure).isEqualTo(Figure.FAT)
        assertThat(review.setAside).containsExactly(FactGroup.PER_100G)
    }

    @Test
    fun `a change with no reason of its own borrows the group's first reason`() {
        val review = proposed(
            ReviewResponse.parse(
                reply(
                    per100g = group(
                        470, 8, 62, 22,
                        proteinReason = "the macros give about 470 kcal with 8 g of protein",
                        confidence = "MEDIUM",
                    ),
                    perUnit = null,
                ),
                OAT_BISCUIT,
            ),
        )

        assertThat(review.setAside).isEmpty()
        assertThat(review.per100g!!.changes).containsExactly(
            FigureChange(Figure.KCAL, 480.0, 470.0, "the macros give about 470 kcal with 8 g of protein"),
            FigureChange(Figure.PROTEIN, 7.0, 8.0, "the macros give about 470 kcal with 8 g of protein"),
        ).inOrder()
    }

    @Test
    fun `a figure past D42's ceiling for its basis sets the group aside`() {
        // Per 100 g: at most 1000 kcal and 110 g. Per one: at most 5000 kcal and 500 g.
        val past100g = ReviewResponse.parse(
            reply(per100g = group(1001, 7, 62, 22, kcalReason = "why"), perUnit = TEN_KCAL_MORE),
            OAT_BISCUIT,
        )
        val pastUnit = ReviewResponse.parse(
            reply(per100g = null, perUnit = group(90, 1, 12, 501, fatReason = "why")),
            OAT_BISCUIT,
        )
        val atUnit = ReviewResponse.parse(
            reply(per100g = null, perUnit = group(5000, 1, 12, 500, kcalReason = "why", fatReason = "why")),
            OAT_BISCUIT,
        )

        assertThat(proposed(past100g).setAside).containsExactly(FactGroup.PER_100G)
        assertThat(pastUnit).isInstanceOf(ReviewResult.Failed::class.java)
        assertThat(proposed(atUnit).perUnit!!.nutrients).isEqualTo(Nutrients(5000.0, 1.0, 12.0, 500.0))
    }

    @Test
    fun `a negative, non-finite or missing figure sets the group aside`() {
        listOf(
            group(90, 1, 12, -4, fatReason = "why"),
            group(90, 1, 12, "\"NaN\"", fatReason = "why"),
            group(90, 1, 12, "\"Infinity\"", fatReason = "why"),
            """{"kcal":90,"protein_g":1,"carbs_g":12,"kcal_reason":"","protein_reason":"",""" +
                """"carbs_reason":"","fat_reason":"why","confidence":"LOW"}""",
        ).forEach { bad ->
            val review = proposed(
                ReviewResponse.parse(reply(per100g = ONE_CHANGE_100G, perUnit = bad), OAT_BISCUIT),
            )
            assertThat(review.perUnit).isNull()
            assertThat(review.setAside).containsExactly(FactGroup.PER_UNIT)
        }
    }

    @Test
    fun `when every group that changed is set aside the reply is unreadable`() {
        val result = ReviewResponse.parse(
            reply(
                per100g = group(500, 7, 62, 22, confidence = "HIGH"),
                perUnit = group(90, 1, 12, 1),
            ),
            OAT_BISCUIT,
        )

        assertThat(result).isInstanceOf(ReviewResult.Failed::class.java)
        assertThat((result as ReviewResult.Failed).failure)
            .isInstanceOf(EstimateResult.Unreadable::class.java)
    }

    // --- The rest --------------------------------------------------------------------------------

    @Test
    fun `per one is ignored when the editor names no unit`() {
        val request = OAT_BISCUIT.copy(unitName = "", perUnit = null, gramsPerUnit = null)

        val review = proposed(
            ReviewResponse.parse(
                reply(per100g = null, perUnit = group(90, 1, 12, 4, kcalReason = "a biscuit")),
                request,
            ),
        )

        assertThat(review.perUnit).isNull()
        assertThat(review.setAside).isEmpty()
    }

    @Test
    fun `a reply that changes nothing is a real answer with nothing to show`() {
        val result = ReviewResponse.parse(
            reply(per100g = group(480, 7, 62, 22), perUnit = null, note = "Consistent."),
            OAT_BISCUIT,
        )

        assertThat(result).isEqualTo(
            ReviewResult.Proposed(
                FoodReview(per100g = null, perUnit = null, note = "Consistent.", setAside = emptyList()),
            ),
        )
    }

    @Test
    fun `null for a group means leave it, and cannot remove it`() {
        val review = proposed(ReviewResponse.parse(reply(per100g = null, perUnit = null), OAT_BISCUIT))

        assertThat(review).isEqualTo(FoodReview(null, null, null, emptyList()))
    }

    @Test
    fun `an unknown confidence reads LOW`() {
        val review = proposed(
            ReviewResponse.parse(
                reply(per100g = null, perUnit = group(90, 1, 12, 4, fatReason = "why", confidence = "PROBABLY")),
                OAT_BISCUIT,
            ),
        )

        assertThat(review.perUnit!!.confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun `garbage is unreadable, never an exception`() {
        listOf(
            "not json",
            """{"choices":[]}""",
            """{"choices":[{"message":{"content":"not json either"}}]}""",
            envelope("""{"per_unit":null,"note":""}"""),
            envelope("""{"per_100g":"nothing","per_unit":null,"note":""}"""),
        ).forEach { body ->
            val result = ReviewResponse.parse(body, OAT_BISCUIT)
            assertThat(result).isInstanceOf(ReviewResult.Failed::class.java)
            assertThat((result as ReviewResult.Failed).failure)
                .isInstanceOf(EstimateResult.Unreadable::class.java)
        }
    }

    @Test
    fun `a failure carries only one of the five failures a screen already words`() {
        ReviewResult.Failed(EstimateResult.NoKey)
        ReviewResult.Failed(EstimateResult.Unreadable("x"))

        runCatching { ReviewResult.Failed(EstimateResult.AmountMissing(listOf("x"))) }
            .also { assertThat(it.isFailure).isTrue() }
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private fun proposed(result: ReviewResult): FoodReview =
        (result as ReviewResult.Proposed).review

    private fun group(
        kcal: Any, protein: Any, carbs: Any, fat: Any,
        kcalReason: String = "", proteinReason: String = "",
        carbsReason: String = "", fatReason: String = "",
        confidence: String = "MEDIUM",
    ): String =
        """{"kcal":$kcal,"protein_g":$protein,"carbs_g":$carbs,"fat_g":$fat,""" +
            """"kcal_reason":${JsonPrimitive(kcalReason)},"protein_reason":${JsonPrimitive(proteinReason)},""" +
            """"carbs_reason":${JsonPrimitive(carbsReason)},"fat_reason":${JsonPrimitive(fatReason)},""" +
            """"confidence":"$confidence"}"""

    private fun reply(per100g: String?, perUnit: String?, note: String = ""): String =
        envelope(
            """{"per_100g":${per100g ?: "null"},"per_unit":${perUnit ?: "null"},""" +
                """"note":${JsonPrimitive(note)}}""",
        )

    private fun envelope(content: String): String =
        """{"choices":[{"message":{"content":${JsonPrimitive(content)}}}]}"""

    private companion object {
        // Invented: the spec's Oat biscuit and Lentil soup (D54 §2, plan's worked examples).
        val OAT_BISCUIT = ReviewRequest(
            process = ReviewProcess.EXISTING_FOOD,
            name = "Oat biscuit",
            brand = "",
            per100g = HeldGroup(Nutrients(480.0, 7.0, 62.0, 22.0), Source.LABEL, null),
            unitName = "biscuit",
            perUnit = HeldGroup(Nutrients(90.0, 1.0, 12.0, 1.0), Source.TYPED, null),
            gramsPerUnit = HeldWeight(18.0, Source.TYPED),
        )
        val LENTIL_SOUP = ReviewRequest(
            process = ReviewProcess.NEW_FOOD,
            name = "Lentil soup",
            brand = "",
            per100g = null,
            unitName = "bowl",
            perUnit = null,
            gramsPerUnit = null,
        )

        const val ONE_CHANGE_100G =
            """{"kcal":470,"protein_g":7,"carbs_g":62,"fat_g":22,"kcal_reason":"why",""" +
                """"protein_reason":"","carbs_reason":"","fat_reason":"","confidence":"LOW"}"""
        const val TEN_KCAL_MORE =
            """{"kcal":100,"protein_g":1,"carbs_g":12,"fat_g":1,"kcal_reason":"why",""" +
                """"protein_reason":"","carbs_reason":"","fat_reason":"","confidence":"LOW"}"""
    }
}
