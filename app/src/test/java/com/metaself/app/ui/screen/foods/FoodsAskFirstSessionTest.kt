package com.metaself.app.ui.screen.foods

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.ai.FakeFoodReviewer
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.aFood
import com.metaself.app.data.time.Now
import com.metaself.app.domain.food.Food
import com.metaself.app.ui.ComposeSession
import com.metaself.app.ui.food.ReviewActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Deleting a whole food and joining two, tapped through against the view model they are really
 * wired to (D36, issue #16).
 *
 * A static drawing can show that a question appears; only pressing through it can show that its
 * Delete deletes and its Keep it keeps — a Delete wired to nothing passes every drawing test. So the
 * view model here is live, made the way the walk harness's manager makes it, and every callback is
 * bound to it rather than to a counter.
 *
 * **Where an answer is drawn matters as much as whether it is.** Delete sits at the foot of a long
 * editor and a duplicate may be picked far down a long list; an answer drawn at the top of the list
 * from there is off screen and the tap looks dead. [ComposeSession] cannot tell — it reads every node
 * whether scrolled into view or not — so the three visibility tests also use Compose's own finders on
 * the same rule, whose `assertIsDisplayed` clips a node by the scrolling column it sits in. Each one
 * first proves the top of the list is off screen, so a test that cannot tell on-screen from
 * off-screen fails on that guard rather than passing. The screen is pinned to a phone's size so the
 * scrolling does not depend on Robolectric's default.
 *
 * Every visibility check asserts existence first. In this Compose version `assertIsDisplayed` on a
 * node that is not there at all fails with the same "not displayed" as one scrolled out of view
 * (seen, not assumed: it is how these tests first failed against the stubs), and by the same check
 * `assertIsNotDisplayed` on a missing node would pass. Without `assertExists` a failure could not say
 * which it was, and a guard could pass on nothing.
 *
 * JUnit 4, because Compose's rule demands it: `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class FoodsAskFirstSessionTest {

    @get:Rule
    val compose = createComposeRule()

    private val session by lazy { ComposeSession(compose) }

    // --- Deleting a food --------------------------------------------------------------------------

    @Test
    fun `deleting a food asks first, naming it, and Keep it leaves it`() {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
        session.start { Foods(foods) }

        session.press("Yoghurt")
        // Something typed and not saved, so Keep it has something to lose: an editor rebuilt on
        // the answer would come back holding the stored name, and this is what would show it.
        session.type("Name", "Yog")
        val asked = session.press("Delete")

        // The question names the stored food, not the half-typed name.
        assertThat(asked).contains("Delete “Yoghurt”? This cannot be undone.")
        assertThat(asked).contains("Keep it")
        // The editor's buttons are replaced, not joined, so exactly one thing says Delete.
        assertThat(asked).doesNotContain("Join with a duplicate")
        assertThat(asked).doesNotContain("Hide")
        assertThat(asked).doesNotContain("Save")
        assertThat(foods.current.map { it.name }).containsExactly("Yoghurt")

        val kept = session.press("Keep it")

        assertThat(kept.none { it.contains("cannot be undone") }).isTrue()
        assertThat(kept).contains("Save")
        assertThat(kept).contains("Yog")
        assertThat(foods.current.map { it.name }).containsExactly("Yoghurt")
    }

    @Test
    fun `deleting a food once asked removes it`() {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
        session.start { Foods(foods) }

        session.press("Yoghurt")
        session.press("Delete")
        val after = session.press("Delete")

        assertThat(foods.current).isEmpty()
        assertThat(after).doesNotContain("Yoghurt")
    }

    /**
     * Refused before anything is asked, and said where Delete was pressed. The editor is taller than
     * the pinned screen, so pressing Delete means the top of the list is out of sight — which is
     * exactly where a refusal drawn in the screen-wide slot would have gone.
     */
    @Test
    fun `a food a saved meal uses is refused where Delete was pressed, without being asked about`() {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
            .usedBySavedMeals(1, "Vegetable salad")
        session.start { Foods(foods) }

        session.press("Yoghurt")
        compose.onNodeWithText("Delete").performScrollTo()
        // Guard: the top of the list is off screen. Without this the test could pass however the
        // refusal were placed.
        compose.onNodeWithText("Search your foods").assertExists().assertIsNotDisplayed()

        val after = session.press("Delete")

        compose.onNodeWithText("uses this", substring = true).assertExists().assertIsDisplayed()
        assertThat(after.none { it.contains("cannot be undone") }).isTrue()
        assertThat(after).doesNotContain("Keep it")
        assertThat(foods.current.map { it.name }).containsExactly("Yoghurt")
        // The sentence says "hide this instead", so Hide has to still be there to press.
        assertThat(after).contains("Hide")
    }

    // --- Joining two foods ------------------------------------------------------------------------

    /**
     * The same irreversible act asks the same question on both routes: the line drawn after picking
     * a duplicate from a food's own screen is the very line drawn after ticking the two and joining
     * them.
     */
    @Test
    fun `joining from a food's own screen asks in the words choosing mode uses`() {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
        session.start { Foods(foods) }

        // Route A: from Yoghurt's own screen.
        session.press("Yoghurt")
        session.press("Join with a duplicate")
        val fromItsScreen = session.press("יוגורט").firstOrNull { it.startsWith("Joining") }

        assertThat(fromItsScreen).isNotNull()
        assertThat(foods.current).hasSize(2)
        val notNow = session.press("Not now")
        assertThat(foods.current).hasSize(2)
        assertThat(notNow).doesNotContain("Join them")

        // Route B: the two ticked in the list.
        session.hold("Yoghurt")
        session.press("יוגורט")
        val fromChoosing = session.press("Join these two into one food")
            .firstOrNull { it.startsWith("Joining") }

        assertThat(fromChoosing).isNotNull()
        assertThat(fromItsScreen).isEqualTo(fromChoosing)
        assertThat(fromItsScreen).endsWith("This cannot be undone.")
        assertThat(fromChoosing).endsWith("This cannot be undone.")
    }

    @Test
    fun `joining from a food's own screen joins once asked`() {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
        session.start { Foods(foods) }

        session.press("Yoghurt")
        session.press("Join with a duplicate")
        session.press("יוגורט")
        session.press("Join them")

        assertThat(foods.current).hasSize(1)
        assertThat(foods.current.single().name).isEqualTo("Yoghurt")
        assertThat(foods.current.single().alsoKnownAs).contains("יוגורט")
    }

    /**
     * The duplicate is picked at the far end of a long list; the question is drawn at its top. Unless
     * the screen brings the question to him, the pick looks like it did nothing, and his next tap on
     * a row abandons a join he never saw asked about.
     */
    @Test
    fun `the join question is on screen after picking a duplicate far down the list`() {
        val foods = FakeFoodRepository(longList())
        session.start { Foods(foods) }

        session.press("Yoghurt")
        session.press("Join with a duplicate")
        compose.onNodeWithText("יוגורט").performScrollTo()
        // Guard: the top of the list is off screen, so a question left up there would fail below.
        compose.onNodeWithText("Search your foods").assertExists().assertIsNotDisplayed()

        session.press("יוגורט")

        compose.onNodeWithText("Joining", substring = true).assertExists().assertIsDisplayed()
        compose.onNodeWithText("Join them").assertExists().assertIsDisplayed()
        assertThat(foods.current).hasSize(22)
    }

    /**
     * Bringing the question into view must happen only while there is a question. Tapping a row while
     * it is up ends the join and opens that food in place; if the scrolling outlived the join, the
     * screen would jump back to the top, away from the food he had just opened.
     */
    @Test
    fun `ending a join by opening a food leaves the screen on that food`() {
        val foods = FakeFoodRepository(longList())
        session.start { Foods(foods) }

        session.press("Yoghurt")
        session.press("Join with a duplicate")
        compose.onNodeWithText("יוגורט").performScrollTo()
        session.press("יוגורט")

        compose.onNodeWithText("Food 20").performScrollTo()
        val after = session.press("Food 20")

        compose.onNodeWithText("Search your foods").assertExists().assertIsNotDisplayed()
        assertThat(after).contains("Save")
        assertThat(after).doesNotContain("Join them")
        assertThat(foods.current).hasSize(22)
    }

    /**
     * Yoghurt first and its Hebrew duplicate last, with twenty foods between them. The fake keeps the
     * order it was given and an empty search keeps the list's order, so the duplicate is at the foot.
     */
    private fun longList(): List<Food> =
        listOf(aFood(name = "Yoghurt")) +
            (1..20).map { aFood(name = "Food %02d".format(it)) } +
            listOf(aFood(name = "יוגורט"))

    /** The food list, wired to a live view model exactly as the walk harness's manager wires it. */
    @Composable
    private fun Foods(foods: FakeFoodRepository) {
        val viewModel = remember { FoodsViewModel(foods, Now { 1_000 }, ProblemLog.NONE, FakeFoodReviewer()) }
        val state by viewModel.state.collectAsState()

        FoodsScreen(
            state = state,
            onSearch = viewModel::search,
            onShowOnlyPortions = viewModel::showOnlyPortions,
            onShowHidden = viewModel::showHidden,
            onEdit = viewModel::edit,
            onSetForm = viewModel::setForm,
            onSave = viewModel::save,
            onCancelEditing = viewModel::cancelEditing,
            onHide = viewModel::hide,
            onUnhide = viewModel::unhide,
            onDelete = viewModel::askToDelete,
            onConfirmDeleting = viewModel::confirmDeleting,
            onCancelDeleting = viewModel::cancelDeleting,
            onBeginMerging = viewModel::beginMerging,
            onMergeInto = viewModel::mergeInto,
            onConfirmMerging = viewModel::confirmJoining,
            onCancelMerging = viewModel::cancelMerging,
            onDismissRefusal = viewModel::dismissRefusal,
            review = ReviewActions(
                onReview = viewModel::review,
                onAccept = viewModel::acceptGroup,
                onAcceptAll = viewModel::acceptAll,
                onDismiss = viewModel::dismissReview,
            ),
            onBeginChoosing = viewModel::beginChoosing,
            onToggleChosen = viewModel::toggleChosen,
            onClearChoosing = viewModel::clearChoosing,
            onMakeMeal = {},
            onJoinChosen = viewModel::joinChosen,
            onBack = {},
        )
    }
}
