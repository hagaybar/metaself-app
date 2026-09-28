package com.metaself.app.data.trainer

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One suggestion (D86, D88). Every one that arrives is a row; at most one is [kept] (design question
 * 1). The form's answers are by name; [minutes] 60 means "60 min or more". [suggestion] is the answer
 * in the reply's own JSON shape (`TrainerResponse.encodePlan`).
 */
@Entity(tableName = "trainer_plans")
data class TrainerPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtMillis: Long,
    /** TREADMILL_WALK, OUTDOOR_WALK, RUN, SOMETHING_ELSE. */
    val activity: String,
    val minutes: Int,
    /** FRESH, NORMAL, TIRED. */
    val feeling: String,
    /** EASY, PUSH, NOT_SURE. */
    val wish: String,
    val words: String?,
    val suggestion: String,
    val model: String,
    val kept: Boolean,
)

/**
 * The owner's words on one session (D87, D88); unique per workout. **No foreign key** to `workouts`: a
 * sync that drops a session must not silently delete his words (design question 6). [felt] is EASY,
 * RIGHT or HARD, or null. [feedback] is `TrainerResponse.encodeFeedback`'s JSON, or null.
 */
@Entity(tableName = "trainer_reviews", indices = [Index(value = ["workoutId"], unique = true)])
data class TrainerReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val planId: Long?,
    val felt: String?,
    val words: String?,
    val feedback: String?,
    val feedbackAtMillis: Long?,
    val model: String?,
)
