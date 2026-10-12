package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.letter.DaySession
import com.metaself.app.domain.letter.FeltEasy
import com.metaself.app.domain.letter.FoodOnMovingDays
import com.metaself.app.domain.letter.FoodWeek
import com.metaself.app.domain.letter.LetterDay
import com.metaself.app.domain.letter.LetterPatterns
import com.metaself.app.domain.letter.LongestRun
import com.metaself.app.domain.letter.PastLetter
import com.metaself.app.domain.letter.PlanRequestWords
import com.metaself.app.domain.letter.SessionGroup
import com.metaself.app.domain.letter.SessionWords
import com.metaself.app.domain.letter.SleepAndSessions
import com.metaself.app.domain.letter.WeekdaysAndWeekends
import com.metaself.app.domain.letter.LetterFigures
import com.metaself.app.domain.letter.LetterRequest
import com.metaself.app.domain.letter.MovementFigures
import com.metaself.app.domain.letter.PlanWeekFigures
import com.metaself.app.domain.letter.WeekFigures
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.profile.Sex
import com.metaself.app.domain.trainer.AttemptFacts
import com.metaself.app.domain.trainer.BeyondPlan
import com.metaself.app.domain.trainer.BodyFacts
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.FoodContext
import com.metaself.app.domain.trainer.GoalFacts
import com.metaself.app.domain.trainer.GoalVerdict
import com.metaself.app.domain.trainer.GoalView
import com.metaself.app.domain.trainer.PlanGoal
import com.metaself.app.domain.trainer.KindLedger
import com.metaself.app.domain.trainer.Origin
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedOutcome
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.PracticeLedger
import com.metaself.app.domain.trainer.Recovery
import com.metaself.app.domain.trainer.SessionFacts
import com.metaself.app.domain.trainer.SessionOutcome
import com.metaself.app.domain.trainer.WeekLedger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** What the letter sends (D101, D141), and how it is asked to write (D143). Every figure and word is invented. */
class LetterPromptTest {

    private val monday = MovementWeek.mondayOf(TEST_EPOCH_DAY)

    private fun week(monday: Long) = WeekFigures(
        monday, FoodWeek(5, 2_000, 100, 200, 70), -0.2, true,
        MovementFigures(3, 120, 9_000, 1, 1, 0, 300, 8_000), PlanWeekFigures("Invented plan", 3, 2, false),
    )

    private val session = SessionFacts(
        epochDay = monday + 1, kind = WorkoutKind.WALK, minutes = 40, distanceM = 4_000, distanceFrom = Origin.SYNCED,
        energyKcal = null, energyFrom = null, avgHeartRate = 110, maxHeartRate = 130, zoneMinutes = null,
        zoneMaxEstimated = true, steps = 5_000, stepsFrom = Origin.SYNCED, felt = null, words = null, plan = null,
    )

    /** The week's ledger, every part filled so every key path shows. Invented figures. */
    private val ledger = WeekLedger(
        monday = monday, current = true,
        practice = PracticeLedger(
            1, 2, listOf(SessionOutcome(PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Walk"), PlannedOutcome.DONE, 40)),
        ),
        beyond = BeyondPlan(listOf(KindLedger(WorkoutKind.WALK, 2, 50, 4_000, 20, listOf(AttemptFacts(WorkoutKind.WALK, 10, 30)))), 8_000, 6_000),
        food = FoodContext(5, 2_000, 100, 2_100),
        recovery = Recovery(420, 400, 60, 58, 45, 40),
    )

    private fun request() = LetterRequest(
        figures = LetterFigures(week(monday), List(4) { week(monday - 7L * (it + 1)) }, 2_100),
        ledger = ledger,
        sessions = listOf(session),
        aboutMe = "Invented note.",
        goal = GoalFacts(GoalDirection.LOSE, 0.5),
        body = BodyFacts(46, Sex.MALE, 180),
        planTitle = "Invented plan",
        planWeek = PlanWeek("Invented focus", listOf(PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Walk"))),
        days = days,
        patterns = LetterPatterns(
            SleepAndSessions(420, 30, SessionGroup(4, 30), SessionGroup(5, 40)),
            WeekdaysAndWeekends(20, 25, 30, 10),
            FoodOnMovingDays(2_200, 6, 1_900, 8),
            FeltEasy(2, 3, 3, 9),
            LongestRun(4, monday - 10, monday - 7),
        ),
        sessionWords = listOf(SessionWords(monday + 1, WorkoutKind.WALK, Felt.EASY, "Invented session words.")),
        planWords = listOf(PlanRequestWords(monday - 20, adjustment = false, words = "Invented plan words.")),
        planGoal = PlanGoal.WeeklyMinutes(150, monday + 27),
        goalView = GoalView(GoalVerdict.WITHIN_REACH, "Invented view."),
        lastLetters = listOf(PastLetter(monday - 7, "Invented look after.", "Invented line.")),
    )

    /** 35 days: the first logged and slept, with a planned walk; the rest with nothing recorded, minutes included. */
    private val days = (monday - 28..monday + 6).map { day ->
        if (day == monday - 28) {
            LetterDay(day, 30, 0, listOf(DaySession(WorkoutKind.WALK, 30, Felt.EASY)), 8_000, 420, 2_000, 100)
        } else {
            // Before the movement record begins: no minutes, not zero.
            LetterDay(day, null, null, emptyList(), null, null, null, null)
        }
    }

    private fun body() = LetterPrompt.body("a-model", request(), RequestProfile.DETERMINISTIC)

    private fun messages(body: String) = Json.parseToJsonElement(body).jsonObject.getValue("messages").jsonArray.map { it.jsonObject }

    private fun user(body: String): JsonObject = Json.parseToJsonElement(
        messages(body).single { it.getValue("role").jsonPrimitive.content == "user" }.getValue("content").jsonPrimitive.content,
    ).jsonObject

    private fun system(body: String): String =
        messages(body).first { it.getValue("role").jsonPrimitive.content == "system" }.getValue("content").jsonPrimitive.content

    /** Every key path in [element], arrays collapsed to `[]`: the whole shape of what is sent. */
    private fun paths(element: JsonElement, prefix: String = ""): Set<String> = when (element) {
        is JsonObject -> element.flatMap { (key, value) ->
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            setOf(path) + paths(value, path)
        }.toSet()
        is JsonArray -> element.flatMap { paths(it, "$prefix[]") }.toSet()
        else -> emptySet()
    }

    @Test
    fun `a letter request names its schema and holds exactly D101's parts`() {
        val body = body()
        val user = user(body)

        assertThat(body).contains("weekly_letter")
        assertThat(user.keys).containsExactlyElementsIn(TOP)
        assertThat(user.getValue("earlier_weeks").jsonArray).hasSize(4)
    }

    /**
     * D101's "never sent", as amended by D141, as the whole shape: food only as weeks' averages, a count of
     * days, and each day's calories and protein; the weight only as the trend's change across a week; a
     * session's figures without the owner's words, felt or plan; no meal, no food, no amount, no time of
     * eating, no single weigh-in, no raw reading; the week's ledger (D111); the 35 day rows and D142's
     * patterns; the owner's words only as this week's answers to "How did it go?", the plan-request words and
     * about_me; the plan's goal and the trainer's view of it; the last letters' look_after and next_week.
     * A new key anywhere fails here before it can leave the phone.
     */
    @Test
    fun `nothing else is sent`() {
        val day = listOf(
            "date", "weekday", "planned_minutes", "own_minutes", "sessions", "sessions[].kind", "sessions[].minutes",
            "sessions[].felt", "steps", "sleep_minutes", "kcal", "protein_g",
        )
        val patterns = listOf(
            "sleep_and_sessions", "sleep_and_sessions.median_sleep_minutes", "sleep_and_sessions.nights",
            "sleep_and_sessions.after_shorter_night", "sleep_and_sessions.after_shorter_night.sessions",
            "sleep_and_sessions.after_shorter_night.average_minutes", "sleep_and_sessions.after_longer_night",
            "sleep_and_sessions.after_longer_night.sessions", "sleep_and_sessions.after_longer_night.average_minutes",
            "weekdays_and_weekends", "weekdays_and_weekends.weekday_minutes_a_day", "weekdays_and_weekends.weekdays",
            "weekdays_and_weekends.weekend_minutes_a_day", "weekdays_and_weekends.weekend_days",
            "food_on_moving_days", "food_on_moving_days.moving_days_kcal_a_day", "food_on_moving_days.moving_days",
            "food_on_moving_days.other_days_kcal_a_day", "food_on_moving_days.other_days",
            "felt_easy", "felt_easy.this_week_easy", "felt_easy.this_week_reviewed", "felt_easy.before_easy",
            "felt_easy.before_reviewed", "longest_run", "longest_run.days", "longest_run.from", "longest_run.to",
        )
        val words = listOf(
            "after_sessions[].date", "after_sessions[].kind", "after_sessions[].felt", "after_sessions[].words",
            "plan_requests[].date", "plan_requests[].asked", "plan_requests[].words",
        )
        val goal = listOf(
            "plan_goal.kind", "plan_goal.minutes", "plan_goal.by", "plan_goal.weeks_to_go",
            "goal_view.verdict", "goal_view.says", "goal_view.instead",
        )
        val letters = listOf("last_letters[].from", "last_letters[].look_after", "last_letters[].next_week")
        val week = listOf(
            "from", "food", "food.days_logged", "food.kcal_a_day", "food.protein_g_a_day", "food.carbs_g_a_day",
            "food.fat_g_a_day", "weight_trend_change_kg", "movement", "movement.sessions", "movement.minutes",
            "movement.distance_m", "movement.felt", "movement.felt.easy", "movement.felt.right", "movement.felt.hard",
            "movement.active_kcal_a_day", "movement.steps_a_day", "weekly_plan", "weekly_plan.planned",
            "weekly_plan.done", "weekly_plan.ended",
        )
        val session = listOf(
            "date", "kind", "minutes", "distance_m", "distance_source", "energy_kcal", "energy_source", "heart_rate",
            "heart_rate.average", "heart_rate.highest", "heart_rate.source", "zone_minutes", "zone_max", "steps",
            "steps_source", "felt", "words", "plan",
        )
        val ledger = listOf(
            "from", "practice", "practice.week", "practice.of_weeks", "practice.sessions", "practice.sessions[].kind",
            "practice.sessions[].planned_minutes", "practice.sessions[].outcome", "practice.sessions[].minutes_done",
            "beyond_the_plan", "beyond_the_plan.sessions_by_kind", "beyond_the_plan.sessions_by_kind[].kind",
            "beyond_the_plan.sessions_by_kind[].sessions", "beyond_the_plan.sessions_by_kind[].minutes",
            "beyond_the_plan.sessions_by_kind[].distance_m", "beyond_the_plan.sessions_by_kind[].brisk_minutes",
            "beyond_the_plan.sessions_by_kind[].attempts", "beyond_the_plan.sessions_by_kind[].attempts[].minutes",
            "beyond_the_plan.sessions_by_kind[].attempts[].planned_minutes", "beyond_the_plan.everyday",
            "beyond_the_plan.everyday.steps_a_day", "beyond_the_plan.everyday.distance_m_a_day",
            "food", "food.days_logged", "food.kcal_a_day", "food.protein_g_a_day", "food.target_kcal",
            "recovery", "recovery.sleep_minutes_a_night", "recovery.usual_sleep_minutes_a_night",
            "recovery.resting_heart_rate", "recovery.usual_resting_heart_rate", "recovery.hrv_ms", "recovery.usual_hrv_ms",
        )
        val expected = TOP + day.map { "days[].$it" } + patterns.map { "patterns.$it" } + words + goal + letters +
            week.map { "week.$it" } + week.map { "earlier_weeks[].$it" } + session.map { "sessions[].$it" } + ledger.map { "ledger.$it" } +
            listOf("goal.direction", "goal.kg_a_week", "body.age", "body.sex", "body.height_cm") +
            listOf("plan.title", "plan.focus", "plan.sessions", "plan.sessions[].kind", "plan.sessions[].minutes", "plan.sessions[].effort", "plan.sessions[].what")

        val user = user(body())

        assertThat(paths(user)).containsExactlyElementsIn(expected)
        val sent = user.getValue("sessions").jsonArray.single().jsonObject
        listOf("felt", "words", "plan").forEach { assertThat(sent.getValue(it)).isEqualTo(JsonNull) }
    }

    @Test
    fun `a session with the owner's words, felt or plan cannot be put in a letter request`() {
        assertThrows<IllegalArgumentException> { request().copy(sessions = listOf(session.copy(words = "Invented words."))) }
        assertThrows<IllegalArgumentException> { request().copy(sessions = listOf(session.copy(felt = Felt.HARD))) }
    }

    /** The request's own types have room for nothing else: a new field fails here. */
    @Test
    fun `the request has room for exactly what D101 and D141 list`() {
        assertThat(fieldsOf(LetterRequest::class.java)).containsExactly(
            "figures", "ledger", "sessions", "aboutMe", "goal", "body", "planTitle", "planWeek", "days", "patterns",
            "sessionWords", "planWords", "planGoal", "goalView", "lastLetters",
        )
        assertThat(fieldsOf(LetterDay::class.java)).containsExactly(
            "epochDay", "plannedMinutes", "ownMinutes", "sessions", "steps", "sleepMinutes", "kcal", "proteinG",
        )
        assertThat(fieldsOf(DaySession::class.java)).containsExactly("kind", "minutes", "felt")
        assertThat(fieldsOf(SessionWords::class.java)).containsExactly("epochDay", "kind", "felt", "words")
        assertThat(fieldsOf(PlanRequestWords::class.java)).containsExactly("epochDay", "adjustment", "words")
        assertThat(fieldsOf(PastLetter::class.java)).containsExactly("weekMonday", "lookAfter", "nextWeek")
        assertThat(fieldsOf(LetterFigures::class.java)).containsExactly("week", "earlier", "targetKcal", "average\$delegate")
        assertThat(fieldsOf(WeekFigures::class.java)).containsExactly("monday", "food", "weightChangeKg", "weighedIn", "movement", "plan")
        assertThat(fieldsOf(FoodWeek::class.java)).containsExactly("daysLogged", "kcal", "proteinG", "carbsG", "fatG")
        assertThat(fieldsOf(MovementFigures::class.java)).containsExactly(
            "sessions", "minutes", "distanceM", "easy", "right", "hard", "activeKcalADay", "stepsADay",
        )
        assertThat(fieldsOf(PlanWeekFigures::class.java)).containsExactly("title", "planned", "done", "ended")
    }

    @Test
    fun `food goes as a week's averages and days logged, and nothing else about eating`() {
        val food = user(body()).getValue("week").jsonObject.getValue("food").jsonObject

        assertThat(food.keys).containsExactly("days_logged", "kcal_a_day", "protein_g_a_day", "carbs_g_a_day", "fat_g_a_day")
        assertThat(food.getValue("kcal_a_day").jsonPrimitive.content).isEqualTo("2000")
    }

    /** D143's rules, written into the instructions word for word; D102's softening is gone. */
    @Test
    fun `the instructions put recognition first, the own-choice wording, no hedging and the figures rule`() {
        val system = system(body())

        listOf(
            "Recognition first, and specific. Name what the effort took, not only what was done",
            "Sessions beyond the plan are their own choice, and are described so: \"of your own\", \"your own choice\".",
            "Never \"nobody asked for\", \"unasked\", \"extra\" or anything that makes them sound unwanted.",
            "Write as the coach, in the first person, to them in the second.",
            "no flattery, no exclamation marks, no emoji, no slogans.",
            "One thing to look after, stated plainly and framed as protecting their progress.",
            "It connects two things or shows a change, and never repeats a total.",
            "Do not hedge: never write \"not a verdict\", \"a useful point to notice\" or anything like them.",
            "rests_on says what it rests on, and says so when that is few days.",
            "Never claim a pattern that is not in patterns or plainly visible in days.",
            "When your last letter's next_week can be checked in the record, say how it went.",
            "Figures: only those given, or a difference or ratio of two given ones",
            "Nothing medical is diagnosed.",
        ).forEach { assertThat(system).contains(it) }
        // D102's rule that read as "never say anything" is not there.
        assertThat(system).doesNotContain("never as a failure")
    }

    @Test
    fun `the request carries the day rows, the patterns, the owner's words, the goal and the last letters`() {
        val user = user(body())

        val rows = user.getValue("days").jsonArray.map { it.jsonObject }
        assertThat(rows).hasSize(35)
        val first = rows.first()
        assertThat(first.getValue("date").jsonPrimitive.content).isEqualTo(TrainerPrompt.date(monday - 28))
        assertThat(first.getValue("weekday").jsonPrimitive.content).isEqualTo("monday")
        assertThat(first.getValue("planned_minutes").jsonPrimitive.content).isEqualTo("30")
        assertThat(first.getValue("sleep_minutes").jsonPrimitive.content).isEqualTo("420")
        assertThat(first.getValue("kcal").jsonPrimitive.content).isEqualTo("2000")
        assertThat(rows[1].getValue("own_minutes")).isEqualTo(JsonNull)
        assertThat(rows[1].getValue("planned_minutes")).isEqualTo(JsonNull)
        // A day with nothing logged sends null food, never zero.
        assertThat(rows[1].getValue("kcal")).isEqualTo(JsonNull)
        assertThat(rows[1].getValue("protein_g")).isEqualTo(JsonNull)

        val patterns = user.getValue("patterns").jsonObject
        assertThat(patterns.getValue("longest_run").jsonObject.getValue("days").jsonPrimitive.content).isEqualTo("4")
        assertThat(user.getValue("after_sessions").jsonArray.single().jsonObject.getValue("words").jsonPrimitive.content)
            .isEqualTo("Invented session words.")
        assertThat(user.getValue("plan_requests").jsonArray.single().jsonObject.getValue("asked").jsonPrimitive.content).isEqualTo("plan")
        assertThat(user.getValue("plan_goal").jsonObject.getValue("weeks_to_go").jsonPrimitive.content).isEqualTo("3")
        assertThat(user.getValue("goal_view").jsonObject.getValue("verdict").jsonPrimitive.content).isEqualTo("within_reach")
        assertThat(user.getValue("last_letters").jsonArray.single().jsonObject.getValue("look_after").jsonPrimitive.content)
            .isEqualTo("Invented look after.")
    }

    /** D142: a pattern resting on too few days goes as null, and the request says nothing more of it. */
    @Test
    fun `a pattern that rests on too few is sent as null`() {
        val user = user(LetterPrompt.body("a-model", request().copy(patterns = LetterPatterns(null, null, null, null, null)), RequestProfile.DETERMINISTIC))

        user.getValue("patterns").jsonObject.values.forEach { assertThat(it).isEqualTo(JsonNull) }
    }

    @Test
    fun `no more than three earlier letters can be put in a request`() {
        val four = List(4) { PastLetter(monday - 7L * (it + 1), "Invented.", "Invented.") }

        assertThrows<IllegalArgumentException> { request().copy(lastLetters = four) }
    }

    /** D112: the letter is told what its ledger holds, and the trainer's five rules, word for word. */
    @Test
    fun `the ledger and the rules for using it are in the instructions`() {
        val system = system(body())

        assertThat(system).contains("ledger is this week's ledger.")
        assertThat(system).contains(TrainerPrompt.LEDGER)
        assertThat(system).contains(TrainerPrompt.WEEK_RULES)
        listOf(
            "Both parts, every time.", "never excuse it with other activity", "Recovery shapes intensity.",
            "Food is context.", "Never prescribe a diet, a food or an amount.", "No figure the app did not count.",
        ).forEach { assertThat(system).contains(it) }
    }

    @Test
    fun `the reply is asked for as the coach's seven texts`() {
        assertThat(LetterPrompt.NAMES).containsExactly(
            "headline", "recognition", "paying_off", "look_after", "rests_on", "next_week", "close",
        ).inOrder()
        val strictless = LetterPrompt.body("a-model", request(), RequestProfile.DETERMINISTIC.copy(strictFormat = false))
        LetterPrompt.NAMES.forEach { assertThat(system(strictless)).contains(it) }
    }

    /** D116: the letter also gives the week's note, asked last, required beside the seven texts. */
    @Test
    fun `the letter asks for week_note too`() {
        val body = Json.parseToJsonElement(body()).jsonObject
        val schema = body.getValue("response_format").jsonObject.getValue("json_schema").jsonObject.getValue("schema").jsonObject

        assertThat(system(body())).endsWith(TrainerPrompt.WEEK_NOTE)
        assertThat(schema.getValue("properties").jsonObject.keys).containsExactlyElementsIn(LetterPrompt.NAMES + "week_note").inOrder()
        assertThat(schema.getValue("required").jsonArray.map { it.jsonPrimitive.content }).containsExactlyElementsIn(LetterPrompt.NAMES + "week_note")
        assertThat(LetterPrompt.NAMES).hasSize(7)
    }

    private companion object {
        val TOP = listOf(
            "week", "ledger", "earlier_weeks", "target_kcal", "sessions", "about_me", "goal", "body", "plan",
            "days", "patterns", "after_sessions", "plan_requests", "plan_goal", "goal_view", "last_letters",
        )
    }

    private fun fieldsOf(type: Class<*>): List<String> =
        type.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }
}
