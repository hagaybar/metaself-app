package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.ai.AiSettings
import com.metaself.app.data.ai.AiSettingsStore
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.trainer.TalkToTheTrainer
import com.metaself.app.di.ApplicationScope
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.Conversation
import com.metaself.app.domain.trainer.ThreadItem
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.guarded
import com.metaself.app.ui.outlived
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * One conversation with the trainer (D145, D146, D159), named by [CONVERSATION]; [ASK] names a message the coach
 * screen has stored, which is asked about on opening. Send stores the owner's words first — the field is emptied only once
 * they are stored — then asks, in [outliving], so a reply on its way is still stored if the screen is left. One
 * request at a time: Send and Try again wait while the trainer is writing. A failed reply leaves the message, not
 * answered, with Try again (D145); a failed write is said (D8).
 */
@HiltViewModel
class TalkViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val talk: TalkToTheTrainer,
    settings: AiSettingsStore,
    private val problems: ProblemLog,
    @ApplicationScope private val outliving: CoroutineScope,
) : ViewModel() {

    /**
     * @property sending the owner's words are being stored.
     * @property writing the id of the message the trainer is answering now.
     * @property failure why the last reply did not come.
     * @property selected the owner's message whose Delete is shown.
     * @property confirmingDelete the owner's message whose deletion is being confirmed.
     * @property confirmingDeleteAll Delete this conversation is being confirmed (D159).
     * @property deletedAll the conversation's messages are gone: the screen goes back.
     */
    data class State(
        val thread: List<ThreadItem> = emptyList(),
        val loaded: Boolean = false,
        val unreadable: Boolean = false,
        val draft: String = "",
        val sending: Boolean = false,
        val writing: Long? = null,
        val failure: EstimateResult? = null,
        val refused: ActionRefused? = null,
        val selected: Long? = null,
        val confirmingDelete: Long? = null,
        val confirmingDeleteAll: Boolean = false,
        val deletedAll: Boolean = false,
        val ceiling: Int = AiSettings.DEFAULT_CEILING,
    ) {
        val busy: Boolean get() = sending || writing != null
        val canSend: Boolean get() = loaded && !busy && draft.isNotBlank()

        /** D159: there are messages for Delete this conversation to remove, stored (a note not yet copied has id 0). */
        val canDeleteAll: Boolean get() = !busy && selected == null && thread.any { it is ThreadItem.Said && it.message.id != 0L }
    }

    private val conversation: String = checkNotNull(savedState[CONVERSATION]) { "a conversation has a key" }

    private val local = MutableStateFlow(State())

    private data class Read(val thread: List<ThreadItem>, val unreadable: Boolean)

    private val thread = talk.observeConversation(conversation)
        .map { Read(it, unreadable = false) }
        .catch { failure ->
            problems.record(PROBLEM_KIND, failure.message ?: failure::class.java.simpleName)
            emit(Read(emptyList(), unreadable = true))
        }

    val state: StateFlow<State> = combine(local, thread, settings.settings.catch { emit(AiSettings()) }) { now, read, ai ->
        now.copy(thread = read.thread, loaded = !read.unreadable, unreadable = read.unreadable, ceiling = ai.dailyCeiling)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    init {
        savedState.get<Long>(ASK)?.takeIf { it != NO_ASK }?.let(::tryAgain)
    }

    /** The field changed; at most [Conversation.MAX_MESSAGE] characters are kept. */
    fun edit(text: String) = local.update { it.copy(draft = Conversation.cut(text), refused = null) }

    fun send() {
        val now = local.value
        if (now.busy || now.draft.isBlank()) return
        val text = now.draft
        local.update { it.copy(sending = true, failure = null, refused = null, selected = null) }
        outlived(
            outliving,
            problems,
            onRefused = { local.update { it.copy(sending = false, writing = null, refused = ActionRefused.NOTHING_CHANGED) } },
            work = {
                val id = talk.say(text, conversation)
                // Stored: the field is emptied unless he has typed on, and the thread shows the trainer writing.
                local.update { it.copy(draft = if (it.draft == text) "" else it.draft, sending = false, writing = id) }
                talk.answer(id)
            },
        ) { outcome -> answered(outcome) }
    }

    /** D145: the message [id] is asked about again. */
    fun tryAgain(id: Long) {
        if (local.value.busy) return
        local.update { it.copy(writing = id, failure = null, refused = null, selected = null) }
        outlived(
            outliving,
            problems,
            onRefused = { local.update { it.copy(writing = null, refused = ActionRefused.NOTHING_CHANGED) } },
            work = { talk.answer(id) },
        ) { outcome -> answered(outcome) }
    }

    private fun answered(outcome: TalkToTheTrainer.Talked) = local.update {
        it.copy(writing = null, failure = (outcome as? TalkToTheTrainer.Talked.Failed)?.failure)
    }

    /** The owner's message [id] shows its Delete, or hides it when shown. */
    fun select(id: Long) = local.update { it.copy(selected = if (it.selected == id) null else id) }

    fun askDelete(id: Long) = local.update { if (it.writing == id) it else it.copy(confirmingDelete = id) }

    fun cancelDelete() = local.update { it.copy(confirmingDelete = null) }

    /** D145: his message and the reply it got. Not the one being answered now. */
    fun confirmDelete() {
        val id = local.value.confirmingDelete ?: return
        local.update { it.copy(confirmingDelete = null, selected = null, refused = null) }
        if (local.value.writing == id) return
        guarded(problems, onRefused = { local.update { it.copy(refused = ActionRefused.NOTHING_CHANGED) } }) {
            talk.delete(id)
        }
    }

    fun askDeleteAll() = local.update { if (it.busy) it else it.copy(confirmingDeleteAll = true) }

    fun cancelDeleteAll() = local.update { it.copy(confirmingDeleteAll = false) }

    /** D159: the conversation's messages and replies; feedback and letters stay. */
    fun confirmDeleteAll() {
        if (!local.value.confirmingDeleteAll) return
        local.update { it.copy(confirmingDeleteAll = false, selected = null, refused = null) }
        if (local.value.busy) return
        guarded(problems, onRefused = { local.update { it.copy(refused = ActionRefused.NOTHING_CHANGED) } }) {
            talk.deleteConversation(conversation)
            local.update { it.copy(deletedAll = true) }
        }
    }

    companion object {
        const val PROBLEM_KIND = "trainer"
        const val CONVERSATION = "conversation"
        const val ASK = "ask"
        const val NO_ASK = -1L
    }
}
