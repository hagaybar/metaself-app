package com.metaself.app.data.health

import com.metaself.app.domain.health.HealthKind

/**
 * One day's four totals from Health Connect's aggregation API, which removes the overlap between
 * apps (D12c, D69). A null is a total Health Connect did not give — no data, or no permission.
 */
data class DayTotals(
    val steps: Int? = null,
    val distanceM: Int? = null,
    val activeKcal: Int? = null,
    val totalKcal: Int? = null,
)

/** The four daily totals, each asked for in its own aggregation call. */
enum class TotalMetric { STEPS, DISTANCE, ACTIVE_KCAL, TOTAL_KCAL }

/**
 * Daily totals over a run of days, and which metrics' calls failed.
 *
 * A metric whose call succeeded says what it found: a day with no bucket, or a null in its [DayTotals],
 * means no data, and a stored figure for that day is stale. A metric in [failed] said nothing, so a
 * figure already stored for it stands.
 */
data class TotalsResult(
    val byDay: Map<Long, DayTotals> = emptyMap(),
    val failed: Set<TotalMetric> = emptySet(),
) {
    companion object {
        /** Every call failed: nothing is known and nothing stored is cleared. */
        val ALL_FAILED = TotalsResult(failed = TotalMetric.entries.toSet())
    }
}

/** One page of changes since a token. */
data class ChangesPage(
    val upserts: List<ReadRecord>,
    val deletedIds: List<String>,
    val nextToken: String,
    val hasMore: Boolean,
    val expired: Boolean,
)

/** What the copying reads from. The real one is Health Connect; tests use a fake. */
interface HealthSource {
    suspend fun grantedKinds(): Set<HealthKind>
    suspend fun changesToken(kind: HealthKind): String
    suspend fun changes(kind: HealthKind, token: String): ChangesPage
    /**
     * Every record of [kind] in [fromMillis, toMillis), all pages. Which records straddling the window's
     * edges Health Connect includes is not verified here; the store deletes whole records that have a
     * sample inside the window, and stores whatever comes back.
     */
    suspend fun readWindow(kind: HealthKind, fromMillis: Long, toMillis: Long): List<ReadRecord>
    /**
     * Daily totals for every day in [fromDay, toDay], one aggregation call per metric. A metric whose
     * call failed is in [TotalsResult.failed]; it never throws for one metric's failure.
     */
    suspend fun dayTotals(fromDay: Long, toDay: Long): TotalsResult
}

/** What the copying writes to. The real one is Room; tests use a fake. */
interface HealthStore {
    suspend fun bookmark(kind: HealthKind): HealthSyncEntity?
    suspend fun saveBookmark(bookmark: HealthSyncEntity)
    /**
     * Applies one batch in one transaction; returns the local days it touched, and marks their archive
     * months out of date.
     */
    suspend fun apply(records: List<ReadRecord>, deletedIds: List<String>): Set<Long>
    /**
     * Replaces the rows of [kind] in the window with [records]; returns the days touched and marks their
     * archive months out of date. A synced workout is updated in place, not recreated.
     */
    suspend fun replaceWindow(
        kind: HealthKind,
        fromMillis: Long,
        toMillis: Long,
        records: List<ReadRecord>,
    ): Set<Long>
    /** Recomputes each day's summary and its workouts' heart-rate figures. Marks no archive month. */
    suspend fun summarise(days: Set<Long>, totals: TotalsResult, nowMillis: Long)
}

/** The one thing the day screen asks for. */
fun interface HealthRecordCopier {
    suspend fun copyNow()

    companion object {
        val NONE = HealthRecordCopier { }
    }
}

/** What Settings shows about the record. */
data class HealthRecordState(
    val days: Int = 0,
    val earliest: Long? = null,
    val lastCopiedMillis: Long? = null,
    val catchingUp: Boolean = false,
    val notAllowed: Set<HealthKind> = emptySet(),
) {
    companion object {
        /**
         * Worked out from what is stored (D65, D66) — pure, so it is tested without Room. With nothing
         * granted at all, [notAllowed] is left empty: the existing "Off. Allow MetaSelf to read your
         * steps and a long walk will add to that day's allowance" line and the Connect button already
         * say it, and naming all thirteen kinds on top would be noise.
         */
        fun from(days: Int, earliest: Long?, syncRows: List<HealthSyncEntity>, granted: Set<HealthKind>): HealthRecordState {
            val lastCopiedMillis = syncRows.mapNotNull { it.tokenAtMillis }.maxOrNull()
            val syncByKind = syncRows.associateBy { HealthKind.parse(it.kind) }
            val catchingUp = granted.any { kind -> syncByKind[kind]?.catchUpDone != true }
            val notAllowed = if (granted.isEmpty()) emptySet() else HealthKind.entries.toSet() - granted
            return HealthRecordState(
                days = days,
                earliest = earliest,
                lastCopiedMillis = lastCopiedMillis,
                catchingUp = catchingUp,
                notAllowed = notAllowed,
            )
        }
    }
}

interface HealthRecordStatus {
    suspend fun current(): HealthRecordState

    companion object {
        val NONE = object : HealthRecordStatus {
            override suspend fun current() = HealthRecordState()
        }
    }
}
