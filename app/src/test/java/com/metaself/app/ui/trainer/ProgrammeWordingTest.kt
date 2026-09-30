package com.metaself.app.ui.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.trainer.PlanProgress
import com.metaself.app.domain.trainer.PlanCounting
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.PlannedTick
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.WeeksPlan
import org.junit.jupiter.api.Test

/** What the weekly plan's screens say (D94–D97). The plan starts Monday 31 August 2026; every figure is invented. */
class ProgrammeWordingTest {

    private val start = TEST_EPOCH_DAY - 3
    private val steady = PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Invented line")
    private val plan = WeeksPlan("Invented", List(4) { PlanWeek("w", listOf(steady, steady, steady)) }, "Invented.")

    @Test
    fun `a planned session is its effort, its kind and its minutes`() {
        assertThat(ProgrammeWording.plannedTitle(steady)).isEqualTo("Steady walk, 40 min")
        assertThat(ProgrammeWording.plannedTitle(PlannedSession(WorkoutKind.CYCLE, 60, PlannedEffort.PUSH, "x"))).isEqualTo("Push ride, 60 min")
        assertThat(ProgrammeWording.plannedTitle(PlannedSession(WorkoutKind.OTHER, 20, PlannedEffort.EASY, "x"))).isEqualTo("Easy session, 20 min")
        assertThat(ProgrammeWording.nextInPlan(PlannedTick(2, 4, steady))).isEqualTo("Next in your plan: steady walk, 40 min")
    }

    @Test
    fun `the card names the plan's length and week, or when it starts`() {
        assertThat(ProgrammeWording.cardHeading(4, 1, start)).isEqualTo("YOUR 4-WEEK PLAN · WEEK 2 OF 4")
        assertThat(ProgrammeWording.cardHeading(4, -1, start + 7)).isEqualTo("YOUR 4-WEEK PLAN · STARTS MON 7 SEP")
        assertThat(ProgrammeWording.weekDates(start)).isEqualTo("This week, Mon 31 Aug – Sun 6 Sep")
        assertThat(ProgrammeWording.span(start, 4)).isEqualTo("Starts Mon 31 Aug, ends Sun 27 Sep")
        assertThat(ProgrammeWording.planHeading(ProgrammeAsk(4, 3))).isEqualTo("THE PLAN · 4 WEEKS · 3 SESSIONS A WEEK")
        assertThat(ProgrammeWording.weekTitle(1, "settle in")).isEqualTo("Week 1 · settle in")
        assertThat(ProgrammeWording.weekTitle(2, " ")).isEqualTo("Week 2")
    }

    @Test
    fun `ticks say done or to do, and which session did it`() {
        val progress = PlanProgress.of(plan, start, listOf(session(1, start)), until = TEST_EPOCH_DAY, counting = PlanCounting(1, 0, emptyMap()))
        val week = progress.weeks.first()

        assertThat(ProgrammeWording.tickTitle(week.ticks[0])).isEqualTo("Done: Steady walk, 40 min")
        assertThat(ProgrammeWording.tickTitle(week.ticks[1])).isEqualTo("To do: Steady walk, 40 min")
        assertThat(ProgrammeWording.tickLine(week.ticks[0].by!!)).isEqualTo("Done Mon · Walking, 45 min")
        assertThat(ProgrammeWording.pastWeek(week)).isEqualTo("Week 1: 1 of 3 done")
        assertThat(ProgrammeWording.thisWeekSoFar(week)).isEqualTo("Week 1 (this week): 1 of 3 so far")
    }

    /** D105: the question under a planned session. Invented. */
    @Test
    fun `a shorter session is asked about by its name and minutes`() {
        assertThat(ProgrammeWording.candidateLine(session(1, start).copy(durationMinutes = 20)))
            .isEqualTo("Walking, 20 min — count it for this?")
    }

    @Test
    fun `an ended plan counts every week, and the adjust page says what is rewritten`() {
        val progress = PlanProgress.of(plan, start, listOf(session(1, start), session(2, start + 7)), until = start + 27, counting = PlanCounting(1, 0, emptyMap()))

        assertThat(ProgrammeWording.endedHeading(4)).isEqualTo("YOUR 4-WEEK PLAN HAS ENDED")
        assertThat(ProgrammeWording.endedLine(progress)).isEqualTo("2 of 12 planned sessions done · weeks 1 of 3, 1 of 3, 0 of 3, 0 of 3")
        assertThat(ProgrammeWording.rewritten(3, 4)).isEqualTo("Weeks 3 and 4: to be rewritten")
        assertThat(ProgrammeWording.rewritten(2, 4)).isEqualTo("Weeks 2 to 4: to be rewritten")
        assertThat(ProgrammeWording.rewritten(4, 4)).isEqualTo("Week 4: to be rewritten")
        assertThat(ProgrammeWording.restRewritten(4)).isEqualTo("Week 4: the rest to be rewritten")
        assertThat(ProgrammeWording.adjustRule(start, 4)).isEqualTo(
            "The trainer rewrites this week's remaining sessions and the weeks after. Weeks already over stay as they were. " +
                "The end date stays Sun 27 Sep.",
        )
    }

    /** A synced thirty-two-minute walk at 07:00 on [day]. Invented. */
    private fun session(id: Long, day: Long) = Workout(
        id = id, epochDay = day, startedAtMillis = day * 86_400_000L + 7 * 3_600_000L, durationMinutes = 45,
        kind = WorkoutKind.WALK, title = null, distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )
}
