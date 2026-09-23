package com.metaself.app.data.weight

import com.metaself.app.domain.weight.WeightReading
import kotlinx.coroutines.flow.Flow

/**
 * Every weight ever logged, oldest first.
 *
 * The whole history rather than a window, because the trend is a walk forwards from the first
 * reading and a window would start it from an arbitrary point. A few thousand rows of two numbers is
 * nothing; if it ever is, the fix is to store the trend alongside the reading, not to truncate.
 */
interface WeightRepository {

    val readings: Flow<List<WeightReading>>

    suspend fun log(reading: WeightReading)

    suspend fun delete(epochDay: Long)
}
