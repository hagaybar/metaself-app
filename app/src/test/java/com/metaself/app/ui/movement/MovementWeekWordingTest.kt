package com.metaself.app.ui.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.FigureSource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import org.junit.jupiter.api.Test

/**
 * Every sentence the Movement screen says (D73, D74). Every figure here is invented — they are the
 * design's own invented examples, so the expected strings can be read against the spec.
 * TEST_EPOCH_DAY is Thursday 3 September 2026.
 */
class MovementWeekWordingTest {

    private val running = workout(title = "Running", minutes = 32, distanceM = 6_200, avgHeartRate = 142)

    private val fullHealth = HealthDay(
        epochDay = TEST_EPOCH_DAY,
        steps = 9_000, stepsSource = FigureSource.TOTAL,
        distanceM = 8_000,
        activeKcal = 410, activeKcalSource = FigureSource.TOTAL,
        restingHeartRate = 58, hrvMs = 42.0, oxygenPct = 97.0, respiratoryRate = 14.0,
        // 80 + 95 + 255 = 430: the stages add up to the night.
        sleepMinutes = 430, deepMinutes = 80, remMinutes = 95, lightMinutes = 255,
    )

    private val fullDay = MovementDay(TEST_EPOCH_DAY, fullHealth, listOf(running), eatenKcal = 1_840)

    private val week = MovementWeek(
        monday = 20_696, distanceM = 42_600, averageActiveKcal = 355,
        workoutCount = 4, workoutMinutes = 141,
        days = emptyList(), previousWeeksM = listOf(38_100, 45_000, null, 44_700),
    )

    @Test
    fun `the kicker names the Monday, in capitals`() {
        assertThat(MovementWeekWording.kicker(20_696)).isEqualTo("THIS WEEK · FROM MON 31 AUG")
    }

    /** Locale.UK would say "Sept" (Java 17's CLDR data); the design says "Sep". */
    @Test
    fun `a day is its weekday and date`() {
        assertThat(MovementWeekWording.dayHeading(TEST_EPOCH_DAY)).isEqualTo("Thu 3 Sep")
    }

    @Test
    fun `the headline figures`() {
        assertThat(MovementWeekWording.distance(week)).isEqualTo("42.6 km")
        assertThat(MovementWeekWording.averageMovement(week)).isEqualTo("355 kcal of movement a day, on average")
        assertThat(MovementWeekWording.workouts(week)).isEqualTo("4 workouts · 2 h 21")
    }

    @Test
    fun `a headline figure that is not recorded is not said`() {
        val empty = week.copy(distanceM = null, averageActiveKcal = null, workoutCount = 0, workoutMinutes = 0)

        assertThat(MovementWeekWording.distance(empty)).isNull()
        assertThat(MovementWeekWording.averageMovement(empty)).isNull()
        assertThat(MovementWeekWording.workouts(empty)).isNull()
    }

    @Test
    fun `one workout is one workout`() {
        assertThat(MovementWeekWording.workouts(week.copy(workoutCount = 1, workoutMinutes = 45)))
            .isEqualTo("1 workout · 45 min")
    }

    @Test
    fun `numbers have thousands separators`() {
        assertThat(MovementWeekWording.averageMovement(week.copy(averageActiveKcal = 1_200)))
            .isEqualTo("1,200 kcal of movement a day, on average")
        assertThat(MovementWeekWording.km(1_234_500)).isEqualTo("1,234.5 km")
    }

    @Test
    fun `durations, distances and paces`() {
        assertThat(MovementWeekWording.duration(45)).isEqualTo("45 min")
        assertThat(MovementWeekWording.duration(60)).isEqualTo("1 h")
        assertThat(MovementWeekWording.duration(65)).isEqualTo("1 h 05")
        assertThat(MovementWeekWording.duration(430)).isEqualTo("7 h 10")
        assertThat(MovementWeekWording.km(6_200)).isEqualTo("6.2 km")
        assertThat(MovementWeekWording.km(45_000)).isEqualTo("45.0 km")
        assertThat(MovementWeekWording.pace(310)).isEqualTo("5:10 /km")
        assertThat(MovementWeekWording.pace(365)).isEqualTo("6:05 /km")
    }

    @Test
    fun `a day's summary is its movement, its workouts and its sleep`() {
        assertThat(MovementWeekWording.summaryLine(fullDay)).isEqualTo("410 kcal · Running 6.2 km · slept 7 h 10")
    }

    @Test
    fun `a workout with no distance is given by its time, and two are listed together`() {
        val weights = workout(title = "Weights", minutes = 45, distanceM = null)
        val day = MovementDay(TEST_EPOCH_DAY, null, listOf(running, weights), eatenKcal = null)

        assertThat(MovementWeekWording.summaryLine(day)).isEqualTo("Running 6.2 km, Weights 45 min")
    }

    @Test
    fun `a day with nothing at all says so once, with nothing beneath when open`() {
        val day = MovementDay(TEST_EPOCH_DAY, null, emptyList(), eatenKcal = null)

        assertThat(MovementWeekWording.summaryLine(day)).isEqualTo("nothing recorded")
        assertThat(MovementWeekWording.detailLines(day)).isEmpty()
    }

    /** Design question 2 in the plan: never "nothing recorded" over a day that has steps. */
    @Test
    fun `a day with only steps shows the steps, not nothing, and does not say them twice when open`() {
        val day = MovementDay(
            TEST_EPOCH_DAY,
            HealthDay(TEST_EPOCH_DAY, steps = 9_000, stepsSource = FigureSource.TOTAL),
            emptyList(),
            eatenKcal = null,
        )

        assertThat(MovementWeekWording.summaryLine(day)).isEqualTo("9,000 steps · phone and band")
        assertThat(MovementWeekWording.detailLines(day)).isEmpty()
    }

    @Test
    fun `a day with steps and food has the steps as its summary and the food beneath`() {
        val day = MovementDay(
            TEST_EPOCH_DAY,
            HealthDay(TEST_EPOCH_DAY, steps = 9_000, stepsSource = FigureSource.TOTAL),
            emptyList(),
            eatenKcal = 1_840,
        )

        assertThat(MovementWeekWording.summaryLine(day)).isEqualTo("9,000 steps · phone and band")
        assertThat(MovementWeekWording.detailLines(day)).containsExactly("1,840 kcal eaten")
    }

    @Test
    fun `beneath an open day's summary, one line per part`() {
        assertThat(MovementWeekWording.detailLines(fullDay)).containsExactly(
            "410 kcal of movement · phone and band",
            "9,000 steps · phone and band",
            "Running · 6.2 km · 32 min · 5:10 /km · avg 142 bpm",
            "Slept 7 h 10 — deep 1 h 20 · REM 1 h 35 · light 4 h 15",
            "Resting 58 · HRV 42 ms · oxygen 97% · breathing 14/min",
            "1,840 kcal eaten",
        ).inOrder()
    }

    @Test
    fun `a figure the owner set says so`() {
        val corrected = fullHealth.copy(stepsSource = FigureSource.CORRECTED, activeKcalSource = FigureSource.CORRECTED)
        val lines = MovementWeekWording.detailLines(MovementDay(TEST_EPOCH_DAY, corrected, emptyList(), null))

        assertThat(lines).contains("410 kcal of movement · you set this")
        assertThat(lines).contains("9,000 steps · you set this")
    }

    @Test
    fun `a source this version does not know is not guessed at`() {
        val unknown = HealthDay(TEST_EPOCH_DAY, activeKcal = 410, activeKcalSource = FigureSource.UNRECOGNISED)

        assertThat(MovementWeekWording.detailLines(MovementDay(TEST_EPOCH_DAY, unknown, emptyList(), null)))
            .containsExactly("410 kcal of movement")
    }

    /** D4, D69: a missing figure is left out, never written as zero. */
    @Test
    fun `only what was recorded is said`() {
        val sparse = HealthDay(TEST_EPOCH_DAY, sleepMinutes = 430, restingHeartRate = 58)
        val bare = workout(title = "Running", minutes = 32, distanceM = null)

        val lines = MovementWeekWording.detailLines(MovementDay(TEST_EPOCH_DAY, sparse, listOf(bare), null))

        assertThat(lines).containsExactly("Running · 32 min", "Slept 7 h 10", "Resting 58").inOrder()
    }

    @Test
    fun `a workout is named by its title, or by its kind when it has none`() {
        val swim = workout(title = null, minutes = 30, distanceM = null, kind = WorkoutKind.SWIM)
        val odd = workout(title = " ", minutes = 30, distanceM = null, kind = WorkoutKind.UNRECOGNISED)
        val yoga = workout(title = "Yoga", minutes = 30, distanceM = null, kind = WorkoutKind.OTHER)

        assertThat(MovementWeekWording.name(swim)).isEqualTo("Swimming")
        assertThat(MovementWeekWording.name(odd)).isEqualTo("Exercise")
        assertThat(MovementWeekWording.name(yoga)).isEqualTo("Yoga")
    }

    @Test
    fun `the last four weeks, newest first, with a dash for a week with no distance`() {
        assertThat(MovementWeekWording.lastFourWeeks(week))
            .isEqualTo("Last four weeks: 38.1 · 45.0 · — · 44.7 km")
    }

    /** Design question 6 in the plan. */
    @Test
    fun `with no distance in any of the four weeks the line is not drawn`() {
        assertThat(MovementWeekWording.lastFourWeeks(week.copy(previousWeeksM = listOf(null, null, null, null))))
            .isNull()
    }

    private fun workout(
        title: String?,
        minutes: Int,
        distanceM: Int?,
        kind: WorkoutKind = WorkoutKind.RUN,
        avgHeartRate: Int? = null,
    ) = Workout(
        id = 0, epochDay = TEST_EPOCH_DAY, startedAtMillis = 0, durationMinutes = minutes,
        kind = kind, title = title, distanceM = distanceM, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED,
        hidden = false, note = null, avgHeartRate = avgHeartRate,
    )
}
