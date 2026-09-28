package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.domain.weight.WeightTrend
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * D89: a line for each of the twelve months before this one. Today is TEST_EPOCH_DAY, Thursday
 * 3 September 2026; the 42-day detail begins on 24 July, so the months are cut on 23 July. June 2026
 * begins on a Monday. Every session, figure and date is invented.
 */
class MonthlyLinesTest {

    private fun lines(
        earliest: Long? = SEP_2025,
        workouts: List<Workout> = emptyList(),
        reviews: List<TrainerReview> = emptyList(),
        days: List<HealthDay> = emptyList(),
        readings: List<WeightReading> = emptyList(),
    ) = MonthlyLines.of(TEST_EPOCH_DAY, earliest, workouts, reviews, days, WeightTrend.of(readings))

    private fun june(
        workouts: List<Workout> = emptyList(),
        reviews: List<TrainerReview> = emptyList(),
        days: List<HealthDay> = emptyList(),
        readings: List<WeightReading> = emptyList(),
    ) = lines(workouts = workouts, reviews = reviews, days = days, readings = readings).single { it.firstDay == JUNE_1 }

    @Test
    fun `the months are the twelve before this one, cut the day before the detail`() {
        val months = lines()

        assertThat(months.map { LocalDate.ofEpochDay(it.firstDay).toString() }).containsExactly(
            "2025-09-01", "2025-10-01", "2025-11-01", "2025-12-01", "2026-01-01", "2026-02-01",
            "2026-03-01", "2026-04-01", "2026-05-01", "2026-06-01", "2026-07-01",
        ).inOrder()
        val july = months.last()
        assertThat(july.lastDay).isEqualTo(TrainerRequest.firstDay(TEST_EPOCH_DAY) - 1)
        assertThat(LocalDate.ofEpochDay(july.lastDay).toString()).isEqualTo("2026-07-23")
        assertThat(july.part).isTrue()
        assertThat(months.dropLast(1).none { it.part }).isTrue()
        assertThat(months.first().lastDay).isEqualTo(LocalDate.of(2025, 9, 30).toEpochDay())
    }

    @Test
    fun `a month before the record begins is left out, and the month it begins in says its first day`() {
        val months = lines(earliest = JUNE_1 + 15)

        assertThat(months.map { it.firstDay }).containsExactly(JUNE_1 + 15, JULY_1).inOrder()
        assertThat(months.first().lastDay).isEqualTo(JUNE_30)
        assertThat(months.first().part).isTrue()
    }

    @Test
    fun `with nothing stored there are no lines`() {
        assertThat(lines(earliest = null)).isEmpty()
    }

    @Test
    fun `a record that begins inside the detail has no lines`() {
        assertThat(lines(earliest = TEST_EPOCH_DAY - 10)).isEmpty()
    }

    @Test
    fun `a month with no sessions still gets a line`() {
        val june = june()

        assertThat(june.sessions).isEqualTo(0)
        assertThat(june.kinds).isEmpty()
        assertThat(june.minutes).isEqualTo(0)
        assertThat(june.longestMinutes).isNull()
        assertThat(june.bestWeekMonday).isNull()
        assertThat(june.bestWeekM).isNull()
        assertThat(june.avgHeartRate).isNull()
        assertThat(june.felt).isNull()
        assertThat(june.stepsADay).isNull()
        assertThat(june.weightChangeKg).isNull()
    }

    /** D74, D81: hidden sessions and walks that do not count are left out; kinds with most sessions first. */
    @Test
    fun `sessions are the visible, counted ones, by kind, with their minutes`() {
        val june = june(
            workouts = listOf(
                session(1, JUNE_1, WorkoutKind.SWIM, 30),
                session(2, JUNE_1 + 2, WorkoutKind.WALK, 40),
                session(3, JUNE_1 + 4, WorkoutKind.WALK, 50),
                session(4, JUNE_1 + 5, WorkoutKind.RUN, 20).copy(hidden = true),
                session(5, JUNE_1 + 6, WorkoutKind.WALK, 60).copy(counted = false),
                session(6, JULY_1, WorkoutKind.WALK, 45),
            ),
        )

        assertThat(june.kinds.map { it.kind to it.sessions })
            .containsExactly(WorkoutKind.WALK to 2, WorkoutKind.SWIM to 1).inOrder()
        assertThat(june.sessions).isEqualTo(3)
        assertThat(june.minutes).isEqualTo(120)
    }

    @Test
    fun `distance per kind only where a session of that kind has one`() {
        val june = june(
            workouts = listOf(
                session(1, JUNE_1, WorkoutKind.WALK, 40).copy(distanceM = 3_000),
                session(2, JUNE_1 + 1, WorkoutKind.WALK, 40),
                session(3, JUNE_1 + 2, WorkoutKind.STRENGTH, 30),
            ),
        )

        assertThat(june.kinds.single { it.kind == WorkoutKind.WALK }.distanceM).isEqualTo(3_000)
        assertThat(june.kinds.single { it.kind == WorkoutKind.STRENGTH }.distanceM).isNull()
    }

    @Test
    fun `the longest session's minutes`() {
        val june = june(
            workouts = listOf(session(1, JUNE_1, WorkoutKind.WALK, 40), session(2, JUNE_1 + 1, WorkoutKind.RUN, 70)),
        )

        assertThat(june.longestMinutes).isEqualTo(70)
    }

    /**
     * The week's distance is D74's: the days' totals added up. The week of 25 May has its 20 km on
     * 2 June, but its Monday is in May, so it is May's week, not June's.
     */
    @Test
    fun `the best week is the highest-distance week whose Monday is in the month`() {
        val months = lines(
            days = listOf(
                HealthDay(JUNE_1 + 1, distanceM = 20_000), // Tuesday 2 June, in the week of Monday 1 June
                HealthDay(JUNE_1 - 7, distanceM = 1_000), // Monday 25 May
                HealthDay(JUNE_1 + 7, distanceM = 6_000),
                HealthDay(JUNE_1 + 9, distanceM = 6_000),
                HealthDay(JUNE_1 + 14, distanceM = 4_000),
            ),
        )
        val june = months.single { it.firstDay == JUNE_1 }
        val may = months.single { it.firstDay == MAY_1 }

        assertThat(june.bestWeekMonday).isEqualTo(JUNE_1)
        assertThat(june.bestWeekM).isEqualTo(20_000)
        assertThat(may.bestWeekMonday).isEqualTo(JUNE_1 - 7)
        assertThat(may.bestWeekM).isEqualTo(1_000)
    }

    /**
     * D89: no day is counted twice. July is cut on Thursday 23 July; the week of Monday 20 July runs
     * into the 42-day detail, and only its days up to the cut count: 3 km, not 13.
     */
    @Test
    fun `the best week stops at the cut`() {
        val july = lines(days = listOf(HealthDay(JULY_20 + 2, distanceM = 3_000), HealthDay(JULY_20 + 5, distanceM = 10_000)))
            .single { it.firstDay == JULY_1 }

        assertThat(july.bestWeekMonday).isEqualTo(JULY_20)
        assertThat(july.bestWeekM).isEqualTo(3_000)
    }

    /** A week whose days all say 0 m is not a best week; nothing is sent rather than a zero. */
    @Test
    fun `a week of no distance is no best week`() {
        val june = june(days = listOf(HealthDay(JUNE_1, distanceM = 0), HealthDay(JUNE_1 + 7, distanceM = 0)))

        assertThat(june.bestWeekMonday).isNull()
        assertThat(june.bestWeekM).isNull()
    }

    @Test
    fun `of two weeks as far, the earlier is the best`() {
        val june = june(days = listOf(HealthDay(JUNE_1 + 7, distanceM = 5_000), HealthDay(JUNE_1 + 14, distanceM = 5_000)))

        assertThat(june.bestWeekMonday).isEqualTo(JUNE_1 + 7)
    }

    /** 100 bpm for 20 minutes and 130 for 40: (2,000 + 5,200) / 60 = 120. */
    @Test
    fun `heart rate is averaged by minutes over sessions that have one`() {
        val june = june(
            workouts = listOf(
                session(1, JUNE_1, WorkoutKind.WALK, 20).copy(avgHeartRate = 100),
                session(2, JUNE_1 + 1, WorkoutKind.RUN, 40).copy(avgHeartRate = 130),
                session(3, JUNE_1 + 2, WorkoutKind.WALK, 60),
            ),
        )

        assertThat(june.avgHeartRate).isEqualTo(120)
    }

    @Test
    fun `felt counts come from the month's reviews`() {
        val workouts = (1L..4L).map { session(it, JUNE_1 + it, WorkoutKind.WALK, 30) } + session(5, JULY_1, WorkoutKind.WALK, 30)
        val reviews = listOf(
            review(1, Felt.EASY), review(2, Felt.RIGHT), review(3, Felt.RIGHT), review(4, null), review(5, Felt.HARD),
        )

        assertThat(june(workouts = workouts, reviews = reviews).felt).isEqualTo(FeltCounts(easy = 1, right = 2, hard = 0))
        assertThat(june(workouts = workouts, reviews = listOf(review(4, null))).felt).isNull()
    }

    @Test
    fun `steps a day are averaged over the days that have a count`() {
        val june = june(
            days = listOf(
                HealthDay(JUNE_1, steps = 6_000),
                HealthDay(JUNE_1 + 1, steps = 8_000),
                HealthDay(JUNE_1 + 2, distanceM = 1_000),
                HealthDay(JULY_1, steps = 20_000),
            ),
        )

        assertThat(june.stepsADay).isEqualTo(7_000)
    }

    /** The smoothed line at each end, never a weigh-in: the expected change is worked out by the trend itself. */
    @Test
    fun `the weight change is the trend's, from the month's first day to its last`() {
        val readings = listOf(WeightReading(MAY_31, 80.0), WeightReading(JUNE_1 + 14, 79.0), WeightReading(JUNE_30, 79.0))
        val trend = WeightTrend.of(readings)

        val change = june(readings = readings).weightChangeKg!!

        assertThat(change).isWithin(1e-9).of(trend.last().trendKg - trend.first().trendKg)
        assertThat(change).isNotEqualTo(79.0 - 80.0)
    }

    @Test
    fun `a month with no weigh-in of its own has no weight change`() {
        assertThat(june(readings = listOf(WeightReading(MAY_31, 80.0), WeightReading(JULY_1, 79.0))).weightChangeKg).isNull()
    }

    /** The trend at each end must rest on a weigh-in at most 14 days before it; after a longer gap it is stale. */
    @Test
    fun `a trend resting on a weigh-in more than 14 days before an end gives no weight change`() {
        val staleStart = listOf(WeightReading(JUNE_1 - 15, 80.0), WeightReading(JUNE_30, 79.0))
        val staleEnd = listOf(WeightReading(MAY_31, 80.0), WeightReading(JUNE_30 - 15, 79.0))
        val fresh = listOf(WeightReading(JUNE_1 - 14, 80.0), WeightReading(JUNE_30 - 14, 79.0))

        assertThat(june(readings = staleStart).weightChangeKg).isNull()
        assertThat(june(readings = staleEnd).weightChangeKg).isNull()
        assertThat(june(readings = fresh).weightChangeKg).isNotNull()
    }

    @Test
    fun `a month with no trend at its first day has no weight change`() {
        assertThat(june(readings = listOf(WeightReading(JUNE_1 + 1, 80.0), WeightReading(JUNE_30, 79.0))).weightChangeKg).isNull()
    }

    /** D92: a combined session is one session of the month, and its other witness's review is its felt. */
    @Test
    fun `a combined session counts once, with the felt of its other witness's review`() {
        val first = session(id = 1, day = JUNE_1 + 2, kind = WorkoutKind.WALK, minutes = 40)
        val second = first.copy(id = 2, durationMinutes = 30)
        val combined = com.metaself.app.domain.movement.SessionWitnesses.combine(listOf(first, second), emptySet())

        val june = june(workouts = combined, reviews = listOf(review(2, Felt.HARD)))

        assertThat(june.sessions).isEqualTo(1)
        assertThat(june.felt).isEqualTo(FeltCounts(easy = 0, right = 0, hard = 1))
    }

    private fun session(id: Long, day: Long, kind: WorkoutKind, minutes: Int) = Workout(
        id = id, epochDay = day, startedAtMillis = day * 86_400_000L + 7 * 3_600_000L, durationMinutes = minutes,
        kind = kind, title = null, distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private fun review(workoutId: Long, felt: Felt?) = TrainerReview(workoutId = workoutId, planId = null, felt = felt, words = null)

    private companion object {
        val SEP_2025 = LocalDate.of(2025, 9, 1).toEpochDay()
        val MAY_1 = LocalDate.of(2026, 5, 1).toEpochDay()
        val MAY_31 = LocalDate.of(2026, 5, 31).toEpochDay()
        val JUNE_1 = LocalDate.of(2026, 6, 1).toEpochDay()
        val JUNE_30 = LocalDate.of(2026, 6, 30).toEpochDay()
        val JULY_1 = LocalDate.of(2026, 7, 1).toEpochDay()
        val JULY_20 = LocalDate.of(2026, 7, 20).toEpochDay()
    }
}
