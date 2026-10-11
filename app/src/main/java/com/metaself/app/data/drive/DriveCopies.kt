package com.metaself.app.data.drive

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.health.ReadingsArchive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * The scheduled backup's Drive half: the daily file, then the detailed readings' months (D71) — a second call, so a
 * failed month never costs the daily file, and the daily file's failure never stops the months.
 *
 * **D139: a backup that fails for want of a connection is one line**, "no connection at HH:mm; the next backup tries
 * again", and the months are not tried: they would fail the same way and say so a second time. Nothing is lost by
 * waiting — the daily file is a whole export, so the next one carries everything this one would have, and a month not
 * sent stays out of date until one is. Only that one day's dated file is never written.
 *
 * Never throws (D8).
 */
class DriveCopies(
    private val drive: DriveCopy,
    private val archive: ReadingsArchive,
    private val problems: ProblemLog,
    private val zone: () -> ZoneId,
) {

    @Inject
    constructor(drive: DriveBackup, archive: ReadingsArchive, problems: ProblemLog) :
        this(drive, archive, problems, { ZoneId.systemDefault() })

    suspend fun write(today: LocalDate, nowMillis: Long) {
        val outcome = try {
            drive.write(today, nowMillis)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            null
        }
        if ((outcome as? DriveOutcome.Failed)?.noConnection == true) {
            val at = Instant.ofEpochMilli(nowMillis).atZone(zone()).format(TIME)
            withContext(Dispatchers.IO) { problems.record("drive", "no connection at $at; the next backup tries again") }
            return
        }
        try {
            archive.writeOutOfDate()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // writeOutOfDate never throws (D8); kept as AutomaticBackup's runCatching was.
        }
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    }
}
