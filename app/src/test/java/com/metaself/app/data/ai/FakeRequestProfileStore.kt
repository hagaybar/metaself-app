package com.metaself.app.data.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Remembered request profiles held in memory (D57), for tests; [writes] counts every everyday
 * remember, [deep] holds what a final analysis remembered (D58 §12.5).
 */
class FakeRequestProfileStore(
    initial: Map<String, RequestProfile> = emptyMap(),
    deepInitial: Map<String, DeepLevel> = emptyMap(),
) : RequestProfileStore {

    val remembered = MutableStateFlow(initial)
    val deep = MutableStateFlow(deepInitial)
    var writes: Int = 0
        private set

    override fun profileFor(model: String): Flow<RequestProfile?> = remembered.map { it[model] }

    override suspend fun remember(model: String, profile: RequestProfile) {
        writes++
        remembered.value = remembered.value + (model to profile)
    }

    override fun deepFor(model: String): Flow<DeepLevel?> = deep.map { it[model] }

    override suspend fun rememberDeep(model: String, effort: String?) {
        deep.value = deep.value + (model to DeepLevel(effort))
    }
}
