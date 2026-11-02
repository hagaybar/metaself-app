package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.ai.AiSettings
import com.metaself.app.data.ai.AiSettingsStore
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.trainer.TalkToTheTrainer
import com.metaself.app.domain.trainer.Conversation
import com.metaself.app.domain.trainer.ConversationSummary
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.guarded
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * The coach screen's box and conversations (D157, D159, D160). Send stores the owner's words as a new conversation,
 * empties the box and hands over to that conversation, which does the asking. The rest of the screen is
 * [TrainerViewModel]'s.
 */
@HiltViewModel
class CoachViewModel @Inject constructor(
    private val talk: TalkToTheTrainer,
    settings: AiSettingsStore,
    private val problems: ProblemLog,
) : ViewModel() {

    data class State(
        val conversations: List<ConversationSummary> = emptyList(),
        val loaded: Boolean = false,
        val unreadable: Boolean = false,
        val draft: String = "",
        val sending: Boolean = false,
        val refused: ActionRefused? = null,
        val ceiling: Int = AiSettings.DEFAULT_CEILING,
    ) {
        val canSend: Boolean get() = !sending && draft.isNotBlank()
    }

    private val local = MutableStateFlow(State())

    private data class Read(val conversations: List<ConversationSummary>, val unreadable: Boolean)

    private val conversations = talk.observeConversations()
        .map { Read(it, unreadable = false) }
        .catch { failure ->
            problems.record(PROBLEM_KIND, failure.message ?: failure::class.java.simpleName)
            emit(Read(emptyList(), unreadable = true))
        }

    val state: StateFlow<State> = combine(local, conversations, settings.settings.catch { emit(AiSettings()) }) { now, read, ai ->
        now.copy(conversations = read.conversations, loaded = !read.unreadable, unreadable = read.unreadable, ceiling = ai.dailyCeiling)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    /** The box changed; at most [Conversation.MAX_MESSAGE] characters are kept. */
    fun edit(text: String) = local.update { it.copy(draft = Conversation.cut(text), refused = null) }

    /** D159: a new conversation; [opened] gets its key and the message to ask about. A failed write is said (D8). */
    fun send(opened: (String, Long) -> Unit) {
        val now = local.value
        if (!now.canSend) return
        local.update { it.copy(sending = true, refused = null) }
        guarded(problems, onRefused = { local.update { it.copy(sending = false, refused = ActionRefused.NOTHING_CHANGED) } }) {
            val (conversation, id) = talk.begin(now.draft)
            local.update { it.copy(draft = if (it.draft == now.draft) "" else it.draft, sending = false) }
            opened(conversation, id)
        }
    }

    companion object {
        const val PROBLEM_KIND = "trainer"
    }
}
