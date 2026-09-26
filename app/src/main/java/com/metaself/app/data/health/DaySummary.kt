package com.metaself.app.data.health

import kotlin.math.roundToInt

/**
 * One day of the record, summarised (D69). Pure.
 *
 * Every figure says where it came from: TOTAL (Health Connect's de-duplicated aggregate), READ (one
 * record), COMPUTED (worked out here from the day's rows) or CORRECTED (the owner's figure, D12d).
 * No data is null, never zero; a day with nothing at all has no summary.
 */
object DaySummary {

    private val ASLEEP = setOf("LIGHT", "DEEP", "REM", "SLEEPING")
    private val AWAKE = setOf("AWAKE", "AWAKE_IN_BED", "OUT_OF_BED")

    fun of(
        epochDay: Long,
        totals: DayTotals,
        readings: List<HealthReadingEntity>,
        nights: List<SleepNight>,
        workouts: List<WorkoutEntity>,
        correction: MovementCorrectionEntity?,
        nowMillis: Long,
    ): HealthDayEntity? {
        fun mean(kind: String): Double? =
            readings.filter { it.kind == kind }.map { it.value }.takeIf { it.isNotEmpty() }?.average()

        val resting = readings.filter { it.kind == "RESTING_HEART_RATE" }.maxByOrNull { it.startMillis }
        val steps = correction?.steps ?: totals.steps
        val active = correction?.activeKcal ?: totals.activeKcal
        val visible = workouts.filterNot { it.hidden }
        val sleep = sleepOf(nights)

        val day = HealthDayEntity(
            epochDay = epochDay,
            computedAtMillis = nowMillis,
            steps = steps,
            stepsSource = source(steps, corrected = correction?.steps != null, otherwise = "TOTAL"),
            distanceM = totals.distanceM,
            distanceSource = totals.distanceM?.let { "TOTAL" },
            activeKcal = active,
            activeKcalSource = source(active, corrected = correction?.activeKcal != null, otherwise = "TOTAL"),
            totalKcal = totals.totalKcal,
            totalKcalSource = totals.totalKcal?.let { "TOTAL" },
            restingHeartRate = resting?.value?.roundToInt(),
            restingHeartRateSource = resting?.let { "READ" },
            avgHeartRate = mean("HEART_RATE")?.roundToInt(),
            avgHeartRateSource = mean("HEART_RATE")?.let { "COMPUTED" },
            hrvMs = mean("HRV_RMSSD"),
            hrvSource = mean("HRV_RMSSD")?.let { "COMPUTED" },
            oxygenPct = mean("OXYGEN_SATURATION"),
            oxygenSource = mean("OXYGEN_SATURATION")?.let { "COMPUTED" },
            respiratoryRate = mean("RESPIRATORY_RATE"),
            respiratoryRateSource = mean("RESPIRATORY_RATE")?.let { "COMPUTED" },
            sleepMinutes = sleep?.asleep,
            deepMinutes = sleep?.stage("DEEP"),
            lightMinutes = sleep?.stage("LIGHT"),
            remMinutes = sleep?.stage("REM"),
            awakeMinutes = sleep?.awake,
            sleepSource = sleep?.let { "COMPUTED" },
            workoutCount = visible.size.takeIf { it > 0 },
            workoutMinutes = visible.sumOf { it.durationMinutes }.takeIf { visible.isNotEmpty() },
            workoutSource = "COMPUTED".takeIf { visible.isNotEmpty() },
        )
        return day.takeUnless { it.copy(computedAtMillis = 0) == HealthDayEntity(epochDay, 0) }
    }

    private fun source(value: Int?, corrected: Boolean, otherwise: String): String? = when {
        value == null -> null
        corrected -> "CORRECTED"
        else -> otherwise
    }

    private class Sleep(val asleep: Int, val awake: Int?, private val byStage: Map<String, Int>) {
        fun stage(name: String): Int? = byStage[name]
    }

    /** Stage minutes when there are stages; the whole night as sleep when there are none. */
    private fun sleepOf(nights: List<SleepNight>): Sleep? {
        if (nights.isEmpty()) return null
        val stages = nights.flatMap { it.stages }
        if (stages.isEmpty()) {
            val whole = nights.sumOf { minutes(it.session.startMillis, it.session.endMillis) }
            return Sleep(asleep = whole, awake = null, byStage = emptyMap())
        }
        val byStage = stages.groupBy { it.stage }.mapValues { (_, spans) ->
            spans.sumOf { minutes(it.startMillis, it.endMillis) }
        }
        return Sleep(
            asleep = byStage.filterKeys { it in ASLEEP }.values.sum(),
            awake = byStage.filterKeys { it in AWAKE }.values.sum().takeIf { byStage.keys.any { it in AWAKE } },
            byStage = byStage,
        )
    }

    private fun minutes(from: Long, to: Long): Int = ((to - from) / 60_000).toInt()
}
