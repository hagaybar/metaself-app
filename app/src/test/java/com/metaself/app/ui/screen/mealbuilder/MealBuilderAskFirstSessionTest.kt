package com.metaself.app.ui.screen.mealbuilder

import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.food.aFood
import com.metaself.app.data.food.aPer100g
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.domain.food.SavedMeal
import com.metaself.app.ui.ComposeSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Deleting a whole saved meal from its builder asks first (D36, issue #16), tapped through.
 *
 * `onDelete` is the nav host's delete-and-go-back, so it is the one callback that must be reached
 * only from the question's Delete: reaching it any earlier deletes before asking, and reaching it
 * from Keep it leaves a screen he asked to stay on. Both are counted, with the back arrow beside
 * them. The question's state is the screen's own, so a fixed state is enough to drive it.
 *
 * JUnit 4, because Compose's rule demands it: `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class MealBuilderAskFirstSessionTest {

    @get:Rule
    val compose = createComposeRule()

    private val session by lazy { ComposeSession(compose) }

    private var deleted = 0
    private var wentBack = 0

    private val cucumber =
        aFood("Cucumber", FoodFacts(per100g = aPer100g(16.0))).copy(id = 1)

    private fun salad() = SavedMeal(
        id = 1,
        name = "Vegetable salad",
        components = listOf(
            MealComponent(10, cucumber, 100.0, CountedAs.GRAMS, position = 0),
        ),
    )

    @Test
    fun `deleting a meal asks first, naming it, and Keep it neither deletes nor leaves`() {
        start()

        val asked = session.press("Delete this meal")

        assertThat(asked).contains("Delete “Vegetable salad”? This cannot be undone.")
        // The button it came from is replaced. A leftover one would not collide with pressing
        // "Delete" — an exact match wins — so this is the only thing that catches it.
        assertThat(asked).doesNotContain("Delete this meal")
        assertThat(deleted).isEqualTo(0)

        val kept = session.press("Keep it")

        assertThat(kept.none { it.contains("cannot be undone") }).isTrue()
        assertThat(kept).contains("Delete this meal")
        assertThat(deleted).isEqualTo(0)
        assertThat(wentBack).isEqualTo(0)
    }

    @Test
    fun `deleting a meal once asked deletes it, once`() {
        start()

        session.press("Delete this meal")
        session.press("Delete")

        assertThat(deleted).isEqualTo(1)
        assertThat(wentBack).isEqualTo(0)
    }

    /**
     * The back arrow stays live while the question is up, because the question is drawn in place,
     * not in a pop-up. Pressing it leaves, once, and leaving is not an answer: nothing is deleted.
     */
    @Test
    fun `the back arrow with the question up goes back once and deletes nothing`() {
        start()

        session.press("Delete this meal")
        session.press("Back")

        assertThat(wentBack).isEqualTo(1)
        assertThat(deleted).isEqualTo(0)
    }

    private fun start() {
        session.start {
            MealBuilderScreen(
                state = MealBuilderUiState(meal = salad()),
                onSetName = {},
                onName = {},
                onRename = {},
                onSearch = {},
                onBeginAdding = {},
                onCountAs = {},
                onSetAmount = {},
                onConfirmAdding = {},
                onCancelAdding = {},
                onSetPendingAmount = { _, _ -> },
                onCountPendingAs = { _, _ -> },
                onConfirmPending = {},
                onDropPending = {},
                onRemove = {},
                onMove = { _, _ -> },
                onBeginCreatingFood = {},
                onCreateFood = {},
                onCancelCreatingFood = {},
                onDelete = { deleted++ },
                onDismissRefusal = {},
                onBack = { wentBack++ },
            )
        }
    }
}
