package com.metaself.app.ui.screen.letter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.ai.AiSettings
import com.metaself.app.data.ai.AiSettingsStore
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.letter.LetterRun
import com.metaself.app.data.letter.LetterSettingsStore
import com.metaself.app.data.letter.LetterStore
import com.metaself.app.data.letter.WeeklyLetterJob
import com.metaself.app.data.letter.WriteWeeklyLetter
import com.metaself.app.di.ApplicationScope
import com.metaself.app.domain.letter.LetterSchedule
import com.metaself.app.domain.letter.WeeklyLetter
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.letter.LetterWording
import com.metaself.app.ui.outlived
import com.metaself.app.ui.trainer.TrainerWording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Weekly letters (D103): every letter, newest first, and **Write it now** (D99, design question 19) when
 * the week there is to write has no letter and is not quiet. The model is asked only from [writeNow], a
 * tap; the ask and the storing of its answer run in [outliving], so leaving the page mid-request does not
 * throw away a paid answer. A failure is logged as the Sunday run's is — by kind, never the model's
 * answer or the request's words (D8).
 */
@HiltViewModel
class LettersViewModel internal constructor(
    private val store: LetterStore,
    private val job: WeeklyLetterJob,
    settings: LetterSettingsStore,
    ai: AiSettingsStore,
    private val problems: ProblemLog,
    private val outliving: CoroutineScope,
    private val clock: () -> LocalDateTime,
) : ViewModel() {

    @Inject
    constructor(
        store: LetterStore,
        job: WeeklyLetterJob,
        settings: LetterSettingsStore,
        ai: AiSettingsStore,
        problems: ProblemLog,
        @ApplicationScope outliving: CoroutineScope,
    ) : this(store, job, settings, ai, problems, outliving, { LocalDateTime.now() })

    /** One letter in the list: its week, its headline, and whether it has been opened. */
    data class Row(val weekMonday: Long, val week: String, val headline: String, val new: Boolean)

    /**
     * @property writeWeek the week Write it now writes, when it is offered.
     * @property writing Write it now was tapped and has not finished; a second tap is ignored.
     * @property said what the last Write it now came to, when it was not a letter: a quiet week, or a failure.
     */
    data class State(
        val loading: Boolean = true,
        val letters: List<Row> = emptyList(),
        val unreadable: Boolean = false,
        val writeWeek: Long? = null,
        val writing: Boolean = false,
        val said: String? = null,
        val refused: ActionRefused? = null,
        val ceiling: Int = AiSettings.DEFAULT_CEILING,
    ) {
        val canWriteNow: Boolean get() = writeWeek != null
    }

    private data class Listed(val letters: List<WeeklyLetter>?, val writeWeek: Long?)

    private val local = MutableStateFlow(State())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val listed = combine(store.observeAll(), settings.settings) { letters, chosen -> letters to chosen.hour }
        .mapLatest { (letters, hour) -> Listed(letters, offered(hour)) }
        .catch { failure ->
            if (failure is CancellationException) throw failure
            runCatching { problems.record(LetterRun.KIND, "not listed: ${failure::class.java.simpleName}") }
            emit(Listed(null, null))
        }

    val state: StateFlow<State> = combine(local, listed, ai.settings.catchToDefault()) { s, l, a ->
        s.copy(
            loading = false,
            letters = l.letters.orEmpty().map { Row(it.weekMonday, LetterWording.weekShort(it.weekMonday), it.texts.headline, it.readAtMillis == null) },
            unreadable = l.letters == null,
            writeWeek = l.writeWeek,
            ceiling = a.dailyCeiling,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    /** The week there is to write now (design question 19), if it still wants a letter; read only, never an ask. */
    private suspend fun offered(hour: Int): Long? {
        val now = clock()
        val week = LetterSchedule.weekToWrite(now, hour) ?: (MovementWeek.mondayOf(now.toLocalDate().toEpochDay()) - 7)
        return week.takeIf { LetterRun.guarded(problems, "not checked") { job.wanted(it) } == true }
    }

    /** Write it now: one ask, for the week offered; a second tap while it runs does nothing. */
    fun writeNow() {
        val week = state.value.writeWeek ?: return
        if (local.value.writing) return
        local.update { it.copy(writing = true, said = null, refused = null) }
        outlived(
            outliving,
            problems,
            onRefused = { local.update { it.copy(writing = false, refused = ActionRefused.NOTHING_CHANGED) } },
            // Through the Sunday run's own guard: a failure is logged by kind, and never reaches the
            // screen's logging, which would write the exception's message.
            work = { LetterRun.guarded(problems, "not written") { job.write(week, copy = false) } },
        ) { outcome ->
            local.update {
                when (outcome) {
                    null -> it.copy(writing = false, refused = ActionRefused.NOTHING_CHANGED)
                    is WriteWeeklyLetter.Outcome.Written, WriteWeeklyLetter.Outcome.AlreadyWritten -> it.copy(writing = false)
                    WriteWeeklyLetter.Outcome.Quiet -> it.copy(writing = false, said = LetterWording.QUIET)
                    is WriteWeeklyLetter.Outcome.Retry -> it.copy(writing = false, said = TrainerWording.failure(outcome.failure))
                    is WriteWeeklyLetter.Outcome.GiveUp -> it.copy(writing = false, said = TrainerWording.failure(outcome.failure))
                }
            }
        }
    }

    /** The ceiling is only said beside Write it now; a settings store that cannot be read says the default. */
    private fun Flow<AiSettings>.catchToDefault() = catch { failure ->
        if (failure is CancellationException) throw failure
        emit(AiSettings())
    }
}
