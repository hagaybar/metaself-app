package com.metaself.app.data.lifecycle

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the app is in the foreground: some activity of it at least STARTED. Health Connect refuses
 * reads from an app that is not, unless it holds READ_HEALTH_DATA_IN_BACKGROUND (which this app does
 * not ask for), so the copying of the health record runs only while this is true.
 */
interface AppForeground {
    val isForeground: StateFlow<Boolean>

    companion object {
        /** Always in the foreground: for code and tests that do not care. */
        val ALWAYS: AppForeground = object : AppForeground {
            override val isForeground: StateFlow<Boolean> = MutableStateFlow(true)
        }
    }
}

/**
 * [AppForeground] from the process's own lifecycle (`ProcessLifecycleOwner`): STARTED when the first
 * activity starts, and back below it about 700 ms after the last one stops (its own delay, so a
 * rotation is not a trip to the background).
 *
 * The lifecycle may only be observed on the main thread; built anywhere else, the observer is added
 * from there, and until then this says false.
 */
@Singleton
class ProcessAppForeground @Inject constructor() : AppForeground {

    private val state = MutableStateFlow(false)
    override val isForeground: StateFlow<Boolean> = state

    init {
        if (Looper.myLooper() == Looper.getMainLooper()) observe() else Handler(Looper.getMainLooper()).post(::observe)
    }

    private fun observe() {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        state.value = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        lifecycle.addObserver(
            LifecycleEventObserver { _, event -> state.value = event.targetState.isAtLeast(Lifecycle.State.STARTED) },
        )
    }
}
