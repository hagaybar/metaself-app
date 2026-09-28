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

/**
 * A workout's distance and movement calories: Health Connect's aggregate over its time, every app's
 * figures de-duplicated (the same provenance as the figures read with the session). Null for a figure
 * not asked for, with no data, or whose call failed.
 */
data class SessionTotals(val distanceM: Int? = null, val energyKcal: Int? = null)

/**
 * A synced workout missing a figure Health Connect may have since received (D81's investigation), on
 * its stored [epochDay].
 *
 * [startMillis] is the session's own start, exact. Only [endMillis] is approximate: it is the start
 * plus the stored duration, which is rounded to whole minutes — the store keeps no end — so the span
 * asked about may end up to half a minute early or late. Accepted.
 */
data class SessionGap(
    val id: Long,
    val epochDay: Long,
    val startMillis: Long,
    val endMillis: Long,
    val needsDistance: Boolean,
    val needsEnergy: Boolean,
)

/**
 * Health Connect refused a read because the app is not in the foreground (it holds no permission to
 * read in the background). Not a failure (D8): the reader lets it through without logging it, and the
 * copying stops its pass quietly, saving nothing for the read that was refused.
 */
class BackgroundReadRefused(cause: SecurityException) : Exception(cause.message, cause) {
    companion object {
        private const val BACKGROUND_PERMISSION = "READ_HEALTH_DATA_IN_BACKGROUND"

        /**
         * [failure] as a [BackgroundReadRefused] when it is one: a [SecurityException] whose message
         * mentions the foreground or the background-read permission (in any case). Null otherwise —
         * any other SecurityException stays what it was.
         */
        fun from(failure: Throwable): BackgroundReadRefused? {
            if (failure is BackgroundReadRefused) return failure
            if (failure !is SecurityException) return null
            val message = failure.message ?: return null
            val background = message.contains("foreground", ignoreCase = true) ||
                message.contains(BACKGROUND_PERMISSION, ignoreCase = true)
            return if (background) BackgroundReadRefused(failure) else null
        }
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
    /**
     * A workout's [SessionTotals] over [startMillis, endMillis), asking only for what is flagged: one
     * aggregation call per figure. A figure whose call failed is null and logged; never throws for it.
     */
    suspend fun sessionTotals(startMillis: Long, endMillis: Long, distance: Boolean, energy: Boolean): SessionTotals
    /**
     * Whether this phone's Health Connect can grant reading history older than 30 days at all (D72).
     * Never throws: anything that goes wrong is false.
     */
    suspend fun historyAvailable(): Boolean
    /**
     * Whether history older than 30 days may be read: [available] AND its permission is granted (D72).
     * [available] is the caller's own [historyAvailable], passed in rather than asked for again here,
     * so a caller that needs both never queries the feature twice. Never throws (D8): anything that
     * goes wrong is false.
     */
    suspend fun historyGranted(available: Boolean): Boolean
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
    /**
     * The visible synced workouts on [days] with no distance, or with no calories from their app — a
     * figure the copying may ask Health Connect for again. A walk that does not count (D81) is left
     * out: it is counted nowhere, so it is not worth a call.
     */
    suspend fun sessionGaps(days: Set<Long>): List<SessionGap>
    /**
     * Fills in whichever of [totals] the workout [id] still lacks; a figure already there is never
     * replaced, and a typed workout is never touched. Calories filled here are the band's (BAND).
     */
    suspend fun fillSessionTotals(id: Long, totals: SessionTotals)
    /**
     * Whether a pass was stopped by the app leaving the foreground (a refusal, or the pass cancelled)
     * and the next foreground copy owes the recent record a second look: totals from [recheckFromDay]
     * (or the last few days, with none), and the gaps of the last month's workouts. Kept as a
     * `health_sync` marker row like [HISTORY_MARKER], so no schema change; a store that has never said
     * "nothing owed" owes it.
     */
    suspend fun recentRecheckDue(): Boolean = false

    /**
     * The earliest day the marker names, when [recentRecheckDue]: the earliest day a cut-short pass
     * left touched but not yet summarised. Null when nothing is owed, or when it is owed with no day
     * recorded — a fresh install, the first open after the upgrade that added the marker, or a
     * restore — and `recheckRecent` then falls back to the last few days.
     */
    suspend fun recheckFromDay(): Long? = null

    /** Records whether [recentRecheckDue], and from which day ([recheckFromDay]) when [due] and known. */
    suspend fun setRecentRecheckDue(due: Boolean, fromDay: Long? = null) = Unit

    /** Whether the catch-ups have already been re-opened for older history (D72). */
    suspend fun historyActedOn(): Boolean
    /** Records that they have. */
    suspend fun markHistoryActedOn()

    companion object {
        /**
         * The `health_sync` row that records [historyActedOn] (D72), so no schema change was needed.
         * Not a [HealthKind]: every reader of that table skips kinds [HealthKind.parse] does not know.
         * A restore clears the table and the marker with it; the catch-ups then start from scratch,
         * so the re-open that follows on the next open finds nothing finished to re-open.
         */
        const val HISTORY_MARKER = "_HISTORY"

        /**
         * The `health_sync` row behind [recentRecheckDue]: `catchUpDone` true means nothing is owed.
         * Absent — a fresh install, the first open after the upgrade that added it, or after a restore
         * — means one recheck is owed. `catchUpCursorMillis` holds [recheckFromDay] — an epoch DAY on
         * this row, not millis; the field is reused rather than adding a column. Not a [HealthKind], so
         * every reader of the table skips it.
         */
        const val RECHECK_MARKER = "_RECHECK"
    }
}

/**
 * A month's raw readings, read together with the [changedAtMillis] the record saw for that month in
 * the SAME read — so a write built from [rows] can be marked written as of exactly the change it
 * reflects, never a moment guessed from outside the read.
 */
data class MonthRows(val rows: List<HealthReadingEntity>, val changedAtMillis: Long)

/** What [ArchiveRecord.insertMissing] added: which days gained a row, and how many rows that was. */
data class Inserted(val days: Set<Long>, val rows: Int) {
    companion object {
        val NONE = Inserted(emptySet(), 0)
    }
}

/** What the Drive archive needs from the record (D71). */
interface ArchiveRecord {
    /** Months never written, or changed since, oldest first, as "YYYY-MM". */
    suspend fun monthsOutOfDate(): List<String>

    /** Every raw reading filed on a day of [month], kind by kind, each kind in time order. */
    suspend fun readingsIn(month: String): MonthRows

    /**
     * Whether this phone has ever written [month] to Drive. False after a new install or cleared data,
     * when Drive's copy of the month may be the fuller one.
     */
    suspend fun everWritten(month: String): Boolean

    /**
     * [month] is in Drive as it reflects [changedAtSeen] — the changedAt a [readingsIn] read saw
     * beside the rows it sent up. Only takes when the month's changedAt is still [changedAtSeen]: a
     * change stamped after that read, and before this call, leaves the month out of date rather than
     * hidden behind a write that missed it.
     */
    suspend fun markWritten(month: String, changedAtSeen: Long)

    /**
     * Rows brought back from Drive, each inserted as it is — its sample index and day kept — unless
     * the phone already holds a row with the same (origin, record id, sample index), which is kept
     * as the phone has it. Never deletes or replaces anything. One transaction.
     *
     * @return the days of the rows actually inserted, and how many rows that was; their months are
     *   marked changed.
     */
    suspend fun insertMissing(rows: List<HealthReadingEntity>): Inserted
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
    /**
     * Whether history older than 30 days may be read (D72). Null when this phone's Health Connect
     * cannot offer it, and when nothing is granted at all (as with [notAllowed]).
     */
    val historyAllowed: Boolean? = null,
    /**
     * Whether this phone's Health Connect can grant history older than 30 days at all (D72), regardless
     * of whether anything is granted yet. Unlike [historyAllowed], this is never folded into null by
     * [notAllowed] being non-empty: the Connect button needs to know, before anything is granted, whether
     * to ask for that permission at all.
     */
    val historyOffered: Boolean = false,
) {
    /** Whether Settings offers the Connect button for the record: a kind, or the history, not allowed. */
    val asksToConnect: Boolean get() = notAllowed.isNotEmpty() || historyAllowed == false

    companion object {
        /**
         * Worked out from what is stored (D65, D66) — pure, so it is tested without Room. With nothing
         * granted at all, [notAllowed] is left empty: the existing "Off. Allow MetaSelf to read your
         * steps and a long walk will add to that day's allowance" line and the Connect button already
         * say it, and naming all thirteen kinds on top would be noise. [history] is null when the
         * phone cannot offer older history (D72), and is dropped for the same reason; [historyOffered]
         * carries that same fact without being dropped, since the Connect button needs it even when
         * nothing has been granted yet.
         */
        fun from(
            days: Int,
            earliest: Long?,
            syncRows: List<HealthSyncEntity>,
            granted: Set<HealthKind>,
            history: Boolean? = null,
            historyOffered: Boolean = false,
        ): HealthRecordState {
            // Only rows that are kinds: the older-history marker (D72) shares the table.
            val syncByKind = syncRows.mapNotNull { row -> HealthKind.parse(row.kind)?.let { it to row } }.toMap()
            val lastCopiedMillis = syncByKind.values.mapNotNull { it.tokenAtMillis }.maxOrNull()
            val catchingUp = granted.any { kind -> syncByKind[kind]?.catchUpDone != true }
            val notAllowed = if (granted.isEmpty()) emptySet() else HealthKind.entries.toSet() - granted
            return HealthRecordState(
                days = days,
                earliest = earliest,
                lastCopiedMillis = lastCopiedMillis,
                catchingUp = catchingUp,
                notAllowed = notAllowed,
                historyAllowed = if (granted.isEmpty()) null else history,
                historyOffered = historyOffered,
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
