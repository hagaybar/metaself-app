package com.metaself.app.domain.streak

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class StreakTest {

    private val today = 20_699L

    @Test
    fun `nothing logged is no run at all`() {
        val streak = Streaks.of(emptySet(), today)

        assertThat(streak.currentDays).isEqualTo(0)
        assertThat(streak.lifetimeDays).isEqualTo(0)
        assertThat(streak.daysInLast30).isEqualTo(0)
    }

    @Test
    fun `consecutive days ending today`() {
        val streak = Streaks.of(daysEndingAt(today, 5), today)

        assertThat(streak.currentDays).isEqualTo(5)
    }

    /**
     * A morning with nothing logged yet. Reading this as a broken run would make the app hostile
     * before breakfast, which is the one thing a habit app cannot afford to be.
     */
    @Test
    fun `a morning with nothing logged yet keeps yesterday's run`() {
        val streak = Streaks.of(daysEndingAt(today - 1, 5), today)

        assertThat(streak.currentDays).isEqualTo(5)
    }

    /** Two days with nothing in them is a run that has ended, and saying otherwise would be a lie. */
    @Test
    fun `a gap of two days ends the run`() {
        val streak = Streaks.of(daysEndingAt(today - 2, 5), today)

        assertThat(streak.currentDays).isEqualTo(0)
    }

    /**
     * Decision D13, and the whole reason the streak is counted rather than stored: a counter loses a
     * run the moment a day goes unlogged, and there is no way back. Filling the day in repairs it,
     * because the run was always there — the writing-down was late.
     */
    @Test
    fun `filling in a skipped day repairs the run with no other action`() {
        val withHole = daysEndingAt(today, 5) - (today - 2)
        assertThat(Streaks.of(withHole, today).currentDays).isEqualTo(2)

        val filled = withHole + (today - 2)
        assertThat(Streaks.of(filled, today).currentDays).isEqualTo(5)
    }

    @Test
    fun `the lifetime count is every day that holds a meal, however scattered`() {
        val scattered = setOf(today, today - 1, today - 40, today - 300)

        assertThat(Streaks.of(scattered, today).lifetimeDays).isEqualTo(4)
    }

    @Test
    fun `the recent count covers thirty days including today`() {
        val streak = Streaks.of(
            setOf(today, today - 29, today - 30, today - 100),
            today,
        )

        assertThat(streak.daysInLast30).isEqualTo(2)
    }

    @Test
    fun `one day is a run of one`() {
        assertThat(Streaks.of(setOf(today), today).currentDays).isEqualTo(1)
    }

    /** Nothing offers to log forwards; this guards a clock that moved, not a case to support. */
    @Test
    fun `a day that has not happened yet counts for nothing`() {
        val streak = Streaks.of(setOf(today + 1, today + 2), today)

        assertThat(streak.currentDays).isEqualTo(0)
        assertThat(streak.lifetimeDays).isEqualTo(0)
    }

    private fun daysEndingAt(last: Long, howMany: Int): Set<Long> =
        (0 until howMany).map { last - it }.toSet()
}
