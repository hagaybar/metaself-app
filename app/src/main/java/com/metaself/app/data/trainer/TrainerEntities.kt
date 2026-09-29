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

/**
 * One evaluation with its plan, or an adjusted version of a plan (D98). Every answer that arrives is a
 * row, kept or not. [evaluation] and [plan] are `TrainerResponse.encodeEvaluation` / `encodeWeeksPlan`'s
 * JSON; [status] is a `ProgrammeStatus` name; [stoppedEpochDay] is the day it stopped running, whatever
 * stopped it. **No foreign key**: [replacesId] names another row of this table, and a restore replaces
 * the table whole.
 */
@Entity(tableName = "trainer_programmes")
data class TrainerProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtMillis: Long,
    val weeks: Int,
    val perWeek: Int,
    val words: String?,
    val evaluation: String?,
    val plan: String,
    val model: String,
    val startEpochDay: Long?,
    val status: String,
    val stoppedEpochDay: Long?,
    val replacesId: Long?,
)

/**
 * The owner's answer to "count it for this?" about one session and a weekly plan (D105): [programmeId] is
 * always the **first** version of an adjusted chain, so an answer carries to every later version and
 * nothing is asked twice. One per pair (the key). **No foreign key**, as for a review: a sync that drops a
 * session must not cascade, and a restore replaces the table whole.
 */
@Entity(tableName = "plan_confirmations", primaryKeys = ["programmeId", "workoutId"])
data class PlanConfirmationEntity(
    val programmeId: Long,
    val workoutId: Long,
    val confirmed: Boolean,
    val answeredAtMillis: Long,
)

/**
 * One weekly letter (D104): its week (unique — one letter a week), the figures and the texts as JSON,
 * the model, whether the band's data may be behind, and when it was read.
 */
@Entity(tableName = "weekly_letters", indices = [Index(value = ["weekMonday"], unique = true)])
data class WeeklyLetterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekMonday: Long,
    val createdAtMillis: Long,
    val figures: String,
    val letter: String,
    val model: String,
    val bandDataUntil: Long?,
    val readAtMillis: Long?,
)
