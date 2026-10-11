package com.metaself.app.domain.letter

import com.metaself.app.domain.trainer.Felt
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/** [sessions] counted, and their average minutes. */
data class SessionGroup(val sessions: Int, val averageMinutes: Int)

/**
 * D142 pattern 1: sessions after a night under the period's median sleep, against after a night at or above
 * it. [nights] is how many nights the median is taken over.
 */
data class SleepAndSessions(val medianSleepMinutes: Int, val nights: Int, val afterShorter: SessionGroup, val afterLonger: SessionGroup)

/** D142 pattern 2: counted minutes a day on weekdays (Monday to Friday) and on weekends, over [weekdays] and [weekendDays] days. */
data class WeekdaysAndWeekends(val weekdayMinutesADay: Int, val weekdays: Int, val weekendMinutesADay: Int, val weekendDays: Int)

/** D142 pattern 3: calories a day on logged days with at least [FoodOnMovingDays.MOVING_MINUTES] counted minutes, against logged days with fewer. */
data class FoodOnMovingDays(val movingKcalADay: Int, val movingDays: Int, val otherKcalADay: Int, val otherDays: Int) {
    companion object {
        const val MOVING_MINUTES = 20
    }
}

/** D142 pattern 4: of the sessions reviewed, how many felt easy — this week, and in the four weeks before. */
data class FeltEasy(val thisWeekEasy: Int, val thisWeekReviewed: Int, val beforeEasy: Int, val beforeReviewed: Int)

/** D142 pattern 5: the longest stretch of consecutive days with at least one counted session, [from] to [to]. */
data class LongestRun(val days: Int, val from: Long, val to: Long)

/** D142: the patterns the phone works out; each null when a side of it rests on fewer than [LetterPatterns.LEAST]. */
data class LetterPatterns(
    val sleepAndSessions: SleepAndSessions?,
    val weekdaysAndWeekends: WeekdaysAndWeekends?,
    val foodOnMovingDays: FoodOnMovingDays?,
    val feltEasy: FeltEasy?,
    val longestRun: LongestRun?,
) {
    companion object {
        /** Every side of a pattern rests on at least this many days or sessions, or it is not sent. */
        const val LEAST = 3

        /**
         * @param days the letter's day rows (D141), oldest first.
         * @param weekMonday the letter's week; [FeltEasy] sets it against the four weeks before.
         */
        fun of(days: List<LetterDay>, weekMonday: Long): LetterPatterns = LetterPatterns(
            sleepAndSessions = sleep(days),
            weekdaysAndWeekends = weekdays(days),
            foodOnMovingDays = food(days),
            feltEasy = felt(days, weekMonday),
            longestRun = longestRun(days),
        )

        private fun sleep(days: List<LetterDay>): SleepAndSessions? {
            val nights = days.mapNotNull { it.sleepMinutes }.sorted()
            if (nights.isEmpty()) return null
            val median = if (nights.size % 2 == 1) nights[nights.size / 2].toDouble() else (nights[nights.size / 2 - 1] + nights[nights.size / 2]) / 2.0
            val slept = days.filter { it.sleepMinutes != null }
            val shorter = slept.filter { it.sleepMinutes!! < median }.flatMap { it.sessions }
            val longer = slept.filter { it.sleepMinutes!! >= median }.flatMap { it.sessions }
            if (shorter.size < LEAST || longer.size < LEAST) return null
            return SleepAndSessions(median.roundToInt(), nights.size, group(shorter.map { it.minutes }), group(longer.map { it.minutes }))
        }

        private fun weekdays(days: List<LetterDay>): WeekdaysAndWeekends? {
            val (weekend, weekday) = days.partition { LocalDate.ofEpochDay(it.epochDay).dayOfWeek in WEEKEND }
            if (weekday.size < LEAST || weekend.size < LEAST) return null
            return WeekdaysAndWeekends(mean(weekday.map { it.minutes }), weekday.size, mean(weekend.map { it.minutes }), weekend.size)
        }

        private fun food(days: List<LetterDay>): FoodOnMovingDays? {
            val (moving, other) = days.filter { it.kcal != null }.partition { it.minutes >= FoodOnMovingDays.MOVING_MINUTES }
            if (moving.size < LEAST || other.size < LEAST) return null
            return FoodOnMovingDays(mean(moving.map { it.kcal!! }), moving.size, mean(other.map { it.kcal!! }), other.size)
        }

        private fun felt(days: List<LetterDay>, weekMonday: Long): FeltEasy? {
            fun reviewed(range: LongRange) = days.filter { it.epochDay in range }.flatMap { it.sessions }.mapNotNull { it.felt }
            val week = reviewed(weekMonday..weekMonday + 6)
            val before = reviewed(weekMonday - 28 until weekMonday)
            if (week.size < LEAST || before.size < LEAST) return null
            return FeltEasy(week.count { it == Felt.EASY }, week.size, before.count { it == Felt.EASY }, before.size)
        }

        /** Of two stretches as long, the later. */
        private fun longestRun(days: List<LetterDay>): LongestRun? {
            var best: LongestRun? = null
            var start: Long? = null
            var previous: Long? = null
            for (day in days.filter { it.sessions.isNotEmpty() }.map { it.epochDay }.sorted()) {
                if (previous == null || day != previous + 1) start = day
                previous = day
                val run = LongestRun((day - start!! + 1).toInt(), start, day)
                if (best == null || run.days >= best.days) best = run
            }
            return best?.takeIf { it.days >= LEAST }
        }

        private fun group(minutes: List<Int>) = SessionGroup(minutes.size, mean(minutes))

        private fun mean(values: List<Int>): Int = values.average().roundToInt()

        private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}
