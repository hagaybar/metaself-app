package com.metaself.app.ui.screen.letter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.health.BackgroundHealthRead
import com.metaself.app.data.letter.LetterScheduling
import com.metaself.app.data.letter.LetterSettings
import com.metaself.app.data.letter.LetterSettingsStore
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.guarded
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * The weekly letter's setting (D99): the switch, the Sunday hour, and the one-time ask for Health
 * Connect's background read, shown only where the phone offers it and it is not held. Writing a choice
 * re-queues the Sunday run. Nothing is asked of the model here.
 */
@HiltViewModel
class LetterSettingsViewModel @Inject constructor(
    private val settings: LetterSettingsStore,
    private val scheduler: LetterScheduling,
    private val background: BackgroundHealthRead,
    private val problems: ProblemLog,
) : ViewModel() {

    data class State(
        val on: Boolean = true,
        val hour: Int = LetterSettings.DEFAULT_HOUR,
        val askBackground: Boolean = false,
        val refused: ActionRefused? = null,
    )

    private val local = MutableStateFlow(State())

    val state: StateFlow<State> = combine(local, settings.settings) { s, chosen -> s.copy(on = chosen.on, hour = chosen.hour) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    /** When the page is shown, and when Health Connect's own screen returns: whether to offer the ask. */
    fun lookedAt() {
        guarded(problems, onRefused = { local.update { it.copy(askBackground = false) } }) {
            val ask = background.offered() && !background.granted()
            local.update { it.copy(askBackground = ask) }
        }
    }

    fun setOn(on: Boolean) = write { settings.setOn(on) }

    fun setHour(hour: Int) = write { settings.setHour(hour) }

    private fun write(change: suspend () -> Unit) {
        local.update { it.copy(refused = null) }
        guarded(problems, onRefused = { local.update { it.copy(refused = ActionRefused.MAYBE_PARTIAL) } }) {
            change()
            scheduler.schedule()
        }
    }
}
