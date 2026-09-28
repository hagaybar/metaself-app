package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.ViewModel
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.trainer.AboutMeStore
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.guarded
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * D90: the page where the owner writes the note sent with every trainer request. Nothing here asks the
 * trainer. Save is offered only once the stored note has been read, so it can never write an empty
 * field over it.
 */
@HiltViewModel
class AboutMeViewModel @Inject constructor(
    private val store: AboutMeStore,
    private val problems: ProblemLog,
) : ViewModel() {

    /**
     * @property loaded the stored note has been read into [text].
     * @property saved the note on screen was saved; the screen goes back.
     */
    data class State(
        val text: String = "",
        val loaded: Boolean = false,
        val saving: Boolean = false,
        val saved: Boolean = false,
        val refused: ActionRefused? = null,
    ) {
        val canSave: Boolean get() = loaded && !saving
    }

    private val local = MutableStateFlow(State())
    val state: StateFlow<State> = local.asStateFlow()

    init {
        guarded(problems, onRefused = { local.update { it.copy(refused = ActionRefused.COULD_NOT_OPEN) } }) {
            val note = store.note.first()
            local.update { it.copy(text = note, loaded = true) }
        }
    }

    /** The field changed; at most [AboutMeStore.MAX] characters are kept. */
    fun edit(text: String) = local.update { now ->
        if (now.saving) now else now.copy(text = text.take(AboutMeStore.MAX), saved = false, refused = null)
    }

    fun save() {
        val now = local.value
        if (!now.canSave) return
        local.update { it.copy(saving = true, refused = null) }
        guarded(problems, onRefused = { local.update { it.copy(saving = false, refused = ActionRefused.NOTHING_CHANGED) } }) {
            store.save(now.text)
            local.update { it.copy(saving = false, saved = true) }
        }
    }
}
