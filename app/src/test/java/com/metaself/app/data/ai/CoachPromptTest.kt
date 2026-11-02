package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.profile.Sex
import com.metaself.app.domain.trainer.BodyFacts
import com.metaself.app.domain.trainer.CoachRequest
import com.metaself.app.domain.trainer.ConversationLine
import com.metaself.app.domain.trainer.Drawer
import com.metaself.app.domain.trainer.DrawerAsk
import com.metaself.app.domain.trainer.FactGroup
import com.metaself.app.domain.trainer.FactSource
import com.metaself.app.domain.trainer.FrontPage
import com.metaself.app.domain.trainer.GoalFacts
import com.metaself.app.domain.trainer.LineKind
import com.metaself.app.domain.trainer.OpenedDrawer
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.RecordBegins
import com.metaself.app.domain.trainer.Rhythm
import com.metaself.app.domain.trainer.SheetFact
import com.metaself.app.domain.trainer.TalkPlan
import com.metaself.app.domain.trainer.TrainerPath
import com.metaself.app.domain.trainer.WeeksPlan
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/** D161, D163: what leaves the phone for a reply. Every word and figure is invented. */
class CoachPromptTest {

    private val asked = ConversationLine(LocalDateTime.of(2026, 9, 3, 8, 30), true, LineKind.MESSAGE, "Invented question.", thisConversation = true)
    private val page = FrontPage(
        today = TEST_EPOCH_DAY,
        goal = GoalFacts(GoalDirection.LOSE, 0.5),
        plan = TalkPlan(
            ProgrammeAsk(2, 2),
            TEST_EPOCH_DAY - 7,
            WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented."))) }, "Invented reason."),
            weekIndex = 1,
        ),
        thisWeek = Rhythm(2, 3),
        weekNote = "Invented note.",
        aboutMe = "Invented about me.",
        sheet = listOf(SheetFact(1, FactGroup.BODY, "An invented ache.", TEST_EPOCH_DAY - 4, FactSource.SAID)),
        body = BodyFacts(46, Sex.MALE, 180),
        floorKcal = 1_600,
        targetKcal = 2_100,
        recordBegins = RecordBegins(TEST_EPOCH_DAY - 90, TEST_EPOCH_DAY - 60, null, TEST_EPOCH_DAY - 5),
    )

    private fun request(opened: List<OpenedDrawer> = emptyList(), look: Int = 1) = CoachRequest(asked, listOf(asked), page, opened, look)

    private fun sent(request: CoachRequest): JsonObject = Json.parseToJsonElement(CoachPrompt.userMessage(request)).jsonObject

    @Test
    fun `a request holds exactly the question, the front page, the drawers and what was opened`() {
        val message = sent(request())

        assertThat(message.keys).containsExactly("question", "front_page", "drawers", "opened")
        assertThat(message.getValue("question").jsonObject.keys).containsExactly("reply_to", "conversation", "look", "last_look")
        assertThat(message.getValue("front_page").jsonObject.keys).containsExactly(
            "today", "goal", "plan", "this_week", "week_note", "about_me", "sheet", "body", "floor_kcal", "target_kcal", "record_begins",
        )
    }

    @Test
    fun `the front page says the floor, the target, the whole sheet and how far back the record goes`() {
        val front = sent(request()).getValue("front_page").jsonObject

        assertThat(front.getValue("floor_kcal").jsonPrimitive.content).isEqualTo("1600")
        assertThat(front.getValue("target_kcal").jsonPrimitive.content).isEqualTo("2100")
        assertThat(front.getValue("sheet").jsonArray.single().jsonObject.getValue("text").jsonPrimitive.content).isEqualTo("An invented ache.")
        assertThat(front.getValue("record_begins").jsonObject.getValue("food").jsonPrimitive.content).isEqualTo("2026-07-05")
        assertThat(front.getValue("plan").jsonObject.getValue("this_week_number").jsonPrimitive.content).isEqualTo("2")
    }

    @Test
    fun `every drawer is listed, by the name the phone knows, in every request`() {
        val names = sent(request()).getValue("drawers").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content }

        assertThat(names).containsExactlyElementsIn(Drawer.entries.map { it.wire }).inOrder()
        assertThat(names).containsExactly("sessions", "days", "food_days", "meals", "habits", "weight", "plan", "conversations")
    }

    @Test
    fun `what was opened goes back with its question, and the last look says so`() {
        val opened = OpenedDrawer(DrawerAsk("meals", "2026-09-01", "2026-09-02", ""), """{"truncated":false}""", null)
        val message = sent(request(listOf(opened), look = 3))

        val first = message.getValue("opened").jsonArray.single().jsonObject
        assertThat(first.getValue("drawer").jsonPrimitive.content).isEqualTo("meals")
        assertThat(first.getValue("answer").jsonObject.getValue("truncated").jsonPrimitive.content).isEqualTo("false")
        assertThat(message.getValue("question").jsonObject.getValue("last_look").jsonPrimitive.content).isEqualTo("true")
        assertThat(sent(request()).getValue("question").jsonObject.getValue("last_look").jsonPrimitive.content).isEqualTo("false")
    }

    @Test
    fun `the reply's shape is the strict schema of open, reply, remember and propose`() {
        val body = Json.parseToJsonElement(CoachPrompt.body("a-model", request(), profile = RequestProfile.DETERMINISTIC)).jsonObject

        val format = body.getValue("response_format").jsonObject.getValue("json_schema").jsonObject
        assertThat(format.getValue("name").jsonPrimitive.content).isEqualTo("coach_reply")
        assertThat(format.getValue("strict").jsonPrimitive.content).isEqualTo("true")
        val properties = format.getValue("schema").jsonObject.getValue("properties").jsonObject
        assertThat(properties.keys).containsExactly("open", "reply", "remember", "propose")
        // D163, D164: the only changes that can be named; none is the calorie target, a food record or a weight.
        val propose = properties.getValue("propose").jsonObject.getValue("properties").jsonObject
        assertThat(propose.getValue("kind").jsonObject.getValue("enum").jsonArray.map { it.jsonPrimitive.content })
            .containsExactly("none", "change_plan", "new_plan", "stop_plan", "fill_week", "next_block", "add_fact", "change_fact", "remove_fact")
        val open = properties.getValue("open").jsonObject.getValue("items").jsonObject.getValue("properties").jsonObject
        assertThat(open.keys).containsExactly("drawer", "from", "to", "words")
        val remember = properties.getValue("remember").jsonObject.getValue("items").jsonObject.getValue("properties").jsonObject
        assertThat(remember.keys).containsExactly("group", "text")
        assertThat(body.getValue("messages").jsonArray.first().jsonObject.getValue("content").jsonPrimitive.content).isEqualTo(CoachPrompt.INSTRUCTIONS)
    }

    @Test
    fun `a model with no strict format is told the shape, and loaded instructions replace the app's`() {
        val loose = CoachPrompt.body("a-model", request(), profile = RequestProfile.DETERMINISTIC.copy(strictFormat = false))
        assertThat(loose).contains("json_object")
        assertThat(loose).contains("Reply with one JSON object")

        val body = Json.parseToJsonElement(CoachPrompt.body("a-model", request(), "Loaded.", RequestProfile.DETERMINISTIC)).jsonObject
        assertThat(body.getValue("messages").jsonArray.first().jsonObject.getValue("content").jsonPrimitive.content).isEqualTo("Loaded.")
    }

    @Test
    fun `the conversation path's instructions are the coach's`() {
        assertThat(TrainerPrompt.instructions(TrainerPath.TALK)).isEqualTo(CoachPrompt.INSTRUCTIONS)
    }

    /** D163: each rule the design names, in the words sent. These are instructions and can fail; that is said in the spec. */
    @Test
    fun `the instructions hold the coach's rules`() {
        val system = oneLine(CoachPrompt.INSTRUCTIONS)

        assertThat(system).contains("open their file before you answer")
        assertThat(system).contains("Never invent a fact about them; general knowledge is fine")
        assertThat(system).contains("say so and ask them")
        assertThat(system).contains("Not logged is not zero, and a gap is not a fast")
        assertThat(system).contains("Every figure you state comes from what you are given")
        assertThat(system).contains("Never advise eating below front_page.floor_kcal")
        assertThat(system).contains("Never call it breakfast, lunch or dinner")
        assertThat(system).contains("medication, allergies, and medical conditions")
        assertThat(system).contains("after answering what they asked and never instead of it")
        assertThat(system).contains("nothing changes until they press it")
        assertThat(system).contains("never say or imply that you have changed anything")
        assertThat(CoachPrompt.INSTRUCTIONS).contains(TrainerPrompt.OWN_CHOICE)
        assertThat(CoachPrompt.INSTRUCTIONS).doesNotContain(TrainerPrompt.WEEK_NOTE)
    }

    @Test
    fun `the warning signs come before anything else, and under-eating and exhaustion are among them`() {
        val system = oneLine(CoachPrompt.INSTRUCTIONS)

        assertThat(system).contains("pain, dizziness or chest discomfort, tell them to stop and see a doctor before saying anything else")
        assertThat(system).contains("eating far too little or are exhausted")
        assertThat(system.indexOf("First, safety.")).isLessThan(system.indexOf("You are given question.reply_to"))
    }

    private fun oneLine(text: String) = text.replace(Regex("\\s+"), " ")
}
