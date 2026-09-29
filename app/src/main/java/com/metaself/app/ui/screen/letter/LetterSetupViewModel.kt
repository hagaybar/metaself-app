package com.metaself.app.ui.screen.letter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.health.BackgroundHealthRead
import com.metaself.app.data.letter.LetterNoteStore
import com.metaself.app.data.letter.LetterRun
import com.metaself.app.data.letter.LetterSettingsStore
import com.metaself.app.data.letter.LetterSetup
import com.metaself.app.data.letter.NotificationAccess
import com.metaself.app.ui.letter.LetterWording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The one-time note on today that sets the weekly letter up ([LetterSetup]). The permissions are read
 * again each time the day comes to the front ([lookedAt]); the asks themselves are the activity's.
 */
@HiltViewModel
class LetterSetupViewModel @Inject constructor(
    settings: LetterSettingsStore,
    private val notes: LetterNoteStore,
    private val notifications: NotificationAccess,
    private val background: BackgroundHealthRead,
    private val problems: ProblemLog,
) : ViewModel() {

    /** The note: its words, and what Set up asks for, in this order. */
    data class Note(val text: String, val askNotifications: Boolean, val askBackground: Boolean)

    private data class Held(val notificationsAllowed: Boolean, val backgroundOffered: Boolean, val backgroundGranted: Boolean)

    /** Null until first read: no note is drawn before the permissions are known. */
    private val held = MutableStateFlow<Held?>(null)

    val note: StateFlow<Note?> = combine(settings.settings, notes.setupDone, held) { chosen, done, h ->
        h ?: return@combine null
        LetterSetup.asks(chosen.on, done, notifications.needed, h.notificationsAllowed, h.backgroundOffered, h.backgroundGranted)
            ?.let { asks -> LetterWording.setupNote(chosen.hour, asks.notifications, asks.background)?.let { Note(it, asks.notifications, asks.background) } }
    }
        .catch { failure ->
            if (failure is CancellationException) throw failure
            runCatching { problems.record(LetterRun.KIND, "setup not read: ${failure::class.java.simpleName}") }
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** When the day comes to the front, and when a permission screen returns. */
    fun lookedAt() {
        viewModelScope.launch {
            LetterRun.guarded(problems, "setup not checked") {
                held.value = Held(notifications.allowed(), background.offered(), background.granted())
            }
        }
    }

    /** Put away, or Set up gone through whatever was chosen: the note never returns. */
    fun done() {
        viewModelScope.launch {
            LetterRun.guarded(problems, "setup not put away") { notes.markSetupDone() }
        }
    }
}
