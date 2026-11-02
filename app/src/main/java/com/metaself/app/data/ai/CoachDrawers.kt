package com.metaself.app.data.ai

import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.PartOfTheClock
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.trainer.Conversation
import com.metaself.app.domain.trainer.Drawer
import com.metaself.app.domain.trainer.DrawerAsk
import com.metaself.app.domain.trainer.DrawerRead
import com.metaself.app.domain.trainer.HabitAverages
import com.metaself.app.domain.trainer.HabitClock
import com.metaself.app.domain.trainer.HabitSpan
import com.metaself.app.domain.trainer.HabitsReport
import com.metaself.app.domain.trainer.LineKind
import com.metaself.app.domain.trainer.MeasuredHabits
import com.metaself.app.domain.trainer.OpenedDrawer
import com.metaself.app.domain.trainer.SessionFacts
import com.metaself.app.domain.trainer.TalkPlan
import com.metaself.app.domain.trainer.ThreadItem
import com.metaself.app.domain.trainer.WeekOutcome
import com.metaself.app.domain.weight.MeasuredRate
import com.metaself.app.domain.weight.TrendPoint
import com.metaself.app.domain.window.hasItsOwnTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * What each drawer answers (D161), pure. Every day-based answer tells apart *not logged*, *logged as zero* and *before
 * the record began*, each is cut to a chosen size and says so, and a bad ask is answered in words rather than failing.
 * Nothing here is read by the phone from the AI's words but the drawer's name, its dates and its search.
 */
object CoachDrawers {

    /** The most days one ask reads; a longer range keeps its newest days. A chosen size: about a quarter of a year. */
    const val MAX_RANGE_DAYS = 92

    /** How many drawers one look may open. A chosen size. */
    const val MAX_OPENS_A_LOOK = 4

    /** All the drawers of one reply together: past this, no more are read. A chosen size. */
    const val MAX_OPENED_CHARS = 60_000

    /** The characters each drawer's answer holds. Chosen sizes. */
    const val MAX_SESSIONS_CHARS = 16_000
    const val MAX_DAYS_CHARS = 10_000
    const val MAX_FOOD_DAYS_CHARS = 10_000
    const val MAX_MEALS_CHARS = 20_000
    const val MAX_HABITS_CHARS = 8_000
    const val MAX_WEIGHT_CHARS = 8_000
    const val MAX_PLAN_CHARS = 16_000
    const val MAX_CONVERSATIONS_CHARS = 16_000

    /** The words of one match in a conversations search; longer is cut and says so. A chosen size. */
    const val MAX_MATCH_CHARS = 1_500

    /** The range an ask comes to, or the sentence saying why it reads nothing. */
    sealed interface Range {
        data class Good(val from: Long, val to: Long, val shortened: Boolean) : Range

        data class Bad(val why: String) : Range
    }

    /** One row of a day-based answer, by the day it belongs to, oldest first. */
    private data class Row(val epochDay: Long, val json: JsonObject)

    fun range(ask: DrawerAsk, today: Long): Range {
        val from = dateOf(ask.from) ?: return Range.Bad("from is not a date: give YYYY-MM-DD")
        val to = dateOf(ask.to) ?: return Range.Bad("to is not a date: give YYYY-MM-DD")
        if (from > to) return Range.Bad("from is after to")
        if (from > today) return Range.Bad("the range is all in the future: today is ${TrainerPrompt.date(today)}")
        val end = minOf(to, today)
        val start = maxOf(from, end - MAX_RANGE_DAYS + 1)
        return Range.Good(start, end, shortened = start != from)
    }

    private fun dateOf(text: String): Long? = try {
        LocalDate.parse(text).toEpochDay()
    } catch (bad: DateTimeParseException) {
        null
    }

    /** A drawer that was asked for and not read, and why: said to the model, never a failure, never on the Read line. */
    fun refused(ask: DrawerAsk, why: String): OpenedDrawer =
        OpenedDrawer(ask, buildJsonObject { put("error", why) }.toString(), read = null)

    fun unknown(ask: DrawerAsk): OpenedDrawer =
        refused(ask, "there is no drawer called \"${ask.drawer}\". The drawers are: " + Drawer.entries.joinToString { it.wire })

    /** The newest rows that fit [max] characters, oldest first, and whether any was left out. */
    private fun capped(rows: List<Row>, max: Int): Pair<List<Row>, Boolean> {
        val kept = ArrayDeque<Row>()
        var chars = 0
        for (row in rows.asReversed()) {
            val length = row.json.toString().length
            if (chars + length > max) break
            chars += length
            kept.addFirst(row)
        }
        return kept.toList() to (kept.size < rows.size)
    }

    /**
     * The answer of a day-based drawer, cut to [max]: [make] has the days the record covers, and [rows] holds them. The
     * days before [begins] are one line, because each is the same fact. [extras] may add to the answer once the rows
     * are known. What the Read line counts is what was left after every cut.
     */
    private fun byDays(
        ask: DrawerAsk,
        drawer: Drawer,
        range: Range.Good,
        begins: Long?,
        max: Int,
        rowsName: String,
        make: (LongRange) -> List<Row>,
        extras: (LongRange) -> List<Pair<String, JsonElement>> = { emptyList() },
    ): OpenedDrawer {
        val first = if (begins == null) range.to + 1 else maxOf(range.from, begins)
        val covered = first..range.to
        val (rows, truncated) = capped(make(covered), max)
        val readFrom = if (truncated) rows.firstOrNull()?.epochDay ?: (range.to + 1) else first
        val days = (readFrom..range.to).toSet()
        val answer = buildJsonObject {
            put("from", TrainerPrompt.date(range.from))
            put("to", TrainerPrompt.date(range.to))
            put("shortened_to_days", if (range.shortened) JsonPrimitive(MAX_RANGE_DAYS) else JsonNull)
            put("record_begins", begins?.let { JsonPrimitive(TrainerPrompt.date(it)) } ?: JsonNull)
            put("before_record", beforeRecord(range, first))
            put("truncated", truncated)
            put("truncated_covers_from", if (truncated) JsonPrimitive(TrainerPrompt.date(readFrom)) else JsonNull)
            extras(readFrom..range.to).forEach { (name, value) -> put(name, value) }
            putJsonArray(rowsName) { rows.forEach { add(it.json) } }
        }
        return OpenedDrawer(ask, answer.toString(), if (days.isEmpty()) null else DrawerRead(drawer, days))
    }

    private fun beforeRecord(range: Range.Good, first: Long): JsonElement {
        val last = minOf(first - 1, range.to)
        if (last < range.from) return JsonNull
        return buildJsonObject {
            put("from", TrainerPrompt.date(range.from))
            put("to", TrainerPrompt.date(last))
            put("days", last - range.from + 1)
        }
    }

    /** `sessions`: the sessions in the range, as sent today (D84), with how each felt and the owner's words. */
    fun sessions(ask: DrawerAsk, range: Range.Good, begins: Long?, sessions: List<SessionFacts>): OpenedDrawer =
        byDays(
            ask, Drawer.SESSIONS, range, begins, MAX_SESSIONS_CHARS, "sessions",
            make = { days -> sessions.filter { it.epochDay in days }.sortedBy { it.epochDay }.map { Row(it.epochDay, TrainerPrompt.sessionJson(it)) } },
            extras = { days ->
                val held = sessions.filter { it.epochDay in days }.map { it.epochDay }.toSet()
                listOf("days_with_no_session" to JsonPrimitive(days.count { it !in held }))
            },
        )

    /** `days`: sleep, resting heart rate, heart-rate variability and steps. A figure that is null was not recorded. */
    fun days(ask: DrawerAsk, range: Range.Good, begins: Long?, days: List<HealthDay>): OpenedDrawer {
        val byDay = days.associateBy { it.epochDay }
        return byDays(ask, Drawer.DAYS, range, begins, MAX_DAYS_CHARS, "days", make = { covered ->
            covered.map { day ->
                val health = byDay[day]
                val figures = listOf(health?.sleepMinutes?.toDouble(), health?.restingHeartRate?.toDouble(), health?.hrvMs, health?.steps?.toDouble()).filterNotNull()
                Row(
                    day,
                    buildJsonObject {
                        put("date", TrainerPrompt.date(day))
                        put(
                            "state",
                            when {
                                figures.isEmpty() -> NOT_LOGGED
                                figures.all { it == 0.0 } -> LOGGED_AS_ZERO
                                else -> LOGGED
                            },
                        )
                        if (figures.isNotEmpty()) {
                            put("sleep_minutes", health?.sleepMinutes?.let(::JsonPrimitive) ?: JsonNull)
                            put("resting_heart_rate", health?.restingHeartRate?.let(::JsonPrimitive) ?: JsonNull)
                            put("hrv_ms", health?.hrvMs?.let(::JsonPrimitive) ?: JsonNull)
                            put("steps", health?.steps?.let(::JsonPrimitive) ?: JsonNull)
                        }
                    },
                )
            }
        })
    }

    /** `food_days`: each day's totals against today's target, which is the only target the record holds. */
    fun foodDays(ask: DrawerAsk, range: Range.Good, begins: Long?, today: Long, totals: Map<Long, DayTotals>, targetKcal: Int?): OpenedDrawer =
        byDays(
            ask, Drawer.FOOD_DAYS, range, begins, MAX_FOOD_DAYS_CHARS, "days",
            make = { covered ->
                covered.map { day ->
                    val total = totals[day]
                    Row(
                        day,
                        buildJsonObject {
                            put("date", TrainerPrompt.date(day))
                            put(
                                "state",
                                when {
                                    total == null -> NOT_LOGGED
                                    total.kcal == 0 -> LOGGED_AS_ZERO
                                    else -> LOGGED
                                },
                            )
                            if (total != null) {
                                put("kcal", total.kcal)
                                put("protein_g", total.proteinG)
                                put("carbs_g", total.carbsG)
                                put("fat_g", total.fatG)
                                put("kcal_against_target", targetKcal?.let { JsonPrimitive(total.kcal - it) } ?: JsonNull)
                            }
                            if (day == today) put("so_far_today", true)
                        },
                    )
                }
            },
            extras = { listOf("target_kcal_today" to (targetKcal?.let(::JsonPrimitive) ?: JsonNull)) },
        )

    /**
     * `meals`: each logging and what it held. A part of the clock (D51) and never a meal's name; a logging with no
     * honest time (D33) says so instead of a time. `Meal.savedMealAdjusted` is not read, nor is `Meal.note`.
     */
    fun meals(ask: DrawerAsk, range: Range.Good, begins: Long?, meals: List<Meal>, zone: ZoneId): OpenedDrawer {
        val byDay = meals.groupBy { it.epochDay }
        return byDays(ask, Drawer.MEALS, range, begins, MAX_MEALS_CHARS, "days", make = { covered ->
            covered.map { day ->
                val held = byDay[day].orEmpty()
                Row(
                    day,
                    buildJsonObject {
                        put("date", TrainerPrompt.date(day))
                        put(
                            "state",
                            when {
                                held.isEmpty() -> NOT_LOGGED
                                held.all { meal -> meal.items.all { it.kcal == 0 } } -> LOGGED_AS_ZERO
                                else -> LOGGED
                            },
                        )
                        if (held.isNotEmpty()) putJsonArray("loggings") { held.sortedBy { it.loggedAtMillis }.forEach { add(logging(it, zone)) } }
                    },
                )
            }
        })
    }

    private fun logging(meal: Meal, zone: ZoneId): JsonObject = buildJsonObject {
        if (hasItsOwnTime(meal, zone)) {
            val at = Instant.ofEpochMilli(meal.loggedAtMillis).atZone(zone)
            put("time", "%02d:%02d".format(at.hour, at.minute))
            put("part_of_the_clock", PartOfTheClock.entries.single { at.hour in it.firstHour..it.lastHour }.name.lowercase())
        } else {
            put("time", JsonNull)
            put("no_honest_time", true)
        }
        putJsonArray("items") { meal.items.forEach { add(item(it)) } }
    }

    private fun item(item: FoodItem): JsonObject = buildJsonObject {
        put("name", item.label)
        put("portion", item.portion?.let(::JsonPrimitive) ?: JsonNull)
        put("kcal", item.kcal)
        put("protein_g", item.proteinG)
        put("carbs_g", item.carbsG)
        put("fat_g", item.fatG)
        put("source", source(item))
    }

    /** D4: a repeat that carries a confidence repeats an estimate, and is one. */
    private fun source(item: FoodItem): String = when {
        item.source == Source.AI_ESTIMATE || item.confidence != null -> "estimate"
        item.source == Source.TYPED -> "typed"
        item.source == Source.LABEL -> "label"
        item.source == Source.REPEATED -> "repeated"
        else -> "unknown"
    }

    /** `weight`: the trend now, its weekly change, and each reading in the range; a day with no reading was not weighed. */
    fun weight(ask: DrawerAsk, range: Range.Good, begins: Long?, trend: List<TrendPoint>, rate: MeasuredRate?): OpenedDrawer =
        byDays(
            ask, Drawer.WEIGHT, range, begins, MAX_WEIGHT_CHARS, "readings",
            make = { covered ->
                trend.filter { it.reading.epochDay in covered }.map { point ->
                    Row(
                        point.reading.epochDay,
                        buildJsonObject {
                            put("date", TrainerPrompt.date(point.reading.epochDay))
                            put("kg", point.reading.kg)
                            put("trend_kg", round1(point.trendKg))
                        },
                    )
                }
            },
            extras = { days ->
                val held = trend.map { it.reading.epochDay }.toSet()
                listOf(
                    "days_not_weighed" to JsonPrimitive(days.count { it !in held }),
                    "latest_trend_kg" to (trend.lastOrNull()?.let { JsonPrimitive(round1(it.trendKg)) } ?: JsonNull),
                    "change_kg_a_week" to (rate?.let { JsonPrimitive(round2(it.kgPerWeek)) } ?: JsonNull),
                    "measured_over_days" to (rate?.let { JsonPrimitive(it.spanDays) } ?: JsonNull),
                )
            },
        )

    /** `plan`: the whole running plan, and how each week went; the oldest weeks of that go first when it is too long. */
    fun plan(ask: DrawerAsk, plan: TalkPlan?, howItWent: List<WeekOutcome>, today: Long): OpenedDrawer {
        if (plan == null) return OpenedDrawer(ask, buildJsonObject { put("plan", JsonNull); put("note", "no plan is running") }.toString(), DrawerRead(Drawer.PLAN))
        val whole = TrainerPrompt.talkPlanJson(plan, today).toString()
        val room = MAX_PLAN_CHARS - whole.length
        if (room < 0) {
            return OpenedDrawer(ask, buildJsonObject { put("truncated", true); put("note", "the plan is too long to send whole; ask them, or use the front page's") }.toString(), null)
        }
        val (kept, truncated) = capped(howItWent.mapIndexed { index, week -> Row(index.toLong(), TrainerPrompt.weekOutcome(week)) }, room)
        val answer = buildJsonObject {
            put("plan", TrainerPrompt.talkPlanJson(plan, today))
            put("truncated", truncated)
            putJsonArray("how_each_week_went") { kept.forEach { add(it.json) } }
        }
        return OpenedDrawer(ask, answer.toString(), DrawerRead(Drawer.PLAN))
    }

    /**
     * `conversations`: every word of [words] found in an item, over [thread] whatever its age, the newest first, cut to
     * [MAX_CONVERSATIONS_CHARS]. [exceptMessage] is the message being answered, which would always match itself.
     */
    fun conversations(ask: DrawerAsk, words: String, thread: List<ThreadItem>, exceptMessage: Long?, zone: ZoneId): OpenedDrawer {
        val sought = words.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (sought.isEmpty()) return refused(ask, "say what to search for, in words")
        val found = thread.asReversed()
            .filterNot { it is ThreadItem.Said && it.message.id == exceptMessage }
            .filter { item -> Conversation.text(item).lowercase().let { text -> sought.all { it in text } } }
        var chars = 0
        var truncated = false
        val kept = mutableListOf<JsonObject>()
        for (item in found) {
            val line = Conversation.line(item, zone)
            val cut = line.text.length > MAX_MATCH_CHARS
            val match = buildJsonObject {
                put("at", TrainerPrompt.at(line.at))
                put("from", if (line.fromOwner) "them" else "you")
                put(
                    "kind",
                    when (line.kind) {
                        LineKind.MESSAGE -> "message"
                        LineKind.SESSION_FEEDBACK -> "session_feedback"
                        LineKind.LETTER -> "letter"
                    },
                )
                put("text", line.text.take(MAX_MATCH_CHARS))
                put("text_cut", cut)
            }
            val length = match.toString().length
            if (chars + length > MAX_CONVERSATIONS_CHARS) {
                truncated = true
                break
            }
            chars += length
            kept += match
        }
        val answer = buildJsonObject {
            put("searched_for", words)
            put("found", found.size)
            put("truncated", truncated)
            putJsonArray("matches") { kept.forEach { add(it) } }
        }
        return OpenedDrawer(ask, answer.toString(), DrawerRead(Drawer.CONVERSATIONS))
    }

    /** `habits` (D162): the phone's figures, with no opinion on them. Older span first to go, should it not fit. */
    fun habits(ask: DrawerAsk, report: HabitsReport): OpenedDrawer {
        fun answer(withBefore: Boolean) = buildJsonObject {
            put("target_kcal_today", report.targetKcal?.let(::JsonPrimitive) ?: JsonNull)
            put("target_protein_g", report.targetProteinG?.let(::JsonPrimitive) ?: JsonNull)
            put("target_carbs_g", report.targetCarbsG?.let(::JsonPrimitive) ?: JsonNull)
            put("target_fat_g", report.targetFatG?.let(::JsonPrimitive) ?: JsonNull)
            put("latest_trend_kg", report.trendKg?.let { JsonPrimitive(round1(it)) } ?: JsonNull)
            put("minimum_logged_days", MeasuredHabits.MIN_LOGGED_DAYS)
            put("truncated", !withBefore)
            put("last_4_weeks", span(report.recent))
            if (withBefore) put("the_4_weeks_before", span(report.before))
        }.toString()
        val whole = answer(withBefore = true)
        return OpenedDrawer(ask, if (whole.length <= MAX_HABITS_CHARS) whole else answer(withBefore = false), DrawerRead(Drawer.HABITS))
    }

    private fun span(span: HabitSpan): JsonObject = buildJsonObject {
        put("from", TrainerPrompt.date(span.from))
        put("to", TrainerPrompt.date(span.to))
        with(span.coverage) {
            put(
                "coverage",
                buildJsonObject {
                    put("days", days)
                    put("before_record", beforeRecord)
                    put("logged", logged)
                    put("not_logged", notLogged)
                    put("logged_as_zero", loggedAsZero)
                    put("logged_under_half_of_target", underHalfTarget)
                    put("loggings_with_no_honest_time", untimedLoggings)
                },
            )
        }
        val stated = span.stated
        if (stated == null) {
            put("note", "too few logged days: no habit is stated")
            return@buildJsonObject
        }
        put("averages_on_days_with_food", averages(stated.averages))
        put("kcal_against_target", stated.kcalAgainstTarget?.let(::JsonPrimitive) ?: JsonNull)
        put("days_over_target", stated.daysOverTarget?.let(::JsonPrimitive) ?: JsonNull)
        put("protein_per_kg", stated.proteinPerKg?.let(::JsonPrimitive) ?: JsonNull)
        put(
            "timing",
            stated.timing?.let { timing ->
                buildJsonObject {
                    put("days", timing.days)
                    put("first_eating", clock(timing.firstEating))
                    put("last_eating", clock(timing.lastEating))
                    put("eating_hours", timing.eatingHours)
                    put("eating_hours_spread", timing.eatingHoursSpread)
                    put("fasting_hours", timing.fastingHours?.let(::JsonPrimitive) ?: JsonNull)
                    put("fasting_hours_spread", timing.fastingHoursSpread?.let(::JsonPrimitive) ?: JsonNull)
                    put("fasts_measured", timing.fastsMeasured)
                    put("late_day_share_of_kcal", timing.lateShare)
                    put("eating_occasions_a_day", timing.occasionsADay)
                }
            } ?: JsonNull,
        )
        put("weekdays", stated.weekday?.let(::averages) ?: JsonNull)
        put("weekends", stated.weekend?.let(::averages) ?: JsonNull)
        with(stated.foods) {
            put(
                "foods",
                buildJsonObject {
                    putJsonArray("most_frequent") {
                        mostFrequent.forEach { food ->
                            add(
                                buildJsonObject {
                                    put("name", food.name)
                                    put("times", food.times)
                                    put("days", food.days)
                                    put("share_of_kcal", food.kcalShare)
                                    put("estimated_share", food.estimatedShare)
                                },
                            )
                        }
                    }
                    putJsonArray("eaten_together") {
                        combinations.forEach { pair ->
                            add(
                                buildJsonObject {
                                    put("foods", "${pair.first} + ${pair.second}")
                                    put("times", pair.times)
                                },
                            )
                        }
                    }
                    put("different_foods", variety)
                    put("share_of_kcal_from_top_${MeasuredHabits.TOP_FEW_FOODS}", topFewShare)
                },
            )
        }
        put("training_days", stated.trainingDays?.let(::averages) ?: JsonNull)
        put("rest_days", stated.restDays?.let(::averages) ?: JsonNull)
        put(
            "around_a_session",
            stated.aroundSession?.let { around ->
                buildJsonObject {
                    put("sessions", around.sessions)
                    put("hours_either_side", around.hours)
                    put("before_kcal", around.beforeKcal)
                    put("before_protein_g", around.beforeProteinG)
                    put("after_kcal", around.afterKcal)
                    put("after_protein_g", around.afterProteinG)
                    put("estimated_share", around.estimatedShare)
                }
            } ?: JsonNull,
        )
        with(stated.sources) {
            put(
                "where_the_kcal_came_from",
                buildJsonObject {
                    put("label", label)
                    put("typed", typed)
                    put("estimate", estimate)
                    put("repeated_of_unknown_origin", repeated)
                    put("unknown", other)
                },
            )
        }
    }

    private fun averages(averages: HabitAverages): JsonObject = buildJsonObject {
        put("days", averages.days)
        put("kcal", averages.kcal)
        put("protein_g", averages.proteinG)
        put("carbs_g", averages.carbsG)
        put("fat_g", averages.fatG)
        put("estimated_share", averages.estimatedShare)
    }

    private fun clock(clock: HabitClock): JsonObject = buildJsonObject {
        put("time", "%02d:%02d".format(clock.minutes / 60, clock.minutes % 60))
        put("spread_minutes", clock.spreadMinutes)
    }

    private fun round1(value: Double): Double = Math.round(value * 10) / 10.0

    private fun round2(value: Double): Double = Math.round(value * 100) / 100.0

    private const val NOT_LOGGED = "not_logged"
    private const val LOGGED_AS_ZERO = "logged_as_zero"
    private const val LOGGED = "logged"
}
