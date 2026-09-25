package com.metaself.app.data.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Remembered request profiles held in memory (D57), for tests; [writes] counts every remember. */
class FakeRequestProfileStore(initial: Map<String, RequestProfile> = emptyMap()) : RequestProfileStore {

    val remembered = MutableStateFlow(initial)
    var writes: Int = 0
        private set

    override fun profileFor(model: String): Flow<RequestProfile?> = remembered.map { it[model] }

    override suspend fun remember(model: String, profile: RequestProfile) {
        writes++
        remembered.value = remembered.value + (model to profile)
    }
}
