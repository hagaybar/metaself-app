package com.metaself.app.domain.health

import kotlin.math.roundToInt

/**
 * Heart-rate figures for a stretch of time — a workout — against a maximum (D70).
 *
 * The maximum is `220 − age` until the trainer offers an observed one, and is always labelled
 * ESTIMATED where it is stored. Zones are the common five bands at 50–60, 60–70, 70–80, 80–90 and
 * 90–100 % of the maximum; a rate at or above the maximum counts in zone 5, one below half of it in
 * none.
 */
object HeartRateZones {

    /**
     * The longest one sample may stand for. A band that stops reporting for twenty minutes has not
     * measured twenty minutes at its last rate. **A choice**, not a published figure: long enough for
     * a band that samples every few minutes, short enough that a gap is not invented.
     */
    const val LONGEST_SAMPLE_SECONDS = 300

    fun estimatedMax(birthYear: Int, currentYear: Int): Int = 220 - (currentYear - birthYear)

    /**
     * @property average the plain mean of the samples, rounded — not weighted by time.
     * @property zoneSeconds five totals, zone 1 to 5.
     */
    data class Figures(val average: Int, val maximum: Int, val zoneSeconds: List<Int>) {
        fun zonesAsText(): String = zoneSeconds.joinToString(",")
    }

    /**
     * @param samples moment in epoch millis to beats per minute, in time order.
     * @param endMillis when the stretch ends; the last sample lasts until then.
     * @return null when there are no samples: nothing measured is not zero.
     */
    fun of(samples: List<Pair<Long, Double>>, endMillis: Long, maxHeartRate: Int): Figures? {
        if (samples.isEmpty()) return null
        val zones = IntArray(5)
        samples.forEachIndexed { index, (at, bpm) ->
            val until = samples.getOrNull(index + 1)?.first ?: endMillis
            val seconds = ((until - at) / 1000).toInt().coerceIn(0, LONGEST_SAMPLE_SECONDS)
            zoneOf(bpm, maxHeartRate)?.let { zones[it] += seconds }
        }
        return Figures(
            average = samples.map { it.second }.average().roundToInt(),
            maximum = samples.maxOf { it.second }.roundToInt(),
            zoneSeconds = zones.toList(),
        )
    }

    /** 0 to 4 for zones 1 to 5, or null below half the maximum. */
    private fun zoneOf(bpm: Double, max: Int): Int? {
        val fraction = bpm / max
        if (fraction < 0.5) return null
        return ((fraction - 0.5) / 0.1).toInt().coerceAtMost(4)
    }
}
