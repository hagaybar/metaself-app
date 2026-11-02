package com.metaself.app.data.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.letter.FakeLetterStore
import com.metaself.app.data.letter.aWeeklyLetter
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.trainer.CoachAnswer
import com.metaself.app.domain.trainer.Conversation
import com.metaself.app.domain.trainer.DrawerAsk
import com.metaself.app.domain.trainer.RememberedFact
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.LineKind
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.FactGroup
import com.metaself.app.domain.trainer.FactSource
import com.metaself.app.domain.trainer.SheetFact
import com.metaself.app.domain.trainer.SheetMadeBy
import com.metaself.app.domain.trainer.ThreadItem
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.WeekNote
import com.metaself.app.domain.trainer.WeekNoteSource
import com.metaself.app.domain.trainer.WeeksPlan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * The conversation (D145–D147), over in-memory stores. Now is 15:00 on [TEST_EPOCH_DAY], UTC; every word, figure
 * and session is invented.
 */
class TalkToTheTrainerTest {

    private val record = FakeMovementRecord()
    private val store = FakeTrainerStore()
    private val coach = FakeCoachModel()
    private val programmes = FakeProgrammeStore()
    private val messages = InMemoryConversationStore()
    private val letters = FakeLetterStore()
    private val weekNotes = InMemoryWeekNoteStore()
    private val sheets = InMemorySheetStore()
    private var now = NOW

    @Test
    fun `a message is stored, asked about once, and its reply stored against it`() = runTest {
        coach.says("Invented reply.")

        val id = talk().say("  Invented question.  ", "talk:1")
        val outcome = talk().answer(id)

        assertThat(outcome).isEqualTo(TalkToTheTrainer.Talked.Replied)
        val stored = messages.observe().first()
        assertThat(stored.map { it.text }).containsExactly("Invented question.", "Invented reply.").inOrder()
        assertThat(stored[0].answered).isTrue()
        assertThat(stored[1].fromOwner).isFalse()
        assertThat(stored[1].model).isEqualTo("a-model")
        assertThat(stored[1].replyTo).isEqualTo(id)
        val request = coach.asked.single()
        assertThat(request.replyTo.text).isEqualTo("Invented question.")
        assertThat(request.conversation.map { it.text }).containsExactly("Invented question.")
    }

    /** D145: a message is never lost because a request failed; Try again sends it again. */
    /** D161: a reply's front page holds the whole sheet. */
    @Test
    fun `a reply carries the newest sheet`() = runTest {
        val fact = SheetFact(1, FactGroup.LIFE, "An invented fact.", TEST_EPOCH_DAY - 4, FactSource.NOTICED)
        sheets.add(NOW - HOUR, listOf(fact), SheetMadeBy.REWRITE, "a-model")
        coach.says("Invented reply.")

        talk().answer(talk().say("Invented question.", "talk:1"))

        assertThat(coach.asked.single().frontPage.sheet).containsExactly(fact)
    }

    @Test
    fun `a failed request keeps the message not answered, and trying again answers it`() = runTest {
        coach.looks += TrainerReply.Failed(EstimateResult.Unreachable())
        val id = talk().say("Invented question.", "talk:1")

        assertThat(talk().answer(id)).isEqualTo(TalkToTheTrainer.Talked.Failed(EstimateResult.Unreachable()))
        assertThat(messages.observe().first().single().answered).isFalse()

        now += 60_000
        coach.says("Invented reply.")
        assertThat(talk().answer(id)).isEqualTo(TalkToTheTrainer.Talked.Replied)

        assertThat(coach.asked).hasSize(2)
        assertThat(coach.asked.last().replyTo.text).isEqualTo("Invented question.")
        assertThat(messages.observe().first().map { it.answered }).containsExactly(true, false).inOrder()
        // Answered once: asking again asks nothing.
        assertThat(talk().answer(id)).isEqualTo(TalkToTheTrainer.Talked.NothingToAnswer)
        assertThat(coach.asked).hasSize(2)
    }

    @Test
    fun `a blank message is refused, and a long one is cut`() = runTest {
        assertThat(runCatching { talk().say("   ", "talk:1") }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        val id = talk().say("a".repeat(2_500), "talk:1")
        assertThat(messages.message(id)!!.text).hasLength(Conversation.MAX_MESSAGE)
    }

    /** D145: deleting his message removes the reply it got, and nothing else. */
    @Test
    fun `deleting a message removes the reply it got`() = runTest {
        coach.says("Invented first reply.")
        coach.says("Invented second reply.")
        val first = talk().say("Invented first.", "talk:1")
        talk().answer(first)
        now += 60_000
        val second = talk().say("Invented second.", "talk:1")
        talk().answer(second)

        talk().delete(first)

        assertThat(messages.observe().first().map { it.text }).containsExactly("Invented second.", "Invented second reply.").inOrder()
    }

    /** D146: feedback and letters show in the thread, in their place, and are never copied into the messages. */
    @Test
    fun `feedback and letters appear in the thread by time, without being copied`() = runTest {
        store.workouts.value = listOf(run(id = 4))
        store.reviews.value = listOf(
            TrainerReview(1, workoutId = 4, planId = null, felt = null, words = null, feedback = FEEDBACK, feedbackAtMillis = NOW - 2 * HOUR, model = "a-model"),
            // Words without feedback are not the trainer's: they do not show.
            TrainerReview(2, workoutId = 5, planId = null, felt = null, words = "Invented."),
        )
        letters.letters.value = listOf(aWeeklyLetter(weekMonday = MONDAY - 7).copy(createdAtMillis = NOW - 3 * HOUR))
        val id = talk().say("Invented question.", "talk:1")

        val thread = talk().observeThread().first()

        assertThat(thread.map { it::class }).containsExactly(ThreadItem.Letter::class, ThreadItem.AfterSession::class, ThreadItem.Said::class).inOrder()
        val feedback = thread[1] as ThreadItem.AfterSession
        assertThat(feedback.kind).isEqualTo(WorkoutKind.RUN)
        assertThat(feedback.minutes).isEqualTo(60)
        assertThat(messages.observe().first().map { it.id }).containsExactly(id)

        coach.says("Invented reply.")
        talk().answer(id)
        val sent = coach.asked.single().conversation
        assertThat(sent.map { it.kind }).containsExactly(LineKind.LETTER, LineKind.SESSION_FEEDBACK, LineKind.MESSAGE).inOrder()
        assertThat(sent[1].text).isEqualTo("Invented headline.\n\nInvented note.")
    }

    /** D147: the conversation sent is the window — nothing older than 14 days, the oldest dropped past the cap. */
    @Test
    fun `the request carries the last 14 days, the oldest dropped past the cap`() = runTest {
        messages.addOwn("Invented, fifteen days ago.", NOW - 15 * DAY, "talk:0")
        messages.addOwn("b".repeat(1_000), NOW - 3 * DAY, "talk:0")
        repeat(12) { messages.addOwn("c".repeat(2_000), NOW - 2 * DAY + it, "talk:0") }
        val id = talk().say("Invented question.", "talk:1")
        coach.says("Invented reply.")

        talk().answer(id)

        val sent = coach.asked.single().conversation
        // 12 × 2,000 + 18 = 24,018 is over the cap: the oldest 2,000-character one goes, and the 1,000 before it.
        assertThat(sent).hasSize(12)
        assertThat(sent.first().text).isEqualTo("c".repeat(2_000))
        assertThat(sent.last().text).isEqualTo("Invented question.")
        assertThat(sent.sumOf { it.text.length }).isAtMost(Conversation.MAX_CHARS)
    }

    @Test
    fun `the running plan, its week and the week's note are on the front page`() = runTest {
        val id = programmes.add(Programme(0, (MONDAY - 15) * DAY + 12 * HOUR, ProgrammeAsk(2, 2), EVALUATION, WEEKS, "a-model"))
        programmes.keep(id, MONDAY - 7, TEST_EPOCH_DAY - 7)
        weekNotes.note.value = WeekNote("Invented week note.", NOW - HOUR, MONDAY, WeekNoteSource.TRAINER)
        coach.says("Invented reply.")

        talk().answer(talk().say("Invented question.", "talk:1"))

        val page = coach.asked.single().frontPage
        assertThat(page.plan!!.weekIndex).isEqualTo(1)
        assertThat(page.plan!!.startEpochDay).isEqualTo(MONDAY - 7)
        assertThat(page.weekNote).isEqualTo("Invented week note.")
    }

    @Test
    fun `an old note and no plan are not on the front page`() = runTest {
        weekNotes.note.value = WeekNote("Invented old note.", NOW - 8 * DAY, MONDAY - 7, WeekNoteSource.TRAINER)
        coach.says("Invented reply.")

        talk().answer(talk().say("Invented question.", "talk:1"))

        assertThat(coach.asked.single().frontPage.plan).isNull()
        assertThat(coach.asked.single().frontPage.weekNote).isNull()
    }

    /** D159: the box begins a new conversation each time; a reply stays in its message's. */
    @Test
    fun `the box begins a new conversation, and a reply stays in it`() = runTest {
        coach.says("Invented reply.")
        val (first, asked) = talk().begin("Invented question.")
        talk().answer(asked)
        now += 1_000
        val (second, _) = talk().begin("Invented other subject.")

        assertThat(second).isNotEqualTo(first)
        assertThat(messages.messages.value.filter { it.conversation == first }.map { it.text })
            .containsExactly("Invented question.", "Invented reply.").inOrder()
        assertThat(talk().observeConversations().first().map { it.key }).containsExactly(second, first).inOrder()
    }

    /** D159: one memory — another conversation's words are sent too, and only this one's are marked as this one's. */
    @Test
    fun `a reply reads the other conversations and marks this one`() = runTest {
        messages.addOwn("Invented, another subject.", NOW - DAY, "talk:9")
        val id = talk().say("Invented question.", "talk:1")

        coach.says("Invented reply.")
        talk().answer(id)

        assertThat(coach.asked.single().conversation.map { it.text to it.thisConversation })
            .containsExactly("Invented, another subject." to false, "Invented question." to true).inOrder()
    }

    /** D159: a conversation being written in is read whole, however old it is. */
    @Test
    fun `this conversation is read whatever its age`() = runTest {
        messages.addOwn("Invented, long ago, here.", NOW - 40 * DAY, "talk:1")
        messages.addOwn("Invented, long ago, elsewhere.", NOW - 40 * DAY, "talk:9")
        val id = talk().say("Invented question.", "talk:1")

        coach.says("Invented reply.")
        talk().answer(id)

        assertThat(coach.asked.single().conversation.map { it.text }).containsExactly("Invented, long ago, here.", "Invented question.").inOrder()
    }

    /** D159: a note's conversation shows the note before anything is stored, and stores it with the first message. */
    @Test
    fun `the note is shown, then stored with the first message`() = runTest {
        val note = WeekNote("Invented note.", NOW - DAY, MovementWeek.mondayOf(TEST_EPOCH_DAY), WeekNoteSource.TRAINER)
        weekNotes.keep(note)
        val key = Conversation.note(note.writtenAtMillis)

        assertThat(talk().observeConversation(key).first().map { Conversation.text(it) }).containsExactly("Invented note.")
        assertThat(messages.messages.value).isEmpty()

        talk().say("Invented answer.", key)
        talk().say("Invented more.", key)

        assertThat(messages.messages.value.map { it.text to it.fromOwner })
            .containsExactly("Invented note." to false, "Invented answer." to true, "Invented more." to true).inOrder()
    }

    /** D163: what was read is stored with the reply, made by the phone; a reply that opened nothing says so by "". */
    @Test
    fun `a reply stores what it really read`() = runTest {
        coach.opens(DrawerAsk("days", "${LocalDate.ofEpochDay(TEST_EPOCH_DAY - 6)}", "${LocalDate.ofEpochDay(TEST_EPOCH_DAY)}", ""))
        coach.says("Invented reply.")
        record.earliest.value = TEST_EPOCH_DAY - 100
        val id = talk().say("Invented question.", "talk:1")

        talk().answer(id)

        assertThat(messages.messages.value.last().opened).isEqualTo("sleep and recovery, 7 days")

        now += 1_000
        coach.says("Invented second reply.")
        talk().answer(talk().say("Invented second.", "talk:1"))

        assertThat(messages.messages.value.last().opened).isEqualTo("")
    }

    /** D161: said facts go on the sheet at once, marked as said, as a version of their own; a bad group is dropped. */
    @Test
    fun `what the owner said goes on the sheet as a new version, and a bad fact is dropped`() = runTest {
        coach.says(
            "Invented reply.",
            remember = listOf(RememberedFact("eating", "You avoid invented berries."), RememberedFact("astrology", "Invented.")),
        )

        talk().answer(talk().say("Invented question.", "talk:1"))

        val version = sheets.observe().first().single()
        assertThat(version.madeBy).isEqualTo(SheetMadeBy.CONVERSATION)
        assertThat(version.model).isNull()
        val fact = version.current.single()
        assertThat(fact.text).isEqualTo("You avoid invented berries.")
        assertThat(fact.group).isEqualTo(FactGroup.EATING)
        assertThat(fact.source).isEqualTo(FactSource.SAID)
        assertThat(fact.sinceEpochDay).isEqualTo(TEST_EPOCH_DAY)
    }

    /** D161: with nothing to add, no version is made. */
    @Test
    fun `a repeat of a fact already there makes no version`() = runTest {
        sheets.add(NOW - HOUR, listOf(SheetFact(1, FactGroup.LIFE, "An invented fact.", TEST_EPOCH_DAY - 4, FactSource.NOTICED)), SheetMadeBy.REWRITE, "a-model")
        coach.says("Invented reply.", remember = listOf(RememberedFact("life", "an invented FACT.")))

        talk().answer(talk().say("Invented question.", "talk:1"))

        assertThat(sheets.observe().first()).hasSize(1)
    }

    /** D161: the reply is stored whatever happens to the fact. */
    @Test
    fun `a sheet that cannot be written leaves the reply stored`() = runTest {
        val refusing = object : SheetStore by sheets {
            override suspend fun add(atMillis: Long, facts: List<SheetFact>, madeBy: SheetMadeBy, model: String?, restoredFrom: Long?): Long = error("invented write failure")
        }
        coach.says("Invented reply.", remember = listOf(RememberedFact("life", "An invented fact.")))
        val talk = TalkToTheTrainer(coachOver(), messages, store, letters, weekNotes, refusing, ProblemLog.NONE, TODAY, Now { now })

        assertThat(talk.answer(talk.say("Invented question.", "talk:1"))).isEqualTo(TalkToTheTrainer.Talked.Replied)
        assertThat(messages.messages.value.last().text).isEqualTo("Invented reply.")
    }

    /** D163: a sentence tried on the workbench is asked about as a message is, and nothing is stored. */
    @Test
    fun `trying out a sentence stores nothing`() = runTest {
        messages.addOwn("Invented, another subject.", NOW - DAY, "talk:9")
        coach.says("Invented reply.", remember = listOf(RememberedFact("life", "An invented fact.")))

        val answer = talk().tryOut(" Invented sentence. ", "Invented instructions.") as CoachAnswer.Answered

        assertThat(answer.text).isEqualTo("Invented reply.")
        assertThat(coach.instructions.single()).isEqualTo("Invented instructions.")
        assertThat(coach.asked.single().replyTo.text).isEqualTo("Invented sentence.")
        assertThat(coach.asked.single().conversation.map { it.text }).containsExactly("Invented, another subject.", "Invented sentence.").inOrder()
        assertThat(messages.messages.value.map { it.text }).containsExactly("Invented, another subject.")
        assertThat(sheets.observe().first()).isEmpty()
    }

    private fun talk() = TalkToTheTrainer(coachOver(), messages, store, letters, weekNotes, sheets, ProblemLog.NONE, TODAY, Now { now })

    private fun coachOver() = askTheCoach(coach, Now { now }, record = record, store = store, programmes = programmes, sheets = sheets, weekNotes = weekNotes)

    private fun run(id: Long) = Workout(
        id = id, epochDay = TEST_EPOCH_DAY, startedAtMillis = TEST_EPOCH_DAY * DAY + 7 * HOUR, durationMinutes = 60,
        kind = WorkoutKind.RUN, title = null, distanceM = null, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private companion object {
        val TODAY = Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
        const val NOW = TEST_EPOCH_DAY * DAY + 15 * HOUR
        val MONDAY = MovementWeek.mondayOf(TEST_EPOCH_DAY)
        val FEEDBACK = Feedback("Invented headline.", "", "", "", "", PlanFollowed.NO_PLAN, note = "Invented note.")
        val EVALUATION = Evaluation("Invented headline.", "Invented.", "Invented.", "")
        val WALK_30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented.")
        val WEEKS = WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(WALK_30, WALK_30)) }, "Invented reason.")
    }
}
