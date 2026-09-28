package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.PlanStep
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.Wish
import com.metaself.app.domain.weight.WeightReading
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * What the trainer sends (D84) — the twin of [EstimatePromptTest]'s "nothing about the person is
 * sent". Every figure, date, title and word is invented; the weigh-in, the target, the sleep and the
 * resting heart rate below are here only so the test can prove they stay on the phone.
 */
class TrainerPromptTest {

    @Test
    fun `a plan request names its schema and holds exactly D84's parts`() {
        val body = Json.parseToJsonElement(TrainerPrompt.planBody("a-model", planRequest(), RequestProfile.DETERMINISTIC)).jsonObject
        val user = Json.parseToJsonElement(userContent(body)).jsonObject

        assertThat(body.toString()).contains("session_plan")
        assertThat(user.keys).containsExactly(
            "question", "today", "about_me", "sessions", "weeks", "months", "weight", "goal", "body", "this_week",
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
     * D84's "never sent". If this fails because the prompt's own wording used a word, rephrase the prompt.
     *
     * Each category has at least one value the fixtures below really hold: the title "Quillberry loop";
     * the day's sleep (430) and resting heart rate (58); the weigh-ins (80.4, 79.2, 78.6, none equal to
     * the smoothed line) and the profile's weight (83.0); the target (71.5). [yearRequest] adds a month
     * whose session has the same title, whose day has the same sleep and resting heart rate, and whose
     * weigh-ins (82.0, 80.0) must stay on the phone too (D89). The words ("sleep",
     * "meal", ...) and the package name have no path into a request; they stay as a guard on the prompt.
     */
    @Test
    fun `no meal, sleep, weigh-in, target, title or app name is sent`() {
        listOf(
            TrainerPrompt.planBody("a-model", planRequest()),
            TrainerPrompt.feedbackBody("a-model", reviewRequest()),
            TrainerPrompt.planBody("a-model", yearRequest()),
        )
            .map { it.lowercase() }
            .forEach { body ->
                listOf(
                    "quillberry", "com.example", "sleep", "slept", "meal", "eaten", "breakfast",
                    "83.0", "80.4", "79.2", "78.6", "71.5", "430", "58", "resting", "82.0", "80.0",
                ).forEach { forbidden -> assertThat(body).doesNotContain(forbidden) }
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
        listOf(TrainerPrompt.feedbackBody("a-model", reviewRequest()), TrainerPrompt.planBody("a-model", planRequest()))
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
        listOf(TrainerPrompt.feedbackBody("a-model", reviewRequest()), TrainerPrompt.planBody("a-model", planRequest()))
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

    @Test
    fun `a plan is asked for as three to six steps with a minute range, what and how`() {
        val system = systemContent(TrainerPrompt.planBody("a-model", planRequest()))

        assertThat(system).contains("three to six steps")
        assertThat(system).contains("from_minute")
    }

    @Test
    fun `each body is built one way, from one request`() {
        listOf("planBody", "feedbackBody").forEach { name ->
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

    private fun request(question: TrainerQuestion) = TrainerRequest.of(
        question = question, today = TEST_EPOCH_DAY, workouts = listOf(walk, strength), reviews = listOf(review),
        plans = mapOf(9L to plan),
        days = listOf(HealthDay(TEST_EPOCH_DAY, distanceM = 4_000, activeKcal = 300, sleepMinutes = 430, restingHeartRate = 58)),
        readings = listOf(
            WeightReading(TEST_EPOCH_DAY - 28, 80.4), WeightReading(TEST_EPOCH_DAY - 14, 79.2), WeightReading(TEST_EPOCH_DAY, 78.6),
        ),
        profile = aProfile(weightKg = 83.0, goal = Goal.lose(0.5, targetKg = 71.5)),
        currentYear = TEST_YEAR, earlierFeedback = emptyList(),
    )

    /**
     * [planRequest] with a year behind it (D89, D90): the record begins on 1 June 2026; June has one
     * 50-minute walk of 4 km, heart 120, felt hard, under the same title as the recent walk; two days with
     * steps and distance, one of them with the day's sleep and resting heart rate; weigh-ins at both ends
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
                HealthDay(TEST_EPOCH_DAY, distanceM = 4_000, activeKcal = 300, sleepMinutes = 430, restingHeartRate = 58),
                HealthDay(JUNE_1 + 9, steps = 6_000, distanceM = 6_000, sleepMinutes = 430, restingHeartRate = 58),
                HealthDay(JUNE_1 + 10, steps = 8_000),
            ),
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
        val JUNE_1 = java.time.LocalDate.of(2026, 6, 1).toEpochDay()
        const val NOTE = "Invented note: an old knee injury, so no running downhill.\nWalks before work."
    }
}
