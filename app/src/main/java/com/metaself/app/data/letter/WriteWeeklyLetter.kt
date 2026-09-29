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
import com.metaself.app.data.trainer.TrainerReviews
import com.metaself.app.data.weight.WeightRepository
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.letter.LetterFigures
import com.metaself.app.domain.letter.LetterReply
import com.metaself.app.domain.letter.LetterRequest
import com.metaself.app.domain.letter.LetterWriter
import com.metaself.app.domain.letter.PlanWeekFigures
import com.metaself.app.domain.letter.WeekFigures
import com.metaself.app.domain.letter.WeeklyLetter
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.profile.ageYears
import com.metaself.app.domain.target.CurrentTarget
import com.metaself.app.domain.trainer.BodyFacts
import com.metaself.app.domain.trainer.GoalFacts
import com.metaself.app.domain.trainer.PlanCounting
import com.metaself.app.domain.trainer.PlanProgress
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeCalendar
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.Programmes
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.weight.WeightTrend
import kotlinx.coroutines.flow.first
import javax.inject.Inject

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
) {
    sealed interface Outcome {
        data object Quiet : Outcome
        data object AlreadyWritten : Outcome
        data class Written(val letter: WeeklyLetter) : Outcome
        /** A failure worth trying again later (network, provider, an unreadable answer). */
        data class Retry(val failure: EstimateResult) : Outcome
        /** A failure no retry mends: no key, a refusal, the day's ceiling. */
        data class GiveUp(val failure: EstimateResult) : Outcome
    }

    /** [copy] false for Write it now: the app is in front and its own copy runs anyway (design question 19). */
    suspend operator fun invoke(weekMonday: Long, copy: Boolean = true): Outcome {
        if (letters.of(weekMonday) != null) return Outcome.AlreadyWritten
        val copied = copy && backgroundRead.granted() && backgroundCopy.copyInBackground()
        val bandDataUntil = if (copied) null else status.current().lastCopiedMillis
        // The copy can take a while; a letter written meanwhile (Write it now) is not asked for twice.
        if (letters.of(weekMonday) != null) return Outcome.AlreadyWritten

        val sunday = weekMonday + 6
        val first = weekMonday - 7L * LetterFigures.WEEKS_BEFORE
        val foodByDay = food.byDay(first, sunday)
        val trend = WeightTrend.of(weights.readings.first())
        val workouts = record.observeWorkouts(first, sunday).first()
        val days = record.observeDays(first, sunday).first()
        val allReviews = reviews.observeReviews().first()
        val all = programmes.all()
        val kept = all.filter { it.status != ProgrammeStatus.OFFERED && it.startEpochDay != null }
            .map { it to counting(it, all) }

        fun week(monday: Long) = WeekFigures.of(monday, foodByDay, trend, workouts, allReviews, days, planWeek(kept, workouts, monday))
        val figuresWeek = week(weekMonday)
        if (figuresWeek.quiet) return Outcome.Quiet

        val profile = profiles.profile.first()
        val target = profile?.let {
            CurrentTarget.of(it, profiles.revision.first(), year(), profiles.burnAdjustmentKcal.first()).kcal
        }
        val figures = LetterFigures(figuresWeek, (1..LetterFigures.WEEKS_BEFORE).map { week(weekMonday - 7L * it) }, target)
        val running = runningIn(kept, weekMonday)?.first
        val request = LetterRequest(
            figures = figures,
            sessions = workouts
                .filter { !it.hidden && it.counted && it.epochDay in weekMonday..sunday }
                .sortedBy { it.startedAtMillis }
                .map { TrainerRequest.session(it, review = null, plan = null) },
            aboutMe = aboutMe.note.first().trim().ifEmpty { null },
            goal = profile?.let { GoalFacts(it.goal.direction, it.goal.kgPerWeek) },
            body = profile?.let { BodyFacts(it.ageYears(year()), it.sex, it.heightCm) },
            planTitle = running?.plan?.title,
            planWeek = running?.let { p -> p.plan.weeks.getOrNull(ProgrammeCalendar.weekIndex(p.startEpochDay!!, weekMonday)) },
            lastNextWeek = letters.of(weekMonday - 7)?.texts?.nextWeek,
        )
        return when (val reply = writer.write(request)) {
            is LetterReply.Failed -> when (reply.failure) {
                is EstimateResult.NoKey, is EstimateResult.Refused, is EstimateResult.CeilingReached -> Outcome.GiveUp(reply.failure)
                else -> Outcome.Retry(reply.failure)
            }
            is LetterReply.Written -> {
                val letter = WeeklyLetter(
                    weekMonday = weekMonday, createdAtMillis = now(), figures = figures, texts = reply.texts,
                    model = reply.model, bandDataUntil = bandDataUntil,
                )
                letters.add(letter)
                Outcome.Written(letter)
            }
        }
    }

    /** D105: a version counts from its chain's first keep, with the answers stored under that first version. */
    private suspend fun counting(programme: Programme, all: List<Programme>): PlanCounting {
        val root = Programmes.rootOf(programme, all)
        return PlanCounting(root.id, root.createdAtMillis, programmes.confirmations(root.id))
    }

    /** Design question 13: the kept plan counted during [monday]'s week, the latest-started. */
    private fun runningIn(kept: List<Pair<Programme, PlanCounting>>, monday: Long): Pair<Programme, PlanCounting>? = kept
        .filter { (p, _) ->
            val start = p.startEpochDay!!
            start <= monday + 6 && Programmes.countedUntil(p, monday + 6) >= monday
        }
        .maxByOrNull { it.first.startEpochDay!! }

    /** Its week's planned and done; done counts only ticked sessions — full, or confirmed short (D105). */
    private fun planWeek(kept: List<Pair<Programme, PlanCounting>>, workouts: List<Workout>, monday: Long): PlanWeekFigures? {
        val (p, counting) = runningIn(kept, monday) ?: return null
        val start = p.startEpochDay!!
        val index = ProgrammeCalendar.weekIndex(start, monday)
        val week = PlanProgress.of(p.plan, start, workouts, Programmes.countedUntil(p, monday + 6), counting).weeks.getOrNull(index)
            ?: return null
        return PlanWeekFigures(p.plan.title, week.planned, week.done, ProgrammeCalendar.ended(start, p.ask.weeks, monday + 6))
    }
}
