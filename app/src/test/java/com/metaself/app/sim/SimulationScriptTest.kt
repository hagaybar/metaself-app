package com.metaself.app.sim

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Pure parsing, so JUnit 5 like everything else that needs no framework. */
class SimulationScriptTest {

    @Test
    fun `reads the four things a person can do to a screen`() {
        val steps = SimulationScript.parse(
            """
            press Build a meal
            hold Cucumber
            type Name this meal | Greek salad
            look
            back
            """.trimIndent(),
        )

        assertThat(steps).containsExactly(
            Step.Press("press Build a meal", "Build a meal"),
            Step.Hold("hold Cucumber", "Cucumber"),
            Step.Type("type Name this meal | Greek salad", "Name this meal", "Greek salad"),
            Step.Look("look"),
            Step.Back("back"),
        ).inOrder()
    }

    @Test
    fun `keeps the agent's notes out of the walk`() {
        val steps = SimulationScript.parse(
            """
            # trying the second tab, since nothing on the first one says "new"
            press My meals

            """.trimIndent(),
        )

        assertThat(steps).containsExactly(Step.Press("press My meals", "My meals"))
    }

    @Test
    fun `a name with spaces in it survives, because every food has one`() {
        val steps = SimulationScript.parse("type Search your foods | cottage cheese 5%")

        assertThat(steps).containsExactly(
            Step.Type("type Search your foods | cottage cheese 5%", "Search your foods", "cottage cheese 5%"),
        )
    }

    @Test
    fun `a step the parser cannot read is kept, not dropped`() {
        val steps = SimulationScript.parse(
            """
            swipe left
            press
            type nothing to separate on
            """.trimIndent(),
        )

        // Dropping these would show a walk that never happened: the agent believed it acted.
        assertThat(steps).hasSize(3)
        assertThat(steps.map { it::class }).containsExactly(
            Step.Unreadable::class, Step.Unreadable::class, Step.Unreadable::class,
        )
        assertThat((steps[0] as Step.Unreadable).why).contains("swipe")
        assertThat((steps[1] as Step.Unreadable).why).contains("needs something")
        assertThat((steps[2] as Step.Unreadable).why).contains("separated")
    }

    @Test
    fun `an empty field is still a field, since a blank text box is a real thing to meet`() {
        val steps = SimulationScript.parse("type | 180")

        assertThat(steps).containsExactly(Step.Type("type | 180", "", "180"))
    }
}
