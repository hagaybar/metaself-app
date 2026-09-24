package com.metaself.app.sim

import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.metaself.app.ui.ComposeSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * A committed walk, frozen as a check: from a cold start it types three foods onto the day, builds a
 * meal of them in the manager and logs that meal onto the day — and not one step may fail to happen
 * (public issue #6, "a committed regression walk").
 *
 * The walk is `src/test/resources/walks/cold-start-meal.txt`, in the agent's own language, replayed
 * by the same code that replays an agent's walk. A path breaking shows here as a step that did not
 * happen, with the transcript of the whole walk in the failure; it is also written to
 * `build/simulation/regression-transcript.md`.
 *
 * JUnit 4, because Compose's rule demands it: `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class RegressionWalkTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `from a cold start a meal is built and logged, with nothing stuck`() {
        val world = World(start = Where.Today)
        val session = ComposeSession(compose)
        val transcript = Transcript()
        session.start { SimulatedApp(world) }

        SimulationScript.parse(script()).forEach { step -> transcript.carryOut(step, session, world) }

        val rendered = transcript.render("The committed cold-start walk")
        File("build/simulation").mkdirs()
        File("build/simulation/regression-transcript.md").writeText(rendered)

        assertWithMessage(rendered).that(transcript.notAsAskedCount).isEqualTo(0)

        // What was stored, read from storage rather than off a screen.
        val built = world.savedMeals.current.single()
        assertThat(built.name).isEqualTo("Carrot plate")
        assertThat(built.components.map { it.food.name }).containsExactly("Carrot", "Hummus", "Pitta").inOrder()

        val logged = world.dayMeals.current.single { it.savedMealId == built.id }
        assertThat(logged.items.map { it.name }).containsExactly("Carrot", "Hummus", "Pitta")
        assertThat(logged.items.sumOf { it.kcal }).isEqualTo(40 + 160 + 250)

        // Every logging falls on the day it was logged onto — the walk's day and the stamps are one
        // clock. On a fixed walk clock they were not, and the day called every logging untimed.
        world.dayMeals.current.forEach { meal ->
            val on = Instant.ofEpochMilli(meal.loggedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            assertThat(on.toEpochDay()).isEqualTo(meal.epochDay)
        }
    }

    private fun script(): String =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("walks/cold-start-meal.txt")) {
            "the committed walk is missing"
        }.bufferedReader().readText()
}
