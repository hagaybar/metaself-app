package com.metaself.app.domain.weight

import kotlin.math.pow

/** One reading, and where the smoothed line sat after it. */
data class TrendPoint(
    val reading: WeightReading,
    val trendKg: Double,
)

/**
 * The smoothed weight trend.
 *
 * Exponential smoothing: each reading pulls the line a fraction [PULL] of the way towards itself.
 * This is what The Hacker's Diet established in 1991 and what the weight-trend apps built since use
 * as their default. A 2 kg water swing moves the line about 200 g and it catches up within a week or
 * two if the change was real.
 *
 * A plain seven-day average was considered and rejected: it lurches when an old reading falls out of
 * the window, and it has nothing to say about the days nobody weighed themselves.
 *
 * **Gaps are compounded.** The pull is applied once per DAY elapsed, not once per reading, so
 * weighing yourself twice a week does not give you a trend that reacts half as fast as somebody
 * weighing daily. After a fortnight away the old trend is stale and the new reading rightly
 * dominates it.
 */
object WeightTrend {

    /** How far each day pulls the line towards the reading. A tenth is the established default. */
    const val PULL = 0.1

    fun of(readings: List<WeightReading>): List<TrendPoint> {
        val inOrder = readings.sortedBy { it.epochDay }
        var trend: Double? = null
        var lastDay: Long? = null

        return inOrder.map { reading ->
            val previous = trend
            val newTrend = if (previous == null) {
                // The first reading IS the trend; there is nothing to smooth towards.
                reading.kg
            } else {
                val daysElapsed = (reading.epochDay - (lastDay ?: reading.epochDay))
                    .coerceAtLeast(1L)
                val pull = 1.0 - (1.0 - PULL).pow(daysElapsed.toDouble())
                previous + (reading.kg - previous) * pull
            }
            trend = newTrend
            lastDay = reading.epochDay
            TrendPoint(reading = reading, trendKg = newTrend)
        }
    }

    /**
     * How far the trend moved across [points]: negative for loss.
     *
     * Measured from the smoothed line at both ends, never from the raw readings. A salty final day
     * would otherwise report a gain across a fortnight of loss, which is the exact failure this
     * whole step exists to prevent.
     */
    fun changeKg(points: List<TrendPoint>): Double =
        if (points.size < 2) 0.0 else points.last().trendKg - points.first().trendKg
}
