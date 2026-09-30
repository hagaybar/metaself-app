package com.metaself.app.domain.trainer

import com.metaself.app.domain.ai.EstimateResult

/** D86's first row. Stored by name, never by ordinal. */
enum class PlanActivity { TREADMILL_WALK, OUTDOOR_WALK, RUN, SOMETHING_ELSE }

/** D86's second row. The last is "60 min or more", stored as 60. */
enum class TimeAvailable(val minutes: Int) {
    MIN_20(20), MIN_30(30), MIN_45(45), MIN_60_OR_MORE(60);

    val orMore: Boolean get() = this == MIN_60_OR_MORE

    companion object {
        fun ofMinutes(minutes: Int): TimeAvailable? = entries.firstOrNull { it.minutes == minutes }
    }
}

/** D86's third row. */
enum class Feeling { FRESH, NORMAL, TIRED }

/** D86's fourth row: "Easy · A push · Not sure". */
enum class Wish { EASY, PUSH, NOT_SURE }

/** The form (D86), all four rows answered. [words] is "" for none. */
data class PlanAnswers(
    val activity: PlanActivity,
    val time: TimeAvailable,
    val feeling: Feeling,
    val wish: Wish,
    val words: String = "",
)

/** One step of a suggestion: a minute range, what, and how (speed, incline or zone where they apply; may be ""). */
data class PlanStep(val fromMinute: Int, val toMinute: Int, val what: String, val how: String)

/** A suggestion as the model gave it (D86): advice, never a measurement (D4). */
data class SessionPlan(val title: String, val steps: List<PlanStep>, val why: String)

/** A stored suggestion (D88). At most one is [kept]. */
data class TrainerPlan(
    val id: Long,
    val createdAtMillis: Long,
    val answers: PlanAnswers,
    val plan: SessionPlan,
    val model: String,
    val kept: Boolean,
)

/** "How it felt" (D87). Stored with the review, never over the workout's own `effort` (D77). */
enum class Felt { EASY, RIGHT, HARD }

/** Whether the session followed its plan, as the trainer judged it (design question 9). */
enum class PlanFollowed { YES, PARTLY, NO, NO_PLAN }

/**
 * Feedback: a headline and a coach's [note] (D108), or — stored before D108 — a headline and four short
 * parts (D87), which keep their shape. A feedback is new-shaped when it carries a note; its four parts are
 * then empty. Advice, never a measurement (D4).
 */
data class Feedback(
    val headline: String,
    val againstPlan: String,
    val numbers: String,
    val nextTime: String,
    val thisWeek: String,
    val followed: PlanFollowed,
    val note: String? = null,
)

/** The owner's words on one session (D87, D88). One per workout. */
data class TrainerReview(
    val id: Long = 0,
    val workoutId: Long,
    val planId: Long?,
    val felt: Felt?,
    val words: String?,
    val feedback: Feedback? = null,
    val feedbackAtMillis: Long? = null,
    val model: String? = null,
)

/**
 * What one trainer call came to: the answer and the model that gave it, or one of the call's failures
 * — [EstimateResult]'s closed set, so the screens word them as the meal estimator does (D86).
 */
sealed interface TrainerReply<out T> {
    data class Answered<T>(val value: T, val model: String) : TrainerReply<T>

    data class Failed(val failure: EstimateResult) : TrainerReply<Nothing> {
        init {
            require(failure !is EstimateResult.Proposed && failure !is EstimateResult.AmountMissing) {
                "a trainer call fails for want of a key, allowance, network, permission or sense; not $failure"
            }
        }
    }
}
