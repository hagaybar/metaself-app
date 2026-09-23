package com.metaself.app.data.weight

import com.metaself.app.domain.weight.WeightReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Weights in memory.
 *
 * Two private copies of this already exist, one inside `DayViewModelTest` and one inside
 * `WeightViewModelTest`. This is a third, shared, for the walk — which needs one only because the day
 * screen will not build without a weight repository, not because it weighs anything.
 */
class InMemoryWeightRepository(initial: List<WeightReading> = emptyList()) : WeightRepository {

    private val state = MutableStateFlow(initial)

    override val readings: Flow<List<WeightReading>> = state

    override suspend fun log(reading: WeightReading) {
        state.value = state.value.filterNot { it.epochDay == reading.epochDay } + reading
    }

    override suspend fun delete(epochDay: Long) {
        state.value = state.value.filterNot { it.epochDay == epochDay }
    }
}
