package com.metaself.app.domain.backup

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.backup.RestoreResult
import com.metaself.app.ui.settings.BackupWording
import org.junit.jupiter.api.Test

class BackupWordingTest {

    @Test
    fun `it reports counts rather than reassurance`() {
        val text = BackupWording.restored(RestoreResult(meals = 400, weights = 50, hasProfile = true))

        assertThat(text).contains("400 meals")
        assertThat(text).contains("50 weights")
    }

    @Test
    fun `one is not ones`() {
        val text = BackupWording.saved(RestoreResult(meals = 1, weights = 1, hasProfile = true))

        assertThat(text).contains("1 meal ")
        assertThat(text).contains("1 weight ")
    }

    /** "Are you sure?" is not information. The question names what is about to be destroyed. */
    @Test
    fun `the confirmation says what will be lost and what will replace it`() {
        val text = BackupWording.confirmReplacing(
            here = RestoreResult(meals = 400, weights = 60, hasProfile = true),
            incoming = RestoreResult(meals = 400, weights = 50, hasProfile = true),
        )

        assertThat(text).contains("delete 400 meals")
        assertThat(text).contains("60 weights")
        assertThat(text).contains("put back 400 meals")
    }

    @Test
    fun `restoring onto an empty phone says there is nothing to lose`() {
        val text = BackupWording.confirmReplacing(
            here = RestoreResult(meals = 0, weights = 0, hasProfile = false),
            incoming = RestoreResult(meals = 400, weights = 50, hasProfile = true),
        )

        assertThat(text).contains("nothing here to lose")
    }

    @Test
    fun `an unreadable file says plainly that nothing was changed`() {
        assertThat(BackupWording.UNREADABLE).contains("Nothing on this phone has been changed")
    }
}
