package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.WorkoutKind
import org.junit.jupiter.api.Test

/**
 * D121: which week is filled in, and when. The goal fixture's plan from Monday 31 August ([TEST_EPOCH_DAY] − 3); every
 * figure is invented.
 */
class PlanFillingTest {

    private val monday = TEST_EPOCH_DAY - 3L
    private val walk = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Easy walk")

    @Test
    fun `next week is offered from Friday, this week whenever it has begun empty`() {
        val firstOnly = card(filled = 1)

        assertThat(PlanFilling.offer(firstOnly, today = monday + 3)).isNull()
        assertThat(PlanFilling.offer(firstOnly, today = monday + 4)).isEqualTo(PlanOffer.FillNextWeek(1))
        assertThat(PlanFilling.offer(firstOnly, today = monday + 6)).isEqualTo(PlanOffer.FillNextWeek(1))
        assertThat(PlanFilling.offer(card(filled = 2), today = monday + 4)).isNull()
        val weekTwo = card(filled = 1, weekIndex = 1)
        assertThat(PlanFilling.offer(weekTwo, today = monday + 7)).isEqualTo(PlanOffer.PlanThisWeek(1))
        assertThat(PlanFilling.offer(weekTwo, today = monday + 11)).isEqualTo(PlanOffer.PlanThisWeek(1))
    }

    @Test
    fun `before week 1, week 1 is next week`() {
        val waiting = card(filled = 0, weekIndex = -1, start = monday + 7)

        assertThat(PlanFilling.offer(waiting, today = monday + 3)).isNull()
        assertThat(PlanFilling.offer(waiting, today = monday + 5)).isEqualTo(PlanOffer.FillNextWeek(0))
    }

    @Test
    fun `a plan without an outline, or one finishing its week before a new plan, is never filled in`() {
        val legacy = card(filled = 1).let { it.copy(programme = it.programme.copy(plan = it.programme.plan.copy(outline = null))) }

        assertThat(PlanFilling.offer(legacy, today = monday + 4)).isNull()
        assertThat(PlanFilling.offer(card(filled = 1).copy(replacedFrom = monday + 7), today = monday + 4)).isNull()
    }

    @Test
    fun `a steady plan's last week offers the next block, a distance plan's nothing`() {
        val steady = card(filled = 4, weekIndex = 3, weeks = 4, goal = PlanGoal.Steady, outline = STEADY_OUTLINE)
        val distance = card(filled = 15, weekIndex = 14)

        assertThat(PlanFilling.offer(steady, today = monday + 21)).isEqualTo(PlanOffer.NextBlock)
        assertThat(PlanFilling.offer(steady.copy(weekIndex = 2), today = monday + 14)).isNull()
        assertThat(PlanFilling.offer(distance, today = monday + 102)).isNull()
        // An empty last week is planned first.
        val emptyLast = card(filled = 3, weekIndex = 3, weeks = 4, goal = PlanGoal.Steady, outline = STEADY_OUTLINE)
        assertThat(PlanFilling.offer(emptyLast, today = monday + 21)).isEqualTo(PlanOffer.PlanThisWeek(3))
    }

    @Test
    fun `Sunday is the day the Trainer page's button offers next week`() {
        assertThat(PlanFilling.sunday(monday + 6)).isTrue()
        assertThat(PlanFilling.sunday(monday + 4)).isFalse()
    }

    private fun card(
        filled: Int,
        weekIndex: Int = 0,
        weeks: Int = 15,
        start: Long = monday,
        goal: PlanGoal = GOAL_15K,
        outline: Outline = OUTLINE_15,
    ): PlanCard.Running {
        val plan = WeeksPlan("Invented", List(weeks) { if (it < filled) PlanWeek("w", listOf(walk)) else PlanWeek("", emptyList()) }, "Invented.", outline)
        val programme = Programme(1, 0, ProgrammeAsk(weeks, 3, goal = goal), null, plan, "a-model", start, ProgrammeStatus.RUNNING)
        return PlanCard.Running(programme, PlanProgress.of(plan, start, emptyList(), start, PlanCounting(1, 0, emptyMap())), weekIndex)
    }
}
