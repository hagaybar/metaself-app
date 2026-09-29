package com.metaself.app.data.backup

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.trainer.PlanConfirmationEntity
import com.metaself.app.domain.backup.BackupPlanConfirmation
import org.junit.jupiter.api.Test

/** D105: where the owner's answers go in the file, and where they come back. Every id and figure is invented. */
class BackupConfirmationsTest {

    @Test
    fun `an answer is written with its session's position in the file, counted from 1`() {
        val written = BackupConfirmations.toFile(workoutIds = listOf(3, 7, 9), confirmations = listOf(PlanConfirmationEntity(2, 9, true, 1_000)))

        assertThat(written).containsExactly(BackupPlanConfirmation(programmeId = 2, workout = 3, confirmed = true, answeredAtMillis = 1_000))
    }

    @Test
    fun `an answer whose session is gone is not written`() {
        assertThat(BackupConfirmations.toFile(listOf(3, 7), listOf(PlanConfirmationEntity(2, 8, false, 1_000)))).isEmpty()
    }

    @Test
    fun `an answer comes back under the id its session is restored with`() {
        // The file's second workout was a duplicate and dropped: positions 1, 3, 4 are restored as 1, 2, 3.
        val rows = BackupConfirmations.rows(listOf(BackupPlanConfirmation(2, 4, false, 1_000)), keptPositions = listOf(1, 3, 4))

        assertThat(rows).containsExactly(PlanConfirmationEntity(2, 3, false, 1_000))
    }

    @Test
    fun `an answer naming a dropped session is dropped, and the first answer for a pair stands`() {
        val rows = BackupConfirmations.rows(
            listOf(
                BackupPlanConfirmation(2, 2, true, 1_000),
                BackupPlanConfirmation(2, 1, true, 2_000),
                BackupPlanConfirmation(2, 1, false, 3_000),
                BackupPlanConfirmation(5, 1, false, 4_000),
            ),
            keptPositions = listOf(1),
        )

        assertThat(rows).containsExactly(PlanConfirmationEntity(2, 1, true, 2_000), PlanConfirmationEntity(5, 1, false, 4_000)).inOrder()
    }
}
