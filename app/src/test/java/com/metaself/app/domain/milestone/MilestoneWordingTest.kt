package com.metaself.app.domain.milestone

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.goal.MilestoneWording
import org.junit.jupiter.api.Test

class MilestoneWordingTest {

    @Test
    fun `each milestone has something to say`() {
        assertThat(MilestoneWording.of(Milestone.firstKg)).isEqualTo("That is your first kilogram.")
        assertThat(MilestoneWording.of(Milestone.halfway))
            .isEqualTo("You are halfway to your goal weight.")
        assertThat(MilestoneWording.of(Milestone.lastKg)).isEqualTo("One kilogram left.")
        assertThat(MilestoneWording.of(Milestone.everyFifth(10)))
            .isEqualTo("10 kg down since you started.")
        assertThat(MilestoneWording.of(Milestone.steady(4)))
            .isEqualTo("4 weeks running, every one of them going the right way.")
    }

    /** A name written by a later version of the app. Silence beats a sentence about "STEADY_26W". */
    @Test
    fun `a milestone this version does not know says nothing`() {
        assertThat(MilestoneWording.of(Milestone("SOMETHING_ELSE"))).isNull()
        assertThat(MilestoneWording.today(listOf(Milestone("SOMETHING_ELSE")))).isNull()
    }

    /** The fifth kilogram is also halfway, for somebody whose goal is ten. */
    @Test
    fun `two on the same day are one message`() {
        val text = MilestoneWording.today(listOf(Milestone.everyFifth(5), Milestone.halfway))!!

        assertThat(text).contains("5 kg down")
        assertThat(text).contains("halfway")
    }

    @Test
    fun `nothing reached today says nothing`() {
        assertThat(MilestoneWording.today(emptyList())).isNull()
    }
}
