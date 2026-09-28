package com.metaself.app.data.health

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one workout file shared to MetaSelf and not yet imported (D82): the activity puts it here, the
 * navigation opens Movement when there is one, and the Movement screen takes it. A second share
 * before the first is taken replaces it.
 */
@Singleton
class SharedWorkoutFiles @Inject constructor() {

    private val held = MutableStateFlow<String?>(null)

    /** The content Uri of the file waiting, as a string; null when none is. */
    val pending: StateFlow<String?> = held.asStateFlow()

    fun offer(uri: String) {
        held.value = uri
    }

    /** The waiting file, handed out once. */
    fun take(): String? = held.getAndUpdateToNull()

    private fun MutableStateFlow<String?>.getAndUpdateToNull(): String? {
        while (true) {
            val now = value
            if (compareAndSet(now, null)) return now
        }
    }
}
