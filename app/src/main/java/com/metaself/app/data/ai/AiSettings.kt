package com.metaself.app.data.ai

import kotlinx.coroutines.flow.Flow

/**
 * The two things about the model the owner can change, and one he cannot.
 *
 * @property model changeable because model names move faster than this app is rebuilt.
 * @property dailyCeiling how many calls a day. It does not protect a leaked key — the thief is not
 *   using this app — it bounds a retry loop or a bug that calls the model on every keystroke.
 * @property usedToday counted on the device and reported nowhere (D16).
 */
data class AiSettings(
    val model: String = EstimatePrompt.DEFAULT_MODEL,
    val dailyCeiling: Int = DEFAULT_CEILING,
    val usedToday: Int = 0,
) {
    val remainingToday: Int get() = (dailyCeiling - usedToday).coerceAtLeast(0)

    companion object {
        const val DEFAULT_CEILING = 30
    }
}

interface AiSettingsStore {

    /** The settings, with [AiSettings.usedToday] already reset if the day has turned over. */
    val settings: Flow<AiSettings>

    suspend fun setModel(model: String)

    suspend fun setDailyCeiling(ceiling: Int)

    /** Records that a call was actually made. Only ever called after one happened. */
    suspend fun recordCall()
}
