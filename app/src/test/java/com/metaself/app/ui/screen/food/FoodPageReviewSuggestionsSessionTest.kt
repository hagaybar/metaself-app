package com.metaself.app.ui.screen.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.ai.FakeFoodReviewer
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.FakeSavedMealRepository
import com.metaself.app.data.food.aFood
import com.metaself.app.data.time.Now
import com.metaself.app.domain.ai.Figure
import com.metaself.app.domain.ai.FigureChange
import com.metaself.app.domain.ai.FoodReview
import com.metaself.app.domain.ai.ReviewResult
import com.metaself.app.domain.ai.Suggestion
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.GramsPerUnit
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.PerUnit
import com.metaself.app.domain.food.Provenance
import com.metaself.app.ui.ComposeSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A review's suggestions in the boxes of a food's page, pressed through against the view model they
 * are really wired to (D54 §12.6): each suggested box is marked for a screen reader, **Back** puts a
 * box back, **Accept changes and save** saves and closes, **Cancel** puts the page back. Every food
 * and figure is invented.
 *
 * A suggestion's colour is on screen, which a render here cannot see; what it can see is the state
 * description the same box carries, which is also what a screen reader hears.
 *
 * JUnit 4, because Compose's rule demands it: `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class FoodPageReviewSuggestionsSessionTest {

    @get:Rule
    val compose = createComposeRule()

    private val session by lazy { ComposeSession(compose) }

    @Test
    fun `suggestions arrive in the boxes, and Save gives way to Accept changes and save`() {
        session.start { Page(FakeFoodRepository(listOf(oatBiscuit()))) }
        assertThat(session.screen()).contains("Save")

        val shown = session.press("Review the figures")

        assertThat(shown).contains("Reviewed: 2 suggestions, in the boxes below.")
        assertThat(shown).contains("470")
        assertThat(shown).contains("The macros give about 470.")
        assertThat(shown).contains("Back to 480")
        assertThat(shown).contains("Back to 1")
        assertThat(shown).contains("Accept changes and save")
        assertThat(shown).contains("Cancel")
        assertThat(shown).doesNotContain("Save")
        assertThat(shown).doesNotContain("Leave it alone")
        assertThat(shown).doesNotContain("Review the figures")
        assertThat(suggested()).isEqualTo(2)
    }

    @Test
    fun `Back puts one box back, and the last one gives Save back`() {
        session.start { Page(FakeFoodRepository(listOf(oatBiscuit()))) }
        session.press("Review the figures")

        val one = session.press("Back to 480")
        assertThat(suggested()).isEqualTo(1)
        assertThat(one).contains("480")
        assertThat(one).contains("Accept changes and save")

        val none = session.press("Back to 1")
        assertThat(suggested()).isEqualTo(0)
        assertThat(none).contains("Save")
        assertThat(none).contains("Reviewed: no suggestions left.")
    }

    @Test
    fun `typing in a suggested box makes it his`() {
        session.start { Page(FakeFoodRepository(listOf(oatBiscuit()))) }
        session.press("Review the figures")

        session.type("4", "5")

        assertThat(suggested()).isEqualTo(1)
    }

    @Test
    fun `Accept changes and save stores the suggestions as estimates, and the page closes`() {
        val foods = FakeFoodRepository(listOf(oatBiscuit()))
        session.start { Page(foods) }
        session.press("Review the figures")

        val after = session.press("Accept changes and save")

        assertThat(after).doesNotContain("Accept changes and save")
        val saved = foods.current.single().facts
        assertThat(saved.per100g!!.nutrients.kcal).isEqualTo(470.0)
        assertThat(saved.per100g!!.provenance.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(saved.perUnit!!.nutrients.fatG).isEqualTo(4.0)
        assertThat(saved.gramsPerUnit!!.provenance.source).isEqualTo(Source.TYPED)
    }

    @Test
    fun `Cancel puts the page back as it was when Review was pressed, and saves nothing`() {
        val foods = FakeFoodRepository(listOf(oatBiscuit()))
        session.start { Page(foods) }
        session.type("Grams", "20")
        session.press("Review the figures")

        val cancelled = session.press("Cancel")

        assertThat(suggested()).isEqualTo(0)
        assertThat(cancelled).contains("480")
        assertThat(cancelled).doesNotContain("470")
        assertThat(cancelled).contains("20")
        assertThat(cancelled).contains("Save")
        assertThat(foods.current.single().facts).isEqualTo(oatBiscuit().facts)
    }

    /** How many boxes on screen a screen reader hears as suggested by the review and not accepted. */
    private fun suggested(): Int =
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, SUGGESTED))
            .fetchSemanticsNodes(atLeastOneRootRequired = false).size

    private fun oatBiscuit() = aFood(
        name = "Oat biscuit",
        facts = FoodFacts(
            per100g = PerHundredGrams(Nutrients(480.0, 7.0, 62.0, 22.0), Provenance(Source.LABEL, null, 0)),
            perUnit = PerUnit("biscuit", Nutrients(90.0, 1.0, 12.0, 1.0), Provenance(Source.TYPED, null, 0)),
            gramsPerUnit = GramsPerUnit(18.0, Provenance(Source.TYPED, null, 0)),
        ),
    )

    /** Per 100 g's calories 480 → 470 and per biscuit's fat 1 → 4, each with a reason. */
    private fun bothChanged() = ReviewResult.Proposed(
        FoodReview(
            per100g = Suggestion(
                nutrients = Nutrients(470.0, 7.0, 62.0, 22.0),
                confidence = Confidence.LOW,
                filled = false,
                changes = listOf(FigureChange(Figure.KCAL, 480.0, 470.0, "The macros give about 470.")),
                reason = null,
                heldSource = Source.LABEL,
            ),
            perUnit = Suggestion(
                nutrients = Nutrients(90.0, 1.0, 12.0, 4.0),
                confidence = Confidence.MEDIUM,
                filled = false,
                changes = listOf(FigureChange(Figure.FAT, 1.0, 4.0, "The biscuit's share of the fat.")),
                reason = null,
                heldSource = Source.TYPED,
            ),
            note = null,
            setAside = emptyList(),
        ),
    )

    /** One food's page, food 1, on a live view model whose review proposes [bothChanged]. */
    @Composable
    private fun Page(foods: FakeFoodRepository) {
        val viewModel = remember {
            FoodPageViewModel(
                foods,
                Now { 1_000 },
                ProblemLog.NONE,
                FakeFoodReviewer(bothChanged()),
                FakeSavedMealRepository(),
                SavedStateHandle(mapOf(FoodPageViewModel.FOOD_ID to 1L)),
            )
        }
        LiveFoodPage(viewModel)
    }

    private companion object {
        const val SUGGESTED = "suggested by the review, not accepted"
    }
}
