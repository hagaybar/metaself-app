package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFigureSource
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.profile.Profile
import com.metaself.app.domain.profile.Sex
import com.metaself.app.domain.profile.ageYears
import com.metaself.app.domain.weight.MeasuredRate
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.domain.weight.WeightTrend

/** Where a distance or steps figure came from, as the model is told it (no app or device name, D84). */
enum class Origin { SYNCED, FILE, TYPED }

/** What is being asked (D84's "the question"). */
sealed interface TrainerQuestion {
    /** [planned]: the running weekly plan's next session, which the suggestion is shaped around (D96). */
    data class Plan(val answers: PlanAnswers, val planned: PlannedTick? = null) : TrainerQuestion

    /**
     * [session] carries the felt effort, the words and the matched plan being asked about; [planned] is
     * the weekly plan's session it ticked, and its week (D96).
     */
    data class Review(val session: SessionFacts, val planned: PlannedTick? = null) : TrainerQuestion

    /**
     * D93: the form, the Monday the plan would start if kept now (D94), and the last kept evaluation
     * with how its plan went (D98), or null.
     */
    data class Evaluate(val ask: ProgrammeAsk, val startEpochDay: Long, val last: LastEvaluation?) : TrainerQuestion

    /**
     * D97: the running [plan], the week the owner is in ([weekIndex], from 0), the done-count of each week
     * before it, the planned sessions already ticked this week, how many this week may still hold
     * ([thisWeekMax]), the owner's words, and how each week up to and including this one went — each
     * planned session's outcome and the attempts ([howItWent], D105). Every count is the phone's (D95).
     */
    data class Adjust(
        val ask: ProgrammeAsk,
        val startEpochDay: Long,
        val plan: WeeksPlan,
        val weekIndex: Int,
        val doneByWeek: List<Int>,
        val tickedThisWeek: List<PlannedSession>,
        val thisWeekMax: Int,
        val words: String,
        val howItWent: List<WeekOutcome>,
    ) : TrainerQuestion {
        init {
            require(weekIndex in plan.weeks.indices) { "weekIndex must name one of the plan's weeks, not $weekIndex" }
            require(thisWeekMax >= 0) { "thisWeekMax cannot be negative, not $thisWeekMax" }
        }
    }
}

/** One session as D84 sends it. [title] and [origin] of the workout are deliberately absent. */
data class SessionFacts(
    val epochDay: Long,
    val kind: WorkoutKind,
    val minutes: Int,
    val distanceM: Int?,
    val distanceFrom: Origin?,
    val energyKcal: Int?,
    val energyFrom: EnergySource?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val zoneMinutes: List<Int>?,
    val zoneMaxEstimated: Boolean,
    val steps: Int?,
    val stepsFrom: Origin?,
    val felt: Felt?,
    val words: String?,
    val plan: SessionPlan?,
)

/** One Monday-based week's D74 figures. [current] is this week, so far. */
data class WeekFacts(
    val monday: Long,
    val distanceM: Int?,
    val averageActiveKcal: Int?,
    val sessions: Int,
    val current: Boolean,
)

/** The weight screen's figures: the smoothed line's last point and its measured weekly change. */
data class WeightFacts(val trendKg: Double, val asOfEpochDay: Long, val kgPerWeek: Double?, val overDays: Int?)

/** The profile's goal: direction and weekly rate. No target (D84 does not list one). */
data class GoalFacts(val direction: GoalDirection, val kgPerWeek: Double)

data class BodyFacts(val ageYears: Int, val sex: Sex, val heightCm: Int)

/**
 * This week's rhythm, counted on the phone and handed to the model (D87). [daysLeft] is the days after
 * today to Sunday, not counting today (0 on a Sunday); the prompt names it `days_left_after_today`.
 */
data class Rhythm(val sessionsSoFar: Int, val daysLeft: Int)

/**
 * Everything one trainer request holds (D84) — built fresh from the stored record every time; no
 * conversation is kept or replayed. There is deliberately no field for a meal, sleep, a raw reading,
 * a single weigh-in, a target weight, a name, or an app or device name; `TrainerRequestTest` fails if
 * one is added.
 *
 * @property aboutMe the owner's standing note (D90), trimmed, sent unchanged; null when there is none.
 * @property months a line for each of up to twelve months before the 42 days (D89), oldest first.
 */
data class TrainerRequest(
    val question: TrainerQuestion,
    val today: Long,
    val aboutMe: String?,
    val sessions: List<SessionFacts>,
    val weeks: List<WeekFacts>,
    val months: List<MonthFacts>,
    val weight: WeightFacts?,
    val goal: GoalFacts?,
    val body: BodyFacts?,
    val thisWeek: Rhythm,
    val earlierFeedback: List<Feedback>,
) {
    companion object {
        const val SESSION_DAYS = 42
        const val WEEKS = 6
        const val FEEDBACK_COUNT = 3

        /** The first day whose sessions go (42 days ending today). */
        fun firstDay(today: Long): Long = today - (SESSION_DAYS - 1)

        /** The first day whose summaries the six weeks need: the Monday five weeks before this one. */
        fun firstSummaryDay(today: Long): Long = MovementWeek.mondayOf(today) - 7L * (WEEKS - 1)

        /** The first day the monthly lines (D89) can need: the first of the month a year before this one. */
        fun firstRecordDay(today: Long): Long = MonthlyLines.firstDay(today)

        /**
         * @param workouts any workouts; only the visible, counted ones of the 42 days are sent as
         *   sessions, and those of the months before them are counted into the monthly lines (D89).
         * @param reviews any reviews; each is attached to its session.
         * @param plans the stored plans the reviews name, by id.
         * @param days the daily summaries from [firstSummaryDay] (or [firstRecordDay], for the monthly
         *   lines) to [today]; meals are never read.
         * @param earlierFeedback newest first; the first [FEEDBACK_COUNT] are sent.
         * @param earliestDay the first day the record holds anything; months before it have no line (D89).
         * @param aboutMe the owner's note (D90), as stored.
         */
        fun of(
            question: TrainerQuestion,
            today: Long,
            workouts: List<Workout>,
            reviews: List<TrainerReview>,
            plans: Map<Long, TrainerPlan>,
            days: List<HealthDay>,
            readings: List<WeightReading>,
            profile: Profile?,
            currentYear: Int,
            earlierFeedback: List<Feedback>,
            earliestDay: Long? = null,
            aboutMe: String? = null,
        ): TrainerRequest {
            val first = firstDay(today)
            // D92: a combined session's review may sit on another of its witnesses.
            val byWorkout = SessionReviews.bySession(workouts, reviews)
            val sessions = workouts
                .filter { !it.hidden && it.counted && it.epochDay in first..today }
                .sortedBy { it.startedAtMillis }
                .map { workout ->
                    val review = byWorkout[workout.id]
                    session(workout, review, review?.planId?.let(plans::get)?.plan)
                }
            val thisMonday = MovementWeek.mondayOf(today)
            val weeks = (0 until WEEKS).map { back ->
                val week = MovementWeek.of(today, days, workouts, emptyMap(), thisMonday - 7L * back)
                WeekFacts(week.monday, week.distanceM, week.averageActiveKcal, week.workoutCount + week.walkCount, back == 0)
            }
            val trend = WeightTrend.of(readings)
            val rate = MeasuredRate.of(trend, today)
            return TrainerRequest(
                question = question,
                today = today,
                aboutMe = aboutMe?.trim()?.takeIf { it.isNotEmpty() },
                sessions = sessions,
                weeks = weeks,
                months = MonthlyLines.of(today, earliestDay, workouts, reviews, days, trend),
                weight = trend.lastOrNull()?.let { WeightFacts(it.trendKg, it.reading.epochDay, rate?.kgPerWeek, rate?.spanDays) },
                goal = profile?.let { GoalFacts(it.goal.direction, it.goal.kgPerWeek) },
                body = profile?.let { BodyFacts(it.ageYears(currentYear), it.sex, it.heightCm) },
                thisWeek = Rhythm(weeks.first().sessions, (thisMonday + 6 - today).toInt()),
                earlierFeedback = earlierFeedback.take(FEEDBACK_COUNT),
            )
        }

        /** The question for D87: [workout] with the review and plan being asked about. */
        fun reviewQuestion(workout: Workout, review: TrainerReview, plan: TrainerPlan?): TrainerQuestion.Review =
            TrainerQuestion.Review(session(workout, review, plan?.plan))

        internal fun session(workout: Workout, review: TrainerReview?, plan: SessionPlan?): SessionFacts = SessionFacts(
            epochDay = workout.epochDay,
            kind = workout.kind,
            minutes = workout.durationMinutes,
            distanceM = workout.distanceM,
            distanceFrom = workout.distanceM?.let {
                when (workout.distanceSource) {
                    WorkoutFigureSource.FILE -> Origin.FILE
                    WorkoutFigureSource.TYPED -> Origin.TYPED
                    null -> if (workout.source == WorkoutSource.TYPED) Origin.TYPED else Origin.SYNCED
                }
            },
            energyKcal = workout.energyKcal,
            energyFrom = workout.energyKcal?.let { workout.energySource },
            avgHeartRate = workout.avgHeartRate,
            maxHeartRate = workout.maxHeartRate,
            // Rounded to the nearest minute; a zone under half a minute reads 0.
            zoneMinutes = workout.zoneSeconds?.map { (it + 30) / 60 },
            zoneMaxEstimated = workout.zoneMaxSource != "OBSERVED",
            steps = workout.steps,
            stepsFrom = workout.steps?.let { if (workout.stepsSource == WorkoutFigureSource.TYPED) Origin.TYPED else Origin.FILE },
            felt = review?.felt,
            words = review?.words?.takeIf { it.isNotBlank() },
            plan = plan,
        )
    }
}

/**
 * Asking the model (D84): one ask each, nothing in its vocabulary that names a vendor. An
 * implementation may resend on a rejected parameter (D57); it never retries a failed answer.
 */
interface Trainer {
    /** [request]'s question must be [TrainerQuestion.Plan]. */
    suspend fun suggest(request: TrainerRequest): TrainerReply<SessionPlan>

    /** [request]'s question must be [TrainerQuestion.Review]. */
    suspend fun feedback(request: TrainerRequest): TrainerReply<Feedback>

    /** [request]'s question must be [TrainerQuestion.Evaluate] (D93). */
    suspend fun evaluate(request: TrainerRequest): TrainerReply<EvaluationAndPlan>

    /** [request]'s question must be [TrainerQuestion.Adjust] (D97). The reply is this week and the weeks after. */
    suspend fun adjust(request: TrainerRequest): TrainerReply<WeeksPlan>
}
