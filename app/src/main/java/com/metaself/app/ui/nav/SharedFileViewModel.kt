package com.metaself.app.ui.nav

import androidx.lifecycle.ViewModel
import com.metaself.app.data.health.SharedWorkoutFiles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Whether a workout file shared to MetaSelf (D82) is waiting, so the navigation can open Movement,
 * and the taking of it, which the Movement screen does only while it is on screen
 * ([com.metaself.app.ui.screen.movement.TakeSharedWorkoutFile]).
 */
@HiltViewModel
class SharedFileViewModel @Inject constructor(private val shared: SharedWorkoutFiles) : ViewModel() {
    val pending: StateFlow<String?> = shared.pending

    /** The waiting file, handed out once. */
    fun take(): String? = shared.take()
}
