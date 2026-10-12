package com.metaself.app.data.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.letter.FakeLetterStore
import com.metaself.app.data.letter.aWeeklyLetter
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.weight.InMemoryWeightRepository
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.trainer.Conversation
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.LineKind
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ThreadItem
import com.metaself.app.domain.trainer.TrainerQuestion
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
    private val trainer = FakeTrainer()
    private val programmes = FakeProgrammeStore()
    private val messages = InMemoryConversationStore()
    private val letters = FakeLetterStore()
    private val weekNotes = InMemoryWeekNoteStore()
    private var now = NOW

    @Test
    fun `a message is stored, asked about once, and its reply stored against it`() = runTest {
        trainer.talks += TrainerReply.Answered("Invented reply.", "a-model")

        val id = talk().say("  Invented question.  ")
        val outcome = talk().answer(id)

        assertThat(outcome).isEqualTo(TalkToTheTrainer.Talked.Replied)
        val stored = messages.observe().first()
        assertThat(stored.map { it.text }).containsExactly("Invented question.", "Invented reply.").inOrder()
        assertThat(stored[0].answered).isTrue()
        assertThat(stored[1].fromOwner).isFalse()
        assertThat(stored[1].model).isEqualTo("a-model")
        assertThat(stored[1].replyTo).isEqualTo(id)
        val question = trainer.asked.single().question as TrainerQuestion.Talk
        assertThat(question.replyTo.text).isEqualTo("Invented question.")
        assertThat(question.conversation.map { it.text }).containsExactly("Invented question.")
    }

    /** D145: a message is never lost because a request failed; Try again sends it again. */
    @Test
    fun `a failed request keeps the message not answered, and trying again answers it`() = runTest {
        trainer.talks += TrainerReply.Failed(EstimateResult.Unreachable())
        val id = talk().say("Invented question.")

        assertThat(talk().answer(id)).isEqualTo(TalkToTheTrainer.Talked.Failed(EstimateResult.Unreachable()))
        assertThat(messages.observe().first().single().answered).isFalse()

        now += 60_000
        trainer.talks += TrainerReply.Answered("Invented reply.", "a-model")
        assertThat(talk().answer(id)).isEqualTo(TalkToTheTrainer.Talked.Replied)

        assertThat(trainer.asked).hasSize(2)
        assertThat((trainer.asked.last().question as TrainerQuestion.Talk).replyTo.text).isEqualTo("Invented question.")
        assertThat(messages.observe().first().map { it.answered }).containsExactly(true, false).inOrder()
        // Answered once: asking again asks nothing.
        assertThat(talk().answer(id)).isEqualTo(TalkToTheTrainer.Talked.NothingToAnswer)
        assertThat(trainer.asked).hasSize(2)
    }

    @Test
    fun `a blank message is refused, and a long one is cut`() = runTest {
        assertThat(runCatching { talk().say("   ") }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        val id = talk().say("a".repeat(2_500))
        assertThat(messages.message(id)!!.text).hasLength(Conversation.MAX_MESSAGE)
    }

    /** D145: deleting his message removes the reply it got, and nothing else. */
    @Test
    fun `deleting a message removes the reply it got`() = runTest {
        trainer.talks += TrainerReply.Answered("Invented first reply.", "a-model")
        trainer.talks += TrainerReply.Answered("Invented second reply.", "a-model")
        val first = talk().say("Invented first.")
        talk().answer(first)
        now += 60_000
        val second = talk().say("Invented second.")
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
        val id = talk().say("Invented question.")

        val thread = talk().observeThread().first()

        assertThat(thread.map { it::class }).containsExactly(ThreadItem.Letter::class, ThreadItem.AfterSession::class, ThreadItem.Said::class).inOrder()
        val feedback = thread[1] as ThreadItem.AfterSession
        assertThat(feedback.kind).isEqualTo(WorkoutKind.RUN)
        assertThat(feedback.minutes).isEqualTo(60)
        assertThat(messages.observe().first().map { it.id }).containsExactly(id)

        trainer.talks += TrainerReply.Answered("Invented reply.", "a-model")
        talk().answer(id)
        val sent = (trainer.asked.single().question as TrainerQuestion.Talk).conversation
        assertThat(sent.map { it.kind }).containsExactly(LineKind.LETTER, LineKind.SESSION_FEEDBACK, LineKind.MESSAGE).inOrder()
        assertThat(sent[1].text).isEqualTo("Invented headline.\n\nInvented note.")
    }

    /** D147: the conversation sent is the window — nothing older than 14 days, the oldest dropped past the cap. */
    @Test
    fun `the request carries the last 14 days, the oldest dropped past the cap`() = runTest {
        messages.addOwn("Invented, fifteen days ago.", NOW - 15 * DAY)
        messages.addOwn("b".repeat(1_000), NOW - 3 * DAY)
        repeat(12) { messages.addOwn("c".repeat(2_000), NOW - 2 * DAY + it) }
        val id = talk().say("Invented question.")
        trainer.talks += TrainerReply.Answered("Invented reply.", "a-model")

        talk().answer(id)

        val sent = (trainer.asked.single().question as TrainerQuestion.Talk).conversation
        // 12 × 2,000 + 18 = 24,018 is over the cap: the oldest 2,000-character one goes, and the 1,000 before it.
        assertThat(sent).hasSize(12)
        assertThat(sent.first().text).isEqualTo("c".repeat(2_000))
        assertThat(sent.last().text).isEqualTo("Invented question.")
        assertThat(sent.sumOf { it.text.length }).isAtMost(Conversation.MAX_CHARS)
    }

    @Test
    fun `the running plan, its week and the week's note go with the question`() = runTest {
        val id = programmes.add(Programme(0, (MONDAY - 15) * DAY + 12 * HOUR, ProgrammeAsk(2, 2), EVALUATION, WEEKS, "a-model"))
        programmes.keep(id, MONDAY - 7, TEST_EPOCH_DAY - 7)
        weekNotes.note.value = WeekNote("Invented week note.", NOW - HOUR, MONDAY, WeekNoteSource.TRAINER)
        val message = talk().say("Invented question.")

        val question = talk().question(messages.message(message)!!)

        assertThat(question.plan!!.weekIndex).isEqualTo(1)
        assertThat(question.plan!!.startEpochDay).isEqualTo(MONDAY - 7)
        assertThat(question.weekNote).isEqualTo("Invented week note.")

        weekNotes.note.value = WeekNote("Invented old note.", NOW - 8 * DAY, MONDAY - 7, WeekNoteSource.TRAINER)
        assertThat(talk().question(messages.message(message)!!).weekNote).isNull()
    }

    @Test
    fun `with no plan running, none goes`() = runTest {
        val message = talk().say("Invented question.")

        assertThat(talk().question(messages.message(message)!!).plan).isNull()
    }

    private fun talk() = TalkToTheTrainer(ask(), trainer, messages, store, letters, weekNotes, Now { now })

    private fun ask() = AskTheTrainer(
        record, store, InMemoryWeightRepository(), FakeProfileRepository(aProfile()), trainer, InMemoryAboutMeStore(), programmes,
        Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }, Now { now }, CurrentYear { TEST_YEAR },
    )

    private fun run(id: Long) = Workout(
        id = id, epochDay = TEST_EPOCH_DAY, startedAtMillis = TEST_EPOCH_DAY * DAY + 7 * HOUR, durationMinutes = 60,
        kind = WorkoutKind.RUN, title = null, distanceM = null, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private companion object {
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
