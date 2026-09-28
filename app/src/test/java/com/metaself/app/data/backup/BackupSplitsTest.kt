package com.metaself.app.data.backup

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.SessionSplitEntity
import com.metaself.app.domain.backup.BackupSessionSplit
import org.junit.jupiter.api.Test

/** D92: where the owner's splits go in the file, and where they come back. Every id is invented. */
class BackupSplitsTest {

    @Test
    fun `a split is written as its two workouts' positions in the file, counted from 1`() {
        val written = BackupSplits.toFile(workoutIds = listOf(3, 7, 9), splits = listOf(SessionSplitEntity(3, 9)))

        assertThat(written).containsExactly(BackupSessionSplit(first = 1, second = 3))
    }

    @Test
    fun `a split whose workout is gone is not written`() {
        assertThat(BackupSplits.toFile(listOf(3, 7), listOf(SessionSplitEntity(3, 8)))).isEmpty()
    }

    @Test
    fun `a split comes back under the ids its workouts are restored with`() {
        // The file's second workout was a duplicate and dropped: positions 1, 3, 4 are restored as 1, 2, 3.
        val rows = BackupSplits.rows(listOf(BackupSessionSplit(4, 1)), keptPositions = listOf(1, 3, 4))

        assertThat(rows).containsExactly(SessionSplitEntity(1, 3))
    }

    @Test
    fun `a split naming a dropped or missing workout, or one twice, is dropped`() {
        val rows = BackupSplits.rows(
            listOf(BackupSessionSplit(1, 2), BackupSessionSplit(1, 9), BackupSessionSplit(3, 3), BackupSessionSplit(1, 3), BackupSessionSplit(3, 1)),
            keptPositions = listOf(1, 3),
        )

        assertThat(rows).containsExactly(SessionSplitEntity(1, 2))
    }
}
