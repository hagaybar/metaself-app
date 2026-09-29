package com.metaself.app.data.trainer

import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.weight.WeightRepository
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.LastEvaluation
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.domain.trainer.PlanConfirmation
import com.metaself.app.domain.trainer.PlanCounting
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlanProgress
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedTick
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeCalendar
import com.metaself.app.domain.trainer.Programmes
import com.metaself.app.domain.trainer.Trainer
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.WeeksPlan
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * The trainer's uses (D86, D87, D93, D97): every request is built here, fresh, from the stored record (a
 * year of it, for the monthly lines, D89) and the owner's note (D90), and only when a screen asks —
 * never in the background (D84). Writes throw — a weekly plan's store refuses a row in the wrong
 * state by throwing too, and nothing here swallows it; the screens catch them (`guarded`) and say so.
 */
class AskTheTrainer @Inject constructor(
    private val record: MovementRecord,
    private val store: TrainerStore,
    private val weights: WeightRepository,
    private val profiles: ProfileRepository,
    private val trainer: Trainer,
    private val aboutMe: AboutMeStore,
    private val programmes: ProgrammeStore,
    private val today: Today,
    private val now: Now,
    private val year: CurrentYear,
) {

    sealed interface Suggested {
        data class Planned(val plan: TrainerPlan) : Suggested
        data class Failed(val failure: EstimateResult) : Suggested
    }

    sealed interface Evaluated {
        data class Offered(val programme: Programme) : Evaluated
        data class Failed(val failure: EstimateResult) : Evaluated
    }

    sealed interface Adjusted {
        /** The new version, stored OFFERED, not yet kept (D97). */
        data class Offered(val programme: Programme) : Adjusted
        data class Failed(val failure: EstimateResult) : Adjusted

        /** No plan runs, or it has ended: nothing was asked. */
        data object NotRunning : Adjusted
    }

    sealed interface Reviewed {
        val review: TrainerReview

        data class Saved(override val review: TrainerReview) : Reviewed
        data class WithFeedback(override val review: TrainerReview) : Reviewed

        /** The words were saved; the call for feedback failed (D87). */
        data class NoFeedback(override val review: TrainerReview, val failure: EstimateResult) : Reviewed

        /**
         * The session left the record while the form was open (the band's app deleted it). The words
         * were saved anyway, as a review without a workout, which the backup keeps (D88); nothing was
         * asked, because there is no session to ask about.
         */
        data class SessionGone(override val review: TrainerReview) : Reviewed
    }

    suspend fun suggest(answers: PlanAnswers): Suggested =
        when (val reply = trainer.suggest(request(TrainerQuestion.Plan(answers, nextPlanned()), exceptWorkoutId = NO_WORKOUT))) {
            is TrainerReply.Failed -> Suggested.Failed(reply.failure)
            is TrainerReply.Answered -> {
                val plan = TrainerPlan(0, now(), answers, reply.value, reply.model, kept = false)
                Suggested.Planned(plan.copy(id = store.addPlan(plan)))
            }
        }

    suspend fun keep(planId: Long) = store.keep(planId)

    suspend fun save(workoutId: Long, felt: Felt?, words: String, planId: Long?, withFeedback: Boolean): Reviewed {
        val workout = store.workout(workoutId)
        val before = store.reviewOf(workoutId)
        val review = (before ?: TrainerReview(workoutId = workoutId, planId = null, felt = null, words = null))
            .copy(planId = planId, felt = felt, words = words.trim().ifEmpty { null })
        val saved = review.copy(id = store.putReview(review))
        if (workout == null) return Reviewed.SessionGone(saved)
        if (!withFeedback) return Reviewed.Saved(saved)

        val plan = planId?.let { store.plans(listOf(it))[it] }
        val question = TrainerRequest.reviewQuestion(workout, saved, plan).copy(planned = plannedTickOf(workout.id))
        return when (val reply = trainer.feedback(request(question, exceptWorkoutId = workoutId))) {
            is TrainerReply.Failed -> Reviewed.NoFeedback(saved, reply.failure)
            is TrainerReply.Answered -> {
                // With no plan sent there is nothing to have followed, whatever the model judged
                // (design question 9); a planId whose plan is gone sends none either.
                val feedback = if (plan == null) reply.value.copy(followed = PlanFollowed.NO_PLAN) else reply.value
                val answered = saved.copy(feedback = feedback, feedbackAtMillis = now(), model = reply.model)
                store.putReview(answered)
                if (planId != null) store.unkeep(planId)
                Reviewed.WithFeedback(answered)
            }
        }
    }

    /** D93: one ask; the answer is stored OFFERED. The plan would start on the Monday keeping it today gives (D94). */
    suspend fun evaluate(ask: ProgrammeAsk): Evaluated {
        val day = today().toEpochDay()
        val question = TrainerQuestion.Evaluate(ask, ProgrammeCalendar.startFor(day), lastEvaluation(day))
        return when (val reply = trainer.evaluate(request(question, exceptWorkoutId = NO_WORKOUT))) {
            is TrainerReply.Failed -> Evaluated.Failed(reply.failure)
            is TrainerReply.Answered -> {
                val programme = Programme(0, now(), ask, reply.value.evaluation, reply.value.plan, reply.model)
                Evaluated.Offered(programme.copy(id = programmes.add(programme)))
            }
        }
    }

    /**
     * D94: [id] runs from the Monday keeping it today gives, which is returned; a running plan is replaced.
     * Refused (throws) when [id] is not an offered answer.
     */
    suspend fun keepProgramme(id: Long): Long {
        val day = today().toEpochDay()
        val start = ProgrammeCalendar.startFor(day)
        programmes.keep(id, start, day)
        return start
    }

    /**
     * D97, D105: the running plan's rest, rewritten, told how each week so far went. The model returns this week's sessions still to do and
     * the weeks after; the phone composes the version — past weeks unchanged, this week's ticked sessions
     * first — and stores it OFFERED (design questions 4, 5).
     */
    suspend fun adjust(words: String): Adjusted {
        val running = running() ?: return Adjusted.NotRunning
        val programme = running.programme
        val start = requireNotNull(programme.startEpochDay)
        val index = running.weekIndex.coerceAtLeast(0)
        val week = running.progress.weeks[index]
        val ticked = week.ticks.filter { it.by != null }.map { it.planned }
        val question = TrainerQuestion.Adjust(
            ask = programme.ask,
            startEpochDay = start,
            plan = programme.plan,
            weekIndex = index,
            doneByWeek = running.progress.weeks.take(index).map { it.done },
            tickedThisWeek = ticked,
            thisWeekMax = week.planned - ticked.size,
            words = words.trim(),
            howItWent = running.progress.weeks.take(index + 1).map { it.outcome() },
        )
        return when (val reply = trainer.adjust(request(question, exceptWorkoutId = NO_WORKOUT))) {
            is TrainerReply.Failed -> Adjusted.Failed(reply.failure)
            is TrainerReply.Answered -> {
                val rest = reply.value
                val thisWeek = PlanWeek(rest.weeks.first().focus, ticked + rest.weeks.first().sessions)
                val composed = WeeksPlan(rest.title, programme.plan.weeks.take(index) + thisWeek + rest.weeks.drop(1), rest.why)
                val version = Programme(
                    id = 0, createdAtMillis = now(), ask = programme.ask.copy(words = words.trim()), evaluation = null,
                    plan = composed, model = reply.model, replacesId = programme.id,
                )
                Adjusted.Offered(version.copy(id = programmes.add(version)))
            }
        }
    }

    /** D97: Keep this version. Refused (throws) when the plan it adjusts no longer runs (design question 6). */
    suspend fun keepAdjusted(version: Programme) =
        programmes.keepAdjusted(version.id, requireNotNull(version.replacesId) { "not an adjusted version" }, today().toEpochDay())

    /** D97: Stop this plan. Refused (throws) when it no longer runs. */
    suspend fun stop(id: Long) = programmes.stop(id, today().toEpochDay())

    /** The running plan with its ticks, while it runs; null when none runs or it has ended. Reads only. */
    suspend fun running(): PlanCard.Running? = card() as? PlanCard.Running

    /**
     * D96: the ticks of the plan on the Trainer screen's card, running or ended — a session on the plan's
     * last Sunday, reviewed on the Monday after, still ticked its planned session. Reads only.
     */
    private suspend fun cardProgress(): PlanProgress? = when (val card = card()) {
        is PlanCard.Running -> card.progress
        is PlanCard.Ended -> card.progress
        PlanCard.None -> null
    }

    private suspend fun card(): PlanCard {
        val programme = programmes.running() ?: return PlanCard.None
        val start = programme.startEpochDay ?: return PlanCard.None
        val workouts = record.observeWorkouts(start, ProgrammeCalendar.lastDay(start, programme.ask.weeks)).first()
        return PlanCard.of(programme, workouts, today().toEpochDay(), counting(programme))
    }

    /** D105: a version counts from its chain's first keep, with the answers stored under that first version. */
    private suspend fun counting(programme: Programme): PlanCounting {
        val root = Programmes.rootOf(programme, programmes.all())
        return PlanCounting(root.id, root.createdAtMillis, programmes.confirmations(root.id))
    }

    /**
     * D105: the owner's answer to "count it for this?", stored under the chain's first version. Refused
     * (throws) unless [workoutId] is an open candidate in this week of the running plan — a stale screen
     * or a second tap after the first answer landed; the store keeps the first answer in any case.
     */
    suspend fun answerCandidate(workoutId: Long, confirmed: Boolean) {
        val running = checkNotNull(running()) { "no weekly plan is running" }
        val week = running.progress.weeks.getOrNull(running.weekIndex)
        check(week != null && week.ticks.any { it.candidate?.id == workoutId }) { "that session is not waiting for an answer" }
        val root = Programmes.rootOf(running.programme, programmes.all())
        programmes.confirm(PlanConfirmation(root.id, workoutId, confirmed, now()))
    }

    /** D96: the planned session [workoutId] ticked, which its feedback request sends. Reads only; nothing is sent. */
    suspend fun plannedTickOf(workoutId: Long): PlannedTick? = cardProgress()?.tickOf(workoutId)

    /** D96: the running plan's next session this week, for the plan form. Reads only; nothing is sent. */
    suspend fun nextPlanned(): PlannedTick? = running()?.next

    /** D98: the evaluation of the running or last plan — its own, or its chain's (design question 3). */
    suspend fun evaluationOf(programme: Programme): Evaluation? = Programmes.evaluationOf(programme, programmes.all())

    /** D93, D98: the last kept evaluation with the newest kept version of its plan and its done-counts. */
    private suspend fun lastEvaluation(day: Long): LastEvaluation? {
        val (evaluated, latest) = Programmes.lastEvaluated(programmes.all()) ?: return null
        val evaluation = evaluated.evaluation ?: return null
        val start = latest.startEpochDay ?: return null
        val until = Programmes.countedUntil(latest, day)
        val workouts = if (until < start) emptyList() else record.observeWorkouts(start, until).first()
        val progress = PlanProgress.of(latest.plan, start, workouts, until, counting(latest))
        val weeksBegun = (ProgrammeCalendar.weekIndex(start, until) + 1).coerceIn(0, latest.ask.weeks)
        val madeOn = Instant.ofEpochMilli(evaluated.createdAtMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        val begun = progress.weeks.take(weeksBegun)
        return LastEvaluation(madeOn, evaluation, latest.plan, begun.map { it.done }, begun.map { it.outcome() })
    }

    private suspend fun request(question: TrainerQuestion, exceptWorkoutId: Long): TrainerRequest {
        val day = today().toEpochDay()
        val reviews = store.observeReviews().first()
        // A year back for the monthly lines (D89); the 42 days and six weeks are within it.
        val first = TrainerRequest.firstRecordDay(day)
        return TrainerRequest.of(
            question = question,
            today = day,
            workouts = record.observeWorkouts(first, day).first(),
            reviews = reviews,
            plans = store.plans(reviews.mapNotNull { it.planId }),
            days = record.observeDays(minOf(first, TrainerRequest.firstSummaryDay(day)), day).first(),
            readings = weights.readings.first(),
            profile = profiles.profile.first(),
            currentYear = year(),
            earlierFeedback = store.latestFeedback(TrainerRequest.FEEDBACK_COUNT, exceptWorkoutId),
            earliestDay = record.observeEarliestDay().first(),
            aboutMe = aboutMe.note.first(),
        )
    }

    private companion object {
        /**
         * No workout's id, and no review's either: workouts are numbered from 1, and a review without
         * a workout is restored under -1, -2, … (D88), whose feedback must still count as earlier.
         */
        const val NO_WORKOUT = Long.MIN_VALUE
    }
}
