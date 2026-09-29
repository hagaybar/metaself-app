package com.metaself.app.data.ai

import com.metaself.app.domain.letter.LetterRequest
import com.metaself.app.domain.letter.WeekFigures
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * What leaves the phone for the weekly letter (D101) — the fourth thing this app sends. Pure, with its
 * own "nothing else is sent" test. Food goes only as a week's averages of daily totals and a count of
 * days logged; the tone rules (D102) are written into the instructions verbatim, not left to chance.
 */
object LetterPrompt {

    private val INSTRUCTIONS = """
        You are a walking and running trainer who writes one person a short letter at the end of each week,
        about their food, weight and movement together. You are given this week's figures and each of the
        four weeks before it, all counted by their app: food as averages a day over the days they logged
        and how many days they logged, the change of their smoothed weight trend across the week, their
        sessions with minutes, distance and how they felt, active energy and steps a day, and their weekly
        plan's planned and ticked sessions when one ran. Also this week's sessions, their standing note
        about themselves (about_me), their goal's direction and weekly rate, their age, sex and height,
        today's daily calorie target, and the "for next week" line of last week's letter. A null is a
        figure not recorded.

        How to write. He asked for recognition of effort, and for encouragement without blame when a week
        goes badly — a trainer, not a new friend:
        - Name the effort behind a result, not only the result ("you made room for it on the days it wasn't convenient"), in the second person, plainly.
        - Compare with his own earlier weeks, never with other people or ideals.
        - One thing to look at, framed as information or a next step, never as a failure; a gap in logging is missing information, not a fault. Say when a figure rests on few days and cannot say much.
        - Never shame, never exaggerate, no exclamation marks, no emoji. Every figure mentioned must be one given.
        - The close is one warm sentence about the week or the person, not a slogan.
        - Nothing medical is diagnosed. If the note mentions an injury, plan around it.
        - Keep the letter consistent with last week's "for next week" line, and say how it went when the
          figures show it.

        Reply with: headline (one line, about the week), effort (what he put in), progress (where it is
        taking him, against his earlier weeks), look_at (one thing, as above), next_week (one concrete
        step), and close (as above).
    """.trimIndent()

    fun body(model: String, request: LetterRequest, profile: RequestProfile = RequestProfile.guess(model)): String =
        ChatRequest.body(
            model, profile,
            listOf(ChatRequest.Message("system", INSTRUCTIONS), ChatRequest.Message("user", user(request).toString())),
            "weekly_letter", SCHEMA,
        )

    private fun user(request: LetterRequest): JsonObject = buildJsonObject {
        put("week", week(request.figures.week))
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
        put("last_next_week", request.lastNextWeek?.let(::JsonPrimitive) ?: JsonNull)
    }

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

    /** The reply's six texts, in the order shown (D102). */
    val NAMES = listOf("headline", "effort", "progress", "look_at", "next_week", "close")

    private val SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonObject("properties") { NAMES.forEach { put(it, text()) } }
        putJsonArray("required") { NAMES.forEach { add(it) } }
    }
}
