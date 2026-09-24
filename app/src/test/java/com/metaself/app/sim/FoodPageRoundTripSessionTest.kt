package com.metaself.app.sim

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.aFood
import com.metaself.app.ui.ComposeSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The list and a food's page, walked through the way the nav host wires them (D55 §1, §5, §6): a
 * row opens the page, Back finds the list as it was, and a page's Hide and Join hand the list what
 * it has to know.
 *
 * Through [SimulatedApp], because the nav host itself cannot be driven (its KDoc says why). What
 * this proves is the wiring the two share — [com.metaself.app.ui.nav.FoodPageExit] and
 * [com.metaself.app.ui.nav.TakeFromFoodPage] — and the view models on either side; what it cannot
 * prove is the nav host's own calls to the back stack, which are checked on the phone.
 *
 * Every food here is invented. JUnit 4, because Compose's rule demands it: `org.junit.Test`, never
 * `org.junit.jupiter.api.Test`. Pinned to a phone's size so the scroll test does not depend on
 * Robolectric's default.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class FoodPageRoundTripSessionTest {

    @get:Rule
    val compose = createComposeRule()

    private val session by lazy { ComposeSession(compose) }

    @Test
    fun `a food opens its page, and Back finds the list searched as it was`() {
        val world = World(
            foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "Tahini"))),
            start = Where.Manager(),
        )
        session.start { SimulatedApp(world) }
        session.type("Search your foods", "yog")

        val page = session.press("Yoghurt")
        assertThat(page).containsAtLeast("Save", "Leave it alone", "Where it's used")
        assertThat(page).doesNotContain("Search your foods")

        world.goBack()
        val list = session.screen()

        assertThat(list).contains("yog")
        assertThat(list).contains("Yoghurt")
        assertThat(list).doesNotContain("Tahini")
        assertThat(list).doesNotContain("Save")
    }

    @Test
    fun `Back from a page finds the list scrolled where it was`() {
        val world = World(foods = FakeFoodRepository(longList()), start = Where.Manager())
        session.start { SimulatedApp(world) }
        compose.onNodeWithText("Food 20").performScrollTo()
        // Guard: the top of the list is off screen, so a list that came back at the top fails below.
        compose.onNodeWithText("Search your foods").assertExists().assertIsNotDisplayed()

        session.press("Food 20")
        world.goBack()
        compose.waitForIdle()

        compose.onNodeWithText("Search your foods").assertExists().assertIsNotDisplayed()
        compose.onNodeWithText("Food 20").assertExists().assertIsDisplayed()
    }

    /** §6: the list says the food is hidden, with Show again, and Show again brings it back. */
    @Test
    fun `hiding on a page says so on the list, and Show again brings it back`() {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "Tahini")))
        val world = World(foods = foods, start = Where.Manager())
        session.start { SimulatedApp(world) }

        session.press("Yoghurt")
        val list = session.press("Hide")

        val line = "“Yoghurt” is hidden — it is no longer offered when you log something."
        assertThat(list).contains(line)
        assertThat(list).contains("Search your foods")
        assertThat(foods.current.first { it.name == "Yoghurt" }.hidden).isTrue()

        val after = session.press("Show again")

        assertThat(after).doesNotContain(line)
        assertThat(foods.current.first { it.name == "Yoghurt" }.hidden).isFalse()
        assertThat(after).contains("Yoghurt")
    }

    /**
     * §5: the page closes and the list beneath picks, with the search he left it with, then asks the
     * one join question, and only its answer joins.
     */
    @Test
    fun `joining from a page reached from the list picks on that list, search kept`() {
        val foods = FakeFoodRepository(
            listOf(aFood(name = "Yoghurt"), aFood(name = "Tahini"), aFood(name = "Yoghurt, Greek")),
        )
        val world = World(foods = foods, start = Where.Manager())
        session.start { SimulatedApp(world) }
        session.type("Search your foods", "yog")
        session.press("Yoghurt")

        val picking = session.press("Join with a duplicate")

        assertThat(picking.any { it.startsWith("Pick the food that is the same thing as “Yoghurt”") })
            .isTrue()
        assertThat(picking).contains("yog")
        assertThat(picking).doesNotContain("Tahini")
        assertThat(picking).doesNotContain("Save")

        val asked = session.press("Yoghurt, Greek")
        assertThat(asked.any { it.startsWith("Joining “Yoghurt, Greek” into “Yoghurt”") }).isTrue()
        assertThat(foods.current).hasSize(3)

        session.press("Join them")
        assertThat(foods.current.map { it.name }).containsExactly("Yoghurt", "Tahini")
    }

    /**
     * From *Give this a portion* no list is beneath the page: Join replaces the page with the list,
     * picking. The page is the walk's first screen here, standing in for that arrival — what it
     * checks is that nothing beneath means the list is opened, not popped to.
     */
    @Test
    fun `joining from a page with no list beneath opens the list picking`() {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt"), aFood(name = "Tahini")))
        val world = World(foods = foods, start = Where.FoodPage(foodId = 1))
        session.start { SimulatedApp(world) }

        val picking = session.press("Join with a duplicate")

        assertThat(picking.any { it.startsWith("Pick the food that is the same thing as “Yoghurt”") })
            .isTrue()
        assertThat(picking).contains("Search your foods")
        assertThat(foods.current).hasSize(2)
    }

    /** Save closes the page onto the list, which says nothing: the row shows the new name. */
    @Test
    fun `saving on a page goes back to the list, showing what was saved`() {
        val foods = FakeFoodRepository(listOf(aFood(name = "Yoghurt")))
        val world = World(foods = foods, start = Where.Manager())
        session.start { SimulatedApp(world) }
        session.press("Yoghurt")
        session.type("Name", "Greek yoghurt")

        val list = session.press("Save")

        assertThat(list).contains("Greek yoghurt")
        assertThat(list).contains("Search your foods")
        assertThat(list.none { it.contains("is hidden —") }).isTrue()
    }

    /**
     * Yoghurt first, then twenty foods: stamped newest first in that order, because the list is
     * ordered by the last edit as the database orders it, and an empty search keeps that order.
     */
    private fun longList() =
        listOf(aFood(name = "Yoghurt", updatedAtMillis = 100)) +
            (1..20).map { aFood(name = "Food %02d".format(it), updatedAtMillis = 100L - it) }
}
