package com.metaself.app.domain.target

import com.metaself.app.domain.weight.TrendPoint
import kotlin.math.roundToInt

/**
 * What the owner's days have actually cost him, measured rather than predicted.
 *
 * **This is not a claim about his metabolism, and nothing may word it as one.** He records some
 * fraction of what he eats — everybody does — so the figure comes out in his own units. That is the
 * point rather than a flaw: a target expressed in the same units produces the right result in the
 * world, whatever the fraction is, as long as it stays roughly the same from week to week (D25a).
 *
 * @property averageLoggedKcal the mean of the days that HOLD food, not of every day in the window.
 *   An unlogged day was not a day of fasting, and the least-bad assumption available is that it
 *   resembled the days either side of it. Stated on screen, never buried.
 * @property fromStoresKcalPerDay energy that did not come from food, positive when losing weight.
 * @property measuredKcal the two added together: what a day cost, counted his way.
 */
data class MeasuredBurn(
    val days: Int,
    val daysLogged: Int,
    val averageLoggedKcal: Int,
    val trendChangeKg: Double,
    val fromStoresKcalPerDay: Int,
    val measuredKcal: Int,
    val formulaKcal: Int,
) {
    /** How far the formula is out. Positive means the formula was under-stating his burn. */
    val differenceKcal: Int get() = measuredKcal - formulaKcal
}

object MeasuredBurnCalculator {

    /** Long enough that water and glycogen stop dominating; short enough to still be about now. */
    const val WINDOW_DAYS = 28

    /**
     * How many of those days must hold food.
     *
     * Six missing days out of twenty-eight is a normal month. Twelve is a window whose average says
     * more about which days were logged than about what was eaten.
     */
    const val MIN_DAYS_LOGGED = 22

    /**
     * Null whenever there is nothing truthful to say. The screen then shows nothing at all rather
     * than a placeholder, because a figure marked "—" invites the owner to wonder what is broken.
     */
    fun of(
        loggedKcalByDay: Map<Long, Int>,
        trend: List<TrendPoint>,
        formulaKcal: Int,
        todayEpochDay: Long,
    ): MeasuredBurn? {
        val firstDay = todayEpochDay - WINDOW_DAYS + 1
        val window = (firstDay..todayEpochDay)

        val logged = loggedKcalByDay.filterKeys { it in window }.filterValues { it > 0 }
        if (logged.size < MIN_DAYS_LOGGED) return null

        val startKg = trendAtOrBefore(trend, firstDay) ?: return null
        val endKg = trend.lastOrNull()?.takeIf { it.reading.epochDay >= todayEpochDay - GAP_DAYS }
            ?.trendKg
            ?: return null

        val averageLogged = logged.values.sum().toDouble() / logged.size
        val changeKg = endKg - startKg

        // Losing weight means energy came OUT of storage, so a fall adds to what the day cost.
        val fromStores = -changeKg * DailyTargetCalculator.KCAL_PER_KG_BODY_FAT / WINDOW_DAYS

        return MeasuredBurn(
            days = WINDOW_DAYS,
            daysLogged = logged.size,
            averageLoggedKcal = averageLogged.roundToInt(),
            trendChangeKg = changeKg,
            fromStoresKcalPerDay = fromStores.roundToInt(),
            measuredKcal = (averageLogged + fromStores).roundToInt(),
            formulaKcal = formulaKcal,
        )
    }

    /** A weight from before the window began is not a reading of where the window started. */
    private const val GAP_DAYS = 4

    private fun trendAtOrBefore(trend: List<TrendPoint>, epochDay: Long): Double? =
        trend.lastOrNull { it.reading.epochDay <= epochDay }?.trendKg
            // Nothing before the window began, but something inside it: the earliest reading there
            // is, provided it is near the start rather than halfway through.
            ?: trend.firstOrNull { it.reading.epochDay <= epochDay + GAP_DAYS }?.trendKg
}
