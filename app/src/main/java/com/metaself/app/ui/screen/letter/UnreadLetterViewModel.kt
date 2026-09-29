package com.metaself.app.ui.screen.letter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.letter.LetterNoteStore
import com.metaself.app.data.letter.LetterRun
import com.metaself.app.data.letter.LetterStore
import com.metaself.app.data.time.Today
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The day's note about the weekly letter (D103): the newest letter, while it is unread, not put away, and
 * at most a week past its Sunday — an old letter is not a notice (D14). Reads only.
 */
@HiltViewModel
class UnreadLetterViewModel @Inject constructor(
    store: LetterStore,
    private val notes: LetterNoteStore,
    today: Today,
    private val problems: ProblemLog,
) : ViewModel() {

    data class Unread(val weekMonday: Long, val headline: String)

    val unread: StateFlow<Unread?> = combine(store.observeAll(), notes.dismissed) { letters, dismissed ->
        letters.maxByOrNull { it.weekMonday }
            ?.takeIf { it.readAtMillis == null && it.weekMonday != dismissed }
            ?.takeIf { today().toEpochDay() - (it.weekMonday + 6) <= NOTED_DAYS }
            ?.let { Unread(it.weekMonday, it.texts.headline) }
    }
        .catch { failure ->
            if (failure is CancellationException) throw failure
            // No note is the whole cost; the letters are still under Trainer → Weekly letters.
            runCatching { problems.record(LetterRun.KIND, "note not read: ${failure::class.java.simpleName}") }
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Put away the note for [weekMonday]'s letter; the letter stays unread in the list. */
    fun dismiss(weekMonday: Long) {
        viewModelScope.launch {
            LetterRun.guarded(problems, "note not put away") { notes.dismiss(weekMonday) }
        }
    }

    private companion object {
        /** A choice: the note stands for a week after the letter's Sunday. */
        const val NOTED_DAYS = 7
    }
}
