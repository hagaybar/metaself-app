package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** D119, D120: a goal, an outline's coverage and bounds, and the ask they make. Every figure is invented. */
class OutlineTest {

    @Test
    fun `an outline covers exactly its weeks, a steady one exactly four`() {
        assertThat(OUTLINE_15.weeks).isEqualTo(15)
        assertThat(OUTLINE_15.covers(15)).isTrue()
        assertThat(OUTLINE_15.covers(14)).isFalse()
        assertThat(OUTLINE_15.covers(16)).isFalse()
        assertThat(STEADY_OUTLINE.covers(4)).isTrue()
        assertThat(Outline(emptyList()).covers(0)).isFalse()
        assertThat(Outline(listOf(Block(Phase.BASE, "Empty", emptyList()))).covers(0)).isFalse()
    }

    @Test
    fun `a target lies in the session bounds and within what the week can hold`() {
        assertThat(OUTLINE_15.fits(perWeek = 3)).isTrue()
        assertThat(target(120, 4).fits(3)).isFalse()
        assertThat(target(200, 181).fits(3)).isFalse()
        assertThat(target(30, 40).fits(3)).isFalse()
        assertThat(target(540, 180).fits(3)).isTrue()
        assertThat(target(541, 180).fits(3)).isFalse()
        assertThat(Outline(listOf(Block(Phase.BASE, "Too long", listOf(target(541, 60)))) ).fits(3)).isFalse()
    }

    @Test
    fun `the targets read in order, later targets replace keeping phases and focus, and a block appends`() {
        assertThat(OUTLINE_15.targets.first()).isEqualTo(target(120, 40))
        assertThat(OUTLINE_15.targets[4]).isEqualTo(target(140, 50))
        assertThat(OUTLINE_15.target(14)).isEqualTo(target(90, 40, easier = true))
        assertThat(OUTLINE_15.target(15)).isNull()

        val changed = OUTLINE_15.withTargetsFrom(3, List(12) { target(100, 30) })
        assertThat(changed.targets.take(3)).isEqualTo(OUTLINE_15.targets.take(3))
        assertThat(changed.targets.drop(3)).containsExactlyElementsIn(List(12) { target(100, 30) })
        assertThat(changed.blocks.map { it.phase to it.focus }).isEqualTo(OUTLINE_15.blocks.map { it.phase to it.focus })
        assertThat(changed.blocks.map { it.weeks.size }).isEqualTo(listOf(4, 4, 5, 2))

        assertThat(OUTLINE_15.withTarget(5, target(100, 30)).target(5)).isEqualTo(target(100, 30))
        val longer = STEADY_OUTLINE + STEADY_OUTLINE.blocks.single()
        assertThat(longer.weeks).isEqualTo(8)
        assertThat(longer.blocks).hasSize(2)
    }

    @Test
    fun `a goal-less ask is 2, 4 or 6 weeks, and with a goal any number from 1`() {
        assertThrows<IllegalArgumentException> { ProgrammeAsk(3, 3) }
        assertThat(ProgrammeAsk(15, 3, goal = GOAL_15K).weeks).isEqualTo(15)
        assertThrows<IllegalArgumentException> { ProgrammeAsk(0, 3, goal = PlanGoal.Steady) }
        assertThrows<IllegalArgumentException> { PlanGoal.Distance(GoalSport.RUN, 0.0, TEST_EPOCH_DAY + 101L) }
    }

    @Test
    fun `Plan ahead's ask counts its weeks from the plan's first Monday to the goal's week`() {
        val monday = TEST_EPOCH_DAY - 3L

        assertThat(PlanAheadAsk(GOAL_15K, 3, "Invented.").toAsk(monday)).isEqualTo(ProgrammeAsk(15, 3, "Invented.", GOAL_15K))
        assertThat(PlanAheadAsk(GOAL_15K, 3).toAsk(monday + 7).weeks).isEqualTo(14)
        assertThat(PlanAheadAsk(PlanGoal.Steady, 2).toAsk(monday)).isEqualTo(ProgrammeAsk(4, 2, goal = PlanGoal.Steady))
    }
}
