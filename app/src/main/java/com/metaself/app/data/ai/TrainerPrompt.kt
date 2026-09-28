package com.metaself.app.data.ai

import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.MonthFacts
import com.metaself.app.domain.trainer.Origin
import com.metaself.app.domain.trainer.SessionFacts
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.TrainerRequest
import kotlinx.serialization.json.JsonArray
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
 * What leaves the phone when the owner asks the trainer (D84) — the third thing this app sends, and
 * like [EstimatePrompt] and [ReviewPrompt] a pure function with its own "nothing else is sent" test.
 *
 * Everything comes from one [TrainerRequest], built fresh from the stored record; no earlier
 * conversation is replayed. The replies are pinned by strict schemas and checked again on the phone
 * ([TrainerResponse]).
 */
object TrainerPrompt {

    private val COMMON = """
        You are a walking and running trainer for one person. You are given their activity record: every
        session of the last 42 days with its figures and where each came from, six weekly totals (this
        week first, so far), a line for each of up to twelve months before those 42 days (months, oldest
        first), their smoothed weight trend and its weekly change, their goal's direction and weekly rate,
        their age, sex and height, and your last feedback to them.

        Each of the months covers the days from "from" to "to"; when whole_month is false it covers only
        those days. A month with no sessions is a month with none. Its figures were counted by the app:
        sessions by kind with their distance, total and longest minutes, the week (from its Monday) with
        the most distance, average heart rate weighted by minutes, how the reviewed sessions felt, steps a
        day, and the smoothed weight trend's change across it. A figure that is null was not recorded.

        about_me is their own standing description of themselves, written once and kept: injuries,
        preferences, equipment, what the training is for. Weigh it with the record. Where it conflicts
        with the numbers, the numbers are what happened.

        Their two aims are their weight goal and a steady rhythm of sessions. Keep your advice consistent
        with your earlier feedback unless the record gives a reason to change it.

        Safety comes first. If their words in the question itself (question.words when they ask for a
        plan, question.session.words when they ask about a session) mention
        pain, dizziness or chest discomfort, tell them to stop and see a doctor before saying anything else.
        Words on earlier sessions are context: you may mention them, but they do not call for this.
        about_me is context too: an old injury it mentions is something to plan around, not a reason to stop.
        You are a trainer for walking and running, not a medical service; never diagnose.

        Sources: "synced" is their phone and band's total over the session; "file" came from a workout
        file; "typed" they typed themselves; "band" is the band's own energy figure; "estimated" is this
        app's estimate from the kind of session and its effort; heart rate is worked out from the band's
        readings; zones are measured against a maximum that is "estimated" (220 minus age) unless it
        says "observed". An estimate is weaker evidence than a measurement.

        The rhythm: this_week gives the sessions they have done this week so far and the days left in it
        after today (not counting today), counted by the app. Use those numbers;
        never count sessions yourself.

        Write plain English, to them, in the second person. Short sentences. Every figure you mention
        must be one given here or one you propose for the next session.
    """.trimIndent()

    private val PLAN = """
        They are asking what to do in their next session. The question gives what they want to do, the
        time they have ("or_more" means at least that), how they feel and what they want today, and any
        words of theirs.

        Reply with a title, three to six steps in order, and one paragraph on why. Each step has
        from_minute and to_minute (whole minutes from the start), what it is, and how: a speed, an
        incline or a heart-rate zone where they apply, otherwise an empty string. The steps must fit the
        time they have.
    """.trimIndent()

    private val FEEDBACK = """
        They have done the session in the question and are telling you how it went: how it felt, their words,
        and the plan it was matched to, if any. Reply with a one-line headline and four short parts:
        against_plan (how it went against the plan; if there was no plan, say so in a few words),
        numbers (what its figures say), next_time (one concrete change for the next session), and
        this_week (the rhythm, from this_week). Give plan_followed: "yes", "partly" or "no" against the
        plan, or "no_plan" when there was none.
    """.trimIndent()

    fun planBody(model: String, request: TrainerRequest, profile: RequestProfile = RequestProfile.guess(model)): String {
        require(request.question is TrainerQuestion.Plan) { "a plan is asked with a plan question" }
        return ChatRequest.body(model, profile, messages(PLAN, request), "session_plan", PLAN_SCHEMA)
    }

    fun feedbackBody(model: String, request: TrainerRequest, profile: RequestProfile = RequestProfile.guess(model)): String {
        require(request.question is TrainerQuestion.Review) { "feedback is asked with a review question" }
        return ChatRequest.body(model, profile, messages(FEEDBACK, request), "session_feedback", FEEDBACK_SCHEMA)
    }

    private fun messages(task: String, request: TrainerRequest) = listOf(
        ChatRequest.Message("system", COMMON + "\n\n" + task),
        ChatRequest.Message("user", user(request).toString()),
    )

    private fun user(request: TrainerRequest): JsonObject = buildJsonObject {
        put("question", question(request.question))
        put("today", date(request.today))
        put("about_me", request.aboutMe?.let(::JsonPrimitive) ?: JsonNull)
        putJsonArray("sessions") { request.sessions.forEach { add(session(it)) } }
        putJsonArray("weeks") {
            request.weeks.forEach { week ->
                add(
                    buildJsonObject {
                        put("from", date(week.monday))
                        put("so_far", week.current)
                        put("distance_m", week.distanceM?.let(::JsonPrimitive) ?: JsonNull)
                        put("movement_kcal_a_day", week.averageActiveKcal?.let(::JsonPrimitive) ?: JsonNull)
                        put("sessions", week.sessions)
                    },
                )
            }
        }
        putJsonArray("months") { request.months.forEach { add(month(it)) } }
        put(
            "weight",
            request.weight?.let { weight ->
                buildJsonObject {
                    put("trend_kg", round1(weight.trendKg))
                    put("as_of", date(weight.asOfEpochDay))
                    put("change_kg_a_week", weight.kgPerWeek?.let { JsonPrimitive(round2(it)) } ?: JsonNull)
                    put("measured_over_days", weight.overDays?.let(::JsonPrimitive) ?: JsonNull)
                }
            } ?: JsonNull,
        )
        put(
            "goal",
            request.goal?.let { goal ->
                buildJsonObject {
                    put("direction", goal.direction.name.lowercase())
                    put("kg_a_week", goal.kgPerWeek)
                }
            } ?: JsonNull,
        )
        put(
            "body",
            request.body?.let { body ->
                buildJsonObject {
                    put("age", body.ageYears)
                    put("sex", body.sex.name.lowercase())
                    put("height_cm", body.heightCm)
                }
            } ?: JsonNull,
        )
        putJsonObject("this_week") {
            put("sessions_so_far", request.thisWeek.sessionsSoFar)
            put("days_left_after_today", request.thisWeek.daysLeft)
        }
        putJsonArray("earlier_feedback") { request.earlierFeedback.forEach { add(feedback(it)) } }
    }

    private fun question(question: TrainerQuestion): JsonObject = when (question) {
        is TrainerQuestion.Plan -> buildJsonObject {
            put("kind", "plan")
            put("what", question.answers.activity.name.lowercase().replace('_', ' '))
            put("minutes_available", question.answers.time.minutes)
            put("or_more", question.answers.time.orMore)
            put("feeling", question.answers.feeling.name.lowercase())
            put("wants", question.answers.wish.name.lowercase().replace('_', ' '))
            put("words", question.answers.words.trim())
        }
        is TrainerQuestion.Review -> buildJsonObject {
            put("kind", "review")
            put("session", session(question.session))
        }
    }

    private fun session(session: SessionFacts): JsonObject = buildJsonObject {
        put("date", date(session.epochDay))
        put("kind", kind(session.kind))
        put("minutes", session.minutes)
        put("distance_m", session.distanceM?.let(::JsonPrimitive) ?: JsonNull)
        put("distance_source", session.distanceFrom?.let { JsonPrimitive(origin(it)) } ?: JsonNull)
        put("energy_kcal", session.energyKcal?.let(::JsonPrimitive) ?: JsonNull)
        put("energy_source", session.energyFrom?.let { JsonPrimitive(energy(it)) } ?: JsonNull)
        put(
            "heart_rate",
            if (session.avgHeartRate == null && session.maxHeartRate == null) {
                JsonNull
            } else {
                buildJsonObject {
                    put("average", session.avgHeartRate?.let(::JsonPrimitive) ?: JsonNull)
                    put("highest", session.maxHeartRate?.let(::JsonPrimitive) ?: JsonNull)
                    put("source", "from readings")
                }
            },
        )
        put("zone_minutes", session.zoneMinutes?.let { zones -> JsonArray(zones.map(::JsonPrimitive)) } ?: JsonNull)
        put("zone_max", if (session.zoneMinutes == null) JsonNull else JsonPrimitive(if (session.zoneMaxEstimated) "estimated" else "observed"))
        put("steps", session.steps?.let(::JsonPrimitive) ?: JsonNull)
        put("steps_source", session.stepsFrom?.let { JsonPrimitive(origin(it)) } ?: JsonNull)
        put("felt", session.felt?.let { JsonPrimitive(it.name.lowercase()) } ?: JsonNull)
        put("words", session.words?.let(::JsonPrimitive) ?: JsonNull)
        put("plan", session.plan?.let(::plan) ?: JsonNull)
    }

    /** One monthly line (D89): counted on the phone, nothing raw. */
    private fun month(month: MonthFacts): JsonObject = buildJsonObject {
        put("from", date(month.firstDay))
        put("to", date(month.lastDay))
        put("whole_month", !month.part)
        put("sessions", month.sessions)
        put("minutes", month.minutes)
        putJsonArray("by_kind") {
            month.kinds.forEach { facts ->
                add(
                    buildJsonObject {
                        put("kind", kind(facts.kind))
                        put("sessions", facts.sessions)
                        put("distance_m", facts.distanceM?.let(::JsonPrimitive) ?: JsonNull)
                    },
                )
            }
        }
        put("longest_minutes", month.longestMinutes?.let(::JsonPrimitive) ?: JsonNull)
        put(
            "best_week",
            if (month.bestWeekMonday == null || month.bestWeekM == null) {
                JsonNull
            } else {
                buildJsonObject {
                    put("from", date(month.bestWeekMonday))
                    put("distance_m", month.bestWeekM)
                }
            },
        )
        put("heart_rate_average", month.avgHeartRate?.let(::JsonPrimitive) ?: JsonNull)
        put(
            "felt",
            month.felt?.let { felt ->
                buildJsonObject {
                    put("easy", felt.easy)
                    put("right", felt.right)
                    put("hard", felt.hard)
                }
            } ?: JsonNull,
        )
        put("steps_a_day", month.stepsADay?.let(::JsonPrimitive) ?: JsonNull)
        put("weight_trend_change_kg", month.weightChangeKg?.let { JsonPrimitive(round2(it)) } ?: JsonNull)
    }

    private fun plan(plan: SessionPlan): JsonObject = planJson(plan)

    private fun feedback(feedback: Feedback): JsonObject = feedbackJson(feedback)

    /** A plan in the reply schema's own shape: sent as an earlier plan, and what `TrainerResponse.encodePlan` stores. */
    fun planJson(plan: SessionPlan): JsonObject = buildJsonObject {
        put("title", plan.title)
        putJsonArray("steps") {
            plan.steps.forEach { step ->
                add(
                    buildJsonObject {
                        put("from_minute", step.fromMinute)
                        put("to_minute", step.toMinute)
                        put("what", step.what)
                        put("how", step.how)
                    },
                )
            }
        }
        put("why", plan.why)
    }

    /** Feedback in the reply schema's own shape: sent as earlier feedback, and what `TrainerResponse.encodeFeedback` stores. */
    fun feedbackJson(feedback: Feedback): JsonObject = buildJsonObject {
        put("headline", feedback.headline)
        put("against_plan", feedback.againstPlan)
        put("numbers", feedback.numbers)
        put("next_time", feedback.nextTime)
        put("this_week", feedback.thisWeek)
        put("plan_followed", feedback.followed.name.lowercase())
    }

    private fun kind(kind: WorkoutKind): String = when (kind) {
        WorkoutKind.RUN -> "run"
        WorkoutKind.WALK -> "walk"
        WorkoutKind.CYCLE -> "cycle"
        WorkoutKind.SWIM -> "swim"
        WorkoutKind.STRENGTH -> "strength"
        WorkoutKind.OTHER, WorkoutKind.UNRECOGNISED -> "other"
    }

    private fun origin(origin: Origin): String = origin.name.lowercase()

    private fun energy(source: EnergySource): String = when (source) {
        EnergySource.BAND -> "band"
        EnergySource.MET_ESTIMATE -> "estimated"
        EnergySource.TYPED -> "typed"
        EnergySource.FILE -> "file"
        EnergySource.NONE -> "unknown"
    }

    private fun date(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()

    private fun round1(value: Double): Double = Math.round(value * 10) / 10.0

    private fun round2(value: Double): Double = Math.round(value * 100) / 100.0

    private fun string() = buildJsonObject { put("type", "string") }

    private fun integer() = buildJsonObject { put("type", "integer") }

    private fun strictObject(vararg properties: Pair<String, JsonObject>) = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonObject("properties") { properties.forEach { (name, schema) -> put(name, schema) } }
        putJsonArray("required") { properties.forEach { add(it.first) } }
    }

    private val PLAN_SCHEMA: JsonObject = strictObject(
        "title" to string(),
        "steps" to buildJsonObject {
            put("type", "array")
            put("items", strictObject("from_minute" to integer(), "to_minute" to integer(), "what" to string(), "how" to string()))
        },
        "why" to string(),
    )

    private val FEEDBACK_SCHEMA: JsonObject = strictObject(
        "headline" to string(),
        "against_plan" to string(),
        "numbers" to string(),
        "next_time" to string(),
        "this_week" to string(),
        "plan_followed" to buildJsonObject {
            put("type", "string")
            putJsonArray("enum") { add("yes"); add("partly"); add("no"); add("no_plan") }
        },
    )
}
