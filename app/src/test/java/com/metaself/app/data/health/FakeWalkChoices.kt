package com.metaself.app.data.health

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Whose walks count (D81), held in memory. Starts with [uncounted] switched off. */
class FakeWalkChoices(vararg uncounted: String) : WalkChoices {

    override val uncounted = MutableStateFlow(uncounted.toSet())

    override suspend fun setCounted(origin: String, counted: Boolean) {
        this.uncounted.update { if (counted) it - origin else it + origin }
    }
}
