package com.metaself.app.data.health

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.drive.DriveFile
import com.metaself.app.data.time.Now
import com.metaself.app.domain.health.HealthKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** The Drive calls the archive makes, with authorisation already done. A fake in tests. */
interface ArchiveDrive {
    /** A token, or null when Drive cannot be reached or consent is wanted (never asked for here). */
    suspend fun token(): String?

    /**
     * This app's month files in Drive, or null when the listing failed. A failed listing is never
     * "no files": taken as empty, a month in Drive would look absent and be replaced unread.
     */
    suspend fun list(token: String): List<DriveFile>?

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
    /** How many months Drive holds, or null when Drive cannot be reached. */
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
 * **Bringing readings back from Drive only ever adds.** Drive's rows reach the phone through
 * [ArchiveRecord.insertMissing]: a row the phone lacks is inserted as it is, and a row the phone
 * already holds — same (origin, record id, sample index) — is left as the phone has it. Nothing is
 * deleted or replaced, so a record whose samples are split across two month files (a series running
 * over midnight at a month's end) keeps both halves whichever file comes back first.
 *
 * **A month in Drive is never replaced by a thinner one.** A phone that has never written a month —
 * a new install, or one whose data was cleared; the daily file carries no raw readings, so restoring
 * it brings none back — may hold less of it than Drive does. So for such a month Drive's copy is
 * downloaded first and what the phone lacks is added to the phone; the file sent up is then what the
 * phone holds. If Drive's copy cannot be downloaded or read, the month is not sent this time and
 * stays out of date. A month this phone has written before is its own and is replaced as it stands.
 *
 * A failed listing is never taken as an empty Drive. One archive run at a time: a write started while
 * another run holds the archive is skipped (the next daily backup comes round), a restore waits.
 * One month or file failing does not stop the others. Never throws upwards (D8); failures go to the
 * problem log as `"drive"`.
 */
@Singleton
class HealthArchive @Inject constructor(
    private val drive: ArchiveDrive,
    private val record: ArchiveRecord,
    private val store: HealthStore,
    private val problems: ProblemLog,
    private val now: Now,
) : ReadingsArchive {

    private val running = Mutex()

    override suspend fun writeOutOfDate(): Int = guarded(0) {
        if (!running.tryLock()) return@guarded 0
        try {
            writeLocked()
        } finally {
            running.unlock()
        }
    }

    private suspend fun writeLocked(): Int {
        val months = record.monthsOutOfDate()
        if (months.isEmpty()) return 0
        val token = drive.token() ?: return 0
        val existing = drive.list(token)
        if (existing == null) {
            log("the month files could not be listed; nothing written")
            return 0
        }
        var written = 0
        for (month in months) {
            try {
                if (writeMonth(month, existing, token)) written++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                log("month $month: ${failure::class.java.simpleName} ${failure.message}")
            }
        }
        return written
    }

    /** One month sent up. @return whether it was, and marked written. */
    private suspend fun writeMonth(month: String, existing: List<DriveFile>, token: String): Boolean {
        val name = ReadingArchive.fileName(month)
        val inDrive = existing.filter { it.name == name }
        if (inDrive.isNotEmpty() && !record.everWritten(month)) {
            val copies = inDrive.map { file -> drive.download(file.id, token)?.let(ReadingArchive::decode) }
            if (copies.any { it == null }) {
                log("month $month in Drive could not be read; left as it is, not replaced")
                return false
            }
            val added = record.insertMissing(knownReadings(copies.flatMap { it!!.readings }))
            if (added.isNotEmpty()) {
                // Totals are not asked for: every metric counts as failed, so the daily figures stand.
                store.summarise(added, TotalsResult.ALL_FAILED, now())
            }
        }
        // Taken BEFORE the rows are read (and after anything added above, which marked the month
        // changed): a change landing while the file is built then leaves the month out of date.
        val asOf = now()
        val bytes = ReadingArchive.encode(month, record.readingsIn(month))
        if (!drive.upload(name, bytes, token)) {
            log("month $month could not be uploaded")
            return false
        }
        // Only after the new one is safely up: the previous copies of THIS month, and nothing else.
        inDrive.forEach { drive.delete(it.id, token) }
        record.markWritten(month, asOf)
        return true
    }

    /** [rows] of the kinds this version stores as readings; any other is left out. */
    private fun knownReadings(rows: List<HealthReadingEntity>): List<HealthReadingEntity> =
        rows.filter { HealthKind.parse(it.kind)?.isReading == true }

    override suspend fun monthsInDrive(): Int? = guarded(null) {
        val token = drive.token() ?: return@guarded null
        monthFiles(token)?.mapNotNull { ReadingArchive.monthOf(it.name) }?.distinct()?.size
    }

    /**
     * Every month file in Drive, downloaded first, then each one's rows added to the phone. Bringing
     * back is not writing: nothing is marked written, and the months that gained rows are marked
     * changed, so the next daily write sends them from the phone, which by then holds everything.
     */
    override suspend fun restoreAll(): ArchiveRestore? = guarded(null) {
        running.withLock { restoreLocked() }
    }

    private suspend fun restoreLocked(): ArchiveRestore? {
        val token = drive.token() ?: return null
        val files = monthFiles(token)?.sortedBy { it.name } ?: return null
        var unreadable = 0
        var unreachable = 0
        val months = mutableListOf<ReadingArchive.Month>()
        for (file in files) {
            val bytes = try {
                drive.download(file.id, token)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                log("${file.name}: ${failure::class.java.simpleName} ${failure.message}")
                null
            }
            if (bytes == null) {
                unreachable++
                continue
            }
            val month = ReadingArchive.decode(bytes)
            if (month == null) {
                unreadable++
                continue
            }
            months += month
        }
        val touched = mutableSetOf<Long>()
        try {
            months.forEach { month -> touched += record.insertMissing(knownReadings(month.readings)) }
        } finally {
            if (touched.isNotEmpty()) {
                // Even when a later month failed: what was added is summarised. Totals are not asked
                // for: every metric counts as failed, so the daily figures stand.
                withContext(NonCancellable) { store.summarise(touched, TotalsResult.ALL_FAILED, now()) }
            }
        }
        val readings = months.flatMap { knownReadings(it.readings) }
            .map { Triple(it.origin, it.recordId, it.sampleIndex) }.distinct().size
        return ArchiveRestore(months.map { it.month }.distinct().size, readings, unreadable, unreachable)
    }

    /** This app's month files, or null (and logged) when the listing failed. */
    private suspend fun monthFiles(token: String): List<DriveFile>? {
        val files = drive.list(token)
        if (files == null) log("the month files could not be listed")
        return files?.filter { ReadingArchive.monthOf(it.name) != null }
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
