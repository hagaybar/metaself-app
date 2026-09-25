package com.metaself.app.ui.screen.food

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.metaself.app.data.food.aPerUnit
import com.metaself.app.data.food.weighing
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.Figure
import com.metaself.app.domain.ai.FigureChange
import com.metaself.app.domain.ai.FoodReview
import com.metaself.app.domain.ai.ReviewItem
import com.metaself.app.domain.ai.Suggestion
import com.metaself.app.domain.ai.UnitSuggestion
import com.metaself.app.domain.ai.Verdict
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.FactGroup
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.Provenance
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.food.FormReview
import com.metaself.app.ui.food.Review
import com.metaself.app.ui.food.ReviewActions
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A review on a food's page (D54 §8–§12), drawn from a state — moved with the editor from the
 * list's render test (D55 §2: the same composables, moved, not copied). The Oat biscuit's figures
 * are invented.
 *
 * Compose, so JUnit 4 — `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class FoodPageReviewRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    /**
     * D54 §12.6: a suggestion goes straight into its box, drawn as suggested and said so to a
     * screen reader, with its reason and its Back beneath it — no list of changes, no Apply, Keep
     * mine or Undo — and Save gives way to Accept changes and save and Cancel.
     */
    @Test
    fun `a suggestion is in its box, marked, with its reason and its Back beneath it`() {
        val texts = draw(answered(FoodReview(null, fatTo4, "Fat was low.", emptyList())))

        assertThat(render.fieldsSaid(SUGGESTED)).containsExactly("4")
        assertThat(texts).contains("Reviewed: 1 suggestion, in the boxes below — Fat was low.")
        assertThat(render.isDrawnBefore("What one of it is worth", "A reason.")).isTrue()
        assertThat(render.isDrawnBefore("A reason.", "Back to 1")).isTrue()
        assertThat(texts.none { "→" in it }).isTrue()
        listOf("Apply these changes", "Keep mine", "Undo", "Save", "Leave it alone", "Review the figures", "Dismiss")
            .forEach { assertWithMessage(it).that(texts).doesNotContain(it) }
        assertThat(texts).contains("Accept changes and save")
        assertThat(texts).contains("Cancel")
        // Named with its box for a screen reader: eight boxes can read "Back to …" at once.
        assertThat(render.describedCount("Fat (g) per biscuit back to 1")).isEqualTo(1)
    }

    @Test
    fun `with nothing waiting in the boxes, Save and Leave it alone are back`() {
        val texts = draw(reviewing(null))

        assertThat(texts).contains("Save")
        assertThat(texts).contains("Leave it alone")
        assertThat(texts).doesNotContain("Accept changes and save")
        assertThat(render.fieldsSaid(SUGGESTED)).isEmpty()
    }

    /** §12.6: a unit named goes back with its figures; they have no Back of their own. */
    @Test
    fun `a named unit carries the only Back for its bundle, and an empty box says Clear`() {
        val answer = FoodReview(
            per100g = null,
            perUnit = Suggestion(Nutrients(24.9, 1.2, 2.1, 1.4), Confidence.MEDIUM, true, emptyList(), "One spoon of it."),
            note = null,
            setAside = emptyList(),
            unit = UnitSuggestion("tablespoon", "A dip is counted by the spoon.", Confidence.MEDIUM, null),
        )
        val texts = draw(answered(answer, humus()))

        assertThat(render.fieldsSaid(SUGGESTED)).containsExactly("tablespoon", "24.9", "1.2", "2.1", "1.4")
        assertThat(texts.count { it == "Clear" }).isEqualTo(1)
        assertThat(texts.count { it.startsWith("Back to") }).isEqualTo(0)
        assertThat(texts.count { it == "One spoon of it." }).isEqualTo(1)
    }

    /** §12.6: saved meals that count the food in units are named under a pending unit. */
    @Test
    fun `saved meals counting the food in units are named under a renamed unit`() {
        val answer = FoodReview(
            null, null, null, emptyList(),
            unit = UnitSuggestion("cracker", "The usual word.", Confidence.MEDIUM, Source.TYPED),
        )
        val state = answered(answer, weight = true).let { it.copy(editing = it.editing!!.copy(unitMeals = 2)) }

        val texts = draw(state)

        assertThat(texts).contains("2 saved meals count this food in biscuit; after saving they will count it in cracker.")
    }

    @Test
    fun `the weight line says a review may suggest one, kept as an estimate`() {
        val texts = draw(reviewing(null))

        assertThat(texts).contains(
            "Nothing in the app works this out. A review may suggest one, which is kept as an estimate. " +
                "It is what turns grams into units and back, so a wrong one would follow into everything you log afterwards.",
        )
    }

    @Test
    fun `the button says what is sent, and reads Reviewing while it is out`() {
        val idle = draw(reviewing(null))

        assertThat(idle).contains("Review the figures")
        assertThat(idle).contains(SENDS)
        assertThat(render.isDrawnBefore("Review the figures", "What 100 g of it are worth")).isTrue()

        val asking = draw(reviewing(Review.Asking()))

        assertThat(asking).contains("Reviewing…")
        assertThat(asking).doesNotContain("Review the figures")
    }

    @Test
    fun `every item set aside is said in its own line`() {
        val texts = draw(
            reviewing(
                Review.Shown(
                    FoodReview(
                        null, null, "A short note.",
                        listOf(ReviewItem.NAME, ReviewItem.PER_100G, ReviewItem.PER_UNIT, ReviewItem.WEIGHT),
                    ),
                    unusable = true,
                ),
            ),
        )

        assertThat(texts).contains("Its suggestion for the name couldn't be used.")
        assertThat(texts).contains("Its suggestion for per 100 g couldn't be used.")
        assertThat(texts).contains("Its suggestion for per biscuit couldn't be used.")
        assertThat(texts).contains("Its suggestion for what one weighs couldn't be used.")
    }

    @Test
    fun `a review that changes nothing says so`() {
        val texts = draw(
            reviewing(Review.Shown(FoodReview(null, null, null, emptyList()), nothingSuggested = true)),
        )

        assertThat(texts).contains("Reviewed: no changes suggested.")
        assertThat(texts).doesNotContain("Accept changes and save")
        assertThat(texts).contains("Dismiss")
    }

    // --- Every outcome ends in a line under the button (D54 §9.4) ----------------------------------

    /**
     * Whatever the review came to, one plain line says so under the button and its small print
     * (§10.4), with the model's verdict when it gave one.
     */
    @Test
    fun `every review outcome is said in one line under the button and its small print`() {
        val outcomes: List<Pair<Review, String>> = listOf(
            Review.Shown(FoodReview(null, null, "The figures agree.", emptyList()), nothingSuggested = true) to
                "Reviewed: no changes suggested — The figures agree.",
            Review.Shown(FoodReview(null, null, null, emptyList()), nothingSuggested = true) to
                "Reviewed: no changes suggested.",
            // D54 §10.3: nothing proposed, but the model said it found a problem — never "no changes".
            Review.Shown(
                FoodReview(null, null, "Per piece does not match per 100 g.", emptyList(), Verdict.PROBLEM_FOUND),
                nothingSuggested = true,
            ) to "Reviewed: a problem found — Per piece does not match per 100 g.",
            Review.Shown(FoodReview(null, null, null, emptyList(), Verdict.PROBLEM_FOUND), nothingSuggested = true) to
                "Reviewed: a problem found.",
            Review.Shown(
                FoodReview(null, null, "Per 100 g looks off.", listOf(ReviewItem.PER_100G)),
                unusable = true,
            ) to "The model's answer arrived, but its suggestions could not be used — Per 100 g looks off.",
            Review.Shown(FoodReview(null, null, null, listOf(ReviewItem.PER_100G)), unusable = true) to
                "The model's answer arrived, but its suggestions could not be used.",
            Review.Shown(FoodReview(null, null, null, emptyList())) to
                "Reviewed: no suggestions left.",
            Review.Failed(EstimateResult.NoKey) to
                "No API key yet. Add one in settings, or type the numbers instead.",
            Review.Failed(EstimateResult.Unreadable("x")) to
                "The answer could not be understood. Type the numbers instead.",
            Review.Failed(null) to COULD_NOT_OPEN,
        )

        outcomes.forEach { (review, line) ->
            val texts = draw(reviewing(review))

            assertWithMessage(line).that(texts.count { it == line }).isEqualTo(1)
            assertWithMessage(line).that(render.isDrawnBefore("Review the figures", line)).isTrue()
            assertWithMessage(line).that(render.isDrawnBefore(SENDS, line)).isTrue()
        }
    }

    /**
     * D54 §10.4: the small print belongs to the button — it says what pressing it sends — so it
     * sits directly under it, and what the review came to comes after, together: the line, then
     * **Dismiss** and **Show the model's answer**.
     */
    @Test
    fun `the small print sits under the button, and the outcome and its buttons come below it`() {
        val state = reviewing(null).let { state ->
            state.copy(
                editing = state.editing!!.copy(
                    reviewing = FormReview(
                        review = Review.Shown(
                            FoodReview(null, null, "Per biscuit is off.", emptyList(), Verdict.PROBLEM_FOUND),
                            nothingSuggested = true,
                        ),
                        modelAnswer = """{"per_100g":null}""",
                    ),
                ),
            )
        }
        val line = "Reviewed: a problem found — Per biscuit is off."

        val texts = draw(state)

        assertThat(texts.indexOf(SENDS)).isEqualTo(texts.indexOf("Review the figures") + 1)
        assertThat(render.isDrawnBefore(SENDS, line)).isTrue()
        assertThat(render.isDrawnBefore(line, "Dismiss")).isTrue()
        assertThat(render.isDrawnBefore("Dismiss", "Show the model's answer")).isTrue()
    }

    /** A failure is said under the button and nowhere else — not again in the slot above Save. */
    @Test
    fun `a review's failure is said once, under the button, with a way to take it down`() {
        val texts = draw(reviewing(Review.Failed(EstimateResult.CeilingReached)))

        val sentence = "You have used today's estimates. Type the numbers, or raise the daily limit in settings."
        assertThat(texts.count { it == sentence }).isEqualTo(1)
        assertThat(render.isDrawnBefore(SENDS, sentence)).isTrue()
        assertThat(texts).contains("Dismiss")
    }

    /** Before any review there is nothing to say, and nothing is. */
    @Test
    fun `no line is drawn before a review, or while one is out`() {
        listOf(null, Review.Asking()).forEach { review ->
            val texts = draw(reviewing(review))
            assertThat(texts.none { it.startsWith("Reviewed") }).isTrue()
        }
    }

    /** D54 §8.3: said as what happened, with the group that went, and never as not understood. */
    @Test
    fun `an answer whose suggestions could not be used says so, and which`() {
        val texts = draw(
            reviewing(
                Review.Shown(FoodReview(null, null, null, listOf(ReviewItem.PER_100G)), unusable = true),
            ),
        )

        assertThat(texts).contains("The model's answer arrived, but its suggestions could not be used.")
        assertThat(texts).contains("Its suggestion for per 100 g couldn't be used.")
        assertThat(texts.joinToString()).doesNotContain("could not be understood")
    }

    /** D54 §8.4: offered as a text button, even after a failure when no review is on screen. */
    @Test
    fun `the model's answer is offered when the page holds it, and not otherwise`() {
        val holding = reviewing(null).let { state ->
            state.copy(
                editing = state.editing!!.copy(
                    reviewing = FormReview(modelAnswer = """{"per_100g":null}"""),
                ),
            )
        }

        assertThat(draw(holding)).contains("Show the model's answer")
        assertThat(draw(reviewing(null))).doesNotContain("Show the model's answer")
    }

    /**
     * D54 §8.4, as amended 2026-09-25: offered after every answer, suggestions waiting in the boxes
     * included — under the outcome line, since the button that asks is not drawn while they wait —
     * and it opens the reply pretty-printed.
     */
    @Test
    fun `the model's answer is offered with suggestions waiting, under the outcome, and opens pretty-printed`() {
        val raw = """{"per_unit":{"fat":4},"note":"Fat was low."}"""
        val texts = draw(answered(FoodReview(null, fatTo4, "Fat was low.", emptyList()), raw = raw))
        val line = "Reviewed: 1 suggestion, in the boxes below — Fat was low."

        assertThat(render.fieldsSaid(SUGGESTED)).containsExactly("4")
        assertThat(texts).contains(SHOW_ANSWER)
        assertThat(render.isDrawnBefore(line, SHOW_ANSWER)).isTrue()
        assertThat(render.isDrawnBefore(SHOW_ANSWER, "Accept changes and save")).isTrue()

        render.click(SHOW_ANSWER)
        val shown = render.textsAgain()

        assertThat(shown).contains("{\n    \"per_unit\": {\n        \"fat\": 4\n    },\n    \"note\": \"Fat was low.\"\n}")
        assertThat(shown).contains("Hide the model's answer")
        assertThat(shown).contains("Copy")
    }

    /** A page with the Oat biscuit open (invented figures) and [review] as its review. */
    /**
     * D56: a food counted in ml is reviewed per 100 ml, so its suggestion is said per 100 ml, in the
     * figures its boxes show. Invented: a carton's 57 kcal per 100 ml, suggested 60.
     */
    @Test
    fun `a suggestion for a food counted in ml goes into its box per 100 ml`() {
        val kcalTo60 = Suggestion(
            nutrients = Nutrients(60.0, 2.9, 4.7, 3.6),
            confidence = Confidence.MEDIUM,
            filled = false,
            changes = listOf(FigureChange(Figure.KCAL, 57.0, 60.0, "A reason.")),
            reason = null,
        )
        val drink = oatDrink().copy(id = 1)
        val form = FoodForm.of(drink)
        val (written, pending) = FormReview().asked(form).answered(FoodReview(null, kcalTo60, null, emptyList()), null, form)
        draw(FoodPageUiState(food = drink, editing = Editing(1, written, reviewing = pending)))

        assertThat(render.fieldsSaid(SUGGESTED)).containsExactly("60")
        assertThat(render.describedCount("Calories per 100 ml back to 57")).isEqualTo(1)
    }

    @Test
    fun `a set-aside suggestion for a food counted in ml is said per 100 ml`() {
        val drink = oatDrink().copy(id = 1)
        val texts = draw(
            FoodPageUiState(
                food = drink,
                editing = Editing(
                    1,
                    FoodForm.of(drink),
                    reviewing = FormReview(
                        review = Review.Shown(FoodReview(null, null, null, listOf(ReviewItem.PER_UNIT)), unusable = true),
                    ),
                ),
            ),
        )

        assertThat(texts).contains("Its suggestion for per 100 ml couldn't be used.")
    }

    /** The page with [answer] arrived: its suggestions in the boxes, pending. */
    private fun answered(
        answer: FoodReview,
        food: Food = biscuit(),
        weight: Boolean = true,
        raw: String? = null,
    ): FoodPageUiState {
        val form = FoodForm.of(food).let { if (weight) it else it.copy(gramsPerUnit = "") }
        val (written, reviewing) = FormReview().asked(form).answered(answer, raw, form)
        return FoodPageUiState(food = food, editing = Editing(1, written, reviewing = reviewing))
    }

    /** The spec's invented Humus (D54 §12.4): a label per 100 g, no unit, no weight. */
    private fun humus() = Food(
        id = 1,
        name = "Humus",
        facts = FoodFacts(
            per100g = PerHundredGrams(Nutrients(166.0, 7.9, 14.3, 19.6), Provenance(Source.LABEL, null, 0)),
        ),
    )

    private fun biscuit() = Food(
        id = 1,
        name = "Oat biscuit",
        facts = FoodFacts(
            per100g = PerHundredGrams(Nutrients(480.0, 7.0, 62.0, 22.0), Provenance(Source.LABEL, null, 0)),
            perUnit = aPerUnit("biscuit", 90.0).copy(nutrients = Nutrients(90.0, 1.0, 12.0, 1.0)),
            gramsPerUnit = weighing(18.0),
        ),
    )

    private fun reviewing(review: Review?): FoodPageUiState {
        val food = Food(
            id = 1,
            name = "Oat biscuit",
            facts = FoodFacts(
                per100g = PerHundredGrams(Nutrients(480.0, 7.0, 62.0, 22.0), Provenance(Source.LABEL, null, 0)),
                perUnit = aPerUnit("biscuit", 90.0).copy(nutrients = Nutrients(90.0, 1.0, 12.0, 1.0)),
                gramsPerUnit = weighing(18.0),
            ),
        )
        return FoodPageUiState(
            food = food,
            editing = Editing(1, FoodForm.of(food), reviewing = FormReview(review = review)),
        )
    }

    private val fatTo4 = Suggestion(
        nutrients = Nutrients(90.0, 1.0, 12.0, 4.0),
        confidence = Confidence.MEDIUM,
        filled = false,
        changes = listOf(FigureChange(Figure.FAT, 1.0, 4.0, "A reason.")),
        reason = null,
    )

    private fun draw(state: FoodPageUiState): List<String> = render.texts {
        FoodPageScreen(
            state = state,
            onSetForm = {},
            onSave = {},
            onHide = {},
            onUnhide = {},
            onDelete = {},
            onConfirmDeleting = {},
            onCancelDeleting = {},
            onBeginJoining = {},
            onDismissRefusal = {},
            review = ReviewActions.NONE,
            onBack = {},
        )
    }

    private companion object {
        /** `R.string.review_show_answer`, as the phone draws it. */
        const val SHOW_ANSWER = "Show the model's answer"

        /** What a screen reader says of a box the review wrote into and he has not accepted (§12.6). */
        const val SUGGESTED = "suggested by the review, not accepted"

        /** `R.string.action_refused_could_not_open`, as the phone draws it. */
        const val COULD_NOT_OPEN =
            "That couldn't be opened. What went wrong is under Settings → Recent problems."

        const val SENDS =
            "Sends this food's name, brand and figures, and where each came from, to the model, " +
                "with your key. Nothing else."
    }
}
