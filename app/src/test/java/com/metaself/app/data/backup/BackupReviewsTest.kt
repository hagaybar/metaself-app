package com.metaself.app.data.backup

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.trainer.TrainerReviewEntity
import com.metaself.app.domain.backup.BackupTrainerReview
import com.metaself.app.domain.backup.BackupWorkout
import org.junit.jupiter.api.Test

/**
 * D88: where each review goes in the file, and where it comes back — with no database. A review whose
 * workout is gone (the band's app deleted it, or a typed one was deleted) is written at the top level
 * and restored under a workout id no workout can have. Every figure and word is invented.
 */
class BackupReviewsTest {

    @Test
    fun `a review whose workout is here goes inside it, and one whose workout is gone goes on its own`() {
        val onThree = review(id = 1, workoutId = 3, words = "On three.")
        val gone = review(id = 2, workoutId = 2, words = "Session gone.")

        val split = BackupReviews.split(workoutIds = setOf(1L, 3L), reviews = listOf(onThree, gone))

        assertThat(split.byWorkout.keys).containsExactly(3L)
        assertThat(split.byWorkout.getValue(3L).words).isEqualTo("On three.")
        assertThat(split.withoutWorkout).containsExactly(
            BackupTrainerReview(planId = 7, felt = "RIGHT", words = "Session gone.", feedback = "{}", feedbackAtMillis = 900, model = "a-model"),
        )
    }

    @Test
    fun `restored, a review goes to its workout's new id and one without a workout to a negative id`() {
        val workouts = listOf(
            walk(startedAt = 1_000),
            walk(startedAt = 2_000).copy(trainerReview = BackupTrainerReview(planId = 7, words = "Second.")),
        )
        val withoutWorkout = listOf(BackupTrainerReview(words = "Gone one."), BackupTrainerReview(planId = 7, words = "Gone two."))

        val rows = BackupReviews.rows(workouts, withoutWorkout, planIds = setOf(7L))

        assertThat(rows.map { it.workoutId to it.words }).containsExactly(
            2L to "Second.",
            -1L to "Gone one.",
            -2L to "Gone two.",
        ).inOrder()
        assertThat(rows.map { it.id }.toSet()).containsExactly(0L)
        assertThat(rows.last().planId).isEqualTo(7L)
    }

    /** A plan the file does not hold is no plan, for a review with or without a workout. */
    @Test
    fun `a plan id the file does not hold is dropped`() {
        val rows = BackupReviews.rows(
            listOf(walk(startedAt = 1_000).copy(trainerReview = BackupTrainerReview(planId = 8))),
            listOf(BackupTrainerReview(planId = 8)),
            planIds = setOf(7L),
        )

        assertThat(rows.map { it.planId }).containsExactly(null, null)
    }

    /** Workouts are restored as 1…n and autoincrement never hands out 0 or less, so these cannot collide. */
    @Test
    fun `a review without a workout never names a workout id a workout can have`() {
        val rows = BackupReviews.rows(emptyList(), List(3) { BackupTrainerReview(words = "w$it") }, planIds = emptySet())

        assertThat(rows.map { it.workoutId }).containsExactly(-1L, -2L, -3L)
        assertThat(rows.all { it.workoutId < 0 }).isTrue()
    }

    private fun review(id: Long, workoutId: Long, words: String) =
        TrainerReviewEntity(id, workoutId, planId = 7, felt = "RIGHT", words = words, feedback = "{}", feedbackAtMillis = 900, model = "a-model")

    private fun walk(startedAt: Long) = BackupWorkout(
        epochDay = 20_699, startedAtMillis = startedAt, durationMinutes = 40, kind = "WALK", energySource = "NONE", source = "SYNCED",
    )
}
