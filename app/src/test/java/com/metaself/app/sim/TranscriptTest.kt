package com.metaself.app.sim

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.ComposeSession
import org.junit.jupiter.api.Test

/**
 * The transcript says it cannot see layout, and says which window each line is in (public issue #6,
 * item 5). Pure, so JUnit 5.
 */
class TranscriptTest {

    @Test
    fun `it says at the top that it records no positions or sizes`() {
        val rendered = Transcript().render("A walk")

        assertThat(rendered).contains(Transcript.NO_GEOMETRY)
        assertThat(rendered).contains("Do not conclude that something is off screen")
    }

    @Test
    fun `with a menu open, it says which lines are the menu's and that the screen is covered`() {
        val transcript = Transcript()
        transcript.record(
            step = Step.Press("press More", "More"),
            outcome = Transcript.Outcome.Did,
            screen = listOf("Today", "Add something", "Weight", "Settings"),
            actions = listOf("\"Weight\" (press)", "\"Settings\" (press)"),
            layers = listOf(
                ComposeSession.Layer("the screen", listOf("Today", "Add something"), reachable = false),
                ComposeSession.Layer("a menu", listOf("Weight", "Settings"), reachable = true),
            ),
        )

        val rendered = transcript.render("A walk")

        assertThat(rendered).contains("On the screen — covered: a finger cannot reach this:\n\n> Today\n> Add something")
        assertThat(rendered).contains("Open over it, a menu:\n\n> Weight\n> Settings")
    }

    @Test
    fun `a tap that landed on an open window is not called done, nor stuck`() {
        val transcript = Transcript()
        transcript.record(
            step = Step.Press("press Add something", "Add something"),
            outcome = Transcript.Outcome.LandedOutside("beneath a menu"),
            screen = listOf("Today"),
            actions = emptyList(),
        )

        val rendered = transcript.render("A walk")

        assertThat(rendered).contains("**LANDED ON WHAT WAS OPEN OVER IT**: beneath a menu")
        assertThat(transcript.stuckCount).isEqualTo(0)
        assertThat(transcript.notAsAskedCount).isEqualTo(1)
    }
}
