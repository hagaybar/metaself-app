package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.movement.WorkoutKind
import org.junit.jupiter.api.Test

/** Release 1's limit 2: kept steps follow their planned session into a new version. Every figure is invented. */
class StepsCarryTest {

    @Test
    fun `an adjustment that puts the done session first moves each session's steps with it`() {
        val old = plan(listOf(EASY_30, STEADY_40))
        val new = plan(listOf(STEADY_40, EASY_30))

        assertThat(StepsCarry.moves(old, new, listOf(PlanPlace(0, 0), PlanPlace(0, 1))))
            .containsExactly(PlanPlace(0, 0), PlanPlace(0, 1), PlanPlace(0, 1), PlanPlace(0, 0))
    }

    @Test
    fun `a session changed in minutes keeps its steps by kind and effort, then by kind`() {
        val old = plan(listOf(STEADY_40, EASY_30))
        val longer = plan(listOf(STEADY_40, EASY_30.copy(minutes = 45)))
        val harder = plan(listOf(EASY_30.copy(effort = PlannedEffort.PUSH), STEADY_40))

        assertThat(StepsCarry.moves(old, longer, listOf(PlanPlace(0, 1)))).containsExactly(PlanPlace(0, 1), PlanPlace(0, 1))
        assertThat(StepsCarry.moves(old, harder, listOf(PlanPlace(0, 0), PlanPlace(0, 1))))
            .containsExactly(PlanPlace(0, 0), PlanPlace(0, 1), PlanPlace(0, 1), PlanPlace(0, 0))
    }

    @Test
    fun `steps whose session is gone go nowhere, and other weeks stay where they were`() {
        val old = WeeksPlan("Invented", listOf(PlanWeek("a", listOf(EASY_30, SWIM_30)), PlanWeek("b", listOf(EASY_30, STEADY_40))), "Invented.")
        val new = WeeksPlan("Invented", listOf(PlanWeek("a", listOf(EASY_30)), PlanWeek("b", listOf(EASY_30, STEADY_40))), "Invented.")

        assertThat(StepsCarry.moves(old, new, listOf(PlanPlace(0, 1), PlanPlace(1, 1), PlanPlace(2, 0))))
            .containsExactly(PlanPlace(0, 1), null, PlanPlace(1, 1), PlanPlace(1, 1), PlanPlace(2, 0), null)
    }

    private fun plan(week: List<PlannedSession>) = WeeksPlan("Invented", listOf(PlanWeek("a", week)), "Invented.")

    private companion object {
        val EASY_30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Easy walk")
        val STEADY_40 = PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Steady walk")
        val SWIM_30 = PlannedSession(WorkoutKind.SWIM, 30, PlannedEffort.EASY, "Easy swim")
    }
}
