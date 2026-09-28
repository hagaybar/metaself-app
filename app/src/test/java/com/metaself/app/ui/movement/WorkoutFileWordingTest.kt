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
import java.time.LocalDate
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

    /** D91: with the day's sessions of its kind to offer, the line asks; the time is the file's. */
    @Test
    fun `no exact match with sessions of its kind asks which`() {
        val half = file.copy(writtenAt = LocalDateTime.of(2026, 9, 3, 10, 30))

        assertThat(line(ImportOutcome.NoMatch(half, listOf(walk))))
            .isEqualTo("No session matches this file exactly (Thu 3 Sep 10:30). Is it one of these?")
        assertThat(WorkoutFileWording.OR_ADD).isEqualTo("Or, if it is a session the record does not have:")
        assertThat(WorkoutFileWording.choice(walk, zone)).isEqualTo("Walking · 10:00 · 40 min")
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

    // --- The problem log's line for each outcome (kind "import"): which figures, never their values
    // beyond what the screen's own line says; the day, never the time of a stored workout. ---

    @Test
    fun `the log says which figures a fill added, to which workout, on which day`() {
        val filled = walk.copy(distanceM = 3_250, distanceSource = WorkoutFigureSource.FILE, steps = 4_000)

        assertThat(logLine(ImportOutcome.Filled(filled, AddedFigures(distanceM = 3_250, steps = 4_000), 3_250)))
            .isEqualTo("added distance and steps to Walking on Thu 3 Sep")
        assertThat(logLine(ImportOutcome.Filled(filled, AddedFigures(distanceM = 3_250, steps = 4_000, kcal = 250), 3_250)))
            .isEqualTo("added distance, steps and calories to Walking on Thu 3 Sep")
    }

    @Test
    fun `the log says every other outcome in one plain line`() {
        assertThat(logLine(ImportOutcome.Unchanged(walk, null)))
            .isEqualTo("nothing new for Walking on Thu 3 Sep")
        assertThat(logLine(ImportOutcome.NoMatch(file)))
            .isEqualTo("no workout matches a file from Thu 3 Sep 10:00")
        assertThat(logLine(ImportOutcome.Several(file, listOf(walk, walk.copy(id = 2)))))
            .isEqualTo("2 workouts match a file from Thu 3 Sep 10:00")
        assertThat(logLine(ImportOutcome.AddedWorkout(walk)))
            .isEqualTo("added Walking on Thu 3 Sep from a file")
        assertThat(logLine(ImportOutcome.Refused(WorkoutFileRefusal.NO_START)))
            .isEqualTo("refused: the file does not say when the workout started")
        assertThat(logLine(ImportOutcome.Failed))
            .isEqualTo("not added: a write failed")
    }

    /** UNREADABLE's own log line is self-contained: it IS the Recent problems entry, so pointing at
     * "Recent problems" from inside it would say nothing; the technical detail is a separate line. */
    @Test
    fun `an unreadable file's log line does not point back at itself`() {
        assertThat(logLine(ImportOutcome.Refused(WorkoutFileRefusal.UNREADABLE)))
            .isEqualTo("refused: the file could not be opened")
    }

    @Test
    fun `every refusal has a log line`() {
        WorkoutFileRefusal.entries.forEach { reason ->
            assertThat(logLine(ImportOutcome.Refused(reason))).startsWith("refused: ")
        }
    }

    /** The log outlives the screen, so a date from a past year says so — MovementWeekWording's rule. */
    @Test
    fun `an older file's log line says the year, a recent one does not`() {
        val oldFile = FileWorkout(writtenAt = LocalDateTime.of(2025, 6, 22, 10, 0), instant = null, seconds = 2_400, distanceM = 3_250.0)
        val oldWalk = walk.copy(epochDay = LocalDate.of(2025, 6, 22).toEpochDay())

        assertThat(logLine(ImportOutcome.NoMatch(oldFile))).isEqualTo("no workout matches a file from Sun 22 Jun 2025 10:00")
        assertThat(logLine(ImportOutcome.AddedWorkout(oldWalk))).isEqualTo("added Walking on Sun 22 Jun 2025 from a file")
        assertThat(logLine(ImportOutcome.NoMatch(file))).isEqualTo("no workout matches a file from Thu 3 Sep 10:00")
    }

    private fun logLine(outcome: ImportOutcome) = WorkoutFileWording.logLine(outcome, TEST_EPOCH_DAY)

    private fun line(outcome: ImportOutcome) = WorkoutFileWording.line(outcome, zone)
}
