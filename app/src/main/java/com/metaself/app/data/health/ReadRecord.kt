package com.metaself.app.data.health

import com.metaself.app.domain.health.HealthKind

/**
 * One Health Connect record, translated into this app's terms by the reader and nothing else.
 * Free of Health Connect types, so everything downstream of the reader is testable on any machine.
 */
sealed interface ReadRecord {
    val origin: String
    val recordId: String

    /** A simple reading: one sample, or a series (heart rate). */
    data class Reading(
        val kind: HealthKind,
        override val origin: String,
        override val recordId: String,
        val samples: List<Sample>,
    ) : ReadRecord

    data class Night(
        override val origin: String,
        override val recordId: String,
        val startMillis: Long,
        val endMillis: Long,
        val title: String?,
        val stages: List<StageSpan>,
    ) : ReadRecord

    /**
     * @property kind a `WorkoutKind` name (`WorkoutKinds.of`).
     * @property distanceM, energyKcal what Health Connect's aggregation gave over the session's time,
     *   or null when it gave nothing (D4: null, never zero).
     */
    data class Session(
        override val origin: String,
        override val recordId: String,
        val startMillis: Long,
        val endMillis: Long,
        val kind: String,
        val title: String?,
        val distanceM: Int?,
        val energyKcal: Int?,
    ) : ReadRecord
}

/** A value at a moment ([endMillis] null) or over a span. */
data class Sample(val startMillis: Long, val endMillis: Long?, val value: Double)

/** @property stage AWAKE, LIGHT, DEEP, REM, SLEEPING, OUT_OF_BED, AWAKE_IN_BED or UNKNOWN. */
data class StageSpan(val stage: String, val startMillis: Long, val endMillis: Long)
