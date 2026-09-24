package com.metaself.app.sim

import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.aFood
import com.metaself.app.ui.ComposeSession
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A screen on the shell's stack keeps its state while another is drawn over it, and loses it once it
 * is popped — as a `NavBackStackEntry` keeps and then clears its view models (public issue #6,
 * item 2). Before this only the manager kept anything, and Back found "Add something" on its first
 * tab with the search emptied.
 *
 * Every food and meal is invented. JUnit 4, because Compose's rule demands it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class BackStackKeepsScreensSessionTest {

    @get:Rule
    val compose = createComposeRule()

    private val session by lazy { ComposeSession(compose) }

    private fun aWorld(start: Where): World {
        val world = World(
            foods = FakeFoodRepository(listOf(aFood(name = "Lentils"), aFood(name = "Rice"))),
            start = start,
        )
        runBlocking { world.savedMeals.create("Lunch box") }
        return world
    }

    private fun tab(label: String) = session.actions().first { it.label == label }.switchedOn

    @Test
    fun `Add something keeps its tab and its search across a screen opened over it`() {
        val world = aWorld(Where.AddSomething)
        session.start { SimulatedApp(world) }
        session.press("My meals")
        session.type("Search what you have eaten", "lun")
        assertThat(tab("My meals")).isTrue()

        session.press("Build a meal")
        assertThat(session.screen()).doesNotContain("Search what you have eaten")
        world.goBack()
        compose.waitForIdle()

        assertThat(tab("My meals")).isTrue()
        assertThat(session.screen()).contains("lun")
        assertThat(session.screen()).contains("Lunch box")
    }

    @Test
    fun `the manager keeps what was ticked across a meal built over it`() {
        val world = aWorld(Where.Manager())
        session.start { SimulatedApp(world) }
        session.hold("Lentils")
        assertThat(session.screen()).contains("1 chosen")

        session.press("My meals")
        session.press("Build a meal")
        world.goBack()
        compose.waitForIdle()
        session.press("My foods")

        assertThat(session.screen()).contains("1 chosen")
    }

    @Test
    fun `a screen popped and opened again starts afresh`() {
        val world = aWorld(Where.Today)
        session.start { SimulatedApp(world) }
        session.press("Add something")
        session.press("My meals")
        world.goBack()
        compose.waitForIdle()

        session.press("Add something")

        assertThat(tab("My meals")).isFalse()
    }
}
