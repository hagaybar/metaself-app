package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Which stored workout a file fills in, and what it fills (D82). The phone's zone is an invented
 * UTC+5, so the two readings of the file's time — the wall clock as written, and the marker taken as
 * true — are five hours apart and never both match. TEST_EPOCH_DAY is 3 September 2026. Every figure
 * is invented.
 */
class WorkoutFileMatchTest {

    private val zone = ZoneOffset.ofHours(5)

    /** A synced 30-minute walk at 10:00 local (05:00 UTC). */
    private val walk = synced(id = 1, startUtc = "2026-09-03T05:00:00Z", minutes = 30)

    @Test
    fun `a file whose wall clock is the session's local start matches, though it says Z`() {
        val file = file(written = "2026-09-03T10:00", instantUtc = "2026-09-03T10:00:00Z", seconds = 1_800)

        assertThat(WorkoutFileMatch.matches(file, listOf(walk), zone)).containsExactly(walk)
    }

    @Test
    fun `a file whose Z really is UTC matches too`() {
        val file = file(written = "2026-09-03T05:00", instantUtc = "2026-09-03T05:00:00Z", seconds = 1_800)

        assertThat(WorkoutFileMatch.matches(file, listOf(walk), zone)).containsExactly(walk)
    }

    @Test
    fun `ten minutes off still matches, eleven does not`() {
        val ten = file(written = "2026-09-03T10:10", seconds = 1_800)
        val eleven = file(written = "2026-09-03T10:11", seconds = 1_800)

        assertThat(WorkoutFileMatch.matches(ten, listOf(walk), zone)).containsExactly(walk)
        assertThat(WorkoutFileMatch.matches(eleven, listOf(walk), zone)).isEmpty()
    }

    @Test
    fun `a duration ten percent off matches, more does not, and a short one has a minute either way`() {
        assertThat(WorkoutFileMatch.matches(file(seconds = 33 * 60), listOf(walk), zone)).containsExactly(walk)
        assertThat(WorkoutFileMatch.matches(file(seconds = 34 * 60), listOf(walk), zone)).isEmpty()

        val short = synced(id = 2, startUtc = "2026-09-03T05:00:00Z", minutes = 5)
        assertThat(WorkoutFileMatch.matches(file(seconds = 6 * 60), listOf(short), zone)).containsExactly(short)
        assertThat(WorkoutFileMatch.matches(file(seconds = 7 * 60), listOf(short), zone)).isEmpty()
    }

    @Test
    fun `a hidden session and a typed one are never candidates, a workout a file made is`() {
        val hidden = walk.copy(id = 2, hidden = true)
        val typed = aTypedWorkout(id = 3, startedAtMillis = walk.startedAtMillis, minutes = 30)
        val fromFile = typed.copy(id = 4, steps = 4_000, stepsSource = WorkoutFigureSource.FILE)

        assertThat(WorkoutFileMatch.matches(file(), listOf(hidden, typed, fromFile), zone)).containsExactly(fromFile)
    }

    @Test
    fun `a session filed on another day never matches`() {
        val otherDay = walk.copy(epochDay = TEST_EPOCH_DAY - 1)

        assertThat(WorkoutFileMatch.matches(file(), listOf(otherDay), zone)).isEmpty()
    }

    @Test
    fun `the days to look on are the local days of both readings of the time`() {
        val lateNight = file(written = "2026-09-03T22:00", instantUtc = "2026-09-03T22:00:00Z")

        assertThat(WorkoutFileMatch.days(lateNight, zone)).containsExactly(TEST_EPOCH_DAY, TEST_EPOCH_DAY + 1)
        assertThat(WorkoutFileMatch.days(file(written = "2026-09-03T10:00"), zone)).containsExactly(TEST_EPOCH_DAY)
    }

    @Test
    fun `a fill adds what is missing, with its source, and keeps every figure already there`() {
        val bare = walk
        val filled = WorkoutFileMatch.fill(bare, file(metres = 3_250.0, kcal = 250, steps = 4_000))

        assertThat(filled.workout.distanceM).isEqualTo(3_250)
        assertThat(filled.workout.distanceSource).isEqualTo(WorkoutFigureSource.FILE)
        assertThat(filled.workout.steps).isEqualTo(4_000)
        assertThat(filled.workout.stepsSource).isEqualTo(WorkoutFigureSource.FILE)
        assertThat(filled.workout.energyKcal).isEqualTo(250)
        assertThat(filled.workout.energySource).isEqualTo(EnergySource.FILE)
        assertThat(filled.added).isEqualTo(AddedFigures(distanceM = 3_250, steps = 4_000, kcal = 250))

        val measured = walk.copy(distanceM = 3_200, energyKcal = 180, energySource = EnergySource.BAND)
        val kept = WorkoutFileMatch.fill(measured, file(metres = 3_250.0, kcal = 250, steps = 4_000))
        assertThat(kept.workout.distanceM).isEqualTo(3_200)
        assertThat(kept.workout.distanceSource).isNull()
        assertThat(kept.workout.energyKcal).isEqualTo(180)
        assertThat(kept.workout.energySource).isEqualTo(EnergySource.BAND)
        assertThat(kept.added).isEqualTo(AddedFigures(steps = 4_000))
    }

    @Test
    fun `filling twice is filling once`() {
        val once = WorkoutFileMatch.fill(walk, file(metres = 3_250.0, steps = 4_000)).workout
        val twice = WorkoutFileMatch.fill(once, file(metres = 3_250.0, steps = 4_000))

        assertThat(twice.workout).isEqualTo(once)
        assertThat(twice.added.any).isFalse()
    }

    @Test
    fun `a file added as a workout is typed, at its wall clock, with the file's figures and no effort`() {
        val added = WorkoutFileMatch.asWorkout(
            file(written = "2026-09-03T10:00", seconds = 1_830, metres = 3_000.0, kcal = 150, steps = 4_000, sport = "Walking"),
            zone,
        )

        assertThat(added.id).isEqualTo(0)
        assertThat(added.source).isEqualTo(WorkoutSource.TYPED)
        assertThat(added.startedAtMillis).isEqualTo(walk.startedAtMillis)
        assertThat(added.epochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(added.durationMinutes).isEqualTo(31)
        assertThat(added.kind).isEqualTo(WorkoutKind.WALK)
        assertThat(added.effort).isNull()
        assertThat(added.distanceM).isEqualTo(3_000)
        assertThat(added.distanceSource).isEqualTo(WorkoutFigureSource.FILE)
        assertThat(added.energyKcal).isEqualTo(150)
        assertThat(added.energySource).isEqualTo(EnergySource.FILE)
        assertThat(added.steps).isEqualTo(4_000)
        assertThat(added.fromFile).isTrue()
    }

    @Test
    fun `the file's sport names the kind, and anything else is Other`() {
        fun kind(sport: String?) = WorkoutFileMatch.asWorkout(file(sport = sport), zone).kind

        assertThat(kind("Running")).isEqualTo(WorkoutKind.RUN)
        assertThat(kind("Biking")).isEqualTo(WorkoutKind.CYCLE)
        assertThat(kind("walking")).isEqualTo(WorkoutKind.WALK)
        assertThat(kind("Swimming")).isEqualTo(WorkoutKind.SWIM)
        assertThat(kind("Other")).isEqualTo(WorkoutKind.OTHER)
        assertThat(kind(null)).isEqualTo(WorkoutKind.OTHER)
        assertThat(WorkoutFileMatch.asWorkout(file(seconds = 10), zone).durationMinutes).isEqualTo(1)
    }

    private fun synced(id: Long, startUtc: String, minutes: Int) = Workout(
        id = id, epochDay = TEST_EPOCH_DAY, startedAtMillis = Instant.parse(startUtc).toEpochMilli(),
        durationMinutes = minutes, kind = WorkoutKind.WALK, title = "Walking", distanceM = null,
        energyKcal = null, energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED,
        hidden = false, note = null,
    )

    private fun file(
        written: String = "2026-09-03T10:00",
        instantUtc: String? = null,
        seconds: Int = 1_800,
        metres: Double? = 3_000.0,
        kcal: Int? = null,
        steps: Int? = null,
        sport: String? = null,
    ) = FileWorkout(
        writtenAt = LocalDateTime.parse(written),
        instant = instantUtc?.let(Instant::parse),
        seconds = seconds,
        distanceM = metres,
        kcal = kcal,
        steps = steps,
        sport = sport,
    )
}
