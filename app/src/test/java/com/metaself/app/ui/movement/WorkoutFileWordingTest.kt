package com.metaself.app.ui.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.AddedFigures
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.FileWorkout
import com.metaself.app.domain.movement.ImportOutcome
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFigureSource
import com.metaself.app.domain.movement.WorkoutFileRefusal
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * What the Movement screen says after a workout file (D82). Every figure is the spec's own kind of
 * invented example; the zone is UTC, so the times read as written. TEST_EPOCH_DAY, 3 September 2026, is a Thursday.
 */
class WorkoutFileWordingTest {

    private val zone = ZoneOffset.UTC

    private val walk = Workout(
        id = 1, epochDay = TEST_EPOCH_DAY, startedAtMillis = Instant.parse("2026-09-03T10:00:00Z").toEpochMilli(),
        durationMinutes = 40, kind = WorkoutKind.WALK, title = "Walking", distanceM = null, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private val file = FileWorkout(writtenAt = LocalDateTime.of(2026, 9, 3, 10, 0), instant = null, seconds = 2_400, distanceM = 3_250.0)

    @Test
    fun `a fill says what it added, to which workout, and when`() {
        val filled = walk.copy(distanceM = 3_250, distanceSource = WorkoutFigureSource.FILE, steps = 4_000)

        assertThat(line(ImportOutcome.Filled(filled, AddedFigures(distanceM = 3_250, steps = 4_000), 3_250)))
            .isEqualTo("Added 3.25 km and 4,000 steps to Walking, Thu 3 Sep 10:00.")
        assertThat(line(ImportOutcome.Filled(filled, AddedFigures(distanceM = 3_250, steps = 4_000, kcal = 250), 3_250)))
            .isEqualTo("Added 3.25 km, 4,000 steps and 250 kcal to Walking, Thu 3 Sep 10:00.")
    }

    @Test
    fun `a distance already there is kept, and the file's is only said beside it`() {
        val measured = walk.copy(distanceM = 3_200, steps = 4_000, stepsSource = WorkoutFigureSource.FILE)

        assertThat(line(ImportOutcome.Filled(measured, AddedFigures(steps = 4_000), 3_250)))
            .isEqualTo("Added 4,000 steps to Walking, Thu 3 Sep 10:00. It already had 3.2 km; the file says 3.25 km.")
    }

    @Test
    fun `nothing new says what the session already had`() {
        assertThat(line(ImportOutcome.Unchanged(walk.copy(distanceM = 3_200), 3_250)))
            .isEqualTo("This session already had 3.2 km; nothing changed.")
        assertThat(line(ImportOutcome.Unchanged(walk.copy(distanceM = 3_250, distanceSource = WorkoutFigureSource.FILE), 3_250)))
            .isEqualTo("This session already had 3.25 km; nothing changed.")
        assertThat(line(ImportOutcome.Unchanged(walk, null)))
            .isEqualTo("This session already had everything in the file; nothing changed.")
    }

    @Test
    fun `no match, several, an added workout and a failed write each have their line`() {
        assertThat(line(ImportOutcome.NoMatch(file))).isEqualTo("No workout matches this file (Thu 3 Sep 10:00).")
        assertThat(line(ImportOutcome.Several(file, listOf(walk, walk.copy(id = 2)))))
            .isEqualTo("Several workouts match this file; choose one.")
        assertThat(line(ImportOutcome.AddedWorkout(walk.copy(source = WorkoutSource.TYPED, title = null))))
            .isEqualTo("Added Walking, Thu 3 Sep 10:00, from the file.")
        assertThat(line(ImportOutcome.Failed)).isEqualTo("The file could not be added; Recent problems says why.")
    }

    @Test
    fun `each refusal is said in plain words`() {
        val said = WorkoutFileRefusal.entries.associateWith { line(ImportOutcome.Refused(it)) }

        assertThat(said[WorkoutFileRefusal.NOT_XML]).isEqualTo("This is not a workout file MetaSelf can read.")
        assertThat(said[WorkoutFileRefusal.NOTHING_TO_ADD]).isEqualTo("The file has no distance, steps or calories to add.")
        assertThat(said[WorkoutFileRefusal.UNREADABLE]).isEqualTo("The file could not be opened; Recent problems says why.")
        assertThat(said.values.toSet()).hasSize(WorkoutFileRefusal.entries.size)
    }

    @Test
    fun `a workout to choose is its name, time and length`() {
        assertThat(WorkoutFileWording.choice(walk, zone)).isEqualTo("Walking · 10:00 · 40 min")
    }

    private fun line(outcome: ImportOutcome) = WorkoutFileWording.line(outcome, zone)
}
