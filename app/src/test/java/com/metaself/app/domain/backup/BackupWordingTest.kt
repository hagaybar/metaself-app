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

    /** A restore deletes the health record too; the question says so when there is one. */
    @Test
    fun `the confirmation names workouts and health days when either side has some`() {
        val text = BackupWording.confirmReplacing(
            here = RestoreResult(meals = 400, weights = 60, hasProfile = true, workouts = 12, healthDays = 30),
            incoming = RestoreResult(meals = 400, weights = 50, hasProfile = true),
        )

        assertThat(text).contains("delete 400 meals, 60 weights, 12 workouts and 30 days of health data")
        assertThat(text).contains("put back 400 meals, 50 weights, 0 workouts and 0 days of health data")
    }

    @Test
    fun `a phone holding only health data still has something to lose`() {
        val text = BackupWording.confirmReplacing(
            here = RestoreResult(meals = 0, weights = 0, hasProfile = false, healthDays = 1),
            incoming = RestoreResult(meals = 400, weights = 50, hasProfile = true),
        )

        assertThat(text).contains("1 day of health data")
        assertThat(text).doesNotContain("nothing here to lose")
    }

    @Test
    fun `saving and restoring name the health record only when there is one`() {
        assertThat(BackupWording.restored(RestoreResult(1, 1, true, workouts = 1, healthDays = 3)))
            .isEqualTo("Restored 1 meal, 1 weight, 1 workout and 3 days of health data.")
        assertThat(BackupWording.saved(RestoreResult(1, 1, true)))
            .isEqualTo("Saved 1 meal and 1 weight to the file.")
    }

    /** D88: a restore replaces the trainer's plans and the session reviews too; the question names them when either side has any. */
    @Test
    fun `the confirmation names the trainer's plans and the session reviews when either side has some`() {
        val text = BackupWording.confirmReplacing(
            here = RestoreResult(meals = 4, weights = 6, hasProfile = true, trainerPlans = 3, trainerReviews = 2),
            incoming = RestoreResult(meals = 4, weights = 5, hasProfile = true, trainerPlans = 1, trainerReviews = 1),
        )

        assertThat(text).contains("delete 4 meals, 6 weights, 3 trainer plans and 2 session reviews already on this phone")
        assertThat(text).contains("put back 4 meals, 5 weights, 1 trainer plan and 1 session review from the file")
    }

    @Test
    fun `a phone holding only the trainer's words still has something to lose`() {
        val text = BackupWording.confirmReplacing(
            here = RestoreResult(meals = 0, weights = 0, hasProfile = false, trainerReviews = 1),
            incoming = RestoreResult(meals = 4, weights = 5, hasProfile = true),
        )

        assertThat(text).contains("0 trainer plans and 1 session review")
        assertThat(text).doesNotContain("nothing here to lose")
    }

    @Test
    fun `restoring names the trainer's record after the health record, and only when there is some`() {
        assertThat(BackupWording.restored(RestoreResult(1, 1, true, workouts = 1, healthDays = 3, trainerPlans = 2, trainerReviews = 1)))
            .isEqualTo("Restored 1 meal, 1 weight, 1 workout, 3 days of health data, 2 trainer plans and 1 session review.")
    }

    /** What the file holds, counted one way for the confirmation and for "saved". Invented. */
    @Test
    fun `a file's trainer record is counted with the reviews inside workouts and those without one`() {
        val walk = BackupWorkout(epochDay = 20_699, startedAtMillis = 1_000, durationMinutes = 40, kind = "WALK", energySource = "NONE", source = "SYNCED")
        val file = Backup(
            workouts = listOf(walk, walk.copy(startedAtMillis = 2_000, trainerReview = BackupTrainerReview(words = "Invented."))),
            trainerReviewsWithoutWorkout = listOf(BackupTrainerReview(words = "Invented too.")),
            trainerPlans = listOf(BackupTrainerPlan(1, 0, "RUN", 30, "FRESH", "PUSH", null, "{}", "m")),
        )

        val counted = RestoreResult.inFile(file)

        assertThat(counted.workouts).isEqualTo(2)
        assertThat(counted.trainerReviews).isEqualTo(2)
        assertThat(counted.trainerPlans).isEqualTo(1)
    }

    /** D98: weekly plans are named when either side has some, after the trainer's plans and reviews. */
    @Test
    fun `the confirmation names weekly plans when either side has some`() {
        val text = BackupWording.confirmReplacing(
            here = RestoreResult(meals = 4, weights = 6, hasProfile = true, trainerPlans = 3, trainerReviews = 2, trainerProgrammes = 2),
            incoming = RestoreResult(meals = 4, weights = 5, hasProfile = true),
        )

        assertThat(text).contains("3 trainer plans, 2 session reviews and 2 weekly plans already on this phone")
        assertThat(text).contains("0 session reviews and 0 weekly plans from the file")
    }

    @Test
    fun `a phone holding only weekly plans still has something to lose`() {
        val text = BackupWording.confirmReplacing(
            here = RestoreResult(meals = 0, weights = 0, hasProfile = false, trainerProgrammes = 1),
            incoming = RestoreResult(meals = 4, weights = 5, hasProfile = true),
        )

        assertThat(text).contains("1 weekly plan")
        assertThat(text).doesNotContain("nothing here to lose")
    }
}
