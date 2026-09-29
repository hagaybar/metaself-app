package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.WorkoutKind
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** D94, D97. TEST_EPOCH_DAY is Thursday 3 September 2026; its Monday is 31 August (20_696). */
class ProgrammeCalendarTest {

    private val monday = TEST_EPOCH_DAY - 3

    @Test
    fun `kept Monday to Thursday starts this week, Friday to Sunday the next`() {
        (0L..3L).forEach { assertThat(ProgrammeCalendar.startFor(monday + it)).isEqualTo(monday) }
        (4L..6L).forEach { assertThat(ProgrammeCalendar.startFor(monday + it)).isEqualTo(monday + 7) }
    }

    @Test
    fun `a plan ends on the Sunday of its last week`() {
        assertThat(ProgrammeCalendar.lastDay(monday, 4)).isEqualTo(monday + 27)
    }

    @Test
    fun `a day's week is counted from the start, before it negative`() {
        assertThat(ProgrammeCalendar.weekIndex(monday, monday)).isEqualTo(0)
        assertThat(ProgrammeCalendar.weekIndex(monday, monday + 6)).isEqualTo(0)
        assertThat(ProgrammeCalendar.weekIndex(monday, monday + 7)).isEqualTo(1)
        assertThat(ProgrammeCalendar.weekIndex(monday, monday - 1)).isEqualTo(-1)
    }

    @Test
    fun `an ended plan is shown for fourteen days after its last day`() {
        val last = ProgrammeCalendar.lastDay(monday, 2)
        assertThat(ProgrammeCalendar.ended(monday, 2, last)).isFalse()
        assertThat(ProgrammeCalendar.ended(monday, 2, last + 1)).isTrue()
        assertThat(ProgrammeCalendar.endedShown(monday, 2, last)).isFalse()
        assertThat(ProgrammeCalendar.endedShown(monday, 2, last + 1)).isTrue()
        assertThat(ProgrammeCalendar.endedShown(monday, 2, last + 14)).isTrue()
        assertThat(ProgrammeCalendar.endedShown(monday, 2, last + 15)).isFalse()
    }

    @Test
    fun `the form's answers are two, four or six weeks and two to five sessions`() {
        assertThrows<IllegalArgumentException> { ProgrammeAsk(3, 3) }
        assertThrows<IllegalArgumentException> { ProgrammeAsk(4, 6) }
        assertThat(ProgrammeAsk(6, 5).weeks).isEqualTo(6)
    }

    @Test
    fun `a planned session is a known kind of five to 180 minutes`() {
        assertThrows<IllegalArgumentException> { PlannedSession(WorkoutKind.UNRECOGNISED, 30, PlannedEffort.EASY, "w") }
        assertThrows<IllegalArgumentException> { PlannedSession(WorkoutKind.WALK, 4, PlannedEffort.EASY, "w") }
        assertThrows<IllegalArgumentException> { PlannedSession(WorkoutKind.WALK, 181, PlannedEffort.EASY, "w") }
    }

    @Test
    fun `a new plan fits the weeks and sessions asked, an adjusted rest may leave this week empty`() {
        val one = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Walk")
        val twoWeeks = WeeksPlan("t", listOf(PlanWeek("a", listOf(one, one)), PlanWeek("b", listOf(one))), "y")
        assertThat(twoWeeks.fits(ProgrammeAsk(2, 2))).isTrue()
        assertThat(twoWeeks.fits(ProgrammeAsk(4, 2))).isFalse()
        assertThat(twoWeeks.copy(weeks = listOf(PlanWeek("a", emptyList()), PlanWeek("b", listOf(one)))).fits(ProgrammeAsk(2, 2))).isFalse()
        assertThat(twoWeeks.fits(ProgrammeAsk(2, 3))).isTrue()

        val rest = WeeksPlan("t", listOf(PlanWeek("now", emptyList()), PlanWeek("next", listOf(one))), "y")
        assertThat(rest.fitsRest(weeksLeft = 2, perWeek = 3, thisWeekMax = 0)).isTrue()
        assertThat(rest.fitsRest(weeksLeft = 3, perWeek = 3, thisWeekMax = 0)).isFalse()
        assertThat(twoWeeks.fitsRest(weeksLeft = 2, perWeek = 3, thisWeekMax = 1)).isFalse()
        assertThat(rest.fitsRest(weeksLeft = 0, perWeek = 3, thisWeekMax = 0)).isFalse()
    }
}
