package com.metaself.app.domain.letter

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.Felt
import org.junit.jupiter.api.Test

/**
 * D142: the patterns the phone works out over the letter's 35 days. The letter's week is the one holding
 * [TEST_EPOCH_DAY] (Monday 31 August to Sunday 6 September 2026); every figure is invented, and round.
 */
class LetterPatternsTest {

    private val monday = MovementWeek.mondayOf(TEST_EPOCH_DAY)
    private val first = monday - 28

    /** 35 empty days: nothing recorded, nothing logged, no session. */
    private fun empty(): MutableList<LetterDay> = (first..monday + 6).map { LetterDay(it, 0, 0, emptyList(), null, null, null, null) }.toMutableList()

    private fun MutableList<LetterDay>.set(day: Long, change: (LetterDay) -> LetterDay) {
        val i = indexOfFirst { it.epochDay == day }
        this[i] = change(this[i])
    }

    private fun walk(minutes: Int, felt: Felt? = null) = DaySession(WorkoutKind.WALK, minutes, felt)

    private fun LetterDay.withSession(minutes: Int, felt: Felt? = null, planned: Boolean = false) = copy(
        plannedMinutes = plannedMinutes + if (planned) minutes else 0,
        ownMinutes = ownMinutes + if (planned) 0 else minutes,
        sessions = sessions + walk(minutes, felt),
    )

    @Test
    fun `nothing recorded gives no pattern but the weekday split, which rests on days`() {
        val patterns = LetterPatterns.of(empty(), monday)

        assertThat(patterns.sleepAndSessions).isNull()
        assertThat(patterns.foodOnMovingDays).isNull()
        assertThat(patterns.feltEasy).isNull()
        assertThat(patterns.longestRun).isNull()
        assertThat(patterns.weekdaysAndWeekends).isEqualTo(WeekdaysAndWeekends(0, 25, 0, 10))
    }

    /**
     * Six nights: 300, 360, 400, 440, 480, 500 — the median is 420, between the middle two. Sessions after the
     * three shorter nights: 20, 30, 40 minutes; after the three longer: 40, 50, 60.
     */
    @Test
    fun `sessions after a night under the median are set against those after a longer one`() {
        val days = empty()
        listOf(300 to 20, 360 to 30, 400 to 40, 440 to 40, 480 to 50, 500 to 60).forEachIndexed { i, (sleep, minutes) ->
            days.set(first + 2L * i) { it.copy(sleepMinutes = sleep).withSession(minutes) }
        }

        val pattern = LetterPatterns.of(days, monday).sleepAndSessions

        assertThat(pattern).isEqualTo(SleepAndSessions(420, 6, SessionGroup(3, 30), SessionGroup(3, 50)))
    }

    @Test
    fun `a night at the median counts as a longer one, and a side under three sessions drops the pattern`() {
        val days = empty()
        // Nights 300, 400, 500: the median is 400 itself.
        listOf(300, 400, 500).forEachIndexed { i, sleep -> days.set(first + i) { it.copy(sleepMinutes = sleep) } }
        days.set(first) { it.withSession(20).withSession(20).withSession(20) }
        days.set(first + 1) { it.withSession(40) }
        days.set(first + 2) { it.withSession(60) }

        assertThat(LetterPatterns.of(days, monday).sleepAndSessions).isNull()

        days.set(first + 2) { it.withSession(60) }
        assertThat(LetterPatterns.of(days, monday).sleepAndSessions).isEqualTo(SleepAndSessions(400, 3, SessionGroup(3, 20), SessionGroup(3, 53)))
    }

    /** Five weeks: 25 weekdays and 10 weekend days. 250 minutes on weekdays, 200 at weekends. */
    @Test
    fun `weekdays and weekends are counted minutes a day, planned and own together`() {
        val days = empty()
        days.set(monday) { it.withSession(150, planned = true) }
        days.set(monday + 1) { it.withSession(100) }
        days.set(monday + 5) { it.withSession(200) }

        assertThat(LetterPatterns.of(days, monday).weekdaysAndWeekends).isEqualTo(WeekdaysAndWeekends(10, 25, 20, 10))
    }

    @Test
    fun `food on moving days counts logged days only, split at twenty counted minutes`() {
        val days = empty()
        // Three moving days logged, three quiet days logged; a moving day not logged is left out.
        (0L..2L).forEach { i -> days.set(first + i) { it.withSession(20).copy(kcal = 2_400) } }
        (3L..5L).forEach { i -> days.set(first + i) { it.withSession(10).copy(kcal = 1_800) } }
        days.set(first + 6) { it.withSession(60) }

        assertThat(LetterPatterns.of(days, monday).foodOnMovingDays).isEqualTo(FoodOnMovingDays(2_400, 3, 1_800, 3))

        days.set(first + 5) { it.copy(kcal = null) }
        assertThat(LetterPatterns.of(days, monday).foodOnMovingDays).isNull()
    }

    @Test
    fun `how sessions felt sets this week's easy share against the four weeks before`() {
        val days = empty()
        days.set(monday) { it.withSession(30, Felt.EASY).withSession(30, Felt.EASY) }
        days.set(monday + 1) { it.withSession(30, Felt.RIGHT).withSession(30) }
        days.set(first) { it.withSession(30, Felt.HARD) }
        days.set(first + 7) { it.withSession(30, Felt.EASY) }

        // Before: two reviewed — too few.
        assertThat(LetterPatterns.of(days, monday).feltEasy).isNull()

        days.set(first + 14) { it.withSession(30, Felt.HARD) }
        assertThat(LetterPatterns.of(days, monday).feltEasy).isEqualTo(FeltEasy(2, 3, 1, 3))
    }

    @Test
    fun `the longest run of days with a session, the later of two as long`() {
        val days = empty()
        (0L..2L).forEach { i -> days.set(first + i) { it.withSession(30) } }
        (10L..12L).forEach { i -> days.set(first + i) { it.withSession(30) } }
        days.set(first + 20) { it.withSession(30) }

        assertThat(LetterPatterns.of(days, monday).longestRun).isEqualTo(LongestRun(3, first + 10, first + 12))

        days.set(first + 13) { it.withSession(30) }
        assertThat(LetterPatterns.of(days, monday).longestRun).isEqualTo(LongestRun(4, first + 10, first + 13))
    }

    @Test
    fun `a run under three days is not a pattern`() {
        val days = empty()
        days.set(first) { it.withSession(30) }
        days.set(first + 1) { it.withSession(30) }

        assertThat(LetterPatterns.of(days, monday).longestRun).isNull()
    }
}
