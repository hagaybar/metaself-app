package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.letter.FakeLetterStore
import com.metaself.app.data.time.Now
import com.metaself.app.data.trainer.FakeTrainer
import com.metaself.app.data.trainer.FakeTrainerStore
import com.metaself.app.data.trainer.InMemoryConversationStore
import com.metaself.app.data.trainer.InMemoryWeekNoteStore
import com.metaself.app.data.trainer.TalkToTheTrainer
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.ThreadItem
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.RecordingProblemLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Your trainer (D145, D146): send, a failure that loses nothing, Try again, delete. Every word is invented. */
@OptIn(ExperimentalCoroutinesApi::class)
class TalkViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val outliving = CoroutineScope(SupervisorJob() + dispatcher)
    private val store = FakeTrainerStore()
    private val trainer = FakeTrainer()
    private val messages = InMemoryConversationStore()
    private val problems = RecordingProblemLog()
    private var now = TrainerScreens.NOW

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() {
        outliving.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `send stores the words, empties the field, and shows the reply`() = runTest {
        trainer.talks += TrainerReply.Answered("Invented reply.", "a-model")
        val viewModel = watched()
        advanceUntilIdle()

        viewModel.edit("Invented question.")
        viewModel.send()
        viewModel.send()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.draft).isEmpty()
        assertThat(state.writing).isNull()
        assertThat(state.failure).isNull()
        assertThat(state.thread.map { (it as ThreadItem.Said).message.text }).containsExactly("Invented question.", "Invented reply.").inOrder()
        assertThat(trainer.asked).hasSize(1)
    }

    /** D145: the message stays, not answered; Try again sends it and the reply arrives. */
    @Test
    fun `a failed reply keeps the message, and Try again answers it`() = runTest {
        trainer.talks += TrainerReply.Failed(EstimateResult.NoKey)
        val viewModel = watched()
        advanceUntilIdle()
        viewModel.edit("Invented question.")
        viewModel.send()
        advanceUntilIdle()

        assertThat(viewModel.state.value.failure).isEqualTo(EstimateResult.NoKey)
        val kept = (viewModel.state.value.thread.single() as ThreadItem.Said).message
        assertThat(kept.answered).isFalse()
        assertThat(viewModel.state.value.draft).isEmpty()

        trainer.talks += TrainerReply.Answered("Invented reply.", "a-model")
        viewModel.tryAgain(kept.id)
        advanceUntilIdle()

        assertThat(viewModel.state.value.failure).isNull()
        assertThat(messages.observe().first().map { it.answered }).containsExactly(true, false).inOrder()
    }

    /** A write that fails stores nothing and keeps the words in the field. */
    @Test
    fun `a message that cannot be stored stays in the field, and nothing is asked`() = runTest {
        messages.failWrites = true
        val viewModel = watched()
        advanceUntilIdle()
        viewModel.edit("Invented question.")

        viewModel.send()
        advanceUntilIdle()

        assertThat(viewModel.state.value.draft).isEqualTo("Invented question.")
        assertThat(viewModel.state.value.refused).isEqualTo(ActionRefused.NOTHING_CHANGED)
        assertThat(viewModel.state.value.busy).isFalse()
        assertThat(trainer.asked).isEmpty()
    }

    @Test
    fun `nothing is sent without words`() = runTest {
        val viewModel = watched()
        advanceUntilIdle()
        viewModel.edit("   ")

        viewModel.send()
        advanceUntilIdle()

        assertThat(messages.messages.value).isEmpty()
        assertThat(viewModel.state.value.canSend).isFalse()
    }

    /** D145: Delete asks first; confirmed, his message and its reply go. */
    @Test
    fun `delete asks first, then removes the message and its reply`() = runTest {
        trainer.talks += TrainerReply.Answered("Invented reply.", "a-model")
        val viewModel = watched()
        advanceUntilIdle()
        viewModel.edit("Invented question.")
        viewModel.send()
        advanceUntilIdle()
        val id = messages.messages.value.first { it.fromOwner }.id

        viewModel.select(id)
        viewModel.askDelete(id)
        advanceUntilIdle()
        assertThat(viewModel.state.value.confirmingDelete).isEqualTo(id)
        viewModel.cancelDelete()
        advanceUntilIdle()
        assertThat(messages.messages.value).hasSize(2)

        viewModel.askDelete(id)
        viewModel.confirmDelete()
        advanceUntilIdle()

        assertThat(messages.messages.value).isEmpty()
        assertThat(viewModel.state.value.thread).isEmpty()
        assertThat(viewModel.state.value.confirmingDelete).isNull()
    }

    /** D159: a message the coach screen stored is asked about on opening, and answered in its conversation. */
    @Test
    fun `a conversation opened to ask answers that message`() = runTest {
        trainer.talks += TrainerReply.Answered("Invented reply.", "a-model")
        val asked = messages.addOwn("Invented question.", now, CONVERSATION)

        val viewModel = watched(ask = asked)
        advanceUntilIdle()

        assertThat(messages.messages.value.map { it.conversation }.distinct()).containsExactly(CONVERSATION)
        assertThat(messages.messages.value.first { it.id == asked }.answered).isTrue()
        assertThat(viewModel.state.value.thread).hasSize(2)
    }

    /** D159: it shows only its own conversation, and sends into it. */
    @Test
    fun `only its own conversation, and Send stays in it`() = runTest {
        messages.addOwn("Invented, another subject.", now, "talk:9")
        trainer.talks += TrainerReply.Answered("Invented reply.", "a-model")
        val viewModel = watched()
        advanceUntilIdle()
        assertThat(viewModel.state.value.thread).isEmpty()

        viewModel.edit("Invented question.")
        viewModel.send()
        advanceUntilIdle()

        assertThat(viewModel.state.value.thread.map { it.conversation }.distinct()).containsExactly(CONVERSATION)
        assertThat(viewModel.state.value.thread).hasSize(2)
    }

    /** D159: Delete this conversation asks first, removes this conversation's messages and no other's, and leaves. */
    @Test
    fun `Delete this conversation asks first and takes only this one`() = runTest {
        messages.addOwn("Invented, another subject.", now, "talk:9")
        messages.addOwn("Invented question.", now, CONVERSATION)
        val viewModel = watched()
        advanceUntilIdle()
        assertThat(viewModel.state.value.canDeleteAll).isTrue()

        viewModel.confirmDeleteAll()
        advanceUntilIdle()
        assertThat(messages.messages.value).hasSize(2)

        viewModel.askDeleteAll()
        viewModel.confirmDeleteAll()
        advanceUntilIdle()

        assertThat(messages.messages.value.map { it.conversation }).containsExactly("talk:9")
        assertThat(viewModel.state.value.deletedAll).isTrue()
    }

    private fun TestScope.watched(ask: Long = TalkViewModel.NO_ASK): TalkViewModel {
        val talk = TalkToTheTrainer(
            TrainerScreens.ask(FakeMovementRecord(), store, trainer), trainer, messages, store, FakeLetterStore(), InMemoryWeekNoteStore(), Now { now },
        )
        val make = { TalkViewModel(SavedStateHandle(mapOf(TalkViewModel.CONVERSATION to CONVERSATION, TalkViewModel.ASK to ask)), talk, FakeAiSettings(), problems, outliving) }
        val viewModel = ViewModelProvider(ViewModelStore(), TrainerScreens.factory(make))[TalkViewModel::class.java]
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    private companion object {
        const val CONVERSATION = "talk:1"
    }
}
