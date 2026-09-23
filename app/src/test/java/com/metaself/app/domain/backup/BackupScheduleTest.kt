package com.metaself.app.domain.backup

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BackupScheduleTest {

    private val today = 20_699L

    @Test
    fun `with no backup ever made, one is due`() {
        assertThat(BackupSchedule.isDue(lastBackupEpochDay = null, todayEpochDay = today)).isTrue()
    }

    @Test
    fun `one made today is enough for today`() {
        assertThat(BackupSchedule.isDue(today, today)).isFalse()
    }

    @Test
    fun `one made yesterday means another is due`() {
        assertThat(BackupSchedule.isDue(today - 1, today)).isTrue()
    }

    @Test
    fun `the file is named by its date, so a folder of them sorts itself`() {
        assertThat(BackupSchedule.fileNameFor("2026-09-04"))
            .isEqualTo("metaself-2026-09-04.json")
    }

    @Test
    fun `fourteen days are kept and the fifteenth goes`() {
        val names = (1..15).map { BackupSchedule.fileNameFor("2026-09-%02d".format(it)) }

        val doomed = BackupSchedule.filesToDelete(names)

        assertThat(doomed).containsExactly("metaself-2026-09-01.json")
    }

    @Test
    fun `with fourteen or fewer nothing is deleted`() {
        val names = (1..14).map { BackupSchedule.fileNameFor("2026-09-%02d".format(it)) }

        assertThat(BackupSchedule.filesToDelete(names)).isEmpty()
    }

    /**
     * The folder is the owner's, not the app's. A backup routine that deletes what it did not write
     * is a data-loss bug waiting for the wrong folder to be picked.
     */
    @Test
    fun `nothing the app did not write is ever deleted`() {
        val names = (1..15).map { BackupSchedule.fileNameFor("2026-09-%02d".format(it) ) } +
            listOf("tax-return.pdf", "holiday.jpg", "notes.json", "metaself.txt")

        val doomed = BackupSchedule.filesToDelete(names)

        assertThat(doomed).containsExactly("metaself-2026-09-01.json")
    }

    @Test
    fun `an empty folder has nothing to delete`() {
        assertThat(BackupSchedule.filesToDelete(emptyList())).isEmpty()
    }
}
