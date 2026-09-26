package com.metaself.app.data.health

import java.time.Instant
import java.time.ZoneId

/**
 * Read records → rows, with every day in the phone's own zone (D68: a reading or a workout belongs to
 * the local day it starts; a night to the local day it ends).
 */
class HealthRows(private val zone: ZoneId) {

    fun dayOf(millis: Long): Long = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    fun readings(record: ReadRecord.Reading): List<HealthReadingEntity> =
        record.samples.mapIndexed { index, sample ->
            HealthReadingEntity(
                kind = record.kind.name,
                startMillis = sample.startMillis,
                endMillis = sample.endMillis,
                value = sample.value,
                unit = requireNotNull(record.kind.unit) { "${record.kind} is not a simple reading" },
                origin = record.origin,
                recordId = record.recordId,
                sampleIndex = index,
                epochDay = dayOf(sample.startMillis),
            )
        }

    /** The night, and its stages with `sessionId` 0 until the night is inserted. */
    fun night(record: ReadRecord.Night): Pair<SleepSessionEntity, List<SleepStageEntity>> =
        SleepSessionEntity(
            epochDay = dayOf(record.endMillis),
            startMillis = record.startMillis,
            endMillis = record.endMillis,
            origin = record.origin,
            recordId = record.recordId,
            title = record.title,
        ) to record.stages.map {
            SleepStageEntity(sessionId = 0, stage = it.stage, startMillis = it.startMillis, endMillis = it.endMillis)
        }

    fun workout(record: ReadRecord.Session): WorkoutEntity = WorkoutEntity(
        epochDay = dayOf(record.startMillis),
        startedAtMillis = record.startMillis,
        durationMinutes = ((record.endMillis - record.startMillis) / 60_000).toInt(),
        kind = record.kind,
        title = record.title,
        distanceM = record.distanceM,
        energyKcal = record.energyKcal,
        energySource = if (record.energyKcal != null) "BAND" else "NONE",
        effort = null,
        source = "SYNCED",
        origin = record.origin,
        originId = record.recordId,
        note = null,
    )
}
