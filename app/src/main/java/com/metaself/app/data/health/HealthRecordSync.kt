package com.metaself.app.data.health

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.time.Now
import com.metaself.app.domain.health.HealthKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copying the health record, once per open and on refresh (D67). Every decision lives here; the
 * reading and the writing are behind [HealthSource] and [HealthStore].
 *
 * One open runs in two phases:
 * - **A.** Every granted kind, in [HealthKind] order, takes a changes token if it has none, or drains
 *   the changes since its token. Token-taking always happens for every granted kind; draining stops
 *   once the budget is spent.
 * - **B.** Kinds still catching up take one week each in turn, newest first, until all are done or
 *   the budget is spent, so no kind waits behind another's history.
 *
 * Days touched are summarised after phase A and after each turn of phase B, with totals asked over
 * runs of at most [TOTALS_DAYS] days that cover them, one call per run (uncounted). **Accepted:** a
 * crash between a saved bookmark and its summarise leaves that day's summary stale until the day
 * changes again.
 *
 * **Accepted:** the count of empty weeks restarts each open, so a history spread thinly across opens
 * may not stop by that rule; it ends by [FURTHEST_DAYS] at worst.
 *
 * **Nothing here throws upwards (D8).** A failure — an exception or an error from the client — is a
 * problem-log entry, and the next open carries on from the last bookmark saved. Log dates are in the
 * phone's own zone.
 */
@Singleton
class HealthRecordSync(
    private val source: HealthSource,
    private val store: HealthStore,
    private val problems: ProblemLog,
    private val now: Now,
    private val zone: () -> ZoneId,
) : HealthRecordCopier {

    @Inject
    constructor(source: HealthSource, store: HealthStore, problems: ProblemLog, now: Now) :
        this(source, store, problems, now, { ZoneId.systemDefault() })

    private val running = Mutex()

    override suspend fun copyNow() {
        if (!running.tryLock()) return
        try {
            copy()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            note("copying stopped: ${failure::class.java.simpleName} ${failure.message}")
        } finally {
            running.unlock()
        }
    }

    private suspend fun copy() {
        val nowMillis = now()
        val granted = source.grantedKinds()
        if (granted.isEmpty()) return

        val budget = Budget(READS_PER_OPEN)
        val touched = mutableSetOf<Long>()
        val catching = mutableListOf<CatchUp>()

        for (kind in HealthKind.entries.filter { it in granted }) {
            try {
                val mark = bringUp(kind, nowMillis, budget, touched)
                if (!mark.catchUpDone) catching += CatchUp(kind, mark)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                note("$kind: ${failure::class.java.simpleName} ${failure.message}")
            }
        }
        summarise(touched, nowMillis)

        while (catching.isNotEmpty() && !budget.spent) {
            for (turn in catching.toList()) {
                if (!budget.take()) break
                try {
                    slice(turn, nowMillis, touched)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    note("${turn.kind}: ${failure::class.java.simpleName} ${failure.message}")
                    turn.finished = true
                }
                if (turn.finished) catching -= turn
            }
            summarise(touched, nowMillis)
        }
    }

    /** Phase A for one kind: a token if it has none (uncounted), otherwise its changes (counted). */
    private suspend fun bringUp(
        kind: HealthKind,
        nowMillis: Long,
        budget: Budget,
        touched: MutableSet<Long>,
    ): HealthSyncEntity {
        val mark = store.bookmark(kind)
        if (mark?.changesToken != null) return drainChanges(kind, mark, nowMillis, budget, touched)

        val fresh = HealthSyncEntity(
            kind = kind.name,
            changesToken = source.changesToken(kind),
            tokenAtMillis = nowMillis,
            catchUpCursorMillis = mark?.catchUpCursorMillis ?: nowMillis,
            catchUpDone = mark?.catchUpDone ?: false,
        )
        store.saveBookmark(fresh)
        return fresh
    }

    /**
     * An expired token takes its fresh token FIRST, then re-reads the window: a change made during the
     * re-read is then after the new token, not lost between the two. The bookmark is saved last.
     */
    private suspend fun drainChanges(
        kind: HealthKind,
        start: HealthSyncEntity,
        nowMillis: Long,
        budget: Budget,
        touched: MutableSet<Long>,
    ): HealthSyncEntity {
        var mark = start
        while (budget.take()) {
            val page = source.changes(kind, mark.changesToken!!)
            if (page.expired) {
                val token = source.changesToken(kind)
                val from = nowMillis - WINDOW_DAYS * DAY
                touched += store.replaceWindow(kind, from, nowMillis, source.readWindow(kind, from, nowMillis))
                mark = mark.copy(changesToken = token, tokenAtMillis = nowMillis)
                store.saveBookmark(mark)
                return mark
            }
            touched += store.apply(page.upserts, page.deletedIds)
            mark = mark.copy(changesToken = page.nextToken, tokenAtMillis = nowMillis)
            store.saveBookmark(mark)
            if (!page.hasMore) break
        }
        return mark
    }

    private class CatchUp(val kind: HealthKind, var mark: HealthSyncEntity) {
        var emptyInARow = 0
        var finished = false
    }

    /**
     * One week of catch-up for one kind. A refusal of a week older than [WINDOW_DAYS] is the history
     * limit (rule 5) unless the failure is [transient]; any other refusal stops the kind for this open
     * and keeps its cursor. The end of a catch-up is logged with its date either way, so the history
     * limit is visible whether Health Connect refuses or returns nothing: after empty weeks, the date
     * is where the empty run began.
     */
    private suspend fun slice(turn: CatchUp, nowMillis: Long, touched: MutableSet<Long>) {
        val kind = turn.kind
        val to = turn.mark.catchUpCursorMillis ?: nowMillis
        val from = to - SLICE_DAYS * DAY
        val records = try {
            source.readWindow(kind, from, to)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (refused: Exception) {
            val reason = "${refused::class.java.simpleName} ${refused.message}"
            if (to <= nowMillis - WINDOW_DAYS * DAY && !transient(refused)) {
                note("$kind: reading before ${dateOf(to)} was refused ($reason); taken as the history limit")
                turn.mark = turn.mark.copy(catchUpDone = true)
                store.saveBookmark(turn.mark)
            } else {
                note("$kind: reading before ${dateOf(to)} failed ($reason); will try again")
            }
            turn.finished = true
            return
        }
        touched += store.apply(records, emptyList())
        turn.emptyInARow = if (records.isEmpty()) turn.emptyInARow + 1 else 0
        val emptyRun = turn.emptyInARow >= EMPTY_SLICES_TO_STOP
        val done = emptyRun || from <= nowMillis - FURTHEST_DAYS * DAY
        turn.mark = turn.mark.copy(catchUpCursorMillis = from, catchUpDone = done)
        store.saveBookmark(turn.mark)
        if (done) {
            val nothingBefore = if (emptyRun) from + turn.emptyInARow * SLICE_DAYS * DAY else from
            note("$kind: nothing before ${dateOf(nothingBefore)}; catch-up finished")
            turn.finished = true
        }
    }

    /**
     * Summarises the days touched since the last summarise. They are cut into runs, each starting at a
     * touched day and reaching at most [TOTALS_DAYS] days on, and each run gets its own totals call, so
     * a scattered set of days is never one call over months. A run whose call throws is summarised with
     * every metric failed, which keeps its stored totals; the other runs are not affected.
     */
    private suspend fun summarise(touched: MutableSet<Long>, nowMillis: Long) {
        if (touched.isEmpty()) return
        val days = touched.sorted()
        touched.clear()
        for (run in runsOf(days)) {
            val totals = try {
                source.dayTotals(run.first(), run.last())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                note("totals: ${failure::class.java.simpleName} ${failure.message}")
                TotalsResult.ALL_FAILED
            }
            store.summarise(run.toSet(), totals, nowMillis)
        }
    }

    private fun runsOf(sortedDays: List<Long>): List<List<Long>> {
        val runs = mutableListOf<MutableList<Long>>()
        for (day in sortedDays) {
            val current = runs.lastOrNull()
            if (current != null && day < current.first() + TOTALS_DAYS) current += day else runs += mutableListOf(day)
        }
        return runs
    }

    /**
     * A failure that says "not now" rather than "never": an I/O failure, a [SecurityException] (Health
     * Connect throws this for reads made while the app is in the background rather than for a
     * genuinely refused permission; treating it as final would end a catch-up over nothing — the
     * history limit still shows up as empty weeks, which the "catch-up finished" line records), the
     * binder's RemoteException or a subclass of it (matched by name, so pure tests need no Android
     * class), or an IllegalStateException whose message says "rate limit", "rate-limit", "ratelimit" or
     * "quota" in any case — not merely "rate", which is inside ordinary words.
     */
    private fun transient(failure: Exception): Boolean =
        failure is IOException ||
            failure is SecurityException ||
            generateSequence<Class<*>>(failure.javaClass) { it.superclass }.any { it.name == REMOTE_EXCEPTION } ||
            (failure is IllegalStateException && failure.message.let { message ->
                message != null && RATE_LIMITED.any { message.contains(it, ignoreCase = true) }
            })

    /**
     * The problem log writes a file (D8); `copyNow` is called from the caller's own dispatcher — Main,
     * in the app — so the write is moved off it here rather than left to block the UI thread.
     */
    private suspend fun note(detail: String) = withContext(Dispatchers.IO) {
        problems.record(kind = "health", detail = detail)
    }

    private fun dateOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate()

    private class Budget(private var left: Int) {
        val spent: Boolean get() = left <= 0
        fun take(): Boolean = if (left > 0) { left--; true } else false
    }

    companion object {
        private const val DAY = 86_400_000L
        private const val REMOTE_EXCEPTION = "android.os.RemoteException"
        private val RATE_LIMITED = listOf("rate limit", "rate-limit", "ratelimit", "quota")

        /** A choice: a totals call covers at most this many consecutive days. */
        const val TOTALS_DAYS = 30L

        /** A choice: a week is a readable slice for any kind. */
        const val SLICE_DAYS = 7L

        /** A choice, matching the documented 30-day life of a changes token; not a measured quota. */
        const val WINDOW_DAYS = 30L

        /** A choice: eight empty weeks in a row means the record has begun. */
        const val EMPTY_SLICES_TO_STOP = 8

        /** A choice: two years bounds the catch-up on a phone that has nothing. */
        const val FURTHEST_DAYS = 730L

        /**
         * A choice that keeps one open cheap. Health Connect's real quota is not measured here.
         *
         * Counted: each page of changes and each catch-up week. **Not counted:** taking a token, the
         * totals calls (one per run of up to [TOTALS_DAYS] touched days), the re-read of the window after an expired token, and each workout's two
         * aggregate calls made while it is read.
         */
        const val READS_PER_OPEN = 60
    }
}
