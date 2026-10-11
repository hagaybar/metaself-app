package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import org.junit.jupiter.api.Test

/**
 * D123's Month view. The goal fixture's plan began five weeks before Monday 31 August 2026, so [TEST_EPOCH_DAY] is in
 * its week 6; every session and figure is invented.
 */
class PlanMonthTest {

    private val start = TEST_EPOCH_DAY - 3L - 35
    private val walk = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Easy walk")
    private val plan = WeeksPlan("Invented", List(15) { PlanWeek("w", if (it < 7) listOf(walk) else emptyList()) }, "Invented.", OUTLINE_15)
    private val programme = Programme(1, 0, ProgrammeAsk(15, 3, goal = GOAL_15K), null, plan, "a-model", start, ProgrammeStatus.RUNNING)

    @Test
    fun `each block is a row of its weeks' targets, with the goal`() {
        val month = PlanMonth.of(programme, start, TEST_EPOCH_DAY, emptyList())!!

        assertThat(month.goal).isEqualTo(GOAL_15K)
        assertThat(month.blocks.map { it.phase to it.weeks.size })
            .containsExactly(Phase.BASE to 4, Phase.BUILD to 4, Phase.PEAK to 5, Phase.TAPER to 2).inOrder()
        assertThat(month.blocks.flatMap { it.weeks }.map { it.number }).isEqualTo((1..15).toList())
        assertThat(month.blocks[0].weeks[3].target.easier).isTrue()
        assertThat(month.blocks[0].focus).isEqualTo("Easy rhythm")
    }

    @Test
    fun `past weeks carry the minutes done, this week is here, later weeks neither`() {
        val workouts = listOf(
            session(1, start, 40), session(2, start + 2, 50),
            session(3, start + 7, 30).copy(hidden = true), session(4, start + 8, 20).copy(counted = false),
            session(5, start + 15, 60).copy(kind = WorkoutKind.SWIM),
            session(6, TEST_EPOCH_DAY, 45),
        )

        val weeks = PlanMonth.of(programme, start, TEST_EPOCH_DAY, workouts)!!.blocks.flatMap { it.weeks }

        assertThat(weeks.take(5).map { it.done }).containsExactly(90, 0, 60, 0, 0).inOrder()
        assertThat(weeks[5].done).isNull()
        assertThat(weeks[5].here).isTrue()
        assertThat(weeks.count { it.here }).isEqualTo(1)
        assertThat(weeks.drop(6).all { it.done == null && !it.here }).isTrue()
    }

    @Test
    fun `the scale is the largest target or done figure, and before the plan nothing is here`() {
        val month = PlanMonth.of(programme, start, TEST_EPOCH_DAY, listOf(session(1, start, 200)))!!
        assertThat(month.scale).isEqualTo(200)
        assertThat(PlanMonth.of(programme, start, TEST_EPOCH_DAY, emptyList())!!.scale).isEqualTo(170)

        val waiting = PlanMonth.of(programme.copy(startEpochDay = TEST_EPOCH_DAY + 4), TEST_EPOCH_DAY + 4, TEST_EPOCH_DAY, emptyList())!!
        assertThat(waiting.blocks.flatMap { it.weeks }.none { it.here || it.done != null }).isTrue()
    }

    @Test
    fun `a plan without an outline has no month`() {
        assertThat(PlanMonth.of(programme.copy(plan = plan.copy(outline = null)), start, TEST_EPOCH_DAY, emptyList())).isNull()
    }

    /** A synced walk of [minutes] at 07:00 on [day]. */
    private fun session(id: Long, day: Long, minutes: Int) = Workout(
        id = id, epochDay = day, startedAtMillis = day * 86_400_000L + 7 * 3_600_000L, durationMinutes = minutes,
        kind = WorkoutKind.WALK, title = null, distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )
}
