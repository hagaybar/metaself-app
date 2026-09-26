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
    /** Every record of [kind] starting in [fromMillis, toMillis), all pages. */
    suspend fun readWindow(kind: HealthKind, fromMillis: Long, toMillis: Long): List<ReadRecord>
    /** Daily totals for every day in [fromDay, toDay], one aggregation call per metric. */
    suspend fun dayTotals(fromDay: Long, toDay: Long): Map<Long, DayTotals>
}

/** What the copying writes to. The real one is Room; tests use a fake. */
interface HealthStore {
    suspend fun bookmark(kind: HealthKind): HealthSyncEntity?
    suspend fun saveBookmark(bookmark: HealthSyncEntity)
    /** Applies one batch in one transaction; returns the local days it touched. */
    suspend fun apply(records: List<ReadRecord>, deletedIds: List<String>): Set<Long>
    /** Replaces every row of [kind] starting in the window with [records]; returns the days touched. */
    suspend fun replaceWindow(
        kind: HealthKind,
        fromMillis: Long,
        toMillis: Long,
        records: List<ReadRecord>,
    ): Set<Long>
    /** Recomputes each day's summary and its workouts' heart-rate figures; marks its archive month. */
    suspend fun summarise(days: Set<Long>, totals: Map<Long, DayTotals>, nowMillis: Long)
}

/** The one thing the day screen asks for. */
fun interface HealthRecordCopier {
    suspend fun copyNow()

    companion object {
        val NONE = HealthRecordCopier { }
    }
}
