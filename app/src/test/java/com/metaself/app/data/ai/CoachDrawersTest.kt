package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.Drawer
import com.metaself.app.domain.trainer.DrawerAsk
import com.metaself.app.domain.trainer.HabitCoverage
import com.metaself.app.domain.trainer.HabitSpan
import com.metaself.app.domain.trainer.HabitsReport
import com.metaself.app.domain.trainer.OpenedDrawer
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.SessionFacts
import com.metaself.app.domain.trainer.TalkPlan
import com.metaself.app.domain.trainer.ThreadItem
import com.metaself.app.domain.trainer.TrainerMessage
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.domain.weight.MeasuredRate
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.domain.weight.WeightTrend
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** D161: what each drawer answers, with today at [TEST_EPOCH_DAY] in UTC. Every figure and word is invented. */
class CoachDrawersTest {

    private val today = TEST_EPOCH_DAY
    private val zone = ZoneOffset.UTC

    private fun date(day: Long) = LocalDate.ofEpochDay(day).toString()

    private fun ask(drawer: String, from: Long, to: Long, words: String = "") = DrawerAsk(drawer, date(from), date(to), words)

    private fun good(from: Long, to: Long = today) = CoachDrawers.range(ask("days", from, to), today) as CoachDrawers.Range.Good

    private fun json(opened: OpenedDrawer): JsonObject = Json.parseToJsonElement(opened.answer).jsonObject

    private fun states(opened: OpenedDrawer, rows: String = "days") =
        json(opened).getValue(rows).jsonArray.map { it.jsonObject.getValue("state").jsonPrimitive.content }

    @Test
    fun `a range is the dates asked, ended at today, and shortened to its newest days`() {
        assertThat(CoachDrawers.range(ask("days", today - 3, today - 1), today)).isEqualTo(CoachDrawers.Range.Good(today - 3, today - 1, false))
        assertThat(CoachDrawers.range(ask("days", today - 3, today + 9), today)).isEqualTo(CoachDrawers.Range.Good(today - 3, today, false))
        assertThat(CoachDrawers.range(ask("days", today - 500, today), today))
            .isEqualTo(CoachDrawers.Range.Good(today - CoachDrawers.MAX_RANGE_DAYS + 1, today, true))
    }

    @Test
    fun `bad dates are said in words`() {
        listOf(
            DrawerAsk("days", "yesterday", date(today), ""),
            DrawerAsk("days", date(today), "", ""),
            ask("days", today - 1, today - 3),
            ask("days", today + 1, today + 3),
        ).forEach { assertThat(CoachDrawers.range(it, today)).isInstanceOf(CoachDrawers.Range.Bad::class.java) }
    }

    @Test
    fun `an unknown drawer names the ones there are, and is not read`() {
        val opened = CoachDrawers.unknown(DrawerAsk("astrology", "", "", ""))

        assertThat(json(opened).getValue("error").jsonPrimitive.content).contains("sessions, days, food_days, meals, habits, weight, plan, conversations")
        assertThat(opened.read).isNull()
    }

    @Test
    fun `days tell apart not logged, logged as zero and before the record began`() {
        val days = listOf(
            HealthDay(today, steps = 0),
            HealthDay(today - 1, steps = 4_000, sleepMinutes = 420, restingHeartRate = 60, hrvMs = 50.0),
            HealthDay(today - 3),
        )

        val opened = CoachDrawers.days(ask("days", today - 5, today), good(today - 5), today - 3, days)

        assertThat(states(opened)).containsExactly("not_logged", "not_logged", "logged", "logged_as_zero").inOrder()
        val before = json(opened).getValue("before_record").jsonObject
        assertThat(before.getValue("days").jsonPrimitive.content).isEqualTo("2")
        assertThat(before.getValue("to").jsonPrimitive.content).isEqualTo(date(today - 4))
        assertThat(opened.read!!.drawer).isEqualTo(Drawer.DAYS)
        assertThat(opened.read!!.days).containsExactly(today - 3, today - 2, today - 1, today)
    }

    @Test
    fun `a day's answer holds sleep, resting heart rate, variability and steps, a missing one null`() {
        val row = json(CoachDrawers.days(ask("days", today, today), good(today), today - 1, listOf(HealthDay(today, sleepMinutes = 420))))
            .getValue("days").jsonArray.single().jsonObject

        assertThat(row.keys).containsExactly("date", "state", "sleep_minutes", "resting_heart_rate", "hrv_ms", "steps")
        assertThat(row.getValue("sleep_minutes").jsonPrimitive.content).isEqualTo("420")
        assertThat(row.getValue("steps")).isEqualTo(JsonNull)
    }

    @Test
    fun `a record with nothing in it says the whole range is before it, and nothing was read`() {
        val opened = CoachDrawers.days(ask("days", today - 2, today), good(today - 2), null, emptyList())

        assertThat(json(opened).getValue("before_record").jsonObject.getValue("days").jsonPrimitive.content).isEqualTo("3")
        assertThat(opened.read).isNull()
    }

    @Test
    fun `a range wholly before the record reads nothing`() {
        val opened = CoachDrawers.days(ask("days", today - 9, today - 5), good(today - 9, today - 5), today - 1, emptyList())

        assertThat(opened.read).isNull()
        assertThat(json(opened).getValue("days").jsonArray).isEmpty()
    }

    @Test
    fun `food days are read against today's target, and today is marked so far`() {
        val totals = mapOf(today to DayTotals(500, 20, 50, 10), today - 1 to DayTotals.NOTHING, today - 3 to DayTotals(2_400, 90, 200, 80))

        val opened = CoachDrawers.foodDays(ask("food_days", today - 3, today), good(today - 3), today - 9, today, totals, 2_000)

        assertThat(states(opened)).containsExactly("logged", "not_logged", "logged_as_zero", "logged").inOrder()
        val rows = json(opened).getValue("days").jsonArray.map { it.jsonObject }
        assertThat(rows.first().getValue("kcal_against_target").jsonPrimitive.content).isEqualTo("400")
        assertThat(rows.last().getValue("so_far_today").jsonPrimitive.content).isEqualTo("true")
        assertThat(rows.first().containsKey("so_far_today")).isFalse()
        assertThat(rows[1].containsKey("kcal")).isFalse()
    }

    private fun meal(day: Long, hour: Int, vararg items: FoodItem, loggedOn: Long = day) =
        Meal(epochDay = day, loggedAtMillis = loggedOn * DAY + hour * HOUR, items = items.toList())

    private val porridge = FoodItem(
        name = "typed porridge", portion = "1 bowl", kcal = 300, proteinG = 10, carbsG = 50, fatG = 5,
        source = Source.AI_ESTIMATE, confidence = Confidence.MEDIUM, foodId = 3, currentName = "Invented porridge",
    )

    @Test
    fun `meals give time, part of the clock, name, portion, figures and source`() {
        val opened = CoachDrawers.meals(ask("meals", today, today), good(today), today - 9, listOf(meal(today, 8, porridge)), zone)

        val logging = json(opened).getValue("days").jsonArray.single().jsonObject.getValue("loggings").jsonArray.single().jsonObject
        assertThat(logging.getValue("time").jsonPrimitive.content).isEqualTo("08:00")
        assertThat(logging.getValue("part_of_the_clock").jsonPrimitive.content).isEqualTo("morning")
        val item = logging.getValue("items").jsonArray.single().jsonObject
        assertThat(item.keys).containsExactly("name", "portion", "kcal", "protein_g", "carbs_g", "fat_g", "source")
        assertThat(item.getValue("name").jsonPrimitive.content).isEqualTo("Invented porridge")
        assertThat(item.getValue("portion").jsonPrimitive.content).isEqualTo("1 bowl")
        assertThat(item.getValue("source").jsonPrimitive.content).isEqualTo("estimate")
    }

    /** D33. */
    @Test
    fun `a logging written down on another day says it has no honest time`() {
        val opened = CoachDrawers.meals(ask("meals", today - 2, today - 2), good(today - 2, today - 2), today - 9, listOf(meal(today - 2, 22, porridge, loggedOn = today)), zone)

        val logging = json(opened).getValue("days").jsonArray.single().jsonObject.getValue("loggings").jsonArray.single().jsonObject
        assertThat(logging.getValue("no_honest_time").jsonPrimitive.content).isEqualTo("true")
        assertThat(logging.getValue("time")).isEqualTo(JsonNull)
        assertThat(logging.containsKey("part_of_the_clock")).isFalse()
    }

    /** D51: no name for a meal anywhere, and a saved meal's own name, which could be anything, does not go either. */
    @Test
    fun `nothing is called breakfast, and a saved meal's name stays home`() {
        val built = meal(today, 8, porridge).copy(savedMealName = "Invented morning box", savedMealAdjusted = true, note = "Invented note")

        val answer = CoachDrawers.meals(ask("meals", today, today), good(today), today - 9, listOf(built), zone).answer

        listOf("breakfast", "lunch", "dinner", "Invented morning box", "Invented note", "adjusted").forEach { assertThat(answer.lowercase()).doesNotContain(it.lowercase()) }
    }

    @Test
    fun `meals tell not logged from zero, and a long answer keeps its newest days and says so`() {
        val zero = meal(today, 8, porridge.copy(kcal = 0, proteinG = 0, carbsG = 0, fatG = 0))
        assertThat(states(CoachDrawers.meals(ask("meals", today - 1, today), good(today - 1), today - 9, listOf(zero), zone)))
            .containsExactly("not_logged", "logged_as_zero").inOrder()

        val long = porridge.copy(name = "x".repeat(400), currentName = null)
        val meals = (0L until 80L).map { meal(today - it, 8, long, long) }
        val opened = CoachDrawers.meals(ask("meals", today - 79, today), good(today - 79), today - 200, meals, zone)

        assertThat(opened.answer.length).isAtMost(CoachDrawers.MAX_MEALS_CHARS + 500)
        val answer = json(opened)
        assertThat(answer.getValue("truncated").jsonPrimitive.content).isEqualTo("true")
        val rows = answer.getValue("days").jsonArray
        assertThat(rows.last().jsonObject.getValue("date").jsonPrimitive.content).isEqualTo(date(today))
        assertThat(answer.getValue("truncated_covers_from").jsonPrimitive.content).isEqualTo(rows.first().jsonObject.getValue("date").jsonPrimitive.content)
        // The Read line counts what went, not what was asked.
        assertThat(opened.read!!.days).hasSize(rows.size)
    }

    private fun session(day: Long, words: String? = null) = SessionFacts(
        day, WorkoutKind.RUN, 30, null, null, null, null, null, null, null, true, null, null, null, words, null,
    )

    @Test
    fun `sessions go as a request sends them, in the range, and count the days without one`() {
        val opened = CoachDrawers.sessions(
            ask("sessions", today - 2, today), good(today - 2), today - 9,
            listOf(session(today - 5), session(today - 1, words = "Invented words.")),
        )

        val answer = json(opened)
        val row = answer.getValue("sessions").jsonArray.single().jsonObject
        assertThat(row.getValue("words").jsonPrimitive.content).isEqualTo("Invented words.")
        assertThat(answer.getValue("days_with_no_session").jsonPrimitive.content).isEqualTo("2")
        assertThat(opened.read!!.days).hasSize(3)
    }

    @Test
    fun `weight gives the trend, its rate and each reading, and counts the days not weighed`() {
        val readings = listOf(WeightReading(today - 40, 80.0), WeightReading(today - 2, 79.0), WeightReading(today, 78.0))
        val trend = WeightTrend.of(readings)
        val rate = MeasuredRate(14, -0.5, -0.25, today)

        val answer = json(CoachDrawers.weight(ask("weight", today - 3, today), good(today - 3), today - 40, trend, rate))

        assertThat(answer.getValue("readings").jsonArray.map { it.jsonObject.getValue("kg").jsonPrimitive.content }).containsExactly("79.0", "78.0").inOrder()
        assertThat(answer.getValue("days_not_weighed").jsonPrimitive.content).isEqualTo("2")
        assertThat(answer.getValue("change_kg_a_week").jsonPrimitive.content).isEqualTo("-0.25")
        assertThat(answer.getValue("before_record")).isEqualTo(JsonNull)
    }

    @Test
    fun `the plan drawer says when none runs, and is read`() {
        val opened = CoachDrawers.plan(DrawerAsk("plan", "", "", ""), null, emptyList(), today)

        assertThat(json(opened).getValue("note").jsonPrimitive.content).isEqualTo("no plan is running")
        assertThat(opened.read!!.days).isNull()
    }

    @Test
    fun `the plan drawer holds the whole plan`() {
        val plan = TalkPlan(
            ProgrammeAsk(2, 2),
            today - 7,
            WeeksPlan("Invented plan", listOf(PlanWeek("w", listOf(PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented.")))), "Invented."),
            weekIndex = 0,
        )

        val answer = json(CoachDrawers.plan(DrawerAsk("plan", "", "", ""), plan, emptyList(), today))

        assertThat(answer.getValue("plan").jsonObject.getValue("plan").jsonObject.getValue("title").jsonPrimitive.content).isEqualTo("Invented plan")
        assertThat(answer.getValue("how_each_week_went").jsonArray).isEmpty()
    }

    private fun said(id: Long, at: Long, text: String) =
        ThreadItem.Said(TrainerMessage(id, at, id % 2 == 1L, text, null, true, conversation = "talk:$id"))

    @Test
    fun `a search finds every word, whatever its age or case, the newest first, and not the message being answered`() {
        val thread = listOf(
            said(1, 1_000, "Invented KNEE ache on the left."),
            said(2, 2_000, "Nothing to see."),
            said(3, 3_000, "The left knee again."),
            said(5, 4_000, "left knee, this very message"),
        )

        val opened = CoachDrawers.conversations(DrawerAsk("conversations", "", "", "knee left"), "knee left", thread, 5, zone)

        val answer = json(opened)
        assertThat(answer.getValue("found").jsonPrimitive.content).isEqualTo("2")
        assertThat(answer.getValue("matches").jsonArray.map { it.jsonObject.getValue("text").jsonPrimitive.content })
            .containsExactly("The left knee again.", "Invented KNEE ache on the left.").inOrder()
        assertThat(opened.read!!.drawer).isEqualTo(Drawer.CONVERSATIONS)
    }

    @Test
    fun `a search with no words says so, and a long one is cut and says so`() {
        assertThat(CoachDrawers.conversations(DrawerAsk("conversations", "", "", " "), " ", emptyList(), null, zone).read).isNull()

        val thread = (1L..40L).map { said(it * 2, it * 1_000, "needle " + "x".repeat(CoachDrawers.MAX_MATCH_CHARS + 100)) }
        val answer = json(CoachDrawers.conversations(DrawerAsk("conversations", "", "", "needle"), "needle", thread, null, zone))

        assertThat(answer.getValue("truncated").jsonPrimitive.content).isEqualTo("true")
        assertThat(answer.getValue("found").jsonPrimitive.content).isEqualTo("40")
        val first = answer.getValue("matches").jsonArray.first().jsonObject
        assertThat(first.getValue("text_cut").jsonPrimitive.content).isEqualTo("true")
        assertThat(first.getValue("text").jsonPrimitive.content).hasLength(CoachDrawers.MAX_MATCH_CHARS)
        assertThat(answer.toString().length).isAtMost(CoachDrawers.MAX_CONVERSATIONS_CHARS + 500)
    }

    @Test
    fun `habits with too few days state their coverage and say nothing else`() {
        val coverage = HabitCoverage(28, 0, 2, 26, 0, 0, 0)
        val span = HabitSpan(today - 28, today - 1, coverage, null)

        val opened = CoachDrawers.habits(DrawerAsk("habits", "", "", ""), HabitsReport(span, span, 2_000, 150, 200, 60, 80.0))

        val recent = json(opened).getValue("last_4_weeks").jsonObject
        assertThat(recent.getValue("coverage").jsonObject.getValue("logged").jsonPrimitive.content).isEqualTo("2")
        assertThat(recent.getValue("note").jsonPrimitive.content).contains("too few logged days")
        assertThat(recent.containsKey("timing")).isFalse()
        assertThat(opened.read!!.drawer).isEqualTo(Drawer.HABITS)
    }

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
    }
}
