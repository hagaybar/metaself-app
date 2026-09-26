package com.metaself.app.data.health

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.time.Now
import com.metaself.app.domain.health.HealthKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copying the health record, once per open and on refresh (D67). Every decision lives here; the
 * reading and the writing are behind [HealthSource] and [HealthStore].
 *
 * **Nothing here throws upwards (D8).** A failure is a problem-log entry and the next open carries on
 * from the last bookmark saved.
 */
@Singleton
class HealthRecordSync @Inject constructor(
    private val source: HealthSource,
    private val store: HealthStore,
    private val problems: ProblemLog,
    private val now: Now,
) : HealthRecordCopier {

    private val running = Mutex()

    override suspend fun copyNow() {
        if (!running.tryLock()) return
        try {
            copy()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
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
        for (kind in HealthKind.entries.filter { it in granted }) {
            if (budget.spent) break
            try {
                touched += copyKind(kind, nowMillis, budget)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                note("$kind: ${failure::class.java.simpleName} ${failure.message}")
            }
        }
        if (touched.isEmpty()) return

        // Always asked for, outside the budget: a day summarised without its totals would lose them.
        val totals = runCatching { source.dayTotals(touched.min(), touched.max()) }
            .onFailure { note("totals: ${it::class.java.simpleName} ${it.message}") }
            .getOrDefault(emptyMap())
        store.summarise(touched, totals, nowMillis)
    }

    private suspend fun copyKind(kind: HealthKind, nowMillis: Long, budget: Budget): Set<Long> {
        val touched = mutableSetOf<Long>()
        var mark = store.bookmark(kind)

        if (mark?.changesToken == null) {
            // Token first: whatever is written while the catch-up runs is in the next changes read.
            mark = HealthSyncEntity(
                kind = kind.name,
                changesToken = source.changesToken(kind),
                tokenAtMillis = nowMillis,
                catchUpCursorMillis = mark?.catchUpCursorMillis ?: nowMillis,
                catchUpDone = mark?.catchUpDone ?: false,
            )
            store.saveBookmark(mark)
        } else {
            mark = drainChanges(kind, mark, nowMillis, budget, touched)
        }

        if (!mark.catchUpDone) catchUp(kind, mark, nowMillis, budget, touched)
        return touched
    }

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
                val from = nowMillis - WINDOW_DAYS * DAY
                touched += store.replaceWindow(kind, from, nowMillis, source.readWindow(kind, from, nowMillis))
                mark = mark.copy(changesToken = source.changesToken(kind), tokenAtMillis = nowMillis)
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

    private suspend fun catchUp(
        kind: HealthKind,
        start: HealthSyncEntity,
        nowMillis: Long,
        budget: Budget,
        touched: MutableSet<Long>,
    ) {
        var mark = start
        var emptyInARow = 0
        while (!mark.catchUpDone && budget.take()) {
            val to = mark.catchUpCursorMillis ?: nowMillis
            val from = to - SLICE_DAYS * DAY
            val records = try {
                source.readWindow(kind, from, to)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (refused: Exception) {
                if (to <= nowMillis - WINDOW_DAYS * DAY) {
                    note("$kind: reading before ${dateOf(to)} was refused; taken as the history limit")
                    store.saveBookmark(mark.copy(catchUpDone = true))
                } else {
                    note("$kind: reading before ${dateOf(to)} was refused; will try again")
                }
                return
            }
            touched += store.apply(records, emptyList())
            emptyInARow = if (records.isEmpty()) emptyInARow + 1 else 0
            val done = emptyInARow >= EMPTY_SLICES_TO_STOP || from <= nowMillis - FURTHEST_DAYS * DAY
            mark = mark.copy(catchUpCursorMillis = from, catchUpDone = done)
            store.saveBookmark(mark)
        }
    }

    private fun note(detail: String) = problems.record(kind = "health", detail = detail)

    private fun dateOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    private class Budget(private var left: Int) {
        val spent: Boolean get() = left <= 0
        fun take(): Boolean = if (left > 0) { left--; true } else false
    }

    companion object {
        private const val DAY = 86_400_000L

        /** A choice: a week is a readable slice for any kind. */
        const val SLICE_DAYS = 7L

        /** A choice matching Health Connect's documented 30-day read limit; not a measured quota. */
        const val WINDOW_DAYS = 30L

        /** A choice: eight empty weeks in a row means the record has begun. */
        const val EMPTY_SLICES_TO_STOP = 8

        /** A choice: two years bounds the catch-up on a phone that has nothing. */
        const val FURTHEST_DAYS = 730L

        /** A choice that keeps one open cheap. Health Connect's real quota is not measured here. */
        const val READS_PER_OPEN = 60
    }
}
