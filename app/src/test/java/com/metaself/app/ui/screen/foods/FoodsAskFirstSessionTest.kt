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
import androidx.lifecycle.SavedStateHandle
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.aFood
import com.metaself.app.domain.food.Food
import com.metaself.app.ui.ComposeSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Joining two foods, tapped through against the view model it is really wired to (D36, issue #16).
 * Deleting a food asks first on its page now (D55 §6), and is pressed through in
 * `FoodPageAskFirstSessionTest`.
 *
 * A static drawing can show that a question appears; only pressing through it can show that its
 * Join them joins and its Not now does not — a button wired to nothing passes every drawing test. So the
 * view model here is live, made the way the walk harness's manager makes it, and every callback is
 * bound to it rather than to a counter.
 *
 * **Where an answer is drawn matters as much as whether it is.** A duplicate may be picked far down
 * a long list; an answer drawn at the top of the list from there is off screen and the tap looks
 * dead. [ComposeSession] cannot tell — it reads every node
 * whether scrolled into view or not — so the visibility tests also use Compose's own finders on
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

    // --- Joining two foods ------------------------------------------------------------------------

    /**
     * The same irreversible act asks the same question on both routes: the line drawn after picking
     * a duplicate from a food's page is the very line drawn after ticking the two and joining
     * them.
     */
    @Test
    fun `joining from a food's page asks in the words choosing mode uses`() {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))

        // Route A: Join with a duplicate on Yoghurt's page, which hands the list the food (D55 §5).
        session.start { Foods(foods, joiningFrom = 1) }
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
    fun `joining from a food's page joins once asked`() {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "יוגורט")))
        session.start { Foods(foods, joiningFrom = 1) }

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
        session.start { Foods(foods, joiningFrom = 1) }

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
     * it is up ends the join and opens that food's page; if the scrolling outlived the join, the
     * list would jump back to the top, away from the food he had just opened — and Back from the
     * page would find it there.
     */
    @Test
    fun `ending a join by opening a food leaves the list on that food`() {
        val foods = FakeFoodRepository(longList())
        session.start { Foods(foods, joiningFrom = 1) }

        compose.onNodeWithText("יוגורט").performScrollTo()
        session.press("יוגורט")

        compose.onNodeWithText("Food 20").performScrollTo()
        val after = session.press("Food 20")

        compose.onNodeWithText("Search your foods").assertExists().assertIsNotDisplayed()
        assertThat(opened).containsExactly(foods.current.first { it.name == "Food 20" }.id)
        assertThat(after).doesNotContain("Join them")
        assertThat(foods.current).hasSize(22)
    }

    /**
     * Yoghurt first and its Hebrew duplicate last, with twenty foods between them. The fake keeps the
     * order it was given and an empty search keeps the list's order, so the duplicate is at the foot.
     */
    /**
     * Stamped newest first in the order written, because the list is ordered by the last edit as the
     * database orders it: Yoghurt at the top, the Hebrew name at the far end.
     */
    private fun longList(): List<Food> =
        listOf(aFood(name = "Yoghurt", updatedAtMillis = 100)) +
            (1..20).map { aFood(name = "Food %02d".format(it), updatedAtMillis = 100L - it) } +
            listOf(aFood(name = "יוגורט", updatedAtMillis = 0))

    /** Every food whose page was opened from the list, in order. */
    private val opened = mutableListOf<Long>()

    /**
     * The food list, wired to a live view model exactly as the walk harness's manager wires it —
     * opened by route to join from [joiningFrom] when there is one, as a page's Join opens it.
     */
    @Composable
    private fun Foods(foods: FakeFoodRepository, joiningFrom: Long? = null) {
        val viewModel = remember {
            FoodsViewModel(
                foods,
                ProblemLog.NONE,
                SavedStateHandle(
                    joiningFrom?.let { mapOf(FoodsViewModel.JOIN_FROM to it.toString()) } ?: emptyMap(),
                ),
            )
        }
        val state by viewModel.state.collectAsState()

        FoodsScreen(
            state = state,
            onSearch = viewModel::search,
            onShowOnlyPortions = viewModel::showOnlyPortions,
            onShowHidden = viewModel::showHidden,
            onOpen = { foodId ->
                viewModel.openingAFood()
                opened += foodId
            },
            onMergeInto = viewModel::mergeInto,
            onConfirmMerging = viewModel::confirmJoining,
            onCancelMerging = viewModel::cancelMerging,
            onDismissRefusal = viewModel::dismissRefusal,
            onShowAgain = viewModel::showAgain,
            onDismissHidden = viewModel::dismissHidden,
            onBeginChoosing = viewModel::beginChoosing,
            onToggleChosen = viewModel::toggleChosen,
            onClearChoosing = viewModel::clearChoosing,
            onMakeMeal = {},
            onJoinChosen = viewModel::joinChosen,
            onBack = {},
        )
    }
}
