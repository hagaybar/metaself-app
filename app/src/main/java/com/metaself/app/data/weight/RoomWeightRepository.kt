package com.metaself.app.data.weight

import com.metaself.app.domain.weight.WeightReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomWeightRepository @Inject constructor(
    private val dao: WeightDao,
) : WeightRepository {

    override val readings: Flow<List<WeightReading>> = dao.observeAll().map { rows ->
        // A row outside plausible bounds can only come from a corrupted store. Dropping it is right
        // here where keeping it was right for food: an impossible weight would distort the trend
        // for every reading after it, whereas an odd meal only distorts its own day.
        rows.mapNotNull { row ->
            runCatching { WeightReading(epochDay = row.epochDay, kg = row.kg) }.getOrNull()
        }
    }

    override suspend fun log(reading: WeightReading) {
        dao.upsert(WeightEntity(epochDay = reading.epochDay, kg = reading.kg))
    }

    override suspend fun delete(epochDay: Long) {
        dao.delete(epochDay)
    }
}
