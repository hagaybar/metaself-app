package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.Problem
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.health.HealthKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The copying rules (D67), with no Health Connect and no database. Every figure is invented; "now" is
 * day 100 at midnight UTC (1970-04-11), so windows are easy to read, and the log's dates are UTC.
 */
class HealthRecordSyncTest {

    private val now = 100 * DAY
    private val source = FakeSource()
    private val store = FakeStore()
    private val problems = RecordingProblems()
    private val sync = HealthRecordSync(source, store, problems, now = { now }, zone = { ZoneOffset.UTC })

    @Test
    fun `nothing granted, nothing read`() = runTest {
        sync.copyNow()

        assertThat(source.calls).isEmpty()
    }

    @Test
    fun `a new kind takes its token before its first slice`() = runTest {
        source.granted = setOf(HealthKind.STEPS)

        sync.copyNow()

        assertThat(source.calls.first()).isEqualTo("token STEPS")
        assertThat(source.calls[1]).isEqualTo("window STEPS ${93 * DAY}..${100 * DAY}")
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).changesToken).isEqualTo("t-STEPS-1")
    }

    @Test
    fun `catch-up goes back a week at a time and stops after eight empty weeks`() = runTest {
        source.granted = setOf(HealthKind.STEPS)

        sync.copyNow()

        val windows = source.calls.filter { it.startsWith("window") }
        assertThat(windows).hasSize(8)
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpDone).isTrue()
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpCursorMillis).isEqualTo(44 * DAY)
        assertThat(problems.logged.map { it.detail })
            .containsExactly("STEPS: nothing before 1970-04-11; catch-up finished")
    }

    /** Rule 4: two years back is as far as the catch-up goes, even when every week has data. */
    @Test
    fun `catch-up stops two years back even when every week has data`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        store.bookmarks[HealthKind.STEPS] = HealthSyncEntity(
            kind = "STEPS", changesToken = "t", tokenAtMillis = 0, catchUpCursorMillis = -620 * DAY, catchUpDone = false,
        )
        source.windowRecords = { from, _ -> listOf(steps("st-$from", from)) }

        sync.copyNow()

        assertThat(source.calls.filter { it.startsWith("window") })
            .containsExactly("window STEPS ${-627 * DAY}..${-620 * DAY}", "window STEPS ${-634 * DAY}..${-627 * DAY}")
            .inOrder()
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpDone).isTrue()
        assertThat(problems.logged.map { it.detail })
            .contains("STEPS: nothing before ${LocalDate.ofEpochDay(-634)}; catch-up finished")
    }

    @Test
    fun `a week with data resets the count of empty weeks`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.windowRecords = { from, _ -> if (from == 86 * DAY) listOf(steps("st-1", 88 * DAY)) else emptyList() }

        sync.copyNow()

        // The week 93..100 is empty, 86..93 has data, then eight empty weeks follow.
        assertThat(source.calls.count { it.startsWith("window") }).isEqualTo(10)
        assertThat(store.applied.flatMap { it.first }.map { it.recordId }).contains("st-1")
    }

    @Test
    fun `a kind with a token drains its changes and keeps the new token`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        store.bookmarks[HealthKind.STEPS] = done("t-old")
        source.pages = mutableListOf(
            ChangesPage(listOf(steps("st-1", 99 * DAY)), emptyList(), "t-2", hasMore = true, expired = false),
            ChangesPage(emptyList(), listOf("st-0"), "t-3", hasMore = false, expired = false),
        )

        sync.copyNow()

        assertThat(source.calls).containsExactly("changes STEPS t-old", "changes STEPS t-2", "totals 99..99").inOrder()
        assertThat(store.applied.map { it.second }).containsExactly(emptyList<String>(), listOf("st-0")).inOrder()
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).changesToken).isEqualTo("t-3")
    }

    @Test
    fun `an expired token re-reads the last thirty days and takes a fresh token`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        store.bookmarks[HealthKind.STEPS] = done("t-old")
        source.pages = mutableListOf(ChangesPage(emptyList(), emptyList(), "", hasMore = false, expired = true))

        sync.copyNow()

        assertThat(source.calls.take(3))
            .containsExactly("changes STEPS t-old", "token STEPS", "window STEPS ${70 * DAY}..${100 * DAY}")
            .inOrder()
        assertThat(store.replaced).containsExactly("STEPS ${70 * DAY}..${100 * DAY}")
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).changesToken).isEqualTo("t-STEPS-1")
    }

    /** Rule 5: an old refusal is the history limit, and the log says from when. */
    @Test
    fun `a refused read older than thirty days ends the catch-up and is recorded`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.refuseBefore = 65 * DAY

        sync.copyNow()

        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpDone).isTrue()
        assertThat(problems.logged.single().kind).isEqualTo("health")
        assertThat(problems.logged.single().detail).contains("STEPS")
        assertThat(problems.logged.single().detail).contains("1970-03-07")
        assertThat(problems.logged.single().detail).contains("history limit")
    }

    /** Rule 5: a failure that says "try later" is not the history limit, however old the week. */
    @Test
    fun `a transient failure of an old read keeps the catch-up going`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.refuseBefore = 65 * DAY
        source.refusal = { IOException("connection lost") }

        sync.copyNow()

        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpDone).isFalse()
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpCursorMillis).isEqualTo(65 * DAY)
        assertThat(problems.logged.single().detail).contains("will try again")
    }

    @Test
    fun `a rate limit on an old read is transient too`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.refuseBefore = 65 * DAY
        source.refusal = { IllegalStateException("Request Rate limited") }

        sync.copyNow()

        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpDone).isFalse()
    }

    @Test
    fun `a refused recent read stops the kind for now and tries again next time`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.refuseBefore = 100 * DAY

        sync.copyNow()

        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpDone).isFalse()
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpCursorMillis).isEqualTo(100 * DAY)
    }

    @Test
    fun `one open never reads more than its budget`() = runTest {
        source.granted = HealthKind.entries.toSet()

        sync.copyNow()

        assertThat(source.calls.count { it.startsWith("window") || it.startsWith("changes") })
            .isEqualTo(HealthRecordSync.READS_PER_OPEN)
        assertThat(source.calls.count { it.startsWith("token") }).isEqualTo(HealthKind.entries.size)
    }

    /** Every kind gets its token at once, however much catch-up the kinds before it still owe. */
    @Test
    fun `every granted kind takes its token before any catch-up`() = runTest {
        source.granted = HealthKind.entries.toSet()

        sync.copyNow()

        assertThat(source.calls.take(HealthKind.entries.size))
            .containsExactlyElementsIn(HealthKind.entries.map { "token $it" })
            .inOrder()
    }

    /** Kinds share the budget: one week each in turn, newest first, so no kind waits for another. */
    @Test
    fun `catch-up takes one week of each kind in turn`() = runTest {
        source.granted = setOf(HealthKind.HEART_RATE, HealthKind.STEPS)

        sync.copyNow()

        assertThat(source.calls.filter { it.startsWith("window") }.take(4)).containsExactly(
            "window STEPS ${93 * DAY}..${100 * DAY}",
            "window HEART_RATE ${93 * DAY}..${100 * DAY}",
            "window STEPS ${86 * DAY}..${93 * DAY}",
            "window HEART_RATE ${86 * DAY}..${93 * DAY}",
        ).inOrder()
    }

    /** Rule 6: an open that runs out of budget leaves a cursor, and the next open starts there. */
    @Test
    fun `the next open carries on from where the budget ran out`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.windowRecords = { from, _ -> listOf(steps("st-$from", from)) }

        sync.copyNow()
        val cursor = store.bookmarks.getValue(HealthKind.STEPS).catchUpCursorMillis!!
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpDone).isFalse()
        source.calls.clear()
        sync.copyNow()

        assertThat(source.calls.first()).isEqualTo("changes STEPS t-STEPS-1")
        assertThat(source.calls.first { it.startsWith("window") })
            .isEqualTo("window STEPS ${cursor - 7 * DAY}..$cursor")
    }

    /** Rule 8: a second copy asked for while one is running does nothing. */
    @Test
    fun `a copy asked for while one runs does nothing`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        val gate = CompletableDeferred<Unit>()
        source.gate = gate

        val first = launch { sync.copyNow() }
        val second = launch { sync.copyNow() }
        runCurrent()
        gate.complete(Unit)
        first.join()
        second.join()

        assertThat(source.grantedAsked).isEqualTo(1)
        assertThat(source.calls.count { it == "token STEPS" }).isEqualTo(1)
    }

    /**
     * Days are summarised as they are copied: after the changes, and after each turn of catch-up, with
     * totals asked for each summarised range.
     */
    @Test
    fun `touched days are summarised, with totals asked for each range`() = runTest {
        source.granted = setOf(HealthKind.STEPS, HealthKind.HEART_RATE)
        store.bookmarks[HealthKind.STEPS] = done("a")
        store.bookmarks[HealthKind.HEART_RATE] = HealthSyncEntity(
            kind = "HEART_RATE", changesToken = "b", tokenAtMillis = 0, catchUpCursorMillis = 100 * DAY, catchUpDone = false,
        )
        source.pages = mutableListOf(
            ChangesPage(listOf(steps("st-1", 97 * DAY), steps("st-2", 99 * DAY)), emptyList(), "a2", false, false),
        )
        source.windowRecords = { from, _ -> if (from == 93 * DAY) listOf(steps("hr-1", 95 * DAY)) else emptyList() }

        sync.copyNow()

        assertThat(store.summarised.flatten().toSet()).containsExactly(95L, 97L, 99L)
        assertThat(store.summarised).containsExactly(setOf(97L, 99L), setOf(95L)).inOrder()
        assertThat(source.calls.filter { it.startsWith("totals") })
            .containsExactly("totals 97..99", "totals 95..95").inOrder()
    }

    @Test
    fun `an error, not only an exception, is logged rather than thrown`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.grantedError = AssertionError("client broke")

        sync.copyNow()

        assertThat(problems.logged.single().detail).contains("AssertionError")
    }

    @Test
    fun `nothing it does throws`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        store.bookmarks[HealthKind.STEPS] = done("t")
        source.changesThrow = true

        sync.copyNow()

        assertThat(problems.logged).isNotEmpty()
    }

    // --- Fakes -----------------------------------------------------------------------------------

    private fun done(token: String, kind: HealthKind = HealthKind.STEPS) = HealthSyncEntity(
        kind = kind.name, changesToken = token, tokenAtMillis = 0, catchUpCursorMillis = 0, catchUpDone = true,
    )

    private fun steps(id: String, at: Long) = ReadRecord.Reading(
        HealthKind.STEPS, "com.example.band", id, listOf(Sample(at, at + 60_000, 100.0)),
    )

    private class FakeSource : HealthSource {
        val calls = mutableListOf<String>()
        var granted: Set<HealthKind> = emptySet()
        var pages = mutableListOf<ChangesPage>()
        var windowRecords: (Long, Long) -> List<ReadRecord> = { _, _ -> emptyList() }
        var refuseBefore: Long? = null
        var refusal: () -> Exception = { SecurityException("refused") }
        var changesThrow = false
        var gate: CompletableDeferred<Unit>? = null
        var grantedError: Throwable? = null
        var grantedAsked = 0
        private var tokens = 0

        override suspend fun grantedKinds(): Set<HealthKind> {
            grantedAsked++
            gate?.await()
            grantedError?.let { throw it }
            return granted
        }
        override suspend fun changesToken(kind: HealthKind): String {
            calls += "token $kind"
            return "t-$kind-${++tokens}"
        }
        override suspend fun changes(kind: HealthKind, token: String): ChangesPage {
            calls += "changes $kind $token"
            if (changesThrow) throw IllegalStateException("unavailable")
            return pages.removeFirstOrNull() ?: ChangesPage(emptyList(), emptyList(), token, hasMore = false, expired = false)
        }
        override suspend fun readWindow(kind: HealthKind, fromMillis: Long, toMillis: Long): List<ReadRecord> {
            calls += "window $kind $fromMillis..$toMillis"
            refuseBefore?.let { if (toMillis <= it) throw refusal() }
            return windowRecords(fromMillis, toMillis)
        }
        override suspend fun dayTotals(fromDay: Long, toDay: Long): Map<Long, DayTotals> {
            calls += "totals $fromDay..$toDay"
            return emptyMap()
        }
    }

    private class FakeStore : HealthStore {
        val bookmarks = mutableMapOf<HealthKind, HealthSyncEntity>()
        val applied = mutableListOf<Pair<List<ReadRecord>, List<String>>>()
        val replaced = mutableListOf<String>()
        val summarised = mutableListOf<Set<Long>>()

        override suspend fun bookmark(kind: HealthKind) = bookmarks[kind]
        override suspend fun saveBookmark(bookmark: HealthSyncEntity) {
            bookmarks[HealthKind.parse(bookmark.kind)!!] = bookmark
        }
        override suspend fun apply(records: List<ReadRecord>, deletedIds: List<String>): Set<Long> {
            applied += records to deletedIds
            return records.filterIsInstance<ReadRecord.Reading>()
                .flatMap { r -> r.samples.map { it.startMillis / DAY } }.toSet()
        }
        override suspend fun replaceWindow(kind: HealthKind, fromMillis: Long, toMillis: Long, records: List<ReadRecord>): Set<Long> {
            replaced += "$kind $fromMillis..$toMillis"
            return emptySet()
        }
        override suspend fun summarise(days: Set<Long>, totals: Map<Long, DayTotals>, nowMillis: Long) {
            summarised += days
        }
    }

    private class RecordingProblems : ProblemLog {
        val logged = mutableListOf<Problem>()
        override fun recent() = logged.toList()
        override fun record(kind: String, detail: String) { logged += Problem(0, kind, detail) }
        override fun clear() = logged.clear()
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
