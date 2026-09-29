package com.metaself.app.ui.screen.letter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.letter.LetterRun
import com.metaself.app.data.letter.LetterStore
import com.metaself.app.data.time.Now
import com.metaself.app.domain.letter.WeeklyLetter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One weekly letter (D103), read once when the page opens; the first time it is shown it is marked read,
 * which takes its note off the day and its "New" off the list. Nothing is asked of the model here.
 */
@HiltViewModel
class LetterViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val store: LetterStore,
    private val now: Now,
    private val problems: ProblemLog,
) : ViewModel() {

    sealed interface State {
        data object Loading : State
        data class Shown(val letter: WeeklyLetter) : State
        data object Missing : State
        data object Unreadable : State
    }

    private val week: Long = checkNotNull(savedState.get<Long>(WEEK)) { "a letter's page needs its week" }
    private val local = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = local.asStateFlow()

    init {
        viewModelScope.launch {
            // A failed read is logged by its kind only (D8) and said; a read that finds nothing is a letter not there.
            val read = LetterRun.guarded(problems, "not read") { Read(store.of(week)) }
            val letter = read?.letter
            local.value = when {
                read == null -> State.Unreadable
                letter == null -> State.Missing
                else -> State.Shown(letter)
            }
            if (letter != null && letter.readAtMillis == null) {
                // Shown whether or not this is written: a note left on the day is the whole cost of a failure.
                LetterRun.guarded(problems, "not marked read") { store.markRead(week, now()) }
            }
        }
    }

    private class Read(val letter: WeeklyLetter?)

    companion object {
        /** The navigation argument: the letter's week's Monday (epoch day). */
        const val WEEK = "week"
    }
}
