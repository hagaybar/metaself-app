package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.Problem
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.AddedFigures
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.ImportOutcome
import com.metaself.app.domain.movement.WaitingFile
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFigureSource
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
    private val store = FakeWorkoutFileStore()
    private val problems = RecordingProblems()
    private val waiting = InMemoryWaitingFileStore()
    private val copies = mutableListOf<String>()

    /** D138: the copy from Health Connect, run before a file is matched; [arriving] is what it brings. */
    private var arriving: Workout? = null
    private var copyFails = false
    private val copier = HealthRecordCopier {
        copies += "copy"
        if (copyFails) throw IllegalStateException("no client")
        arriving?.let { store.rows += it }
    }
    private val nowMillis = 1_000_000L
    private val import = ImportWorkoutFile(files, store, problems, copier, waiting, { nowMillis }) { zone }

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
    fun `no match waits and offers adding it, which adds a typed workout the same file then finds`() = runTest {
        files.texts[URI] = tcx(metres = 3_000, steps = 4_000)

        val none = import.import(URI)
        assertThat(none).isInstanceOf(ImportOutcome.Waiting::class.java)
        assertThat(store.rows).isEmpty()

        val added = import.add((none as ImportOutcome.Waiting).file)
        assertThat(added).isInstanceOf(ImportOutcome.AddedWorkout::class.java)
        val stored = store.rows.single()
        assertThat(stored.source).isEqualTo(WorkoutSource.TYPED)
        assertThat(stored.fromFile).isTrue()
        assertThat(waiting.all()).isEmpty()

        assertThat(import.import(URI)).isInstanceOf(ImportOutcome.Unchanged::class.java)
        assertThat(store.rows).hasSize(1)
    }

    /** Add pressed after the session arrived: it is filled, not doubled. */
    @Test
    fun `adding a file that now matches fills the match instead`() = runTest {
        files.texts[URI] = tcx(metres = 3_000)
        val none = import.import(URI) as ImportOutcome.Waiting
        store.rows += walk

        assertThat(import.add(none.file)).isInstanceOf(ImportOutcome.Filled::class.java)
        assertThat(store.rows).hasSize(1)
        assertThat(waiting.all()).isEmpty()
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

    /** D91: a session hidden between the offer and the tap is not filled; the offer is made again without it. */
    @Test
    fun `a session hidden before it is chosen is not filled`() = runTest {
        store.rows += walk.copy(durationMinutes = 60)
        files.texts[URI] = tcx(metres = 3_000)
        val none = import.import(URI) as ImportOutcome.NoMatch
        store.rows[0] = store.rows[0].copy(hidden = true)

        val chosen = import.choose(none.file, 1)

        assertThat(chosen).isEqualTo(ImportOutcome.NoMatch(none.file, emptyList()))
        assertThat(store.rows.single().distanceM).isNull()
    }

    /** D138: nothing of its kind that day — the file waits for its session, from the moment it was imported. */
    @Test
    fun `no match on a day with no session of its kind keeps the file waiting`() = runTest {
        store.rows += walk.copy(durationMinutes = 60, kind = WorkoutKind.RUN)
        files.texts[URI] = tcx(metres = 3_000)

        val outcome = import.import(URI)

        assertThat(outcome).isInstanceOf(ImportOutcome.Waiting::class.java)
        assertThat(waiting.all().single()).isEqualTo(WaitingFile((outcome as ImportOutcome.Waiting).file, nowMillis))
        assertThat(problems.logged).isEmpty()
    }

    /** D138: D91's offer does not wait by itself; "None of these — wait for the band" does. */
    @Test
    fun `no match with a session of its kind waits only when asked to`() = runTest {
        store.rows += walk.copy(durationMinutes = 60)
        files.texts[URI] = tcx(metres = 3_000)

        val none = import.import(URI) as ImportOutcome.NoMatch
        assertThat(waiting.all()).isEmpty()

        assertThat(import.wait(none.file)).isEqualTo(ImportOutcome.Waiting(none.file))
        assertThat(waiting.all().map { it.file }).containsExactly(none.file)
    }

    /** D138: a session of the file's kind chosen fills it, and a file that was waiting stops. */
    @Test
    fun `choosing a session for a waiting file stops it waiting`() = runTest {
        store.rows += walk.copy(durationMinutes = 60)
        files.texts[URI] = tcx(metres = 3_000)
        val none = import.import(URI) as ImportOutcome.NoMatch
        import.wait(none.file)

        assertThat(import.choose(none.file, 1)).isInstanceOf(ImportOutcome.Filled::class.java)
        assertThat(waiting.all()).isEmpty()
    }

    /** D138: fetch first — a session the copy brings is matched at once. */
    @Test
    fun `the copy runs before matching, so a session it brings is filled`() = runTest {
        arriving = walk
        files.texts[URI] = tcx(metres = 3_000)

        assertThat(import.import(URI)).isInstanceOf(ImportOutcome.Filled::class.java)
        assertThat(copies).containsExactly("copy")
        assertThat(waiting.all()).isEmpty()
    }

    @Test
    fun `a copy that fails is logged and the import goes on`() = runTest {
        copyFails = true
        store.rows += walk
        files.texts[URI] = tcx(metres = 3_000)

        assertThat(import.import(URI)).isInstanceOf(ImportOutcome.Filled::class.java)
        assertThat(problems.logged.single().detail).contains("IllegalStateException no client")
    }

    @Test
    fun `a file refused for its content asks for no copy`() = runTest {
        files.texts[URI] = "not a workout"

        import.import(URI)

        assertThat(copies).isEmpty()
    }

    /** A waiting file imported again once its session is there: filled, and no longer waiting. */
    @Test
    fun `a waiting file imported again after its session arrived is filled and stops waiting`() = runTest {
        files.texts[URI] = tcx(metres = 3_000)
        import.import(URI)
        store.rows += walk

        assertThat(import.import(URI)).isInstanceOf(ImportOutcome.Filled::class.java)
        assertThat(waiting.all()).isEmpty()
    }

    /** The file cannot be kept waiting: it is offered to add as before, and the failure is logged. */
    @Test
    fun `a file that cannot be kept waiting is offered to add, and logged`() = runTest {
        waiting.failing = true
        files.texts[URI] = tcx(metres = 3_000)

        val outcome = import.import(URI)

        assertThat(outcome).isInstanceOf(ImportOutcome.NoMatch::class.java)
        assertThat((outcome as ImportOutcome.NoMatch).sameKind).isEmpty()
        assertThat(problems.logged.single().detail).contains("IllegalStateException disk full")
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
