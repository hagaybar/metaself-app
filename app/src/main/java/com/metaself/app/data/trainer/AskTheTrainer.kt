package com.metaself.app.data.trainer

import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.letter.FoodTotals
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.weight.WeightRepository
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.target.CurrentTarget
import com.metaself.app.domain.trainer.CountedPlan
import com.metaself.app.domain.trainer.DatedEvaluation
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.LastEvaluation
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.domain.trainer.PlanConfirmation
import com.metaself.app.domain.trainer.PlanAheadAsk
import com.metaself.app.domain.trainer.PlanCounting
import com.metaself.app.domain.trainer.PlanFilling
import com.metaself.app.domain.trainer.PlanOffer
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlanMatch
import com.metaself.app.domain.trainer.PlanMonth
import com.metaself.app.domain.trainer.PlanPlace
import com.metaself.app.domain.trainer.PlanProgress
import com.metaself.app.domain.trainer.PlanShown
import com.metaself.app.domain.trainer.PlanTimeline
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedTick
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeCalendar
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.Programmes
import com.metaself.app.domain.trainer.SessionSlot
import com.metaself.app.domain.trainer.StepsCarry
import com.metaself.app.domain.trainer.Trainer
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.TrainerWeek
import com.metaself.app.domain.trainer.WeekNote
import com.metaself.app.domain.trainer.WeekLedgers
import com.metaself.app.domain.trainer.WeekNoteSource
import com.metaself.app.domain.trainer.WeeksPlan
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * The trainer's uses (D86, D87, D93, D97): every request is built here, fresh, from the stored record (a
 * year of it, for the monthly lines, D89), the owner's note (D90), each day's food totals and the kept
 * weekly plans (D110), and only when a screen asks — never in the background (D84). Writes throw — a weekly plan's store refuses a row in the wrong
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
    /** Each day's food totals (D110), the letter's reader; never a meal. Defaulted for tests that send no food. */
    private val food: FoodTotals = FoodTotals { _, _ -> emptyMap() },
    /** Where each answer's week_note is kept (D116). Defaulted for tests that do not look at it. */
    private val weekNotes: WeekNoteStore = WeekNoteStore.NONE,
) {

    sealed interface Suggested {
        data class Planned(val plan: TrainerPlan) : Suggested
        data class Failed(val failure: EstimateResult) : Suggested
    }

    /** D122: a planned session of the running plan, with its place — what a suggestion is asked and kept for. */
    data class PlannedFor(val slot: SessionSlot, val tick: PlannedTick)

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

    /** D121: a week filled in, or the next block, kept at once as the plan's next version (release 2's choice 5). */
    sealed interface Filled {
        /** [programme] is the version now running; [why] the trainer's sentence, "" when it said none. */
        data class Done(val programme: Programme, val why: String) : Filled
        data class Failed(val failure: EstimateResult) : Filled

        /** Nothing to fill in, or no next block to plan, today: nothing was asked. */
        data object NotOffered : Filled
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

    /**
     * D86, D122: one suggestion, stored not kept, for the planned session at [slot] (Get the steps, D123) — or, with
     * none named, the running plan's next open session (D96); kept, its steps belong to that session. A named place
     * no longer in the running plan, or no plan, leaves it for no planned session: kept, it is the Next session.
     */
    suspend fun suggest(answers: PlanAnswers, slot: SessionSlot? = null): Suggested {
        val planned = plannedFor(slot)
        return when (val reply = trainer.suggest(request(planQuestion(answers, planned)))) {
            is TrainerReply.Failed -> Suggested.Failed(reply.failure)
            is TrainerReply.Answered -> {
                keepNote(reply.weekNote)
                val plan = TrainerPlan(0, now(), answers, reply.value, reply.model, kept = false, slot = planned?.slot)
                Suggested.Planned(plan.copy(id = store.addPlan(plan)))
            }
        }
    }

    private suspend fun plannedFor(slot: SessionSlot?): PlannedFor? = if (slot == null) nextFor() else plannedAt(slot)

    /** D96, D122: the running plan's next open session this week, with its place; null when none. Reads only. */
    suspend fun nextFor(): PlannedFor? {
        val running = running() ?: return null
        val week = running.progress.weeks.getOrNull(running.weekIndex) ?: return null
        val at = week.ticks.indexOfFirst { it.by == null }.takeIf { it >= 0 } ?: return null
        val root = Programmes.rootOf(running.programme, programmes.all())
        return PlannedFor(SessionSlot(root.id, running.weekIndex, at), PlannedTick(running.weekIndex + 1, running.progress.weeks.size, week.ticks[at].planned))
    }

    /**
     * D123: the running plan's session at [slot], when the chain is the running one's and the week is this week or
     * next week (Get the steps is offered for no other); null otherwise. Done or open alike (D122). Reads only.
     */
    suspend fun plannedAt(slot: SessionSlot): PlannedFor? {
        val running = running() ?: return null
        val programme = running.programme
        val start = programme.startEpochDay ?: return null
        if (Programmes.rootOf(programme, programmes.all()).id != slot.chainId) return null
        val thisMonday = MovementWeek.mondayOf(today().toEpochDay())
        if (ProgrammeCalendar.monday(start, slot.week) !in listOf(thisMonday, thisMonday + 7)) return null
        val session = programme.plan.weeks.getOrNull(slot.week)?.sessions?.getOrNull(slot.session) ?: return null
        return PlannedFor(slot, PlannedTick(slot.week + 1, programme.plan.weeks.size, session))
    }

    /** D123: the running plan's session at [week] and [session] of its chain, as [plannedAt] reads it. Reads only. */
    suspend fun plannedAt(week: Int, session: Int): PlannedFor? {
        val running = running() ?: return null
        return plannedAt(SessionSlot(Programmes.rootOf(running.programme, programmes.all()).id, week, session))
    }

    /** D122: the kept suggestion for no planned session, made in the last seven days — the plan page's Next session. */
    suspend fun nextSession(): TrainerPlan? = PlanMatch.offered(store.keptPlans().firstOrNull { it.slot == null }, now())

    suspend fun keep(planId: Long) = store.keep(planId)

    suspend fun save(workoutId: Long, felt: Felt?, words: String, planId: Long?, withFeedback: Boolean): Reviewed {
        val workout = store.workout(workoutId)
        val before = store.reviewOf(workoutId)
        val review = (before ?: TrainerReview(workoutId = workoutId, planId = null, felt = null, words = null))
            .copy(planId = planId, felt = felt, words = words.trim().ifEmpty { null })
        val saved = review.copy(id = store.putReview(review))
        if (workout == null) return Reviewed.SessionGone(saved)
        if (!withFeedback) return Reviewed.Saved(saved)

        val question = feedbackQuestion(workout, saved)
        return when (val reply = trainer.feedback(request(question, exceptWorkoutId = workoutId))) {
            is TrainerReply.Failed -> Reviewed.NoFeedback(saved, reply.failure)
            is TrainerReply.Answered -> {
                keepNote(reply.weekNote)
                // With no plan sent there is nothing to have followed, whatever the model judged
                // (design question 9); a planId whose plan is gone sends none either.
                val feedback = if (question.session.plan == null) reply.value.copy(followed = PlanFollowed.NO_PLAN) else reply.value
                val answered = saved.copy(feedback = feedback, feedbackAtMillis = now(), model = reply.model)
                store.putReview(answered)
                // D122: steps kept for a planned session stay with it; only the unlinked one is used up.
                if (planId != null && store.plans(listOf(planId))[planId]?.slot == null) store.unkeep(planId)
                Reviewed.WithFeedback(answered)
            }
        }
    }

    /**
     * D124: Plan ahead — the goal, sessions a week and words; the weeks follow from the goal, counted from the Monday
     * keeping the answer today would give (next Monday while a plan runs). Stored OFFERED, with its goal.
     */
    suspend fun planAhead(ask: PlanAheadAsk): Evaluated = evaluate(ask.toAsk(planStart()))

    /** The Monday an answer kept today starts on: next Monday while a plan runs (D124), else D94's. Reads only. */
    private suspend fun planStart(): Long = replacingFrom() ?: ProgrammeCalendar.startFor(today().toEpochDay())

    /** D93: one ask; the answer is stored OFFERED. The plan would start on the Monday keeping it today gives (D94). */
    suspend fun evaluate(ask: ProgrammeAsk): Evaluated =
        when (val reply = trainer.evaluate(request(evaluateQuestion(ask)))) {
            is TrainerReply.Failed -> Evaluated.Failed(reply.failure)
            is TrainerReply.Answered -> {
                keepNote(reply.weekNote)
                val programme = Programme(0, now(), ask, reply.value.evaluation, reply.value.plan, reply.model)
                Evaluated.Offered(programme.copy(id = programmes.add(programme)))
            }
        }

    /**
     * D94, D124: [id] runs from the Monday keeping it today gives, which is returned — next Monday while a plan
     * runs ([replacingFrom]), which stops today, replaced. Refused (throws) when [id] is not an offered answer.
     */
    suspend fun keepProgramme(id: Long): Long {
        val day = today().toEpochDay()
        val start = replacingFrom() ?: ProgrammeCalendar.startFor(day)
        programmes.keep(id, start, day)
        return start
    }

    /** D124: while a plan runs (before its first week too), the Monday a new one would replace it from; else null. Reads only. */
    suspend fun replacingFrom(): Long? = running()?.let { MovementWeek.mondayOf(today().toEpochDay()) + 7 }

    /**
     * D97, D105: the running plan's rest, rewritten, told how each week so far went. The model returns this week's sessions still to do and
     * the weeks after; the phone composes the version — past weeks unchanged, this week's ticked sessions
     * first — and stores it OFFERED (design questions 4, 5).
     */
    suspend fun adjust(words: String): Adjusted {
        val running = adjustable() ?: return Adjusted.NotRunning
        val programme = running.programme
        val question = adjustQuestion(running, words)
        return when (val reply = trainer.adjust(request(question))) {
            is TrainerReply.Failed -> Adjusted.Failed(reply.failure)
            is TrainerReply.Answered -> {
                keepNote(reply.weekNote)
                val rest = reply.value.plan
                val thisWeek = PlanWeek(rest.weeks.first().focus, question.tickedThisWeek + rest.weeks.first().sessions)
                val rewritten = listOf(thisWeek) + rest.weeks.drop(1)
                val outline = programme.plan.outline
                // D121: with an outline, the filled weeks are rewritten and the later ones keep their (empty) place;
                // the targets from this week are the reply's, the blocks the plan's.
                val composed = if (outline == null) {
                    WeeksPlan(rest.title, programme.plan.weeks.take(question.weekIndex) + rewritten, rest.why)
                } else {
                    val weeks = programme.plan.weeks.take(question.weekIndex) + rewritten + programme.plan.weeks.drop(question.weekIndex + rewritten.size)
                    WeeksPlan(rest.title, weeks, rest.why, outline.withTargetsFrom(question.weekIndex, reply.value.targets))
                }
                val version = Programme(
                    id = 0, createdAtMillis = now(), ask = programme.ask.copy(words = question.words), evaluation = null,
                    plan = composed, model = reply.model, replacesId = programme.id,
                )
                Adjusted.Offered(version.copy(id = programmes.add(version)))
            }
        }
    }

    /**
     * D121: Fill in next week, or Plan this week — whichever [PlanFilling] offers today. The answer is kept at once as
     * the plan's next version (the filled week's sessions, its target as answered); the old version stops today,
     * adjusted, and kept steps follow their sessions. Only when tapped (D84).
     */
    suspend fun fillWeek(): Filled {
        val running = adjustable() ?: return Filled.NotOffered
        val question = fillQuestion(running) ?: return Filled.NotOffered
        return when (val reply = trainer.fill(request(question))) {
            is TrainerReply.Failed -> Filled.Failed(reply.failure)
            is TrainerReply.Answered -> {
                keepNote(reply.weekNote)
                val programme = running.programme
                val plan = programme.plan
                val weeks = plan.weeks.mapIndexed { index, week -> if (index == question.week) reply.value.week else week }
                val outline = requireNotNull(plan.outline).withTarget(question.week, reply.value.target)
                val version = keepVersion(programme, programme.ask, plan.copy(weeks = weeks, outline = outline), reply.model)
                Filled.Done(version, reply.value.why)
            }
        }
    }

    /**
     * D121: a steady plan's next block, in its last week: four more weeks, empty, and the block, kept at once as the
     * plan's next version. Only when tapped (D84).
     */
    suspend fun nextBlock(): Filled {
        val running = adjustable() ?: return Filled.NotOffered
        val question = nextBlockQuestion(running) ?: return Filled.NotOffered
        return when (val reply = trainer.nextBlock(request(question))) {
            is TrainerReply.Failed -> Filled.Failed(reply.failure)
            is TrainerReply.Answered -> {
                keepNote(reply.weekNote)
                val programme = running.programme
                val plan = programme.plan
                val block = reply.value.block
                val longer = plan.copy(
                    weeks = plan.weeks + List(block.weeks.size) { PlanWeek("", emptyList()) },
                    outline = requireNotNull(plan.outline) + block,
                )
                val version = keepVersion(programme, programme.ask.copy(weeks = programme.ask.weeks + block.weeks.size), longer, reply.model)
                Filled.Done(version, reply.value.why)
            }
        }
    }

    /** A new version of [programme], stored and kept at once from the chain's start (D97's machinery); it is returned. */
    private suspend fun keepVersion(programme: Programme, ask: ProgrammeAsk, plan: WeeksPlan, model: String): Programme {
        val version = Programme(0, now(), ask, null, plan, model, replacesId = programme.id)
        val stored = version.copy(id = programmes.add(version))
        keepAdjusted(stored)
        return stored.copy(status = ProgrammeStatus.RUNNING, startEpochDay = programme.startEpochDay)
    }

    /**
     * D116: an answer's week_note, about this week, kept before anything else is stored — so an answer the
     * owner discards later (Ask again, Keep the old one) still leaves it. A note is never worth failing a paid
     * answer for: a failed write is dropped.
     */
    private suspend fun keepNote(text: String?) {
        if (text == null) return
        val monday = MovementWeek.mondayOf(today().toEpochDay())
        runCatching { weekNotes.keep(WeekNote(text, now(), monday, WeekNoteSource.TRAINER)) }
    }

    /**
     * D97: Keep this version. Refused (throws) when the plan it adjusts no longer runs (design question 6). The steps
     * kept for the old version's sessions then follow them (release 1's limit 2).
     */
    suspend fun keepAdjusted(version: Programme) {
        val oldId = requireNotNull(version.replacesId) { "not an adjusted version" }
        val old = programmes.all().firstOrNull { it.id == oldId }
        programmes.keepAdjusted(version.id, oldId, today().toEpochDay())
        if (old != null) carrySteps(old, version)
    }

    /**
     * Release 1's limit 2: the steps kept for [old]'s planned sessions move to the same sessions in [new]
     * ([StepsCarry]); steps whose session [new] no longer has are no longer kept. After the keep, in writes of their
     * own: a failure leaves the steps where they were, and the version kept.
     */
    private suspend fun carrySteps(old: Programme, new: Programme) {
        val root = Programmes.rootOf(old, programmes.all())
        val kept = store.keptPlans().filter { it.slot?.chainId == root.id }
        val moves = StepsCarry.moves(old.plan, new.plan, kept.mapNotNull { it.slot?.place })
        kept.forEach { plan ->
            val slot = plan.slot ?: return@forEach
            when (val to = moves[slot.place]) {
                null -> store.unkeep(plan.id)
                slot.place -> Unit
                else -> store.moveSlot(plan.id, SessionSlot(root.id, to.week, to.session))
            }
        }
    }

    /** D97: Stop this plan. Refused (throws) when it no longer runs. */
    suspend fun stop(id: Long) = programmes.stop(id, today().toEpochDay())

    /**
     * The plan in effect with its ticks, while it runs — a plan replaced from next Monday until its Sunday (release 1's
     * limit 3); null when none runs or it has ended. Reads only.
     */
    suspend fun running(): PlanCard.Running? = card() as? PlanCard.Running

    /** The running plan Adjust may change: not one finishing its week before the plan that replaces it. */
    private suspend fun adjustable(): PlanCard.Running? = running()?.takeIf { it.replacedFrom == null }

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
        val programme = Programmes.inEffect(programmes.all(), today().toEpochDay()) ?: return PlanCard.None
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

    /**
     * Release 1's limit 1 (D87, D122): the suggestion a new review of [workout] is offered — the steps kept for the
     * planned session it ticked, when there are any; else the newest kept suggestion of the seven days before it.
     * Reads only.
     */
    suspend fun stepsFor(workout: Workout): TrainerPlan? {
        val (programme, progress) = when (val card = card()) {
            is PlanCard.Running -> card.programme to card.progress
            is PlanCard.Ended -> card.programme to card.progress
            PlanCard.None -> null to null
        }
        val place = progress?.placeOf(workout.id)
        if (programme != null && place != null) {
            val slot = SessionSlot(Programmes.rootOf(programme, programmes.all()).id, place.week, place.session)
            store.keptPlans().firstOrNull { it.slot == slot }?.let { return it }
        }
        return PlanMatch.forSession(store.keptPlan(), workout)
    }

    /** D96: the planned session [workoutId] ticked, which its feedback request sends. Reads only; nothing is sent. */
    suspend fun plannedTickOf(workoutId: Long): PlannedTick? = cardProgress()?.tickOf(workoutId)

    /** D96: the running plan's next session this week, for the plan form. Reads only; nothing is sent. */
    suspend fun nextPlanned(): PlannedTick? = running()?.next

    /**
     * D117: the plan "See the plan" draws — the running one; else one that ended, or was stopped, in the last
     * [ProgrammeCalendar.ENDED_SHOWN_DAYS] days; null when none. Reads only.
     */
    suspend fun planShown(): PlanShown? {
        val card = card()
        val programme = when (card) {
            is PlanCard.Running -> card.programme
            is PlanCard.Ended -> card.programme
            PlanCard.None -> recentlyStopped(today().toEpochDay())
        } ?: return null
        val start = programme.startEpochDay ?: return null
        val steps = stepsOf(programme)
        return PlanShown(
            timelineOf(programme, start).withSteps(steps.keys), card as? PlanCard.Running, datedEvaluationOf(programme), steps,
            programme.ask.goal, monthOf(programme, start),
        )
    }

    /** D123: [programme]'s Month from [start], its past weeks' minutes read from the record; null without an outline. Reads only. */
    private suspend fun monthOf(programme: Programme, start: Long): PlanMonth? {
        if (programme.plan.outline == null) return null
        val day = today().toEpochDay()
        val read = minOf(day, ProgrammeCalendar.lastDay(start, programme.plan.weeks.size))
        val workouts = if (read < start) emptyList() else record.observeWorkouts(start, read).first()
        return PlanMonth.of(programme, start, day, workouts)
    }

    /** D122: the steps kept for [programme]'s chain's planned sessions, by place. */
    private suspend fun stepsOf(programme: Programme): Map<PlanPlace, TrainerPlan> {
        val root = Programmes.rootOf(programme, programmes.all())
        return store.keptPlans().mapNotNull { plan -> plan.slot?.takeIf { it.chainId == root.id }?.let { it.place to plan } }
            .distinctBy { it.first }
            .toMap()
    }

    /** D118: an answer offered and not yet kept, drawn as it would be from [start], with its chain's evaluation. Reads only. */
    suspend fun planShownOf(offered: Programme, start: Long): PlanShown =
        PlanShown(timelineOf(offered, start), null, datedEvaluationOf(offered), goal = offered.ask.goal, month = monthOf(offered, start))

    private suspend fun recentlyStopped(day: Long): Programme? = programmes.all()
        .filter { programme ->
            val stopped = programme.stoppedEpochDay
            programme.status == ProgrammeStatus.STOPPED && programme.startEpochDay != null && stopped != null &&
                day - stopped in 0..ProgrammeCalendar.ENDED_SHOWN_DAYS.toLong()
        }
        .maxByOrNull { it.createdAtMillis }

    /**
     * D117, D118: [programme] drawn as its weeks from [start]. A kept version is counted as the Trainer page
     * counts it, to today or its stop day; an adjusted version not yet kept as its chain would count it (its
     * weeks already past are the running plan's, D97); an evaluation's answer not yet kept counts nothing. Each
     * week begun gets its ledger (D110), for the sessions of its own. Reads only.
     */
    suspend fun timelineOf(programme: Programme, start: Long): PlanTimeline {
        val day = today().toEpochDay()
        if (programme.startEpochDay == null && programme.replacesId == null) {
            return PlanTimeline.of(programme, start, day, null, emptyMap())
        }
        val read = minOf(day, ProgrammeCalendar.lastDay(start, programme.plan.weeks.size))
        val until = if (programme.startEpochDay != null) Programmes.countedUntil(programme, day) else read
        val workouts = if (read < start) emptyList() else record.observeWorkouts(start, read).first()
        val progress = PlanProgress.of(programme.plan, start, workouts, until, counting(programme))
        val plans = countedPlans()
        val ledgers = programme.plan.weeks.indices.map { ProgrammeCalendar.monday(start, it) }
            .filter { it <= read }
            .associateWith { WeekLedgers.of(it, day, workouts, emptyList(), emptyMap(), null, plans) }
        return PlanTimeline.of(programme, start, day, progress, ledgers)
    }

    /** D117: the evaluation [programme] carries, or its chain's, with the day it was made (D98). Reads only. */
    suspend fun datedEvaluationOf(programme: Programme): DatedEvaluation? {
        val evaluated = Programmes.evaluatedOf(programme, programmes.all()) ?: return null
        val evaluation = evaluated.evaluation ?: return null
        val made = Instant.ofEpochMilli(evaluated.createdAtMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        return DatedEvaluation(evaluation, made)
    }

    // The question builders. The asks above and the workbench (D106) build their questions here and only
    // here, so the workbench's request is the real one by construction. Each reads only.

    /** D86, D96: the plan form's answers, with the running plan's next session. */
    suspend fun planQuestion(answers: PlanAnswers): TrainerQuestion.Plan = planQuestion(answers, nextFor())

    /** D122: the plan form's answers, with the planned session they are for. */
    private fun planQuestion(answers: PlanAnswers, planned: PlannedFor?): TrainerQuestion.Plan = TrainerQuestion.Plan(answers, planned?.tick)

    /** D87, D96: [workout] with [review], the single-session plan it names, and the planned session it ticked. */
    suspend fun feedbackQuestion(workout: Workout, review: TrainerReview): TrainerQuestion.Review {
        val plan = review.planId?.let { store.plans(listOf(it))[it] }
        return TrainerRequest.reviewQuestion(workout, review, plan).copy(planned = plannedTickOf(workout.id))
    }

    /**
     * D93, D94, D98: the form, the Monday keeping it today gives — next Monday while a plan runs (D124) — and the last
     * kept evaluation.
     */
    suspend fun evaluateQuestion(ask: ProgrammeAsk): TrainerQuestion.Evaluate =
        TrainerQuestion.Evaluate(ask, planStart(), lastEvaluation(today().toEpochDay()))

    /** D124: Plan ahead's question, its weeks counted from where the plan would start. */
    suspend fun planAheadQuestion(ask: PlanAheadAsk): TrainerQuestion.Evaluate = evaluateQuestion(ask.toAsk(planStart()))

    /** D121: the week [PlanFilling] offers to fill in today, with how the weeks before it went; null when none. */
    suspend fun fillQuestion(): TrainerQuestion.FillWeek? = adjustable()?.let { fillQuestion(it) }

    private fun fillQuestion(running: PlanCard.Running): TrainerQuestion.FillWeek? {
        val week = when (val offer = PlanFilling.offer(running, today().toEpochDay())) {
            is PlanOffer.PlanThisWeek -> offer.week
            is PlanOffer.FillNextWeek -> offer.week
            PlanOffer.NextBlock, null -> return null
        }
        val programme = running.programme
        val begun = running.progress.weeks.take(week.coerceAtMost(running.weekIndex + 1).coerceAtLeast(0))
        return TrainerQuestion.FillWeek(
            programme.ask, requireNotNull(programme.startEpochDay), programme.plan, week, running.weekIndex,
            begun.map { it.done }, running.progress.weeks.take((running.weekIndex + 1).coerceAtLeast(0)).map { it.outcome() },
        )
    }

    /** D121: a steady plan's next block, from how its weeks went; null unless offered today. */
    suspend fun nextBlockQuestion(): TrainerQuestion.NextBlock? = adjustable()?.let { nextBlockQuestion(it) }

    private fun nextBlockQuestion(running: PlanCard.Running): TrainerQuestion.NextBlock? {
        if (PlanFilling.offer(running, today().toEpochDay()) != PlanOffer.NextBlock) return null
        val programme = running.programme
        val index = running.weekIndex
        return TrainerQuestion.NextBlock(
            programme.ask, requireNotNull(programme.startEpochDay), programme.plan, index,
            running.progress.weeks.take(index).map { it.done }, running.progress.weeks.take(index + 1).map { it.outcome() },
        )
    }

    /** D97, D105: the running plan's rest and how each week went; null when no plan runs. */
    suspend fun adjustQuestion(words: String): TrainerQuestion.Adjust? = adjustable()?.let { adjustQuestion(it, words) }

    private fun adjustQuestion(running: PlanCard.Running, words: String): TrainerQuestion.Adjust {
        val programme = running.programme
        val index = running.weekIndex.coerceAtLeast(0)
        val week = running.progress.weeks[index]
        val ticked = week.ticks.filter { it.by != null }.map { it.planned }
        return TrainerQuestion.Adjust(
            ask = programme.ask,
            startEpochDay = requireNotNull(programme.startEpochDay),
            plan = programme.plan,
            weekIndex = index,
            doneByWeek = running.progress.weeks.take(index).map { it.done },
            tickedThisWeek = ticked,
            thisWeekMax = week.planned - ticked.size,
            words = words.trim(),
            howItWent = running.progress.weeks.take(index + 1).map { it.outcome() },
        )
    }

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

    /** Every ask's request (D84), fresh from the stored record. Reads only; shared with the workbench (D106). */
    suspend fun request(question: TrainerQuestion, exceptWorkoutId: Long = NO_WORKOUT): TrainerRequest {
        val day = today().toEpochDay()
        val reviews = store.observeReviews().first()
        // A year back for the monthly lines (D89); the 42 days, the six weeks and the 28 days before them
        // for the usuals (D110) are within it — the minOf says so rather than assuming it.
        val first = minOf(TrainerRequest.firstRecordDay(day), TrainerRequest.firstLedgerDay(day))
        val profile = profiles.profile.first()
        return TrainerRequest.of(
            question = question,
            today = day,
            workouts = record.observeWorkouts(first, day).first(),
            reviews = reviews,
            plans = store.plans(reviews.mapNotNull { it.planId }),
            days = record.observeDays(first, day).first(),
            food = food.byDay(TrainerRequest.firstSummaryDay(day), day),
            targetKcal = targetKcal(),
            weeklyPlans = countedPlans(),
            readings = weights.readings.first(),
            profile = profile,
            currentYear = year(),
            earlierFeedback = store.latestFeedback(TrainerRequest.FEEDBACK_COUNT, exceptWorkoutId),
            earliestDay = record.observeEarliestDay().first(),
            aboutMe = aboutMe.note.first(),
        )
    }

    /**
     * D114: this week for the Trainer page — its ledger and strip, counted exactly as the trainer's request
     * counts this week's ledger — from [workouts] and [days] as the page observes them (this week's sessions,
     * and its days with the 28 before them). Reads each day's food, today's target and the kept plans; sends
     * nothing.
     */
    suspend fun thisWeek(day: Long, workouts: List<Workout>, days: List<HealthDay>): TrainerWeek {
        val monday = MovementWeek.mondayOf(day)
        return TrainerWeek.of(monday, day, workouts, days, food.byDay(monday, day), targetKcal(), countedPlans())
    }

    /** Today's calorie target (D110), the letter's; null with no profile. */
    private suspend fun targetKcal(): Int? = profiles.profile.first()?.let {
        CurrentTarget.of(it, profiles.revision.first(), year(), profiles.burnAdjustmentKcal.first()).kcal
    }

    /** D110: every kept version of a weekly plan, each counted from its chain's first keep (D105). */
    private suspend fun countedPlans(): List<CountedPlan> {
        val all = programmes.all()
        return all.filter { it.status != ProgrammeStatus.OFFERED && it.startEpochDay != null }.map { programme ->
            val root = Programmes.rootOf(programme, all)
            CountedPlan(programme, PlanCounting(root.id, root.createdAtMillis, programmes.confirmations(root.id)))
        }
    }

    companion object {
        /**
         * No workout's id, and no review's either: workouts are numbered from 1, and a review without
         * a workout is restored under -1, -2, … (D88), whose feedback must still count as earlier.
         */
        const val NO_WORKOUT = Long.MIN_VALUE
    }
}
