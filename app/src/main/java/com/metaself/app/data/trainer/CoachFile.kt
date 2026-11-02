package com.metaself.app.data.trainer

import com.metaself.app.data.ai.CoachDrawers
import com.metaself.app.data.day.MealRepository
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.letter.FoodTotals
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.weight.WeightRepository
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.profile.ageYears
import com.metaself.app.domain.target.CurrentTarget
import com.metaself.app.domain.target.DailyTarget
import com.metaself.app.domain.trainer.BodyFacts
import com.metaself.app.domain.trainer.Drawer
import com.metaself.app.domain.trainer.DrawerAsk
import com.metaself.app.domain.trainer.FrontPage
import com.metaself.app.domain.trainer.GoalFacts
import com.metaself.app.domain.trainer.MeasuredHabits
import com.metaself.app.domain.trainer.OpenedDrawer
import com.metaself.app.domain.trainer.RecordBegins
import com.metaself.app.domain.trainer.Rhythm
import com.metaself.app.domain.trainer.SessionFacts
import com.metaself.app.domain.trainer.SessionReviews
import com.metaself.app.domain.trainer.TalkPlan
import com.metaself.app.domain.trainer.ThreadItem
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.WeekNotes
import com.metaself.app.domain.weight.MeasuredRate
import com.metaself.app.domain.weight.WeightTrend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * The owner's file as the coach reads it (D161): the front page and the drawers, fresh from the stored record, **only
 * read** and only while a reply is being asked for (D84). A drawer that fails to read says so to the model; the reply
 * goes on without it.
 */
class CoachFile @Inject constructor(
    private val record: MovementRecord,
    private val store: TrainerStore,
    private val weights: WeightRepository,
    private val profiles: ProfileRepository,
    private val ask: AskTheTrainer,
    private val aboutMe: AboutMeStore,
    private val sheets: SheetStore,
    private val weekNotes: WeekNoteStore,
    private val meals: MealRepository,
    private val food: FoodTotals,
    private val today: Today,
    private val now: Now,
    private val year: CurrentYear,
) {

    /** D161: goal, plan and this week, the coach's note, about me, the whole sheet, body, floor and target, how far back. */
    suspend fun frontPage(thread: List<ThreadItem>): FrontPage {
        val day = today().toEpochDay()
        val running = ask.running()
        val plan = running?.programme?.startEpochDay?.let { start -> TalkPlan(running.programme.ask, start, running.programme.plan, running.weekIndex) }
        val profile = profiles.profile.first()
        val target = target()
        val monday = MovementWeek.mondayOf(day)
        val week = record.observeWorkouts(monday, day).first().count { !it.hidden && it.counted }
        return FrontPage(
            today = day,
            goal = profile?.let { GoalFacts(it.goal.direction, it.goal.kgPerWeek) },
            plan = plan,
            thisWeek = Rhythm(week, (monday + 6 - day).toInt()),
            weekNote = runCatching { WeekNotes.shown(weekNotes.note.first(), monday)?.text }.getOrNull(),
            aboutMe = aboutMe.note.first()?.trim()?.takeIf { it.isNotEmpty() },
            sheet = sheets.newest()?.current?.takeIf { it.isNotEmpty() },
            body = profile?.let { BodyFacts(it.ageYears(year()), it.sex, it.heightCm) },
            floorKcal = target?.floorKcal,
            targetKcal = target?.kcal,
            recordBegins = RecordBegins(
                movement = record.observeEarliestDay().first(),
                food = meals.observeLoggedDays().first().minOrNull(),
                weight = weights.readings.first().minOfOrNull { it.epochDay },
                talk = thread.firstOrNull()?.let { Instant.ofEpochMilli(it.atMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() },
            ),
        )
    }

    /**
     * What [asks] come to, one answer for each, in order. [chars] is what this reply has read so far: past
     * [CoachDrawers.MAX_OPENED_CHARS], and past [CoachDrawers.MAX_OPENS_A_LOOK] asks, nothing more is read, and it says so.
     */
    suspend fun open(asks: List<DrawerAsk>, thread: List<ThreadItem>, exceptMessage: Long?, chars: Int): List<OpenedDrawer> {
        var total = chars
        return asks.mapIndexed { index, ask ->
            val answer = when {
                index >= CoachDrawers.MAX_OPENS_A_LOOK -> CoachDrawers.refused(ask, "only ${CoachDrawers.MAX_OPENS_A_LOOK} drawers can be opened at one look")
                total >= CoachDrawers.MAX_OPENED_CHARS -> CoachDrawers.refused(ask, "you have read as much as one reply may: answer from what you have")
                else -> read(ask, thread, exceptMessage)
            }
            total += answer.answer.length
            answer
        }
    }

    private suspend fun read(ask: DrawerAsk, thread: List<ThreadItem>, exceptMessage: Long?): OpenedDrawer {
        val drawer = Drawer.named(ask.drawer) ?: return CoachDrawers.unknown(ask)
        return try {
            when (drawer) {
                Drawer.HABITS -> habits(ask)
                Drawer.PLAN -> plan(ask)
                Drawer.CONVERSATIONS -> CoachDrawers.conversations(ask, ask.words, thread, exceptMessage, ZoneId.systemDefault())
                else -> when (val range = CoachDrawers.range(ask, today().toEpochDay())) {
                    is CoachDrawers.Range.Bad -> CoachDrawers.refused(ask, range.why)
                    is CoachDrawers.Range.Good -> byDate(drawer, ask, range)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unreadable: Exception) {
            CoachDrawers.refused(ask, "this drawer could not be read just now")
        }
    }

    private suspend fun byDate(drawer: Drawer, ask: DrawerAsk, range: CoachDrawers.Range.Good): OpenedDrawer {
        val day = today().toEpochDay()
        return when (drawer) {
            Drawer.SESSIONS -> CoachDrawers.sessions(ask, range, record.observeEarliestDay().first(), sessions(range.from, range.to))
            Drawer.DAYS -> CoachDrawers.days(ask, range, record.observeEarliestDay().first(), record.observeDays(range.from, range.to).first())
            Drawer.FOOD_DAYS -> CoachDrawers.foodDays(ask, range, firstLogged(), day, food.byDay(range.from, range.to), target()?.kcal)
            Drawer.MEALS -> CoachDrawers.meals(ask, range, firstLogged(), meals.mealsBetween(range.from, range.to), ZoneId.systemDefault())
            Drawer.WEIGHT -> {
                val readings = weights.readings.first()
                val trend = WeightTrend.of(readings)
                CoachDrawers.weight(ask, range, readings.minOfOrNull { it.epochDay }, trend, MeasuredRate.of(trend, day))
            }
            Drawer.HABITS, Drawer.PLAN, Drawer.CONVERSATIONS -> error("not read by date")
        }
    }

    /** The visible, counted sessions of the span, each with its review and the plan it names, as a request sends them. */
    private suspend fun sessions(from: Long, to: Long): List<SessionFacts> {
        val workouts = record.observeWorkouts(from, to).first()
        val reviews = store.observeReviews().first()
        val plans = store.plans(reviews.mapNotNull { it.planId })
        val byWorkout = SessionReviews.bySession(workouts, reviews)
        return workouts.filter { !it.hidden && it.counted }.sortedBy { it.startedAtMillis }.map { workout ->
            val review = byWorkout[workout.id]
            TrainerRequest.session(workout, review, review?.planId?.let(plans::get)?.plan)
        }
    }

    private suspend fun plan(ask: DrawerAsk): OpenedDrawer {
        val running = this.ask.running()
        val start = running?.programme?.startEpochDay
        val plan = if (running != null && start != null) TalkPlan(running.programme.ask, start, running.programme.plan, running.weekIndex) else null
        val weeks = running?.progress?.weeks.orEmpty().take((running?.weekIndex ?: -1) + 1).map { it.outcome() }
        return CoachDrawers.plan(ask, plan, weeks, today().toEpochDay())
    }

    /** D162: fresh on demand, never stored. */
    private suspend fun habits(ask: DrawerAsk): OpenedDrawer {
        val day = today().toEpochDay()
        val first = MeasuredHabits.firstDayRead(day)
        val workouts: List<Workout> = record.observeWorkouts(first, day).first()
        val report = MeasuredHabits.of(
            today = day,
            meals = meals.mealsBetween(first, day),
            zone = ZoneId.systemDefault(),
            target = target(),
            trendKg = WeightTrend.of(weights.readings.first()).lastOrNull()?.trendKg,
            workouts = workouts,
            firstLoggedDay = firstLogged(),
        )
        return CoachDrawers.habits(ask, report)
    }

    private suspend fun firstLogged(): Long? = meals.observeLoggedDays().first().minOrNull()

    /** Today's target and its floor, by the app's own calculation (D9), null with no profile. */
    private suspend fun target(): DailyTarget? = profiles.profile.first()?.let {
        CurrentTarget.of(it, profiles.revision.first(), year(), profiles.burnAdjustmentKcal.first())
    }
}
