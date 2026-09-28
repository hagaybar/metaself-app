package com.metaself.app.ui.screen.movement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Takes a workout file shared to MetaSelf (D82) — only while this Movement screen is the one on
 * screen — and hands it to [onFile].
 *
 * The lifecycle is the composition's own: inside a navigation destination that is the back stack
 * entry, which is RESUMED only while it is the top destination of a started activity. So a Movement
 * entry left under another screen, or a Movement screen in an activity that has gone to the
 * background (a share from another app starts a fresh activity in that app's task, and the old one
 * stays alive, stopped), never takes the file. The Movement screen the share actually opens does.
 */
@Composable
fun TakeSharedWorkoutFile(pending: StateFlow<String?>, take: () -> String?, onFile: (String) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentTake by rememberUpdatedState(take)
    val currentOnFile by rememberUpdatedState(onFile)
    LaunchedEffect(lifecycle, pending) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            pending.filterNotNull().collect { currentTake()?.let(currentOnFile) }
        }
    }
}
