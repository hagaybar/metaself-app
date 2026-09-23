package com.metaself.app.domain.weight

/**
 * How fast the smoothed trend has ACTUALLY been moving.
 *
 * This is arithmetic about the past and nothing may word it as a prediction: how far the line moved,
 * divided by how long it took. `WeightTrend.changeKg` is the numerator's idea and already argues the
 * case for measuring the smoothed line rather than the readings — a rate computed from two mornings
 * would report a gain across a fortnight of loss whenever the last morning was salty (D11).
 *
 * Knows nothing about goals, deliberately. Which direction the owner WANTS to go is
 * [com.metaself.app.domain.goal.GoalForecast]'s business, and a measurement that already took a side
 * could not be reused by anything that took the other one.
 *
 * @property spanDays the days actually observed between the two trend points used — NOT [WINDOW_DAYS].
 *   Whatever displays this must state it, because it is the denominator and it varies.
 * @property changeKg signed: negative means the trend went DOWN. The opposite convention to
 *   [com.metaself.app.domain.profile.Goal.kgPerWeek], which is a positive magnitude with the
 *   direction carrying the sign — right for an intention, wrong for an observation.
 * @property asOfEpochDay the latest trend point used, which is at most [GAP_DAYS] before today.
 */
data class MeasuredRate(
    val spanDays: Int,
    val changeKg: Double,
    val kgPerWeek: Double,
    val asOfEpochDay: Long,
) {
    companion object {

        /**
         * Matching `MeasuredBurnCalculator.WINDOW_DAYS` exactly, and for its stated reason: long
         * enough that water and glycogen stop dominating, short enough to still be about now. The
         * two figures on screen agreeing about what "recently" means is worth more than tuning them
         * apart.
         *
         * The window opens [WINDOW_DAYS] days BEFORE today, so a reading every day spans exactly
         * 28 days. The measured burn opens one day later because it counts 28 days of food
         * inclusive; a rate is a distance between two points and counts the gap.
         */
        const val WINDOW_DAYS = 28

        /** A weight from before the window began is not a reading of where the window began. */
        const val GAP_DAYS = 4

        /**
         * Multiplying a four-day change by 1.75 to reach a week reports noise as a rate.
         *
         * At today's constants this cannot fire before the gap rules do: the start is at most
         * [GAP_DAYS] after the window opens and the end at most [GAP_DAYS] before today, so any
         * span that gets this far is at least 28 - 4 - 4 = 20 days. It stays as the guard that
         * holds if either constant moves.
         */
        const val MIN_SPAN_DAYS = 14

        /**
         * Null whenever there is nothing truthful to say. The screen then shows nothing at all
         * rather than a placeholder, because a figure marked "—" invites the owner to wonder what
         * is broken.
         */
        fun of(trend: List<TrendPoint>, todayEpochDay: Long): MeasuredRate? {
            val windowStart = todayEpochDay - WINDOW_DAYS

            val end = trend.lastOrNull()
                ?.takeIf { it.reading.epochDay >= todayEpochDay - GAP_DAYS }
                ?: return null

            // The point at or before the window opened, or failing that the first one shortly
            // after it — the same fallback, and the same constant, as the measured burn uses.
            val start = trend.lastOrNull { it.reading.epochDay <= windowStart }
                ?: trend.firstOrNull { it.reading.epochDay <= windowStart + GAP_DAYS }
                ?: return null

            val spanDays = (end.reading.epochDay - start.reading.epochDay).toInt()
            if (spanDays < MIN_SPAN_DAYS) return null

            val changeKg = end.trendKg - start.trendKg

            return MeasuredRate(
                spanDays = spanDays,
                changeKg = changeKg,
                kgPerWeek = changeKg / spanDays * DAYS_PER_WEEK,
                asOfEpochDay = end.reading.epochDay,
            )
        }

        private const val DAYS_PER_WEEK = 7.0
    }
}
