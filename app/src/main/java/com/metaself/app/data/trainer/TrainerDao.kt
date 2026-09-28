package com.metaself.app.data.trainer

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.metaself.app.data.health.WorkoutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainerDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlan(plan: TrainerPlanEntity): Long

    @Query("SELECT * FROM trainer_plans WHERE id = :id")
    suspend fun plan(id: Long): TrainerPlanEntity?

    @Query("SELECT * FROM trainer_plans WHERE id IN (:ids)")
    suspend fun plans(ids: List<Long>): List<TrainerPlanEntity>

    @Query("SELECT * FROM trainer_plans WHERE kept = 1 ORDER BY createdAtMillis DESC LIMIT 1")
    fun observeKept(): Flow<TrainerPlanEntity?>

    @Query("SELECT * FROM trainer_plans WHERE kept = 1 ORDER BY createdAtMillis DESC LIMIT 1")
    suspend fun kept(): TrainerPlanEntity?

    @Query("UPDATE trainer_plans SET kept = 0 WHERE kept = 1")
    suspend fun unkeepAll()

    @Query("UPDATE trainer_plans SET kept = :kept WHERE id = :id")
    suspend fun setKept(id: Long, kept: Boolean)

    @Query("SELECT * FROM trainer_reviews WHERE workoutId = :workoutId")
    suspend fun reviewOf(workoutId: Long): TrainerReviewEntity?

    @Query("SELECT * FROM trainer_reviews ORDER BY id")
    fun observeReviews(): Flow<List<TrainerReviewEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReview(review: TrainerReviewEntity): Long

    @Update
    suspend fun updateReview(review: TrainerReviewEntity)

    /** The sessions that have a review, whatever their day; a review whose session is gone is left out. */
    @Query("SELECT w.* FROM workouts w JOIN trainer_reviews r ON r.workoutId = w.id ORDER BY w.startedAtMillis DESC")
    fun observeReviewedWorkouts(): Flow<List<WorkoutEntity>>

    /** Newest first, leaving out [exceptWorkoutId]'s own. */
    @Query(
        "SELECT feedback FROM trainer_reviews WHERE feedback IS NOT NULL AND workoutId != :exceptWorkoutId " +
            "ORDER BY feedbackAtMillis DESC LIMIT :count",
    )
    suspend fun latestFeedback(count: Int, exceptWorkoutId: Long): List<String>

    // The backup (D88).
    @Query("SELECT * FROM trainer_plans ORDER BY id")
    suspend fun allPlans(): List<TrainerPlanEntity>

    @Query("SELECT * FROM trainer_reviews ORDER BY id")
    suspend fun allReviews(): List<TrainerReviewEntity>

    @Query("DELETE FROM trainer_plans")
    suspend fun deletePlans()

    @Query("DELETE FROM trainer_reviews")
    suspend fun deleteReviews()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlans(plans: List<TrainerPlanEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReviews(reviews: List<TrainerReviewEntity>)
}
