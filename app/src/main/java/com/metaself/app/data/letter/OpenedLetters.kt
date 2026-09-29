package com.metaself.app.data.letter

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What a notification's tap asked to open (design question 16), until the navigation takes it. */
sealed interface LetterOpen {
    data class Letter(val weekMonday: Long) : LetterOpen
    data object List : LetterOpen
}

/**
 * The one letter open a notification's tap asked for and the navigation has not yet taken (D103): the
 * activity puts it here, the navigation takes it and opens the letter or Weekly letters. A later tap
 * before the first is taken replaces it.
 */
@Singleton
class OpenedLetters @Inject constructor() {
    private val held = MutableStateFlow<LetterOpen?>(null)
    val pending: StateFlow<LetterOpen?> = held.asStateFlow()

    fun offer(open: LetterOpen) {
        held.value = open
    }

    /** The waiting open, handed out once. */
    fun take(): LetterOpen? = held.getAndSet(null)

    private fun <T> MutableStateFlow<T>.getAndSet(value: T): T {
        while (true) {
            val now = this.value
            if (compareAndSet(now, value)) return now
        }
    }
}

/** The letter a launch asks to open: the notification's extras (D103). Null for any other launch. */
internal fun letterOpen(weekMonday: Long?, list: Boolean): LetterOpen? = when {
    weekMonday != null -> LetterOpen.Letter(weekMonday)
    list -> LetterOpen.List
    else -> null
}
