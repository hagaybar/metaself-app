package com.metaself.app.data.backup

import com.metaself.app.data.health.SessionSplitEntity
import com.metaself.app.data.health.toEntity
import com.metaself.app.domain.backup.BackupSessionSplit
import com.metaself.app.domain.movement.SessionSplit

/**
 * Where the owner's session splits go in the file, and where they come back (D92). Pure.
 *
 * The file has no workout ids, so a split names its two workouts by their positions in the file's
 * `workouts`, counted from 1. A restore inserts the workouts it keeps as ids 1…n in file order, so a
 * position becomes an id once the file's own duplicate workouts are dropped. A split naming a workout
 * that is not there — gone from the phone at export, or dropped at restore — is left out.
 */
internal object BackupSplits {

    /** [workoutIds] in the order the file writes them. */
    fun toFile(workoutIds: List<Long>, splits: List<SessionSplitEntity>): List<BackupSessionSplit> {
        val positionOf = workoutIds.withIndex().associate { (at, id) -> id to at + 1 }
        return splits.mapNotNull { split ->
            val first = positionOf[split.firstWorkoutId]
            val second = positionOf[split.secondWorkoutId]
            if (first == null || second == null) null else BackupSessionSplit(first, second)
        }
    }

    /**
     * @param keptPositions the file position of each workout restored, in the order they are inserted
     *   (as ids 1…n).
     */
    fun rows(fileSplits: List<BackupSessionSplit>, keptPositions: List<Int>): List<SessionSplitEntity> {
        val idAt = keptPositions.withIndex().associate { (at, position) -> position to at + 1L }
        return fileSplits.mapNotNull { split ->
            val first = idAt[split.first]
            val second = idAt[split.second]
            if (first == null || second == null || first == second) null else SessionSplit.of(first, second).toEntity()
        }.distinct()
    }
}
