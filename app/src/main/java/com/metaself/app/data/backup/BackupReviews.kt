package com.metaself.app.data.backup

import com.metaself.app.data.trainer.TrainerReviewEntity
import com.metaself.app.domain.backup.BackupTrainerReview
import com.metaself.app.domain.backup.BackupWorkout

/**
 * Where each of the owner's reviews goes in the file, and where it comes back (D88). Pure.
 *
 * A review is written inside its workout, so the file needs no workout ids. A review whose workout is
 * no longer on the phone — the band's app deleted it, or a typed one was deleted; the table has no
 * foreign key, so the words stay — is written on its own, and would otherwise be lost at the next
 * restore.
 *
 * **Restored, such a review is given the workout id -1, -2, … in file order.** A restore renumbers the
 * workouts 1…n, so the review's old id could name a different, restored session; and the workouts'
 * id is autoincrement, which never hands out 0 or less, so a negative id cannot be picked up by a
 * session synced later either. The column stays NOT NULL and unique; nothing reads a review by a
 * negative id.
 */
internal object BackupReviews {

    class Split(
        /** By the id of the workout they are written inside. */
        val byWorkout: Map<Long, TrainerReviewEntity>,
        val withoutWorkout: List<BackupTrainerReview>,
    )

    fun split(workoutIds: Set<Long>, reviews: List<TrainerReviewEntity>): Split {
        val (on, off) = reviews.partition { it.workoutId in workoutIds }
        return Split(on.associateBy { it.workoutId }, off.map { it.toBackup() })
    }

    /**
     * @param workouts the workouts in the order they are inserted, as ids 1…n.
     * @param planIds the plans the file holds; a review naming another has no plan.
     */
    fun rows(
        workouts: List<BackupWorkout>,
        withoutWorkout: List<BackupTrainerReview>,
        planIds: Set<Long>,
    ): List<TrainerReviewEntity> {
        val onWorkouts = workouts.mapIndexedNotNull { at, workout -> workout.trainerReview?.toRow(at + 1L, planIds) }
        val onNone = withoutWorkout.mapIndexed { at, review -> review.toRow(workoutIdWithout(at), planIds) }
        return onWorkouts + onNone
    }

    /** The workout id the [at]th review without a workout is restored under: -1, -2, … */
    fun workoutIdWithout(at: Int): Long = -(at + 1L)

    fun TrainerReviewEntity.toBackup() = BackupTrainerReview(planId, felt, words, feedback, feedbackAtMillis, model)

    private fun BackupTrainerReview.toRow(workoutId: Long, planIds: Set<Long>) =
        TrainerReviewEntity(0, workoutId, planId?.takeIf(planIds::contains), felt, words, feedback, feedbackAtMillis, model)
}
