package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.Problem
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.AddedFigures
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.FileWorkout
import com.metaself.app.domain.movement.ImportOutcome
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFigureSource
import com.metaself.app.domain.movement.WorkoutFileMatch
import com.metaself.app.domain.movement.WorkoutFileRefusal
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset

/**
 * Importing a workout file (D82) over a fake file source and a fake store. The file is built here
 * from invented, round figures — a 30-minute walk at 10:00 on 3 September 2026, written the way a
 * band's app writes it (wall clock with a `Z`) — and the phone's zone is an invented UTC+5.
 */
class ImportWorkoutFileTest {

    private val zone = ZoneOffset.ofHours(5)
    private val files = FakeFiles()
    private val store = FakeStore()
    private val problems = RecordingProblems()
    private val import = ImportWorkoutFile(files, store, problems) { zone }

    /** The synced walk the file belongs to: 10:00 local is 05:00 UTC. */
    private val walk = Workout(
        id = 1, epochDay = TEST_EPOCH_DAY, startedAtMillis = Instant.parse("2026-09-03T05:00:00Z").toEpochMilli(),
        durationMinutes = 30, kind = WorkoutKind.WALK, title = "Walking", distanceM = null, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    @Test
    fun `one match is filled, and the outcome says what was added`() = runTest {
        store.rows += walk
        files.texts[URI] = tcx(metres = 3_250, steps = 4_000)

        val outcome = import.import(URI)

        assertThat(outcome).isInstanceOf(ImportOutcome.Filled::class.java)
        outcome as ImportOutcome.Filled
        assertThat(outcome.added).isEqualTo(AddedFigures(distanceM = 3_250, steps = 4_000))
        assertThat(outcome.fileDistanceM).isEqualTo(3_250)
        assertThat(store.rows.single().distanceSource).isEqualTo(WorkoutFigureSource.FILE)
        assertThat(store.daysAsked.single()).containsExactly(TEST_EPOCH_DAY)
    }

    @Test
    fun `the same file twice changes nothing the second time`() = runTest {
        store.rows += walk
        files.texts[URI] = tcx(metres = 3_250, steps = 4_000)
        import.import(URI)
        val after = store.rows.single()

        val again = import.import(URI)

        assertThat(again).isEqualTo(ImportOutcome.Unchanged(after, 3_250))
        assertThat(store.rows.single()).isEqualTo(after)
    }

    @Test
    fun `no match offers adding it, which adds a typed workout the same file then finds`() = runTest {
        files.texts[URI] = tcx(metres = 3_000, steps = 4_000)

        val none = import.import(URI)
        assertThat(none).isInstanceOf(ImportOutcome.NoMatch::class.java)
        assertThat(store.rows).isEmpty()

        val added = import.add((none as ImportOutcome.NoMatch).file)
        assertThat(added).isInstanceOf(ImportOutcome.AddedWorkout::class.java)
        val stored = store.rows.single()
        assertThat(stored.source).isEqualTo(WorkoutSource.TYPED)
        assertThat(stored.fromFile).isTrue()

        assertThat(import.import(URI)).isInstanceOf(ImportOutcome.Unchanged::class.java)
        assertThat(store.rows).hasSize(1)
    }

    /** Add pressed after the session arrived: it is filled, not doubled. */
    @Test
    fun `adding a file that now matches fills the match instead`() = runTest {
        files.texts[URI] = tcx(metres = 3_000)
        val none = import.import(URI) as ImportOutcome.NoMatch
        store.rows += walk

        assertThat(import.add(none.file)).isInstanceOf(ImportOutcome.Filled::class.java)
        assertThat(store.rows).hasSize(1)
    }

    @Test
    fun `several matches are listed, and the one chosen is filled`() = runTest {
        val second = walk.copy(id = 2, startedAtMillis = walk.startedAtMillis + 5 * 60_000)
        store.rows += walk
        store.rows += second
        files.texts[URI] = tcx(metres = 3_000)

        val several = import.import(URI)
        assertThat(several).isInstanceOf(ImportOutcome.Several::class.java)
        several as ImportOutcome.Several
        assertThat(several.choices.map { it.id }).containsExactly(1L, 2L)

        val chosen = import.choose(several.file, 2)
        assertThat((chosen as ImportOutcome.Filled).workout.id).isEqualTo(2)
        assertThat(store.rows.first { it.id == 1L }.distanceM).isNull()
    }

    /** D91: a file of 30 minutes that finds no partner lists the day's walk it might be, which can then be chosen. */
    @Test
    fun `no match on a day with a session of its kind offers it, and choosing it fills it`() = runTest {
        val longer = walk.copy(durationMinutes = 60)
        store.rows += longer
        files.texts[URI] = tcx(metres = 3_000)

        val none = import.import(URI)

        assertThat(none).isInstanceOf(ImportOutcome.NoMatch::class.java)
        none as ImportOutcome.NoMatch
        assertThat(none.sameKind).containsExactly(longer)
        assertThat(store.daysAsked).hasSize(1)
        val chosen = import.choose(none.file, 1)
        assertThat((chosen as ImportOutcome.Filled).added).isEqualTo(AddedFigures(distanceM = 3_000))
    }

    @Test
    fun `no match on a day with no session of its kind offers none`() = runTest {
        store.rows += walk.copy(durationMinutes = 60, kind = WorkoutKind.RUN)
        files.texts[URI] = tcx(metres = 3_000)

        assertThat((import.import(URI) as ImportOutcome.NoMatch).sameKind).isEmpty()
    }

    @Test
    fun `a file refused for its content is said, not logged`() = runTest {
        files.texts[URI] = "not a workout"

        assertThat(import.import(URI)).isEqualTo(ImportOutcome.Refused(WorkoutFileRefusal.NOT_XML))
        assertThat(problems.logged).isEmpty()
    }

    @Test
    fun `a file too large is refused by the source's reason`() = runTest {
        files.refusals[URI] = WorkoutFileRefusal.TOO_LARGE

        assertThat(import.import(URI)).isEqualTo(ImportOutcome.Refused(WorkoutFileRefusal.TOO_LARGE))
    }

    @Test
    fun `a file that cannot be opened is refused and logged`() = runTest {
        files.failure = IOException("gone")

        assertThat(import.import(URI)).isEqualTo(ImportOutcome.Refused(WorkoutFileRefusal.UNREADABLE))
        assertThat(problems.logged.single().kind).isEqualTo("workout file")
        assertThat(problems.logged.single().detail).contains("IOException gone")
    }

    @Test
    fun `a write that fails is Failed and logged, and nothing throws`() = runTest {
        store.rows += walk
        store.failing = true
        files.texts[URI] = tcx(metres = 3_000)

        assertThat(import.import(URI)).isEqualTo(ImportOutcome.Failed)
        assertThat(problems.logged.single().detail).contains("IllegalStateException disk full")
    }

    /** An invented band-style file: a 30-minute walk at 10:00, the wall clock written with a Z. */
    private fun tcx(metres: Int? = null, steps: Int? = null) = """<?xml version="1.0"?>
        <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
          <Activities><Activity Sport="Walking"><Id>2026-09-03T10:00:00Z</Id>
            <Lap StartTime="2026-09-03T10:00:00Z"><TotalTimeSeconds>1800</TotalTimeSeconds>
              ${metres?.let { "<DistanceMeters>$it</DistanceMeters>" }.orEmpty()}
              ${steps?.let { "<Steps>$it</Steps>" }.orEmpty()}
              <HeartRateBpm>100</HeartRateBpm>
            </Lap>
          </Activity></Activities>
        </TrainingCenterDatabase>"""

    private class FakeFiles : WorkoutFileSource {
        val texts = mutableMapOf<String, String>()
        val refusals = mutableMapOf<String, WorkoutFileRefusal>()
        var failure: Exception? = null
        override suspend fun read(uri: String): FileText {
            failure?.let { throw it }
            refusals[uri]?.let { return FileText.Refused(it) }
            return FileText.Text(texts.getValue(uri))
        }
    }

    private class FakeStore : WorkoutFileStore {
        val rows = mutableListOf<Workout>()
        val daysAsked = mutableListOf<Set<Long>>()
        var failing = false
        override suspend fun on(days: Set<Long>): List<Workout> {
            daysAsked += days
            return rows.filter { it.epochDay in days }
        }
        override suspend fun fill(id: Long, file: FileWorkout): WorkoutFileMatch.Filling? {
            if (failing) throw IllegalStateException("disk full")
            val index = rows.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return null
            val filling = WorkoutFileMatch.fill(rows[index], file)
            rows[index] = filling.workout
            return filling
        }
        override suspend fun add(workout: Workout): Long {
            if (failing) throw IllegalStateException("disk full")
            val id = (rows.maxOfOrNull { it.id } ?: 0) + 100
            rows += workout.copy(id = id)
            return id
        }
    }

    private class RecordingProblems : ProblemLog {
        val logged = mutableListOf<Problem>()
        override fun recent() = logged.toList()
        override fun record(kind: String, detail: String) { logged += Problem(0, kind, detail) }
        override fun clear() = logged.clear()
    }

    private companion object {
        const val URI = "content://example/walk.tcx"
    }
}
