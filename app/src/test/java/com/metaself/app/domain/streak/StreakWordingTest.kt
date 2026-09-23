package com.metaself.app.domain.streak

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.day.StreakWording
import org.junit.jupiter.api.Test

class StreakWordingTest {

    @Test
    fun `a run, a lifetime and a recent window`() {
        val streak = Streak(currentDays = 12, lifetimeDays = 45, daysInLast30 = 22)

        assertThat(StreakWording.run(streak)).isEqualTo("12 days in a row")
        assertThat(StreakWording.lifetime(streak)).isEqualTo("45 days logged")
        assertThat(StreakWording.recent(streak)).isEqualTo("22 of the last 30")
    }

    @Test
    fun `one day is not one days`() {
        val streak = Streak(currentDays = 1, lifetimeDays = 1, daysInLast30 = 1)

        assertThat(StreakWording.run(streak)).isEqualTo("1 day in a row")
        assertThat(StreakWording.lifetime(streak)).isEqualTo("1 day logged")
    }

    /**
     * D14: the past never nags. "0 days in a row" is a reproach dressed as a statistic, and getting
     * the run back is a matter of logging a meal rather than of being told it is gone.
     */
    @Test
    fun `a run that has ended is not announced`() {
        val streak = Streak(currentDays = 0, lifetimeDays = 45, daysInLast30 = 3)

        assertThat(StreakWording.run(streak)).isNull()
        assertThat(StreakWording.lifetime(streak)).isEqualTo("45 days logged")
        assertThat(StreakWording.recent(streak)).isEqualTo("3 of the last 30")
    }

    @Test
    fun `before anything is logged there is nothing to say`() {
        val nothing = Streak(0, 0, 0)

        assertThat(StreakWording.run(nothing)).isNull()
        assertThat(StreakWording.lifetime(nothing)).isNull()
        assertThat(StreakWording.recent(nothing)).isNull()
    }

    /** D52: the day's one figure, which names its unit because it stands alone. */
    @Test
    fun `the day's figure is the run or the month, in words`() {
        assertThat(StreakWording.day(ConsistencyFigure.Run(days = 12, milestone = false)))
            .isEqualTo("12 days in a row")
        assertThat(StreakWording.day(ConsistencyFigure.Recent(days = 22)))
            .isEqualTo("22 of the last 30 days")
    }

    @Test
    fun `a milestone's words are the run's words without the figure`() {
        assertThat(StreakWording.runWords(30)).isEqualTo("days in a row")
    }
}
