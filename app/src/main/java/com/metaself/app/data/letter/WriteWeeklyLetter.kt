package com.metaself.app.data.letter

import com.metaself.app.data.health.BackgroundHealthCopy
import com.metaself.app.data.health.BackgroundHealthRead
import com.metaself.app.data.health.HealthRecordStatus
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.trainer.AboutMeStore
import com.metaself.app.data.trainer.ProgrammeStore
import com.metaself.app.data.trainer.SheetStore
import com.metaself.app.data.trainer.newest
import com.metaself.app.data.trainer.TrainerReviews
import com.metaself.app.data.trainer.WeekNoteStore
import com.metaself.app.data.weight.WeightRepository
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.letter.LetterDays
import com.metaself.app.domain.letter.LetterFigures
import com.metaself.app.domain.letter.LetterPatterns
import com.metaself.app.domain.letter.LetterReply
import com.metaself.app.domain.letter.LetterRequest
import com.metaself.app.domain.letter.LetterWriter
import com.metaself.app.domain.letter.PastLetter
import com.metaself.app.domain.letter.PlanRequestWords
import com.metaself.app.domain.letter.PlanWeekFigures
import com.metaself.app.domain.letter.SessionWords
import com.metaself.app.domain.letter.WeekFigures
import com.metaself.app.domain.letter.WeeklyLetter
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.WeekSessions
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.profile.ageYears
import com.metaself.app.domain.target.CurrentTarget
import com.metaself.app.domain.trainer.BodyFacts
import com.metaself.app.domain.trainer.CountedPlan
import com.metaself.app.domain.trainer.GoalFacts
import com.metaself.app.domain.trainer.PlanCounting
import com.metaself.app.domain.trainer.PlanProgress
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeCalendar
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.Programmes
import com.metaself.app.domain.trainer.SessionReviews
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.WeekNote
import com.metaself.app.domain.trainer.WeekNoteSource
import com.metaself.app.domain.trainer.WeekLedgers
import com.metaself.app.domain.weight.WeightTrend
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * What Write it now needs of the letter's job (design question 19): [WriteWeeklyLetter] is the one; an
 * interface so the screen's test can stand in for it.
 */
interface WeeklyLetterJob {
    suspend fun write(weekMonday: Long, copy: Boolean): WriteWeeklyLetter.Outcome
    suspend fun wanted(weekMonday: Long): Boolean

    /** D144: [weekMonday]'s letter asked for again and, when written, put in place of the stored one. */
    suspend fun writeAgain(weekMonday: Long): WriteWeeklyLetter.Outcome
}

/**
 * One weekly letter (D99–D102), from the record: copy the band's data if allowed, count the week and the
 * four before it, ask once, store. Used by the worker (Sunday) and by Write it now (a tap). Never throws
 * for a failed ask — it says what happened; a failed read or write throws, and the caller logs it by kind.
 *
 * Nothing here logs: the writer logs a failed ask by its kind, and a failure's details — an
 * [EstimateResult.Unreadable]'s answer above all — never leave the [Outcome].
 */
class WriteWeeklyLetter @Inject constructor(
    private val food: FoodTotals,
    private val weights: WeightRepository,
    private val record: MovementRecord,
    private val reviews: TrainerReviews,
    private val programmes: ProgrammeStore,
    private val profiles: ProfileRepository,
    private val aboutMe: AboutMeStore,
    private val writer: LetterWriter,
    private val letters: LetterStore,
    private val backgroundRead: BackgroundHealthRead,
    private val backgroundCopy: BackgroundHealthCopy,
    private val status: HealthRecordStatus,
    private val now: Now,
    private val year: CurrentYear,
    /** Told when a letter is stored, so a "couldn't be written" notification still showing is taken down. */
    private val notifier: LetterNotifier = LetterNotifier.NONE,
    /** Where the letter's week_note is kept (D116), about the letter's week. */
    private val weekNotes: WeekNoteStore = WeekNoteStore.NONE,
    /** D151: the letter carries the newest sheet, as every trainer request does. */
    private val sheets: SheetStore = SheetStore.NONE,
) : WeeklyLetterJob {
    sealed interface Outcome {
        data object Quiet : Outcome
        data object AlreadyWritten : Outcome
        data class Written(val letter: WeeklyLetter) : Outcome
        /** A failure worth trying again later (network, a provider error — 5xx or 429 —, an unreadable answer). */
        data class Retry(val failure: EstimateResult) : Outcome
        /** A failure no retry mends: no key, a refusal other than a provider error, the day's ceiling. */
        data class GiveUp(val failure: EstimateResult) : Outcome
    }

    companion object {
        /**
         * D99: no network, a provider error (a 5xx, or 429 — too many requests) or an unreadable answer is
         * retried until Monday noon; a missing key, the day's ceiling, or any other refusal (a bad key, no
         * credit, a request refused) no retry mends.
         */
        fun failed(reply: LetterReply.Failed): Outcome = when (val failure = reply.failure) {
            is EstimateResult.NoKey, is EstimateResult.CeilingReached -> Outcome.GiveUp(failure)
            is EstimateResult.Refused -> if (reply.status.retryable()) Outcome.Retry(failure) else Outcome.GiveUp(failure)
            else -> Outcome.Retry(failure)
        }

        private const val TOO_MANY_REQUESTS = 429

        private fun Int?.retryable(): Boolean = this != null && (this == TOO_MANY_REQUESTS || this in 500..599)
    }

    override suspend fun write(weekMonday: Long, copy: Boolean): Outcome = invoke(weekMonday, copy)

    /** [copy] false for Write it now: the app is in front and its own copy runs anyway (design question 19). */
    suspend operator fun invoke(weekMonday: Long, copy: Boolean = true): Outcome {
        if (letters.of(weekMonday) != null) return Outcome.AlreadyWritten
        val copied = copy && backgroundRead.granted() && backgroundCopy.copyInBackground()
        val bandDataUntil = if (copied) null else status.current().lastCopiedMillis
        // The copy can take a while; a letter written meanwhile (Write it now) is not asked for twice.
        if (letters.of(weekMonday) != null) return Outcome.AlreadyWritten
        return ask(weekMonday, bandDataUntil, again = false)
    }

    /**
     * D144: the week's letter asked for again — the Sunday letter's own request, its figures recounted now,
     * the band's data not copied first (as Write it now). Written, it replaces the stored letter, already
     * read, and nothing is notified; otherwise the stored letter is left as it was. Only a tap calls this.
     */
    override suspend fun writeAgain(weekMonday: Long): Outcome =
        ask(weekMonday, status.current().lastCopiedMillis, again = true)

    private suspend fun ask(weekMonday: Long, bandDataUntil: Long?, again: Boolean): Outcome {
        val sunday = weekMonday + 6
        val counted = count(weekMonday)
        val figuresWeek = counted.week(weekMonday)
        if (figuresWeek.quiet) return Outcome.Quiet
        val workouts = counted.workouts

        val profile = profiles.profile.first()
        val target = profile?.let {
            CurrentTarget.of(it, profiles.revision.first(), year(), profiles.burnAdjustmentKcal.first()).kcal
        }
        val figures = LetterFigures(figuresWeek, (1..LetterFigures.WEEKS_BEFORE).map { counted.week(weekMonday - 7L * it) }, target)
        val running = CountedPlan.covering(counted.kept, weekMonday)?.programme
        // D141: the 35 day rows, and D142's patterns over them.
        val dayRows = LetterDays.of(sunday, workouts, counted.kept, counted.reviews, counted.days, counted.food, record.observeEarliestMovementDay().first())
        val weekSessions = WeekSessions.between(workouts, weekMonday, sunday)
        val reviewed = SessionReviews.bySession(weekSessions, counted.reviews)
        val request = LetterRequest(
            figures = figures,
            // D110, D111: the week's ledger, counted to its Sunday.
            ledger = WeekLedgers.of(weekMonday, sunday, workouts, counted.days, counted.food, target, counted.kept),
            sessions = weekSessions.map { TrainerRequest.session(it, review = null, plan = null) },
            aboutMe = aboutMe.note.first().trim().ifEmpty { null },
            goal = profile?.let { GoalFacts(it.goal.direction, it.goal.kgPerWeek) },
            body = profile?.let { BodyFacts(it.ageYears(year()), it.sex, it.heightCm) },
            planTitle = running?.plan?.title,
            planWeek = running?.let { p -> p.plan.weeks.getOrNull(ProgrammeCalendar.weekIndex(p.startEpochDay!!, weekMonday)) },
            days = dayRows,
            patterns = LetterPatterns.of(dayRows, weekMonday),
            // D141: this week's answers to "How did it go?" that have words.
            sessionWords = weekSessions.mapNotNull { session ->
                reviewed[session.id]?.words?.trim()?.ifEmpty { null }?.let { SessionWords(session.epochDay, session.kind, reviewed[session.id]?.felt, it) }
            },
            planWords = planWords(counted.programmes, weekMonday - WeekLedgers.USUAL_DAYS, sunday),
            planGoal = running?.ask?.goal,
            goalView = running?.let { Programmes.rootOf(it, counted.programmes).goalView ?: it.goalView },
            lastLetters = letters.observeAll().first()
                .filter { it.weekMonday < weekMonday }
                .sortedByDescending { it.weekMonday }
                .take(LetterRequest.LAST_LETTERS)
                .map { PastLetter(it.weekMonday, it.texts.lookAfter, it.texts.nextWeek) },
            sheet = sheets.newest()?.current?.takeIf { it.isNotEmpty() },
        )
        return when (val reply = writer.write(request)) {
            is LetterReply.Failed -> failed(reply)
            is LetterReply.Written -> {
                val letter = WeeklyLetter(
                    weekMonday = weekMonday, createdAtMillis = now(), figures = figures, texts = reply.texts,
                    model = reply.model, bandDataUntil = bandDataUntil,
                )
                if (again) {
                    // D144: asked for on the letter's own page, so it is read as it arrives.
                    letters.replace(letter.copy(readAtMillis = now()))
                } else {
                    letters.add(letter)
                    // A notification is never worth failing a stored letter for.
                    runCatching { notifier.letterWritten() }
                }
                // Nor is the week's note (D116).
                reply.weekNote?.let { runCatching { weekNotes.keep(WeekNote(it, now(), weekMonday, WeekNoteSource.LETTER)) } }
                Outcome.Written(letter)
            }
        }
    }

    /**
     * Whether [weekMonday]'s week still wants a letter: it has none and is not quiet. Reads only — no copy,
     * no ask. For a run that comes too late to write (design question 6) and for Write it now.
     */
    override suspend fun wanted(weekMonday: Long): Boolean =
        letters.of(weekMonday) == null && !count(weekMonday).week(weekMonday).quiet

    /** The record over [weekMonday]'s week and the four before it, read once. */
    private class Counted(
        val workouts: List<Workout>,
        val days: List<HealthDay>,
        val food: Map<Long, DayTotals>,
        val kept: List<CountedPlan>,
        val reviews: List<TrainerReview>,
        val programmes: List<Programme>,
        val week: (Long) -> WeekFigures,
    )

    private suspend fun count(weekMonday: Long): Counted {
        val sunday = weekMonday + 6
        // The four earlier weeks (D100) and the ledger's 28 days of usuals (D110) — the same span today.
        val first = minOf(weekMonday - 7L * LetterFigures.WEEKS_BEFORE, weekMonday - WeekLedgers.USUAL_DAYS)
        val foodByDay = food.byDay(first, sunday)
        val trend = WeightTrend.of(weights.readings.first())
        val workouts = record.observeWorkouts(first, sunday).first()
        val days = record.observeDays(first, sunday).first()
        val allReviews = reviews.observeReviews().first()
        val all = programmes.all()
        val kept = all.filter { it.status != ProgrammeStatus.OFFERED && it.startEpochDay != null }
            .map { CountedPlan(it, counting(it, all)) }
        return Counted(workouts, days, foodByDay, kept, allReviews, all) { monday ->
            WeekFigures.of(monday, foodByDay, trend, workouts, allReviews, days, planWeek(kept, workouts, monday))
        }
    }

    /**
     * D141: the words given with a plan request or an adjustment made [from] to [to], oldest first; the same
     * words carried into a later version are sent once.
     */
    private fun planWords(all: List<Programme>, from: Long, to: Long): List<PlanRequestWords> = all
        .sortedBy { it.createdAtMillis }
        .mapNotNull { p ->
            val day = Instant.ofEpochMilli(p.createdAtMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
            p.ask.words.trim().ifEmpty { null }?.takeIf { day in from..to }?.let { PlanRequestWords(day, p.replacesId != null, it) }
        }
        .distinctBy { it.words }

    /** D105: a version counts from its chain's first keep, with the answers stored under that first version. */
    private suspend fun counting(programme: Programme, all: List<Programme>): PlanCounting {
        val root = Programmes.rootOf(programme, all)
        return PlanCounting(root.id, root.createdAtMillis, programmes.confirmations(root.id))
    }

    /**
     * Its week's planned and done; done counts only ticked sessions — full, or confirmed short (D105). The
     * plan is [CountedPlan.covering]'s: design question 13's latest-started, and of two with one start, the
     * one made later (D110).
     */
    private fun planWeek(kept: List<CountedPlan>, workouts: List<Workout>, monday: Long): PlanWeekFigures? {
        val plan = CountedPlan.covering(kept, monday) ?: return null
        val p = plan.programme
        val index = ProgrammeCalendar.weekIndex(plan.start, monday)
        val week = PlanProgress.of(p.plan, plan.start, workouts, Programmes.countedUntil(p, monday + 6), plan.counting).weeks.getOrNull(index)
            ?: return null
        return PlanWeekFigures(p.plan.title, week.planned, week.done, ProgrammeCalendar.ended(plan.start, p.ask.weeks, monday + 6))
    }
}
