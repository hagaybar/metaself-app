package com.metaself.app.ui.screen.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.ai.FakeFoodReviewer
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.FakeFoodRepository
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
 * Apply these changes, Undo and Save on a food's own page (D55), pressed through against the view
 * model they are really wired to (D54 §11): a box the review changed is marked for a screen reader after Apply, and the mark
 * goes when he types in it, undoes, or saves. Every figure is invented.
 *
 * A mark is a colour on screen, which a render here cannot see; what it can see is the state
 * description the same box carries, which is also what a screen reader hears.
 *
 * JUnit 4, because Compose's rule demands it: `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class FoodPageReviewAppliedSessionTest {

    @get:Rule
    val compose = createComposeRule()

    private val session by lazy { ComposeSession(compose) }

    @Test
    fun `applying marks the changed boxes, and typing in one takes its mark off`() {
        session.start { Page(FakeFoodRepository(listOf(oatBiscuit()))) }
        val shown = session.press("Review the figures")
        assertThat(shown).contains("Per 100 g: Calories 480 → 470")
        assertThat(shown).contains("Per biscuit: Fat 1 → 4")
        assertThat(marked()).isEqualTo(0)

        val applied = session.press("Apply these changes")

        assertThat(applied).contains("2 figures changed by the review — not saved yet. Save to keep them, or Undo.")
        assertThat(marked()).isEqualTo(2)

        val typed = session.type("4", "5")

        assertThat(marked()).isEqualTo(1)
        assertThat(typed).contains("1 figure changed by the review — not saved yet. Save to keep it, or Undo.")
    }

    @Test
    fun `Undo puts the figures back, takes every mark off, and offers the changes again`() {
        session.start { Page(FakeFoodRepository(listOf(oatBiscuit()))) }
        session.press("Review the figures")
        session.press("Apply these changes")
        assertThat(marked()).isEqualTo(2)

        val undone = session.press("Undo")

        assertThat(marked()).isEqualTo(0)
        assertThat(undone).contains("480")
        assertThat(undone).doesNotContain("470")
        assertThat(undone).contains("Apply these changes")
        assertThat(undone.none { it.contains("changed by the review") }).isTrue()
    }

    @Test
    fun `Save keeps the applied figures, and the page closes`() {
        val foods = FakeFoodRepository(listOf(oatBiscuit()))
        session.start { Page(foods) }
        session.press("Review the figures")
        session.press("Apply these changes")
        assertThat(marked()).isEqualTo(2)

        val after = session.press("Save")

        assertThat(after).doesNotContain("Save")
        assertThat(marked()).isEqualTo(0)
        val saved = foods.current.single().facts
        assertThat(saved.per100g!!.nutrients.kcal).isEqualTo(470.0)
        assertThat(saved.perUnit!!.nutrients.fatG).isEqualTo(4.0)
        assertThat(saved.per100g!!.provenance.source).isEqualTo(Source.AI_ESTIMATE)
    }

    /** How many boxes on screen a screen reader hears as changed by the review and not saved. */
    private fun marked(): Int =
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, CHANGED))
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
            ),
            perUnit = Suggestion(
                nutrients = Nutrients(90.0, 1.0, 12.0, 4.0),
                confidence = Confidence.MEDIUM,
                filled = false,
                changes = listOf(FigureChange(Figure.FAT, 1.0, 4.0, "The biscuit's share of the fat.")),
                reason = null,
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
                SavedStateHandle(mapOf(FoodPageViewModel.FOOD_ID to 1L)),
            )
        }
        LiveFoodPage(viewModel)
    }

    private companion object {
        const val CHANGED = "changed by the review, not saved"
    }
}
