package com.metaself.app.ui.screen.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.ai.FakeFoodReviewer
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.aFood
import com.metaself.app.data.time.Now
import com.metaself.app.ui.ComposeSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Deleting a whole food from its own page, tapped through against the view model it is really wired
 * to (D36, as D55 §6 draws it): the question takes the buttons' place, its Delete deletes, its Keep
 * it keeps, and a refusal is said where Delete was pressed.
 *
 * A static drawing can show that a question appears; only pressing through it can show that its
 * Delete deletes — a Delete wired to nothing passes every drawing test.
 *
 * [ComposeSession] reads every node whether scrolled into view or not, so the refusal's visibility is
 * checked with Compose's own finder on the same rule, whose `assertIsDisplayed` clips by the
 * scrolling column. Existence is asserted first, because on a node that is not there at all
 * `assertIsDisplayed` fails with the same words as on one scrolled out of view.
 *
 * JUnit 4, because Compose's rule demands it: `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class FoodPageAskFirstSessionTest {

    @get:Rule
    val compose = createComposeRule()

    private val session by lazy { ComposeSession(compose) }

    @Test
    fun `deleting a food asks first, naming it, and Keep it leaves it`() {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
        session.start { Page(foods) }

        // Something typed and not saved, so Keep it has something to lose: a form rebuilt on the
        // answer would come back holding the stored name, and this is what would show it.
        session.type("Name", "Yog")
        val asked = session.press("Delete")

        // The question names the stored food, not the half-typed name.
        assertThat(asked).contains("Delete “Yoghurt”? This cannot be undone.")
        assertThat(asked).contains("Keep it")
        // The buttons are replaced, not joined, so exactly one thing says Delete.
        assertThat(asked).doesNotContain("Join with a duplicate")
        assertThat(asked).doesNotContain("Hide")
        assertThat(foods.current.map { it.name }).containsExactly("Yoghurt")

        val kept = session.press("Keep it")

        assertThat(kept.none { it.contains("cannot be undone") }).isTrue()
        assertThat(kept).contains("Save")
        assertThat(kept).contains("Yog")
        assertThat(foods.current.map { it.name }).containsExactly("Yoghurt")
    }

    @Test
    fun `deleting a food once asked removes it and leaves the page`() {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
        session.start { Page(foods) }

        session.press("Delete")
        val after = session.press("Delete")

        assertThat(foods.current).isEmpty()
        assertThat(after).doesNotContain("Save")
    }

    /**
     * Refused before anything is asked, and said where Delete was pressed: at the foot of the page,
     * which is taller than the pinned screen.
     */
    @Test
    fun `a food a saved meal uses is refused where Delete was pressed, without being asked about`() {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
            .usedBySavedMeals(1, "Vegetable salad")
        session.start { Page(foods) }

        compose.onNodeWithText("Delete").performScrollTo()
        val after = session.press("Delete")

        compose.onNodeWithText("uses this", substring = true).assertExists().assertIsDisplayed()
        assertThat(after.none { it.contains("cannot be undone") }).isTrue()
        assertThat(after).doesNotContain("Keep it")
        assertThat(foods.current.map { it.name }).containsExactly("Yoghurt")
        // The sentence says "hide this instead", so Hide has to still be there to press.
        assertThat(after).contains("Hide")
    }

    /** Food 1's page on a live view model. */
    @Composable
    private fun Page(foods: FakeFoodRepository) {
        val viewModel = remember {
            FoodPageViewModel(
                foods,
                Now { 1_000 },
                ProblemLog.NONE,
                FakeFoodReviewer(),
                SavedStateHandle(mapOf(FoodPageViewModel.FOOD_ID to 1L)),
            )
        }
        LiveFoodPage(viewModel)
    }
}
