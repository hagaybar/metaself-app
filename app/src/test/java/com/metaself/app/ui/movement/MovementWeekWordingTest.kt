package com.metaself.app.ui.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.FigureSource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.DistanceWitness
import com.metaself.app.domain.movement.OtherDistance
import com.metaself.app.domain.movement.SessionWitnesses
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFigureSource
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.movement.aTypedWorkout
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Every sentence the Movement screen says (D73, D74). Every figure here is invented — they are the
 * design's own invented examples, so the expected strings can be read against the spec.
 * TEST_EPOCH_DAY is Thursday 3 September 2026.
 */
class MovementWeekWordingTest {

    /** The phone's zone for a session's start time (D91): UTC here, so 07:00 on the day reads 07:00. */
    private val zone = ZoneOffset.UTC

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
        monday = 20_696, thisMonday = 20_696, today = TEST_EPOCH_DAY, distanceM = 42_600, averageActiveKcal = 355,
        workoutCount = 4, walkCount = 0, workoutMinutes = 141,
        days = emptyList(), previousWeeksM = listOf(38_100, 45_000, null, 44_700),
    )

    /** D83: this week, last week, and further back — each by its Monday, in capitals. */
    @Test
    fun `the kicker names the week and its Monday, in capitals`() {
        assertThat(MovementWeekWording.kicker(20_696, thisMonday = 20_696, today = TEST_EPOCH_DAY)).isEqualTo("THIS WEEK · FROM MON 31 AUG")
        assertThat(MovementWeekWording.kicker(20_689, thisMonday = 20_696, today = TEST_EPOCH_DAY)).isEqualTo("LAST WEEK · FROM MON 24 AUG")
        assertThat(MovementWeekWording.kicker(20_682, thisMonday = 20_696, today = TEST_EPOCH_DAY)).isEqualTo("WEEK OF MON 17 AUG")
    }

    /** D83, amended: a date outside today's year says its year. The dates are invented. */
    @Test
    fun `a week in an earlier year names its year`() {
        val monday = LocalDate.of(2025, 6, 16).toEpochDay()

        assertThat(MovementWeekWording.kicker(monday, thisMonday = 20_696, today = TEST_EPOCH_DAY)).isEqualTo("WEEK OF MON 16 JUN 2025")
        assertThat(MovementWeekWording.dayHeading(monday + 6, today = TEST_EPOCH_DAY)).isEqualTo("Sun 22 Jun 2025")
    }

    /** Across New Year each date is judged on its own: last year's days say their year, this year's do not. */
    @Test
    fun `a week across the new year gives the year only to last year's dates`() {
        val today = LocalDate.of(2027, 1, 1).toEpochDay()
        val monday = LocalDate.of(2026, 12, 28).toEpochDay()

        assertThat(MovementWeekWording.kicker(monday, thisMonday = monday, today = today)).isEqualTo("THIS WEEK · FROM MON 28 DEC 2026")
        assertThat(MovementWeekWording.dayHeading(today, today = today)).isEqualTo("Fri 1 Jan")
        assertThat(MovementWeekWording.dayHeading(today - 1, today = today)).isEqualTo("Thu 31 Dec 2026")
    }

    /** Locale.UK would say "Sept" (Java 17's CLDR data); the design says "Sep". */
    @Test
    fun `a day is its weekday and date`() {
        assertThat(MovementWeekWording.dayHeading(TEST_EPOCH_DAY, today = TEST_EPOCH_DAY)).isEqualTo("Thu 3 Sep")
    }

    @Test
    fun `the headline figures`() {
        assertThat(MovementWeekWording.distance(week)).isEqualTo("42.6 km")
        assertThat(MovementWeekWording.averageMovement(week)).isEqualTo("355 kcal of movement a day, on average")
        assertThat(MovementWeekWording.workouts(week)).isEqualTo("4 workouts · 2 h 21")
    }

    @Test
    fun `a headline figure that is not recorded is not said`() {
        val empty = week.copy(distanceM = null, averageActiveKcal = null, workoutCount = 0, walkCount = 0, workoutMinutes = 0)

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
        val weights = workout(title = "Weights", minutes = 45, distanceM = null, kind = WorkoutKind.STRENGTH)
        val day = MovementDay(TEST_EPOCH_DAY, null, listOf(running, weights), eatenKcal = null)

        assertThat(MovementWeekWording.summaryLine(day)).isEqualTo("Running 6.2 km, Weights 45 min")
    }

    @Test
    fun `a day with nothing at all says so once, with nothing beneath when open`() {
        val day = MovementDay(TEST_EPOCH_DAY, null, emptyList(), eatenKcal = null)

        assertThat(MovementWeekWording.summaryLine(day)).isEqualTo("nothing recorded")
        assertThat(MovementWeekWording.detailLines(day, zone)).isEmpty()
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
        assertThat(MovementWeekWording.detailLines(day, zone)).isEmpty()
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
        assertThat(MovementWeekWording.detailLines(day, zone)).containsExactly("1,840 kcal eaten")
    }

    @Test
    fun `beneath an open day's summary, one line per part`() {
        assertThat(MovementWeekWording.detailLines(fullDay, zone)).containsExactly(
            "410 kcal of movement · phone and band",
            "9,000 steps · phone and band",
            "Running · 07:00 · 6.2 km · 32 min · 5:10 /km · avg 142 bpm",
            "Slept 7 h 10 — deep 1 h 20 · REM 1 h 35 · light 4 h 15",
            "Resting 58 · HRV 42 ms · oxygen 97% · breathing 14/min",
            "1,840 kcal eaten",
        ).inOrder()
    }

    @Test
    fun `a figure the owner set says so`() {
        val corrected = fullHealth.copy(stepsSource = FigureSource.CORRECTED, activeKcalSource = FigureSource.CORRECTED)
        val lines = MovementWeekWording.detailLines(MovementDay(TEST_EPOCH_DAY, corrected, emptyList(), null), zone)

        assertThat(lines).contains("410 kcal of movement · you set this")
        assertThat(lines).contains("9,000 steps · you set this")
    }

    @Test
    fun `a source this version does not know is not guessed at`() {
        val unknown = HealthDay(TEST_EPOCH_DAY, activeKcal = 410, activeKcalSource = FigureSource.UNRECOGNISED)

        assertThat(MovementWeekWording.detailLines(MovementDay(TEST_EPOCH_DAY, unknown, emptyList(), null), zone))
            .containsExactly("410 kcal of movement")
    }

    /** D4, D69: a missing figure is left out, never written as zero. */
    @Test
    fun `only what was recorded is said`() {
        val sparse = HealthDay(TEST_EPOCH_DAY, sleepMinutes = 430, restingHeartRate = 58)
        val bare = workout(title = "Running", minutes = 32, distanceM = null)

        val lines = MovementWeekWording.detailLines(MovementDay(TEST_EPOCH_DAY, sparse, listOf(bare), null), zone)

        assertThat(lines).containsExactly("Running · 07:00 · 32 min", "Slept 7 h 10", "Resting 58").inOrder()
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

    /** D78: "2 workouts · 3 walks · 4 h 30" — 270 minutes, invented. */
    @Test
    fun `the headline counts walks apart from workouts, with every session's time`() {
        val busy = week.copy(workoutCount = 2, walkCount = 3, workoutMinutes = 270)

        assertThat(MovementWeekWording.workouts(busy)).isEqualTo("2 workouts · 3 walks · 4 h 30")
    }

    @Test
    fun `either part is left out when it is zero, and one is one`() {
        assertThat(MovementWeekWording.workouts(week.copy(workoutCount = 0, walkCount = 3, workoutMinutes = 270)))
            .isEqualTo("3 walks · 4 h 30")
        assertThat(MovementWeekWording.workouts(week.copy(workoutCount = 0, walkCount = 1, workoutMinutes = 45)))
            .isEqualTo("1 walk · 45 min")
    }

    /** D78: 50 + 40 + 60 = 150 minutes, invented. */
    @Test
    fun `same-kind sessions are combined in the summary, by their total time`() {
        val walks = listOf(50, 40, 60).mapIndexed { i, minutes ->
            workout(title = "Walking", minutes = minutes, distanceM = null, kind = WorkoutKind.WALK, startedAtMillis = i.toLong())
        }

        assertThat(MovementWeekWording.summaryLine(MovementDay(TEST_EPOCH_DAY, null, walks, null)))
            .isEqualTo("Walking ×3 · 2 h 30")
    }

    @Test
    fun `combined sessions give their total distance when every one has one`() {
        // 2,000 + 3,000 = 5,000 m.
        val walks = listOf(
            workout(title = "Walking", minutes = 30, distanceM = 2_000, kind = WorkoutKind.WALK),
            workout(title = "Walking", minutes = 45, distanceM = 3_000, kind = WorkoutKind.WALK, startedAtMillis = 1),
        )

        assertThat(MovementWeekWording.summaryLine(MovementDay(TEST_EPOCH_DAY, null, walks, null)))
            .isEqualTo("Walking ×2 · 5.0 km")
    }

    @Test
    fun `combined sessions with one distance missing give their time`() {
        val walks = listOf(
            workout(title = "Walking", minutes = 30, distanceM = 2_000, kind = WorkoutKind.WALK),
            workout(title = "Walking", minutes = 40, distanceM = null, kind = WorkoutKind.WALK, startedAtMillis = 1),
        )

        assertThat(MovementWeekWording.summaryLine(MovementDay(TEST_EPOCH_DAY, null, walks, null)))
            .isEqualTo("Walking ×2 · 1 h 10")
    }

    @Test
    fun `combined sessions with different titles are named by their kind, in the order kinds first started`() {
        val day = MovementDay(
            TEST_EPOCH_DAY,
            null,
            listOf(
                running.copy(startedAtMillis = 0),
                workout(title = "Hiking", minutes = 40, distanceM = null, kind = WorkoutKind.WALK, startedAtMillis = 1),
                workout(title = "Walking", minutes = 50, distanceM = null, kind = WorkoutKind.WALK, startedAtMillis = 2),
            ),
            null,
        )

        assertThat(MovementWeekWording.summaryLine(day)).isEqualTo("Running 6.2 km, Walking ×2 · 1 h 30")
    }

    /** D91: a session line says when it started, in the phone's zone — so a file whose time differs shows. */
    @Test
    fun `a session line says when it started, in the phone's zone`() {
        val walk = workout(title = "Walking", minutes = 25, distanceM = 2_000, kind = WorkoutKind.WALK, avgHeartRate = 105)
            .copy(startedAtMillis = SEVEN + 3 * 3_600_000L + 20 * 60_000L)

        assertThat(MovementWeekWording.detailLines(MovementDay(TEST_EPOCH_DAY, null, listOf(walk), null), zone))
            .containsExactly("Walking · 10:20 · 2.0 km · 25 min · avg 105 bpm")
        assertThat(MovementWeekWording.detailLines(MovementDay(TEST_EPOCH_DAY, null, listOf(walk), null), ZoneOffset.ofHours(5)))
            .containsExactly("Walking · 15:20 · 2.0 km · 25 min · avg 105 bpm")
    }

    /** D78: pace for runs only. */
    @Test
    fun `a walk with a distance shows no pace`() {
        val walk = workout(title = "Walking", minutes = 50, distanceM = 4_000, kind = WorkoutKind.WALK)

        assertThat(MovementWeekWording.detailLines(MovementDay(TEST_EPOCH_DAY, null, listOf(walk), null), zone))
            .containsExactly("Walking · 07:00 · 4.0 km · 50 min")
    }

    /** D4: a typed workout's energy says where it came from. */
    @Test
    fun `a typed workout's line says its energy and where it came from`() {
        fun line(workout: Workout) =
            MovementWeekWording.detailLines(MovementDay(TEST_EPOCH_DAY, null, listOf(workout), null), zone).single()

        assertThat(line(aTypedWorkout(startedAtMillis = SEVEN))).isEqualTo("Weights · 07:00 · 45 min · about 150 kcal, estimated")
        assertThat(line(aTypedWorkout(energyKcal = 300, energySource = EnergySource.TYPED, startedAtMillis = SEVEN)))
            .isEqualTo("Weights · 07:00 · 45 min · 300 kcal, you set this")
        assertThat(line(aTypedWorkout(energyKcal = null, energySource = EnergySource.NONE, startedAtMillis = SEVEN)))
            .isEqualTo("Weights · 07:00 · 45 min")
    }

    @Test
    fun `an open day's workout line carries its workout, and no other line does`() {
        val typed = aTypedWorkout(startedAtMillis = SEVEN)
        val rows = MovementWeekWording.detailRows(MovementDay(TEST_EPOCH_DAY, fullHealth, listOf(typed), 1_840), zone)

        assertThat(rows.mapNotNull { it.workout }).containsExactly(typed)
        assertThat(rows.single { it.workout != null }.text).isEqualTo("Weights · 07:00 · 45 min · about 150 kcal, estimated")
    }

    /** D82: a file's distance says so, to its metre-true two decimals; a file's calories say so too. */
    @Test
    fun `a workout's figures from a file say they are from the file`() {
        val filled = workout(title = "Walking", minutes = 40, distanceM = 3_250, kind = WorkoutKind.WALK)
            .copy(distanceSource = WorkoutFigureSource.FILE)
        val added = aTypedWorkout(kind = WorkoutKind.WALK, minutes = 40, energyKcal = 250, energySource = EnergySource.FILE, startedAtMillis = SEVEN)

        val filledRow = MovementWeekWording.detailRows(MovementDay(TEST_EPOCH_DAY, fullHealth, listOf(filled), 1_840), zone)
            .single { it.workout != null }.text
        val addedRow = MovementWeekWording.detailRows(MovementDay(TEST_EPOCH_DAY, fullHealth, listOf(added), 1_840), zone)
            .single { it.workout != null }.text

        assertThat(filledRow).isEqualTo("Walking · 07:00 · 3.25 km (from file) · 40 min")
        assertThat(addedRow).isEqualTo("Walking · 07:00 · 40 min · 250 kcal, from the file")
    }

    /** D92: a session other workouts also recorded says how many. */
    @Test
    fun `a combined session says how many more recorded it`() {
        val first = running.copy(id = 1)
        val combined = SessionWitnesses.combine(listOf(first, first.copy(id = 2), first.copy(id = 3, durationMinutes = 30)), emptySet()).single()

        assertThat(MovementWeekWording.alsoRecorded(combined)).isEqualTo("also recorded by 2 more")
        assertThat(MovementWeekWording.alsoRecorded(running)).isNull()
    }

    /** D92: a distance another witness disagrees with is shown beside the session's own. */
    @Test
    fun `a disagreeing distance is said beside the session's own, with who said it`() {
        fun line(saidBy: DistanceWitness) = MovementWeekWording.detailLines(
            fullDay.copy(health = null, eatenKcal = null, workouts = listOf(running.copy(otherDistance = OtherDistance(5_000, saidBy)))),
            zone,
        ).single()

        assertThat(line(DistanceWitness.APP)).isEqualTo("Running · 07:00 · 6.2 km · another app said 5.0 km · 32 min · 5:10 /km · avg 142 bpm")
        assertThat(line(DistanceWitness.FILE)).contains("6.2 km · a file said 5.00 km · ")
        assertThat(line(DistanceWitness.TYPED)).contains("6.2 km · you typed 5.0 km · ")
    }

    /** D4, D92: a distance the owner typed, on a session a band recorded, says it was typed, not measured. */
    @Test
    fun `a typed distance on a recorded session says so, also beside a disagreeing one`() {
        fun line(workout: Workout) = MovementWeekWording.detailLines(fullDay.copy(health = null, eatenKcal = null, workouts = listOf(workout)), zone).single()
        val typedOnBand = running.copy(distanceSource = WorkoutFigureSource.TYPED)

        assertThat(line(typedOnBand)).contains(" · 6.2 km (you typed) · 32 min")
        assertThat(line(typedOnBand.copy(otherDistance = OtherDistance(5_000, DistanceWitness.APP))))
            .contains(" · 6.2 km (you typed) · another app said 5.0 km · ")
        assertThat(line(aTypedWorkout(distanceM = 6_000, kind = WorkoutKind.RUN, startedAtMillis = SEVEN).copy(distanceSource = WorkoutFigureSource.TYPED)))
            .contains(" · 6.0 km · ")
    }

    private fun workout(
        title: String?,
        minutes: Int,
        distanceM: Int?,
        kind: WorkoutKind = WorkoutKind.RUN,
        avgHeartRate: Int? = null,
        startedAtMillis: Long = SEVEN,
    ) = Workout(
        id = 0, epochDay = TEST_EPOCH_DAY, startedAtMillis = startedAtMillis, durationMinutes = minutes,
        kind = kind, title = title, distanceM = distanceM, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED,
        hidden = false, note = null, avgHeartRate = avgHeartRate,
    )

    private companion object {
        /** 07:00 UTC on TEST_EPOCH_DAY. */
        const val SEVEN = TEST_EPOCH_DAY * 86_400_000L + 7 * 3_600_000L
    }
}
