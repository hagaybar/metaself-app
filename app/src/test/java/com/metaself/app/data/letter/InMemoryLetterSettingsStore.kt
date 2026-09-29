package com.metaself.app.data.letter

import kotlinx.coroutines.flow.MutableStateFlow

/** In memory, clamping the hour as the real store does. */
class InMemoryLetterSettingsStore(initial: LetterSettings = LetterSettings()) : LetterSettingsStore {
    override val settings = MutableStateFlow(initial)

    override suspend fun setOn(on: Boolean) {
        settings.value = settings.value.copy(on = on)
    }

    override suspend fun setHour(hour: Int) {
        settings.value = settings.value.copy(hour = hour.coerceIn(LetterSettings.HOURS))
    }
}
