package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.Problem
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.lifecycle.FakeAppForeground
import com.metaself.app.domain.health.HealthKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
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
    private val foreground = FakeAppForeground()
    private val sync = HealthRecordSync(source, store, problems, now = { now }, zone = { ZoneOffset.UTC }, foreground = foreground)

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
        source.refusal = { IllegalArgumentException("refused") }

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

    /** Rule 5: Health Connect throws this for a read made while the app is in the background. */
    @Test
    fun `a security exception on an old read is transient too`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.refuseBefore = 65 * DAY
        source.refusal = { SecurityException("not allowed in background") }

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

    /** "rate" inside another word is not a rate limit: an old refusal saying so is the history limit. */
    @Test
    fun `a failure that only contains the letters of rate is not transient`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.refuseBefore = 65 * DAY
        source.refusal = { IllegalStateException("inaccurate range refused") }

        sync.copyNow()

        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpDone).isTrue()
    }

    @Test
    fun `a quota message on an old read is transient`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.refuseBefore = 65 * DAY
        source.refusal = { IllegalStateException("API QUOTA exceeded") }

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

    /** M9: totals are asked thirty days at most at a time, each run of days summarised with its own. */
    @Test
    fun `totals are asked in runs of at most thirty days`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        store.bookmarks[HealthKind.STEPS] = done("a")
        source.pages = mutableListOf(
            ChangesPage(
                listOf(steps("st-1", 10 * DAY), steps("st-2", 39 * DAY), steps("st-3", 40 * DAY), steps("st-4", 99 * DAY)),
                emptyList(), "a2", false, false,
            ),
        )

        sync.copyNow()

        assertThat(source.calls.filter { it.startsWith("totals") })
            .containsExactly("totals 10..39", "totals 40..40", "totals 99..99").inOrder()
        assertThat(store.summarised).containsExactly(setOf(10L, 39L), setOf(40L), setOf(99L)).inOrder()
    }

    /** M9, I2: a totals call that throws fails every metric for its own days, and only those. */
    @Test
    fun `a totals call that fails marks only its own days as failed`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        store.bookmarks[HealthKind.STEPS] = done("a")
        source.pages = mutableListOf(
            ChangesPage(listOf(steps("st-1", 10 * DAY), steps("st-2", 99 * DAY)), emptyList(), "a2", false, false),
        )
        source.totalsThrowFrom = 50

        sync.copyNow()

        assertThat(store.totalsGiven.map { it.failed }).containsExactly(
            emptySet<TotalMetric>(),
            TotalMetric.entries.toSet(),
        ).inOrder()
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

    // --- Older history (D72) --------------------------------------------------------------------

    /**
     * A finished catch-up is re-opened once older history is allowed. Its cursor is kept when it is
     * within the last [HealthRecordSync.WINDOW_DAYS] days, and otherwise brought back to that edge:
     * without the permission, weeks past the limit may have read as empty, so the cursor can sit weeks
     * beyond the last week that was really read.
     */
    @Test
    fun `older history allowed for the first time re-opens finished catch-ups`() = runTest {
        source.granted = setOf(HealthKind.STEPS, HealthKind.HEART_RATE)
        source.history = true
        store.bookmarks[HealthKind.STEPS] = done("t-s").copy(catchUpCursorMillis = 44 * DAY)
        store.bookmarks[HealthKind.HEART_RATE] = done("t-h", HealthKind.HEART_RATE).copy(catchUpCursorMillis = 80 * DAY)

        sync.copyNow()

        val windows = source.calls.filter { it.startsWith("window") }
        assertThat(windows).contains("window STEPS ${63 * DAY}..${70 * DAY}")
        assertThat(windows).contains("window HEART_RATE ${73 * DAY}..${80 * DAY}")
        assertThat(windows.first { it.startsWith("window STEPS") }).isEqualTo("window STEPS ${63 * DAY}..${70 * DAY}")
        assertThat(windows.first { it.startsWith("window HEART_RATE") })
            .isEqualTo("window HEART_RATE ${73 * DAY}..${80 * DAY}")
        assertThat(store.historyMarked).isTrue()
    }

    @Test
    fun `a catch-up still running past the limit is brought back to it too`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.history = true
        store.bookmarks[HealthKind.STEPS] = done("t").copy(catchUpCursorMillis = 50 * DAY, catchUpDone = false)

        sync.copyNow()

        assertThat(source.calls.first { it.startsWith("window") }).isEqualTo("window STEPS ${63 * DAY}..${70 * DAY}")
    }

    /**
     * A cursor already at two years back is re-opened too, not left finished: before the permission, a
     * catch-up could have walked all the way there on empty weeks alone, so being at the bound proves
     * nothing about what is really there.
     */
    @Test
    fun `a catch-up that reached two years back is re-opened at the thirty-day edge too`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.history = true
        store.bookmarks[HealthKind.STEPS] = done("t").copy(catchUpCursorMillis = -630 * DAY)

        sync.copyNow()

        assertThat(source.calls.first { it.startsWith("window") }).isEqualTo("window STEPS ${63 * DAY}..${70 * DAY}")
        assertThat(store.historyMarked).isTrue()
    }

    @Test
    fun `older history is acted on once, not on every open`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.history = true
        store.historyMarked = true
        store.bookmarks[HealthKind.STEPS] = done("t").copy(catchUpCursorMillis = 44 * DAY)

        sync.copyNow()

        assertThat(source.calls.filter { it.startsWith("window") }).isEmpty()
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpDone).isTrue()
        assertThat(source.historyAsked).isEqualTo(0)
    }

    @Test
    fun `older history not allowed changes nothing`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.history = false
        store.bookmarks[HealthKind.STEPS] = done("t").copy(catchUpCursorMillis = 44 * DAY)

        sync.copyNow()

        assertThat(source.calls.filter { it.startsWith("window") }).isEmpty()
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpDone).isTrue()
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpCursorMillis).isEqualTo(44 * DAY)
        assertThat(store.historyMarked).isFalse()
    }

    /** A second open after the first: the catch-up carries on from its own cursor, not the edge again. */
    @Test
    fun `after the re-open the catch-up carries on from where it got to`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.history = true
        store.bookmarks[HealthKind.STEPS] = done("t").copy(catchUpCursorMillis = 44 * DAY)
        source.windowRecords = { from, _ -> listOf(steps("st-$from", from)) }
        sync.copyNow()
        val reached = store.bookmarks.getValue(HealthKind.STEPS).catchUpCursorMillis!!
        source.calls.clear()

        sync.copyNow()

        assertThat(source.calls.first { it.startsWith("window") })
            .isEqualTo("window STEPS ${reached - 7 * DAY}..$reached")
    }

    @Test
    fun `a failure while re-opening is logged, and the marker is not written`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.history = true
        store.bookmarks[HealthKind.STEPS] = done("t").copy(catchUpCursorMillis = 44 * DAY)
        store.markThrows = true

        sync.copyNow()

        assertThat(store.historyMarked).isFalse()
        assertThat(problems.logged.map { it.detail }.any { it.contains("older history") }).isTrue()
    }

    // --- A workout's missing distance, asked again (D81's investigation) ---------------------------

    /**
     * A session read before its app wrote the distance of that stretch had none, and was never asked
     * again. Now a synced session missing a figure, on a day that has just received that figure's
     * readings, is asked again.
     */
    @Test
    fun `a session missing its distance on a day distance just arrived is asked for it again, and it is stored`() = runTest {
        grantedAllDone(HealthKind.DISTANCE, HealthKind.ACTIVE_KCAL, HealthKind.EXERCISE)
        source.pages = mutableListOf(page(reading(HealthKind.DISTANCE, "d-1", 99 * DAY)))
        store.gaps = listOf(gap(7, 99, needsDistance = true, needsEnergy = true))
        source.sessionTotals = { _, _ -> SessionTotals(distanceM = 5_000) }

        sync.copyNow()

        assertThat(store.gapsAsked.single()).containsExactly(98L, 99L)
        assertThat(sessionCalls()).containsExactly("session ${99 * DAY}..${99 * DAY + 30 * MINUTE} distance=true energy=false")
        assertThat(store.filled).containsExactly(7L to SessionTotals(distanceM = 5_000))
    }

    @Test
    fun `a session missing its calories on a day calories just arrived is asked for calories only`() = runTest {
        grantedAllDone(HealthKind.DISTANCE, HealthKind.ACTIVE_KCAL, HealthKind.EXERCISE)
        // Distance drains first (HealthKind's order) and gets the empty page; calories get the second.
        source.pages = mutableListOf(page(), page(reading(HealthKind.ACTIVE_KCAL, "k-1", 99 * DAY)))
        store.gaps = listOf(gap(7, 99, needsDistance = true, needsEnergy = true))

        sync.copyNow()

        assertThat(sessionCalls()).containsExactly("session ${99 * DAY}..${99 * DAY + 30 * MINUTE} distance=false energy=true")
    }

    /** A session filed the day before, running past midnight, may have its distance filed on this day. */
    @Test
    fun `a session on the day before a day distance arrived is asked too, and one two days before is not`() = runTest {
        grantedAllDone(HealthKind.DISTANCE, HealthKind.EXERCISE)
        source.pages = mutableListOf(page(reading(HealthKind.DISTANCE, "d-1", 99 * DAY)))
        store.gaps = listOf(gap(7, 98, needsDistance = true), gap(8, 97, needsDistance = true))

        sync.copyNow()

        assertThat(sessionCalls()).containsExactly("session ${98 * DAY}..${98 * DAY + 30 * MINUTE} distance=true energy=false")
    }

    /** Only distance or calories arriving can change a session's missing figures; steps cannot. */
    @Test
    fun `days that received only other kinds are not looked at`() = runTest {
        grantedAllDone(HealthKind.STEPS, HealthKind.DISTANCE, HealthKind.EXERCISE)
        source.pages = mutableListOf(page(reading(HealthKind.STEPS, "st-1", 99 * DAY)))
        store.gaps = listOf(gap(7, 99, needsDistance = true))

        sync.copyNow()

        assertThat(store.gapsAsked).isEmpty()
        assertThat(sessionCalls()).isEmpty()
    }

    @Test
    fun `nothing is asked when no session is missing anything`() = runTest {
        grantedAllDone(HealthKind.DISTANCE, HealthKind.EXERCISE)
        source.pages = mutableListOf(page(reading(HealthKind.DISTANCE, "d-1", 99 * DAY)))

        sync.copyNow()

        assertThat(store.gapsAsked).hasSize(1)
        assertThat(sessionCalls()).isEmpty()
        assertThat(store.filled).isEmpty()
    }

    @Test
    fun `a figure the session already has is not asked for, though its readings arrived`() = runTest {
        grantedAllDone(HealthKind.DISTANCE, HealthKind.ACTIVE_KCAL, HealthKind.EXERCISE)
        source.pages = mutableListOf(
            page(reading(HealthKind.DISTANCE, "d-1", 99 * DAY)),
            page(reading(HealthKind.ACTIVE_KCAL, "k-1", 99 * DAY)),
        )
        store.gaps = listOf(
            gap(7, 99, needsDistance = false, needsEnergy = true),
            gap(8, 99, startMinute = 60, needsDistance = true, needsEnergy = false),
        )

        sync.copyNow()

        assertThat(sessionCalls()).containsExactly(
            "session ${99 * DAY + 60 * MINUTE}..${99 * DAY + 90 * MINUTE} distance=true energy=false",
            "session ${99 * DAY}..${99 * DAY + 30 * MINUTE} distance=false energy=true",
        ).inOrder()
    }

    @Test
    fun `without workouts allowed, no session is looked for`() = runTest {
        grantedAllDone(HealthKind.DISTANCE)
        source.pages = mutableListOf(page(reading(HealthKind.DISTANCE, "d-1", 99 * DAY)))

        sync.copyNow()

        assertThat(store.gapsAsked).isEmpty()
    }

    /** An expired token's re-read of the window touches every day it holds; that is not an arrival. */
    @Test
    fun `days only an expired token's re-read touched are not asked again`() = runTest {
        grantedAllDone(HealthKind.DISTANCE, HealthKind.EXERCISE)
        source.pages = mutableListOf(ChangesPage(emptyList(), emptyList(), "x", hasMore = false, expired = true))
        store.replacedDays = setOf(95L, 99L)
        store.gaps = listOf(gap(7, 99, needsDistance = true))

        sync.copyNow()

        assertThat(store.replaced).isNotEmpty()
        assertThat(store.gapsAsked).isEmpty()
        assertThat(sessionCalls()).isEmpty()
    }

    @Test
    fun `an ask that fails is logged, and the next session is still asked`() = runTest {
        grantedAllDone(HealthKind.DISTANCE, HealthKind.EXERCISE)
        source.pages = mutableListOf(page(reading(HealthKind.DISTANCE, "d-1", 99 * DAY)))
        store.gaps = listOf(gap(7, 99, needsDistance = true), gap(8, 99, startMinute = 60, needsDistance = true))
        source.sessionTotals = { start, _ ->
            if (start == 99 * DAY + 60 * MINUTE) throw IllegalStateException("unavailable") else SessionTotals(distanceM = 3_000)
        }

        sync.copyNow()

        assertThat(store.filled).containsExactly(7L to SessionTotals(distanceM = 3_000))
        assertThat(problems.logged.map { it.detail }).contains("workout totals: IllegalStateException unavailable")
    }

    /** Leaving the screen stops the copying; asking again must not swallow that (D8 is for failures). */
    @Test
    fun `a cancellation while asking again is not logged and stops the copying`() = runTest {
        grantedAllDone(HealthKind.DISTANCE, HealthKind.EXERCISE)
        source.pages = mutableListOf(page(reading(HealthKind.DISTANCE, "d-1", 99 * DAY)))
        store.gaps = listOf(gap(7, 99, needsDistance = true), gap(8, 99, startMinute = 60, needsDistance = true))
        source.sessionTotals = { _, _ -> throw CancellationException("left") }

        val outcome = runCatching { sync.copyNow() }

        assertThat(outcome.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        assertThat(sessionCalls()).hasSize(1)
        assertThat(store.summarised).isEmpty()
        assertThat(problems.logged).isEmpty()
    }

    /** The cap is a choice ([HealthRecordSync.SESSION_ASKS_PER_OPEN]); the newest sessions go first. */
    @Test
    fun `at most the cap of sessions are asked in one open, newest first, and the rest are logged`() = runTest {
        grantedAllDone(HealthKind.DISTANCE, HealthKind.EXERCISE)
        source.pages = mutableListOf(page(reading(HealthKind.DISTANCE, "d-1", 99 * DAY)))
        val cap = HealthRecordSync.SESSION_ASKS_PER_OPEN
        store.gaps = (0 until cap + 2).map { n -> gap(n.toLong(), 99, startMinute = n * 40L, needsDistance = true) }

        sync.copyNow()

        val asked = sessionCalls()
        assertThat(asked).hasSize(cap)
        assertThat(asked.first()).startsWith("session ${99 * DAY + (cap + 1) * 40 * MINUTE}..")
        assertThat(asked.joinToString()).doesNotContain("session ${99 * DAY}..")
        assertThat(problems.logged.map { it.detail }).contains("workout totals: 2 more not asked this open")
    }

    /** Catch-up weeks were read just now, sessions and all; they are not asked twice in one open. */
    @Test
    fun `days only a catch-up week touched are not asked again`() = runTest {
        source.granted = setOf(HealthKind.DISTANCE, HealthKind.EXERCISE)
        store.bookmarks[HealthKind.DISTANCE] = HealthSyncEntity(
            kind = "DISTANCE", changesToken = "a", tokenAtMillis = 0, catchUpCursorMillis = 100 * DAY, catchUpDone = false,
        )
        store.bookmarks[HealthKind.EXERCISE] = done("e", HealthKind.EXERCISE)
        source.windowRecords = { from, _ -> if (from == 93 * DAY) listOf(reading(HealthKind.DISTANCE, "d-1", 95 * DAY)) else emptyList() }
        store.gaps = listOf(gap(7, 95, needsDistance = true))

        sync.copyNow()

        assertThat(store.gapsAsked).isEmpty()
        assertThat(sessionCalls()).isEmpty()
    }

    // --- The app in the background. Health Connect refuses reads from an app that is not in the
    // foreground (without READ_HEALTH_DATA_IN_BACKGROUND): that is not a failure, so nothing is logged,
    // the pass stops, and what the refusal may have left behind is looked at again next time. ---

    @Test
    fun `a copy asked for in the background reads nothing`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        foreground.isForeground.value = false

        sync.copyNow()

        assertThat(source.grantedAsked).isEqualTo(0)
        assertThat(source.calls).isEmpty()
    }

    @Test
    fun `a background refusal stops the pass quietly and keeps the last saved bookmark`() = runTest {
        grantedAllDone(HealthKind.STEPS, HealthKind.HEART_RATE)
        source.pages = mutableListOf(
            ChangesPage(listOf(steps("st-1", 99 * DAY)), emptyList(), "t-2", hasMore = true, expired = false),
        )
        source.refuseInBackground = { it == "changes STEPS t-2" }

        sync.copyNow()

        assertThat(problems.logged).isEmpty()
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).changesToken).isEqualTo("t-2")
        assertThat(source.calls).containsExactly("changes STEPS t-STEPS", "changes STEPS t-2").inOrder()
        assertThat(store.recheckDue).isTrue()
    }

    @Test
    fun `a catch-up week refused in the background saves no cursor and is not the history limit`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.refuseInBackground = { it == "window STEPS ${86 * DAY}..${93 * DAY}" }

        sync.copyNow()

        assertThat(problems.logged).isEmpty()
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpCursorMillis).isEqualTo(93 * DAY)
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpDone).isFalse()
    }

    @Test
    fun `totals refused in the background are not logged and summarise nothing`() = runTest {
        grantedAllDone(HealthKind.STEPS)
        source.pages = mutableListOf(page(steps("st-1", 99 * DAY)))
        source.refuseInBackground = { it.startsWith("totals") }

        sync.copyNow()

        assertThat(problems.logged).isEmpty()
        assertThat(store.summarised).isEmpty()
        assertThat(store.recheckDue).isTrue()
    }

    @Test
    fun `leaving the foreground mid-copy cancels the pass, and bookmarks stand at the last finished week`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.onWindow = { from ->
            if (from == 79 * DAY) {
                foreground.isForeground.value = false
                awaitCancellation()
            }
        }

        sync.copyNow()

        assertThat(source.calls.last()).isEqualTo("window STEPS ${79 * DAY}..${86 * DAY}")
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpCursorMillis).isEqualTo(86 * DAY)
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpDone).isFalse()
        assertThat(problems.logged).isEmpty()
        assertThat(store.recheckDue).isTrue()
    }

    // --- D99: the weekly letter's copy, for a caller that has checked the background-read permission. ---

    @Test
    fun `the background copy runs a pass even when the app is not in front`() = runTest {
        grantedAllDone(HealthKind.STEPS)
        foreground.isForeground.value = false

        sync.copyNow()
        assertThat(source.calls).isEmpty()

        val done = sync.copyInBackground()

        assertThat(done).isTrue()
        assertThat(source.calls).contains("changes STEPS t-STEPS")
        assertThat(problems.logged).isEmpty()
    }

    @Test
    fun `a background copy Health Connect refuses returns false, logs nothing, and owes a recheck`() = runTest {
        grantedAllDone(HealthKind.STEPS)
        foreground.isForeground.value = false
        source.refuseInBackground = { it.startsWith("changes") }

        val done = sync.copyInBackground()

        assertThat(done).isFalse()
        assertThat(problems.logged).isEmpty()
        assertThat(store.recheckDue).isTrue()
    }

    @Test
    fun `a background copy that fails returns false and logs the failure by kind`() = runTest {
        source.grantedError = IllegalStateException("unavailable")

        val done = sync.copyInBackground()

        assertThat(done).isFalse()
        assertThat(problems.logged.map { it.detail }).containsExactly("copying stopped: IllegalStateException unavailable")
    }

    @Test
    fun `a background copy while another copy runs does nothing and returns false`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.gate = CompletableDeferred()
        val first = launch { sync.copyNow() }
        runCurrent()

        val done = sync.copyInBackground()

        assertThat(done).isFalse()
        assertThat(source.grantedAsked).isEqualTo(1)
        source.gate!!.complete(Unit)
        first.join()
    }

    /**
     * What a refusal may have left: workouts stored without a figure whose ask was refused, and days
     * whose totals were. The next foreground copy re-asks the gaps of the last [HealthRecordSync.WINDOW_DAYS]
     * days, within the per-open cap, and re-totals the last [HealthRecordSync.RECHECK_DAYS] days; then
     * nothing is owed.
     */
    @Test
    fun `after a refusal the next copy re-asks the month's gaps and re-totals the last days, once`() = runTest {
        grantedAllDone(HealthKind.DISTANCE, HealthKind.ACTIVE_KCAL, HealthKind.EXERCISE)
        store.recheckDue = true
        store.gaps = listOf(gap(7, 80, needsDistance = true, needsEnergy = true))
        source.sessionTotals = { _, _ -> SessionTotals(distanceM = 4_000, energyKcal = 200) }

        sync.copyNow()

        assertThat(store.gapsAsked.single()).containsExactlyElementsIn((70L..100L).toList())
        assertThat(sessionCalls()).containsExactly("session ${80 * DAY}..${80 * DAY + 30 * MINUTE} distance=true energy=true")
        assertThat(store.filled).containsExactly(7L to SessionTotals(distanceM = 4_000, energyKcal = 200))
        assertThat(store.summarised).containsExactly(setOf(98L, 99L, 100L))
        assertThat(store.recheckDue).isFalse()

        source.calls.clear()
        store.gapsAsked.clear()
        sync.copyNow()

        assertThat(sessionCalls()).isEmpty()
        assertThat(store.gapsAsked).isEmpty()
    }

    @Test
    fun `the re-ask shares the per-open cap with the ordinary asks`() = runTest {
        grantedAllDone(HealthKind.DISTANCE, HealthKind.EXERCISE)
        store.recheckDue = true
        val cap = HealthRecordSync.SESSION_ASKS_PER_OPEN
        store.gaps = (0 until cap + 3).map { n -> gap(n.toLong(), 90, startMinute = n * 40L, needsDistance = true) }

        sync.copyNow()

        assertThat(sessionCalls()).hasSize(cap)
    }

    /** The marker would otherwise be cleared with gaps still unasked, and never reached again. */
    @Test
    fun `the recheck marker stays owed when the per-open cap left gaps unasked`() = runTest {
        grantedAllDone(HealthKind.DISTANCE, HealthKind.EXERCISE)
        store.recheckDue = true
        val cap = HealthRecordSync.SESSION_ASKS_PER_OPEN
        store.gaps = (0 until cap + 1).map { n -> gap(n.toLong(), 90, startMinute = n * 40L, needsDistance = true) }

        sync.copyNow()

        assertThat(sessionCalls()).hasSize(cap)
        assertThat(store.recheckDue).isTrue()
    }

    /** The cap being exactly enough is not "limited by the cap": nothing is left unasked. */
    @Test
    fun `the recheck marker clears when the cap was not the limiting factor`() = runTest {
        grantedAllDone(HealthKind.DISTANCE, HealthKind.EXERCISE)
        store.recheckDue = true
        val cap = HealthRecordSync.SESSION_ASKS_PER_OPEN
        store.gaps = (0 until cap).map { n -> gap(n.toLong(), 90, startMinute = n * 40L, needsDistance = true) }

        sync.copyNow()

        assertThat(sessionCalls()).hasSize(cap)
        assertThat(store.recheckDue).isFalse()
    }

    /**
     * Before this fix, a [BackgroundReadRefused] from the totals call discarded the days touched so far
     * (a local variable, lost with the pass); only the last [HealthRecordSync.RECHECK_DAYS] days were
     * re-tried next open, never a catch-up slice's own day if it lay further back. Now the earliest such
     * day is kept in the marker, and the next copy re-totals from there through today.
     */
    @Test
    fun `totals refused after a catch-up slice leaves that slice's day owed, and the next copy summarises it`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.windowRecords = { from, _ -> if (from == 93 * DAY) listOf(steps("st-1", 95 * DAY)) else emptyList() }
        source.refuseInBackground = { it.startsWith("totals") }

        sync.copyNow()

        assertThat(problems.logged).isEmpty()
        assertThat(store.summarised).isEmpty()
        assertThat(store.recheckDue).isTrue()
        assertThat(store.recheckDay).isEqualTo(95L)

        source.refuseInBackground = { false }
        sync.copyNow()

        assertThat(store.summarised).containsExactly(setOf(95L, 96L, 97L, 98L, 99L, 100L))
    }

    /**
     * The watch coroutine only cancels the pass when the app leaves the foreground; an unrelated outside
     * cancellation (the caller's own scope torn down) must still reach the caller as a cancellation, not
     * be swallowed as a refusal that owes a recheck.
     */
    @Test
    fun `an outside cancellation of copyNow propagates, and is not taken for the app leaving`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        val started = CompletableDeferred<Unit>()
        source.onWindow = { started.complete(Unit); awaitCancellation() }

        val job = launch { sync.copyNow() }
        started.await()
        job.cancelAndJoin()

        assertThat(job.isCancelled).isTrue()
        assertThat(problems.logged).isEmpty()
        assertThat(store.recheckDue).isFalse()
    }

    private fun sessionCalls() = source.calls.filter { it.startsWith("session") }

    private fun page(vararg records: ReadRecord) = ChangesPage(records.toList(), emptyList(), "next", hasMore = false, expired = false)

    private fun reading(kind: HealthKind, id: String, at: Long) = ReadRecord.Reading(
        kind, "com.example.band", id, listOf(Sample(at, at + 60_000, 100.0)),
    )

    /** A session on [day], starting [startMinute] minutes into it and lasting 30. */
    private fun gap(id: Long, day: Long, startMinute: Long = 0, needsDistance: Boolean = false, needsEnergy: Boolean = false) =
        SessionGap(
            id = id,
            epochDay = day,
            startMillis = day * DAY + startMinute * MINUTE,
            endMillis = day * DAY + (startMinute + 30) * MINUTE,
            needsDistance = needsDistance,
            needsEnergy = needsEnergy,
        )

    private fun grantedAllDone(vararg kinds: HealthKind) {
        source.granted = kinds.toSet()
        kinds.forEach { store.bookmarks[it] = done("t-$it", it) }
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
        var totalsThrowFrom: Long? = null
        var history = false
        var historyAsked = 0

        /** Which calls Health Connect refuses as made from the background (matched on the call's line). */
        var refuseInBackground: (String) -> Boolean = { false }

        /** Runs inside each window read, after it is recorded: a place to suspend or flip foreground. */
        var onWindow: suspend (Long) -> Unit = {}

        private fun call(line: String) {
            calls += line
            if (refuseInBackground(line)) throw BackgroundReadRefused(SecurityException("Must be in foreground to read data"))
        }
        private var tokens = 0

        override suspend fun historyAvailable(): Boolean = true
        override suspend fun historyGranted(available: Boolean): Boolean {
            historyAsked++
            return available && history
        }

        override suspend fun grantedKinds(): Set<HealthKind> {
            grantedAsked++
            gate?.await()
            grantedError?.let { throw it }
            return granted
        }
        override suspend fun changesToken(kind: HealthKind): String {
            call("token $kind")
            return "t-$kind-${++tokens}"
        }
        override suspend fun changes(kind: HealthKind, token: String): ChangesPage {
            call("changes $kind $token")
            if (changesThrow) throw IllegalStateException("unavailable")
            return pages.removeFirstOrNull() ?: ChangesPage(emptyList(), emptyList(), token, hasMore = false, expired = false)
        }
        override suspend fun readWindow(kind: HealthKind, fromMillis: Long, toMillis: Long): List<ReadRecord> {
            call("window $kind $fromMillis..$toMillis")
            onWindow(fromMillis)
            refuseBefore?.let { if (toMillis <= it) throw refusal() }
            return windowRecords(fromMillis, toMillis)
        }
        var sessionTotals: (Long, Long) -> SessionTotals = { _, _ -> SessionTotals() }
        override suspend fun sessionTotals(startMillis: Long, endMillis: Long, distance: Boolean, energy: Boolean): SessionTotals {
            call("session $startMillis..$endMillis distance=$distance energy=$energy")
            return sessionTotals(startMillis, endMillis)
        }
        override suspend fun dayTotals(fromDay: Long, toDay: Long): TotalsResult {
            call("totals $fromDay..$toDay")
            totalsThrowFrom?.let { if (fromDay >= it) throw IllegalStateException("unavailable") }
            return TotalsResult()
        }
    }

    private class FakeStore : HealthStore {
        val bookmarks = mutableMapOf<HealthKind, HealthSyncEntity>()
        val applied = mutableListOf<Pair<List<ReadRecord>, List<String>>>()
        val replaced = mutableListOf<String>()
        val summarised = mutableListOf<Set<Long>>()
        val totalsGiven = mutableListOf<TotalsResult>()
        var historyMarked = false
        var markThrows = false

        override suspend fun bookmark(kind: HealthKind) = bookmarks[kind]
        override suspend fun historyActedOn() = historyMarked
        override suspend fun markHistoryActedOn() {
            if (markThrows) throw IllegalStateException("disk full")
            historyMarked = true
        }
        override suspend fun saveBookmark(bookmark: HealthSyncEntity) {
            bookmarks[HealthKind.parse(bookmark.kind)!!] = bookmark
        }
        override suspend fun apply(records: List<ReadRecord>, deletedIds: List<String>): Set<Long> {
            applied += records to deletedIds
            return records.filterIsInstance<ReadRecord.Reading>()
                .flatMap { r -> r.samples.map { it.startMillis / DAY } }.toSet()
        }
        var replacedDays = emptySet<Long>()
        override suspend fun replaceWindow(kind: HealthKind, fromMillis: Long, toMillis: Long, records: List<ReadRecord>): Set<Long> {
            replaced += "$kind $fromMillis..$toMillis"
            return replacedDays
        }
        override suspend fun summarise(days: Set<Long>, totals: TotalsResult, nowMillis: Long) {
            summarised += days
            totalsGiven += totals
        }
        var gaps = emptyList<SessionGap>()
        val gapsAsked = mutableListOf<Set<Long>>()
        val filled = mutableListOf<Pair<Long, SessionTotals>>()
        override suspend fun sessionGaps(days: Set<Long>): List<SessionGap> {
            gapsAsked += days
            return gaps
        }
        override suspend fun fillSessionTotals(id: Long, totals: SessionTotals) {
            filled += id to totals
        }
        var recheckDue = false
        var recheckDay: Long? = null
        override suspend fun recentRecheckDue() = recheckDue
        override suspend fun recheckFromDay() = recheckDay
        override suspend fun setRecentRecheckDue(due: Boolean, fromDay: Long?) {
            recheckDue = due
            recheckDay = if (due) fromDay else null
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
        const val MINUTE = 60_000L
    }
}
