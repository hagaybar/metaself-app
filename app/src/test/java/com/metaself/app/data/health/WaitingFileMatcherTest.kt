package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.FileWorkout
import com.metaself.app.domain.movement.FilledLater
import com.metaself.app.domain.movement.WaitingFile
import com.metaself.app.domain.movement.WaitingLine
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFigureSource
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.ui.RecordingProblemLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * D138: waiting files matched each time the copy has run. The file is an invented 30-minute walk at 10:00 on
 * 3 September 2026, written the way a band's app writes it (wall clock with a `Z`); the phone's zone is an invented
 * UTC+5, so the session it belongs to starts at 05:00 UTC.
 */
class WaitingFileMatcherTest {

    private val zone = ZoneOffset.ofHours(5)
    private val store = FakeWorkoutFileStore()
    private val waiting = InMemoryWaitingFileStore()
    private val problems = RecordingProblemLog()

    /** Noon UTC on TEST_EPOCH_DAY. */
    private var nowMillis = TEST_EPOCH_DAY * DAY + 12 * 3_600_000L
    private val matcher = WaitingFileMatcher(store, waiting, problems, { nowMillis }) { zone }

    private val file = FileWorkout(
        writtenAt = LocalDateTime.of(2026, 9, 3, 10, 0),
        instant = Instant.parse("2026-09-03T10:00:00Z"),
        seconds = 1_800,
        distanceM = 3_000.0,
        steps = 4_000,
        sport = "Walking",
    )

    private val walk = Workout(
        id = 1, epochDay = TEST_EPOCH_DAY, startedAtMillis = Instant.parse("2026-09-03T05:00:00Z").toEpochMilli(),
        durationMinutes = 30, kind = WorkoutKind.WALK, title = "Walking", distanceM = null, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    @Test
    fun `a session that arrives is filled with only what it lacked, and the file stops waiting`() = runTest {
        waiting.keep(file, nowMillis)
        store.rows += walk.copy(steps = 3_900)

        matcher.afterCopy()

        val filled = store.rows.single()
        assertThat(filled.distanceM).isEqualTo(3_000)
        assertThat(filled.distanceSource).isEqualTo(WorkoutFigureSource.FILE)
        assertThat(filled.steps).isEqualTo(3_900)
        assertThat(filled.stepsSource).isNull()
        assertThat(waiting.all()).isEmpty()
        assertThat(waiting.filled).containsExactly(FilledLater(WorkoutKind.WALK, TEST_EPOCH_DAY))
        assertThat(problems.recorded).isEmpty()
    }

    @Test
    fun `with its session still missing, a file keeps waiting`() = runTest {
        waiting.keep(file, nowMillis)

        matcher.afterCopy()

        assertThat(waiting.all()).containsExactly(WaitingFile(file, nowMillis))
        assertThat(waiting.filled).isEmpty()
    }

    @Test
    fun `several matches keep it waiting, and its line offers them`() = runTest {
        waiting.keep(file, nowMillis)
        store.rows += walk
        store.rows += walk.copy(id = 2, startedAtMillis = walk.startedAtMillis + 5 * 60_000)

        matcher.afterCopy()

        assertThat(waiting.all()).hasSize(1)
        assertThat(store.rows.all { it.distanceM == null }).isTrue()
        assertThat(matcher.lines.first().single().choices.map { it.id }).containsExactly(1L, 2L)
    }

    @Test
    fun `a file whose session already had everything stops waiting with nothing to say`() = runTest {
        waiting.keep(file, nowMillis)
        store.rows += walk.copy(distanceM = 2_900, steps = 3_900)

        matcher.afterCopy()

        assertThat(waiting.all()).isEmpty()
        assertThat(waiting.filled).isEmpty()
    }

    @Test
    fun `a file waiting more than 14 days is dropped and written to Recent problems`() = runTest {
        waiting.keep(file, nowMillis - 14 * DAY - 1)
        val exactly = file.copy(seconds = 1_500)
        waiting.keep(exactly, nowMillis - 14 * DAY)

        matcher.afterCopy()

        assertThat(waiting.all().map { it.file }).containsExactly(exactly)
        assertThat(problems.recorded.map { it.kind to it.detail })
            .containsExactly("import" to "a file from Thu 3 Sep 10:00 never found its session")
    }

    @Test
    fun `a failure is logged and the file keeps waiting`() = runTest {
        waiting.keep(file, nowMillis)
        store.rows += walk
        store.failing = true

        matcher.afterCopy()

        assertThat(waiting.all()).hasSize(1)
        assertThat(problems.recorded.single().kind).isEqualTo(ImportWorkoutFile.PROBLEM_KIND)
        assertThat(problems.recorded.single().detail).contains("IllegalStateException disk full")
    }

    @Test
    fun `a file's line offers no sessions when none or one matches`() = runTest {
        waiting.keep(file, nowMillis)

        assertThat(matcher.lines.first()).containsExactly(WaitingLine(WaitingFile(file, nowMillis)))
    }

    @Test
    fun `opening the page drops what waited too long and gives each note once`() = runTest {
        waiting.keep(file, nowMillis - 15 * DAY)
        waiting.noteFilled(FilledLater(WorkoutKind.RUN, TEST_EPOCH_DAY - 1))

        assertThat(matcher.opened()).containsExactly(FilledLater(WorkoutKind.RUN, TEST_EPOCH_DAY - 1))
        assertThat(matcher.opened()).isEmpty()
        assertThat(waiting.all()).isEmpty()
        assertThat(problems.recorded.single().detail).endsWith("never found its session")
    }

    @Test
    fun `dropping a file stops it waiting`() = runTest {
        waiting.keep(file, nowMillis)

        matcher.drop(file)

        assertThat(waiting.all()).isEmpty()
        assertThat(problems.recorded).isEmpty()
    }

    @Test
    fun `nothing waiting asks the record nothing`() = runTest {
        matcher.afterCopy()

        assertThat(store.daysAsked).isEmpty()
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}

