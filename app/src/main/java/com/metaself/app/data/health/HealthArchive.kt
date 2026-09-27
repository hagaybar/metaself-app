package com.metaself.app.data.health

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.drive.DriveFile
import com.metaself.app.data.time.Now
import com.metaself.app.domain.health.HealthKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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

/**
 * What one write of the out-of-date months did. Every outcome but [Sent] with nothing failed and
 * [NothingDue] leaves a line in the problem log as `"drive"`, so a month that did not appear in Drive
 * always has a reason written down.
 */
sealed interface ArchiveWrite {
    /** Months were tried: [written] went up and were marked; [failed] did not and stay out of date. */
    data class Sent(val written: Int, val failed: Int) : ArchiveWrite

    /** No month had changed since it was last written; Drive was not asked. */
    data object NothingDue : ArchiveWrite

    /** Drive gave no token: out of reach, or consent is wanted (never asked for here). */
    data object NoDrive : ArchiveWrite

    /** Drive's month files could not be listed, so nothing was sent (a failed listing is never empty). */
    data object ListingFailed : ArchiveWrite

    /** Another archive run held the archive; this one was skipped. */
    data object Busy : ArchiveWrite

    /** Something threw outside any one month. */
    data object Failed : ArchiveWrite
}

/** What Settings asks of the archive. */
interface ReadingsArchive {
    /** How many months Drive holds, or null when Drive cannot be reached. */
    suspend fun monthsInDrive(): Int?

    /** Every month in Drive brought back, or null when Drive cannot be reached. */
    suspend fun restoreAll(): ArchiveRestore?

    /** The months that changed since they were last written, sent to Drive. Never throws (D8). */
    suspend fun writeOutOfDate(): ArchiveWrite

    companion object {
        val NONE = object : ReadingsArchive {
            override suspend fun monthsInDrive(): Int? = null
            override suspend fun restoreAll(): ArchiveRestore? = null
            override suspend fun writeOutOfDate(): ArchiveWrite = ArchiveWrite.NothingDue
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
class HealthArchive(
    private val drive: ArchiveDrive,
    private val record: ArchiveRecord,
    private val store: HealthStore,
    private val problems: ProblemLog,
    private val now: Now,
    private val dispatcher: CoroutineDispatcher,
) : ReadingsArchive {

    /** Off the screen's thread: encoding and decoding a month's rows is real work. */
    @Inject
    constructor(drive: ArchiveDrive, record: ArchiveRecord, store: HealthStore, problems: ProblemLog, now: Now) :
        this(drive, record, store, problems, now, Dispatchers.Default)

    private val running = Mutex()

    override suspend fun writeOutOfDate(): ArchiveWrite = withContext(dispatcher) {
        guarded<ArchiveWrite>(ArchiveWrite.Failed) {
            if (!running.tryLock()) {
                // Not a failure — the other run will send this month, or the next daily backup will —
                // so this is not written to the problem log.
                return@guarded ArchiveWrite.Busy
            }
            try {
                writeLocked()
            } finally {
                running.unlock()
            }
        }
    }

    private suspend fun writeLocked(): ArchiveWrite {
        val months = record.monthsOutOfDate()
        if (months.isEmpty()) return ArchiveWrite.NothingDue
        val token = drive.token()
        if (token == null) {
            log("Drive gave no token (out of reach, or consent is wanted); nothing written")
            return ArchiveWrite.NoDrive
        }
        val existing = drive.list(token)
        if (existing == null) {
            log("the month files could not be listed; nothing written")
            return ArchiveWrite.ListingFailed
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
        // Every month not written was logged where it failed, in writeMonth or just above.
        return ArchiveWrite.Sent(written = written, failed = months.size - written)
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
            if (added.days.isNotEmpty()) {
                // Totals are not asked for: every metric counts as failed, so the daily figures stand.
                store.summarise(added.days, TotalsResult.ALL_FAILED, now())
            }
        }
        // The rows and the changedAt they reflect come from the SAME read, so marking the month
        // written as of that changedAt is exact: a change landing after this read — even while the
        // file is still being built — leaves the month's changedAt past it, and the mark below misses.
        val monthRows = record.readingsIn(month)
        val bytes = ReadingArchive.encode(month, monthRows.rows)
        if (!drive.upload(name, bytes, token)) {
            log("month $month could not be uploaded")
            return false
        }
        // Only after the new one is safely up: the previous copies of THIS month, and nothing else.
        inDrive.forEach { drive.delete(it.id, token) }
        record.markWritten(month, monthRows.changedAtMillis)
        return true
    }

    /** [rows] of the kinds this version stores as readings; any other is left out. */
    private fun knownReadings(rows: List<HealthReadingEntity>): List<HealthReadingEntity> =
        rows.filter { HealthKind.parse(it.kind)?.isReading == true }

    override suspend fun monthsInDrive(): Int? = withContext(dispatcher) {
        guarded(null) {
            val token = drive.token() ?: return@guarded null
            monthFiles(token)?.mapNotNull { ReadingArchive.monthOf(it.name) }?.distinct()?.size
        }
    }

    /**
     * Every month file in Drive, downloaded first, then each one's rows added to the phone. Bringing
     * back is not writing: nothing is marked written, and the months that gained rows are marked
     * changed, so the next daily write sends them from the phone, which by then holds everything.
     */
    override suspend fun restoreAll(): ArchiveRestore? = withContext(dispatcher) {
        guarded(null) { running.withLock { restoreLocked() } }
    }

    /**
     * One month at a time: every copy of a month is downloaded, decoded and added, and dropped before
     * the next month's files are even listed — so a large restore never holds more than one month's
     * decoded rows at once. [ArchiveRestore.readings] counts rows actually inserted, not rows seen: a
     * sample the phone already held does not count twice, and one Drive never got right does not count
     * at all.
     */
    private suspend fun restoreLocked(): ArchiveRestore? {
        val token = drive.token() ?: return null
        val files = monthFiles(token)?.sortedBy { it.name }?.groupBy { ReadingArchive.monthOf(it.name)!! }
            ?: return null
        var unreadable = 0
        var unreachable = 0
        var monthsBrought = 0
        var rowsAdded = 0
        val touched = mutableSetOf<Long>()
        try {
            for ((_, copies) in files.toSortedMap()) {
                var decodedOne = false
                for (file in copies) {
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
                    decodedOne = true
                    val inserted = record.insertMissing(knownReadings(month.readings))
                    touched += inserted.days
                    rowsAdded += inserted.rows
                }
                if (decodedOne) monthsBrought++
            }
        } finally {
            if (touched.isNotEmpty()) {
                // Even when a later month failed: what was added is summarised. Totals are not asked
                // for: every metric counts as failed, so the daily figures stand.
                withContext(NonCancellable) { store.summarise(touched, TotalsResult.ALL_FAILED, now()) }
            }
        }
        return ArchiveRestore(monthsBrought, rowsAdded, unreadable, unreachable)
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
