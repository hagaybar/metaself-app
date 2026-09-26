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

    /** UNKNOWN is a stretch inside a sleep session its app did not classify, so it is asleep. */
    private val ASLEEP = setOf("LIGHT", "DEEP", "REM", "SLEEPING", "UNKNOWN")
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

        val heartRate = mean("HEART_RATE")?.roundToInt()
        val hrv = mean("HRV_RMSSD")
        val oxygen = mean("OXYGEN_SATURATION")
        val breathing = mean("RESPIRATORY_RATE")
        val resting = readings.filter { it.kind == "RESTING_HEART_RATE" }.maxByOrNull { it.startMillis }
        val steps = correction?.steps ?: totals.steps
        val active = correction?.activeKcal ?: totals.activeKcal
        val visible = workouts.filterNot { it.hidden }
        val sleep = sleepOf(nights)

        val nothing = steps == null && totals.distanceM == null && active == null && totals.totalKcal == null &&
            resting == null && heartRate == null && hrv == null && oxygen == null && breathing == null &&
            sleep == null && visible.isEmpty()
        if (nothing) return null

        return HealthDayEntity(
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
            avgHeartRate = heartRate,
            avgHeartRateSource = heartRate?.let { "COMPUTED" },
            hrvMs = hrv,
            hrvSource = hrv?.let { "COMPUTED" },
            oxygenPct = oxygen,
            oxygenSource = oxygen?.let { "COMPUTED" },
            respiratoryRate = breathing,
            respiratoryRateSource = breathing?.let { "COMPUTED" },
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
    }

    private fun source(value: Int?, corrected: Boolean, otherwise: String): String? = when {
        value == null -> null
        corrected -> "CORRECTED"
        else -> otherwise
    }

    private class Sleep(val asleep: Int, val awake: Int?, private val byStage: Map<String, Int>) {
        fun stage(name: String): Int? = byStage[name]
    }

    /**
     * The day's sleep. Nights that overlap are one night recorded by more than one app, so only one
     * of them counts: the longest, then the one with more stages, then the smaller record id. Each
     * night kept is counted on its own — its stages when it has any, its whole length as sleep when
     * it has none — and the milliseconds are added up before they are rounded to minutes.
     */
    private fun sleepOf(nights: List<SleepNight>): Sleep? {
        if (nights.isEmpty()) return null
        val stageMillis = mutableMapOf<String, Long>()
        var wholeMillis = 0L
        for (night in distinct(nights)) {
            if (night.stages.isEmpty()) {
                wholeMillis += night.session.endMillis - night.session.startMillis
            } else {
                night.stages.forEach { stageMillis.merge(it.stage, it.endMillis - it.startMillis, Long::plus) }
            }
        }
        val byStage = stageMillis.mapValues { (_, millis) -> minutes(millis) }
        val asleep = wholeMillis + stageMillis.filterKeys { it in ASLEEP }.values.sum()
        val awake = stageMillis.filterKeys { it in AWAKE }
        return Sleep(
            asleep = minutes(asleep),
            awake = awake.takeIf { it.isNotEmpty() }?.let { minutes(it.values.sum()) },
            byStage = byStage,
        )
    }

    /** Groups nights whose [start, end) spans overlap, and keeps the best of each group. */
    private fun distinct(nights: List<SleepNight>): List<SleepNight> {
        val best = compareByDescending<SleepNight> { it.session.endMillis - it.session.startMillis }
            .thenByDescending { it.stages.size }
            .thenBy { it.session.recordId }
        val groups = mutableListOf<MutableList<SleepNight>>()
        var groupEnd = Long.MIN_VALUE
        for (night in nights.sortedBy { it.session.startMillis }) {
            if (groups.isEmpty() || night.session.startMillis >= groupEnd) {
                groups += mutableListOf(night)
                groupEnd = night.session.endMillis
            } else {
                groups.last() += night
                groupEnd = maxOf(groupEnd, night.session.endMillis)
            }
        }
        return groups.map { it.sortedWith(best).first() }
    }

    /** Whole minutes, half a minute rounding up. */
    private fun minutes(millis: Long): Int = ((millis + 30_000) / 60_000).toInt()
}
