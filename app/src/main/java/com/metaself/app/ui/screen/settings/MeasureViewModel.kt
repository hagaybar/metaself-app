package com.metaself.app.ui.screen.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.diagnostics.measure.Diagnostics
import com.metaself.app.data.diagnostics.measure.MeasureRecorder
import com.metaself.app.data.diagnostics.measure.MeasureSwitch
import com.metaself.app.data.diagnostics.measure.MeasureWindow
import com.metaself.app.ui.guarded
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** D152 (temporary): the switch and the download on Recent problems. */
@HiltViewModel
class MeasureViewModel @Inject constructor(
    private val switch: MeasureSwitch,
    private val diagnostics: Diagnostics,
    private val recorder: MeasureRecorder,
    private val problems: ProblemLog,
) : ViewModel() {

    /** [untilMillis] while on; [saved] the last download's outcome, null before one. */
    data class State(val untilMillis: Long? = null, val saved: Boolean? = null)

    private val saved = MutableStateFlow<Boolean?>(null)

    val state: StateFlow<State> = combine(switch.times, saved) { times, lastSave ->
        val on = MeasureWindow.isOn(times.startedAt, times.until, recorder.clock.wallMillis())
        State(untilMillis = times.until.takeIf { on }, saved = lastSave)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun setOn(on: Boolean) = guarded(problems, onRefused = {}) {
        if (on) switch.turnOn() else switch.turnOff()
    }

    fun download(uri: Uri) = guarded(problems, onRefused = { saved.value = false }) {
        saved.value = null
        saved.value = diagnostics.writeTo(uri)
    }
}
