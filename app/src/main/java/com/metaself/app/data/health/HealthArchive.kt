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

/**
 * What bringing months back did. [unreachable] counts files Drive did not hand over (worth trying
 * again); [unreadable] counts files that came back but could not be read whole.
 */
data class ArchiveRestore(val months: Int, val readings: Int, val unreadable: Int, val unreachable: Int)

/** What Settings asks of the archive. */
interface ReadingsArchive {
    /** How many month files Drive holds, or null when Drive cannot be reached. */
    suspend fun monthsInDrive(): Int?

    /** Every month in Drive brought back, or null when Drive cannot be reached. */
    suspend fun restoreAll(): ArchiveRestore?

    /** The months that changed since they were last written, sent to Drive. @return how many were. */
    suspend fun writeOutOfDate(): Int

    companion object {
        val NONE = object : ReadingsArchive {
            override suspend fun monthsInDrive(): Int? = null
            override suspend fun restoreAll(): ArchiveRestore? = null
            override suspend fun writeOutOfDate(): Int = 0
        }
    }
}

/**
 * The raw readings in the owner's Drive, one file per month (D71).
 *
 * Written with each daily backup: the months whose rows changed since they were last written. Never
 * deletes a month; replaces only the file of the month it is writing, and only once the new copy is
 * up.
 *
 * **A month in Drive is never replaced by a thinner one.** A phone that has never written a month —
 * a new install, or one whose data was cleared; the daily file carries no raw readings, so restoring
 * it brings none back — may hold less of it than Drive does. So for such a month Drive's copy is downloaded first and the file sent up is
 * the union: every record the phone has, as the phone has it, plus every record only Drive has. If
 * Drive's copy cannot be downloaded or read, the month is not sent this time and stays out of date;
 * a Drive month nobody could read is never replaced. A month this phone has written before is its
 * own and is replaced as it stands.
 *
 * The records only Drive had are also applied back to the phone's own store, through the same door a
 * restore uses, so a later write of the same month — now the phone's own, since this write marks it
 * written — does not send the record's rows alone and drop Drive's contribution again.
 *
 * A restore brings every month back through the record's own door, so a record already here is
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

    override suspend fun writeOutOfDate(): Int = guarded(0) {
        val months = record.monthsOutOfDate()
        if (months.isEmpty()) return@guarded 0
        val token = drive.token() ?: return@guarded 0
        val existing = drive.list(token)
        var written = 0
        for (month in months) {
            val name = ReadingArchive.fileName(month)
            val inDrive = existing.filter { it.name == name }
            val phone = record.readingsIn(month)
            val rows = if (inDrive.isEmpty() || record.everWritten(month)) {
                phone
            } else {
                val drives = inDrive.map { file -> drive.download(file.id, token)?.let(ReadingArchive::decode) }
                if (drives.any { it == null }) {
                    log("month $month in Drive could not be read; left as it is, not replaced")
                    continue
                }
                val driveRows = drives.flatMap { it!!.readings }
                val onlyDrive = driveOnly(phone, driveRows)
                if (onlyDrive.isNotEmpty()) {
                    // Fed back to the phone's own store now, not just uploaded: see the class KDoc.
                    val touched = store.apply(groupIntoRecords(onlyDrive), emptyList())
                    store.summarise(touched, TotalsResult.ALL_FAILED, now())
                }
                union(phone, driveRows)
            }
            // Taken AFTER the rows are read and, in the union branch, after applying Drive's extra
            // records to the phone: `store.apply` marks the month changed, and taking `asOf` before it
            // would leave that change looking like it happened after this write, so the month would
            // stay "out of date" and be uploaded again next time for no reason. Taken here, `asOf`
            // covers the apply too, and the uploaded union reflects the phone exactly as this write
            // leaves it.
            val asOf = now()
            val bytes = ReadingArchive.encode(month, rows)
            if (!drive.upload(name, bytes, token)) {
                log("month $month could not be uploaded")
                continue
            }
            // Only after the new one is safely up: the previous copy of THIS month, and nothing else.
            inDrive.forEach { drive.delete(it.id, token) }
            record.markWritten(month, asOf)
            written++
        }
        written
    }

    /**
     * Every row the phone has, plus the rows of every record only Drive has. A record is one
     * (origin, record id): where both hold it, the phone's copy is kept whole, because the phone read
     * it from Health Connect as it stands now — taking Drive's extra samples into it would make a
     * record neither side ever had. In (kind, time, sample) order, so the file reads straight.
     */
    private fun union(phone: List<HealthReadingEntity>, drive: List<HealthReadingEntity>): List<HealthReadingEntity> =
        (phone + driveOnly(phone, drive))
            .sortedWith(compareBy({ it.kind }, { it.startMillis }, { it.sampleIndex }))

    /** The rows of [drive], keyed by (origin, record id), that [phone] does not already hold. */
    private fun driveOnly(phone: List<HealthReadingEntity>, drive: List<HealthReadingEntity>): List<HealthReadingEntity> {
        val held = phone.map { it.origin to it.recordId }.toSet()
        return drive.filter { (it.origin to it.recordId) !in held }
    }

    /**
     * [rows], one file's or one batch's worth, grouped back into the records `HealthStore.apply`
     * expects: one [ReadRecord.Reading] per (kind, origin, record id), its samples in order. A row of
     * a kind this version does not know, or that is not a reading, is left out.
     */
    private fun groupIntoRecords(rows: List<HealthReadingEntity>): List<ReadRecord.Reading> =
        rows.mapNotNull { row -> HealthKind.parse(row.kind)?.takeIf { it.isReading }?.let { it to row } }
            .groupBy { (kind, row) -> Triple(kind, row.origin, row.recordId) }
            .map { (key, grouped) ->
                ReadRecord.Reading(
                    kind = key.first,
                    origin = key.second,
                    recordId = key.third,
                    samples = grouped.map { it.second }.sortedBy { it.sampleIndex }
                        .map { Sample(it.startMillis, it.endMillis, it.value) },
                )
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
        var unreachable = 0
        val touched = mutableSetOf<Long>()
        for (file in files) {
            val bytes = drive.download(file.id, token)
            if (bytes == null) {
                unreachable++
                continue
            }
            val month = ReadingArchive.decode(bytes)
            if (month == null) {
                unreadable++
                continue
            }
            val records = groupIntoRecords(month.readings)
            touched += store.apply(records, emptyList())
            record.markWritten(month.month, now())
            readings += records.sumOf { it.samples.size }
            months++
        }
        if (touched.isNotEmpty()) {
            // Totals are not asked for: every metric counts as failed, so the daily figures stand.
            store.summarise(touched, TotalsResult.ALL_FAILED, now())
        }
        ArchiveRestore(months, readings, unreadable, unreachable)
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
