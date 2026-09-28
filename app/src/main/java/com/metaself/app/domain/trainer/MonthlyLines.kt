package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.weight.TrendPoint
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/** One kind's sessions in a month (D89): how many, and their distance when any of them has one. */
data class KindFacts(val kind: WorkoutKind, val sessions: Int, val distanceM: Int?)

/** How the month's reviewed sessions felt (D87), counted. */
data class FeltCounts(val easy: Int, val right: Int, val hard: Int)

/**
 * One month's line (D89), computed on the phone. [firstDay]..[lastDay] are the days it covers; [part]
 * when that is not the whole calendar month (the month cut before the 42-day detail, or the month the
 * record begins in). Every figure is null when the month has nothing to make it from — never zero.
 *
 * @property kinds most sessions first, ties in [WorkoutKind]'s order.
 * @property bestWeekMonday the Monday, in the covered days, of the week with the most distance, by
 *   D74's week distance (the days' totals added up); the week may run past the month's end.
 * @property avgHeartRate the sessions' average heart rates, weighted by their minutes.
 * @property felt null when no session of the month has a felt review.
 * @property stepsADay the mean over the covered days that have a step count.
 * @property weightChangeKg the smoothed trend's change from the first day to the last; null unless the
 *   trend exists at the first day and the month holds a reading of its own (never a single weigh-in).
 */
data class MonthFacts(
    val firstDay: Long,
    val lastDay: Long,
    val part: Boolean,
    val kinds: List<KindFacts>,
    val minutes: Int,
    val longestMinutes: Int?,
    val bestWeekMonday: Long?,
    val bestWeekM: Int?,
    val avgHeartRate: Int?,
    val felt: FeltCounts?,
    val stepsADay: Int?,
    val weightChangeKg: Double?,
) {
    val sessions: Int get() = kinds.sumOf { it.sessions }
}

/** D89: a line for each of the twelve calendar months before this one. Pure. */
object MonthlyLines {

    const val MONTHS = 12

    /** The first day any monthly line can cover: the first of the month twelve months before this one. */
    fun firstDay(today: Long): Long =
        YearMonth.from(LocalDate.ofEpochDay(today)).minusMonths(MONTHS.toLong()).atDay(1).toEpochDay()

    /**
     * Oldest first. Each month is cut at the day before the 42-day detail begins
     * ([TrainerRequest.firstDay]), so no day is counted twice; a month wholly inside the detail, or wholly
     * before [earliestDay] (the first day the record holds anything), has no line. With [earliestDay] null
     * the record holds nothing, and there are none.
     *
     * @param workouts any workouts; the visible, counted ones (D74, D81) of each month are used.
     * @param reviews any reviews; a session's felt effort comes from its own.
     * @param days the daily summaries covering the months (and the week after the last Monday, for the best week).
     * @param trend the smoothed weight line, in reading order ([com.metaself.app.domain.weight.WeightTrend.of]).
     */
    fun of(
        today: Long,
        earliestDay: Long?,
        workouts: List<Workout>,
        reviews: List<TrainerReview>,
        days: List<HealthDay>,
        trend: List<TrendPoint>,
    ): List<MonthFacts> {
        if (earliestDay == null) return emptyList()
        val cut = TrainerRequest.firstDay(today) - 1
        val thisMonth = YearMonth.from(LocalDate.ofEpochDay(today))
        val feltBy = reviews.associateBy({ it.workoutId }, { it.felt })
        val byDay = days.associateBy { it.epochDay }
        val visible = workouts.filter { !it.hidden && it.counted }
        return (MONTHS downTo 1).mapNotNull { back ->
            val month = thisMonth.minusMonths(back.toLong())
            val monthStart = month.atDay(1).toEpochDay()
            val monthEnd = month.atEndOfMonth().toEpochDay()
            val first = maxOf(monthStart, earliestDay)
            val last = minOf(monthEnd, cut)
            if (first > last) {
                null
            } else {
                month(first, last, first != monthStart || last != monthEnd, visible, feltBy, byDay, trend)
            }
        }
    }

    private fun month(
        first: Long,
        last: Long,
        part: Boolean,
        visible: List<Workout>,
        feltBy: Map<Long, Felt?>,
        byDay: Map<Long, HealthDay>,
        trend: List<TrendPoint>,
    ): MonthFacts {
        val sessions = visible.filter { it.epochDay in first..last }
        val kinds = sessions.groupBy { it.kind }
            .map { (kind, same) ->
                val distances = same.mapNotNull { it.distanceM }
                KindFacts(kind, same.size, distances.takeIf { it.isNotEmpty() }?.sum())
            }
            .sortedWith(compareByDescending<KindFacts> { it.sessions }.thenBy { it.kind.ordinal })

        val best = (first..last)
            .filter { MovementWeek.mondayOf(it) == it }
            .mapNotNull { monday ->
                (monday..monday + 6).mapNotNull { byDay[it]?.distanceM }
                    .takeIf { it.isNotEmpty() }
                    ?.let { monday to it.sum() }
            }
            .maxWithOrNull(compareBy<Pair<Long, Int>> { it.second }.thenByDescending { it.first })

        val withHeart = sessions.filter { it.avgHeartRate != null && it.durationMinutes > 0 }
        val heartMinutes = withHeart.sumOf { it.durationMinutes }
        val heart = if (heartMinutes == 0) null else {
            (withHeart.sumOf { it.avgHeartRate!!.toLong() * it.durationMinutes }.toDouble() / heartMinutes).roundToInt()
        }

        val felts = sessions.mapNotNull { feltBy[it.id] }
        val felt = if (felts.isEmpty()) null else {
            FeltCounts(felts.count { it == Felt.EASY }, felts.count { it == Felt.RIGHT }, felts.count { it == Felt.HARD })
        }

        val steps = (first..last).mapNotNull { byDay[it]?.steps }

        return MonthFacts(
            firstDay = first,
            lastDay = last,
            part = part,
            kinds = kinds,
            minutes = sessions.sumOf { it.durationMinutes },
            longestMinutes = sessions.maxOfOrNull { it.durationMinutes },
            bestWeekMonday = best?.first,
            bestWeekM = best?.second,
            avgHeartRate = heart,
            felt = felt,
            stepsADay = steps.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            weightChangeKg = weightChange(first, last, trend),
        )
    }

    /** The trend after the last reading on or before each end; null unless both exist and differ. */
    private fun weightChange(first: Long, last: Long, trend: List<TrendPoint>): Double? {
        val start = trend.lastOrNull { it.reading.epochDay <= first } ?: return null
        val end = trend.lastOrNull { it.reading.epochDay <= last } ?: return null
        return if (end.reading.epochDay <= first) null else end.trendKg - start.trendKg
    }
}
