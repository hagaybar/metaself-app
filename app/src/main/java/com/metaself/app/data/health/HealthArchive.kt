package com.metaself.app.data.health

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.drive.DriveFile
import com.metaself.app.data.time.Now
import com.metaself.app.domain.health.HealthKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** The Drive calls the archive makes, with authorisation already done. A fake in tests. */
interface ArchiveDrive {
    /** A token, or null when Drive cannot be reached or consent is wanted (never asked for here). */
    suspend fun token(): String?

    /** This app's month files in Drive. */
    suspend fun list(token: String): List<DriveFile>

    suspend fun upload(fileName: String, bytes: ByteArray, token: String): Boolean

    suspend fun download(id: String, token: String): ByteArray?

    suspend fun delete(id: String, token: String): Boolean
}

/** What bringing months back did. [unreadable] counts files that could not be fetched or read whole. */
data class ArchiveRestore(val months: Int, val readings: Int, val unreadable: Int)

/** What Settings asks of the archive. */
interface ReadingsArchive {
    /** How many month files Drive holds, or null when Drive cannot be reached. */
    suspend fun monthsInDrive(): Int?

    /** Every month in Drive brought back, or null when Drive cannot be reached. */
    suspend fun restoreAll(): ArchiveRestore?

    companion object {
        val NONE = object : ReadingsArchive {
            override suspend fun monthsInDrive(): Int? = null
            override suspend fun restoreAll(): ArchiveRestore? = null
        }
    }
}

/**
 * The raw readings in the owner's Drive, one file per month (D71).
 *
 * Written with each daily backup: the months whose rows changed since they were last written. Never
 * deletes a month; replaces only the file of the month it is writing, and only once the new copy is
 * up. A restore brings every month back through the record's own door, so a record already here is
 * replaced, never doubled, and nothing the phone has that the file lacks is removed.
 * Never throws upwards (D8); failures go to the problem log as `"drive"`.
 */
@Singleton
class HealthArchive @Inject constructor(
    private val drive: ArchiveDrive,
    private val record: ArchiveRecord,
    private val store: HealthStore,
    private val problems: ProblemLog,
    private val now: Now,
) : ReadingsArchive {

    /** @return how many months were written. */
    suspend fun writeOutOfDate(): Int = guarded(0) {
        val months = record.monthsOutOfDate()
        if (months.isEmpty()) return@guarded 0
        val token = drive.token() ?: return@guarded 0
        val existing = drive.list(token)
        var written = 0
        for (month in months) {
            // Taken BEFORE the rows are read: a change made meanwhile leaves the month out of date.
            val asOf = now()
            val bytes = ReadingArchive.encode(month, record.readingsIn(month))
            val name = ReadingArchive.fileName(month)
            if (!drive.upload(name, bytes, token)) {
                log("month $month could not be uploaded")
                continue
            }
            // Only after the new one is safely up: the previous copy of THIS month, and nothing else.
            existing.filter { it.name == name }.forEach { drive.delete(it.id, token) }
            record.markWritten(month, asOf)
            written++
        }
        written
    }

    override suspend fun monthsInDrive(): Int? = guarded(null) {
        val token = drive.token() ?: return@guarded null
        drive.list(token).count { ReadingArchive.monthOf(it.name) != null }
    }

    override suspend fun restoreAll(): ArchiveRestore? = guarded(null) {
        val token = drive.token() ?: return@guarded null
        val files = drive.list(token).filter { ReadingArchive.monthOf(it.name) != null }.sortedBy { it.name }
        var months = 0
        var readings = 0
        var unreadable = 0
        val touched = mutableSetOf<Long>()
        for (file in files) {
            val month = drive.download(file.id, token)?.let(ReadingArchive::decode)
            if (month == null) {
                unreadable++
                continue
            }
            val records = month.readings
                .mapNotNull { row -> HealthKind.parse(row.kind)?.takeIf { it.isReading }?.let { it to row } }
                .groupBy { (kind, row) -> Triple(kind, row.origin, row.recordId) }
                .map { (key, rows) ->
                    ReadRecord.Reading(
                        kind = key.first,
                        origin = key.second,
                        recordId = key.third,
                        samples = rows.map { it.second }.sortedBy { it.sampleIndex }
                            .map { Sample(it.startMillis, it.endMillis, it.value) },
                    )
                }
            touched += store.apply(records, emptyList())
            record.markWritten(month.month, now())
            readings += records.sumOf { it.samples.size }
            months++
        }
        if (touched.isNotEmpty()) {
            // Totals are not asked for: every metric counts as failed, so the daily figures stand.
            store.summarise(touched, TotalsResult.ALL_FAILED, now())
        }
        ArchiveRestore(months, readings, unreadable)
    }

    private suspend fun log(detail: String) = withContext(Dispatchers.IO) {
        problems.record("drive", "archive: $detail")
    }

    private suspend fun <T> guarded(otherwise: T, block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        log("${failure::class.java.simpleName} ${failure.message}")
        otherwise
    }
}
