package com.metaself.app.domain.trainer

import com.metaself.app.domain.day.DayTotals
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

    /**
     * D121: fill in [week] (from 0) of the running [plan], which has an outline — next week, or this week once begun
     * empty. [weekIndex] is the week the owner is in (−1 before the plan starts); [doneByWeek] and [howItWent] cover
     * each week begun before [week] (D105).
     */
    data class FillWeek(
        val ask: ProgrammeAsk,
        val startEpochDay: Long,
        val plan: WeeksPlan,
        val week: Int,
        val weekIndex: Int,
        val doneByWeek: List<Int>,
        val howItWent: List<WeekOutcome>,
    ) : TrainerQuestion {
        init {
            require(week in plan.weeks.indices) { "week must name one of the plan's weeks, not $week" }
            require(plan.outline != null) { "only a plan with an outline is filled in" }
        }
    }

    /** D121: a steady plan's next four weeks, from how its block went; [weekIndex] is its last week. */
    data class NextBlock(
        val ask: ProgrammeAsk,
        val startEpochDay: Long,
        val plan: WeeksPlan,
        val weekIndex: Int,
        val doneByWeek: List<Int>,
        val howItWent: List<WeekOutcome>,
    ) : TrainerQuestion {
        init {
            require(plan.outline != null) { "only a plan with an outline has blocks" }
        }
    }

    /**
     * D126: three goals fitted to the record. [perWeek] and [words] are what the form holds (either may be empty);
     * [startEpochDay] is the Monday a plan kept today would start on; [last] is how past planned weeks went (D105), as
     * the evaluate path sends it; [running] the running plan's goal and outline (D125), null when none runs.
     */
    data class SuggestGoals(
        val perWeek: Int?,
        val words: String,
        val startEpochDay: Long,
        val last: LastEvaluation?,
        val running: RunningGoal?,
    ) : TrainerQuestion

    /**
     * D147: a reply in the conversation. [conversation] is the last 14 days of it, word for word and oldest first, cut
     * to the newest 24,000 characters ([Conversation.window]) — the owner's messages, the trainer's replies, and the
     * feedback and letters in their place; [replyTo] is the owner's message being answered (the newest, unless it is
     * tried again after a later one); [plan] the running weekly plan, its week and its goal, null when none runs;
     * [weekNote] the coach's note on this week as the Trainer page shows it (D116), which Reply › answers, or null.
     */
    data class Talk(
        val conversation: List<ConversationLine>,
        val replyTo: ConversationLine,
        val plan: TalkPlan?,
        val weekNote: String? = null,
    ) : TrainerQuestion {
        init {
            require(replyTo.fromOwner && replyTo.kind == LineKind.MESSAGE) { "a reply answers one of the owner's messages" }
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
 * Everything one trainer request holds (D84, amended by D111) — built fresh from the stored record every
 * time; the only conversation it carries is a reply's question's (D147), never on any other path. There is deliberately no field for a meal, a food, a single
 * night's sleep or a single day's heart rate, a raw reading, a single weigh-in, a target weight, a name, or
 * an app or device name; a week's food and recovery go only as averages in its ledger (D110).
 * `TrainerRequestTest` fails if a field is added.
 *
 * @property weeks six week ledgers (D110), this week first and so far, then the five before it.
 * @property aboutMe the owner's standing note (D90), trimmed, sent unchanged; null when there is none.
 * @property months a line for each of up to twelve months before the 42 days (D89), oldest first.
 * @property sheet what the trainer knows about the owner (D149, D151): the newest version's current facts, null when
 *   there is no version or it holds none. Every request carries it.
 */
data class TrainerRequest(
    val question: TrainerQuestion,
    val today: Long,
    val aboutMe: String?,
    val sessions: List<SessionFacts>,
    val weeks: List<WeekLedger>,
    val months: List<MonthFacts>,
    val weight: WeightFacts?,
    val goal: GoalFacts?,
    val body: BodyFacts?,
    val thisWeek: Rhythm,
    val earlierFeedback: List<Feedback>,
    val sheet: List<SheetFact>? = null,
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

        /** The first day the six ledgers read: the 28 days before the first week, for recovery's usuals (D110). */
        fun firstLedgerDay(today: Long): Long = firstSummaryDay(today) - WeekLedgers.USUAL_DAYS

        /**
         * @param workouts any workouts; only the visible, counted ones of the 42 days are sent as
         *   sessions; those of the six weeks are placed in their ledgers (D110); those of the months before
         *   the 42 days are counted into the monthly lines (D89).
         * @param reviews any reviews; each is attached to its session.
         * @param plans the stored single-session plans the reviews name, by id.
         * @param days the daily summaries from [firstLedgerDay] (or [firstRecordDay], for the monthly
         *   lines) to [today].
         * @param food each day's food totals over the six weeks (D110); a meal is never read.
         * @param targetKcal today's calorie target, beside each week's food (D110); null with no profile.
         * @param weeklyPlans every kept version of a weekly plan, with its counting (D105): each week's practice.
         * @param earlierFeedback newest first; the first [FEEDBACK_COUNT] are sent.
         * @param earliestDay the first day the record holds anything; months before it have no line (D89).
         * @param aboutMe the owner's note (D90), as stored.
         * @param sheet the newest version of the sheet (D151), or null; only its current facts are sent.
         */
        fun of(
            question: TrainerQuestion,
            today: Long,
            workouts: List<Workout>,
            reviews: List<TrainerReview>,
            plans: Map<Long, TrainerPlan>,
            days: List<HealthDay>,
            food: Map<Long, DayTotals>,
            targetKcal: Int?,
            weeklyPlans: List<CountedPlan>,
            readings: List<WeightReading>,
            profile: Profile?,
            currentYear: Int,
            earlierFeedback: List<Feedback>,
            earliestDay: Long? = null,
            aboutMe: String? = null,
            sheet: SheetVersion? = null,
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
                WeekLedgers.of(thisMonday - 7L * back, today, workouts, days, food, targetKcal, weeklyPlans)
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
                // D87, unchanged: this week's visible, counted sessions so far, as the Movement screen counts them.
                thisWeek = Rhythm(
                    workouts.count { !it.hidden && it.counted && it.epochDay in thisMonday..today },
                    (thisMonday + 6 - today).toInt(),
                ),
                earlierFeedback = earlierFeedback.take(FEEDBACK_COUNT),
                sheet = sheet?.current?.takeIf { it.isNotEmpty() },
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

    /**
     * [request]'s question must be [TrainerQuestion.Adjust] (D97). The reply is this week and the weeks after — for a
     * plan with an outline, the weeks it fills and every week's target from this one (D121).
     */
    suspend fun adjust(request: TrainerRequest): TrainerReply<AdjustedRest>

    /** [request]'s question must be [TrainerQuestion.FillWeek] (D121). */
    suspend fun fill(request: TrainerRequest): TrainerReply<FilledWeek>

    /** [request]'s question must be [TrainerQuestion.NextBlock] (D121). */
    suspend fun nextBlock(request: TrainerRequest): TrainerReply<BlockAnswer>

    /** [request]'s question must be [TrainerQuestion.SuggestGoals] (D126). One to three suggestions, each checked. */
    suspend fun suggestGoals(request: TrainerRequest): TrainerReply<List<GoalSuggestion>>

    /** [request]'s question must be [TrainerQuestion.Talk] (D147). The reply's words, never blank. */
    suspend fun talk(request: TrainerRequest): TrainerReply<String>
}
