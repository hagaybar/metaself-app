package com.metaself.app.domain.backup

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.backup.BackupOutcome
import com.metaself.app.ui.settings.AutomaticBackupWording
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AutomaticBackupWordingTest {

    /** A file name is checkable by opening the folder. "Backed up successfully" is not. */
    @Test
    fun `a written copy is named`() {
        val text = AutomaticBackupWording.outcome(
            BackupOutcome.Written("metaself-2026-09-04.json", deleted = 0),
        )

        assertThat(text).contains("metaself-2026-09-04.json")
    }

    @Test
    fun `it says how many old copies went, and one is not ones`() {
        assertThat(AutomaticBackupWording.outcome(BackupOutcome.Written("x.json", deleted = 1)))
            .contains("1 older copy.")
        assertThat(AutomaticBackupWording.outcome(BackupOutcome.Written("x.json", deleted = 3)))
            .contains("3 older copies.")
    }

    @Test
    fun `a folder that has gone says what to do about it`() {
        assertThat(AutomaticBackupWording.outcome(BackupOutcome.FolderUnreachable))
            .contains("Pick it again")
    }

    @Test
    fun `a failure names its reason rather than shrugging`() {
        assertThat(AutomaticBackupWording.outcome(BackupOutcome.Failed("IOException")))
            .contains("IOException")
    }

    @Test
    fun `with no folder it explains what picking one would do`() {
        assertThat(AutomaticBackupWording.NOT_SET).contains("once a day")
        assertThat(AutomaticBackupWording.NOT_SET).contains("Drive folder works")
    }

    @Test
    fun `with a folder but no copy yet it says when the first will come`() {
        assertThat(AutomaticBackupWording.set(null)).contains("next time you open the app")
    }

    @Test
    fun `with copies made it says when, and how many are kept`() {
        val text = AutomaticBackupWording.set(LocalDate.of(2026, 9, 4))

        assertThat(text).contains("2026-09-04")
        assertThat(text).contains("14 days are kept")
    }
}
