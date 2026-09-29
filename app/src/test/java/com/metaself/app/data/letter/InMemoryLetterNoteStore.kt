package com.metaself.app.data.letter

import kotlinx.coroutines.flow.MutableStateFlow

/** In memory: the day's note put away, the setup note done, and the week the Sunday run gave up on. */
class InMemoryLetterNoteStore : LetterNoteStore {
    override val dismissed = MutableStateFlow<Long?>(null)
    override val setupDone = MutableStateFlow(false)
    override val gaveUp = MutableStateFlow<Long?>(null)

    override suspend fun dismiss(weekMonday: Long) {
        dismissed.value = weekMonday
    }

    override suspend fun markSetupDone() {
        setupDone.value = true
    }

    override suspend fun markGaveUp(weekMonday: Long) {
        gaveUp.value = weekMonday
    }
}
