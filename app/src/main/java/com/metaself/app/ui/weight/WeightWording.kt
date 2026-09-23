package com.metaself.app.ui.weight

import com.metaself.app.domain.weight.TrendPoint
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.domain.weight.WeightTrend
import java.util.Locale
import kotlin.math.abs

/**
 * The sentences the weight screen shows.
 *
 * Pure, and in `ui/`, the division this project has used since the version marker.
 *
 * One decimal place throughout. A bathroom scale's second decimal is noise, and showing it would be
 * claiming a precision the measurement does not have — the same rule as decision D4's, applied to a
 * number the owner measured himself.
 */
object WeightWording {

    /** Below this, a fortnight's movement is not a direction. */
    private const val LEVEL_KG = 0.1

    fun trend(points: List<TrendPoint>): String? =
        points.lastOrNull()?.let { kg(it.trendKg) }

    fun reading(reading: WeightReading): String = kg(reading.kg)

    /**
     * The last thing the scale actually said, and when.
     *
     * Shown beside the trend because the trend is a number the owner has never typed and may never
     * have seen on a scale. Without this, the largest figure on the screen looks like the app
     * getting his weight wrong.
     */
    fun latestReading(points: List<TrendPoint>, todayEpochDay: Long): String? {
        val latest = points.maxByOrNull { it.reading.epochDay } ?: return null
        val whenIt = when (latest.reading.epochDay) {
            todayEpochDay -> "today"
            todayEpochDay - 1 -> "yesterday"
            else -> "${todayEpochDay - latest.reading.epochDay} days ago"
        }
        return "Last weighed ${kg(latest.reading.kg)}, $whenIt"
    }


    fun change(points: List<TrendPoint>): String? {
        if (points.size < 2) return null
        val days = points.last().reading.epochDay - points.first().reading.epochDay
        val change = WeightTrend.changeKg(points)
        val magnitude = kg(abs(change))
        return when {
            abs(change) < LEVEL_KG -> "Level over $days days"
            change < 0 -> "Down $magnitude over $days days"
            else -> "Up $magnitude over $days days"
        }
    }

    private fun kg(value: Double): String = String.format(Locale.US, "%.1f kg", value)
}
