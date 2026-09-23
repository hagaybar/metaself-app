package com.metaself.app.ui.screen.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.day.DeletedEntry
import com.metaself.app.data.day.MealRepository
import com.metaself.app.data.food.FoodRepository
import com.metaself.app.data.food.DetachedRows
import com.metaself.app.data.food.LoggedFoods
import com.metaself.app.data.food.MealResult
import com.metaself.app.data.food.SavedMealRepository
import com.metaself.app.domain.food.MealFromDay
import com.metaself.app.ui.screen.repeat.LoggedMeal
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.weight.WeightRepository
import com.metaself.app.data.time.CurrentHour
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.correctionOf
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.food.FoodKeys
import com.metaself.app.domain.day.Remaining
import com.metaself.app.domain.profile.Profile
import com.metaself.app.domain.target.CurrentTarget
import com.metaself.app.domain.target.TargetRevision
import com.metaself.app.data.backup.DailyBackup
import com.metaself.app.data.movement.StepAccess
import com.metaself.app.data.movement.StepSource
import com.metaself.app.domain.goal.GoalArrival
import com.metaself.app.domain.goal.GoalProgress
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.milestone.Milestone
import com.metaself.app.domain.product.Product
import com.metaself.app.domain.movement.ActivityEnergy
import com.metaself.app.domain.movement.DayMovement
import com.metaself.app.domain.movement.MovementCap
import com.metaself.app.domain.movement.MovementCredit
import com.metaself.app.domain.movement.MovementToday
import com.metaself.app.domain.movement.NormalDay
import com.metaself.app.domain.milestone.Milestones
import com.metaself.app.domain.encourage.Encouragements
import com.metaself.app.ui.encourage.EncouragementWording
import com.metaself.app.domain.streak.Streaks
import com.metaself.app.domain.window.DayVerdict
import com.metaself.app.domain.window.EatingStretch
import com.metaself.app.domain.window.MealRead
import com.metaself.app.domain.window.WindowRule
import com.metaself.app.domain.window.WindowRules
import com.metaself.app.domain.target.BurnAdjustment
import com.metaself.app.domain.target.DailyTargetCalculator
import com.metaself.app.domain.target.MeasuredBurnCalculator
import com.metaself.app.domain.target.TargetRevisionRule
import com.metaself.app.domain.weight.WeightTrend
import com.metaself.app.ui.food.MealWording
import com.metaself.app.ui.food.RetaughtBecause
import com.metaself.app.ui.food.RetaughtWording
import com.metaself.app.ui.goal.GoalWording
import com.metaself.app.ui.goal.MilestoneWording
import com.metaself.app.ui.target.RevisionWording
import com.metaself.app.ui.day.DayTotalsWording
import com.metaself.app.ui.window.WindowWording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * The day, against the target.
 *
 * The date is read once per emission rather than held, so an app left open across midnight shows
 * the new day when something changes rather than yesterday's list for ever.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DayViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val meals: MealRepository,
    private val weights: WeightRepository,
    private val savedMeals: SavedMealRepository,
    private val foods: FoodRepository,
    private val today: Today,
    /**
     * The moment, for the measured window alone.
     *
     * A stretch is open until the fast completes, so whether last night has a verdict yet depends
     * on what time it is now — a question a day number cannot answer. Injected rather than read
     * here for the reason [Today] and [CurrentHour] are: a clock read inside a calculation is what
     * makes the calculation untestable.
     */
    private val now: Now,
    private val currentYear: CurrentYear,
    private val automaticBackup: DailyBackup,
    private val steps: StepSource,
    private val currentHour: CurrentHour,
    private val loggedFoods: LoggedFoods,
) : ViewModel() {

    /**
     * What today's walking is worth, refreshed when the app opens.
     *
     * Not recomputed on every emission: reading Health Connect is a cross-process call, and the day
     * screen redraws whenever anything at all changes. Once per open is the same rhythm the weekly
     * revision and the daily backup use.
     */
    private val _movement = MutableStateFlow<Movement?>(null)
    private val _encouragement = MutableStateFlow<String?>(null)

    /**
     * How the window has been going, counted once per open rather than on every emission.
     *
     * Counting it means reading a fortnight of days out of the database; the day screen redraws
     * whenever anything at all changes, and doing it there would read those days on every keystroke
     * of a meal being typed.
     */
    private val _windowTally = MutableStateFlow(0 to 0)

    /**
     * The latest stretch of eating under the ratio, open or closed, with its real beginning — or null.
     *
     * What today's one sentence is about (D32): an open one gives "last meal by" or "next meal from",
     * a closed one "your 14 hours were up". A fact about the record rather than about the day on
     * screen, and the RECORD rather than a verdict, because whether it is still open depends on when
     * the question is asked, and the screen asks at [_moment].
     *
     * Held here, refreshed when the record changes, rather than worked out on every emission. It
     * can cost a walk back over many days — see [theLatestStretch] — and the day screen redraws
     * whenever anything at all changes, down to a row being ticked.
     */
    private val _latestStretch = MutableStateFlow<EatingStretch?>(null)

    /**
     * The moment today's sentence is said at, read again each time the screen comes to the front.
     *
     * The sentence changes with the clock and nothing else: "next meal from 12:00" becomes "your 14
     * hours were up at 12:00" at 12:00, and "Good morning." goes at noon. Nothing in the record
     * moves at those moments, so without this the view model — which outlives the screen being
     * backgrounded — would go on saying the morning's sentence into the afternoon.
     *
     * Read on each return to the screen rather than on a timer. A timer would redraw the day every
     * minute for a sentence that changes a few times a day, and a screen left open across 12:00
     * catches up the next time it is looked at.
     */
    private val _moment = MutableStateFlow(now())

    /**
     * Which calendar day is "today" as far as the day screen is concerned.
     *
     * It moves only when the screen comes to the front on a new date. The view model belongs to the
     * activity, which Android may keep alive overnight; the day pager fixed "today" once, when it was
     * built, so a morning after an evening's use opened on YESTERDAY under the heading "Today" — no
     * "Good morning", no next-meal time, and breakfast filed onto the day before with a time on
     * another date (found by review, 2026-09-18). The pager rebuilds around this when it changes.
     */
    private val _calendarToday = MutableStateFlow(today().toEpochDay())
    val calendarToday: StateFlow<Long> = _calendarToday.asStateFlow()

    /** Bumped on each return to the screen, so the stretch and the tally are read again with it. */
    private val _looked = MutableStateFlow(0)

    /** The screen came to the front: bring the day and the moment up to date, and recount. */
    fun lookedAt() {
        _moment.value = now()
        _calendarToday.value = today().toEpochDay()
        _looked.value++
    }

    /** What was read from Health Connect: every day's steps, and what a usual day looks like. */
    private data class Movement(
        val byDay: Map<Long, DayMovement>,
        val normalSteps: Int?,
        val normalEnergyKcal: Int?,
        val weightKg: Double,
        val capKcal: Int,
    )

    init {
        // One attempt per view model — that is, once per app open. There is no background work in
        // this app yet, and a target that revised while the phone was in a drawer would buy nothing
        // over one that revises when it is next looked at.
        //
        // An unopened month therefore produces ONE revision, not four: the missed ones would each
        // have been computed from a trend that no longer exists to be checked.
        viewModelScope.launch {
            val profile = profiles.profile.first() ?: return@launch
            val trend = WeightTrend.of(weights.readings.first())
            val todayEpochDay = today().toEpochDay()

            // Measured BEFORE the revision, so the revision it produces already carries the
            // correction and announces one number rather than two in quick succession (D25).
            val adjustment = measureWhatADayCosts(profile, trend, todayEpochDay)

            val revision = TargetRevisionRule.revise(
                profile = profile,
                trend = trend,
                last = profiles.revision.first(),
                today = todayEpochDay,
                currentYear = currentYear(),
                burnAdjustmentKcal = adjustment,
            ) ?: return@launch

            profiles.saveRevision(revision)
        }

        celebrateArrivingOnce()
        markMilestonesOnce()
        takeTheDailyCopy()
        readTodaysSteps()
        noticeSomethingGood()
        followTheOpenStretch()
        putBackDetachedRows()
    }

    /**
     * Repair the rows corrections detached from their foods before issue #22 was fixed.
     *
     * Every time the app opens, because it is cheap when there is nothing to do — one read of the rows
     * attached to nothing — and because a row can also come loose when its food is deleted and later
     * made again under the same name. Silent: nothing on screen changes but that those rows can now
     * be made into a meal. See [DetachedRows] for why it only looks foods up and never makes one.
     */
    private fun putBackDetachedRows() {
        viewModelScope.launch { runCatching { DetachedRows.reattach(meals, foods) } }
    }

    /**
     * How much of what the window governed he kept, from the record, the way the streak is (D13).
     *
     * Two kinds, two units, and they are not interchangeable. The fixed hours are a statement about
     * a day, so the fraction is days; a ratio is a statement about hours, so the fraction is eating
     * stretches (design §3.1). Counting one in the other's unit would put a number on screen that
     * answers a question he did not ask.
     */
    private suspend fun countTheWindow(rules: List<WindowRule>, nowMillis: Long) {
            val todayEpochDay = today().toEpochDay()
            val inForce = WindowRules.inForceOn(rules, todayEpochDay) ?: return
            val zone = ZoneId.systemDefault()

            _windowTally.value = when (inForce) {
                is WindowRule.Fixed -> {
                    val from = maxOf(inForce.fromEpochDay, todayEpochDay - TALLY_DAYS)
                    val byDay = (from..todayEpochDay).associateWith { meals.observeDay(it).first() }
                    WindowRules.daysKept(
                        rules = rules,
                        mealsByDay = byDay,
                        fromEpochDay = from,
                        toEpochDay = todayEpochDay,
                        zone = zone,
                        nowMillis = nowMillis,
                    )
                }

                // Since the day the ratio was set, and no fortnight (D32): "Kept 10 of 14 stretches
                // since 3 Sep" is what the marker has to say. The rule's own gate —
                // did this stretch begin on or after that day? — is what bounds it, so there is
                // nothing to clamp. One read per day since then, redone when the record changes;
                // a ratio a year old is 365 small reads, which a phone does without noticing.
                is WindowRule.Measured -> WindowRules.stretchesKept(
                    rule = inForce,
                    meals = mealsOver(inForce.fromEpochDay - LEAD_IN_DAYS, todayEpochDay),
                    // The days actually read, said out loud, so a stretch still running when the
                    // read stopped is left open rather than counted as a kept or broken one.
                    read = MealRead.OverDays(inForce.fromEpochDay - LEAD_IN_DAYS, todayEpochDay),
                    zone = zone,
                    nowMillis = nowMillis,
                )
            }
    }

    /**
     * Keep [_latestStretch] and the tally current: the latest stretch, whichever day it began on.
     *
     * Re-read whenever today's meals change rather than once per app open, because the answer moves
     * with the record: logging after a long enough fast starts a NEW stretch, and a held one would
     * then say the owner had been eating since a moment he had not. Room invalidates a query flow
     * per TABLE, so a change to any meal on any day brings this round again, which is what makes
     * correcting an old day put the sentence right too.
     */
    private fun followTheOpenStretch() {
        viewModelScope.launch {
            profiles.windowRules
                .combine(meals.observeDay(todayEpochDayNow())) { rules, _ -> rules }
                .combine(_looked) { rules, _ -> rules }
                .collectLatest { rules ->
                    // ONE moment for everything the screen says about the ratio: today's sentence,
                    // the tally beside it and each day's verdicts. Three clocks read at three times
                    // let the tally lag a stretch behind the sentence next to it, and settings. Read
                    // afresh whenever the record changes — something was just logged, at now — and
                    // whenever the screen comes to the front.
                    val moment = now()
                    _moment.value = moment
                    _latestStretch.value = theLatestStretch(rules, moment)
                    // The tally moves with the record too. It was read once, at start-up, so a
                    // stretch that closed while the app was open was not counted until it was
                    // reopened — and D32 puts the tally in words on the screen.
                    countTheWindow(rules, moment)
                }
        }
    }

    /**
     * The latest stretch under the ratio, read back far enough to know where it really began.
     *
     * Null unless a ratio is in force now, unless there is a stretch at all, and unless the walk got
     * to see the beginning — the third is the one that matters. A read that stops in the middle of a
     * stretch cannot tell where it began ([StretchVerdict.startKnown]), and a closing time or a span
     * taken from the edge of a read would be an artefact stated as a measurement, which D4 forbids.
     *
     * So the read widens a day at a time until the beginning is inside it. It usually stops at once:
     * the ordinary three days hold a real fast. It walks further exactly for a ratio whose fast is
     * never reached, which is ONE stretch that never closes (design §3.0a).
     *
     * A CLOSED stretch is returned too (D32): it is what "your 14 hours were up at 12:00" is said
     * about. Until D32 only an open one was, because the only sentence was about eating still going.
     *
     * It stops at the day the rule began, and says nothing about a stretch that began before then.
     * A rule never applies backwards (design §2.4). That bound is also what keeps the walk finite.
     */
    private suspend fun theLatestStretch(rules: List<WindowRule>, nowMillis: Long): EatingStretch? {
        val todayEpochDay = todayEpochDayNow()
        val rule = WindowRules.inForceOn(rules, todayEpochDay) as? WindowRule.Measured
            ?: return null
        val zone = ZoneId.systemDefault()
        val read = mutableMapOf<Long, List<Meal>>()
        var from = todayEpochDay - LEAD_IN_DAYS

        while (true) {
            // The LAST stretch is the only one today's sentence is about: every earlier one was
            // shut by the fast that let the next one begin.
            val last = WindowRules.stretches(
                rule = rule,
                meals = (from..todayEpochDay).flatMap { day ->
                    read.getOrPut(day) { meals.observeDay(day).first() }
                },
                // This read ends on today, so nothing it holds can have run past it: there is no
                // later day to have eaten on.
                read = MealRead.OverDays(from, todayEpochDay),
                zone = zone,
                nowMillis = nowMillis,
            ).verdicts.lastOrNull()

            if (last == null) return null
            if (last.startKnown) {
                return last.stretch.takeIf { it.startedOnEpochDay >= rule.fromEpochDay }
            }
            if (from <= rule.fromEpochDay - LEAD_IN_DAYS) return null
            from--
        }
    }

    /**
     * Every meal on a small range of days, oldest day first.
     *
     * A stretch does not stop at midnight, so the measured window is always asked about a range
     * rather than a day — and reading two days further back than the range being judged is what
     * tells the walk whether its first input OPENS a stretch or continues one (see
     * [WindowRules.stretches] for why two is enough). One `observeDay` per day, which is what the
     * tally has always done, rather than a new query over the meal table.
     */
    private suspend fun mealsOver(fromEpochDay: Long, toEpochDay: Long): List<Meal> =
        (fromEpochDay..toEpochDay).flatMap { meals.observeDay(it).first() }

    /**
     * The same range, out of days already read rather than read again.
     *
     * A day that was never read is simply absent, and that is correct rather than lenient: the only
     * time a day is missing is when the fixed hours govern, and the fixed hours are a statement
     * about one calendar day and want nothing else.
     */
    private fun mealsIn(
        byDay: Map<Long, List<Meal>>,
        fromEpochDay: Long,
        toEpochDay: Long,
    ): List<Meal> = (fromEpochDay..toEpochDay).flatMap { byDay[it].orEmpty() }

    /**
     * Say one encouraging thing, at most once a day.
     *
     * Everything it can notice is already in the record; nothing new is tracked. The budget rather
     * than the wording is what keeps this worth having — too much praise diminishes its value —
     * and there is no occasion for a failure: silence is the whole of what this app says about a
     * bad day (D14).
     */
    private fun noticeSomethingGood() {
        viewModelScope.launch {
            val todayEpochDay = today().toEpochDay()
            val lastSeen = profiles.lastSeenDay.first()
            // Written before anything is decided, so a crash cannot make him "away" for ever.
            profiles.saveLastSeenDay(todayEpochDay)

            if (profiles.lastEncouragedDay.first() == todayEpochDay) return@launch

            val rules = profiles.windowRules.first()
            val zone = ZoneId.systemDefault()
            val yesterday = todayEpochDay - 1

            // Only once today's window has actually begun: a verdict delivered at one minute past
            // midnight is delivered to nobody, and it belongs at the point the day's
            // eating starts. For the fixed kind that point is the start hour he chose. A ratio has
            // no start hour at all, so its equivalent is the day's FIRST INPUT having landed —
            // same intent, same rhythm, and no extra clock reading (D29).
            val begun = when (val inForce = WindowRules.inForceOn(rules, todayEpochDay)) {
                null -> false
                is WindowRule.Fixed -> currentHour() >= inForce.window.startHour
                is WindowRule.Measured -> meals.observeDay(todayEpochDay).first().isNotEmpty()
            }
            val windowKeptYesterday = begun &&
                verdictOn(rules, yesterday, zone)?.let { it.judged && it.kept } == true

            val streak = Streaks.of(meals.observeLoggedDays().first(), todayEpochDay)
            val walked = _movement.value

            val occasion = Encouragements.pick(
                somethingRarer = profiles.arrival.first()?.isTodayS(todayEpochDay) == true,
                alreadySaidToday = false,
                daysAway = lastSeen?.let { (todayEpochDay - it).toInt() } ?: 0,
                windowKeptYesterday = windowKeptYesterday,
                windowKeptLastSeven = keptTheWholeRun(rules, todayEpochDay, zone),
                trendAtNewLow = trendAtNewLow(),
                streakDays = streak.currentDays,
                stepsToday = walked?.byDay?.get(todayEpochDay)?.steps ?: 0,
                usualSteps = walked?.normalSteps,
            ) ?: return@launch

            profiles.saveEncouragedDay(todayEpochDay)
            _encouragement.value = EncouragementWording.of(occasion, streak.currentDays)
        }
    }

    /**
     * How one day stood, asked of the rule that governed that day.
     *
     * A ratio is shown the days either side as well: a stretch that began the evening before is
     * not this day's and the walk can only know that by seeing it, and a stretch that began on this
     * day may not have ended until the morning after (design §3). The fixed kind is shown the same
     * range and ignores all but its own day, which [WindowRules.judge] takes care of, so this one
     * reading serves both.
     */
    private suspend fun verdictOn(
        rules: List<WindowRule>,
        epochDay: Long,
        zone: ZoneId,
    ): DayVerdict? {
        val from = epochDay - LEAD_IN_DAYS
        val through = minOf(epochDay + RUN_ON_DAYS, today().toEpochDay())
        return WindowRules.judge(
            rules = rules,
            meals = mealsOver(from, through),
            read = MealRead.OverDays(from, through),
            epochDay = epochDay,
            zone = zone,
            nowMillis = now(),
        )
    }

    /**
     * A whole run of success, in the unit the window in force counts in — or nothing at all.
     *
     * The spirit is what it always was and the unit is what moved. For the fixed hours it is the
     * seven days ending YESTERDAY, every one judged and every one kept, and never a day before
     * those hours began — so a week the owner switched kinds in holds no run at all, and says
     * nothing. For a ratio it is the last
     * seven CLOSED, JUDGED stretches, every one kept (design §3.2): a stretch still fasting has no
     * verdict to contribute, and neither has one holding a single input, so neither is counted in
     * and neither silences the compliment by being there.
     *
     * **Deliberate, and stated here so it is not "fixed": this stays hard to earn.** Under the
     * fixed hours a day with a single input is not judged at all, so one quiet day takes `judged`
     * to six and the compliment is withheld by an un-perfect week rather than by a broken one. That
     * is the honest reading of not judging a one-input day: the app has no opinion about a day it
     * declined to score, and inventing one to keep a compliment alive would be exactly the
     * retroactive scoring this feature refuses. Under a ratio the quiet day simply contributes no
     * stretch and the run reaches further back for its seventh, which is the same rule reading
     * naturally in the new unit rather than a loosening of it.
     *
     * The measured half reads the same fortnight the tally does. Seven stretches are usually seven
     * days or fewer, and a run that has to reach further back than a fortnight to find its seventh
     * is not a run.
     */
    private suspend fun keptTheWholeRun(
        rules: List<WindowRule>,
        todayEpochDay: Long,
        zone: ZoneId,
    ): Boolean = when (val inForce = WindowRules.inForceOn(rules, todayEpochDay)) {
        null -> false

        is WindowRule.Fixed -> {
            // Never back past the day these hours began. Without the clamp a week the owner
            // switched kinds in reached into the days a RATIO governed, and [WindowRules.daysKept]
            // scores such a day from its own meals alone — a day-sliced reading, narrower than the
            // record, which calls an evening's eating kept and never sees it run on to noon the
            // next day. That flatters, and the honest consequence of not doing it is that in a
            // switch week the compliment is silent: seven days these hours governed do not exist.
            val from = maxOf(todayEpochDay - A_WHOLE_RUN, inForce.fromEpochDay)
            val byDay = (from..(todayEpochDay - 1)).associateWith { meals.observeDay(it).first() }
            val (kept, judged) = WindowRules.daysKept(
                rules = rules,
                mealsByDay = byDay,
                fromEpochDay = from,
                toEpochDay = todayEpochDay - 1,
                zone = zone,
                nowMillis = now(),
            )
            judged == A_WHOLE_RUN.toInt() && kept == A_WHOLE_RUN.toInt()
        }

        is WindowRule.Measured -> {
            val from = todayEpochDay - TALLY_DAYS - LEAD_IN_DAYS
            val judged = WindowRules.stretches(
                rule = inForce,
                meals = mealsOver(from, todayEpochDay),
                // The fortnight is the whole of what was read, and the run is drawn from stretches
                // the read saw begin and end. A stretch already running when the fortnight opened
                // has a span that is the fortnight's shape rather than his, and is left out.
                read = MealRead.OverDays(from, todayEpochDay),
                zone = zone,
                nowMillis = now(),
            ).verdicts.filter { it.judged }

            judged.size >= A_WHOLE_RUN &&
                judged.takeLast(A_WHOLE_RUN.toInt()).all { it.kept }
        }
    }

    /** The lightest the smoothed trend has been, which is a thing worth noticing once a day. */
    private suspend fun trendAtNewLow(): Boolean {
        val trend = WeightTrend.of(weights.readings.first())
        if (trend.size < 2) return false
        val latest = trend.last().trendKg
        return trend.dropLast(1).all { it.trendKg > latest }
    }

    /**
     * Steps above his own usual day, turned into calories (D12).
     *
     * Everything here fails silently to nothing: no Health Connect, no permission, not enough
     * history, or an ordinary day. All four mean "no credit", and none of them may interrupt him
     * logging his lunch (D8).
     */
    /**
     * True while a pull-down is being served, so the indicator can stay until there is an answer.
     *
     * A refresh that finishes before the finger lifts looks like nothing happened; one that spins
     * for ever looks broken. Both are avoided by tying it to the actual read.
     */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /**
     * Read the steps again, on request.
     *
     * Everything else on this screen is already live — meals, weights and the target all come from
     * flows that update themselves. Steps do not: they are a cross-process read done once per app
     * open, so a walk taken WHILE the app is open never appeared until it was closed and reopened.
     * That is the whole reason this exists.
     */
    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            runCatching { readMovement() }
            _refreshing.value = false
        }
    }

    private fun readTodaysSteps() {
        viewModelScope.launch { readMovement() }
    }

    private suspend fun readMovement() {
            val profile = profiles.profile.first() ?: return
            if (steps.access() != StepAccess.GRANTED) return

            val todayDate = today()
            val history = steps.history(
                from = todayDate.minusDays(NormalDay.WINDOW_DAYS.toLong()),
                to = todayDate,
            )

            // Kept whole rather than reduced to a credit: the count is shown every day, and only
            // some days earn anything (D12a).
            _movement.value = Movement(
                byDay = history.associateBy { it.epochDay },
                normalSteps = NormalDay.steps(history, todayDate.toEpochDay()),
                normalEnergyKcal = NormalDay.energyKcal(
                    history,
                    todayDate.toEpochDay(),
                    profile.weightKg,
                ),
                weightKg = profile.weightKg,
                capKcal = MovementCap.forGoal(profile.goal),
            )
    }

    /**
     * The copy the owner does not have to remember to make (D26).
     *
     * Here, with the other once-per-open work, because this app has no background machinery and
     * arranging some for a daily file copy would cost a foreground service and a wakelock to save
     * him nothing: a backup taken while the phone was in a drawer is worth no more than one taken
     * when it is next picked up.
     *
     * Nothing it does can reach the screen. A backup that interrupts the app it is protecting has
     * got its priorities backwards.
     */
    private fun takeTheDailyCopy() {
        viewModelScope.launch {
            runCatching { automaticBackup.runIfDue(today(), System.currentTimeMillis()) }
        }
    }

    /**
     * The way to the goal, marked once each.
     *
     * Which milestones the record has reached is recomputed every time; which have been ANNOUNCED is
     * stored, and only the difference is written down and said. That separation is what the spec
     * requires — a milestone is announced once and never again — and it is why a weight that crosses
     * a threshold, drifts back and crosses again says nothing the second time.
     *
     * Milestones already true the first time this runs are still announced. Somebody who has already
     * lost four kilograms when the feature arrives sees his first kilogram once; quietly marking old
     * ones as spent would mean the feature's first act is to withhold something, and there is no
     * record that could tell the two cases apart anyway.
     */
    private fun markMilestonesOnce() {
        viewModelScope.launch {
            val profile = profiles.profile.first() ?: return@launch
            val trend = WeightTrend.of(weights.readings.first())
            val reached = Milestones.reached(profile.goal, trend)
            val alreadySaid = profiles.milestones.first()

            val fresh = reached.filterNot { alreadySaid.containsKey(it) }
            if (fresh.isEmpty()) return@launch

            val today = today().toEpochDay()
            profiles.recordMilestones(alreadySaid + fresh.associateWith { today })
        }
    }

    /**
     * What a day has actually cost, against what the formula predicted (D25).
     *
     * The formula's resting burn is sound; the activity multiplier it is scaled by is five buckets
     * from a dropdown, and adjacent ones are hundreds of calories apart. This measures the
     * difference and moves a standing correction a little way towards it.
     *
     * It returns the CURRENT adjustment unchanged when there is not enough to go on, so a month
     * with too few logged days simply leaves the target where it was.
     */
    private suspend fun measureWhatADayCosts(
        profile: com.metaself.app.domain.profile.Profile,
        trend: List<com.metaself.app.domain.weight.TrendPoint>,
        todayEpochDay: Long,
    ): Int {
        val standing = profiles.burnAdjustmentKcal.first()

        // What the formula alone says a day costs: the target with the goal's deficit added back.
        val formulaKcal = DailyTargetCalculator.of(
            profile.copy(weightKg = trend.lastOrNull()?.trendKg ?: profile.weightKg),
            currentYear(),
        ).maintenanceKcal

        val measured = MeasuredBurnCalculator.of(
            loggedKcalByDay = meals.kcalByDaySince(
                todayEpochDay - MeasuredBurnCalculator.WINDOW_DAYS + 1,
            ),
            trend = trend,
            formulaKcal = formulaKcal,
            todayEpochDay = todayEpochDay,
        )

        val next = BurnAdjustment.next(standing, measured)
        if (next != standing) profiles.saveBurnAdjustment(next)
        return next
    }

    /**
     * Reaching the goal weight: recorded, announced for the day, and the goal switched to holding.
     *
     * The switch is the owner's decision (D21). The alternative was an app that goes on prescribing
     * a deficit to somebody who has finished losing weight until he thinks to turn it off.
     *
     * Arrival is judged from the smoothed trend, never a single reading, and is written down as a
     * fact rather than recomputed — so a weight that drifts up afterwards cannot arrive a second
     * time. The goal no longer has a target either, which is the second of two independent reasons
     * this can only happen once.
     */
    private fun celebrateArrivingOnce() {
        viewModelScope.launch {
            val profile = profiles.profile.first() ?: return@launch
            val targetKg = profile.goal.targetKg ?: return@launch
            val trend = WeightTrend.of(weights.readings.first())
            val progress = GoalProgress.of(profile.goal, trend) ?: return@launch
            if (!progress.arrived) return@launch

            profiles.saveArrival(
                GoalArrival(targetKg = targetKg, epochDay = today().toEpochDay()),
            )
            profiles.save(profile.copy(goal = Goal.hold()))
        }
    }

    /**
     * The day on screen. Starts on today and is not remembered across restarts: decision D14 says
     * the app opens on today after a gap and shows no gap-shaped hole, and reopening on whatever day
     * was last looked at is exactly that hole.
     */
    private val selectedDay = MutableStateFlow(today().toEpochDay())


    /**
     * What was logged a moment ago, or null.
     *
     * Saving a meal used to return the owner to the day with nothing to say it had worked — the
     * same gap the weight screen had, in a different place. Cleared when he
     * dismisses it or moves to another day.
     */
    private val _justLogged = MutableStateFlow<String?>(null)

    /** Which meals he built are open on the day in front of him. */
    private val _openMeals = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * Which rows on the day are ticked, on the way to becoming a meal (design §3.5).
     *
     * Item ids rather than meal ids, because rows logged at different moments can be pulled
     * together. They belong to the day they were chosen on and are dropped when the day changes.
     */
    private val _chosen = MutableStateFlow<Set<Long>>(emptySet())

    /** Why the last attempt to make a meal out of the day was refused, naming the row. */
    private val _refusal = MutableStateFlow<String?>(null)

    /**
     * What the last logging action changed about a food's own stored figures (D45, issue #13).
     *
     * In memory rather than on disk, unlike the target notice's "seen" flag: a weekly revision
     * outlives a process, and this is about something he did seconds ago. Replaced whole by each
     * logging action — including replaced with nothing when the next action has nothing to say.
     *
     * **Written from inside the coroutine, so the last action to FINISH wins, not the last one
     * tapped.** Two logs started inside one database round trip could therefore leave the earlier
     * one's sentence standing; both sentences are true and both writes are committed, so the cost
     * is a sentence about the wrong one of two real changes, and ordering the assignments would
     * mean holding the notice back until the write it describes has actually happened. Read no
     * ordering into the assignment that the taps do not guarantee.
     */
    private val _foodRetaught = MutableStateFlow<String?>(null)

    val state: StateFlow<DayUiState> = combine(
        profiles.profile,
        profiles.revision,
        profiles.revisionSeen,
        selectedDay,
        _justLogged,
    ) { profile, revision, seen, day, justLogged ->
        DayInputs(profile, revision, seen, day, justLogged)
    }
        // Nested rather than a sixth argument: `combine` has typed overloads for five flows and
        // falls back to an untyped vararg beyond that, which compiles to Array<Any?> and loses
        // every type in the block.
        .combine(profiles.arrival) { inputs, arrival -> inputs.copy(arrival = arrival) }
        // The streak is counted from the record on every emission rather than stored, so filling in
        // a day the owner missed repairs the run with nothing else to do (D13).
        .combine(meals.observeLoggedDays()) { inputs, days -> inputs.copy(loggedDays = days) }
        .combine(profiles.milestones) { inputs, reached -> inputs.copy(milestones = reached) }
        .combine(profiles.burnAdjustmentKcal) { inputs, adjustment ->
            inputs.copy(burnAdjustmentKcal = adjustment)
        }
        .combine(_movement) { inputs, movement -> inputs.copy(movement = movement) }
        .combine(profiles.windowRules) { inputs, rules -> inputs.copy(windows = rules) }
        .combine(_encouragement) { inputs, said -> inputs.copy(encouragement = said) }
        .combine(_windowTally) { inputs, tally -> inputs.copy(windowTally = tally) }
        .combine(_latestStretch) { inputs, latest -> inputs.copy(latestStretch = latest) }
        .combine(_moment) { inputs, moment -> inputs.copy(moment = moment) }
        .combine(_openMeals) { inputs, open -> inputs.copy(openMeals = open) }
        .combine(_chosen) { inputs, chosen -> inputs.copy(chosen = chosen) }
        .combine(_refusal) { inputs, refusal -> inputs.copy(refusal = refusal) }
        .combine(_foodRetaught) { inputs, retaught -> inputs.copy(foodRetaught = retaught) }
        .flatMapLatest { inputs ->
            if (inputs.profile == null) {
                flowOf(DayUiState.NeedsSetup)
            } else {
                // Computed once per profile and revision, not once per change to the day's list.
                val target = CurrentTarget.of(
                    inputs.profile,
                    inputs.revision,
                    currentYear(),
                    inputs.burnAdjustmentKcal,
                )
                val notice = inputs.revision
                    ?.takeIf { !inputs.seen }
                    ?.let { RevisionWording.notice(it) }

                val isToday = inputs.day == today().toEpochDay()

                // The count is shown for whichever day is on screen. The CREDIT is today's only: a
                // past day's allowance must not change because of what was walked since, and D14
                // says the past neither nags nor shifts underfoot.
                // A day Health Connect has nothing for still shows, saying so, PROVIDED it is
                // today: before the owner has walked anywhere there is no record, and vanishing is
                // indistinguishable from the feature having broken. A past day with no record is
                // left alone, because "no steps recorded" against every old day is only noise.
                val nothingYet = inputs.movement
                    ?.takeIf { isToday && !it.byDay.containsKey(inputs.day) }
                    ?.let {
                        MovementToday(
                            steps = 0,
                            recorded = false,
                            normalSteps = it.normalSteps,
                        )
                    }

                val walked = inputs.movement?.byDay?.get(inputs.day)?.let { day ->
                    val normalEnergy = inputs.movement.normalEnergyKcal
                    MovementToday(
                        steps = day.steps,
                        normalSteps = inputs.movement.normalSteps,
                        sessions = day.sessions,
                        credit = if (isToday && normalEnergy != null) {
                            MovementCredit.of(
                                today = ActivityEnergy.of(day, inputs.movement.weightKg),
                                normalEnergyKcal = normalEnergy,
                                capKcal = inputs.movement.capKcal,
                            ).takeIf { it.kcal > 0 }
                        } else {
                            null
                        },
                    )
                }

                val shown = walked ?: nothingYet
                val allowance = walked?.credit
                    ?.let { target.copy(kcal = target.kcal + it.kcal) }
                    ?: target

                // Which days have to be read to draw this one.
                //
                // The shown day always, and that is the whole of it while the fixed hours govern:
                // "eat between 06:00 and 20:00" is a statement about one calendar day. A ratio is a
                // statement about hours and a stretch does not stop at midnight, so it needs more:
                //
                // - the days BEFORE the one on screen, when a ratio governed it, because whether
                //   its first input OPENS a stretch or continues last night's can only be known by
                //   looking at last night;
                // - the days AFTER it, for the mirror reason: a stretch belongs to the day it began
                //   on and is shown its whole length there, so last night's dinner and this
                //   morning's coffee have to be read together (design §3). Never past today, where
                //   there is nothing to find.
                //
                // The days ending TODAY used to be read as well, whatever day was on screen, to
                // answer whether a stretch is open right now. Nothing draws that answer any more
                // (see `windowOpenNow` below), so the read is gone with it.
                //
                // One flow while the fixed hours govern, six at the very worst — a ratio, with the
                // stretch's run-on days. Small and bounded, and `observeDay` per day is the reading
                // the tally already does rather than a new query over the meal table.
                val shownRule = WindowRules.inForceOn(inputs.windows, inputs.day)
                val ruleNow = WindowRules.inForceOn(inputs.windows, todayEpochDayNow())
                // The tally belongs to the rule in force now, and counts only what that rule governed,
                // so it is said only on days that rule governed — on or after the day it began. A day
                // before it was under another rule, or none, and a tally printed on a day before its
                // rule began is a count of something that had not started.
                val governedByTheRuleNow = ruleNow != null && inputs.day >= ruleNow.fromEpochDay
                val lastDayOfTheStretch = minOf(inputs.day + RUN_ON_DAYS, todayEpochDayNow())
                val days = buildSet {
                    add(inputs.day)
                    if (shownRule is WindowRule.Measured) {
                        (inputs.day - LEAD_IN_DAYS..lastDayOfTheStretch).forEach { add(it) }
                    }
                }.toList()

                combine(days.map { meals.observeDay(it) }) { read ->
                    val byDay = days.zip(read.toList()).toMap()
                    val dayMeals = byDay.getValue(inputs.day)
                    val zone = ZoneId.systemDefault()

                    DayUiState.Ready(
                        epochDay = inputs.day,
                        isToday = isToday,
                        target = allowance,
                        meals = dayMeals,
                        remaining = Remaining.of(allowance, DayTotals.of(dayMeals)),
                        movement = shown,
                        encouragement = inputs.encouragement.takeIf { isToday },
                        windowKept = if (governedByTheRuleNow) inputs.windowTally.first else 0,
                        windowJudged = if (governedByTheRuleNow) inputs.windowTally.second else 0,
                        // Whether the hours he set are open NOW — a fact about this moment and not
                        // about the day on screen, which is why it is asked of today's rule rather
                        // than of the shown day's.
                        //
                        // The FIXED kind alone answers it, and a ratio in force now leaves it null
                        // (design §4, corrected 2026-09-17). A stretch does have an open-or-shut
                        // state and this briefly reported it, which put a ring on a past fixed day
                        // whose fill came from whether eating is going on right now — a mark about
                        // a different thing from the one that day was judged by, sitting beside
                        // that day's own tally. Nothing draws a ratio's state: the measured kind
                        // has its own mark and does not use the ring at all.
                        windowOpenNow = (ruleNow as? WindowRule.Fixed)
                            ?.window
                            ?.contains(currentHour()),
                        // Today's one sentence under a ratio: a time he can act on (D32). Asked of
                        // TODAY's rule and the latest stretch rather than of the day on screen —
                        // that is what lets it reach a morning whose today began no stretch, and the
                        // owner whose fast is never reached.
                        ratioNow = (ruleNow as? WindowRule.Measured)?.let { ratio ->
                            WindowWording.ratioNow(
                                latest = inputs.latestStretch,
                                window = ratio.window,
                                nowMillis = inputs.moment,
                                zone = zone,
                                isToday = isToday,
                            )
                        },
                        // The tally in words, since the ratio was set (D32). On every day, because
                        // it is a fact about the record rather than about the day on screen.
                        ratioTally = (ruleNow as? WindowRule.Measured)
                            ?.takeIf { governedByTheRuleNow }
                            ?.let { ratio ->
                                WindowWording.keptStretches(
                                    kept = inputs.windowTally.first,
                                    judged = inputs.windowTally.second,
                                    since = LocalDate.ofEpochDay(ratio.fromEpochDay),
                                )
                            },
                        // The days either side are handed over too, and ignored unless a ratio
                        // governs this day: a stretch belongs to the day it BEGAN on, and the walk
                        // can tell neither which day that is nor how far the stretch ran without
                        // seeing the evening before and the morning after.
                        verdict = WindowRules.judge(
                            rules = inputs.windows,
                            meals = mealsIn(
                                byDay,
                                inputs.day - LEAD_IN_DAYS,
                                lastDayOfTheStretch,
                            ),
                            // Where the read stopped, handed over with the meals. A stretch still
                            // being added to on the last day read cannot be known to have ended,
                            // and comes back open rather than as a span this range invented.
                            read = MealRead.OverDays(
                                inputs.day - LEAD_IN_DAYS,
                                lastDayOfTheStretch,
                            ),
                            epochDay = inputs.day,
                            zone = zone,
                            nowMillis = inputs.moment,
                        ),
                        targetChangeNotice = notice,
                        foodRetaughtNotice = inputs.foodRetaught,
                        openMeals = inputs.openMeals,
                        chosen = inputs.chosen,
                        refusal = inputs.refusal,
                        justLogged = inputs.justLogged,
                        goalReached = inputs.arrival
                            ?.takeIf { it.isTodayS(today().toEpochDay()) }
                            ?.let { GoalWording.celebration(it.targetKg, target.kcal) },
                        streak = Streaks.of(inputs.loggedDays, today().toEpochDay()),
                        // Shown on the day it was reached and on no other (D14).
                        milestoneReached = MilestoneWording.today(
                            inputs.milestones
                                .filterValues { it == today().toEpochDay() }
                                .keys,
                        ),
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = DayUiState.Loading,
        )

    /** The owner has seen what was logged; it does not come back. */
    fun dismissJustLogged() {
        _justLogged.value = null
    }

    /** The owner has read the notice; it does not come back. */
    fun dismissTargetChange() {
        viewModelScope.launch { profiles.markRevisionSeen() }
    }

    /**
     * The owner has read the line saying a food's figures changed; it does not come back.
     *
     * Its own dismissal rather than the target notice's: the two are about different things — the
     * weekly revision moved his target, and what he just logged moved a food — so neither may clear
     * the other (D45, issue #13).
     */
    fun dismissFoodRetaught() {
        _foodRetaught.value = null
    }

    /** Everything the day's state is built from, so `combine` has one thing to hand on. */
    private data class DayInputs(
        val profile: Profile?,
        val revision: TargetRevision?,
        val seen: Boolean,
        val day: Long,
        val justLogged: String?,
        val arrival: GoalArrival? = null,
        val loggedDays: Set<Long> = emptySet(),
        val milestones: Map<Milestone, Long> = emptyMap(),
        val burnAdjustmentKcal: Int = 0,
        val movement: Movement? = null,
        val windows: List<WindowRule> = emptyList(),
        val encouragement: String? = null,
        val windowTally: Pair<Int, Int> = 0 to 0,
        val latestStretch: EatingStretch? = null,
        val moment: Long = 0L,
        val openMeals: Set<Long> = emptySet(),
        val chosen: Set<Long> = emptySet(),
        val refusal: String? = null,
        val foodRetaught: String? = null,
    )

    /** Dismiss today's line. It does not come back: it has had its one appearance. */
    fun dismissEncouragement() {
        _encouragement.value = null
    }

    private fun todayEpochDayNow(): Long = today().toEpochDay()

    fun showDay(epochDay: Long) {
        selectedDay.value = epochDay
        _justLogged.value = null
        // Choosing belongs to the day it started on: the ids are rows on one date, and carrying them
        // across a swipe would offer to make a meal out of rows that are no longer on screen.
        _chosen.value = emptySet()
        _refusal.value = null
        // The line about a food's figures is deliberately left standing: a food is not a property
        // of a day, and swiping to yesterday must not take it away before it has been read (D45).
    }

    /** Write down one thing eaten, on the day being looked at. */
    fun log(item: FoodItem) {
        logOn(selectedDay.value, item)
        _justLogged.value = "Logged ${item.name} — ${item.kcal} kcal"
    }

    /**
     * Write down a whole meal — several components at once, from the model's proposal.
     *
     * One meal holding every item, not one meal per item: they were eaten together, and step 9's
     * repeat will re-log them together.
     */
    fun logMeal(items: List<FoodItem>) {
        if (items.isEmpty()) return
        sayWhatWasLogged(items)
        viewModelScope.launch { writeMeal(items) }
    }

    /**
     * Log what the model proposed, and leave exactly those rows chosen, ready to be named (D46, #24).
     *
     * The same write [logMeal] does, and then one thing more: the ids that write handed back become
     * the choice, so the naming sheet the day already uses can be opened over the accept screen with
     * those rows and no others in it.
     *
     * **Written first, and on its own.** A meal that cannot be made must never cost him the log, so
     * nothing about gathering is attempted here — that is the sheet's confirm, and it is
     * [makeMealFromChosen] unchanged.
     *
     * **The choice is ASSIGNED, never added to.** *Describe a meal* is reachable from the day with
     * rows already ticked, and adding would make a meal that quietly included them.
     *
     * **An old choice and an old refusal go in the tap's own frame, before the write.** The accept
     * screen opens the naming sheet the moment the day has rows chosen, and the write is two
     * database round trips away; anything cleared only when it answers is on screen throughout that
     * window. A choice left standing there is a sheet over rows he ticked earlier, and a Confirm
     * pressed inside it would gather THOSE rows into the named meal — the very swallowing this path
     * exists to prevent. A refusal left standing is an old sentence under a brand-new name, and it
     * need not be a stale frame: the day's refusal is written by [setEatenAt] too, so it can have
     * been on the day for minutes. The day screen already clears it before opening this same sheet
     * (`DayScreenContent`'s *Make a meal*); the route from the accept screen does the same thing
     * here, where it cannot be forgotten by a second caller.
     *
     * An empty answer logs nothing and changes nothing, exactly as [logMeal] refuses one: a sheet
     * over no rows is a Confirm that silently does nothing. It returns before the clearing on
     * purpose — a second tap on the offer hands back an empty answer, and clearing then would throw
     * away the choice the first tap has just made.
     */
    fun logMealAndChoose(items: List<FoodItem>) {
        if (items.isEmpty()) return
        // Synchronously, so that every frame between the tap and the write has an empty choice in
        // it: the sheet stays shut until the ids arrive, and it can never open over the old ones.
        _chosen.value = emptySet()
        _refusal.value = null
        sayWhatWasLogged(items)
        viewModelScope.launch {
            val ids = writeMeal(items)
            // Assigned, not added to: a row ticked on the day before he came here is not part of
            // what he just described.
            _chosen.value = ids.toSet()
        }
    }

    /**
     * Write one meal down, and hand back the ids of the rows it wrote.
     *
     * The body [logMeal] has always had, said once: the rows are attached to their foods before they
     * are written, so a row points at its food from the moment it exists, and the notice about what
     * that logging taught a food is replaced whole (D45). The ids are the write's own answer, in the
     * order the rows were given, and only the route that goes on to name a meal reads them.
     */
    private suspend fun writeMeal(items: List<FoodItem>): List<Long> {
        // Attached before it is written, so the row points at the food from the moment it
        // exists. Every way in reaches this line.
        val attached = loggedFoods.attach(items)
        // One notice per action, replaced whole — including replaced with nothing (D45).
        _foodRetaught.value = RetaughtWording.notice(attached.retaught, RetaughtBecause.JUST_LOGGED)
        return meals.log(
            Meal(
                epochDay = selectedDay.value,
                loggedAtMillis = System.currentTimeMillis(),
                items = attached.items,
            ),
        )
    }

    /** Saving is never silent, whichever way a described meal was accepted. */
    private fun sayWhatWasLogged(items: List<FoodItem>) {
        _justLogged.value = if (items.size == 1) {
            "Logged ${items.single().name} — ${items.single().kcal} kcal"
        } else {
            "Logged ${items.size} items — ${items.sumOf { it.kcal }} kcal"
        }
    }

    /**
     * Write down one thing eaten, off a packet.
     *
     * The barcode travels with it so the food is identified by the packet rather than only by what
     * the packet happens to be called — which is what makes a bar described last week and scanned
     * today one food. The packet's figures are offered to that food — the figures as printed, not
     * worked back from the whole-gram row — and, being a label, win; they touch nothing about what
     * one of it is worth or what one of it weighs. Barcode, brand and figures all come off the one
     * product, so they cannot disagree.
     */
    fun logScanned(item: FoodItem, product: Product) {
        _justLogged.value = "Logged ${item.name} — ${item.kcal} kcal"
        viewModelScope.launch {
            val attached = loggedFoods.attach(
                item,
                barcode = product.barcode,
                brand = product.brand,
                labelPer100g = product.nutrientsPer100g(),
            )
            // A newer packet replacing an older packet's figure is said out loud too (D45).
            _foodRetaught.value = RetaughtWording.notice(attached.retaught, RetaughtBecause.JUST_LOGGED)
            meals.log(
                Meal(
                    epochDay = selectedDay.value,
                    loggedAtMillis = System.currentTimeMillis(),
                    items = listOf(attached.item),
                ),
            )
        }
    }

    /**
     * Write down a meal the owner built.
     *
     * The day's row carries which meal it was, so renaming the meal retitles every day it was ever
     * eaten — and whether what was logged differed from the meal **as it stood at that moment**,
     * which is a fact known exactly then and never again.
     *
     * That flag is an input to how the day's row is labelled and to nothing else. Nothing learns
     * from it: drop the oil every day for a month and the salad still has oil in it.
     */
    fun logSavedMeal(meal: LoggedMeal) {
        if (meal.items.isEmpty()) return
        _justLogged.value = if (meal.items.size == 1) {
            "Logged ${meal.items.single().name} — ${meal.items.single().kcal} kcal"
        } else {
            "Logged ${meal.items.size} items — ${meal.items.sumOf { it.kcal }} kcal"
        }
        viewModelScope.launch {
            // Every item of a saved meal carries its food already, so this attach teaches nothing
            // and has nothing to report — which still replaces whatever the last action said.
            val attached = loggedFoods.attach(meal.items)
            _foodRetaught.value = RetaughtWording.notice(attached.retaught, RetaughtBecause.JUST_LOGGED)
            meals.log(
                Meal(
                    epochDay = selectedDay.value,
                    loggedAtMillis = System.currentTimeMillis(),
                    items = attached.items,
                    savedMealId = meal.savedMealId,
                    savedMealAdjusted = meal.adjusted,
                ),
            )
        }
    }

    /**
     * Open a meal he built, or close it again.
     *
     * Kept per meal rather than one-at-a-time: two salads on one day are two things he may want to
     * look at, and closing one to see the other would be the screen having an opinion about that.
     */
    fun toggleMeal(mealId: Long) {
        _openMeals.value = _openMeals.value.toMutableSet().apply {
            if (!add(mealId)) remove(mealId)
        }
    }

    // --- Making a meal out of the day (design §3.5) ---------------------------------------------

    /**
     * Holding a row starts choosing, and chooses that row.
     *
     * Holding rather than tapping, for the reason the foods list uses: a tap on a row already means
     * something — it opens what is under a meal — and choosing that began on a tap would turn every
     * look at the day into the start of a meal.
     */
    fun beginChoosing(itemId: Long) {
        _chosen.value = _chosen.value + itemId
    }

    /** Tapping while choosing ticks a row or unticks it; the last one out ends choosing. */
    fun toggleChosen(itemId: Long) {
        val chosen = _chosen.value
        _chosen.value = if (itemId in chosen) chosen - itemId else chosen + itemId
    }

    /**
     * A whole meal he built, taken into the choice or out of it in one act (D50, #50).
     *
     * **All in, or all out — never row by row.** Toggling each of its rows in turn would invert a
     * meal that was half chosen rather than choosing it, so a kicker tapped on a partly ticked meal
     * would drop exactly the rows he had already picked. Partly in counts as not in, so the first
     * tap always completes the meal and only a second one empties it.
     *
     * Here rather than at the call site because this is a rule about what the choice IS, and a rule
     * written in a screen is a rule no test can put a half-chosen meal to.
     */
    fun chooseMeal(meal: Meal) {
        val ids = meal.items.map { it.id }.toSet()
        val chosen = _chosen.value
        _chosen.value = if (ids.all { it in chosen }) chosen - ids else chosen + ids
    }

    /**
     * Everything on the day, across the meals it was logged in.
     *
     * Which is what "save this day as a meal" amounts to (design §4): the same gesture with every
     * row ticked, and one tap to get there rather than a button of its own.
     */
    fun chooseAll() {
        viewModelScope.launch {
            _chosen.value = meals.observeDay(selectedDay.value).first()
                .flatMap { it.items }
                .map { it.id }
                .toSet()
        }
    }

    /** End choosing outright, leaving the day exactly as it was. */
    fun clearChoosing() {
        _chosen.value = emptySet()
        _refusal.value = null
    }

    /** He has read the refusal. What he chose is still chosen, so he can drop a row and try again. */
    fun dismissRefusal() {
        _refusal.value = null
    }

    /**
     * Name the chosen rows, and they become a meal he built, with those rows gathered under it.
     *
     * The whole act, in order and in one coroutine: read the rows in the DAY's order — not the order
     * he happened to tap — turn them into components, create the meal, put each component in, and
     * only then repoint the rows. **Not one stored number changes**: the day gains a title and the
     * parts stay exactly as they were logged.
     *
     * **Three things stop it, and each stops it whole.** A row that cannot become a component,
     * because half a meal is worse than none; a name another meal already holds, because creating
     * nothing and gathering anyway would point the rows at a meal that does not exist; and a row
     * that has gone from the day since he ticked it, which would make the meal quietly smaller than
     * what he chose. In every case what he chose stays chosen, so the refusal is a next step rather
     * than a dead end.
     */
    fun makeMealFromChosen(name: String) {
        val typed = name.trim()
        if (typed.isBlank()) return
        viewModelScope.launch {
            val epochDay = selectedDay.value
            val chosen = _chosen.value
            if (chosen.isEmpty()) return@launch

            val rows = meals.observeDay(epochDay).first()
                .flatMap { it.items }
                .filter { it.id in chosen }

            // A row deleted between ticking it and naming the meal would otherwise be quietly left
            // out, and a meal quietly smaller than the day it came from is exactly the half meal
            // this whole path refuses. The rows are re-read here because they must be — the choice
            // is ids, and the day may have moved under it — so the count is compared as well.
            //
            // **Including when every one of them has gone.** That case used to return in silence,
            // in front of this comparison, on the reasoning that there was nothing to make a meal
            // out of. There is not, and he still needs telling: the sheet closes when the choosing
            // ends and a silent return ends nothing, so it sat open over a button that did nothing
            // at all. Zero rows against two chosen is the same refusal as one against two.
            if (rows.size != chosen.size) {
                _refusal.value = MealWording.chosenRowGone
                return@launch
            }

            // Read once, here, so the conversion itself stays pure and synchronous.
            val byId = rows.mapNotNull { it.foodId }
                .distinct()
                .mapNotNull { foods.byId(it) }
                .associateBy { it.id }

            val outcome = MealFromDay.from(rows) { byId[it] }
            if (outcome.refusals.isNotEmpty()) {
                _refusal.value = outcome.refusals.joinToString("\n")
                return@launch
            }

            val mealId = when (val built = savedMeals.create(typed)) {
                is MealResult.Built -> built.mealId
                is MealResult.NameTaken -> {
                    _refusal.value = MealWording.nameTaken(typed)
                    return@launch
                }
                // Unreachable today: `create` answers only Built or NameTaken. Named rather than
                // dropped so this reads as a case that cannot arise, not a case forgotten.
                MealResult.Done -> return@launch
            }

            outcome.components.forEach { component ->
                savedMeals.put(
                    mealId = mealId,
                    foodId = component.food.id,
                    amount = component.amount,
                    countedAs = component.countedAs,
                )
            }

            meals.gatherIntoSavedMeal(
                epochDay = epochDay,
                itemIds = rows.map { it.id },
                savedMealId = mealId,
            )

            // Done with, so the day goes back to being a day.
            _chosen.value = emptySet()
            _refusal.value = null
        }
    }

    /**
     * Correct one item already stored, leaving it where it is — and attached to the food it is.
     *
     * Until issue #22 every correction silently detached the row from its food: the form rebuilt it
     * without the link, and this wrote that straight to the record. The row then looked unchanged,
     * could never join a meal, and nothing could reattach it.
     *
     * **The same name is the same food**: a corrected calorie figure changes the row, never which
     * food it is. **A new name is a claim that it was a different food**, so the row goes to the food
     * that name belongs to — found, or made — exactly as logging it under that name would have done.
     * A row that was attached to nothing and keeps its name stays attached to nothing: its food may
     * have been deleted on purpose, and correcting a figure is no reason to bring it back.
     *
     * **A figure he changes here is his, not the packet's** (D44, issue #35): a scanned row whose
     * calories or macros he corrects is stored as TYPED, because the manufacturer never declared the
     * number he just typed. This is the one place a correction becomes a stored row, so every route
     * into the editor behaves the same and the editor itself stays a dumb carrier of source and
     * confidence. It happens BEFORE the row reaches the food list, so a corrected row that is also
     * renamed teaches its new food his own figure rather than the copied one D43 gives a row that is
     * still the label's. Every other source is carried through exactly as before — correcting a
     * guess does not make it a measurement (D4).
     */
    fun correctItem(before: FoodItem, after: FoodItem) {
        viewModelScope.launch {
            val his = after.correctionOf(before)
            val sameFood = FoodKeys.nameKey(before.name) == FoodKeys.nameKey(after.name)
            // A rename teaches the food it renames onto exactly as logging does — D44's own route
            // — so it speaks exactly as logging does (D45), and replaces whatever the last action
            // said. A correction that KEEPS the name teaches no food and is not a logging action,
            // so it leaves the last sentence alone: fixing the grams on one row is the commonest
            // thing he does after logging, and clearing here would wipe the notice unread —
            // exactly the silence issue #13 exists to close.
            val attached = if (sameFood) null else loggedFoods.attach(his.copy(foodId = null))
            if (attached != null) {
                _foodRetaught.value =
                    RetaughtWording.notice(attached.retaught, RetaughtBecause.JUST_LOGGED)
            }
            val corrected = attached?.item ?: his.copy(foodId = before.foodId)
            meals.updateItem(corrected)
        }
    }

    /**
     * Say when a meal was eaten, on the day it is on (D33).
     *
     * A typical case: something missed and logged later, whose time is known. Everything the ratio
     * says is derived from meal times, and the app otherwise records the moment of LOGGING.
     *
     * The time is put on the meal's own day, so the meal stays where it is and only moves within it;
     * something eaten after midnight belongs on the next day's page. A time that has not happened yet
     * is refused rather than stored: it would hold open a fast that has closed, and every time the
     * ratio says would follow it. Correcting a time corrects the record and any verdict follows it —
     * that is not a rule reaching backwards (D27); the rule in force that day still judges it.
     */
    fun setEatenAt(meal: Meal, hour: Int, minute: Int) {
        val at = LocalDate.ofEpochDay(meal.epochDay)
            .atTime(hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        if (at > now()) {
            _refusal.value = DayTotalsWording.laterThanNow(hour, minute)
            return
        }
        // A refusal from an earlier try goes with the success that replaces it.
        _refusal.value = null
        viewModelScope.launch { meals.setEatenAt(meal.id, at) }
    }

    /**
     * Remove one item, keeping the store's receipt for it so that [undoDelete] can put it back.
     *
     * The receipt comes from the store, read in the same transaction as the delete, because the
     * row's id is gone the moment the row is and its meal may go with it. It is held as a promise,
     * set here before the delete runs, so an Undo pressed before the delete has finished waits for
     * THIS delete rather than acting on whatever an earlier one left behind.
     */
    fun deleteItem(item: FoodItem) {
        val deleted = CompletableDeferred<DeletedEntry?>()
        undoable.addLast(deleted)
        _canUndo.value = true
        viewModelScope.launch {
            // Completed in `finally` as well, for a delete that is cancelled — the screen going away
            // mid-delete — so an Undo waiting on it finishes with nothing to put back rather than
            // waiting for ever. A delete that throws still surfaces its error as before. The second
            // `complete` does nothing.
            try {
                deleted.complete(meals.deleteItem(item.id))
            } finally {
                deleted.complete(null)
            }
        }
    }

    /**
     * Remove everything that is ticked, in one act (#50) — each row keeping its own receipt.
     *
     * Through [deleteItem] once per row rather than through a delete of its own, so every row that
     * goes this way is as recoverable as a row deleted on its own: the receipts stack, and Undo
     * takes them back one at a time, most recent first. A single "delete these" in the store would
     * have been one receipt for several rows and a second thing for Undo to know how to reverse.
     *
     * The rows are re-read from the store rather than taken from what is on screen, for
     * [makeMealFromChosen]'s reason: the choice is ids, and the day may have moved under it.
     *
     * The choice is spent as it is used. Leaving it standing would leave the bar offering to make a
     * meal out of rows that are no longer there.
     */
    fun deleteChosen() {
        viewModelScope.launch {
            val chosen = _chosen.value
            if (chosen.isEmpty()) return@launch
            val rows = meals.observeDay(selectedDay.value).first()
                .flatMap { it.items }
                .filter { it.id in chosen }
            _chosen.value = emptySet()
            _refusal.value = null
            rows.forEach(::deleteItem)
        }
    }

    /**
     * Put back the last thing deleted, as it was (issue #25): that row, under its own id, in the
     * meal it came from, at the time it was eaten, on the day it was on — whichever page is showing
     * now, because the day travels inside the receipt.
     *
     * Not through [logOn]: restoring is not logging. Re-logging stamped the row with the moment of
     * undoing, which on a past day falls on another date and left the meal untimed, and it ran the
     * row through the food list, which could make a food or offer one figures.
     *
     * Cleared as it is used, so a second press does not put the row back twice.
     */
    fun undoDelete() {
        val deleted = undoable.removeLastOrNull() ?: return
        _canUndo.value = undoable.isNotEmpty()
        viewModelScope.launch { deleted.await()?.let { meals.restore(it) } }
    }

    private fun logOn(epochDay: Long, item: FoodItem) {
        viewModelScope.launch {
            val attached = loggedFoods.attach(item)
            // The issue's own door: typing a name he has used before still teaches that food, and
            // now says which figure it replaced (D45, issue #13).
            _foodRetaught.value = RetaughtWording.notice(attached.retaught, RetaughtBecause.JUST_LOGGED)
            meals.log(
                Meal(
                    epochDay = epochDay,
                    loggedAtMillis = System.currentTimeMillis(),
                    items = listOf(attached.item),
                ),
            )
        }
    }

    /**
     * Every row deleted on this screen, oldest first, each as the promise of its own receipt.
     *
     * A stack rather than the single slot it replaces: tidying up two rows and then changing his
     * mind used to cost the first one for good, because the second delete overwrote the only
     * receipt there was. Undo takes the most recent, which is the order anyone expects and the
     * order the rows left in.
     *
     * Unbounded, and deliberately so. The receipts live in memory only as long as this screen
     * does, and the alternative — a cap — would mean picking a number at which an undo silently
     * stops working, which is the defect being fixed rather than a fix for it.
     */
    private val undoable = ArrayDeque<CompletableDeferred<DeletedEntry?>>()

    private val _canUndo = MutableStateFlow(false)

    /**
     * Whether anything deleted here can still be put back — what the Undo line is drawn from.
     *
     * Held by the view model rather than by the screen, which used to keep a boolean of its own
     * and set it beside each call. That flag could not count: two deletes and one undo left it
     * true with a second row still recoverable, or false with one, depending on which way round
     * they happened.
     */
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private companion object {
        /** A fortnight: long enough to show whether a decision is holding, short enough to matter. */
        const val TALLY_DAYS = 13L

        /**
         * How far past the start of a range the measured window has to read.
         *
         * Two calendar days, and the reasoning is [WindowRules.stretches]'s: a fast is at most
         * twenty-three hours, so an input more than a day before the first input of a range has
         * already closed whatever stretch it was in. One day covers it whatever the hour; the
         * second is margin. It buys knowing whether the range's first input OPENS a stretch or
         * merely continues one begun the evening before.
         */
        const val LEAD_IN_DAYS = 2L

        /**
         * How far past a day the measured window has to read to see the end of what began on it.
         *
         * The mirror of [LEAD_IN_DAYS], and it buys something different: not where a stretch
         * BEGINS, but how far it ran. A stretch belongs to the day it began on and is shown its
         * whole length there, so last night's dinner and this morning's coffee are one line on last
         * night — which is the case this whole change exists for.
         *
         * Two days rather than one for margin, and it is a bound rather than a proof: a stretch
         * still being added to two days after it began would mean eating across two whole nights
         * without ever fasting the hours he set. Such a stretch is broken many times over by any
         * ratio — and the app still says nothing of the kind about it, because the read declares
         * where it stopped ([MealRead]) and a stretch still being added to on the last day read has
         * not been seen to end. It comes back OPEN: no span at all rather than a short one, the
         * still-open sentence in its place, and no contribution to either half of the tally.
         * Nothing is ever read past today, where there is nothing to find.
         */
        const val RUN_ON_DAYS = 2L

        /** Seven of whatever the window in force counts in. A run, not a tally. */
        const val A_WHOLE_RUN = 7L

        const val STOP_TIMEOUT_MS = 5_000L
    }
}
