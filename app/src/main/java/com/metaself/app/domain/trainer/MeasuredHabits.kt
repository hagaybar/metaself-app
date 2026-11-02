package com.metaself.app.domain.trainer

import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.PartOfTheClock
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.target.DailyTarget
import com.metaself.app.domain.window.hasItsOwnTime
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** D162: how much of a span there is to read: stated always, first, because a gap must never read as a fast. */
data class HabitCoverage(
    val days: Int,
    val beforeRecord: Int,
    val logged: Int,
    val notLogged: Int,
    val loggedAsZero: Int,
    val underHalfTarget: Int,
    val untimedLoggings: Int,
)

/** D162, D4: a day's average over [days] days, and the share of its calories that rests on an estimate, 0 to 1. */
data class HabitAverages(val days: Int, val kcal: Int, val proteinG: Int, val carbsG: Int, val fatG: Int, val estimatedShare: Double)

/** D162: a clock figure, in minutes from midnight, and how far the days spread around it (one standard deviation). */
data class HabitClock(val minutes: Int, val spreadMinutes: Int)

/**
 * D162: when eating happens, read only from loggings with an honest time (D33). [days] is the days it rests on: a
 * logged day with no logging lacking a time. [fastingHours] is null with no two such days side by side, because a gap
 * is not a fast. [lateShare] is the share of timed calories in the evening and night parts of the clock (D51).
 */
data class HabitTiming(
    val days: Int,
    val firstEating: HabitClock,
    val lastEating: HabitClock,
    val eatingHours: Double,
    val eatingHoursSpread: Double,
    val fastingHours: Double?,
    val fastingHoursSpread: Double?,
    val fastsMeasured: Int,
    val lateShare: Double,
    val occasionsADay: Double,
)

/** D162: one food: [times] logged, on [days] days, its share of the span's calories and how much of it is estimates. */
data class HabitFood(val name: String, val times: Int, val days: Int, val kcalShare: Double, val estimatedShare: Double)

/** D162: two foods eaten at one occasion, [times] over the span. */
data class HabitPair(val first: String, val second: String, val times: Int)

/** D162: what is eaten: the most frequent, what goes together, how many different foods, and how much a few of them carry. */
data class HabitFoods(
    val mostFrequent: List<HabitFood>,
    val combinations: List<HabitPair>,
    val variety: Int,
    val topFewShare: Double,
)

/** D162: food in the hours either side of a session, averaged over [sessions] sessions on logged days with honest times. */
data class HabitAroundSession(
    val sessions: Int,
    val hours: Int,
    val beforeKcal: Int,
    val beforeProteinG: Int,
    val afterKcal: Int,
    val afterProteinG: Int,
    val estimatedShare: Double,
)

/** D162, D4: where the span's calories came from, each 0 to 1. [repeated] is a repeat whose origin the row does not say. */
data class HabitSources(val label: Double, val typed: Double, val estimate: Double, val repeated: Double, val other: Double)

/**
 * D162: what a span's logged days show. A part with too few days behind it is null: not stated. [daysOverTarget] is
 * against today's target, which is the only target the record holds.
 */
data class HabitsStated(
    val averages: HabitAverages,
    val kcalAgainstTarget: Int?,
    val daysOverTarget: Int?,
    val proteinPerKg: Double?,
    val timing: HabitTiming?,
    val weekday: HabitAverages?,
    val weekend: HabitAverages?,
    val foods: HabitFoods,
    val trainingDays: HabitAverages?,
    val restDays: HabitAverages?,
    val aroundSession: HabitAroundSession?,
    val sources: HabitSources,
)

/** D162: one span: its [coverage] always, [stated] only with at least [MeasuredHabits.MIN_LOGGED_DAYS] logged days. */
data class HabitSpan(val from: Long, val to: Long, val coverage: HabitCoverage, val stated: HabitsStated?)

/** D162: the last weeks against the ones before, with what they are read against. Computed on demand, never stored. */
data class HabitsReport(
    val recent: HabitSpan,
    val before: HabitSpan,
    val targetKcal: Int?,
    val targetProteinG: Int?,
    val targetCarbsG: Int?,
    val targetFatG: Int?,
    val trendKg: Double?,
)

/**
 * D162: measured habits, worked out by the phone and never by the AI. Pure.
 *
 * Whole days only: a span ends yesterday, because today half-logged would read as under-eating. Averages rest on days
 * with calories in them; a day logged as zero is counted in coverage, not in an average. A food is its food id, else
 * its name. `Meal.savedMealAdjusted` is never read: its KDoc forbids counting it.
 *
 * **An eating day runs from the start of morning to the end of the night that follows it** (D51's parts of the clock),
 * so eating past midnight belongs to the evening it continues rather than opening a new day: a logging at half past
 * midnight is the last of one day, never the first of the next. Calories stay on the day they were logged against.
 */
object MeasuredHabits {

    /** D162: each span, in days: 4 weeks. A chosen span. */
    const val SPAN_DAYS = 28

    /** D162: below this many logged days a span states nothing but its coverage. A chosen size. */
    const val MIN_LOGGED_DAYS = 7

    /** Below this many days, one side of a comparison (weekend, training days) is not stated. A chosen size. */
    const val MIN_SPLIT_DAYS = 3

    /** Below this many sessions, food around a session is not stated. A chosen size. */
    const val MIN_SESSIONS = 3

    /** Food this close before or after a session counts as around it. A chosen size. */
    const val AROUND_SESSION_HOURS = 3

    /** Loggings this close together are one occasion. A chosen size. */
    const val OCCASION_GAP_MINUTES = 30

    const val MOST_FREQUENT_FOODS = 8
    const val COMBINATIONS = 5

    /** The "few foods" whose share of the calories is stated. A chosen size. */
    const val TOP_FEW_FOODS = 5

    /** A chosen pair: named here so it can be changed in one place. */
    val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    /** How a stored moment reads as an eating day: the part of the clock it opens on (D51). */
    private val EATING_DAY_STARTS_HOUR = PartOfTheClock.MORNING.firstHour

    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS

    /**
     * @param meals the loggings from [firstDayRead] to [today], at least.
     * @param target today's target, null with no profile.
     * @param trendKg the latest point of the smoothed weight trend, null with no reading.
     * @param workouts any workouts; only the visible, counted ones are read.
     * @param firstLoggedDay the first day any food was logged, null when none was.
     */
    fun of(
        today: Long,
        meals: List<Meal>,
        zone: ZoneId,
        target: DailyTarget?,
        trendKg: Double?,
        workouts: List<Workout>,
        firstLoggedDay: Long?,
    ): HabitsReport {
        val sessions = workouts.filter { !it.hidden && it.counted }
        val recentTo = today - 1
        val recentFrom = today - SPAN_DAYS
        val beforeTo = recentFrom - 1
        val beforeFrom = beforeTo - SPAN_DAYS + 1
        return HabitsReport(
            recent = span(recentFrom, recentTo, meals, zone, target, trendKg, sessions, firstLoggedDay),
            before = span(beforeFrom, beforeTo, meals, zone, target, trendKg, sessions, firstLoggedDay),
            targetKcal = target?.kcal,
            targetProteinG = target?.macros?.proteinG,
            targetCarbsG = target?.macros?.carbsG,
            targetFatG = target?.macros?.fatG,
            trendKg = trendKg,
        )
    }

    /** The first day [of] reads. */
    fun firstDayRead(today: Long): Long = today - 2L * SPAN_DAYS

    private fun span(
        from: Long,
        to: Long,
        meals: List<Meal>,
        zone: ZoneId,
        target: DailyTarget?,
        trendKg: Double?,
        sessions: List<Workout>,
        firstLoggedDay: Long?,
    ): HabitSpan {
        val byDay = meals.filter { it.epochDay in from..to }.groupBy { it.epochDay }
        val days = (to - from + 1).toInt()
        val beforeRecord = if (firstLoggedDay == null) days else (firstLoggedDay - from).coerceIn(0, days.toLong()).toInt()
        val kcalOf = byDay.mapValues { (_, loggings) -> loggings.sumOf { meal -> meal.items.sumOf { it.kcal } } }
        val zero = kcalOf.count { it.value == 0 }
        val untimed = byDay.values.flatten().count { !hasItsOwnTime(it, zone) }
        val coverage = HabitCoverage(
            days = days,
            beforeRecord = beforeRecord,
            logged = byDay.size,
            notLogged = days - beforeRecord - byDay.size,
            loggedAsZero = zero,
            underHalfTarget = target?.let { goal -> kcalOf.count { it.value in 1 until goal.kcal / 2 } } ?: 0,
            untimedLoggings = untimed,
        )
        if (byDay.size < MIN_LOGGED_DAYS) return HabitSpan(from, to, coverage, null)
        val eatingDays = byDay.filterKeys { (kcalOf[it] ?: 0) > 0 }
        return HabitSpan(from, to, coverage, stated(from, to, byDay, eatingDays, meals, zone, target, trendKg, sessions))
    }

    private fun stated(
        from: Long,
        to: Long,
        byDay: Map<Long, List<Meal>>,
        eatingDays: Map<Long, List<Meal>>,
        meals: List<Meal>,
        zone: ZoneId,
        target: DailyTarget?,
        trendKg: Double?,
        sessions: List<Workout>,
    ): HabitsStated? {
        val averages = averages(eatingDays.values.toList()) ?: return null
        val sessionDays = sessions.filter { it.epochDay in from..to }.map { it.epochDay }.toSet()
        val weekdayOf = { day: Long -> LocalDate.ofEpochDay(day).dayOfWeek }
        val items = byDay.values.flatten().flatMap { it.items }
        return HabitsStated(
            averages = averages,
            kcalAgainstTarget = target?.let { averages.kcal - it.kcal },
            daysOverTarget = target?.let { goal -> eatingDays.values.count { day -> day.sumOf { m -> m.items.sumOf { it.kcal } } > goal.kcal } },
            proteinPerKg = trendKg?.takeIf { it > 0 }?.let { round2(averages.proteinG / it) },
            timing = timing(from, to, byDay, meals, zone),
            weekday = split(eatingDays) { weekdayOf(it) !in WEEKEND },
            weekend = split(eatingDays) { weekdayOf(it) in WEEKEND },
            foods = foods(byDay, zone, items),
            trainingDays = split(eatingDays) { it in sessionDays },
            restDays = split(eatingDays) { it !in sessionDays },
            aroundSession = aroundSession(sessions.filter { it.epochDay in from..to }, byDay, meals, zone),
            sources = sources(items),
        )
    }

    private fun split(days: Map<Long, List<Meal>>, keep: (Long) -> Boolean): HabitAverages? =
        days.filterKeys(keep).values.toList().takeIf { it.size >= MIN_SPLIT_DAYS }?.let(::averages)

    private fun averages(days: List<List<Meal>>): HabitAverages? {
        if (days.isEmpty()) return null
        val items = days.flatten().flatMap { it.items }
        fun mean(figure: (FoodItem) -> Int) = (items.sumOf(figure).toDouble() / days.size).roundToInt()
        return HabitAverages(days.size, mean { it.kcal }, mean { it.proteinG }, mean { it.carbsG }, mean { it.fatG }, estimatedShare(items))
    }

    /** D4: a row rests on an estimate when the model made it, or it repeats one (a repeat keeps its confidence). */
    private fun restsOnEstimate(item: FoodItem) = item.source == Source.AI_ESTIMATE || item.confidence != null

    private fun estimatedShare(items: List<FoodItem>): Double = share(items.filter(::restsOnEstimate), items)

    private fun share(part: List<FoodItem>, whole: List<FoodItem>): Double {
        val total = whole.sumOf { it.kcal }
        return if (total == 0) 0.0 else round2(part.sumOf { it.kcal }.toDouble() / total)
    }

    private fun sources(items: List<FoodItem>): HabitSources {
        val estimates = items.filter(::restsOnEstimate)
        val rest = items.filterNot(::restsOnEstimate)
        return HabitSources(
            label = share(rest.filter { it.source == Source.LABEL }, items),
            typed = share(rest.filter { it.source == Source.TYPED }, items),
            estimate = share(estimates, items),
            repeated = share(rest.filter { it.source == Source.REPEATED }, items),
            other = share(rest.filter { it.source == Source.UNRECOGNISED }, items),
        )
    }

    /** A logging's moment read as minutes into its eating day, and which eating day that is. */
    private data class EatingMoment(val eatingDay: Long, val minutes: Int, val meal: Meal)

    private fun moment(meal: Meal, zone: ZoneId): EatingMoment {
        val at = Instant.ofEpochMilli(meal.loggedAtMillis).atZone(zone)
        val minuteOfDay = at.hour * 60 + at.minute
        val start = EATING_DAY_STARTS_HOUR * 60
        return if (minuteOfDay >= start) {
            EatingMoment(meal.epochDay, minuteOfDay - start, meal)
        } else {
            EatingMoment(meal.epochDay - 1, minuteOfDay + 24 * 60 - start, meal)
        }
    }

    private fun timing(from: Long, to: Long, byDay: Map<Long, List<Meal>>, meals: List<Meal>, zone: ZoneId): HabitTiming? {
        // D33: a day holding a logging with no honest time is out of timing whole: what it hides could be the first or
        // last thing eaten that day. Its other loggings stay in the late-day share, which is about calories at a time.
        val clean = byDay.filterValues { day -> day.all { hasItsOwnTime(it, zone) } }.keys
        val timedAll = meals.filter { hasItsOwnTime(it, zone) }.map { moment(it, zone) }
        val eatingDays = timedAll.filter { it.eatingDay in from..to && it.eatingDay in clean }
            .groupBy { it.eatingDay }
            .mapValues { (_, moments) -> moments.sortedBy { it.meal.loggedAtMillis } }
        if (eatingDays.size < MIN_LOGGED_DAYS) return null

        val start = EATING_DAY_STARTS_HOUR * 60
        val firsts = eatingDays.values.map { it.first().minutes.toDouble() }
        val lasts = eatingDays.values.map { it.last().minutes.toDouble() }
        val spans = eatingDays.values.map { (it.last().meal.loggedAtMillis - it.first().meal.loggedAtMillis).toDouble() / HOUR_MILLIS }
        val fasts = eatingDays.keys.sorted().mapNotNull { day ->
            val next = eatingDays[day + 1] ?: return@mapNotNull null
            (next.first().meal.loggedAtMillis - eatingDays.getValue(day).last().meal.loggedAtMillis).toDouble() / HOUR_MILLIS
        }
        val timedInSpan = timedAll.filter { it.meal.epochDay in from..to }
        val timedKcal = timedInSpan.sumOf { moment -> moment.meal.items.sumOf { it.kcal } }
        val lateKcal = timedInSpan.filter { isLate(it.meal, zone) }.sumOf { moment -> moment.meal.items.sumOf { it.kcal } }
        return HabitTiming(
            days = eatingDays.size,
            firstEating = HabitClock((firsts.average().roundToInt() + start) % (24 * 60), spread(firsts).roundToInt()),
            lastEating = HabitClock((lasts.average().roundToInt() + start) % (24 * 60), spread(lasts).roundToInt()),
            eatingHours = round1(spans.average()),
            eatingHoursSpread = round1(spread(spans)),
            fastingHours = fasts.takeIf { it.isNotEmpty() }?.let { round1(it.average()) },
            fastingHoursSpread = fasts.takeIf { it.isNotEmpty() }?.let { round1(spread(it)) },
            fastsMeasured = fasts.size,
            lateShare = if (timedKcal == 0) 0.0 else round2(lateKcal.toDouble() / timedKcal),
            occasionsADay = round1(eatingDays.values.map { occasions(it.map { moment -> moment.meal }).size }.average()),
        )
    }

    private fun isLate(meal: Meal, zone: ZoneId): Boolean {
        val hour = Instant.ofEpochMilli(meal.loggedAtMillis).atZone(zone).hour
        return hour >= PartOfTheClock.EVENING.firstHour || hour <= PartOfTheClock.NIGHT.lastHour
    }

    /** Loggings (in time order) gathered into occasions, close together by [OCCASION_GAP_MINUTES]. */
    private fun occasions(loggings: List<Meal>): List<List<Meal>> {
        val out = mutableListOf<MutableList<Meal>>()
        for (meal in loggings) {
            val last = out.lastOrNull()?.last()
            if (last != null && meal.loggedAtMillis - last.loggedAtMillis <= OCCASION_GAP_MINUTES * MINUTE_MILLIS) {
                out.last() += meal
            } else {
                out += mutableListOf(meal)
            }
        }
        return out
    }

    private fun foods(byDay: Map<Long, List<Meal>>, zone: ZoneId, items: List<FoodItem>): HabitFoods {
        val byFood = items.groupBy(::identity)
        val daysOf = mutableMapOf<String, MutableSet<Long>>()
        byDay.forEach { (day, loggings) -> loggings.flatMap { it.items }.forEach { daysOf.getOrPut(identity(it)) { mutableSetOf() } += day } }
        val nameOf = byFood.mapValues { (_, rows) -> rows.last().label }

        val most = byFood.entries.sortedWith(compareByDescending<Map.Entry<String, List<FoodItem>>> { it.value.size }.thenBy { nameOf.getValue(it.key) })
            .take(MOST_FREQUENT_FOODS)
            .map { (key, rows) -> HabitFood(nameOf.getValue(key), rows.size, daysOf.getValue(key).size, share(rows, items), estimatedShare(rows)) }

        val pairs = mutableMapOf<Pair<String, String>, Int>()
        byDay.values.forEach { loggings ->
            val (timed, untimed) = loggings.partition { hasItsOwnTime(it, zone) }
            // A logging with no honest time is its own occasion: only what it holds was certainly eaten together.
            val gathered = occasions(timed.sortedBy { it.loggedAtMillis }) + untimed.map { listOf(it) }
            gathered.forEach { occasion ->
                val keys = occasion.flatMap { it.items }.map(::identity).distinct().sorted()
                keys.forEachIndexed { index, one -> keys.drop(index + 1).forEach { two -> pairs.merge(one to two, 1, Int::plus) } }
            }
        }
        val combinations = pairs.entries.filter { it.value >= 2 }
            .sortedWith(compareByDescending<Map.Entry<Pair<String, String>, Int>> { it.value }.thenBy { nameOf.getValue(it.key.first) }.thenBy { nameOf.getValue(it.key.second) })
            .take(COMBINATIONS)
            .map { HabitPair(nameOf.getValue(it.key.first), nameOf.getValue(it.key.second), it.value) }

        val topFew = byFood.values.sortedByDescending { rows -> rows.sumOf { it.kcal } }.take(TOP_FEW_FOODS).flatten()
        return HabitFoods(most, combinations, byFood.size, share(topFew, items))
    }

    /** D162: a food is its id, falling back to its name. */
    private fun identity(item: FoodItem): String = item.foodId?.let { "id:$it" } ?: "name:${item.label.trim().lowercase()}"

    private fun aroundSession(sessions: List<Workout>, byDay: Map<Long, List<Meal>>, meals: List<Meal>, zone: ZoneId): HabitAroundSession? {
        val window = AROUND_SESSION_HOURS * HOUR_MILLIS
        val timed = meals.filter { hasItsOwnTime(it, zone) }
        // Only a session on a logged day with honest times can say what was, or was not, eaten around it.
        val read = sessions.filter { session -> byDay[session.epochDay]?.all { hasItsOwnTime(it, zone) } == true }
        if (read.size < MIN_SESSIONS) return null
        fun mean(figure: (Workout) -> Int) = (read.sumOf(figure).toDouble() / read.size).roundToInt()
        fun before(session: Workout) = timed.filter { it.loggedAtMillis in (session.startedAtMillis - window) until session.startedAtMillis }.flatMap { it.items }
        fun after(session: Workout): List<FoodItem> {
            val end = session.startedAtMillis + session.durationMinutes * MINUTE_MILLIS
            return timed.filter { it.loggedAtMillis in end..(end + window) }.flatMap { it.items }
        }
        return HabitAroundSession(
            sessions = read.size,
            hours = AROUND_SESSION_HOURS,
            beforeKcal = mean { s -> before(s).sumOf { it.kcal } },
            beforeProteinG = mean { s -> before(s).sumOf { it.proteinG } },
            afterKcal = mean { s -> after(s).sumOf { it.kcal } },
            afterProteinG = mean { s -> after(s).sumOf { it.proteinG } },
            estimatedShare = estimatedShare(read.flatMap { before(it) + after(it) }),
        )
    }

    private fun spread(values: List<Double>): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    private fun round1(value: Double): Double = Math.round(value * 10) / 10.0

    private fun round2(value: Double): Double = Math.round(value * 100) / 100.0
}
