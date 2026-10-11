package com.metaself.app.data.ai

import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.MonthFacts
import com.metaself.app.domain.trainer.Origin
import com.metaself.app.domain.trainer.PlannedOutcome
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.PlannedTick
import com.metaself.app.domain.trainer.SessionFacts
import com.metaself.app.domain.trainer.SessionOutcome
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.TrainerPath
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.WeekLedger
import com.metaself.app.domain.trainer.WeekOutcome
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.domain.trainer.Block
import com.metaself.app.domain.trainer.Outline
import com.metaself.app.domain.trainer.PlanGoal
import com.metaself.app.domain.trainer.WeekTarget
import kotlinx.serialization.json.JsonArray
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
        session of the last 42 days with its figures and where each came from, six week ledgers (weeks,
        below), a line for each of up to twelve months before those 42 days (months, oldest
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

        When question.planned is given, its week is the week of their weekly plan it falls in, counted
        from 1, and of_weeks is how many weeks that plan has.
    """.trimIndent()

    /** D111: the six ledgers, as a trainer request holds them. */
    private val SIX_WEEKS = """
        weeks holds six week ledgers, this week first and then the five before it; so_far is true for this
        week, which is not over yet.
    """.trimIndent()

    /** D110: what a week's ledger holds. Shared, word for word, with the letter's instructions. */
    internal val LEDGER = """
        A week's ledger runs Monday to Sunday from "from" and was counted by the app from their record. Every
        session of the week is in exactly one of its two parts, so nothing is counted twice and nothing is
        left out.
        practice is their weekly plan's week, when a kept plan covered it, and null when none did: week
        (counted from 1) of of_weeks, and each planned session with its kind, planned_minutes, outcome
        ("done", "done_short_confirmed" or "not_done") and minutes_done.
        beyond_the_plan is everything else they did. sessions_by_kind gives, for each kind, how many sessions
        ticked no planned session, their total minutes, their total distance_m where recorded, and
        brisk_minutes: the minutes in heart-rate zones 2 to 5, summed over those sessions that had zones
        (null when none had any, which is not zero). attempts lists those of them that were an attempt at a
        planned session, each with its minutes and the planned_minutes it attempted. everyday gives steps and
        distance a day, each averaged over the days that recorded it; it already includes the sessions' movement,
        so never add the two or take one from the other.
        food gives the days they logged food, their average calories and protein a day over those days, and
        their daily calorie target.
        recovery gives their average sleep a night in minutes, resting heart rate and heart-rate variability
        (hrv_ms), each over the days of the week that recorded it (null when fewer than 2 did), and each with
        its usual: the same average over the 28 days before the week's Monday,
        null when fewer than 7 of those days recorded it.
        A null is a figure not recorded, never zero.
    """.trimIndent()

    /** D112: how the ledgers are used — on every path and in the letter, word for word. */
    internal val WEEK_RULES = """
        How to use a week's ledger:
        - Both parts, every time. Judge planned practice as a commitment: name a planned session that was
          not done as not done, and never excuse it with other activity. Recognise what was done beyond the
          plan specifically, with its figures (for example "three other walks, 90 minutes, 40 of them
          brisk"), never with a phrase like "keep it up".
        - Lead with what matters most this week, whichever part it is in. A plan that asks for less than
          they already do is itself worth saying, and is a reason to suggest more.
        - Recovery shapes intensity. Sleep below its usual, or a resting heart rate above its usual,
          is a reason for an easier session or a rest day; say so. Do not diagnose.
        - Food is context. You may note eating well below or above the target alongside the training load.
          Never prescribe a diet, a food or an amount.
        - No figure the app did not count. Every figure you state about their record comes from what you
          are given; you may compare two of them, but never estimate one.
    """.trimIndent()

    /**
     * D116: the week's note, asked last on every path and in the letter, in the same request — never in a
     * request of its own. Shared, word for word, with the letter's instructions.
     */
    internal val WEEK_NOTE = """
        Also give week_note: at most two sentences about this week as a whole, as its ledger shows it,
        following the rules above for using a week's ledger: both parts, what matters most first, recovery
        shaping intensity, food as context, and no figure the app did not count. It is shown on its own,
        apart from the rest of your reply, so it must make sense without it.
    """.trimIndent()

    /** The last paragraph of the shared part, for the plan, evaluate and adjust paths. */
    private val VOICE = """
        Write plain English, to them, in the second person. Short sentences. Every figure you mention
        must be one given here or one you propose for the next session.
    """.trimIndent()

    /**
     * D108: [VOICE] as the feedback note was tried on the workbench — its own task sets the voice and the
     * length, so only the rule on figures is kept.
     */
    private val VOICE_NOTE = "Every figure you mention must be one given here or one you propose for the next session."

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

    /**
     * D108: the instructions tried on the workbench (D106) — one short note, the session placed in the
     * record's story — and plan_followed. Sent with [COMMON] and [VOICE_NOTE], never [VOICE].
     */
    private val FEEDBACK = """
        They have just done the session in the question and told you how it felt. Write them a note about
        it, the way a good coach who has followed them for months would: warm, encouraging, natural, in plain
        English, to them, in the second person. Sound like a person who is glad they went, not like a report.

        Tell them what their band cannot. They have already seen this session's figures on their wrist, so do
        not read them back unless they mean something. Put the session in the story of their record:

        - Where it sits in their weekly plan, when question.planned is given: which week of how many
          (week and of_weeks), and whether it is the first planned session they have done.
        - How it compares with their recent weeks and the months before: is it typical for them, a step up,
          a return after a quiet spell? Say which, plainly.
        - What it means for their two aims: a steady rhythm of sessions, and their weight goal.

        Doing more than planned, at an effort that felt easy, is a good sign, not a deviation; say so. Only
        hold them back if the record or their words give a reason (pain, a hard effort, a sharp jump in load).

        Mention where a figure came from, or that it is an estimate, only when that changes your advice. Never
        add a caveat for its own sake.

        Be honest about what the record shows. A few days is not a trend; one good week is a start, not a
        habit. Say "a good start" rather than "clearly improving" unless several weeks bear it out.

        Include at least one concrete comparison with a figure from the record: for example the most
        sessions in a week since a given month, or how this session's minutes compare with their usual
        length. Take the figure from the data; never estimate one yourself.

        Every sentence must be about them. Leave out any sentence that would fit anyone, such as "a single
        walk won't decide it" or "keep it relaxed".

        If there is one thing worth trying next time that follows from their record, say it in a sentence;
        if there is nothing specific, say nothing. Keep the whole note short enough to
        read in half a minute. Reply with the headline, one line, as headline, and the note as note.

        Also give plan_followed: "yes", "partly" or "no" against the plan in question.session.plan, or "no_plan" when
        that is null; question.planned does not change it. The note does not mention it.
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
        They are asking where they stand, and for a plan for the weeks ahead. The question gives their goal
        (question.goal, below), how many weeks the plan runs (weeks: for a distance or weekly_minutes, from the date it starts
        to the goal's week; for a steady rhythm, one block of four), how many sessions a week they can manage
        (sessions_a_week), any words of theirs, the date the plan starts (starts, a Monday; weeks run Monday
        to Sunday), and your last evaluation if there is one, with the plan that ran with it and how many of
        its sessions were done each week (done_by_week, counted by the app: one number for each week that had
        begun, so a plan stopped early has fewer numbers than weeks) and how each of those weeks went
        (how_it_went, below).

        Reply with an evaluation, a plan and an outline. The evaluation: a one-line headline; going_well;
        to_work_on; and since_last, what has changed since the last evaluation, or an
        empty string when there is none. Judge from the record; do not invent a test or a score.

        The outline: the blocks from the plan's first week to the goal's week, in order — for a steady rhythm
        one block of exactly four weeks — whose weeks together number exactly weeks; each week's target with
        minutes, longest_minutes (5 to 180) and easier. When question.goal is null, give an empty list of
        blocks.

        The plan: a title; the sessions of its first two weeks only — the later weeks carry their target
        until they are filled in — or, when question.goal is null, exactly as many weeks as asked; in order,
        each with a short focus and between one and sessions_a_week sessions; each session has a kind (walk,
        run, cycle, swim, strength or other), whole minutes from 5 to 180, an effort (easy, steady or push)
        and one line on what it is. Sessions have no day: each can be done on any day of its week. Then one
        paragraph on why.
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

        When the plan has an outline, only this week and the next carry sessions: reply with this week and
        next week only (this week alone in the plan's last week), and give targets — one per week from this
        one to the end, each with minutes, longest_minutes (5 to 180) and easier — which may change the
        outline's targets but not its blocks. When it has no outline, give targets as an empty list.
    """.trimIndent() + "\n\n" + HOW_IT_WENT

    /** D121: one week filled in, for a plan with an outline. */
    private val FILL_WEEK = """
        They are following your weekly plan, which has an outline, and ask you to fill in one week of it:
        week_to_fill, counted from 1 — next week, or this week when it has begun with no sessions. The
        question gives the plan with its outline, the week they are in (this_week_number, from 1; 0 before
        the plan starts), how many planned sessions were done in each week before it (done_by_week, counted
        by the app) and how each week so far went (how_it_went, below).

        Reply with that week: a short focus and between one and sessions_a_week sessions, each with a kind
        (walk, run, cycle, swim, strength or other), whole minutes from 5 to 180, an effort (easy, steady or
        push) and one line on what it is; then its target — minutes, longest_minutes (5 to 180) and easier —
        as the outline has it, or changed when the record argues for it; and target_why: one sentence on why
        you changed the target, or an empty string when you did not.
    """.trimIndent() + "\n\n" + HOW_IT_WENT

    /** D121: a steady plan's next block. */
    private val NEXT_BLOCK = """
        They are following your weekly plan towards a steady rhythm and are in the last week of its last
        block; they ask for the next block. The question gives the plan with its outline, the week they are
        in (this_week_number, from 1), how many planned sessions were done in each week before it
        (done_by_week, counted by the app) and how each week of the plan went (how_it_went, below).

        Reply with the next block, made from how this one went: a phase (usually steady), a one-line focus
        and exactly four weeks, each with its target — minutes, longest_minutes (5 to 180) and easier; then
        why, one paragraph. Its weeks are filled in with sessions later, as they come.
    """.trimIndent() + "\n\n" + HOW_IT_WENT

    /** D119, D120, D125: the goal and the outline, on the four plan paths. */
    private val GOALS = """
        question.goal is the plan's goal — not the weight goal above. It is null for a plan made before goals;
        otherwise either kind "steady" (keep a steady rhythm, with no end date, planned one block at a time)
        or kind "distance", with the sport (run, walk, swim or ride), km, by (the date, a Sunday) and
        weeks_to_go (counted by the app, from this week to the goal's week), or kind "weekly_minutes" (more time
        each week), with minutes (a week's total), by and weeks_to_go as a distance has them. For weekly_minutes,
        build the weekly total towards the goal's minutes, reaching it in the goal's week and holding it there —
        no taper, there is no race; use base and build blocks, with the conventions' easier weeks. A plan's outline is its blocks in
        order, each with a phase (base, build, peak, taper or steady), a one-line focus and one target per
        week: minutes (the week's total), longest_minutes (its longest session) and easier (an easier week).
        filled_weeks lists the weeks, counted from 1, that carry sessions; the others carry their target only.
    """.trimIndent()

    /**
     * D125: the coaching conventions, sent as conventions — the phone enforces none of them — and the rule on a
     * week's sum. On the four plan paths, never on a session's plan or its feedback.
     */
    internal val CONVENTIONS = """
        Coaching conventions — rules of thumb from endurance coaching, not measured standards. Use them as a
        coach would, and depart from them when the record argues otherwise:
        - Blocks of about four weeks, each with one focus: base, then build, then peak, then a taper of one to
          two weeks before a race. A steady rhythm repeats steady blocks.
        - Weekly load rising by about a tenth from one week to the next.
        - An easier week every fourth week.
        - Most sessions easy.
        A week's sessions should sum near its target, unless the week's ledger argues otherwise; then say why.
    """.trimIndent()

    fun planBody(model: String, request: TrainerRequest, profile: RequestProfile = RequestProfile.guess(model)): String {
        require(request.question is TrainerQuestion.Plan) { "a plan is asked with a plan question" }
        return ChatRequest.body(model, profile, messages(request), "session_plan", PLAN_SCHEMA)
    }

    fun feedbackBody(model: String, request: TrainerRequest, profile: RequestProfile = RequestProfile.guess(model)): String {
        require(request.question is TrainerQuestion.Review) { "feedback is asked with a review question" }
        return ChatRequest.body(model, profile, messages(request), "session_feedback", FEEDBACK_SCHEMA)
    }

    fun evaluateBody(model: String, request: TrainerRequest, profile: RequestProfile = RequestProfile.guess(model)): String {
        require(request.question is TrainerQuestion.Evaluate) { "an evaluation is asked with an evaluation question" }
        return ChatRequest.body(model, profile, messages(request), "evaluation_and_plan", EVALUATION_SCHEMA)
    }

    fun adjustBody(model: String, request: TrainerRequest, profile: RequestProfile = RequestProfile.guess(model)): String {
        require(request.question is TrainerQuestion.Adjust) { "an adjustment is asked with an adjust question" }
        return ChatRequest.body(model, profile, messages(request), "weeks_plan", ADJUST_SCHEMA)
    }

    fun fillBody(model: String, request: TrainerRequest, profile: RequestProfile = RequestProfile.guess(model)): String {
        require(request.question is TrainerQuestion.FillWeek) { "a week is filled in with a fill question" }
        return ChatRequest.body(model, profile, messages(request), "week_filled", FILL_SCHEMA)
    }

    fun nextBlockBody(model: String, request: TrainerRequest, profile: RequestProfile = RequestProfile.guess(model)): String {
        require(request.question is TrainerQuestion.NextBlock) { "a next block is asked with a next-block question" }
        return ChatRequest.body(model, profile, messages(request), "next_block", NEXT_BLOCK_SCHEMA)
    }

    /**
     * D106: the whole system message [path] sends today — the shared part, the six ledgers and D112's
     * rules, the voice paragraph and the path's own, and the week's note (D116), joined as every body joins them. The workbench saves this to a file as the starting point. A model without the
     * strict format is also sent the schema paragraph after it; that is reply shape, which the workbench
     * never sends.
     */
    fun instructions(path: TrainerPath): String =
        COMMON + "\n\n" + SIX_WEEKS + "\n\n" + LEDGER + "\n\n" + WEEK_RULES + "\n\n" + when (path) {
            TrainerPath.FEEDBACK -> VOICE_NOTE + "\n\n" + FEEDBACK
            TrainerPath.PLAN -> VOICE + "\n\n" + PLAN
            TrainerPath.EVALUATE -> VOICE + "\n\n" + EVALUATE + PLANNING
            TrainerPath.ADJUST -> VOICE + "\n\n" + ADJUST + PLANNING
            TrainerPath.FILL_WEEK -> VOICE + "\n\n" + FILL_WEEK + PLANNING
            TrainerPath.NEXT_BLOCK -> VOICE + "\n\n" + NEXT_BLOCK + PLANNING
        } + "\n\n" + WEEK_NOTE

    /** D125: what the four plan paths add after their own task — the goal and outline, then the conventions. */
    private val PLANNING: String get() = "\n\n" + GOALS + "\n\n" + CONVENTIONS

    /** The user message every body sends: [request], serialised. Shared with the workbench (D106). */
    fun userMessage(request: TrainerRequest): String = user(request).toString()

    /**
     * D106: the workbench's body — [system] as the only system message, the real user message, the
     * model's own settings — and no reply shape: no `response_format`, no schema instruction.
     */
    fun workbenchBody(
        model: String,
        system: String,
        request: TrainerRequest,
        profile: RequestProfile = RequestProfile.guess(model),
    ): String = ChatRequest.plainBody(
        model,
        profile,
        listOf(ChatRequest.Message("system", system), ChatRequest.Message("user", userMessage(request))),
    )

    private fun messages(request: TrainerRequest) = listOf(
        ChatRequest.Message("system", instructions(TrainerPath.of(request.question))),
        ChatRequest.Message("user", userMessage(request)),
    )

    private fun user(request: TrainerRequest): JsonObject = buildJsonObject {
        put("question", question(request.question, request.today))
        put("today", date(request.today))
        put("about_me", request.aboutMe?.let(::JsonPrimitive) ?: JsonNull)
        putJsonArray("sessions") { request.sessions.forEach { add(sessionJson(it)) } }
        putJsonArray("weeks") { request.weeks.forEach { add(ledgerJson(it)) } }
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

    private fun question(question: TrainerQuestion, today: Long): JsonObject = when (question) {
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
            put("goal", sentGoal(question.ask.goal, today))
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
            put("goal", sentGoal(question.ask.goal, today))
            put("weeks", question.ask.weeks)
            put("sessions_a_week", question.ask.perWeek)
            put("starts", date(question.startEpochDay))
            put("plan", weeksPlanJson(question.plan))
            put("this_week_number", question.weekIndex + 1)
            putJsonArray("filled_weeks") { question.plan.filledWeeks.forEach { add(it) } }
            putJsonArray("done_by_week") { question.doneByWeek.forEach { add(it) } }
            putJsonArray("done_this_week") { question.tickedThisWeek.forEach { add(plannedJson(it)) } }
            put("max_this_week", question.thisWeekMax)
            put("words", question.words.trim())
            putJsonArray("how_it_went") { question.howItWent.forEach { add(weekOutcome(it)) } }
        }
        is TrainerQuestion.FillWeek -> buildJsonObject {
            put("kind", "fill_week")
            put("goal", sentGoal(question.ask.goal, today))
            put("weeks", question.ask.weeks)
            put("sessions_a_week", question.ask.perWeek)
            put("starts", date(question.startEpochDay))
            put("plan", weeksPlanJson(question.plan))
            put("this_week_number", question.weekIndex + 1)
            put("week_to_fill", question.week + 1)
            putJsonArray("filled_weeks") { question.plan.filledWeeks.forEach { add(it) } }
            putJsonArray("done_by_week") { question.doneByWeek.forEach { add(it) } }
            putJsonArray("how_it_went") { question.howItWent.forEach { add(weekOutcome(it)) } }
        }
        is TrainerQuestion.NextBlock -> buildJsonObject {
            put("kind", "next_block")
            put("goal", sentGoal(question.ask.goal, today))
            put("weeks", question.ask.weeks)
            put("sessions_a_week", question.ask.perWeek)
            put("starts", date(question.startEpochDay))
            put("plan", weeksPlanJson(question.plan))
            put("this_week_number", question.weekIndex + 1)
            putJsonArray("filled_weeks") { question.plan.filledWeeks.forEach { add(it) } }
            putJsonArray("done_by_week") { question.doneByWeek.forEach { add(it) } }
            putJsonArray("how_it_went") { question.howItWent.forEach { add(weekOutcome(it)) } }
        }
    }

    /** D125, D127: the goal as sent — as stored, and for a dated goal the weeks from this week to the goal's week, counted here. */
    private fun sentGoal(goal: PlanGoal?, today: Long): JsonElement {
        if (goal == null) return JsonNull
        val stored = goalJson(goal)
        if (goal !is PlanGoal.Dated) return stored
        val weeksToGo = (MovementWeek.mondayOf(goal.byEpochDay) - MovementWeek.mondayOf(today)) / 7
        return JsonObject(stored + ("weeks_to_go" to JsonPrimitive(weeksToGo)))
    }

    /** D105: one week as the phone counted it — each planned session's outcome, and the attempts. Minutes only. */
    private fun weekOutcome(week: WeekOutcome): JsonObject = buildJsonObject {
        put("week", week.week)
        putJsonArray("sessions") {
            week.sessions.forEach { session -> add(sessionOutcomeJson(session)) }
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

    /** D105: a planned session's outcome, as `how_it_went` and a ledger's practice send it. Minutes only. */
    private fun sessionOutcomeJson(session: SessionOutcome): JsonObject = buildJsonObject {
        put("kind", kind(session.planned.kind))
        put("planned_minutes", session.planned.minutes)
        put("outcome", outcome(session.outcome))
        put("minutes_done", session.minutesDone?.let(::JsonPrimitive) ?: JsonNull)
    }

    /**
     * D110: one week's ledger, every figure the phone's; a null was not recorded. Shared with the letter,
     * which sends its week whole and so without `so_far` ([withSoFar] false).
     */
    internal fun ledgerJson(ledger: WeekLedger, withSoFar: Boolean = true): JsonObject = buildJsonObject {
        put("from", date(ledger.monday))
        if (withSoFar) put("so_far", ledger.current)
        put(
            "practice",
            ledger.practice?.let { practice ->
                buildJsonObject {
                    put("week", practice.week)
                    put("of_weeks", practice.ofWeeks)
                    putJsonArray("sessions") { practice.sessions.forEach { add(sessionOutcomeJson(it)) } }
                }
            } ?: JsonNull,
        )
        putJsonObject("beyond_the_plan") {
            putJsonArray("sessions_by_kind") {
                ledger.beyond.kinds.forEach { group ->
                    add(
                        buildJsonObject {
                            put("kind", kind(group.kind))
                            put("sessions", group.sessions)
                            put("minutes", group.minutes)
                            put("distance_m", number(group.distanceM))
                            put("brisk_minutes", number(group.briskMinutes))
                            putJsonArray("attempts") {
                                group.attempts.forEach { attempt ->
                                    add(
                                        buildJsonObject {
                                            put("minutes", attempt.minutes)
                                            put("planned_minutes", attempt.plannedMinutes)
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
            putJsonObject("everyday") {
                put("steps_a_day", number(ledger.beyond.stepsADay))
                put("distance_m_a_day", number(ledger.beyond.distanceMADay))
            }
        }
        putJsonObject("food") {
            put("days_logged", ledger.food.daysLogged)
            put("kcal_a_day", number(ledger.food.kcalADay))
            put("protein_g_a_day", number(ledger.food.proteinGADay))
            put("target_kcal", number(ledger.food.targetKcal))
        }
        putJsonObject("recovery") {
            put("sleep_minutes_a_night", number(ledger.recovery.sleepMinutes))
            put("usual_sleep_minutes_a_night", number(ledger.recovery.usualSleepMinutes))
            put("resting_heart_rate", number(ledger.recovery.restingHeartRate))
            put("usual_resting_heart_rate", number(ledger.recovery.usualRestingHeartRate))
            put("hrv_ms", number(ledger.recovery.hrvMs))
            put("usual_hrv_ms", number(ledger.recovery.usualHrvMs))
        }
    }

    private fun number(value: Int?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

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

    /**
     * Feedback in the shape it was stored: sent as earlier feedback, and what `TrainerResponse.encodeFeedback`
     * stores. A note (D108) is its reply schema's shape; four parts (D87) are the shape asked for before it.
     */
    fun feedbackJson(feedback: Feedback): JsonObject = buildJsonObject {
        put("headline", feedback.headline)
        if (feedback.note != null) {
            put("note", feedback.note)
        } else {
            put("against_plan", feedback.againstPlan)
            put("numbers", feedback.numbers)
            put("next_time", feedback.nextTime)
            put("this_week", feedback.thisWeek)
        }
        put("plan_followed", feedback.followed.name.lowercase())
    }

    /** A planned session in the reply schema's own shape (D94). */
    fun plannedJson(session: PlannedSession): JsonObject = buildJsonObject {
        put("kind", kind(session.kind))
        put("minutes", session.minutes)
        put("effort", session.effort.name.lowercase())
        put("what", session.what)
    }

    /**
     * A plan of weeks in the reply schema's own shape: sent back, and what `TrainerResponse.encodeWeeksPlan` stores.
     * Its outline (D120), when it has one, goes with it under `outline` — so what the trainer is sent back is what is
     * stored (D125); a plan made before outlines is written exactly as it always was.
     */
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
        plan.outline?.let { put("outline", outlineJson(it)) }
    }

    /** D120: an outline, in the reply schema's own shape. */
    fun outlineJson(outline: Outline): JsonObject = buildJsonObject {
        putJsonArray("blocks") { outline.blocks.forEach { add(blockJson(it)) } }
    }

    /** D120: one block — its phase by name, its focus, and each week's target. */
    fun blockJson(block: Block): JsonObject = buildJsonObject {
        put("phase", block.phase.name.lowercase())
        put("focus", block.focus)
        putJsonArray("weeks") { block.weeks.forEach { add(targetJson(it)) } }
    }

    /** D120: one week's target. */
    fun targetJson(target: WeekTarget): JsonObject = buildJsonObject {
        put("minutes", target.minutes)
        put("longest_minutes", target.longest)
        put("easier", target.easier)
    }

    /** D119: a goal as stored beside its plan, and as sent (with `weeks_to_go` added for a distance, D125). */
    fun goalJson(goal: PlanGoal): JsonObject = buildJsonObject {
        when (goal) {
            PlanGoal.Steady -> put("kind", "steady")
            is PlanGoal.Distance -> {
                put("kind", "distance")
                put("sport", goal.sport.name.lowercase())
                put("km", goal.km)
                put("by", date(goal.byEpochDay))
            }
            is PlanGoal.WeeklyMinutes -> {
                put("kind", "weekly_minutes")
                put("minutes", goal.minutes)
                put("by", date(goal.byEpochDay))
            }
        }
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
        "week_note" to string(),
    )

    private val FEEDBACK_SCHEMA: JsonObject = strictObject(
        "headline" to string(),
        "note" to string(),
        "plan_followed" to buildJsonObject {
            put("type", "string")
            putJsonArray("enum") { add("yes"); add("partly"); add("no"); add("no_plan") }
        },
        "week_note" to string(),
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

    private fun boolean() = buildJsonObject { put("type", "boolean") }

    /** D120: one week's target. */
    private val TARGET_SCHEMA: JsonObject = strictObject("minutes" to integer(), "longest_minutes" to integer(), "easier" to boolean())

    private val BLOCK_SCHEMA: JsonObject = strictObject(
        "phase" to enumOf("base", "build", "peak", "taper", "steady"),
        "focus" to string(),
        "weeks" to arraySchema(TARGET_SCHEMA),
    )

    private val EVALUATION_SCHEMA: JsonObject = strictObject(
        "evaluation" to strictObject(
            "headline" to string(), "going_well" to string(), "to_work_on" to string(), "since_last" to string(),
        ),
        "plan" to WEEKS_PLAN_SCHEMA,
        "outline" to strictObject("blocks" to arraySchema(BLOCK_SCHEMA)),
        "week_note" to string(),
    )

    /** D121: one week filled in, its target, and why it changed. */
    private val FILL_SCHEMA: JsonObject = strictObject(
        "focus" to string(),
        "sessions" to arraySchema(PLANNED_SCHEMA),
        "target" to TARGET_SCHEMA,
        "target_why" to string(),
        "week_note" to string(),
    )

    /** D121: a steady plan's next block. */
    private val NEXT_BLOCK_SCHEMA: JsonObject = strictObject("block" to BLOCK_SCHEMA, "why" to string(), "week_note" to string())

    /** D97's reply: the plan of weeks, and the week's note (D116). The plan inside an evaluation keeps [WEEKS_PLAN_SCHEMA]. */
    private val ADJUST_SCHEMA: JsonObject = strictObject(
        "title" to string(),
        "weeks" to arraySchema(strictObject("focus" to string(), "sessions" to arraySchema(PLANNED_SCHEMA))),
        "why" to string(),
        "targets" to arraySchema(TARGET_SCHEMA),
        "week_note" to string(),
    )
}
