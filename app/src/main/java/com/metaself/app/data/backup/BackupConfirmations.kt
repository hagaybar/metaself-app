package com.metaself.app.data.backup

import com.metaself.app.data.trainer.PlanConfirmationEntity
import com.metaself.app.domain.backup.BackupPlanConfirmation

/**
 * Where the owner's answers about shorter sessions go in the file, and where they come back (D105): by the
 * session's position in the file's `workouts`, as a split is written (D92), since a workout's id is not
 * kept across a restore. An answer whose session is not in the file is not written; one naming a session
 * not restored is dropped.
 */
internal object BackupConfirmations {

    /** [workoutIds] in the order the file writes them. */
    fun toFile(workoutIds: List<Long>, confirmations: List<PlanConfirmationEntity>): List<BackupPlanConfirmation> {
        val positionOf = workoutIds.withIndex().associate { (at, id) -> id to at + 1 }
        return confirmations.mapNotNull { row ->
            positionOf[row.workoutId]?.let { BackupPlanConfirmation(row.programmeId, it, row.confirmed, row.answeredAtMillis) }
        }
    }

    /**
     * @param keptPositions the file position of each workout restored, in the order they are inserted
     *   (as ids 1…n). The first answer for a (plan, session) pair stands, as on the phone.
     */
    fun rows(fileConfirmations: List<BackupPlanConfirmation>, keptPositions: List<Int>): List<PlanConfirmationEntity> {
        val idAt = keptPositions.withIndex().associate { (at, position) -> position to at + 1L }
        return fileConfirmations
            .mapNotNull { answer ->
                idAt[answer.workout]?.let { PlanConfirmationEntity(answer.programmeId, it, answer.confirmed, answer.answeredAtMillis) }
            }
            .distinctBy { it.programmeId to it.workoutId }
    }
}
