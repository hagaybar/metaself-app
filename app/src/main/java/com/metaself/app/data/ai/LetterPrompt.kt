package com.metaself.app.data.ai

import com.metaself.app.domain.letter.LetterDay
import com.metaself.app.domain.letter.LetterPatterns
import com.metaself.app.domain.letter.LetterRequest
import com.metaself.app.domain.letter.SessionGroup
import com.metaself.app.domain.letter.WeekFigures
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate

/**
 * What leaves the phone for the weekly letter (D101, amended by D141) — the fourth thing this app sends.
 * Pure, with its own "nothing else is sent" test. Food goes only as a week's averages of daily totals, a
 * count of days logged, and each day's calories and protein; never a meal, a food's name, an amount or a time
 * of eating. How the letter is written (D143) is in the instructions verbatim, not left to chance.
 */
object LetterPrompt {

    private val INSTRUCTIONS = """
        You are a walking and running coach who writes one person a short letter at the end of each week,
        about their food, weight and movement together. You are given this week's figures and each of the
        four weeks before it, all counted by their app: food as averages a day over the days they logged
        and how many days they logged, the change of their smoothed weight trend across the week, their
        sessions with minutes, distance and how they felt, active energy and steps a day, and their weekly
        plan's planned and ticked sessions when one ran. Also this week's sessions, this week's ledger
        (below), their goal's direction and weekly rate, their age, sex and height, and today's daily
        calorie target.

        days holds the last 35 days, one row a day, oldest first, ending on this week's Sunday: the date and
        weekday; planned_minutes, the minutes of sessions that ticked a planned session, and own_minutes,
        the minutes of every other counted session, which were their own choice, both counted as the ledger
        counts them; sessions, each with its kind, minutes and felt ("easy", "right" or "hard", null when
        not reviewed); steps; sleep_minutes, the night ending that morning; and kcal and protein_g logged
        that day, null when nothing was logged that day.

        patterns were worked out by their app over those 35 days. Each is null when a side of it rests on
        fewer than three days or sessions, and then says nothing.
        - sleep_and_sessions: sessions after a night under the period's median sleep, against sessions after a night at or above it: how many, and their average minutes.
        - weekdays_and_weekends: counted minutes a day from Monday to Friday, against Saturday and Sunday, with how many days each.
        - food_on_moving_days: calories a day on logged days with at least 20 counted minutes, against logged days with fewer, with how many days each.
        - felt_easy: of the sessions they reviewed, how many felt easy this week, against the four weeks before.
        - longest_run: the longest stretch of consecutive days with at least one counted session.

        Their own words: about_me, their standing note about themselves; after_sessions, this week's answers
        to "How did it go?"; plan_requests, the words they gave when asking for a plan or an adjustment in
        the last five weeks. plan_goal is the goal of the plan that ran this week, when one did, and
        goal_view your stand on it when the plan was made. last_letters are your last three letters, newest
        first, each with its week, its look_after and its next_week.
        A null is a figure not recorded, never zero.
    """.trimIndent() + "\n\n" + "ledger is this week's ledger." + "\n\n" + TrainerPrompt.LEDGER + "\n\n" +
        TrainerPrompt.WEEK_RULES + "\n\n" + """
        How to write the letter:
        - Recognition first, and specific. Name what the effort took, not only what was done: going on the day it did not fit, choosing to do more, keeping a habit like logging every day. Say why it matters.
        - Sessions beyond the plan are their own choice, and are described so: "of your own", "your own choice". Never "nobody asked for", "unasked", "extra" or anything that makes them sound unwanted.
        - Write as the coach, in the first person, to them in the second. Short sentences. Warmth comes from specifics; no flattery, no exclamation marks, no emoji, no slogans.
        - One thing to look after, stated plainly and framed as protecting their progress. It connects two things or shows a change, and never repeats a total. Do not hedge: never write "not a verdict", "a useful point to notice" or anything like them. rests_on says what it rests on, and says so when that is few days.
        - Prefer a pattern or a thread across your last letters for paying_off or look_after when one is telling. Never claim a pattern that is not in patterns or plainly visible in days.
        - next_week follows from recognition and look_after: one or two concrete steps. When your last letter's next_week can be checked in the record, say how it went.
        - Figures: only those given, or a difference or ratio of two given ones (as "200 under your target"). Compare with their own earlier weeks, never with other people or ideals.
        - Nothing medical is diagnosed. If their words mention an injury, plan around it.

        Reply with: headline (one line: your judgement of the week, warm where the week earned it);
        recognition (what they did this week and what it took, in two to four short paragraphs separated by
        a blank line); paying_off (how the effort is showing in their record, opening with a short sentence
        that says it plainly, which is shown in bold); look_after (the one thing, as above); rests_on (what
        look_after rests on, in a few words, as "5 of 7 days logged · this week against the four before");
        next_week (one or two concrete steps); and close (one sentence about the week or the person, set
        apart, not a slogan).
    """.trimIndent() + "\n\n" + TrainerPrompt.WEEK_NOTE

    fun body(model: String, request: LetterRequest, profile: RequestProfile = RequestProfile.guess(model)): String =
        ChatRequest.body(
            model, profile,
            listOf(ChatRequest.Message("system", INSTRUCTIONS), ChatRequest.Message("user", user(request).toString())),
            "weekly_letter", SCHEMA,
        )

    private fun user(request: LetterRequest): JsonObject = buildJsonObject {
        val sunday = request.figures.week.monday + 6
        put("week", week(request.figures.week))
        put("ledger", TrainerPrompt.ledgerJson(request.ledger, withSoFar = false))
        putJsonArray("earlier_weeks") { request.figures.earlier.forEach { add(week(it)) } }
        put("target_kcal", request.figures.targetKcal?.let(::JsonPrimitive) ?: JsonNull)
        putJsonArray("sessions") { request.sessions.forEach { add(TrainerPrompt.sessionJson(it)) } }
        put("about_me", request.aboutMe?.let(::JsonPrimitive) ?: JsonNull)
        put(
            "goal",
            request.goal?.let { buildJsonObject { put("direction", it.direction.name.lowercase()); put("kg_a_week", it.kgPerWeek) } } ?: JsonNull,
        )
        put(
            "body",
            request.body?.let { buildJsonObject { put("age", it.ageYears); put("sex", it.sex.name.lowercase()); put("height_cm", it.heightCm) } } ?: JsonNull,
        )
        val planWeek = request.planWeek
        put(
            "plan",
            if (request.planTitle == null || planWeek == null) JsonNull else buildJsonObject {
                put("title", request.planTitle)
                put("focus", planWeek.focus)
                putJsonArray("sessions") { planWeek.sessions.forEach { add(TrainerPrompt.plannedJson(it)) } }
            },
        )
        putJsonArray("days") { request.days.forEach { add(day(it)) } }
        put("patterns", patterns(request.patterns))
        putJsonArray("after_sessions") {
            request.sessionWords.forEach { w ->
                add(
                    buildJsonObject {
                        put("date", TrainerPrompt.date(w.epochDay))
                        put("kind", TrainerPrompt.kind(w.kind))
                        put("felt", w.felt?.let { JsonPrimitive(it.name.lowercase()) } ?: JsonNull)
                        put("words", w.words)
                    },
                )
            }
        }
        putJsonArray("plan_requests") {
            request.planWords.forEach { w ->
                add(
                    buildJsonObject {
                        put("date", TrainerPrompt.date(w.epochDay))
                        put("asked", if (w.adjustment) "adjustment" else "plan")
                        put("words", w.words)
                    },
                )
            }
        }
        put("plan_goal", TrainerPrompt.sentGoal(request.planGoal, sunday))
        put("goal_view", request.goalView?.let(TrainerPrompt::goalViewJson) ?: JsonNull)
        putJsonArray("last_letters") {
            request.lastLetters.forEach { l ->
                add(
                    buildJsonObject {
                        put("from", TrainerPrompt.date(l.weekMonday))
                        put("look_after", l.lookAfter)
                        put("next_week", l.nextWeek)
                    },
                )
            }
        }
    }

    /** D141: one day row. */
    private fun day(day: LetterDay): JsonObject = buildJsonObject {
        put("date", TrainerPrompt.date(day.epochDay))
        put("weekday", LocalDate.ofEpochDay(day.epochDay).dayOfWeek.name.lowercase())
        put("planned_minutes", day.plannedMinutes)
        put("own_minutes", day.ownMinutes)
        putJsonArray("sessions") {
            day.sessions.forEach { s ->
                add(
                    buildJsonObject {
                        put("kind", TrainerPrompt.kind(s.kind))
                        put("minutes", s.minutes)
                        put("felt", s.felt?.let { JsonPrimitive(it.name.lowercase()) } ?: JsonNull)
                    },
                )
            }
        }
        put("steps", day.steps.json())
        put("sleep_minutes", day.sleepMinutes.json())
        put("kcal", day.kcal.json())
        put("protein_g", day.proteinG.json())
    }

    /** D142: each pattern with its figures and what it rests on; null when it rests on too few. */
    private fun patterns(p: LetterPatterns): JsonObject = buildJsonObject {
        put(
            "sleep_and_sessions",
            p.sleepAndSessions?.let {
                buildJsonObject {
                    put("median_sleep_minutes", it.medianSleepMinutes)
                    put("nights", it.nights)
                    put("after_shorter_night", group(it.afterShorter))
                    put("after_longer_night", group(it.afterLonger))
                }
            } ?: JsonNull,
        )
        put(
            "weekdays_and_weekends",
            p.weekdaysAndWeekends?.let {
                buildJsonObject {
                    put("weekday_minutes_a_day", it.weekdayMinutesADay)
                    put("weekdays", it.weekdays)
                    put("weekend_minutes_a_day", it.weekendMinutesADay)
                    put("weekend_days", it.weekendDays)
                }
            } ?: JsonNull,
        )
        put(
            "food_on_moving_days",
            p.foodOnMovingDays?.let {
                buildJsonObject {
                    put("moving_days_kcal_a_day", it.movingKcalADay)
                    put("moving_days", it.movingDays)
                    put("other_days_kcal_a_day", it.otherKcalADay)
                    put("other_days", it.otherDays)
                }
            } ?: JsonNull,
        )
        put(
            "felt_easy",
            p.feltEasy?.let {
                buildJsonObject {
                    put("this_week_easy", it.thisWeekEasy)
                    put("this_week_reviewed", it.thisWeekReviewed)
                    put("before_easy", it.beforeEasy)
                    put("before_reviewed", it.beforeReviewed)
                }
            } ?: JsonNull,
        )
        put(
            "longest_run",
            p.longestRun?.let {
                buildJsonObject {
                    put("days", it.days)
                    put("from", TrainerPrompt.date(it.from))
                    put("to", TrainerPrompt.date(it.to))
                }
            } ?: JsonNull,
        )
    }

    private fun group(g: SessionGroup): JsonObject = buildJsonObject {
        put("sessions", g.sessions)
        put("average_minutes", g.averageMinutes)
    }

    private fun Int?.json(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull

    private fun week(week: WeekFigures): JsonObject = buildJsonObject {
        put("from", TrainerPrompt.date(week.monday))
        putJsonObject("food") {
            put("days_logged", week.food.daysLogged)
            put("kcal_a_day", week.food.kcal?.let(::JsonPrimitive) ?: JsonNull)
            put("protein_g_a_day", week.food.proteinG?.let(::JsonPrimitive) ?: JsonNull)
            put("carbs_g_a_day", week.food.carbsG?.let(::JsonPrimitive) ?: JsonNull)
            put("fat_g_a_day", week.food.fatG?.let(::JsonPrimitive) ?: JsonNull)
        }
        put("weight_trend_change_kg", week.weightChangeKg?.let { JsonPrimitive(Math.round(it * 100) / 100.0) } ?: JsonNull)
        putJsonObject("movement") {
            put("sessions", week.movement.sessions)
            put("minutes", week.movement.minutes)
            put("distance_m", week.movement.distanceM?.let(::JsonPrimitive) ?: JsonNull)
            putJsonObject("felt") { put("easy", week.movement.easy); put("right", week.movement.right); put("hard", week.movement.hard) }
            put("active_kcal_a_day", week.movement.activeKcalADay?.let(::JsonPrimitive) ?: JsonNull)
            put("steps_a_day", week.movement.stepsADay?.let(::JsonPrimitive) ?: JsonNull)
        }
        put(
            "weekly_plan",
            week.plan?.let { buildJsonObject { put("planned", it.planned); put("done", it.done); put("ended", it.ended) } } ?: JsonNull,
        )
    }

    private fun text() = buildJsonObject { put("type", "string") }

    /** The reply's seven texts, in the order shown (D140). */
    val NAMES = listOf("headline", "recognition", "paying_off", "look_after", "rests_on", "next_week", "close")

    /** The seven texts, then the week's note (D116), which is not one of the letter's texts. */
    private val SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonObject("properties") { (NAMES + WEEK_NOTE).forEach { put(it, text()) } }
        putJsonArray("required") { (NAMES + WEEK_NOTE).forEach { add(it) } }
    }

    private const val WEEK_NOTE = "week_note"
}
