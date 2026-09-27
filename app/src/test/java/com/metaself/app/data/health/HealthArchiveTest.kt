package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.Problem
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.drive.DriveFile
import com.metaself.app.data.time.Now
import com.metaself.app.domain.health.HealthKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * The Drive archive of detailed readings (D71), over fakes: which months are written, what is never
 * deleted, and what a restore brings back. Every figure is invented and round.
 *
 * Days: 20_690 is 2026-08-25, 20_696 is 2026-08-31, 20_697 is 2026-09-01, 20_699 is 2026-09-03.
 */
class HealthArchiveTest {

    private val drive = FakeDrive()
    private val record = FakeArchiveRecord()
    private val store = FakeStore()
    private val problems = RecordingProblems()
    private val archive = HealthArchive(drive, record, store, problems, Now { NOW })

    // --- Writing -----------------------------------------------------------------------------------

    @Test
    fun `each out-of-date month is written, replacing the one already there, and marked`() = runTest {
        record.outOfDate = listOf("2026-08", "2026-09")
        record.held += listOf(beat("hr-1", day = 20_690), beat("hr-2", day = 20_699))
        record.writtenBefore += "2026-09"
        drive.put("metaself-readings-2026-09.json.gz", byteArrayOf(0))

        val written = archive.writeOutOfDate()

        assertThat(written).isEqualTo(2)
        assertThat(drive.files.keys)
            .containsExactly("metaself-readings-2026-08.json.gz", "metaself-readings-2026-09.json.gz")
        assertThat(drive.stored.count { it.name == "metaself-readings-2026-09.json.gz" }).isEqualTo(1)
        assertThat(monthInDrive("2026-09").map { it.recordId }).containsExactly("hr-2")
        assertThat(record.written.keys).containsExactly("2026-08", "2026-09")
        assertThat(record.written.getValue("2026-08")).isEqualTo(NOW)
    }

    /**
     * I3: the moment a month is marked written as of is taken BEFORE its rows are read, so a change
     * landing while the file is built leaves the month out of date rather than hidden.
     */
    @Test
    fun `a month is marked as of a moment taken before its rows were read`() = runTest {
        var clock = 1_000L
        val ticking = HealthArchive(drive, record, store, problems, Now { clock })
        record.outOfDate = listOf("2026-09")
        record.held += beat("hr-1", day = 20_699)
        record.writtenBefore += "2026-09"
        record.onRead = { clock = 2_000L }

        ticking.writeOutOfDate()

        assertThat(record.written.getValue("2026-09")).isEqualTo(1_000L)
    }

    /** The new copy goes up before the old one is removed, so a month is never absent from Drive. */
    @Test
    fun `the old copy of a month is removed only after the new one is up`() = runTest {
        record.outOfDate = listOf("2026-09")
        record.held += beat("hr-1", day = 20_699)
        record.writtenBefore += "2026-09"
        drive.put("metaself-readings-2026-09.json.gz", byteArrayOf(0))

        archive.writeOutOfDate()

        val upload = drive.calls.indexOf("upload metaself-readings-2026-09.json.gz")
        val delete = drive.calls.indexOfFirst { it.startsWith("delete") }
        assertThat(upload).isAtLeast(0)
        assertThat(delete).isGreaterThan(upload)
    }

    @Test
    fun `a month whose upload failed is not marked, so it is tried again`() = runTest {
        record.outOfDate = listOf("2026-09")
        record.held += beat("hr-1", day = 20_699)
        record.writtenBefore += "2026-09"
        drive.put("metaself-readings-2026-09.json.gz", byteArrayOf(0))
        drive.refuseUploads = true

        assertThat(archive.writeOutOfDate()).isEqualTo(0)

        assertThat(record.written).isEmpty()
        assertThat(problems.logged.single().kind).isEqualTo("drive")
        // The copy already there is kept when its replacement did not go up.
        assertThat(drive.files).containsKey("metaself-readings-2026-09.json.gz")
    }

    /** I5: one month failing does not stop the others. */
    @Test
    fun `a month that throws is logged and the next month is still written`() = runTest {
        record.outOfDate = listOf("2026-08", "2026-09")
        record.held += listOf(beat("hr-1", day = 20_690), beat("hr-2", day = 20_699))
        drive.uploadThrowsFor += "metaself-readings-2026-08.json.gz"

        assertThat(archive.writeOutOfDate()).isEqualTo(1)

        assertThat(drive.files.keys).containsExactly("metaself-readings-2026-09.json.gz")
        assertThat(record.written.keys).containsExactly("2026-09")
        assertThat(problems.logged.map { it.kind }).containsExactly("drive")
    }

    /**
     * A phone that has never written a month (a new install, or a restore from the daily file) may
     * hold less of it than Drive does. Drive's copy is read first and what the phone lacks is added to
     * the phone; the file sent up is then what the phone holds.
     */
    @Test
    fun `a month this phone never wrote keeps what only Drive has`() = runTest {
        inDrive("2026-09", beat("hr-1", day = 20_697))
        record.outOfDate = listOf("2026-09")
        record.held += beat("hr-2", day = 20_699)

        assertThat(archive.writeOutOfDate()).isEqualTo(1)

        assertThat(monthInDrive("2026-09").map { it.recordId }).containsExactly("hr-1", "hr-2")
        assertThat(drive.stored.count { it.name == "metaself-readings-2026-09.json.gz" }).isEqualTo(1)
        assertThat(record.written.keys).containsExactly("2026-09")
    }

    /** C1: Drive's extra rows reach the phone through the door that only adds, never through apply. */
    @Test
    fun `writing the union adds Drive's extra rows to the phone and re-summarises their days`() = runTest {
        inDrive("2026-09", beat("hr-1", day = 20_697))
        record.outOfDate = listOf("2026-09")
        record.held += beat("hr-2", day = 20_699)

        archive.writeOutOfDate()

        assertThat(record.held.map { it.recordId }).containsExactly("hr-1", "hr-2")
        assertThat(store.applied).isEmpty()
        assertThat(store.deletedIds).isEmpty()
        assertThat(store.summarised.single().first).containsExactly(20_697L)
        // Totals are not asked for: every metric is marked failed, so the restored daily figure stands.
        assertThat(store.summarised.single().second.failed).containsExactlyElementsIn(TotalMetric.entries)
    }

    /**
     * The file sent up is the phone's rows as they stand AFTER Drive's were added, read from the
     * record — not a union built beside it — so the next write of the month, now the phone's own,
     * sends the same thing.
     */
    @Test
    fun `the union write uploads what the phone holds, and a later write keeps it`() = runTest {
        inDrive("2026-09", beat("hr-1", day = 20_697))
        record.outOfDate = listOf("2026-09")
        record.held += beat("hr-2", day = 20_699)

        archive.writeOutOfDate()
        val first = monthInDrive("2026-09")
        assertThat(first).containsExactlyElementsIn(record.readingsIn("2026-09"))

        // The phone wrote this month now; a later change goes the phone's own way, reading no Drive.
        record.held += beat("hr-3", day = 20_699)
        assertThat(archive.writeOutOfDate()).isEqualTo(1)

        assertThat(monthInDrive("2026-09").map { it.recordId }).containsExactly("hr-1", "hr-2", "hr-3")
    }

    /**
     * On a sample both hold, the phone's row wins: it was read from Health Connect as it stands now.
     * A sample only Drive has is added beside it.
     */
    @Test
    fun `on a sample both hold, the phone's row wins`() = runTest {
        drive.put(
            "metaself-readings-2026-09.json.gz",
            ReadingArchive.encode(
                "2026-09",
                listOf(
                    beat("hr-1", day = 20_699, index = 0, bpm = 50.0),
                    beat("hr-1", day = 20_699, index = 1, bpm = 50.0),
                ),
            ),
        )
        record.outOfDate = listOf("2026-09")
        record.held += beat("hr-1", day = 20_699, index = 0, bpm = 70.0)

        archive.writeOutOfDate()

        assertThat(monthInDrive("2026-09").map { it.sampleIndex to it.value })
            .containsExactly(0 to 70.0, 1 to 50.0)
    }

    /** Never replace a Drive month that could not be read: it may be fuller than the phone. */
    @Test
    fun `a Drive copy that cannot be read is left alone, and the month is not marked`() = runTest {
        drive.put("metaself-readings-2026-09.json.gz", byteArrayOf(1, 2, 3))
        record.outOfDate = listOf("2026-09")
        record.held += beat("hr-1", day = 20_699)

        assertThat(archive.writeOutOfDate()).isEqualTo(0)

        assertThat(drive.calls.filter { it.startsWith("upload") || it.startsWith("delete") }).isEmpty()
        assertThat(drive.files.getValue("metaself-readings-2026-09.json.gz")).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(record.written).isEmpty()
        assertThat(problems.logged.single().kind).isEqualTo("drive")
    }

    @Test
    fun `a Drive copy that cannot be downloaded is left alone, and the month is not marked`() = runTest {
        inDrive("2026-09", beat("hr-1", day = 20_697))
        drive.refuseDownloads = true
        record.outOfDate = listOf("2026-09")
        record.held += beat("hr-2", day = 20_699)

        assertThat(archive.writeOutOfDate()).isEqualTo(0)

        assertThat(drive.calls.filter { it.startsWith("upload") || it.startsWith("delete") }).isEmpty()
        assertThat(record.written).isEmpty()
        assertThat(problems.logged.single().kind).isEqualTo("drive")
    }

    /** A month this phone wrote before is its own: replaced as it stands, without reading Drive's. */
    @Test
    fun `a month this phone wrote before is replaced without being read`() = runTest {
        inDrive("2026-09", beat("hr-1", day = 20_697))
        record.writtenBefore += "2026-09"
        record.outOfDate = listOf("2026-09")
        record.held += beat("hr-2", day = 20_699)

        assertThat(archive.writeOutOfDate()).isEqualTo(1)

        assertThat(drive.calls.filter { it.startsWith("download") }).isEmpty()
        assertThat(monthInDrive("2026-09").map { it.recordId }).containsExactly("hr-2")
    }

    /** C2: a listing that failed is not a Drive with no files in it, so nothing is sent or marked. */
    @Test
    fun `a failed listing writes nothing`() = runTest {
        inDrive("2026-09", beat("hr-1", day = 20_697))
        drive.listFails = true
        record.outOfDate = listOf("2026-09")
        record.held += beat("hr-2", day = 20_699)

        assertThat(archive.writeOutOfDate()).isEqualTo(0)

        assertThat(drive.calls.filter { it.startsWith("upload") || it.startsWith("delete") }).isEmpty()
        assertThat(record.written).isEmpty()
        assertThat(problems.logged.single().kind).isEqualTo("drive")
    }

    /** I2: a write already running is not joined by a second one; the second does nothing. */
    @Test
    fun `a second write while one is running is skipped`() = runTest {
        record.outOfDate = listOf("2026-09")
        record.held += beat("hr-1", day = 20_699)
        val gate = CompletableDeferred<Unit>()
        drive.tokenGate = gate

        val first = async { archive.writeOutOfDate() }
        runCurrent()
        assertThat(archive.writeOutOfDate()).isEqualTo(0)
        assertThat(drive.calls).containsExactly("token")

        gate.complete(Unit)
        assertThat(first.await()).isEqualTo(1)
    }

    @Test
    fun `no Drive, nothing written and nothing marked`() = runTest {
        record.outOfDate = listOf("2026-09")
        drive.tokenAvailable = false

        assertThat(archive.writeOutOfDate()).isEqualTo(0)
        assertThat(drive.calls).containsExactly("token")
        assertThat(record.written).isEmpty()
    }

    @Test
    fun `nothing out of date, Drive is not even asked`() = runTest {
        assertThat(archive.writeOutOfDate()).isEqualTo(0)
        assertThat(drive.calls).isEmpty()
    }

    /** Red line: an archive write never deletes a month file. */
    @Test
    fun `writing never deletes anything but the file it replaces`() = runTest {
        drive.put("metaself-readings-2026-07.json.gz", byteArrayOf(0))
        drive.put("metaself-2026-09-03.json", byteArrayOf(0))
        record.outOfDate = listOf("2026-09")
        record.held += beat("hr-1", day = 20_699)

        archive.writeOutOfDate()

        assertThat(drive.files).containsKey("metaself-readings-2026-07.json.gz")
        assertThat(drive.files).containsKey("metaself-2026-09-03.json")
        assertThat(drive.calls.filter { it.startsWith("delete") }).isEmpty()
    }

    /** D8: Drive failing never throws upwards; it is written down instead. */
    @Test
    fun `a failure inside Drive is logged, never thrown`() = runTest {
        record.outOfDate = listOf("2026-09")
        drive.listThrows = true

        assertThat(archive.writeOutOfDate()).isEqualTo(0)
        assertThat(archive.monthsInDrive()).isNull()
        assertThat(archive.restoreAll()).isNull()
        assertThat(problems.logged.map { it.kind }.toSet()).containsExactly("drive")
    }

    // --- Counting ----------------------------------------------------------------------------------

    @Test
    fun `Drive's months are counted, daily files and strangers ignored`() = runTest {
        inDrive("2026-08")
        inDrive("2026-09")
        drive.put("metaself-2026-09-03.json", byteArrayOf(0))
        drive.put("metaself-readings-notes.txt", byteArrayOf(0))

        assertThat(archive.monthsInDrive()).isEqualTo(2)
    }

    /** Two copies of one month, left by a write that stopped between upload and delete, are one month. */
    @Test
    fun `two copies of one month count as one month`() = runTest {
        inDrive("2026-09")
        inDrive("2026-09")

        assertThat(archive.monthsInDrive()).isEqualTo(1)
    }

    @Test
    fun `no Drive, no count`() = runTest {
        drive.tokenAvailable = false

        assertThat(archive.monthsInDrive()).isNull()
        assertThat(archive.restoreAll()).isNull()
    }

    /** C2: a listing that failed is Drive not answering, not an empty Drive. */
    @Test
    fun `a failed listing is no answer, not zero months`() = runTest {
        inDrive("2026-09", beat("hr-1", day = 20_699))
        drive.listFails = true

        assertThat(archive.monthsInDrive()).isNull()
        assertThat(archive.restoreAll()).isNull()
        assertThat(record.held).isEmpty()
    }

    // --- Bringing back -----------------------------------------------------------------------------

    @Test
    fun `bringing months back adds every reading in them and re-summarises their days`() = runTest {
        drive.put(
            "metaself-readings-2026-09.json.gz",
            ReadingArchive.encode(
                "2026-09",
                listOf(
                    beat("hr-1", day = 20_699, index = 0),
                    beat("hr-1", day = 20_699, index = 1),
                    beat("hr-2", day = 20_700),
                ),
            ),
        )

        val result = archive.restoreAll()!!

        assertThat(result).isEqualTo(ArchiveRestore(months = 1, readings = 3, unreadable = 0, unreachable = 0))
        assertThat(record.held.map { it.recordId to it.sampleIndex })
            .containsExactly("hr-1" to 0, "hr-1" to 1, "hr-2" to 0)
        assertThat(store.summarised.single().first).containsExactly(20_699L, 20_700L)
        // Totals are not asked for: every metric is marked failed, so the restored daily figures stand.
        assertThat(store.summarised.single().second.failed).containsExactlyElementsIn(TotalMetric.entries)
    }

    /**
     * C1: bringing back is not writing. The month is left out of date (the rows it gained marked it
     * changed), so the next daily write sends it from the phone, which now holds everything.
     */
    @Test
    fun `bringing months back marks nothing written`() = runTest {
        inDrive("2026-09", beat("hr-1", day = 20_699))

        archive.restoreAll()

        assertThat(record.written).isEmpty()
        assertThat(record.changed).containsExactly("2026-09")
    }

    /** Red line: a restore only ever adds. It never goes through the store's replacing door. */
    @Test
    fun `bringing months back deletes nothing and never replaces a record`() = runTest {
        record.held += beat("hr-1", day = 20_699, index = 0, bpm = 70.0)
        record.held += beat("hr-9", day = 20_699)
        inDrive("2026-09", beat("hr-1", day = 20_699))

        archive.restoreAll()

        assertThat(store.applied).isEmpty()
        assertThat(store.deletedIds).isEmpty()
        assertThat(drive.calls.filter { it.startsWith("delete") }).isEmpty()
        assertThat(record.held.map { it.recordId }).containsExactly("hr-1", "hr-9")
    }

    /** On a sample both hold, the phone's row wins; nothing is doubled. */
    @Test
    fun `bringing back keeps the phone's row where both hold a sample`() = runTest {
        record.held += beat("hr-1", day = 20_699, index = 0, bpm = 70.0)
        drive.put(
            "metaself-readings-2026-09.json.gz",
            ReadingArchive.encode(
                "2026-09",
                listOf(beat("hr-1", day = 20_699, index = 0, bpm = 50.0), beat("hr-1", day = 20_699, index = 1, bpm = 50.0)),
            ),
        )

        archive.restoreAll()

        assertThat(record.held.map { it.sampleIndex to it.value }).containsExactly(0 to 70.0, 1 to 50.0)
    }

    /**
     * A heart-rate series running over midnight at a month's end has samples filed in both months, so
     * it is in both files. Restoring one file must not remove what the other brought, whichever comes
     * first, and each month written afterwards carries its own half.
     */
    @Test
    fun `a record split across two month files keeps both parts, in either order`() = runTest {
        for (order in listOf(listOf("2026-08", "2026-09"), listOf("2026-09", "2026-08"))) {
            val drive = FakeDrive()
            val record = FakeArchiveRecord()
            val archive = HealthArchive(drive, record, FakeStore(), RecordingProblems(), Now { NOW })
            val august = beat("hr-x", day = 20_696, index = 0)
            val september = beat("hr-x", day = 20_697, index = 1)
            val files = mapOf("2026-08" to august, "2026-09" to september)
            order.forEach { month ->
                drive.put(ReadingArchive.fileName(month), ReadingArchive.encode(month, listOf(files.getValue(month))))
            }

            assertThat(archive.restoreAll()!!.readings).isEqualTo(2)
            assertThat(record.held.map { it.sampleIndex }).containsExactly(0, 1)

            record.outOfDate = listOf("2026-08", "2026-09")
            assertThat(archive.writeOutOfDate()).isEqualTo(2)
            assertThat(record.held.map { it.sampleIndex }).containsExactly(0, 1)

            val aug = ReadingArchive.decode(drive.files.getValue(ReadingArchive.fileName("2026-08")))!!.readings
            val sep = ReadingArchive.decode(drive.files.getValue(ReadingArchive.fileName("2026-09")))!!.readings
            assertThat(aug.map { it.sampleIndex to it.epochDay }).containsExactly(0 to 20_696L)
            assertThat(sep.map { it.sampleIndex to it.epochDay }).containsExactly(1 to 20_697L)
        }
    }

    /** Two copies of one month bring back one month's readings, not two. */
    @Test
    fun `duplicate copies of a month double nothing`() = runTest {
        val rows = listOf(beat("hr-1", day = 20_699, index = 0), beat("hr-1", day = 20_699, index = 1))
        drive.put("metaself-readings-2026-09.json.gz", ReadingArchive.encode("2026-09", rows))
        drive.put("metaself-readings-2026-09.json.gz", ReadingArchive.encode("2026-09", rows))

        val result = archive.restoreAll()!!

        assertThat(result.months).isEqualTo(1)
        assertThat(result.readings).isEqualTo(2)
        assertThat(record.held).hasSize(2)
    }

    @Test
    fun `an unreadable month is counted and skipped, never half-applied`() = runTest {
        drive.put("metaself-readings-2026-08.json.gz", byteArrayOf(1, 2, 3))
        inDrive("2026-09", beat("hr-1", day = 20_699))

        val result = archive.restoreAll()!!

        assertThat(result.months).isEqualTo(1)
        assertThat(result.unreadable).isEqualTo(1)
        assertThat(result.unreachable).isEqualTo(0)
        assertThat(record.held.map { it.recordId }).containsExactly("hr-1")
    }

    /** Could not be fetched is not the same as could not be read: the first may work tomorrow. */
    @Test
    fun `a month that could not be downloaded is counted apart from one that could not be read`() = runTest {
        inDrive("2026-09", beat("hr-1", day = 20_699))
        drive.refuseDownloads = true

        val result = archive.restoreAll()!!

        assertThat(result).isEqualTo(ArchiveRestore(months = 0, readings = 0, unreadable = 0, unreachable = 1))
        assertThat(record.held).isEmpty()
        assertThat(store.summarised).isEmpty()
    }

    /** I5: one file's download throwing is that file unreachable; the others still come back. */
    @Test
    fun `a download that throws is one file unreachable, and the rest still come back`() = runTest {
        inDrive("2026-08", beat("hr-1", day = 20_690))
        inDrive("2026-09", beat("hr-2", day = 20_699))
        drive.downloadThrowsFor += drive.stored.first().id

        val result = archive.restoreAll()!!

        assertThat(result).isEqualTo(ArchiveRestore(months = 1, readings = 1, unreadable = 0, unreachable = 1))
        assertThat(record.held.map { it.recordId }).containsExactly("hr-2")
        assertThat(problems.logged.map { it.kind }).containsExactly("drive")
    }

    /** What was added before a failure is still summarised: the finally, not the happy path. */
    @Test
    fun `days added before a failure midway are still re-summarised`() = runTest {
        inDrive("2026-08", beat("hr-1", day = 20_690))
        inDrive("2026-09", beat("hr-2", day = 20_699))
        record.insertThrowsFor += "2026-09"

        assertThat(archive.restoreAll()).isNull()

        assertThat(record.held.map { it.recordId }).containsExactly("hr-1")
        assertThat(store.summarised.single().first).containsExactly(20_690L)
    }

    @Test
    fun `a reading of a kind this version does not know is left out`() = runTest {
        drive.put(
            "metaself-readings-2026-09.json.gz",
            ReadingArchive.encode("2026-09", listOf(beat("hr-1", day = 20_699), beat("x-1", day = 20_699).copy(kind = "STRESS"))),
        )

        assertThat(archive.restoreAll()!!.readings).isEqualTo(1)
        assertThat(record.held.map { it.kind }).containsExactly("HEART_RATE")
    }

    // --- Helpers -----------------------------------------------------------------------------------

    /** [month]'s file in Drive, holding [rows]. */
    private fun inDrive(month: String, vararg rows: HealthReadingEntity) =
        drive.put(ReadingArchive.fileName(month), ReadingArchive.encode(month, rows.toList()))

    private fun monthInDrive(month: String): List<HealthReadingEntity> =
        ReadingArchive.decode(drive.files.getValue(ReadingArchive.fileName(month)))!!.readings

    /** A heart-rate sample at minute [index] of [day], UTC. */
    private fun beat(record: String, day: Long, index: Int = 0, bpm: Double = 60.0) = HealthReadingEntity(
        kind = "HEART_RATE", startMillis = day * DAY + index * 60_000L, endMillis = null, value = bpm,
        unit = "bpm", origin = "com.example.band", recordId = record, sampleIndex = index, epochDay = day,
    )

    /**
     * Drive as it behaves: every upload is a NEW file with a new id, even under a name already there;
     * a delete removes that id only. [files] is a view by name of the latest upload.
     */
    private class FakeDrive : ArchiveDrive {
        data class Stored(val id: String, val name: String, val bytes: ByteArray)

        val stored = mutableListOf<Stored>()
        val calls = mutableListOf<String>()
        var tokenAvailable = true
        var tokenGate: CompletableDeferred<Unit>? = null
        var refuseUploads = false
        var refuseDownloads = false
        var listThrows = false
        var listFails = false
        val uploadThrowsFor = mutableSetOf<String>()
        val downloadThrowsFor = mutableSetOf<String>()
        private var nextId = 0

        val files: Map<String, ByteArray> get() = stored.associate { it.name to it.bytes }

        fun put(name: String, bytes: ByteArray) {
            stored += Stored("f${nextId++}", name, bytes)
        }

        override suspend fun token(): String? {
            calls += "token"
            tokenGate?.await()
            return if (tokenAvailable) "token" else null
        }

        override suspend fun list(token: String): List<DriveFile>? {
            calls += "list"
            if (listThrows) throw IllegalStateException("unavailable")
            if (listFails) return null
            // The archive listing: this app's month files only, as the real query asks.
            return stored.filter { it.name.startsWith("metaself-readings-") }.map { DriveFile(it.id, it.name) }
        }

        override suspend fun upload(fileName: String, bytes: ByteArray, token: String): Boolean {
            calls += "upload $fileName"
            if (fileName in uploadThrowsFor) throw IllegalStateException("connection reset")
            if (refuseUploads) return false
            put(fileName, bytes)
            return true
        }

        override suspend fun download(id: String, token: String): ByteArray? {
            calls += "download $id"
            if (id in downloadThrowsFor) throw IllegalStateException("connection reset")
            if (refuseDownloads) return null
            return stored.firstOrNull { it.id == id }?.bytes
        }

        override suspend fun delete(id: String, token: String): Boolean {
            calls += "delete $id"
            return stored.removeAll { it.id == id }
        }
    }

    /**
     * The record as Room keeps it: rows unique by (origin, record id, sample index), filed by
     * [HealthReadingEntity.epochDay]. [insertMissing] skips a clashing row as `IGNORE` does.
     */
    private class FakeArchiveRecord : ArchiveRecord {
        var outOfDate = listOf<String>()
        val held = mutableListOf<HealthReadingEntity>()
        val written = mutableMapOf<String, Long>()
        val changed = mutableSetOf<String>()
        val insertThrowsFor = mutableSetOf<String>()
        var onRead: () -> Unit = {}

        /** Months this phone had written before the test began. */
        val writtenBefore = mutableSetOf<String>()

        override suspend fun monthsOutOfDate() = outOfDate
        override suspend fun everWritten(month: String) = month in writtenBefore || month in written
        override suspend fun readingsIn(month: String): List<HealthReadingEntity> {
            onRead()
            return held.filter { monthOf(it.epochDay) == month }
                .sortedWith(compareBy({ it.kind }, { it.startMillis }, { it.sampleIndex }))
        }
        override suspend fun markWritten(month: String, atMillis: Long) {
            written[month] = atMillis
        }
        override suspend fun insertMissing(rows: List<HealthReadingEntity>): Set<Long> {
            if (rows.any { monthOf(it.epochDay) in insertThrowsFor }) throw IllegalStateException("disk full")
            val inserted = mutableSetOf<Long>()
            rows.forEach { row ->
                val clash = held.any {
                    it.origin == row.origin && it.recordId == row.recordId && it.sampleIndex == row.sampleIndex
                }
                if (!clash) {
                    held += row.copy(id = 0)
                    inserted += row.epochDay
                }
            }
            changed += inserted.map(::monthOf)
            return inserted
        }

        private fun monthOf(epochDay: Long) = LocalDate.ofEpochDay(epochDay).toString().substring(0, 7)
    }

    private class FakeStore : HealthStore {
        val applied = mutableListOf<List<ReadRecord>>()
        val deletedIds = mutableListOf<String>()
        val summarised = mutableListOf<Pair<Set<Long>, TotalsResult>>()

        override suspend fun bookmark(kind: HealthKind): HealthSyncEntity? = null
        override suspend fun saveBookmark(bookmark: HealthSyncEntity) = Unit
        override suspend fun apply(records: List<ReadRecord>, deletedIds: List<String>): Set<Long> {
            applied += records
            this.deletedIds += deletedIds
            return emptySet()
        }
        override suspend fun replaceWindow(
            kind: HealthKind,
            fromMillis: Long,
            toMillis: Long,
            records: List<ReadRecord>,
        ): Set<Long> = throw AssertionError("the archive never replaces a window")
        override suspend fun summarise(days: Set<Long>, totals: TotalsResult, nowMillis: Long) {
            summarised += days to totals
        }
    }

    private class RecordingProblems : ProblemLog {
        val logged = mutableListOf<Problem>()
        override fun recent() = logged.toList()
        override fun record(kind: String, detail: String) {
            logged += Problem(0, kind, detail)
        }
        override fun clear() = logged.clear()
    }

    private companion object {
        const val DAY = 86_400_000L
        const val NOW = 5_000L
    }
}
