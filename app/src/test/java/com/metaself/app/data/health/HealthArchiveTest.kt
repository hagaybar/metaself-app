package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.Problem
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.drive.DriveFile
import com.metaself.app.data.time.Now
import com.metaself.app.domain.health.HealthKind
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The Drive archive of detailed readings (D71), over fakes: which months are written, what is never
 * deleted, and what a restore brings back. Every figure is invented and round.
 */
class HealthArchiveTest {

    private val drive = FakeDrive()
    private val record = FakeArchiveRecord()
    private val store = FakeStore()
    private val problems = RecordingProblems()
    private val archive = HealthArchive(drive, record, store, problems, Now { NOW })

    @Test
    fun `each out-of-date month is written, replacing the one already there, and marked`() = runTest {
        record.outOfDate = listOf("2026-08", "2026-09")
        record.rows["2026-08"] = listOf(beat("hr-1", day = 20_690))
        record.rows["2026-09"] = listOf(beat("hr-2", day = 20_699))
        record.writtenBefore += "2026-09"
        drive.put("metaself-readings-2026-09.json.gz", byteArrayOf(0))

        val written = archive.writeOutOfDate()

        assertThat(written).isEqualTo(2)
        assertThat(drive.files.keys)
            .containsExactly("metaself-readings-2026-08.json.gz", "metaself-readings-2026-09.json.gz")
        assertThat(drive.stored.count { it.name == "metaself-readings-2026-09.json.gz" }).isEqualTo(1)
        assertThat(ReadingArchive.decode(drive.files.getValue("metaself-readings-2026-09.json.gz"))!!.readings.single().recordId)
            .isEqualTo("hr-2")
        assertThat(record.written.keys).containsExactly("2026-08", "2026-09")
        // Marked as of a moment taken BEFORE the rows were read.
        assertThat(record.written.getValue("2026-08")).isEqualTo(NOW)
    }

    /** The new copy goes up before the old one is removed, so a month is never absent from Drive. */
    @Test
    fun `the old copy of a month is removed only after the new one is up`() = runTest {
        record.outOfDate = listOf("2026-09")
        record.rows["2026-09"] = listOf(beat("hr-1", day = 20_699))
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
        record.rows["2026-09"] = listOf(beat("hr-1", day = 20_699))
        record.writtenBefore += "2026-09"
        drive.put("metaself-readings-2026-09.json.gz", byteArrayOf(0))
        drive.refuseUploads = true

        assertThat(archive.writeOutOfDate()).isEqualTo(0)

        assertThat(record.written).isEmpty()
        assertThat(problems.logged.single().kind).isEqualTo("drive")
        // The copy already there is kept when its replacement did not go up.
        assertThat(drive.files).containsKey("metaself-readings-2026-09.json.gz")
    }

    /**
     * A phone that has never written a month (a new install, or a restore from the daily file) may
     * hold less of it than Drive does. Drive's copy is read first and kept: what the phone lacks is
     * carried into the new file.
     */
    @Test
    fun `a month this phone never wrote keeps what only Drive has`() = runTest {
        drive.put("metaself-readings-2026-09.json.gz", ReadingArchive.encode("2026-09", listOf(beat("hr-1", day = 20_697))))
        record.outOfDate = listOf("2026-09")
        record.rows["2026-09"] = listOf(beat("hr-2", day = 20_699))

        assertThat(archive.writeOutOfDate()).isEqualTo(1)

        val month = ReadingArchive.decode(drive.files.getValue("metaself-readings-2026-09.json.gz"))!!
        assertThat(month.readings.map { it.recordId }).containsExactly("hr-1", "hr-2")
        assertThat(drive.stored.count { it.name == "metaself-readings-2026-09.json.gz" }).isEqualTo(1)
        assertThat(record.written.keys).containsExactly("2026-09")
    }

    /**
     * The record only Drive had is not just uploaded back to Drive: it is also applied to the phone's
     * own store, through the same door a restore uses, and the day it falls on is re-summarised. Only
     * a later write of this month, using the record's own copy, proves it was kept (next test).
     */
    @Test
    fun `writing the union also applies Drive's extra record to the phone's own store`() = runTest {
        drive.put("metaself-readings-2026-09.json.gz", ReadingArchive.encode("2026-09", listOf(beat("hr-1", day = 20_697))))
        record.outOfDate = listOf("2026-09")
        record.rows["2026-09"] = listOf(beat("hr-2", day = 20_699))

        archive.writeOutOfDate()

        val applied = store.applied.flatten().filterIsInstance<ReadRecord.Reading>()
        assertThat(applied.map { it.recordId }).containsExactly("hr-1")
        assertThat(store.summarised.single().first).containsExactly(20_697L)
        // Totals are not asked for: every metric is marked failed, so the restored daily figure stands.
        assertThat(store.summarised.single().second.failed).containsExactlyElementsIn(TotalMetric.entries)
    }

    /**
     * The bug this guards: the first union write marked the month written without the record ever
     * gaining Drive's extra reading, so the second write — now going through the "phone wrote this
     * before" branch, reading only the record — sent the record's rows alone and Drive's contribution
     * was gone for good. Applying it back to the phone (previous test) is what the second write relies
     * on: this test stands in for that record now holding it.
     */
    @Test
    fun `a second write of an already-written month keeps what only Drive had`() = runTest {
        drive.put("metaself-readings-2026-09.json.gz", ReadingArchive.encode("2026-09", listOf(beat("hr-1", day = 20_697))))
        record.outOfDate = listOf("2026-09")
        record.rows["2026-09"] = listOf(beat("hr-2", day = 20_699))

        archive.writeOutOfDate()
        // Stands in for `store.apply` having reached the record: the phone now holds both readings.
        // `everWritten` is already true from the first write, so this goes through the "phone wrote
        // this before" branch, reading only the record, never Drive.
        record.rows["2026-09"] = listOf(beat("hr-2", day = 20_699), beat("hr-1", day = 20_697))

        assertThat(archive.writeOutOfDate()).isEqualTo(1)

        val month = ReadingArchive.decode(drive.files.getValue("metaself-readings-2026-09.json.gz"))!!
        assertThat(month.readings.map { it.recordId }).containsExactly("hr-2", "hr-1")
    }

    /**
     * On a record both hold, the phone's copy is the one kept, whole: the phone read it from Health
     * Connect as it stands now, and a sample Drive has that the phone's copy lacks is not added to it.
     */
    @Test
    fun `on a record both hold, the phone's copy wins whole`() = runTest {
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
        record.rows["2026-09"] = listOf(beat("hr-1", day = 20_699, index = 0, bpm = 70.0))

        archive.writeOutOfDate()

        val month = ReadingArchive.decode(drive.files.getValue("metaself-readings-2026-09.json.gz"))!!
        assertThat(month.readings.map { it.sampleIndex to it.value }).containsExactly(0 to 70.0)
    }

    /** Never replace a Drive month that could not be read: it may be fuller than the phone. */
    @Test
    fun `a Drive copy that cannot be read is left alone, and the month is not marked`() = runTest {
        drive.put("metaself-readings-2026-09.json.gz", byteArrayOf(1, 2, 3))
        record.outOfDate = listOf("2026-09")
        record.rows["2026-09"] = listOf(beat("hr-1", day = 20_699))

        assertThat(archive.writeOutOfDate()).isEqualTo(0)

        assertThat(drive.calls.filter { it.startsWith("upload") || it.startsWith("delete") }).isEmpty()
        assertThat(drive.files.getValue("metaself-readings-2026-09.json.gz")).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(record.written).isEmpty()
        assertThat(problems.logged.single().kind).isEqualTo("drive")
    }

    @Test
    fun `a Drive copy that cannot be downloaded is left alone, and the month is not marked`() = runTest {
        drive.put("metaself-readings-2026-09.json.gz", ReadingArchive.encode("2026-09", listOf(beat("hr-1", day = 20_697))))
        drive.refuseDownloads = true
        record.outOfDate = listOf("2026-09")
        record.rows["2026-09"] = listOf(beat("hr-2", day = 20_699))

        assertThat(archive.writeOutOfDate()).isEqualTo(0)

        assertThat(drive.calls.filter { it.startsWith("upload") || it.startsWith("delete") }).isEmpty()
        assertThat(record.written).isEmpty()
        assertThat(problems.logged.single().kind).isEqualTo("drive")
    }

    /** A month this phone wrote before is its own: replaced as it stands, without reading Drive's. */
    @Test
    fun `a month this phone wrote before is replaced without being read`() = runTest {
        drive.put("metaself-readings-2026-09.json.gz", ReadingArchive.encode("2026-09", listOf(beat("hr-1", day = 20_697))))
        record.writtenBefore += "2026-09"
        record.outOfDate = listOf("2026-09")
        record.rows["2026-09"] = listOf(beat("hr-2", day = 20_699))

        assertThat(archive.writeOutOfDate()).isEqualTo(1)

        assertThat(drive.calls.filter { it.startsWith("download") }).isEmpty()
        val month = ReadingArchive.decode(drive.files.getValue("metaself-readings-2026-09.json.gz"))!!
        assertThat(month.readings.map { it.recordId }).containsExactly("hr-2")
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
        record.rows["2026-09"] = listOf(beat("hr-1", day = 20_699))

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

    @Test
    fun `Drive's months are counted, daily files and strangers ignored`() = runTest {
        drive.put("metaself-readings-2026-08.json.gz", ReadingArchive.encode("2026-08", emptyList()))
        drive.put("metaself-readings-2026-09.json.gz", ReadingArchive.encode("2026-09", emptyList()))
        drive.put("metaself-2026-09-03.json", byteArrayOf(0))
        drive.put("metaself-readings-notes.txt", byteArrayOf(0))

        assertThat(archive.monthsInDrive()).isEqualTo(2)
    }

    @Test
    fun `no Drive, no count`() = runTest {
        drive.tokenAvailable = false

        assertThat(archive.monthsInDrive()).isNull()
        assertThat(archive.restoreAll()).isNull()
    }

    @Test
    fun `bringing months back applies every record in them and re-summarises their days`() = runTest {
        drive.put(
            "metaself-readings-2026-09.json.gz",
            ReadingArchive.encode(
                "2026-09",
                listOf(beat("hr-1", day = 20_699, index = 0), beat("hr-1", day = 20_699, index = 1), beat("hr-2", day = 20_700)),
            ),
        )

        val result = archive.restoreAll()!!

        assertThat(result).isEqualTo(ArchiveRestore(months = 1, readings = 3, unreadable = 0, unreachable = 0))
        val applied = store.applied.flatten().filterIsInstance<ReadRecord.Reading>()
        assertThat(applied.map { it.recordId }).containsExactly("hr-1", "hr-2")
        assertThat(applied.first { it.recordId == "hr-1" }.samples).hasSize(2)
        assertThat(store.summarised.single().first).containsExactly(20_699L, 20_700L)
        // Totals are not asked for: every metric is marked failed, so the restored daily figures stand.
        assertThat(store.summarised.single().second.failed).containsExactlyElementsIn(TotalMetric.entries)
        assertThat(record.written.keys).containsExactly("2026-09")
    }

    /** Red line: a restore goes through the store's own door and never asks it to delete anything. */
    @Test
    fun `bringing months back deletes nothing`() = runTest {
        drive.put("metaself-readings-2026-09.json.gz", ReadingArchive.encode("2026-09", listOf(beat("hr-1", day = 20_699))))

        archive.restoreAll()

        assertThat(store.deletedIds).isEmpty()
        assertThat(drive.calls.filter { it.startsWith("delete") }).isEmpty()
    }

    @Test
    fun `an unreadable month is counted and skipped, never half-applied`() = runTest {
        drive.put("metaself-readings-2026-08.json.gz", byteArrayOf(1, 2, 3))
        drive.put("metaself-readings-2026-09.json.gz", ReadingArchive.encode("2026-09", listOf(beat("hr-1", day = 20_699))))

        val result = archive.restoreAll()!!

        assertThat(result.months).isEqualTo(1)
        assertThat(result.unreadable).isEqualTo(1)
        assertThat(result.unreachable).isEqualTo(0)
        assertThat(store.applied).hasSize(1)
        assertThat(record.written.keys).containsExactly("2026-09")
    }

    /** Could not be fetched is not the same as could not be read: the first may work tomorrow. */
    @Test
    fun `a month that could not be downloaded is counted apart from one that could not be read`() = runTest {
        drive.put("metaself-readings-2026-09.json.gz", ReadingArchive.encode("2026-09", listOf(beat("hr-1", day = 20_699))))
        drive.refuseDownloads = true

        val result = archive.restoreAll()!!

        assertThat(result).isEqualTo(ArchiveRestore(months = 0, readings = 0, unreadable = 0, unreachable = 1))
        assertThat(store.applied).isEmpty()
        assertThat(record.written).isEmpty()
    }

    @Test
    fun `a reading of a kind this version does not know is left out`() = runTest {
        drive.put(
            "metaself-readings-2026-09.json.gz",
            ReadingArchive.encode("2026-09", listOf(beat("hr-1", day = 20_699), beat("x-1", day = 20_699).copy(kind = "STRESS"))),
        )

        assertThat(archive.restoreAll()!!.readings).isEqualTo(1)
    }

    // --- Helpers -----------------------------------------------------------------------------------

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
        var refuseUploads = false
        var refuseDownloads = false
        var listThrows = false
        private var nextId = 0

        val files: Map<String, ByteArray> get() = stored.associate { it.name to it.bytes }

        fun put(name: String, bytes: ByteArray) {
            stored += Stored("f${nextId++}", name, bytes)
        }

        override suspend fun token(): String? {
            calls += "token"
            return if (tokenAvailable) "token" else null
        }

        override suspend fun list(token: String): List<DriveFile> {
            calls += "list"
            if (listThrows) throw IllegalStateException("unavailable")
            // The archive listing: this app's month files only, as the real query asks.
            return stored.filter { it.name.startsWith("metaself-readings-") }.map { DriveFile(it.id, it.name) }
        }

        override suspend fun upload(fileName: String, bytes: ByteArray, token: String): Boolean {
            calls += "upload $fileName"
            if (refuseUploads) return false
            put(fileName, bytes)
            return true
        }

        override suspend fun download(id: String, token: String): ByteArray? {
            calls += "download $id"
            if (refuseDownloads) return null
            return stored.firstOrNull { it.id == id }?.bytes
        }

        override suspend fun delete(id: String, token: String): Boolean {
            calls += "delete $id"
            return stored.removeAll { it.id == id }
        }
    }

    private class FakeArchiveRecord : ArchiveRecord {
        var outOfDate = listOf<String>()
        val rows = mutableMapOf<String, List<HealthReadingEntity>>()
        val written = mutableMapOf<String, Long>()

        /** Months this phone had written before the test began. */
        val writtenBefore = mutableSetOf<String>()

        override suspend fun monthsOutOfDate() = outOfDate
        override suspend fun everWritten(month: String) = month in writtenBefore || month in written
        override suspend fun readingsIn(month: String) = rows[month].orEmpty()
        override suspend fun markWritten(month: String, atMillis: Long) {
            written[month] = atMillis
        }
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
            return records.filterIsInstance<ReadRecord.Reading>()
                .flatMap { r -> r.samples.map { it.startMillis / DAY } }.toSet()
        }
        override suspend fun replaceWindow(
            kind: HealthKind,
            fromMillis: Long,
            toMillis: Long,
            records: List<ReadRecord>,
        ): Set<Long> = throw AssertionError("a restore never replaces a window")
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
