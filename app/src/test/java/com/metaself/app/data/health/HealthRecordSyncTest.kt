package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.Problem
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.health.HealthKind
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The copying rules (D67), with no Health Connect and no database. Every figure is invented; "now" is
 * day 100 at midnight UTC, so windows are easy to read.
 */
class HealthRecordSyncTest {

    private val now = 100 * DAY
    private val source = FakeSource()
    private val store = FakeStore()
    private val problems = RecordingProblems()
    private val sync = HealthRecordSync(source, store, problems, now = { now })

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

        assertThat(source.calls.count { !it.startsWith("token") }).isAtMost(HealthRecordSync.READS_PER_OPEN)
    }

    @Test
    fun `touched days are summarised once, with totals asked for their whole range`() = runTest {
        source.granted = setOf(HealthKind.STEPS, HealthKind.HEART_RATE)
        store.bookmarks[HealthKind.STEPS] = done("a")
        store.bookmarks[HealthKind.HEART_RATE] = done("b", HealthKind.HEART_RATE)
        source.pages = mutableListOf(
            ChangesPage(listOf(steps("st-1", 97 * DAY)), emptyList(), "a2", false, false),
            ChangesPage(listOf(steps("st-2", 99 * DAY)), emptyList(), "b2", false, false),
        )

        sync.copyNow()

        assertThat(source.calls.filter { it.startsWith("totals") }).containsExactly("totals 97..99")
        assertThat(store.summarised).containsExactly(setOf(97L, 99L))
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
        var changesThrow = false
        private var tokens = 0

        override suspend fun grantedKinds() = granted
        override suspend fun changesToken(kind: HealthKind): String {
            calls += "token $kind"
            return "t-$kind-${++tokens}"
        }
        override suspend fun changes(kind: HealthKind, token: String): ChangesPage {
            calls += "changes $kind $token"
            if (changesThrow) throw IllegalStateException("unavailable")
            return pages.removeFirst()
        }
        override suspend fun readWindow(kind: HealthKind, fromMillis: Long, toMillis: Long): List<ReadRecord> {
            calls += "window $kind $fromMillis..$toMillis"
            refuseBefore?.let { if (toMillis <= it) throw SecurityException("refused") }
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
