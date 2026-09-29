package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.letter.FoodWeek
import com.metaself.app.domain.letter.LetterFigures
import com.metaself.app.domain.letter.LetterRequest
import com.metaself.app.domain.letter.MovementFigures
import com.metaself.app.domain.letter.PlanWeekFigures
import com.metaself.app.domain.letter.WeekFigures
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.profile.Sex
import com.metaself.app.domain.trainer.BodyFacts
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.GoalFacts
import com.metaself.app.domain.trainer.Origin
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.SessionFacts
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

/** What the letter sends (D101), and how it is asked to write (D102). Every figure and word is invented. */
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

    private fun request() = LetterRequest(
        figures = LetterFigures(week(monday), List(4) { week(monday - 7L * (it + 1)) }, 2_100),
        sessions = listOf(session),
        aboutMe = "Invented note.",
        goal = GoalFacts(GoalDirection.LOSE, 0.5),
        body = BodyFacts(46, Sex.MALE, 180),
        planTitle = "Invented plan",
        planWeek = PlanWeek("Invented focus", listOf(PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Walk"))),
        lastNextWeek = "Invented line.",
    )

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
        assertThat(user.keys).containsExactly(
            "week", "earlier_weeks", "target_kcal", "sessions", "about_me", "goal", "body", "plan", "last_next_week",
        )
        assertThat(user.getValue("earlier_weeks").jsonArray).hasSize(4)
    }

    /**
     * D101's "never sent", as the whole shape: food only as a week's averages and a count of days; the
     * weight only as the trend's change across a week; a session's figures without the owner's words,
     * felt or plan; no sleep, no meal, no food, no amount, no time of eating, no single weigh-in. A new
     * key anywhere fails here before it can leave the phone.
     */
    @Test
    fun `nothing else is sent`() {
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
        val expected = listOf("week", "earlier_weeks", "target_kcal", "sessions", "about_me", "goal", "body", "plan", "last_next_week") +
            week.map { "week.$it" } + week.map { "earlier_weeks[].$it" } + session.map { "sessions[].$it" } +
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
    fun `the request has room for exactly what D101 lists`() {
        assertThat(fieldsOf(LetterRequest::class.java)).containsExactly(
            "figures", "sessions", "aboutMe", "goal", "body", "planTitle", "planWeek", "lastNextWeek",
        )
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

    /** D102's tone rules, written into the instructions word for word. */
    @Test
    fun `the tone rules are in the instructions`() {
        val system = system(body())

        listOf(
            "Name the effort behind a result, not only the result (\"you made room for it on the days it wasn't convenient\"), in the second person, plainly.",
            "Compare with his own earlier weeks, never with other people or ideals.",
            "One thing to look at, framed as information or a next step, never as a failure; a gap in logging is missing information, not a fault. Say when a figure rests on few days and cannot say much.",
            "Never shame, never exaggerate, no exclamation marks, no emoji. Every figure mentioned must be one given.",
            "The close is one warm sentence about the week or the person, not a slogan.",
            "Nothing medical is diagnosed.",
        ).forEach { assertThat(system).contains(it) }
    }

    @Test
    fun `the reply is asked for as six texts`() {
        assertThat(LetterPrompt.NAMES).containsExactly("headline", "effort", "progress", "look_at", "next_week", "close").inOrder()
        val strictless = LetterPrompt.body("a-model", request(), RequestProfile.DETERMINISTIC.copy(strictFormat = false))
        LetterPrompt.NAMES.forEach { assertThat(system(strictless)).contains(it) }
    }

    private fun fieldsOf(type: Class<*>): List<String> =
        type.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }
}
