package com.metaself.app.ui.screen.trainer

import androidx.compose.ui.semantics.Role
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.letter.LetterTexts
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.Proposal
import com.metaself.app.domain.trainer.ProposalKind
import com.metaself.app.domain.trainer.ProposalState
import com.metaself.app.domain.trainer.ThreadItem
import com.metaself.app.domain.trainer.TrainerMessage
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.trainer.TalkWording
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4.
 *
 * Which words Your trainer draws (D145, D146), in which order, and that each door calls back. Not provable here
 * (CLAUDE.md, Testing): a bubble's colour, its side, its width. Every message, session and letter is invented.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "+h8000dp")
class TalkScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `the screen is called Your trainer, says it is AI advice and what leaves the phone`() {
        val texts = draw(TalkViewModel.State(loaded = true))

        assertThat(texts).containsAtLeast("Your trainer", "Back", TalkWording.AI, TalkWording.EMPTY)
        assertThat(texts).contains(TalkWording.SENT_TO_OPENAI)
    }

    /** D164: a ready card shows the change, Confirm and Not now, and each calls back with its reply. */
    @Test
    fun `a ready card shows the change and asks for Confirm`() {
        val proposal = Proposal(ProposalKind.STOP_PLAN, ProposalState.READY, title = "Invented plan", programmeId = 1)
        val message = TrainerMessage(7, 1_000, false, "Invented reply.", "a-model", false, replyTo = 6, conversation = "talk:1", proposal = proposal)
        var confirmed: Long? = null
        val texts = render.texts(heightPx = 4_000) {
            run { TalkScreen(TalkViewModel.State(thread = listOf(ThreadItem.Said(message)), loaded = true), {}, {}, {}, {}, {}, {}, {}, {}, {}, onConfirmProposal = { confirmed = it }) }
        }

        assertThat(texts).containsAtLeast("Stop your plan: Invented plan", "Confirm", "Not now")
        render.click("Confirm")
        assertThat(confirmed).isEqualTo(7L)
    }

    /** D164: a card that is over says how it ended, with no Confirm. */
    @Test
    fun `a card that is over has no Confirm`() {
        val message = TrainerMessage(7, 1_000, false, "Invented reply.", "a-model", false, replyTo = 6, conversation = "talk:1", proposal = Proposal(ProposalKind.STOP_PLAN, ProposalState.OUT_OF_DATE))
        val texts = render.texts(heightPx = 4_000) {
            run { TalkScreen(TalkViewModel.State(thread = listOf(ThreadItem.Said(message)), loaded = true), {}, {}, {}, {}, {}, {}, {}, {}, {}) }
        }

        assertThat(texts).contains(TalkWording.OUT_OF_DATE)
        assertThat(texts).doesNotContain("Confirm")
    }

    /** D163: under a coach's reply, small, what the phone says it read; nothing under a reply from before the file. */
    @Test
    fun `a reply shows what it read`() {
        val thread = listOf(
            ThreadItem.Said(TrainerMessage(1, 1_000, true, "Invented question.", null, true, conversation = "talk:1")),
            ThreadItem.Said(TrainerMessage(2, 2_000, false, "Invented reply.", "a-model", false, 1, "talk:1", "sleep and recovery, 7 days · meals, 3 days")),
            ThreadItem.Said(TrainerMessage(3, 3_000, false, "Invented second.", "a-model", false, 1, "talk:1", "")),
            ThreadItem.Said(TrainerMessage(4, 4_000, false, "Invented older.", "a-model", false, 1, "talk:1", null)),
        )

        val texts = draw(TalkViewModel.State(loaded = true, thread = thread))

        assertThat(texts).containsAtLeast("Read: sleep and recovery, 7 days · meals, 3 days", "Read: the front page only")
        assertThat(texts.count { it.startsWith("Read: ") }).isEqualTo(2)
    }

    /** D146: the letter as a card, the feedback in full under its label, then the messages — each in its place. */
    @Test
    fun `the thread runs in time order, a letter as a card and feedback under its label`() {
        var opened: Long? = null
        val texts = draw(state(THREAD), onLetter = { opened = it })

        val order = listOf(
            // The card is one node holding its title and its line, so the line is checked on its own below.
            TalkWording.day(TEST_EPOCH_DAY - 1), "Your letter · week of 24 Aug",
            TalkWording.day(TEST_EPOCH_DAY), "AFTER YOUR RUN · 60 MIN", "Invented headline.", "Invented question.", "Invented reply.",
        )
        order.zipWithNext().forEach { (first, second) -> assertWithMessage("$first before $second").that(render.isDrawnBefore(first, second)).isTrue() }
        assertThat(texts).doesNotContain(TalkWording.EMPTY)
        assertThat(texts.single { it.startsWith("Invented headline.") }).isEqualTo("Invented headline.\n\nInvented note.")
        assertThat(texts.single { it.startsWith("Arrived ") }).endsWith(" · tap to read")
        assertThat(render.roleOf("Your letter · week of 24 Aug")).isEqualTo(Role.Button)
        render.click("Your letter · week of 24 Aug")
        assertThat(opened).isEqualTo(TEST_EPOCH_DAY - 10)
    }

    /** D145: a message with no reply says so, with Try again, which waits while the trainer is writing. */
    @Test
    fun `an unanswered message has Try again`() {
        var tried: Long? = null
        val texts = draw(state(listOf(said(7, NOW, owner = true, answered = false))), onTryAgain = { tried = it })

        assertThat(texts).containsAtLeast("Invented question.", TalkWording.NOT_ANSWERED, TalkWording.TRY_AGAIN).inOrder()
        render.click(TalkWording.TRY_AGAIN)
        assertThat(tried).isEqualTo(7L)

        draw(state(listOf(said(7, NOW, owner = true, answered = false), said(8, NOW + 1, owner = true, answered = false))).copy(writing = 8))
        assertThat(render.isEnabled(TalkWording.TRY_AGAIN)).isFalse()
    }

    @Test
    fun `while the trainer is writing it says so, and Send waits`() {
        val texts = draw(state(listOf(said(7, NOW, owner = true, answered = false))).copy(writing = 7, draft = "Invented next."))

        assertThat(texts).contains(TalkWording.WRITING)
        assertThat(texts).doesNotContain(TalkWording.TRY_AGAIN)
        assertThat(render.isEnabledDescribed(TalkWording.SEND)).isFalse()
    }

    @Test
    fun `Send sends the words typed, and waits for words`() {
        var sent = 0
        draw(state(emptyList()).copy(draft = "Invented words."), onSend = { sent++ })
        render.clickDescribed(TalkWording.SEND)
        assertThat(sent).isEqualTo(1)

        draw(state(emptyList()))
        assertThat(render.isEnabledDescribed(TalkWording.SEND)).isFalse()
    }

    @Test
    fun `a failure says why and that the message is kept`() {
        val texts = draw(state(listOf(said(7, NOW, owner = true, answered = false))).copy(failure = EstimateResult.Unreachable()))

        assertThat(texts).contains("Could not reach the model. Your message is kept; Try again sends it.")
    }

    /** D145: his message, tapped, offers Delete; Delete asks first, naming the reply that goes with it. */
    @Test
    fun `his message tapped offers Delete, which asks first`() {
        var selected: Long? = null
        var asked: Long? = null
        draw(state(listOf(said(7, NOW, owner = true, answered = true))), onSelect = { selected = it })
        render.click("Invented question.")
        assertThat(selected).isEqualTo(7L)

        val texts = draw(state(listOf(said(7, NOW, owner = true, answered = true))).copy(selected = 7), onAskDelete = { asked = it })
        assertThat(texts).contains(TalkWording.DELETE)
        render.click(TalkWording.DELETE)
        assertThat(asked).isEqualTo(7L)
        // The confirmation is a dialog, a window of its own, which this rig does not read; TalkViewModelTest covers it.
    }

    @Test
    fun `an unreadable conversation says so`() {
        assertThat(draw(TalkViewModel.State(unreadable = true))).contains(TalkWording.UNREADABLE)
    }

    private fun draw(
        state: TalkViewModel.State,
        onSend: () -> Unit = {},
        onTryAgain: (Long) -> Unit = {},
        onSelect: (Long) -> Unit = {},
        onAskDelete: (Long) -> Unit = {},
        onLetter: (Long) -> Unit = {},
    ): List<String> = render.texts(heightPx = TALL_PX) {
        TalkScreen(
            state = state, onBack = {}, onEdit = {}, onSend = onSend, onTryAgain = onTryAgain, onSelect = onSelect,
            onAskDelete = onAskDelete, onConfirmDelete = {}, onCancelDelete = {}, onLetter = onLetter,
        )
    }

    private fun state(thread: List<ThreadItem>) = TalkViewModel.State(thread = thread, loaded = true)

    private fun said(id: Long, at: Long, owner: Boolean, answered: Boolean = owner) = ThreadItem.Said(
        TrainerMessage(id, at, owner, if (owner) "Invented question." else "Invented reply.", if (owner) null else "a-model", answered, if (owner) null else id - 1),
    )

    private companion object {
        /** A lazy list composes only what fits: with the tall display above, every row of these short threads does. */
        const val TALL_PX = 12_000
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
        const val NOW = TEST_EPOCH_DAY * DAY + 12 * HOUR

        val FEEDBACK = Feedback("Invented headline.", "", "", "", "", PlanFollowed.NO_PLAN, note = "Invented note.")
        val LETTER = LetterTexts.Coach("Invented.", "Invented.", "Invented.", "Invented.", "Invented.", "Invented.", "Invented.")

        /** A letter at noon the day before, a run's feedback this morning, then a question and its reply. */
        val THREAD: List<ThreadItem> = listOf(
            ThreadItem.Letter(TEST_EPOCH_DAY - 10, NOW - DAY, LETTER),
            ThreadItem.AfterSession(4, NOW - 3 * HOUR, WorkoutKind.RUN, 60, FEEDBACK),
            ThreadItem.Said(TrainerMessage(7, NOW - HOUR, true, "Invented question.", null, answered = true)),
            ThreadItem.Said(TrainerMessage(8, NOW - HOUR + 60_000, false, "Invented reply.", "a-model", answered = false, replyTo = 7)),
        )
    }
}
