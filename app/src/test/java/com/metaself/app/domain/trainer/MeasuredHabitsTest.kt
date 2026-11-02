package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.target.DailyTarget
import com.metaself.app.domain.target.MacroSplit
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

/** D162, over a clock in UTC. Every food and figure is invented, and picked to keep the arithmetic round. */
class MeasuredHabitsTest {

    private val zone = ZoneOffset.UTC
    private val today = TEST_EPOCH_DAY
    private val target = DailyTarget(2_000, 2_000, 1_500, 1_500, 2_500, -500, false, false, MacroSplit(150, 60, 200))

    private fun at(day: Long, hour: Int, minute: Int = 0) = day * DAY + hour * HOUR + minute * MINUTE

    private fun item(
        name: String = "Invented food",
        kcal: Int = 500,
        protein: Int = 25,
        source: Source = Source.TYPED,
        confidence: Confidence? = null,
        foodId: Long? = null,
        currentName: String? = null,
    ) = FoodItem(name = name, kcal = kcal, proteinG = protein, carbsG = 50, fatG = 10, source = source, confidence = confidence, foodId = foodId, currentName = currentName)

    private fun meal(day: Long, hour: Int, minute: Int = 0, vararg items: FoodItem) =
        Meal(epochDay = day, loggedAtMillis = at(day, hour, minute), items = items.toList().ifEmpty { listOf(item()) })

    /** [count] days ending yesterday, each eaten at 08:00 and at 20:00. */
    private fun twoMealDays(count: Int) = (1..count).flatMap { back -> listOf(meal(today - back, 8), meal(today - back, 20)) }

    private fun report(
        meals: List<Meal>,
        workouts: List<Workout> = emptyList(),
        firstLogged: Long? = today - 100,
        trendKg: Double? = 80.0,
    ) = MeasuredHabits.of(today, meals, zone, target, trendKg, workouts, firstLogged)

    private fun session(day: Long, hour: Int, minutes: Int = 60, hidden: Boolean = false) = Workout(
        id = day, epochDay = day, startedAtMillis = at(day, hour), durationMinutes = minutes, kind = WorkoutKind.RUN, title = null,
        distanceM = null, energyKcal = null, energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED, hidden = hidden, note = null,
    )

    @Test
    fun `coverage is always stated, and below the minimum nothing else is`() {
        val few = report(twoMealDays(MeasuredHabits.MIN_LOGGED_DAYS - 1))

        assertThat(few.recent.stated).isNull()
        with(few.recent.coverage) {
            assertThat(days).isEqualTo(MeasuredHabits.SPAN_DAYS)
            assertThat(logged).isEqualTo(MeasuredHabits.MIN_LOGGED_DAYS - 1)
            assertThat(notLogged).isEqualTo(MeasuredHabits.SPAN_DAYS - MeasuredHabits.MIN_LOGGED_DAYS + 1)
            assertThat(beforeRecord).isEqualTo(0)
        }
        assertThat(report(twoMealDays(MeasuredHabits.MIN_LOGGED_DAYS)).recent.stated).isNotNull()
    }

    @Test
    fun `days before the record began are not days not logged, and a zero is not a gap`() {
        val zero = meal(today - 2, 8, 0, item(kcal = 0, protein = 0))
        val few = report(listOf(zero, meal(today - 1, 8)), firstLogged = today - 10)

        with(few.recent.coverage) {
            assertThat(beforeRecord).isEqualTo(18)
            assertThat(logged).isEqualTo(2)
            assertThat(loggedAsZero).isEqualTo(1)
            assertThat(notLogged).isEqualTo(8)
        }
        assertThat(few.before.coverage.beforeRecord).isEqualTo(MeasuredHabits.SPAN_DAYS)
        assertThat(report(emptyList(), firstLogged = null).recent.coverage.beforeRecord).isEqualTo(MeasuredHabits.SPAN_DAYS)
    }

    @Test
    fun `a half-logged day is named in coverage, against the target`() {
        val low = (1..2L).map { meal(today - it, 8, 0, item(kcal = 999)) } + meal(today - 3, 8, 0, item(kcal = 1_000))

        assertThat(report(low).recent.coverage.underHalfTarget).isEqualTo(2)
    }

    @Test
    fun `calories, macros and protein per kilo are read against the target, on days with food only`() {
        val days = twoMealDays(10) + meal(today - 20, 8, 0, item(kcal = 0, protein = 0))

        val stated = report(days).recent.stated!!

        assertThat(stated.averages.days).isEqualTo(10)
        assertThat(stated.averages.kcal).isEqualTo(1_000)
        assertThat(stated.averages.proteinG).isEqualTo(50)
        assertThat(stated.averages.carbsG).isEqualTo(100)
        assertThat(stated.averages.fatG).isEqualTo(20)
        assertThat(stated.kcalAgainstTarget).isEqualTo(-1_000)
        assertThat(stated.daysOverTarget).isEqualTo(0)
        assertThat(stated.proteinPerKg).isEqualTo(0.63)
        assertThat(report(days, trendKg = null).recent.stated!!.proteinPerKg).isNull()
    }

    @Test
    fun `eating times, eating hours and fasting hours come from honest times`() {
        val timing = report(twoMealDays(10)).recent.stated!!.timing!!

        assertThat(timing.days).isEqualTo(10)
        assertThat(timing.firstEating).isEqualTo(HabitClock(8 * 60, 0))
        assertThat(timing.lastEating).isEqualTo(HabitClock(20 * 60, 0))
        assertThat(timing.eatingHours).isEqualTo(12.0)
        assertThat(timing.fastingHours).isEqualTo(12.0)
        assertThat(timing.fastingHoursSpread).isEqualTo(0.0)
        assertThat(timing.fastsMeasured).isEqualTo(9)
        assertThat(timing.occasionsADay).isEqualTo(2.0)
        assertThat(timing.lateShare).isEqualTo(0.5)
    }

    @Test
    fun `regularity is how far the days spread`() {
        val days = (1..10).flatMap { back -> listOf(meal(today - back, if (back % 2 == 0) 7 else 9), meal(today - back, 20)) }

        val timing = report(days).recent.stated!!.timing!!

        assertThat(timing.firstEating).isEqualTo(HabitClock(8 * 60, 60))
        assertThat(timing.lastEating.spreadMinutes).isEqualTo(0)
    }

    /** A logging past midnight is the end of the evening it continues, not the start of a new day. */
    @Test
    fun `eating past midnight ends the day before and opens nothing`() {
        val days = (1..10).flatMap { back -> listOf(meal(today - back, 12), meal(today - back, 0, 30)) } + meal(today, 0, 30)

        val timing = report(days).recent.stated!!.timing!!

        assertThat(timing.firstEating.minutes).isEqualTo(12 * 60)
        assertThat(timing.lastEating.minutes).isEqualTo(30)
        assertThat(timing.eatingHours).isEqualTo(12.5)
        assertThat(timing.fastingHours).isEqualTo(11.5)
    }

    /** A gap is not a fast: no fast is measured across a day not logged. */
    @Test
    fun `no fast is measured across a day not logged`() {
        val days = (1..20).filter { it % 2 == 0 }.flatMap { back -> listOf(meal(today - back, 8), meal(today - back, 20)) }

        val timing = report(days).recent.stated!!.timing!!

        assertThat(timing.days).isEqualTo(10)
        assertThat(timing.fastingHours).isNull()
        assertThat(timing.fastsMeasured).isEqualTo(0)
    }

    /** D33: written down later, on another date: when it was eaten is unknown, so that day says nothing of timing. */
    @Test
    fun `a logging with no honest time takes its day out of timing, and stays in calories`() {
        val written = Meal(epochDay = today - 5, loggedAtMillis = at(today - 2, 23), items = listOf(item(kcal = 1_000)))

        val report = report(twoMealDays(10) + written)
        val stated = report.recent.stated!!

        assertThat(report.recent.coverage.untimedLoggings).isEqualTo(1)
        assertThat(stated.timing!!.days).isEqualTo(9)
        assertThat(stated.timing!!.lastEating.minutes).isEqualTo(20 * 60)
        assertThat(stated.timing!!.fastsMeasured).isEqualTo(7)
        assertThat(stated.averages.kcal).isEqualTo(1_100)
    }

    @Test
    fun `too few days with honest times state no timing`() {
        val written = (1..10L).map { Meal(epochDay = today - it, loggedAtMillis = at(today, 7), items = listOf(item())) }

        assertThat(report(written).recent.stated!!.timing).isNull()
    }

    @Test
    fun `loggings close together are one occasion`() {
        val days = (1..10).flatMap { back -> listOf(meal(today - back, 12), meal(today - back, 12, MeasuredHabits.OCCASION_GAP_MINUTES), meal(today - back, 13)) }

        assertThat(report(days).recent.stated!!.timing!!.occasionsADay).isEqualTo(1.0)
    }

    @Test
    fun `weekdays and weekends are told apart, and one with too few days is not stated`() {
        val days = (1..14L).map { back ->
            val weekend = LocalDate.ofEpochDay(today - back).dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            meal(today - back, 12, 0, item(kcal = if (weekend) 3_000 else 1_000))
        }

        val stated = report(days).recent.stated!!

        assertThat(stated.weekday!!.days).isEqualTo(10)
        assertThat(stated.weekday!!.kcal).isEqualTo(1_000)
        assertThat(stated.weekend!!.days).isEqualTo(4)
        assertThat(stated.weekend!!.kcal).isEqualTo(3_000)
        assertThat(stated.daysOverTarget).isEqualTo(4)

        val noWeekend = days.filter { LocalDate.ofEpochDay(it.epochDay).dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }
        assertThat(report(noWeekend).recent.stated!!.weekend).isNull()
    }

    @Test
    fun `a food is its id whatever it was typed as, else its name, and the frequent ones are counted`() {
        val soup = { typed: String -> item(name = typed, kcal = 400, foodId = 7, currentName = "Invented soup") }
        val days = (1..10L).flatMap { back ->
            listOf(
                meal(today - back, 8, 0, soup(if (back % 2 == 0L) "soup" else "the soup")),
                meal(today - back, 13, 0, item(name = "Invented Bread", kcal = 100)),
                meal(today - back, 13, 5, item(name = "invented bread", kcal = 300)),
            ) + if (back == 1L) listOf(meal(today - back, 19, 0, item(name = "Invented once", kcal = 200))) else emptyList()
        }

        val foods = report(days).recent.stated!!.foods

        assertThat(foods.variety).isEqualTo(3)
        assertThat(foods.mostFrequent.first().name.lowercase()).isEqualTo("invented bread")
        assertThat(foods.mostFrequent.first().times).isEqualTo(20)
        assertThat(foods.mostFrequent.first().days).isEqualTo(10)
        val named = foods.mostFrequent.single { it.name == "Invented soup" }
        assertThat(named.times).isEqualTo(10)
        // 4,000 of the 8,200 kcal.
        assertThat(named.kcalShare).isEqualTo(0.49)
        assertThat(foods.topFewShare).isEqualTo(1.0)
    }

    @Test
    fun `foods eaten at one occasion are a combination, at another they are not`() {
        val days = (1..10L).flatMap { back ->
            listOf(
                meal(today - back, 8, 0, item(name = "Invented oats"), item(name = "Invented milk")),
                meal(today - back, 8, 15, item(name = "Invented berries")),
                meal(today - back, 19, 0, item(name = "Invented fish")),
            )
        }

        val pairs = report(days).recent.stated!!.foods.combinations

        assertThat(pairs.map { it.times }.toSet()).containsExactly(10)
        assertThat(pairs.flatMap { listOf(it.first, it.second) }).doesNotContain("Invented fish")
        assertThat(pairs).hasSize(3)
    }

    @Test
    fun `a pair seen once is no combination`() {
        val days = twoMealDays(10) + meal(today - 20, 8, 0, item(name = "Invented a"), item(name = "Invented b"))

        assertThat(report(days).recent.stated!!.foods.combinations).isEmpty()
    }

    @Test
    fun `training days are read against rest days, and a hidden session is no session`() {
        val days = (1..10L).map { back -> meal(today - back, 8, 0, item(kcal = if (back <= 4) 3_000 else 1_000)) }
        val workouts = (1..4L).map { session(today - it, 10) } + session(today - 8, 10, hidden = true)

        val stated = report(days, workouts).recent.stated!!

        assertThat(stated.trainingDays!!.days).isEqualTo(4)
        assertThat(stated.trainingDays!!.kcal).isEqualTo(3_000)
        assertThat(stated.restDays!!.days).isEqualTo(6)
        assertThat(stated.restDays!!.kcal).isEqualTo(1_000)
    }

    @Test
    fun `food around a session is what was eaten in the hours either side of it`() {
        val days = (1..10L).flatMap { back ->
            listOf(
                meal(today - back, 8, 0, item(kcal = 300, protein = 10)),
                meal(today - back, 12, 0, item(kcal = 600, protein = 40, source = Source.AI_ESTIMATE, confidence = Confidence.LOW)),
                meal(today - back, 20, 0, item(kcal = 900, protein = 90)),
            )
        }
        val workouts = (1..4L).map { session(today - it, 10, minutes = 60) }

        val around = report(days, workouts).recent.stated!!.aroundSession!!

        assertThat(around.sessions).isEqualTo(4)
        assertThat(around.beforeKcal).isEqualTo(300)
        assertThat(around.beforeProteinG).isEqualTo(10)
        assertThat(around.afterKcal).isEqualTo(600)
        assertThat(around.afterProteinG).isEqualTo(40)
        // 600 of the 900 kcal around each session.
        assertThat(around.estimatedShare).isEqualTo(0.67)
    }

    @Test
    fun `too few sessions on logged days say nothing of food around them`() {
        val workouts = listOf(session(today - 1, 10), session(today - 2, 10), session(today - 25, 10))

        assertThat(report(twoMealDays(10), workouts).recent.stated!!.aroundSession).isNull()
    }

    /** D4: a repeat that carries a confidence repeats an estimate. */
    @Test
    fun `where the calories came from, and how much rests on estimates`() {
        val days = (1..10L).map { back ->
            meal(
                today - back,
                12,
                0,
                item(kcal = 400, source = Source.LABEL),
                item(kcal = 200, source = Source.TYPED),
                item(kcal = 200, source = Source.AI_ESTIMATE, confidence = Confidence.LOW),
                item(kcal = 100, source = Source.REPEATED, confidence = Confidence.HIGH),
                item(kcal = 100, source = Source.REPEATED),
            )
        }

        val stated = report(days).recent.stated!!

        assertThat(stated.sources).isEqualTo(HabitSources(label = 0.4, typed = 0.2, estimate = 0.3, repeated = 0.1, other = 0.0))
        assertThat(stated.averages.estimatedShare).isEqualTo(0.3)
    }

    @Test
    fun `today is not read, and the older span is the four weeks before`() {
        val today_ = listOf(meal(today, 8, 0, item(kcal = 9_000)))
        val older = (1..10).map { meal(today - MeasuredHabits.SPAN_DAYS - it, 8, 0, item(kcal = 1_500)) }

        val report = report(twoMealDays(10) + today_ + older)

        assertThat(report.recent.from).isEqualTo(today - MeasuredHabits.SPAN_DAYS)
        assertThat(report.recent.to).isEqualTo(today - 1)
        assertThat(report.recent.stated!!.averages.kcal).isEqualTo(1_000)
        assertThat(report.before.to).isEqualTo(today - MeasuredHabits.SPAN_DAYS - 1)
        assertThat(report.before.from).isEqualTo(MeasuredHabits.firstDayRead(today))
        assertThat(report.before.stated!!.averages.kcal).isEqualTo(1_500)
    }

    /** Its KDoc forbids counting it. */
    @Test
    fun `whether a saved meal was adjusted changes nothing`() {
        val days = twoMealDays(10)

        assertThat(report(days.map { it.copy(savedMealAdjusted = true) })).isEqualTo(report(days))
    }

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
        const val MINUTE = 60_000L
    }
}
