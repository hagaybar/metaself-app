package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.trainer.AttemptFacts
import com.metaself.app.domain.trainer.CountedPlan
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.LastEvaluation
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanCounting
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.PlanStep
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedOutcome
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.PlannedTick
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.GOAL_15K
import com.metaself.app.domain.trainer.OUTLINE_15
import com.metaself.app.domain.trainer.PlanGoal
import com.metaself.app.domain.trainer.GOAL_150_MIN
import com.metaself.app.domain.trainer.RunningGoal
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.STEADY_OUTLINE
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.SessionOutcome
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerPath
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.WeekOutcome
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.domain.trainer.Wish
import com.metaself.app.domain.weight.WeightReading
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import com.metaself.app.domain.trainer.ConversationLine
import com.metaself.app.domain.trainer.LineKind
import com.metaself.app.domain.trainer.FactGroup
import com.metaself.app.domain.trainer.FactSource
import com.metaself.app.domain.trainer.SheetFact
import com.metaself.app.domain.trainer.TalkPlan
import java.time.LocalDateTime
import org.junit.jupiter.api.assertThrows

/**
 * What the trainer sends (D84) — the twin of [EstimatePromptTest]'s "nothing about the person is
 * sent". Every figure, date, title and word is invented; the weigh-in, the target, each single night's
 * sleep and each single day's resting heart rate below are here only so the test can prove they stay on
 * the phone.
 */
class TrainerPromptTest {

    @Test
    fun `a plan request names its schema and holds exactly D84's parts`() {
        val body = Json.parseToJsonElement(TrainerPrompt.planBody("a-model", planRequest(), RequestProfile.DETERMINISTIC)).jsonObject
        val user = Json.parseToJsonElement(userContent(body)).jsonObject

        assertThat(body.toString()).contains("session_plan")
        assertThat(user.keys).containsExactly(
            "question", "today", "about_me", "sheet", "sessions", "weeks", "months", "weight", "goal", "body", "this_week",
            "earlier_feedback",
        )
        assertThat(user.getValue("question").jsonObject.getValue("kind").jsonPrimitive.content).isEqualTo("plan")
        assertThat(user.getValue("this_week").jsonObject.getValue("sessions_so_far").jsonPrimitive.int).isEqualTo(2)
    }

    @Test
    fun `a feedback request names its schema and asks about one session`() {
        val body = TrainerPrompt.feedbackBody("a-model", reviewRequest(), RequestProfile.DETERMINISTIC)
        val user = Json.parseToJsonElement(userContent(Json.parseToJsonElement(body).jsonObject)).jsonObject

        assertThat(body).contains("session_feedback")
        val question = user.getValue("question").jsonObject
        assertThat(question.getValue("kind").jsonPrimitive.content).isEqualTo("review")
        assertThat(question.getValue("session").jsonObject.getValue("felt").jsonPrimitive.content).isEqualTo("right")
    }

    @Test
    fun `a session is sent with each figure's source, in words`() {
        val session = sentSessions(planRequest()).last()

        assertThat(session.getValue("distance_source").jsonPrimitive.content).isEqualTo("synced")
        assertThat(session.getValue("energy_source").jsonPrimitive.content).isEqualTo("band")
        assertThat(session.getValue("heart_rate").jsonObject.getValue("source").jsonPrimitive.content).isEqualTo("from readings")
        assertThat(session.getValue("zone_minutes").jsonArray.map { it.jsonPrimitive.int }).containsExactly(10, 20, 10, 0, 0).inOrder()
        assertThat(session.getValue("zone_max").jsonPrimitive.content).isEqualTo("estimated")
    }

    /**
     * D84's "never sent", amended by D111 and no further. If this fails because the prompt's own wording used
     * a word, rephrase the prompt.
     *
     * Each category has at least one value the fixtures really hold: the title "Quillberry loop"; a single
     * night's sleep (410, 450), a single day's resting heart rate (55, 61) and HRV (37, 53) — whose week's
     * averages (430, 58, 45) may go (D111); a single day's calories (1,900, 2,300) and protein (94, 126) —
     * whose averages may go; sleep stages (95 deep minutes), oxygen (96.5) and breathing rate (14.5); the
     * weigh-ins (80.4, 79.2, 78.6) and the profile's weight (83.0); the target weight (71.5). [yearRequest]
     * adds a June day outside every week's 28 days, whose sleep (470) and resting heart rate (64) stay on the
     * phone, and weigh-ins (82.0, 80.0) that must too (D89). A number is matched as a whole number, so 410
     * does not match inside 4100. The words and the package name have no path into a request; they stay as
     * a guard on the prompt.
     */
    @Test
    fun `no meal, single night or day, raw reading, weigh-in, target weight, title or app name is sent`() {
        listOf(
            TrainerPrompt.planBody("a-model", planRequest()),
            TrainerPrompt.feedbackBody("a-model", reviewRequest()),
            TrainerPrompt.planBody("a-model", yearRequest()),
        )
            .map { it.lowercase() }
            .forEach { body ->
                listOf(
                    "quillberry", "com.example", "slept", "meal", "eaten", "breakfast", "oxygen", "respiratory",
                    "deep_minutes", "83.0", "80.4", "79.2", "78.6", "71.5", "82.0", "80.0", "96.5", "14.5",
                ).forEach { forbidden -> assertThat(body).doesNotContain(forbidden) }
                listOf("410", "450", "55", "61", "37", "53", "1900", "2300", "94", "126", "95", "470", "64").forEach { forbidden ->
                    assertThat(Regex("(?<![0-9.])$forbidden(?![0-9.])").containsMatchIn(body)).isFalse()
                }
            }
    }

    /**
     * D89: June's line, from [yearRequest]. The trend's change is worked out from 82.0 kg on 31 May and
     * 80.0 kg on 30 June: 82 − 2 × (1 − 0.9^30) = 80.0848 kg at the end, a change of −1.92 kg.
     */
    @Test
    fun `a month is sent with its days, its sessions by kind and each figure`() {
        val body = Json.parseToJsonElement(TrainerPrompt.planBody("a-model", yearRequest(), RequestProfile.DETERMINISTIC)).jsonObject
        val months = Json.parseToJsonElement(userContent(body)).jsonObject.getValue("months").jsonArray.map { it.jsonObject }

        assertThat(months.map { it.getValue("from").jsonPrimitive.content }).containsExactly("2026-06-01", "2026-07-01").inOrder()
        val june = months.first()
        assertThat(june.keys).containsExactly(
            "from", "to", "whole_month", "sessions", "minutes", "by_kind", "longest_minutes", "best_week",
            "heart_rate_average", "felt", "steps_a_day", "weight_trend_change_kg",
        )
        assertThat(june.getValue("to").jsonPrimitive.content).isEqualTo("2026-06-30")
        assertThat(june.getValue("whole_month").jsonPrimitive.content).isEqualTo("true")
        assertThat(june.getValue("sessions").jsonPrimitive.int).isEqualTo(1)
        assertThat(june.getValue("minutes").jsonPrimitive.int).isEqualTo(50)
        val walks = june.getValue("by_kind").jsonArray.single().jsonObject
        assertThat(walks.getValue("kind").jsonPrimitive.content).isEqualTo("walk")
        assertThat(walks.getValue("sessions").jsonPrimitive.int).isEqualTo(1)
        assertThat(walks.getValue("distance_m").jsonPrimitive.int).isEqualTo(4_000)
        assertThat(june.getValue("longest_minutes").jsonPrimitive.int).isEqualTo(50)
        val best = june.getValue("best_week").jsonObject
        assertThat(best.getValue("from").jsonPrimitive.content).isEqualTo("2026-06-08")
        assertThat(best.getValue("distance_m").jsonPrimitive.int).isEqualTo(6_000)
        assertThat(june.getValue("heart_rate_average").jsonPrimitive.int).isEqualTo(120)
        val felt = june.getValue("felt").jsonObject
        assertThat(listOf("easy", "right", "hard").map { felt.getValue(it).jsonPrimitive.int }).containsExactly(0, 0, 1).inOrder()
        assertThat(june.getValue("steps_a_day").jsonPrimitive.int).isEqualTo(7_000)
        assertThat(june.getValue("weight_trend_change_kg").jsonPrimitive.content).isEqualTo("-1.92")

        val july = months.last()
        assertThat(july.getValue("to").jsonPrimitive.content).isEqualTo("2026-07-23")
        assertThat(july.getValue("whole_month").jsonPrimitive.content).isEqualTo("false")
        assertThat(july.getValue("sessions").jsonPrimitive.int).isEqualTo(0)
        assertThat(july.getValue("by_kind").jsonArray).isEmpty()
        listOf("longest_minutes", "best_week", "heart_rate_average", "felt", "steps_a_day", "weight_trend_change_kg").forEach {
            assertThat(july.getValue(it)).isEqualTo(JsonNull)
        }
    }

    /** D90: the note goes exactly as stored, and the model is told what it is and what outranks it. */
    @Test
    fun `the note is sent unchanged, and the numbers are what happened`() {
        val body = TrainerPrompt.planBody("a-model", yearRequest(), RequestProfile.DETERMINISTIC)
        val user = Json.parseToJsonElement(userContent(Json.parseToJsonElement(body).jsonObject)).jsonObject

        assertThat(user.getValue("about_me").jsonPrimitive.content).isEqualTo(NOTE)
        assertThat(Json.parseToJsonElement(userContent(Json.parseToJsonElement(TrainerPrompt.planBody("a-model", planRequest())).jsonObject))
            .jsonObject.getValue("about_me")).isEqualTo(JsonNull)
        val system = systemContent(body)
        assertThat(system).contains("about_me")
        assertThat(system).contains("standing description")
        assertThat(system).contains("the numbers are what happened")
        assertThat(system).contains("months")
    }

    /** D89: the two distances in a month are different things, and the model is told which is which. */
    @Test
    fun `a month's best week is all movement, its kinds' distance sessions only`() {
        val system = systemContent(TrainerPrompt.planBody("a-model", yearRequest()))

        assertThat(system).contains("best_week's distance is the whole days' distance")
        assertThat(system).contains("by_kind's distance is the sessions' only")
    }

    /** A standing note about an old injury is context, as words on earlier sessions are; it does not call for stopping. */
    @Test
    fun `the note is context, not a reason to stop`() {
        assertThat(systemContent(TrainerPrompt.planBody("a-model", yearRequest()))).contains("about_me is context too")
    }

    /** The smoothed line goes, rounded; the fixture's weigh-ins are 80.4, 79.2 and 78.6 (invented). */
    @Test
    fun `the weight sent is the smoothed line`() {
        val body = Json.parseToJsonElement(TrainerPrompt.planBody("a-model", planRequest(), RequestProfile.DETERMINISTIC)).jsonObject
        val weight = Json.parseToJsonElement(userContent(body)).jsonObject.getValue("weight").jsonObject

        assertThat(weight.getValue("trend_kg").jsonPrimitive.content).isEqualTo("78.8")
    }

    /**
     * D87: the rule is about the words in the question itself — the form's words for a plan, the
     * review's words for feedback. Words on earlier sessions are context, and do not trigger it.
     */
    @Test
    fun `pain, dizziness or chest discomfort in the question come before anything else, and it is not medical`() {
        listOf(
            TrainerPrompt.feedbackBody("a-model", reviewRequest()), TrainerPrompt.planBody("a-model", planRequest()),
            TrainerPrompt.evaluateBody("a-model", evaluateRequest()), TrainerPrompt.adjustBody("a-model", adjustRequest()),
        )
            .map(::systemContent)
            .forEach { system ->
                assertThat(system).contains("pain, dizziness or chest discomfort")
                assertThat(system).contains("stop and see a doctor")
                assertThat(system).contains("not a medical service")
                assertThat(system).contains("question.words")
                assertThat(system).contains("question.session.words")
                assertThat(system).contains("Words on earlier sessions are context")
                assertThat(system).doesNotContain("anywhere in this request")
            }
    }

    /** The profile's sex is sent as data; the instructions assume none, so they are right for any profile. */
    @Test
    fun `the instructions name no gender`() {
        val gendered = Regex("\\b(he|his|him|himself|she|her|hers|herself)\\b", RegexOption.IGNORE_CASE)
        listOf(
            TrainerPrompt.feedbackBody("a-model", reviewRequest()), TrainerPrompt.planBody("a-model", planRequest()),
            TrainerPrompt.evaluateBody("a-model", evaluateRequest()), TrainerPrompt.adjustBody("a-model", adjustRequest()),
        )
            .map(::systemContent)
            .forEach { system -> assertThat(gendered.findAll(system).map { it.value }.toList()).isEmpty() }
    }

    @Test
    fun `the rhythm is the phone's count, never the model's`() {
        val system = systemContent(TrainerPrompt.feedbackBody("a-model", reviewRequest()))

        assertThat(system).contains("this_week")
        assertThat(system).contains("never count sessions yourself")
    }

    /** The days left do not count today, and the model is told so. Today is a Thursday: three are left. */
    @Test
    fun `the days left are named as after today`() {
        val body = Json.parseToJsonElement(TrainerPrompt.planBody("a-model", planRequest(), RequestProfile.DETERMINISTIC)).jsonObject
        val thisWeek = Json.parseToJsonElement(userContent(body)).jsonObject.getValue("this_week").jsonObject

        assertThat(thisWeek.keys).containsExactly("sessions_so_far", "days_left_after_today")
        assertThat(thisWeek.getValue("days_left_after_today").jsonPrimitive.int).isEqualTo(3)
        assertThat(systemContent(TrainerPrompt.planBody("a-model", planRequest()))).contains("not counting today")
    }

    /** D110, D111: six ledgers, newest first; each with exactly these keys, and only the phone's counts. */
    @Test
    fun `each week is sent as its ledger, with exactly these keys`() {
        val body = Json.parseToJsonElement(TrainerPrompt.planBody("a-model", ledgerRequest(), RequestProfile.DETERMINISTIC)).jsonObject
        val weeks = Json.parseToJsonElement(userContent(body)).jsonObject.getValue("weeks").jsonArray.map { it.jsonObject }

        assertThat(weeks.map { it.getValue("from").jsonPrimitive.content })
            .containsExactly("2026-08-31", "2026-08-24", "2026-08-17", "2026-08-10", "2026-08-03", "2026-07-27").inOrder()
        val week = weeks.first()
        assertThat(week.keys).containsExactly("from", "so_far", "practice", "beyond_the_plan", "food", "recovery")
        assertThat(week.getValue("so_far").jsonPrimitive.boolean).isTrue()
        assertThat(weeks[1].getValue("so_far").jsonPrimitive.boolean).isFalse()

        val practice = week.getValue("practice").jsonObject
        assertThat(practice.keys).containsExactly("week", "of_weeks", "sessions")
        val planned = practice.getValue("sessions").jsonArray.map { it.jsonObject }
        assertThat(planned.first().keys).containsExactly("kind", "planned_minutes", "outcome", "minutes_done")
        assertThat(planned.map { it.getValue("outcome").jsonPrimitive.content }).containsExactly("done", "not_done").inOrder()
        assertThat(weeks[1].getValue("practice")).isEqualTo(JsonNull)

        val beyond = week.getValue("beyond_the_plan").jsonObject
        assertThat(beyond.keys).containsExactly("sessions_by_kind", "everyday")
        val strength = beyond.getValue("sessions_by_kind").jsonArray.single().jsonObject
        assertThat(strength.keys).containsExactly("kind", "sessions", "minutes", "distance_m", "brisk_minutes", "attempts")
        assertThat(strength.getValue("kind").jsonPrimitive.content).isEqualTo("strength")
        assertThat(strength.getValue("minutes").jsonPrimitive.int).isEqualTo(45)
        assertThat(strength.getValue("brisk_minutes")).isEqualTo(JsonNull)
        assertThat(beyond.getValue("everyday").jsonObject.keys).containsExactly("steps_a_day", "distance_m_a_day")

        val food = week.getValue("food").jsonObject
        assertThat(food.keys).containsExactly("days_logged", "kcal_a_day", "protein_g_a_day", "target_kcal")
        assertThat(food.getValue("days_logged").jsonPrimitive.int).isEqualTo(2)
        assertThat(food.getValue("kcal_a_day").jsonPrimitive.int).isEqualTo(2_100)
        assertThat(food.getValue("protein_g_a_day").jsonPrimitive.int).isEqualTo(110)
        assertThat(food.getValue("target_kcal").jsonPrimitive.int).isEqualTo(2_000)

        val recovery = week.getValue("recovery").jsonObject
        assertThat(recovery.keys).containsExactly(
            "sleep_minutes_a_night", "usual_sleep_minutes_a_night", "resting_heart_rate", "usual_resting_heart_rate",
            "hrv_ms", "usual_hrv_ms",
        )
        assertThat(recovery.getValue("sleep_minutes_a_night").jsonPrimitive.int).isEqualTo(430)
        assertThat(recovery.getValue("resting_heart_rate").jsonPrimitive.int).isEqualTo(58)
        assertThat(recovery.getValue("hrv_ms").jsonPrimitive.int).isEqualTo(45)
        assertThat(recovery.getValue("usual_sleep_minutes_a_night")).isEqualTo(JsonNull)
    }

    @Test
    fun `a plan is asked for as three to six steps with a minute range, what and how`() {
        val system = systemContent(TrainerPrompt.planBody("a-model", planRequest()))

        assertThat(system).contains("three to six steps")
        assertThat(system).contains("from_minute")
    }

    @Test
    fun `each body is built one way, from one request`() {
        listOf("planBody", "feedbackBody", "evaluateBody", "adjustBody").forEach { name ->
            val ways = TrainerPrompt::class.java.declaredMethods.filter { it.name == name }
            assertThat(ways).hasSize(1)
            assertThat(ways.single().parameterTypes.toList())
                .containsExactly(String::class.java, TrainerRequest::class.java, RequestProfile::class.java).inOrder()
        }
    }

    @Test
    fun `a plan request for a review, or a review for a plan, is refused`() {
        assertThrows<IllegalArgumentException> { TrainerPrompt.planBody("a-model", reviewRequest()) }
        assertThrows<IllegalArgumentException> { TrainerPrompt.feedbackBody("a-model", planRequest()) }
    }

    // --- D93–D97 -----------------------------------------------------------------------------------

    @Test
    fun `an evaluation request names its schema and sends the form, the start and the last evaluation`() {
        val body = Json.parseToJsonElement(TrainerPrompt.evaluateBody("a-model", evaluateRequest(), RequestProfile.DETERMINISTIC)).jsonObject
        val user = Json.parseToJsonElement(userContent(body)).jsonObject
        val question = user.getValue("question").jsonObject

        assertThat(body.toString()).contains("evaluation_and_plan")
        assertThat(user.keys).containsExactly(
            "question", "today", "about_me", "sheet", "sessions", "weeks", "months", "weight", "goal", "body", "this_week",
            "earlier_feedback",
        )
        assertThat(question.keys).containsExactly("kind", "goal", "weeks", "sessions_a_week", "words", "starts", "last_evaluation")
        assertThat(question.getValue("goal")).isEqualTo(JsonNull)
        assertThat(question.getValue("kind").jsonPrimitive.content).isEqualTo("evaluate")
        assertThat(question.getValue("starts").jsonPrimitive.content).isEqualTo("2026-08-31")
        val last = question.getValue("last_evaluation").jsonObject
        assertThat(last.keys).containsExactly("date", "evaluation", "plan", "done_by_week", "how_it_went")
        assertThat(last.getValue("how_it_went").jsonArray.map { it.jsonObject.getValue("week").jsonPrimitive.int }).containsExactly(1, 2).inOrder()
        assertThat(last.getValue("done_by_week").jsonArray.map { it.jsonPrimitive.int }).containsExactly(3, 2).inOrder()
        val planned = last.getValue("plan").jsonObject.getValue("weeks").jsonArray.first().jsonObject
            .getValue("sessions").jsonArray.first().jsonObject
        assertThat(planned.keys).containsExactly("kind", "minutes", "effort", "what")
        assertThat(planned.getValue("effort").jsonPrimitive.content).isEqualTo("steady")
    }

    @Test
    fun `with no earlier evaluation, last_evaluation is null and since_last may be empty`() {
        val body = Json.parseToJsonElement(TrainerPrompt.evaluateBody("a-model", evaluateRequest(last = null), RequestProfile.DETERMINISTIC)).jsonObject
        val question = Json.parseToJsonElement(userContent(body)).jsonObject.getValue("question").jsonObject

        assertThat(question.getValue("last_evaluation")).isEqualTo(JsonNull)
        assertThat(systemContent(TrainerPrompt.evaluateBody("a-model", evaluateRequest()))).contains("empty string when there is none")
    }

    @Test
    fun `an adjust request sends the plan, the week, the phone's counts and the words`() {
        val body = Json.parseToJsonElement(TrainerPrompt.adjustBody("a-model", adjustRequest(), RequestProfile.DETERMINISTIC)).jsonObject
        val question = Json.parseToJsonElement(userContent(body)).jsonObject.getValue("question").jsonObject

        assertThat(body.toString()).contains("weeks_plan")
        assertThat(question.keys).containsExactly(
            "kind", "goal", "weeks", "sessions_a_week", "starts", "plan", "this_week_number", "filled_weeks", "done_by_week",
            "done_this_week", "max_this_week", "words", "how_it_went",
        )
        assertThat(question.getValue("filled_weeks").jsonArray.map { it.jsonPrimitive.int }).containsExactly(1, 2).inOrder()
        assertThat(question.getValue("this_week_number").jsonPrimitive.int).isEqualTo(2)
        assertThat(question.getValue("done_by_week").jsonArray.map { it.jsonPrimitive.int }).containsExactly(3)
        assertThat(question.getValue("done_this_week").jsonArray).hasSize(1)
        assertThat(question.getValue("max_this_week").jsonPrimitive.int).isEqualTo(2)
    }

    @Test
    fun `the weekly plan's instructions say what may come back, and that the counts are the phone's`() {
        val evaluate = systemContent(TrainerPrompt.evaluateBody("a-model", evaluateRequest()))
        val adjust = systemContent(TrainerPrompt.adjustBody("a-model", adjustRequest()))

        assertThat(evaluate).contains("exactly as many weeks as asked")
        assertThat(evaluate).contains("5 to 180")
        assertThat(evaluate).contains("do not invent a test or a score")
        assertThat(evaluate).contains("fewer numbers than weeks")
        assertThat(adjust).contains("max_this_week")
        assertThat(adjust).contains("Weeks already over are not yours to change")
        listOf(evaluate, adjust).forEach { system ->
            assertThat(system).contains("counted by the app")
            assertThat(system).contains("pain, dizziness or chest discomfort")
            assertThat(system).contains("an evaluation or a change to their weekly plan")
        }
    }

    @Test
    fun `a plan question and a review carry the weekly plan's session, or null`() {
        val tick = PlannedTick(2, 4, PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Steady walk"))
        val withPlanned = request(
            TrainerQuestion.Plan(PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_45, Feeling.FRESH, Wish.NOT_SURE), tick),
        )
        val question = Json.parseToJsonElement(userContent(Json.parseToJsonElement(
            TrainerPrompt.planBody("a-model", withPlanned, RequestProfile.DETERMINISTIC),
        ).jsonObject)).jsonObject.getValue("question").jsonObject
        val review = Json.parseToJsonElement(userContent(Json.parseToJsonElement(
            TrainerPrompt.feedbackBody("a-model", reviewRequest(), RequestProfile.DETERMINISTIC),
        ).jsonObject)).jsonObject.getValue("question").jsonObject

        assertThat(question.getValue("planned").jsonObject.keys).containsExactly("week", "of_weeks", "kind", "minutes", "effort", "what")
        assertThat(question.getValue("planned").jsonObject.getValue("week").jsonPrimitive.int).isEqualTo(2)
        assertThat(question.getValue("planned").jsonObject.getValue("of_weeks").jsonPrimitive.int).isEqualTo(4)
        assertThat(review.getValue("planned")).isEqualTo(JsonNull)
        assertThat(systemContent(TrainerPrompt.planBody("a-model", withPlanned))).contains("question.planned")
    }

    @Test
    fun `an evaluate body for another question, or an adjust body for another, is refused`() {
        assertThrows<IllegalArgumentException> { TrainerPrompt.evaluateBody("a-model", planRequest()) }
        assertThrows<IllegalArgumentException> { TrainerPrompt.adjustBody("a-model", evaluateRequest()) }
    }

    @Test
    fun `a review's own weekly-plan tick is sent too, with its keys and week number`() {
        val tick = PlannedTick(3, 4, PlannedSession(WorkoutKind.RUN, 25, PlannedEffort.PUSH, "Push run"))
        val ticked = request(TrainerQuestion.Review(TrainerRequest.reviewQuestion(walk, review, plan).session, tick))

        val question = Json.parseToJsonElement(userContent(Json.parseToJsonElement(
            TrainerPrompt.feedbackBody("a-model", ticked, RequestProfile.DETERMINISTIC),
        ).jsonObject)).jsonObject.getValue("question").jsonObject

        assertThat(question.getValue("planned").jsonObject.keys).containsExactly("week", "of_weeks", "kind", "minutes", "effort", "what")
        assertThat(question.getValue("planned").jsonObject.getValue("week").jsonPrimitive.int).isEqualTo(3)
        assertThat(question.getValue("planned").jsonObject.getValue("of_weeks").jsonPrimitive.int).isEqualTo(4)
    }

    /** D107: the shared instructions say what of_weeks is, so any instructions can say "the first week of two". */
    @Test
    fun `every path's instructions say of_weeks is the plan's length`() {
        listOf(
            TrainerPrompt.planBody("a-model", planRequest()),
            TrainerPrompt.feedbackBody("a-model", reviewRequest()),
            TrainerPrompt.evaluateBody("a-model", evaluateRequest()),
            TrainerPrompt.adjustBody("a-model", adjustRequest()),
        ).forEach { body ->
            assertThat(systemContent(body)).contains("of_weeks is how many weeks that plan has")
        }
    }

    /** D110, D112: every path's instructions say what a ledger holds and carry the five rules for using it. */
    @Test
    fun `every path's instructions carry the ledger and the rules for using it`() {
        TrainerPath.entries.forEach { path ->
            val system = TrainerPrompt.instructions(path)

            assertThat(system).contains(TrainerPrompt.LEDGER)
            assertThat(system).contains(TrainerPrompt.WEEK_RULES)
            assertThat(system).contains("weeks holds six week ledgers, this week first")
            RULES.forEach { assertThat(system).contains(it) }
            assertThat(system).doesNotContain("six weekly totals")
        }
    }

    /**
     * D116: every path asks for the week's note last, in the same request, and every reply schema requires it — but a
     * reply in the conversation (D147), which asks for its words only and never replaces the coach's note.
     */
    @Test
    fun `every path asks for week_note last, and its schema requires it`() {
        TrainerPath.entries.filter { it != TrainerPath.TALK }
            .forEach { path -> assertThat(TrainerPrompt.instructions(path)).endsWith("\n\n" + TrainerPrompt.WEEK_NOTE) }
        assertThat(TrainerPrompt.instructions(TrainerPath.TALK)).doesNotContain(TrainerPrompt.WEEK_NOTE)
        assertThat(TrainerPrompt.WEEK_NOTE).contains("at most two sentences about this week as a whole")
        assertThat(TrainerPrompt.WEEK_NOTE).contains("no figure the app did not count")
        listOf(
            TrainerPrompt.planBody("a-model", planRequest(), RequestProfile.DETERMINISTIC),
            TrainerPrompt.feedbackBody("a-model", reviewRequest(), RequestProfile.DETERMINISTIC),
            TrainerPrompt.evaluateBody("a-model", evaluateRequest(), RequestProfile.DETERMINISTIC),
            TrainerPrompt.adjustBody("a-model", adjustRequest(), RequestProfile.DETERMINISTIC),
        ).forEach { body ->
            val schema = Json.parseToJsonElement(body).jsonObject.getValue("response_format").jsonObject
                .getValue("json_schema").jsonObject.getValue("schema").jsonObject
            assertThat(schema.getValue("properties").jsonObject.getValue("week_note").jsonObject.getValue("type").jsonPrimitive.content)
                .isEqualTo("string")
            assertThat(schema.getValue("required").jsonArray.map { it.jsonPrimitive.content }).contains("week_note")
        }
    }

    /** D110: the ledger's own words for what is never added up, and what a null is. */
    @Test
    fun `the ledger says everyday movement is never split, and a null is not zero`() {
        assertThat(TrainerPrompt.LEDGER).contains("so never add the two or take one from the other")
        assertThat(TrainerPrompt.LEDGER).contains("A null is a figure not recorded, never zero.")
        assertThat(TrainerPrompt.LEDGER).contains("null when fewer than 7 of those days recorded it")
    }

    /** plan_followed judges the matched single-session plan alone; a weekly-plan tick is context for against_plan only. */
    @Test
    fun `plan_followed stays about the matched single-session plan, even when a weekly tick is given`() {
        val system = systemContent(TrainerPrompt.feedbackBody("a-model", reviewRequest()))

        assertThat(system).contains("plan_followed")
        assertThat(system).contains("question.session.plan")
        assertThat(system).contains("question.planned does not change it")
    }

    /**
     * D108: the feedback task is the note tried on the workbench. COMMON goes with it unchanged except
     * that its voice paragraph is only the rule on figures, as tried; the other three paths still send it whole.
     */
    @Test
    fun `the feedback task asks for a coach's note, without Short sentences`() {
        val feedback = TrainerPrompt.instructions(TrainerPath.FEEDBACK)

        assertThat(feedback).contains("They have just done the session in the question and told you how it felt. Write them a note about")
        assertThat(feedback).contains("Tell them what their band cannot.")
        assertThat(feedback).contains("Every sentence must be about them.")
        assertThat(feedback).contains("Every figure you mention must be one given here or one you propose for the next session.")
        assertThat(feedback).doesNotContain("Short sentences.")
        assertThat(feedback).doesNotContain("Write plain English, to them, in the second person.")
        listOf("against_plan", "numbers (", "next_time", "four short parts").forEach { assertThat(feedback).doesNotContain(it) }
        listOf(TrainerPath.PLAN, TrainerPath.EVALUATE, TrainerPath.ADJUST).forEach { path ->
            assertThat(TrainerPrompt.instructions(path)).contains(
                "Write plain English, to them, in the second person. Short sentences. Every figure you mention\n" +
                    "must be one given here or one you propose for the next session.",
            )
            assertThat(TrainerPrompt.instructions(path)).doesNotContain("Write them a note")
        }
    }

    /** D108, D116: the reply is a headline, a note, plan_followed and the week's note, and nothing else. */
    @Test
    fun `a feedback reply is asked for as headline, note, plan_followed and week_note`() {
        val schema = Json.parseToJsonElement(TrainerPrompt.feedbackBody("a-model", reviewRequest(), RequestProfile.DETERMINISTIC))
            .jsonObject.getValue("response_format").jsonObject.getValue("json_schema").jsonObject.getValue("schema").jsonObject

        assertThat(schema.getValue("properties").jsonObject.keys).containsExactly("headline", "note", "plan_followed", "week_note").inOrder()
        assertThat(schema.getValue("required").jsonArray.map { it.jsonPrimitive.content })
            .containsExactly("headline", "note", "plan_followed", "week_note")
    }

    /** D108: earlier feedback goes in the shape it was stored — a note as a note, four parts as four parts. */
    @Test
    fun `earlier feedback is sent in the shape it was stored`() {
        val note = Feedback("Invented headline.", "", "", "", "", PlanFollowed.PARTLY, note = "Invented note.")
        val parts = Feedback("Invented old headline.", "Invented.", "Invented.", "Invented.", "Invented.", PlanFollowed.YES)
        val body = Json.parseToJsonElement(
            TrainerPrompt.planBody("a-model", request(planRequest().question, earlier = listOf(note, parts)), RequestProfile.DETERMINISTIC),
        ).jsonObject
        val sent = Json.parseToJsonElement(userContent(body)).jsonObject.getValue("earlier_feedback").jsonArray.map { it.jsonObject }

        assertThat(sent[0].keys).containsExactly("headline", "note", "plan_followed").inOrder()
        assertThat(sent[0].getValue("note").jsonPrimitive.content).isEqualTo("Invented note.")
        assertThat(sent[0].getValue("plan_followed").jsonPrimitive.content).isEqualTo("partly")
        assertThat(sent[1].keys)
            .containsExactly("headline", "against_plan", "numbers", "next_time", "this_week", "plan_followed").inOrder()
    }

    /** D105: each week's outcomes and attempts, minutes only — nothing about a session but its kind and minutes. */
    @Test
    fun `how each week went is sent as outcomes and attempts, with exactly these keys`() {
        val body = Json.parseToJsonElement(TrainerPrompt.adjustBody("a-model", adjustRequest(), RequestProfile.DETERMINISTIC)).jsonObject
        val week = Json.parseToJsonElement(userContent(body)).jsonObject.getValue("question").jsonObject
            .getValue("how_it_went").jsonArray.first().jsonObject

        assertThat(week.keys).containsExactly("week", "sessions", "attempts")
        val sessions = week.getValue("sessions").jsonArray.map { it.jsonObject }
        assertThat(sessions.first().keys).containsExactly("kind", "planned_minutes", "outcome", "minutes_done")
        assertThat(sessions.map { it.getValue("outcome").jsonPrimitive.content })
            .containsExactly("done", "done_short_confirmed", "not_done").inOrder()
        assertThat(sessions[1].getValue("minutes_done").jsonPrimitive.int).isEqualTo(30)
        assertThat(sessions[2].getValue("minutes_done")).isEqualTo(JsonNull)
        val attempt = week.getValue("attempts").jsonArray.single().jsonObject
        assertThat(attempt.keys).containsExactly("kind", "minutes", "planned_minutes")
        assertThat(attempt.getValue("kind").jsonPrimitive.content).isEqualTo("walk")
        assertThat(attempt.getValue("minutes").jsonPrimitive.int).isEqualTo(10)
        assertThat(attempt.getValue("planned_minutes").jsonPrimitive.int).isEqualTo(40)
    }

    @Test
    fun `the weekly plan's instructions say attempts are effort and a signal, never a failure`() {
        listOf(
            systemContent(TrainerPrompt.evaluateBody("a-model", evaluateRequest())),
            systemContent(TrainerPrompt.adjustBody("a-model", adjustRequest())),
        ).forEach { system ->
            assertThat(system).contains("how_it_went")
            assertThat(system).contains("done_short_confirmed")
            assertThat(system).contains("Attempts are effort")
            assertThat(system).contains("never as a")
        }
        assertThat(systemContent(TrainerPrompt.planBody("a-model", planRequest()))).doesNotContain("how_it_went")
        assertThat(systemContent(TrainerPrompt.feedbackBody("a-model", reviewRequest()))).doesNotContain("how_it_went")
    }

    /** D151: every path's body — and the workbench's — sends the sheet's current facts, and null when there are none. */
    @Test
    fun `every path sends the sheet`() {
        val goals = request(TrainerQuestion.SuggestGoals(3, "", TEST_EPOCH_DAY + 4, LAST, RunningGoal(GOAL_15K, OUTLINE_15)))
        val bodies: List<(TrainerRequest) -> String> = listOf(
            { TrainerPrompt.planBody("a-model", it) },
            { TrainerPrompt.feedbackBody("a-model", it) },
            { TrainerPrompt.evaluateBody("a-model", it) },
            { TrainerPrompt.adjustBody("a-model", it) },
            { TrainerPrompt.fillBody("a-model", it) },
            { TrainerPrompt.nextBlockBody("a-model", it) },
            { TrainerPrompt.suggestGoalsBody("a-model", it) },
            { TrainerPrompt.talkBody("a-model", it) },
            { TrainerPrompt.workbenchBody("a-model", LOADED, it) },
        )
        val requests = listOf(
            planRequest(), reviewRequest(), evaluateRequest(), adjustRequest(), fillRequest(), nextBlockRequest(), goals,
            talkRequest(), planRequest(),
        )
        bodies.zip(requests).forEach { (body, request) ->
            val sent = Json.parseToJsonElement(userContent(Json.parseToJsonElement(body(request.copy(sheet = SHEET))).jsonObject)).jsonObject
            val sheet = sent.getValue("sheet").jsonArray.single().jsonObject
            assertThat(sheet.keys).containsExactly("group", "text", "since", "source").inOrder()
            assertThat(sheet.getValue("text").jsonPrimitive.content).isEqualTo("An invented fact.")
            assertThat(sheet.getValue("group").jsonPrimitive.content).isEqualTo("goals")
            assertThat(sheet.getValue("source").jsonPrimitive.content).isEqualTo("they_said")
            assertThat(sheet.getValue("since").jsonPrimitive.content).isEqualTo("2026-08-29")
            val without = Json.parseToJsonElement(userContent(Json.parseToJsonElement(body(request)).jsonObject)).jsonObject
            assertThat(without.getValue("sheet")).isEqualTo(JsonNull)
        }
    }

    @Test
    fun `the shared instructions say what the sheet is, and that the numbers win`() {
        TrainerPath.entries.forEach { path ->
            val text = TrainerPrompt.instructions(path)
            assertThat(text).contains("sheet is what you know about them from your conversations with them")
            assertThat(text).contains("where it conflicts with the numbers, the numbers are what happened")
        }
    }

    /** D106: "save the app's instructions" is the system message each real body sends, not a copy of it. */
    @Test
    fun `each path's instructions are exactly the system message its body sends`() {
        val strict = RequestProfile.DETERMINISTIC
        assertThat(systemContent(TrainerPrompt.planBody("a-model", planRequest(), strict))).isEqualTo(TrainerPrompt.instructions(TrainerPath.PLAN))
        assertThat(systemContent(TrainerPrompt.feedbackBody("a-model", reviewRequest(), strict))).isEqualTo(TrainerPrompt.instructions(TrainerPath.FEEDBACK))
        assertThat(systemContent(TrainerPrompt.evaluateBody("a-model", evaluateRequest(), strict))).isEqualTo(TrainerPrompt.instructions(TrainerPath.EVALUATE))
        assertThat(systemContent(TrainerPrompt.adjustBody("a-model", adjustRequest(), strict))).isEqualTo(TrainerPrompt.instructions(TrainerPath.ADJUST))
        assertThat(systemContent(TrainerPrompt.fillBody("a-model", fillRequest(), strict))).isEqualTo(TrainerPrompt.instructions(TrainerPath.FILL_WEEK))
        assertThat(systemContent(TrainerPrompt.nextBlockBody("a-model", nextBlockRequest(), strict))).isEqualTo(TrainerPrompt.instructions(TrainerPath.NEXT_BLOCK))
    }

    @Test
    fun `each body's user message is userMessage of its request`() {
        listOf(
            planRequest() to TrainerPrompt.planBody("a-model", planRequest()),
            reviewRequest() to TrainerPrompt.feedbackBody("a-model", reviewRequest()),
            evaluateRequest() to TrainerPrompt.evaluateBody("a-model", evaluateRequest()),
            adjustRequest() to TrainerPrompt.adjustBody("a-model", adjustRequest()),
        ).forEach { (request, body) ->
            assertThat(userContent(Json.parseToJsonElement(body).jsonObject)).isEqualTo(TrainerPrompt.userMessage(request))
        }
    }

    /** D106: the loaded text is the whole system message; the reply is not pinned in any way. */
    @Test
    fun `a workbench body has the given text as its only system message, the real user message, and no reply shape`() {
        val body = Json.parseToJsonElement(
            TrainerPrompt.workbenchBody("a-model", LOADED, planRequest(), RequestProfile.REASONING),
        ).jsonObject

        val sent = messages(body)
        assertThat(sent.map { it.getValue("role").jsonPrimitive.content }).containsExactly("system", "user").inOrder()
        assertThat(sent[0].getValue("content").jsonPrimitive.content).isEqualTo(LOADED)
        assertThat(sent[1].getValue("content").jsonPrimitive.content).isEqualTo(TrainerPrompt.userMessage(planRequest()))
        assertThat(body.keys).containsExactly("model", "reasoning_effort", "messages").inOrder()
        assertThat(body.getValue("reasoning_effort").jsonPrimitive.content).isEqualTo("low")
    }

    @Test
    fun `a workbench body keeps the model's settings, and adds no schema even for a model without the strict format`() {
        val deterministic = Json.parseToJsonElement(
            TrainerPrompt.workbenchBody("a-model", LOADED, reviewRequest(), RequestProfile.DETERMINISTIC),
        ).jsonObject
        val loose = TrainerPrompt.workbenchBody("a-model", LOADED, reviewRequest(), RequestProfile.REASONING.copy(strictFormat = false))

        assertThat(deterministic.keys).containsExactly("model", "temperature", "messages").inOrder()
        assertThat(deterministic.getValue("temperature").jsonPrimitive.int).isEqualTo(0)
        assertThat(systemContent(loose)).isEqualTo(LOADED)
        assertThat(Json.parseToJsonElement(loose).jsonObject.keys).doesNotContain("response_format")
    }

    /** Invented: a four-week, three-a-week ask; the plan starts Monday 31 August 2026. */
    private fun evaluateRequest(last: LastEvaluation? = LAST) = request(
        TrainerQuestion.Evaluate(ProgrammeAsk(4, 3, "Invented words."), TEST_EPOCH_DAY - 3, last),
    )

    // --- D119–D121, D125: goals and outlines. The goal fixture; every figure is invented. ------------------

    @Test
    fun `a goal's evaluation sends the goal with its weeks to go, and the weeks it makes`() {
        val request = request(TrainerQuestion.Evaluate(ProgrammeAsk(15, 3, "Invented words.", GOAL_15K), TEST_EPOCH_DAY - 3, null))
        val body = Json.parseToJsonElement(TrainerPrompt.evaluateBody("a-model", request, RequestProfile.DETERMINISTIC)).jsonObject
        val question = Json.parseToJsonElement(userContent(body)).jsonObject.getValue("question").jsonObject
        val goal = question.getValue("goal").jsonObject

        assertThat(goal.keys).containsExactly("kind", "sport", "km", "by", "weeks_to_go")
        assertThat(goal.getValue("kind").jsonPrimitive.content).isEqualTo("distance")
        assertThat(goal.getValue("sport").jsonPrimitive.content).isEqualTo("run")
        assertThat(goal.getValue("km").jsonPrimitive.double).isEqualTo(15.0)
        assertThat(goal.getValue("by").jsonPrimitive.content).isEqualTo("2026-12-13")
        assertThat(goal.getValue("weeks_to_go").jsonPrimitive.int).isEqualTo(14)
        assertThat(question.getValue("weeks").jsonPrimitive.int).isEqualTo(15)
        val steady = request(TrainerQuestion.Evaluate(ProgrammeAsk(4, 3, goal = PlanGoal.Steady), TEST_EPOCH_DAY - 3, null))
        val steadyGoal = Json.parseToJsonElement(userContent(Json.parseToJsonElement(TrainerPrompt.evaluateBody("a-model", steady)).jsonObject))
            .jsonObject.getValue("question").jsonObject.getValue("goal").jsonObject
        assertThat(steadyGoal.keys).containsExactly("kind")
        val schema = Json.parseToJsonElement(TrainerPrompt.evaluateBody("a-model", request, RequestProfile.DETERMINISTIC)).jsonObject
            .getValue("response_format").jsonObject.getValue("json_schema").jsonObject.getValue("schema").jsonObject
        assertThat(schema.getValue("required").jsonArray.map { it.jsonPrimitive.content }).contains("outline")
    }

    @Test
    fun `an outlined plan is sent with its outline and which weeks are filled`() {
        val question = Json.parseToJsonElement(TrainerPrompt.userMessage(fillRequest())).jsonObject.getValue("question").jsonObject

        assertThat(question.keys).containsExactly(
            "kind", "goal", "weeks", "sessions_a_week", "starts", "plan", "this_week_number", "week_to_fill", "filled_weeks",
            "done_by_week", "how_it_went",
        )
        assertThat(question.getValue("kind").jsonPrimitive.content).isEqualTo("fill_week")
        assertThat(question.getValue("week_to_fill").jsonPrimitive.int).isEqualTo(3)
        assertThat(question.getValue("this_week_number").jsonPrimitive.int).isEqualTo(2)
        assertThat(question.getValue("filled_weeks").jsonArray.map { it.jsonPrimitive.int }).containsExactly(1, 2).inOrder()
        val outline = question.getValue("plan").jsonObject.getValue("outline").jsonObject
        val first = outline.getValue("blocks").jsonArray.first().jsonObject
        assertThat(first.keys).containsExactly("phase", "focus", "weeks")
        assertThat(first.getValue("phase").jsonPrimitive.content).isEqualTo("base")
        assertThat(first.getValue("weeks").jsonArray.first().jsonObject.keys).containsExactly("minutes", "longest_minutes", "easier")

        val block = Json.parseToJsonElement(TrainerPrompt.userMessage(nextBlockRequest())).jsonObject.getValue("question").jsonObject
        assertThat(block.keys).containsExactly(
            "kind", "goal", "weeks", "sessions_a_week", "starts", "plan", "this_week_number", "filled_weeks", "done_by_week", "how_it_went",
        )
        assertThat(block.getValue("kind").jsonPrimitive.content).isEqualTo("next_block")
        assertThat(block.getValue("goal").jsonObject.getValue("kind").jsonPrimitive.content).isEqualTo("steady")
    }

    /** D127: a time goal is sent as stored — minutes and by — with the weeks to go counted by the phone. */
    @Test
    fun `a time goal is sent with its minutes, its date and its weeks to go`() {
        val request = request(TrainerQuestion.Evaluate(ProgrammeAsk(15, 3, goal = GOAL_150_MIN), TEST_EPOCH_DAY - 3, null))
        val question = Json.parseToJsonElement(TrainerPrompt.userMessage(request)).jsonObject.getValue("question").jsonObject
        val goal = question.getValue("goal").jsonObject

        assertThat(goal.keys).containsExactly("kind", "minutes", "by", "weeks_to_go").inOrder()
        assertThat(goal.getValue("kind").jsonPrimitive.content).isEqualTo("weekly_minutes")
        assertThat(goal.getValue("minutes").jsonPrimitive.int).isEqualTo(150)
        assertThat(goal.getValue("by").jsonPrimitive.content).isEqualTo("2026-12-13")
        assertThat(goal.getValue("weeks_to_go").jsonPrimitive.int).isEqualTo(14)
        assertThat(TrainerPrompt.goalJson(GOAL_150_MIN).keys).containsExactly("kind", "minutes", "by").inOrder()
    }

    /** D129: Plan ahead's instructions take a stand on the goal; no other path's do. Its schema requires goal_view. */
    @Test
    fun `Plan ahead's instructions take a stand on the goal`() {
        val system = TrainerPrompt.instructions(TrainerPath.EVALUATE)
        assertThat(system).contains(TrainerPrompt.GOAL_VIEW)
        assertThat(TrainerPrompt.GOAL_VIEW).contains("Take a stand on the goal before you plan for it.")
        assertThat(TrainerPrompt.GOAL_VIEW).contains("Be plain rather than polite about a big jump, and never pretend an unrealistic goal is fine.")
        assertThat(TrainerPrompt.GOAL_VIEW).contains("The plan is still for the goal as asked: they decide.")
        TrainerPath.entries.filter { it != TrainerPath.EVALUATE }.forEach { assertThat(TrainerPrompt.instructions(it)).doesNotContain(TrainerPrompt.GOAL_VIEW) }
        val schema = Json.parseToJsonElement(TrainerPrompt.evaluateBody("a-model", evaluateRequest(), RequestProfile.DETERMINISTIC)).jsonObject
            .getValue("response_format").jsonObject.getValue("json_schema").jsonObject.getValue("schema").jsonObject
        assertThat(schema.getValue("required").jsonArray.map { it.jsonPrimitive.content }).contains("goal_view")
        val view = schema.getValue("properties").jsonObject.getValue("goal_view").jsonObject
        assertThat(view.getValue("required").jsonArray.map { it.jsonPrimitive.content }).containsExactly("verdict", "says", "instead").inOrder()
    }

    /** D128: the plan paths, and suggesting goals, are told a distance goal is one session, not a total. */
    @Test
    fun `a distance goal is told as one session of that distance`() {
        listOf(TrainerPath.EVALUATE, TrainerPath.ADJUST, TrainerPath.FILL_WEEK, TrainerPath.NEXT_BLOCK, TrainerPath.SUGGEST_GOALS).forEach { path ->
            assertThat(TrainerPrompt.instructions(path)).contains("A distance goal is one session of that distance, not a total over the weeks.")
        }
    }

    /** D127: the plan paths are told how to build towards a time goal — reached in its week and held, no taper. */
    @Test
    fun `the plan paths carry the time goal's rule`() {
        listOf(TrainerPath.EVALUATE, TrainerPath.ADJUST, TrainerPath.FILL_WEEK, TrainerPath.NEXT_BLOCK).forEach { path ->
            val system = TrainerPrompt.instructions(path)
            assertThat(system).contains("weekly_minutes")
            assertThat(system).contains("reaching it in the goal's week and holding it there")
            assertThat(system).contains("no taper, there is no race")
        }
    }

    // --- D126: suggesting goals. Every figure is invented. ----------------------------------------------------

    /** D126: the ask carries the ledgers, the form's words and sessions a week, the window, the running goal and outline. */
    @Test
    fun `a suggest-goals request carries the form, the window, the running goal and the last evaluation`() {
        val request = request(
            TrainerQuestion.SuggestGoals(3, " Invented words. ", TEST_EPOCH_DAY + 4, LAST, RunningGoal(GOAL_15K, OUTLINE_15)),
        )
        val body = Json.parseToJsonElement(TrainerPrompt.suggestGoalsBody("a-model", request, RequestProfile.DETERMINISTIC)).jsonObject
        val user = Json.parseToJsonElement(userContent(body)).jsonObject
        val question = user.getValue("question").jsonObject

        assertThat(user.getValue("weeks").jsonArray).hasSize(6)
        assertThat(question.keys).containsExactly(
            "kind", "sessions_a_week", "words", "starts", "earliest_by", "latest_by", "running_plan", "last_evaluation",
        ).inOrder()
        assertThat(question.getValue("kind").jsonPrimitive.content).isEqualTo("suggest_goals")
        assertThat(question.getValue("sessions_a_week").jsonPrimitive.int).isEqualTo(3)
        assertThat(question.getValue("words").jsonPrimitive.content).isEqualTo("Invented words.")
        assertThat(question.getValue("earliest_by").jsonPrimitive.content).isEqualTo("2026-10-18")
        assertThat(question.getValue("latest_by").jsonPrimitive.content).isEqualTo("2027-04-04")
        val running = question.getValue("running_plan").jsonObject
        assertThat(running.getValue("goal").jsonObject.getValue("weeks_to_go").jsonPrimitive.int).isEqualTo(14)
        assertThat(running.getValue("outline").jsonObject.getValue("blocks").jsonArray).hasSize(4)
        assertThat(question.getValue("last_evaluation").jsonObject.keys).contains("how_it_went")
        assertThat(systemContent(TrainerPrompt.suggestGoalsBody("a-model", request, RequestProfile.DETERMINISTIC)))
            .isEqualTo(TrainerPrompt.instructions(TrainerPath.SUGGEST_GOALS))
        val schema = body.getValue("response_format").jsonObject.getValue("json_schema").jsonObject.getValue("schema").jsonObject
        assertThat(schema.getValue("required").jsonArray.map { it.jsonPrimitive.content }).containsExactly("suggestions", "week_note")

        val empty = Json.parseToJsonElement(
            TrainerPrompt.userMessage(request(TrainerQuestion.SuggestGoals(null, "", TEST_EPOCH_DAY + 4, null, null))),
        ).jsonObject.getValue("question").jsonObject
        assertThat(empty.getValue("sessions_a_week")).isEqualTo(JsonNull)
        assertThat(empty.getValue("running_plan")).isEqualTo(JsonNull)
        assertThat(empty.getValue("last_evaluation")).isEqualTo(JsonNull)
    }

    /** D126: three goals of differing kind, fitted to both ledgers, words first, within the conventions; not on the workbench. */
    @Test
    fun `the suggest-goals instructions ask for three goals of differing kind`() {
        val system = TrainerPrompt.instructions(TrainerPath.SUGGEST_GOALS)

        assertThat(system).contains("Suggest exactly three goals")
        assertThat(system).contains("they differ in kind")
        assertThat(system).contains("parts of each week counted")
        assertThat(system).contains("they weigh more than the record")
        assertThat(system).contains("a title of at most eight words")
        assertThat(system).contains("No figure the app did not count")
        assertThat(system).contains(TrainerPrompt.CONVENTIONS)
        assertThat(TrainerPath.WORKBENCH).doesNotContain(TrainerPath.SUGGEST_GOALS)
    }

    /** D125: the conventions go to the four plan paths, labelled as conventions; never to a session or its feedback. */
    @Test
    fun `the plan paths carry the coaching conventions as conventions, and the sum-near-target rule`() {
        listOf(TrainerPath.EVALUATE, TrainerPath.ADJUST, TrainerPath.FILL_WEEK, TrainerPath.NEXT_BLOCK).forEach { path ->
            val system = TrainerPrompt.instructions(path)
            assertThat(system).contains(TrainerPrompt.CONVENTIONS)
            assertThat(system).contains("not measured standards")
            assertThat(system).contains("sum near its target")
        }
        listOf(TrainerPath.PLAN, TrainerPath.FEEDBACK).forEach { assertThat(TrainerPrompt.instructions(it)).doesNotContain(TrainerPrompt.CONVENTIONS) }
        assertThat(TrainerPrompt.CONVENTIONS).contains("about a tenth")
        assertThat(TrainerPrompt.CONVENTIONS).contains("every fourth week")
        assertThat(TrainerPrompt.CONVENTIONS).contains("taper")
        assertThat(TrainerPrompt.instructions(TrainerPath.FILL_WEEK)).contains("target_why")
        assertThat(TrainerPrompt.instructions(TrainerPath.NEXT_BLOCK)).contains("four weeks")
    }

    private fun fillRequest() = request(
        TrainerQuestion.FillWeek(
            ProgrammeAsk(15, 3, goal = GOAL_15K), TEST_EPOCH_DAY - 10, OUTLINED, week = 2, weekIndex = 1, doneByWeek = listOf(3),
            howItWent = listOf(WEEK_ONE, WEEK_ONE.copy(week = 2)),
        ),
    )

    private fun nextBlockRequest() = request(
        TrainerQuestion.NextBlock(
            ProgrammeAsk(4, 3, goal = PlanGoal.Steady), TEST_EPOCH_DAY - 24,
            WEEKS.copy(weeks = WEEKS.weeks + List(2) { PlanWeek("", listOf(STEADY)) }, outline = STEADY_OUTLINE),
            weekIndex = 3, doneByWeek = listOf(3, 3, 2), howItWent = listOf(WEEK_ONE),
        ),
    )

    private fun adjustRequest() = request(
        TrainerQuestion.Adjust(
            ProgrammeAsk(4, 3), TEST_EPOCH_DAY - 10, WEEKS, weekIndex = 1, doneByWeek = listOf(3),
            tickedThisWeek = listOf(STEADY), thisWeekMax = 2, words = "Invented words.",
            howItWent = listOf(WEEK_ONE, WEEK_ONE.copy(week = 2)),
        ),
    )

    /** D147: a reply's request names its schema, and its question carries the conversation, reply_to and the plan. */
    @Test
    fun `a talk request carries the conversation oldest first, the message answered and the plan`() {
        val body = Json.parseToJsonElement(TrainerPrompt.talkBody("a-model", talkRequest(), RequestProfile.DETERMINISTIC)).jsonObject
        val format = body.getValue("response_format").jsonObject.getValue("json_schema").jsonObject

        assertThat(format.getValue("name").jsonPrimitive.content).isEqualTo("conversation_reply")
        assertThat(format.getValue("schema").jsonObject.getValue("properties").jsonObject.keys).containsExactly("reply")
        assertThat(systemContent(TrainerPrompt.talkBody("a-model", talkRequest(), RequestProfile.DETERMINISTIC)))
            .isEqualTo(TrainerPrompt.instructions(TrainerPath.TALK))
        val user = Json.parseToJsonElement(userContent(body)).jsonObject
        assertThat(userContent(body)).isEqualTo(TrainerPrompt.userMessage(talkRequest()))
        val question = user.getValue("question").jsonObject
        assertThat(question.getValue("kind").jsonPrimitive.content).isEqualTo("talk")
        val lines = question.getValue("conversation").jsonArray.map { it.jsonObject }
        assertThat(lines.map { it.keys }).containsExactly(LINE_KEYS, LINE_KEYS, LINE_KEYS, LINE_KEYS)
        assertThat(lines.map { it.getValue("kind").jsonPrimitive.content }).containsExactly("letter", "session_feedback", "message", "message").inOrder()
        assertThat(lines.map { it.getValue("from").jsonPrimitive.content }).containsExactly("you", "you", "them", "you").inOrder()
        assertThat(lines[0].getValue("week").jsonPrimitive.content).isEqualTo("2026-08-24")
        assertThat(lines[0].getValue("at").jsonPrimitive.content).isEqualTo("2026-08-30T20:00")
        assertThat(lines[1].getValue("session").jsonObject.getValue("kind").jsonPrimitive.content).isEqualTo("run")
        assertThat(lines[1].getValue("session").jsonObject.getValue("minutes").jsonPrimitive.int).isEqualTo(60)
        assertThat(lines[2].getValue("session")).isEqualTo(JsonNull)
        assertThat(lines[2].getValue("text").jsonPrimitive.content).isEqualTo("Invented question.")
        assertThat(question.getValue("reply_to").jsonObject.getValue("text").jsonPrimitive.content).isEqualTo("Invented question.")
        assertThat(question.getValue("reply_to").jsonObject.getValue("at").jsonPrimitive.content).isEqualTo("2026-09-03T08:30")
        val plan = question.getValue("plan").jsonObject
        assertThat(plan.getValue("this_week_number").jsonPrimitive.int).isEqualTo(2)
        assertThat(plan.getValue("starts").jsonPrimitive.content).isEqualTo(TrainerPrompt.date(TEST_EPOCH_DAY - 10))
        assertThat(plan.getValue("plan").jsonObject.getValue("title").jsonPrimitive.content).isEqualTo(WEEKS.title)
        assertThat(plan.keys).containsExactly("goal", "weeks", "sessions_a_week", "starts", "plan", "this_week_number", "filled_weeks")

        assertThat(question.getValue("week_note").jsonPrimitive.content).isEqualTo("Invented week note.")
        assertThat(question.keys).containsExactly("kind", "conversation", "reply_to", "plan", "week_note")

        val noPlan = talkRequest(plan = null, weekNote = null)
        val sent = Json.parseToJsonElement(TrainerPrompt.userMessage(noPlan)).jsonObject.getValue("question").jsonObject
        assertThat(sent.getValue("plan")).isEqualTo(JsonNull)
        assertThat(sent.getValue("week_note")).isEqualTo(JsonNull)
    }

    /** D147: the coach's rules for a reply, and the phrase the letter bans, banned in the same words. */
    @Test
    fun `the talk instructions hold the coach's rules and ban nobody asked for as the letter does`() {
        val system = TrainerPrompt.instructions(TrainerPath.TALK)

        listOf(
            "You are the same coach who writes their feedback after a session, their plans, the note on their week and the\nweekly letter.",
            "in a few sentences unless they ask for more",
            "Use the record's numbers and never invent one",
            "You may disagree with them. When you do, say so plainly and say why, from the record.",
            "You cannot change their plan, their goal or anything else stored on their phone from this conversation.",
            "tell them what to change and where",
            "never say or imply that you have changed it",
            "If reply_to mentions pain, dizziness or chest discomfort, tell them to stop and see a doctor",
            "Reply with reply: your answer.",
        ).forEach { assertThat(system).contains(it) }
        assertThat(system).contains(TrainerPrompt.OWN_CHOICE)
        assertThat(TrainerPrompt.OWN_CHOICE).contains("Never \"nobody asked for\"")
        // The letter's sentence, word for word (LetterPromptTest pins it in the letter's instructions).
        assertThat(TrainerPrompt.OWN_CHOICE).isEqualTo(
            "Sessions beyond the plan are their own choice, and are described so: \"of your own\", \"your own choice\". " +
                "Never \"nobody asked for\", \"unasked\", \"extra\" or anything that makes them sound unwanted.",
        )
        assertThat(system).contains(TrainerPrompt.WEEK_RULES)
        assertThat(system).doesNotContain(TrainerPrompt.CONVENTIONS)
        assertThat(system).doesNotContain(TrainerPrompt.GOAL_VIEW)
    }

    @Test
    fun `a talk body for another question is refused`() {
        assertThrows<IllegalArgumentException> { TrainerPrompt.talkBody("a-model", planRequest()) }
        assertThrows<IllegalArgumentException> { TrainerPrompt.planBody("a-model", talkRequest()) }
    }

    private fun talkRequest(
        plan: TalkPlan? = TalkPlan(ProgrammeAsk(4, 3), TEST_EPOCH_DAY - 10, WEEKS, weekIndex = 1),
        weekNote: String? = "Invented week note.",
    ): TrainerRequest {
        val asked = ConversationLine(LocalDateTime.of(2026, 9, 3, 8, 30), true, LineKind.MESSAGE, "Invented question.")
        return request(
            TrainerQuestion.Talk(
                conversation = listOf(
                    ConversationLine(LocalDateTime.of(2026, 8, 30, 20, 0), false, LineKind.LETTER, "Invented letter.", letterWeek = TEST_EPOCH_DAY - 10),
                    ConversationLine(LocalDateTime.of(2026, 9, 2, 9, 0), false, LineKind.SESSION_FEEDBACK, "Invented feedback.", WorkoutKind.RUN, 60),
                    asked,
                    ConversationLine(LocalDateTime.of(2026, 9, 3, 8, 31), false, LineKind.MESSAGE, "Invented answer."),
                ),
                replyTo = asked,
                plan = plan,
                weekNote = weekNote,
            ),
        )
    }

    // --- Fixtures, all invented --------------------------------------------------------------------

    private val walk = session(id = 1, day = TEST_EPOCH_DAY, kind = WorkoutKind.WALK, minutes = 40).copy(
        title = "Quillberry loop", distanceM = 3_000, energyKcal = 200, energySource = EnergySource.BAND,
        avgHeartRate = 110, maxHeartRate = 130, zoneSeconds = listOf(600, 1_200, 600, 0, 0), zoneMaxSource = "ESTIMATED",
    )
    private val strength = session(id = 2, day = TEST_EPOCH_DAY - 2, kind = WorkoutKind.STRENGTH, minutes = 45).copy(
        source = WorkoutSource.TYPED, energyKcal = 150, energySource = EnergySource.MET_ESTIMATE,
    )

    private val plan = TrainerPlan(
        id = 9, createdAtMillis = 0,
        answers = PlanAnswers(PlanActivity.OUTDOOR_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.EASY),
        plan = SessionPlan("Easy loop", listOf(PlanStep(0, 45, "Walk", "easy pace")), "Invented."),
        model = "a-model", kept = false,
    )
    private val review = TrainerReview(id = 1, workoutId = 1, planId = 9, felt = Felt.RIGHT, words = "Invented words.")

    private fun planRequest() = request(
        TrainerQuestion.Plan(PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_30, Feeling.FRESH, Wish.PUSH)),
    )

    private fun reviewRequest() = request(TrainerRequest.reviewQuestion(walk, review, plan))

    /**
     * Two days this week, each with its own sleep, resting heart rate and HRV, so the week's averages (430
     * minutes, 58, 45 ms) differ from every single day's; one of them also holds sleep stages, oxygen and a
     * breathing rate, which never go. Two days of food, whose averages (2,100 kcal, 110 g) differ from each day's.
     */
    private fun request(question: TrainerQuestion, earlier: List<Feedback> = emptyList(), weeklyPlans: List<CountedPlan> = emptyList()) =
        TrainerRequest.of(
            question = question, today = TEST_EPOCH_DAY, workouts = listOf(walk, strength), reviews = listOf(review),
            plans = mapOf(9L to plan),
            days = listOf(
                HealthDay(
                    TEST_EPOCH_DAY, distanceM = 4_000, activeKcal = 300, sleepMinutes = 410, restingHeartRate = 55, hrvMs = 37.0,
                    deepMinutes = 95, oxygenPct = 96.5, respiratoryRate = 14.5,
                ),
                HealthDay(TEST_EPOCH_DAY - 1, sleepMinutes = 450, restingHeartRate = 61, hrvMs = 53.0),
            ),
            food = mapOf(TEST_EPOCH_DAY - 1 to DayTotals(1_900, 94, 200, 70), TEST_EPOCH_DAY to DayTotals(2_300, 126, 200, 70)),
            targetKcal = 2_000,
            weeklyPlans = weeklyPlans,
            readings = listOf(
                WeightReading(TEST_EPOCH_DAY - 28, 80.4), WeightReading(TEST_EPOCH_DAY - 14, 79.2), WeightReading(TEST_EPOCH_DAY, 78.6),
            ),
            profile = aProfile(weightKg = 83.0, goal = Goal.lose(0.5, targetKg = 71.5)),
            currentYear = TEST_YEAR, earlierFeedback = earlier,
        )

    /** [planRequest] with a kept plan of two weeks of two steady walks, begun this Monday: the walk ticks one. */
    private fun ledgerRequest() = request(
        TrainerQuestion.Plan(PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_30, Feeling.FRESH, Wish.PUSH)),
        weeklyPlans = listOf(
            CountedPlan(
                Programme(
                    1, 0, ProgrammeAsk(2, 2), null,
                    WeeksPlan("Invented plan", List(2) { PlanWeek("Invented focus", listOf(STEADY, STEADY)) }, "Invented."),
                    "a-model", TEST_EPOCH_DAY - 3, ProgrammeStatus.RUNNING,
                ),
                PlanCounting(1, 0, emptyMap()),
            ),
        ),
    )

    /**
     * [planRequest] with a year behind it (D89, D90): the record begins on 1 June 2026; June has one
     * 50-minute walk of 4 km, heart 120, felt hard, under the same title as the recent walk; two June days with
     * steps and distance, one of them with a night's sleep and the day's resting heart rate, outside every
     * week's 28 days; weigh-ins at both ends
     * of June. And the owner's note.
     */
    private fun yearRequest(): TrainerRequest {
        val juneWalk = session(id = 3, day = JUNE_1 + 9, kind = WorkoutKind.WALK, minutes = 50).copy(
            title = "Quillberry loop", distanceM = 4_000, avgHeartRate = 120,
        )
        return TrainerRequest.of(
            question = TrainerQuestion.Plan(PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_30, Feeling.FRESH, Wish.PUSH)),
            today = TEST_EPOCH_DAY, workouts = listOf(walk, strength, juneWalk),
            reviews = listOf(review, TrainerReview(id = 2, workoutId = 3, planId = null, felt = Felt.HARD, words = "Invented.")),
            plans = mapOf(9L to plan),
            days = listOf(
                HealthDay(TEST_EPOCH_DAY, distanceM = 4_000, activeKcal = 300),
                HealthDay(JUNE_1 + 9, steps = 6_000, distanceM = 6_000, sleepMinutes = 470, restingHeartRate = 64),
                HealthDay(JUNE_1 + 10, steps = 8_000),
            ),
            food = emptyMap(),
            targetKcal = 2_000,
            weeklyPlans = emptyList(),
            readings = listOf(WeightReading(JUNE_1 - 1, 82.0), WeightReading(JUNE_1 + 29, 80.0)),
            profile = aProfile(weightKg = 83.0, goal = Goal.lose(0.5, targetKg = 71.5)),
            currentYear = TEST_YEAR, earlierFeedback = emptyList(),
            earliestDay = JUNE_1, aboutMe = NOTE,
        )
    }

    private fun session(id: Long, day: Long, kind: WorkoutKind, minutes: Int) = Workout(
        id = id, epochDay = day, startedAtMillis = day * 86_400_000L + 7 * 3_600_000L, durationMinutes = minutes,
        kind = kind, title = null, distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private fun messages(body: JsonObject) = body.getValue("messages").jsonArray.map { it.jsonObject }

    private fun userContent(body: JsonObject): String =
        messages(body).first { it.getValue("role").jsonPrimitive.content == "user" }.getValue("content").jsonPrimitive.content

    private fun systemContent(body: String): String =
        messages(Json.parseToJsonElement(body).jsonObject)
            .first { it.getValue("role").jsonPrimitive.content == "system" }.getValue("content").jsonPrimitive.content

    private fun sentSessions(request: TrainerRequest): List<JsonObject> {
        val body = Json.parseToJsonElement(TrainerPrompt.planBody("a-model", request, RequestProfile.DETERMINISTIC)).jsonObject
        return Json.parseToJsonElement(userContent(body)).jsonObject.getValue("sessions").jsonArray.map { it.jsonObject }
    }

    private companion object {
        val SHEET = listOf(SheetFact(1, FactGroup.GOALS, "An invented fact.", TEST_EPOCH_DAY - 5, FactSource.SAID, edited = true))
        /** D147: what each line of the conversation is sent with, and nothing else. */
        val LINE_KEYS = setOf("at", "from", "kind", "session", "week", "text")

        /** D112's five rules, as fragments each on one line of the text. */
        val RULES = listOf(
            "Both parts, every time.",
            "never excuse it with other activity",
            "Lead with what matters most this week, whichever part it is in.",
            "is itself worth saying, and is a reason to suggest more.",
            "Recovery shapes intensity.",
            "is a reason for an easier session or a rest day; say so. Do not diagnose.",
            "Food is context.",
            "Never prescribe a diet, a food or an amount.",
            "No figure the app did not count.",
            "you may compare two of them, but never estimate one.",
        )
        const val LOADED = "Invented instructions: a short note from a coach."
        val JUNE_1 = java.time.LocalDate.of(2026, 6, 1).toEpochDay()
        const val NOTE = "Invented note: an old knee injury, so no running downhill.\nWalks before work."
        val STEADY = PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Steady walk")
        /** The goal fixture's fifteen weeks, the first two filled. */
        val OUTLINED: WeeksPlan by lazy { WEEKS.copy(weeks = WEEKS.weeks + List(13) { PlanWeek("", emptyList()) }, outline = OUTLINE_15) }
        val WEEKS = WeeksPlan(
            "Invented plan",
            listOf(PlanWeek("settle in", listOf(STEADY, STEADY, STEADY)), PlanWeek("a little longer", listOf(STEADY, STEADY, STEADY))),
            "Invented reason.",
        )
        /** D105, invented: two done, one done shorter and confirmed; an attempt of ten minutes against forty. */
        val WEEK_ONE = WeekOutcome(
            1,
            listOf(
                SessionOutcome(STEADY, PlannedOutcome.DONE, 40),
                SessionOutcome(STEADY, PlannedOutcome.DONE_SHORT, 30),
                SessionOutcome(STEADY, PlannedOutcome.NOT_DONE, null),
            ),
            listOf(AttemptFacts(WorkoutKind.WALK, 10, 40)),
        )
        val LAST = LastEvaluation(
            TEST_EPOCH_DAY - 30, Evaluation("Invented.", "Invented.", "Invented.", ""), WEEKS, listOf(3, 2),
            listOf(WEEK_ONE, WEEK_ONE.copy(week = 2, attempts = emptyList())),
        )
    }
}
