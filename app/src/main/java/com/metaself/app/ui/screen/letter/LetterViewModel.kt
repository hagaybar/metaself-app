package com.metaself.app.ui.screen.letter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.letter.LetterRun
import com.metaself.app.data.letter.LetterStore
import com.metaself.app.data.letter.WeeklyLetterJob
import com.metaself.app.data.letter.WriteWeeklyLetter
import com.metaself.app.data.time.Now
import com.metaself.app.di.ApplicationScope
import com.metaself.app.domain.letter.WeeklyLetter
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.letter.LetterWording
import com.metaself.app.ui.outlived
import com.metaself.app.ui.trainer.TrainerWording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One weekly letter (D103), read once when the page opens; the first time it is shown it is marked read,
 * which takes its note off the day and its "New" off the list. The model is asked only from [writeAgain]
 * (D144), on the newest letter, after its confirmation — never on opening. The ask and the storing of its
 * answer run in [outliving], so leaving the page mid-request does not throw away a paid answer; a failure
 * is logged as the Sunday run's is, by kind only (D8).
 */
@HiltViewModel
class LetterViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val store: LetterStore,
    private val now: Now,
    private val problems: ProblemLog,
    private val job: WeeklyLetterJob,
    @ApplicationScope private val outliving: CoroutineScope,
) : ViewModel() {

    sealed interface State {
        data object Loading : State

        /**
         * @property newest the newest letter stored: only it can be written again (D144).
         * @property confirming "Write this letter again" was tapped; the confirmation is showing.
         * @property writing the trainer is being asked again; the letter shown stays until it answers.
         * @property said what the last ask came to when it was not a letter: a quiet week, or a failure.
         */
        data class Shown(
            val letter: WeeklyLetter,
            val newest: Boolean = false,
            val confirming: Boolean = false,
            val writing: Boolean = false,
            val said: String? = null,
            val refused: ActionRefused? = null,
        ) : State
        data object Missing : State
        data object Unreadable : State
    }

    private val week: Long = checkNotNull(savedState.get<Long>(WEEK)) { "a letter's page needs its week" }
    private val local = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = local.asStateFlow()

    init {
        viewModelScope.launch {
            // A failed read is logged by its kind only (D8) and said; a read that finds nothing is a letter not there.
            val read = LetterRun.guarded(problems, "not read") {
                Read(store.of(week), store.observeAll().first().maxOfOrNull { it.weekMonday } == week)
            }
            val letter = read?.letter
            local.value = when {
                read == null -> State.Unreadable
                letter == null -> State.Missing
                else -> State.Shown(letter, newest = read.newest)
            }
            if (letter != null && letter.readAtMillis == null) {
                // Shown whether or not this is written: a note left on the day is the whole cost of a failure.
                LetterRun.guarded(problems, "not marked read") { store.markRead(week, now()) }
            }
        }
    }

    /** D144: "Write this letter again" — shows the confirmation; asks nothing. Only on the newest letter. */
    fun offerAgain() = change { if (it.newest && !it.writing) it.copy(confirming = true, said = null, refused = null) else it }

    /** D144: "Keep this one". */
    fun keep() = change { it.copy(confirming = false) }

    /** D144: "Write it again" — one ask; written, the new letter replaces this one; otherwise this one stays. */
    fun writeAgain() {
        val shown = local.value as? State.Shown ?: return
        if (!shown.newest || !shown.confirming || shown.writing) return
        change { it.copy(confirming = false, writing = true) }
        outlived(
            outliving,
            problems,
            onRefused = { change { it.copy(writing = false, refused = ActionRefused.NOTHING_CHANGED) } },
            // Through the Sunday run's own guard: a failure is logged by kind, never with its message.
            work = { LetterRun.guarded(problems, "not written again") { job.writeAgain(week) } },
        ) { outcome ->
            change {
                when (outcome) {
                    null -> it.copy(writing = false, refused = ActionRefused.NOTHING_CHANGED)
                    is WriteWeeklyLetter.Outcome.Written -> State.Shown(outcome.letter, newest = true)
                    WriteWeeklyLetter.Outcome.AlreadyWritten -> it.copy(writing = false)
                    WriteWeeklyLetter.Outcome.Quiet -> it.copy(writing = false, said = LetterWording.QUIET)
                    is WriteWeeklyLetter.Outcome.Retry -> it.copy(writing = false, said = TrainerWording.failure(outcome.failure))
                    is WriteWeeklyLetter.Outcome.GiveUp -> it.copy(writing = false, said = TrainerWording.failure(outcome.failure))
                }
            }
        }
    }

    private fun change(edit: (State.Shown) -> State.Shown) = local.update { (it as? State.Shown)?.let(edit) ?: it }

    private class Read(val letter: WeeklyLetter?, val newest: Boolean)

    companion object {
        /** The navigation argument: the letter's week's Monday (epoch day). */
        const val WEEK = "week"
    }
}
