package com.metaself.app.data.ai

import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.MonthFacts
import com.metaself.app.domain.trainer.Origin
import com.metaself.app.domain.trainer.PlannedOutcome
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.PlannedTick
import com.metaself.app.domain.trainer.SessionFacts
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.WeekOutcome
import com.metaself.app.domain.trainer.WeeksPlan
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
        best_week's distance is the whole days' distance (all movement),
        while by_kind's distance is the sessions' only.

        about_me is their own standing description of themselves, written once and kept: injuries,
        preferences, equipment, what the training is for. Weigh it with the record. Where it conflicts
        with the numbers, the numbers are what happened.

        Their two aims are their weight goal and a steady rhythm of sessions. Keep your advice consistent
        with your earlier feedback unless the record gives a reason to change it.

        Safety comes first. If their words in the question itself (question.words when they ask for a
        plan, an evaluation or a change to their weekly plan; question.session.words when they ask about
        a session) mention
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

        When the question gives planned, its week is the week of their weekly plan it falls in, counted
        from 1, and of_weeks is how many weeks that plan has.

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

        When question.planned is given, they are following a weekly plan and this is its next session
        (its week, kind, minutes, effort and what it is): shape the suggestion around it unless their
        answers say otherwise.
    """.trimIndent()

    private val FEEDBACK = """
        They have done the session in the question and are telling you how it went: how it felt, their words,
        and the plan it was matched to, if any. Reply with a one-line headline and four short parts:
        against_plan (how it went against the plan; if there was no plan, say so in a few words),
        numbers (what its figures say), next_time (one concrete change for the next session), and
        this_week (the rhythm, from this_week). Give plan_followed: "yes", "partly" or "no" against the
        plan, or "no_plan" when there was none.

        When question.planned is given, the session ticked that session of their weekly plan (counted by
        the app); say how it went against it in against_plan as well. plan_followed still judges only the
        plan in question.session.plan — "no_plan" when that is null, even though question.planned is
        given; question.planned does not change it.
    """.trimIndent()

    /** D105: shared by the evaluate and adjust instructions. */
    private val HOW_IT_WENT = """
        how_it_went gives, for each week, counted by the app: each planned session with its kind, its
        planned_minutes and its outcome — "done" (a session at least as long as planned), "done_short_confirmed"
        (a shorter session they said counts; minutes_done against planned_minutes) or "not_done" — and the
        attempts: sessions of a planned kind that ticked nothing (under half as long as planned, one they
        said does not count, or one still waiting for their answer), each with its minutes against the
        planned_minutes of the planned session it came nearest to. Attempts are effort: recognise them, and
        read them as a signal for the plan (for instance, that planned sessions may be too long), never as a
        failure. Use these counts; never count sessions yourself.
    """.trimIndent()

    private val EVALUATE = """
        They are asking where they stand, and for a plan for the weeks ahead. The question gives how many
        weeks, how many sessions a week they can manage (sessions_a_week), any words of theirs, the date the
        plan starts (starts, a Monday; weeks run Monday to Sunday), and your last evaluation if there is one,
        with the plan that ran with it and how many of its sessions were done each week (done_by_week,
        counted by the app: one number for each week that had begun, so a plan stopped early has
        fewer numbers than weeks) and how each of those weeks went (how_it_went, below).

        Reply with an evaluation and a plan. The evaluation: a one-line headline; going_well; to_work_on;
        and since_last, what has changed since the last evaluation, or an empty string when there is none.
        Judge from the record; do not invent a test or a score.

        The plan: a title; exactly as many weeks as asked, in order, each with a short focus and between
        one and sessions_a_week sessions; each session has a kind (walk, run, cycle, swim, strength or
        other), whole minutes from 5 to 180, an effort (easy, steady or push) and one line on what it is.
        Sessions have no day: each can be done on any day of its week. Then one paragraph on why.
    """.trimIndent() + "\n\n" + HOW_IT_WENT

    private val ADJUST = """
        They are following your weekly plan and ask you to change what is left of it. The question gives
        the plan, the week they are in (this_week_number, from 1), how many planned sessions were done in
        each week before it (done_by_week, counted by the app), the planned sessions already done this week
        (done_this_week, counted by the app), their words, and how each week so far went, this one included
        (how_it_went, below).

        Reply with a plan in the same shape for this week and the weeks after: a title; one entry per
        remaining week, starting with this one; and one paragraph on why. For this week give only the
        sessions still to do, at most max_this_week, and none is allowed; for each later week between one
        and sessions_a_week. Each session has a kind (walk, run, cycle, swim, strength or other), whole
        minutes from 5 to 180, an effort (easy, steady or push) and one line on what it is. Keep the same
        number of weeks; the end date does not move. Weeks already over are not yours to change.
    """.trimIndent() + "\n\n" + HOW_IT_WENT

    fun planBody(model: String, request: TrainerRequest, profile: RequestProfile = RequestProfile.guess(model)): String {
        require(request.question is TrainerQuestion.Plan) { "a plan is asked with a plan question" }
        return ChatRequest.body(model, profile, messages(PLAN, request), "session_plan", PLAN_SCHEMA)
    }

    fun feedbackBody(model: String, request: TrainerRequest, profile: RequestProfile = RequestProfile.guess(model)): String {
        require(request.question is TrainerQuestion.Review) { "feedback is asked with a review question" }
        return ChatRequest.body(model, profile, messages(FEEDBACK, request), "session_feedback", FEEDBACK_SCHEMA)
    }

    fun evaluateBody(model: String, request: TrainerRequest, profile: RequestProfile = RequestProfile.guess(model)): String {
        require(request.question is TrainerQuestion.Evaluate) { "an evaluation is asked with an evaluation question" }
        return ChatRequest.body(model, profile, messages(EVALUATE, request), "evaluation_and_plan", EVALUATION_SCHEMA)
    }

    fun adjustBody(model: String, request: TrainerRequest, profile: RequestProfile = RequestProfile.guess(model)): String {
        require(request.question is TrainerQuestion.Adjust) { "an adjustment is asked with an adjust question" }
        return ChatRequest.body(model, profile, messages(ADJUST, request), "weeks_plan", WEEKS_PLAN_SCHEMA)
    }

    private fun messages(task: String, request: TrainerRequest) = listOf(
        ChatRequest.Message("system", COMMON + "\n\n" + task),
        ChatRequest.Message("user", user(request).toString()),
    )

    private fun user(request: TrainerRequest): JsonObject = buildJsonObject {
        put("question", question(request.question))
        put("today", date(request.today))
        put("about_me", request.aboutMe?.let(::JsonPrimitive) ?: JsonNull)
        putJsonArray("sessions") { request.sessions.forEach { add(sessionJson(it)) } }
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
            put("planned", question.planned?.let(::plannedTick) ?: JsonNull)
        }
        is TrainerQuestion.Review -> buildJsonObject {
            put("kind", "review")
            put("session", sessionJson(question.session))
            put("planned", question.planned?.let(::plannedTick) ?: JsonNull)
        }
        is TrainerQuestion.Evaluate -> buildJsonObject {
            put("kind", "evaluate")
            put("weeks", question.ask.weeks)
            put("sessions_a_week", question.ask.perWeek)
            put("words", question.ask.words.trim())
            put("starts", date(question.startEpochDay))
            put(
                "last_evaluation",
                question.last?.let { last ->
                    buildJsonObject {
                        put("date", date(last.epochDay))
                        put("evaluation", evaluationJson(last.evaluation))
                        put("plan", weeksPlanJson(last.plan))
                        putJsonArray("done_by_week") { last.doneByWeek.forEach { add(it) } }
                        putJsonArray("how_it_went") { last.weeks.forEach { add(weekOutcome(it)) } }
                    }
                } ?: JsonNull,
            )
        }
        is TrainerQuestion.Adjust -> buildJsonObject {
            put("kind", "adjust")
            put("weeks", question.ask.weeks)
            put("sessions_a_week", question.ask.perWeek)
            put("starts", date(question.startEpochDay))
            put("plan", weeksPlanJson(question.plan))
            put("this_week_number", question.weekIndex + 1)
            putJsonArray("done_by_week") { question.doneByWeek.forEach { add(it) } }
            putJsonArray("done_this_week") { question.tickedThisWeek.forEach { add(plannedJson(it)) } }
            put("max_this_week", question.thisWeekMax)
            put("words", question.words.trim())
            putJsonArray("how_it_went") { question.howItWent.forEach { add(weekOutcome(it)) } }
        }
    }

    /** D105: one week as the phone counted it — each planned session's outcome, and the attempts. Minutes only. */
    private fun weekOutcome(week: WeekOutcome): JsonObject = buildJsonObject {
        put("week", week.week)
        putJsonArray("sessions") {
            week.sessions.forEach { session ->
                add(
                    buildJsonObject {
                        put("kind", kind(session.planned.kind))
                        put("planned_minutes", session.planned.minutes)
                        put("outcome", outcome(session.outcome))
                        put("minutes_done", session.minutesDone?.let(::JsonPrimitive) ?: JsonNull)
                    },
                )
            }
        }
        putJsonArray("attempts") {
            week.attempts.forEach { attempt ->
                add(
                    buildJsonObject {
                        put("kind", kind(attempt.kind))
                        put("minutes", attempt.minutes)
                        put("planned_minutes", attempt.plannedMinutes)
                    },
                )
            }
        }
    }

    private fun outcome(outcome: PlannedOutcome): String = when (outcome) {
        PlannedOutcome.DONE -> "done"
        PlannedOutcome.DONE_SHORT -> "done_short_confirmed"
        PlannedOutcome.NOT_DONE -> "not_done"
    }

    private fun plannedTick(tick: PlannedTick): JsonObject = buildJsonObject {
        put("week", tick.week)
        put("of_weeks", tick.ofWeeks)
        plannedJson(tick.session).forEach { (name, value) -> put(name, value) }
    }

    internal fun sessionJson(session: SessionFacts): JsonObject = buildJsonObject {
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

    /** A planned session in the reply schema's own shape (D94). */
    fun plannedJson(session: PlannedSession): JsonObject = buildJsonObject {
        put("kind", kind(session.kind))
        put("minutes", session.minutes)
        put("effort", session.effort.name.lowercase())
        put("what", session.what)
    }

    /** A plan of weeks in the reply schema's own shape: sent back, and what `TrainerResponse.encodeWeeksPlan` stores. */
    fun weeksPlanJson(plan: WeeksPlan): JsonObject = buildJsonObject {
        put("title", plan.title)
        putJsonArray("weeks") {
            plan.weeks.forEach { week ->
                add(
                    buildJsonObject {
                        put("focus", week.focus)
                        putJsonArray("sessions") { week.sessions.forEach { add(plannedJson(it)) } }
                    },
                )
            }
        }
        put("why", plan.why)
    }

    /** An evaluation in the reply schema's own shape: sent as the last one, and what `TrainerResponse.encodeEvaluation` stores. */
    fun evaluationJson(evaluation: Evaluation): JsonObject = buildJsonObject {
        put("headline", evaluation.headline)
        put("going_well", evaluation.goingWell)
        put("to_work_on", evaluation.toWorkOn)
        put("since_last", evaluation.sinceLast)
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

    internal fun date(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()

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

    private fun enumOf(vararg values: String) = buildJsonObject {
        put("type", "string")
        putJsonArray("enum") { values.forEach { add(it) } }
    }

    private fun arraySchema(items: JsonObject) = buildJsonObject {
        put("type", "array")
        put("items", items)
    }

    private val PLANNED_SCHEMA: JsonObject = strictObject(
        "kind" to enumOf("walk", "run", "cycle", "swim", "strength", "other"),
        "minutes" to integer(),
        "effort" to enumOf("easy", "steady", "push"),
        "what" to string(),
    )

    private val WEEKS_PLAN_SCHEMA: JsonObject = strictObject(
        "title" to string(),
        "weeks" to arraySchema(strictObject("focus" to string(), "sessions" to arraySchema(PLANNED_SCHEMA))),
        "why" to string(),
    )

    private val EVALUATION_SCHEMA: JsonObject = strictObject(
        "evaluation" to strictObject(
            "headline" to string(), "going_well" to string(), "to_work_on" to string(), "since_last" to string(),
        ),
        "plan" to WEEKS_PLAN_SCHEMA,
    )
}
