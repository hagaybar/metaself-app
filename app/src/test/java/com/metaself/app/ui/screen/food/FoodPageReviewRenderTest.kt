package com.metaself.app.ui.screen.food

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.metaself.app.data.food.aPerUnit
import com.metaself.app.data.food.weighing
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.Figure
import com.metaself.app.domain.ai.FigureChange
import com.metaself.app.domain.ai.FoodReview
import com.metaself.app.domain.ai.Suggestion
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
 * A review on a food's page (D54 §8–§11), drawn from a state — moved with the editor from the
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
     * D54 §11: what would change is listed in one place, under the verdict — group, figure, old →
     * new, its reason small — with Apply these changes and Keep mine, and nothing under the groups'
     * headings or in any box until he applies it.
     */
    @Test
    fun `a review lists what would change under its verdict, with Apply and Keep mine`() {
        val texts = draw(reviewing(Review.Shown(FoodReview(null, fatTo4, "Fat was low.", emptyList()))))

        val verdict = "Reviewed: 1 suggestion below — Fat was low."
        val change = "Per biscuit: Fat 1 → 4"
        assertThat(texts.count { it == change }).isEqualTo(1)
        assertThat(texts).contains("A reason.")
        assertThat(render.isDrawnBefore(verdict, change)).isTrue()
        assertThat(render.isDrawnBefore(change, "A reason.")).isTrue()
        assertThat(render.isDrawnBefore("A reason.", "Apply these changes")).isTrue()
        assertThat(render.isDrawnBefore("Apply these changes", "Keep mine")).isTrue()
        assertThat(render.isDrawnBefore("Keep mine", "What 100 g of it are worth")).isTrue()
        assertThat(render.fieldTexts()).doesNotContain("4")
        // One way to take the suggestions, for the whole review — no per-group button beside it.
        assertThat(texts.filter { it.startsWith("Apply") || it.startsWith("Use ") })
            .containsExactly("Apply these changes")
        assertThat(texts.count { it == "Keep mine" }).isEqualTo(1)
        assertThat(texts).doesNotContain("Dismiss")
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
    fun `two groups are listed each on its line, and a note and a set-aside group are said`() {
        val texts = draw(
            reviewing(
                Review.Shown(
                    FoodReview(
                        per100g = null,
                        perUnit = fatTo4,
                        note = "A short note.",
                        setAside = listOf(com.metaself.app.domain.ai.ReviewItem.PER_100G),
                    ),
                ),
            ),
        )

        assertThat(texts).contains("Reviewed: 1 suggestion below — A short note.")
        assertThat(texts).contains("Its suggestion for per 100 g couldn't be used.")

        val both = draw(
            reviewing(
                Review.Shown(
                    FoodReview(
                        per100g = fatTo4.copy(
                            changes = listOf(FigureChange(Figure.KCAL, 480.0, 470.0, "Another.")),
                        ),
                        perUnit = fatTo4,
                        note = null,
                        setAside = emptyList(),
                    ),
                ),
            ),
        )

        assertThat(both).contains("Per 100 g: Calories 480 → 470")
        assertThat(both).contains("Per biscuit: Fat 1 → 4")
        assertThat(render.isDrawnBefore("Per 100 g: Calories", "Per biscuit: Fat")).isTrue()
        assertThat(both.count { it == "Apply these changes" }).isEqualTo(1)
    }

    @Test
    fun `a review that changes nothing says so`() {
        val texts = draw(
            reviewing(Review.Shown(FoodReview(null, null, null, emptyList()), nothingSuggested = true)),
        )

        assertThat(texts).contains("Reviewed: no changes suggested.")
        assertThat(texts).doesNotContain("Apply these changes")
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
            Review.Shown(FoodReview(null, fatTo4, "Per piece was off.", emptyList(), Verdict.PROBLEM_FOUND)) to
                "Reviewed: 1 suggestion below — Per piece was off.",
            Review.Shown(FoodReview(null, fatTo4, "Fat was low for the piece.", emptyList())) to
                "Reviewed: 1 suggestion below — Fat was low for the piece.",
            Review.Shown(FoodReview(fatTo4, fatTo4, null, emptyList())) to
                "Reviewed: 2 suggestions below.",
            Review.Shown(
                FoodReview(null, null, "Per 100 g looks off.", listOf(com.metaself.app.domain.ai.ReviewItem.PER_100G)),
                unusable = true,
            ) to "The model's answer arrived, but its suggestions could not be used — Per 100 g looks off.",
            Review.Shown(FoodReview(null, null, null, listOf(com.metaself.app.domain.ai.ReviewItem.PER_100G)), unusable = true) to
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
                Review.Shown(FoodReview(null, null, null, listOf(com.metaself.app.domain.ai.ReviewItem.PER_100G)), unusable = true),
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
     * D54 §11: after Apply these changes, each box the review changed says so to a screen reader,
     * and a line under the verdict counts them, with Undo. A box it kept says nothing.
     */
    @Test
    fun `after applying, the changed boxes are marked and counted under the verdict`() {
        val base = reviewing(Review.Shown(FoodReview(null, fatTo4, "Fat was low.", emptyList())))
        val (form, applied) = base.editing!!.reviewing.apply(base.editing!!.form)
        val texts = draw(base.copy(editing = base.editing!!.copy(form = form, reviewing = applied)))

        val line = "1 figure changed by the review — not saved yet. Save to keep it, or Undo."
        assertThat(texts).contains(line)
        assertThat(render.isDrawnBefore("Reviewed: changes applied — Fat was low.", line)).isTrue()
        assertThat(render.isDrawnBefore(line, "Undo")).isTrue()
        assertThat(texts).doesNotContain("Apply these changes")
        assertThat(render.fieldsSaid(CHANGED)).containsExactly("4")
    }

    /** A page with the Oat biscuit open (invented figures) and [review] as its review. */
    /**
     * D56: a food counted in ml is reviewed per 100 ml, so its suggestion is said per 100 ml, in the
     * figures its boxes show. Invented: a carton's 57 kcal per 100 ml, suggested 60.
     */
    @Test
    fun `a review of a food counted in ml is said per 100 ml`() {
        val kcalTo60 = Suggestion(
            nutrients = Nutrients(60.0, 2.9, 4.7, 3.6),
            confidence = Confidence.MEDIUM,
            filled = false,
            changes = listOf(FigureChange(Figure.KCAL, 57.0, 60.0, "A reason.")),
            reason = null,
        )
        val drink = oatDrink().copy(id = 1)
        val texts = draw(
            FoodPageUiState(
                food = drink,
                editing = Editing(
                    1,
                    FoodForm.of(drink),
                    reviewing = FormReview(review = Review.Shown(FoodReview(null, kcalTo60, null, emptyList()))),
                ),
            ),
        )

        assertThat(texts).contains("Per 100 ml: Calories 57 → 60")
        assertThat(texts.none { it.startsWith("Per ml") }).isTrue()
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
                        review = Review.Shown(FoodReview(null, null, null, listOf(com.metaself.app.domain.ai.ReviewItem.PER_UNIT)), unusable = true),
                    ),
                ),
            ),
        )

        assertThat(texts).contains("Its suggestion for per 100 ml couldn't be used.")
    }

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
        /** What a screen reader says of a box the review changed and he has not saved (D54 §11). */
        const val CHANGED = "changed by the review, not saved"

        /** `R.string.action_refused_could_not_open`, as the phone draws it. */
        const val COULD_NOT_OPEN =
            "That couldn't be opened. What went wrong is under Settings → Recent problems."

        const val SENDS =
            "Sends this food's name, brand and figures, and where each came from, to the model, " +
                "with your key. Nothing else."
    }
}
