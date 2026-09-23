package com.metaself.app.domain.streak

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** D52: which one consistency figure the day shows, and when it is a milestone. */
class ConsistencyTest {

    private fun figure(
        streak: Streak,
        runEndsToday: Boolean = true,
        milestoneAlreadySaid: Boolean = false,
    ) = Consistency.of(streak, runEndsToday, milestoneAlreadySaid)

    @Test
    fun `a run of three or more is the figure`() {
        assertThat(figure(Streak(currentDays = 3, lifetimeDays = 40, daysInLast30 = 20)))
            .isEqualTo(ConsistencyFigure.Run(days = 3, milestone = false))
    }

    @Test
    fun `a run of two gives way to the thirty-day count`() {
        assertThat(figure(Streak(currentDays = 2, lifetimeDays = 40, daysInLast30 = 26)))
            .isEqualTo(ConsistencyFigure.Recent(days = 26))
    }

    /** D14: a run that has ended is not mentioned; the month still is. */
    @Test
    fun `no run shows the thirty-day count`() {
        assertThat(figure(Streak(currentDays = 0, lifetimeDays = 40, daysInLast30 = 4)))
            .isEqualTo(ConsistencyFigure.Recent(days = 4))
    }

    @Test
    fun `nothing is shown before anything is logged`() {
        assertThat(figure(Streak(0, 0, 0))).isNull()
    }

    /** "0 of the last 30 days" is the same reproach as "0 days in a row". */
    @Test
    fun `a quiet month is not printed as a zero`() {
        assertThat(figure(Streak(currentDays = 0, lifetimeDays = 40, daysInLast30 = 0))).isNull()
    }

    @Test
    fun `runs of exactly 7, 30, 100 and 365 are milestones`() {
        listOf(7, 30, 100, 365).forEach { days ->
            assertThat(figure(Streak(days, days, minOf(days, 30))))
                .isEqualTo(ConsistencyFigure.Run(days = days, milestone = true))
        }
    }

    @Test
    fun `the days either side of a milestone are not`() {
        listOf(6, 8, 14, 29, 31, 99, 101, 364, 366).forEach { days ->
            assertThat((figure(Streak(days, days, minOf(days, 30))) as ConsistencyFigure.Run).milestone)
                .isFalse()
        }
    }

    /** The morning after, before anything is logged, the run still stands; the milestone was yesterday's. */
    @Test
    fun `a run that ended yesterday is plain`() {
        assertThat(figure(Streak(7, 7, 7), runEndsToday = false))
            .isEqualTo(ConsistencyFigure.Run(days = 7, milestone = false))
    }

    /** The weekly congratulation already said it, so the figure does not say it again. */
    @Test
    fun `a milestone already said is plain`() {
        assertThat(figure(Streak(7, 7, 7), milestoneAlreadySaid = true))
            .isEqualTo(ConsistencyFigure.Run(days = 7, milestone = false))
    }
}
