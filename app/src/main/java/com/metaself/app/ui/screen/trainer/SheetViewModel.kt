package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.TrainerSheets
import com.metaself.app.domain.trainer.SheetVersion
import com.metaself.app.domain.trainer.Sheets
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.guarded
import com.metaself.app.ui.trainer.SheetWording
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
 * What I know about you (D149, D150): the newest version of the sheet, the facts dropped within 28 days, and the
 * owner's changes — Edit, Remove and Go back — each a new version. Nothing here asks the trainer. A refused change is
 * said (D8).
 */
@HiltViewModel
class SheetViewModel @Inject constructor(
    private val sheets: TrainerSheets,
    private val today: Today,
    private val problems: ProblemLog,
) : ViewModel() {

    /**
     * @property editing the fact being edited and the words in the field.
     * @property editRefused why the last edit was not saved.
     * @property removing the fact whose removal is being confirmed.
     * @property goingBack Go back is being confirmed.
     */
    data class State(
        val versions: List<SheetVersion> = emptyList(),
        val today: Long = 0,
        val loaded: Boolean = false,
        val unreadable: Boolean = false,
        val editing: Editing? = null,
        val editRefused: String? = null,
        val removing: Long? = null,
        val goingBack: Boolean = false,
        val busy: Boolean = false,
        val refused: ActionRefused? = null,
    ) {
        val newest: SheetVersion? get() = versions.maxByOrNull { it.id }
        val dropped get() = newest?.dropped(today).orEmpty()
        val lastRewrite: Long? get() = Sheets.lastRewriteAt(versions)
        val canGoBack: Boolean get() = loaded && !busy && Sheets.canGoBack(versions)
    }

    data class Editing(val id: Long, val text: String)

    private val local = MutableStateFlow(State())

    private data class Read(val versions: List<SheetVersion>, val unreadable: Boolean)

    private val read = sheets.observe()
        .map { Read(it, unreadable = false) }
        .catch { failure ->
            problems.record(PROBLEM_KIND, failure::class.java.simpleName)
            emit(Read(emptyList(), unreadable = true))
        }

    val state: StateFlow<State> = combine(local, read) { now, read ->
        now.copy(versions = read.versions, loaded = !read.unreadable, unreadable = read.unreadable, today = today().toEpochDay())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun startEdit(id: Long) {
        val fact = state.value.newest?.current?.firstOrNull { it.id == id } ?: return
        local.update { it.copy(editing = Editing(id, fact.text), editRefused = null, refused = null) }
    }

    /** The field changed; a few characters over the limit are kept so the count can say so. */
    fun editText(text: String) = local.update { now ->
        now.editing?.let { now.copy(editing = it.copy(text = text.take(Sheets.MAX_FACT + 50)), editRefused = null) } ?: now
    }

    fun cancelEdit() = local.update { it.copy(editing = null, editRefused = null) }

    fun saveEdit() {
        val editing = local.value.editing ?: return
        if (local.value.busy) return
        local.update { it.copy(busy = true, refused = null) }
        guarded(problems, onRefused = { local.update { it.copy(busy = false, refused = ActionRefused.NOTHING_CHANGED) } }) {
            val outcome = sheets.edit(editing.id, editing.text)
            val why = SheetWording.refused(outcome)
            local.update { if (why == null) it.copy(busy = false, editing = null, editRefused = null) else it.copy(busy = false, editRefused = why) }
        }
    }

    fun askRemove(id: Long) = local.update { it.copy(removing = id, refused = null) }

    fun cancelRemove() = local.update { it.copy(removing = null) }

    fun confirmRemove() {
        val id = local.value.removing ?: return
        local.update { it.copy(removing = null, busy = true, refused = null) }
        guarded(problems, onRefused = { local.update { it.copy(busy = false, refused = ActionRefused.NOTHING_CHANGED) } }) {
            sheets.remove(id)
            local.update { it.copy(busy = false) }
        }
    }

    fun askGoBack() = local.update { if (it.busy) it else it.copy(goingBack = true, refused = null) }

    fun cancelGoBack() = local.update { it.copy(goingBack = false) }

    fun confirmGoBack() {
        if (!local.value.goingBack) return
        local.update { it.copy(goingBack = false, busy = true, refused = null) }
        guarded(problems, onRefused = { local.update { it.copy(busy = false, refused = ActionRefused.NOTHING_CHANGED) } }) {
            sheets.goBack()
            local.update { it.copy(busy = false) }
        }
    }

    companion object {
        const val PROBLEM_KIND = "trainer"
    }
}
