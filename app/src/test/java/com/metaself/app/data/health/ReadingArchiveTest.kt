package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.drive.DriveFile
import com.metaself.app.data.drive.DriveFiles
import com.metaself.app.domain.backup.BackupSchedule
import org.junit.jupiter.api.Test
import java.util.zip.GZIPInputStream

/** Every figure is invented and round. */
class ReadingArchiveTest {

    @Test
    fun `a month is named for itself`() {
        assertThat(ReadingArchive.fileName("2026-09")).isEqualTo("metaself-readings-2026-09.json.gz")
        assertThat(ReadingArchive.monthOf("metaself-readings-2026-09.json.gz")).isEqualTo("2026-09")
        assertThat(ReadingArchive.monthOf("metaself-2026-09-03.json")).isNull()
    }

    /** Red line: the daily pruning must never be able to delete a month file. */
    @Test
    fun `the daily backup's pruning can never match a month file`() {
        val month = ReadingArchive.fileName("2026-09")
        assertThat(BackupSchedule.isBackupFile(month)).isFalse()

        val manyDays = (1..30).map { DriveFile("d$it", BackupSchedule.fileNameFor("2026-09-%02d".format(it))) }
        // More month files than the daily rule keeps: "metaself-r…" sorts after "metaself-2…", so with
        // fewer than fourteen the keep-the-newest rule would spare them even if the name rule matched.
        val months = (0 until 20).map { n ->
            val name = ReadingArchive.fileName(java.time.YearMonth.of(2025, 1).plusMonths(n.toLong()).toString())
            DriveFile("m$n", name)
        }

        val deleted = DriveFiles.toDelete(manyDays + months)
        assertThat(deleted).isNotEmpty()
        assertThat(deleted.map { it.name }.filter { ReadingArchive.monthOf(it) != null }).isEmpty()
    }

    @Test
    fun `rows go out and come back exactly`() {
        val rows = listOf(beat("hr-1", 0, 60.0), beat("hr-1", 1, 62.0), steps("st-1"))

        val back = ReadingArchive.decode(ReadingArchive.encode("2026-09", rows))!!

        assertThat(back.month).isEqualTo("2026-09")
        assertThat(back.readings).containsExactlyElementsIn(rows.map { it.copy(id = 0) })
    }

    @Test
    fun `the file is gzip and, unzipped, readable words`() {
        val bytes = ReadingArchive.encode("2026-09", listOf(beat("hr-1", 0, 60.0)))

        val text = GZIPInputStream(bytes.inputStream()).bufferedReader().readText()

        assertThat(text).contains("\"month\": \"2026-09\"")
        assertThat(text).contains("\"kind\": \"HEART_RATE\"")
        assertThat(text).contains("\"record_id\": \"hr-1\"")
    }

    @Test
    fun `rubbish, a truncated file, or a later version is refused`() {
        assertThat(ReadingArchive.decode(byteArrayOf(1, 2, 3))).isNull()
        val whole = ReadingArchive.encode("2026-09", listOf(beat("hr-1", 0, 60.0)))
        assertThat(ReadingArchive.decode(whole.copyOf(whole.size / 2))).isNull()
        assertThat(ReadingArchive.decode(ReadingArchive.encodeRaw("""{"version": 99, "month": "2026-09", "readings": []}""")))
            .isNull()
    }

    private fun beat(record: String, index: Int, bpm: Double) = HealthReadingEntity(
        kind = "HEART_RATE", startMillis = 1_000L + index * 60_000, endMillis = null, value = bpm,
        unit = "bpm", origin = "com.example.band", recordId = record, sampleIndex = index, epochDay = 20_699,
    )

    private fun steps(record: String) = HealthReadingEntity(
        kind = "STEPS", startMillis = 1_000, endMillis = 61_000, value = 100.0, unit = "count",
        origin = "com.example.band", recordId = record, sampleIndex = 0, epochDay = 20_699,
    )
}
