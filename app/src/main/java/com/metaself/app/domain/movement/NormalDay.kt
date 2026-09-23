package com.metaself.app.domain.movement

/**
 * What a usual day looks like for this owner, in steps.
 *
 * The **median** of the last thirty days, not the mean. A median ignores the occasional enormous day
 * instead of letting it drag the normal upwards, which is the whole point: a baseline poisoned by
 * a fortnight of hard training is not what an ordinary day looks like.
 *
 * It rolls, so as habits change the normal follows and stops paying for what has become
 * ordinary. That is not the system failing — it is the handoff. Habitual movement belongs in the
 * daily target, where the measured burn (D25) will find it; only the exceptional belongs here.
 */
object NormalDay {

    /**
     * Thirty days, which is also all Health Connect will give without a further permission. The
     * right span is not a long one anyway: a rolling month is a truer picture of now than a
     * quarter containing a fitter or a lazier season.
     */
    const val WINDOW_DAYS = 30

    /**
     * Below this there is no normal worth having, so nothing is credited at all.
     *
     * Ten days of a month is enough to say what a usual day looks like. Four is enough to be
     * badly wrong about it in either direction.
     */
    const val MIN_DAYS = 10

    /**
     * Null when there is not enough to go on.
     *
     * Days with NO RECORD are absent from [history] and are therefore ignored rather than counted as
     * zero. A week with a flat phone is not a week of sitting still, and treating it as one would
     * drag the normal down and inflate every credit afterwards. A day that recorded few steps is
     * kept, because that is real information about a real day.
     *
     * Today is excluded by the caller: a day still in progress is not a day to measure a normal by.
     */
    fun steps(history: List<DayMovement>, todayEpochDay: Long): Int? {
        val window = history
            .filter { it.epochDay in (todayEpochDay - WINDOW_DAYS)..<todayEpochDay }
            .map { it.steps }

        if (window.size < MIN_DAYS) return null
        return medianOf(window)
    }

    /**
     * The usual day's ENERGY, which is what the credit is measured against (D12b).
     *
     * Separate from the step median because the two are used for different things: the step median
     * is what the bar on screen is drawn against, and this is what decides whether a day earned
     * anything. For somebody with no band the two say the same thing, since one is a fixed multiple
     * of the other.
     */
    fun energyKcal(history: List<DayMovement>, todayEpochDay: Long, weightKg: Double): Int? {
        val window = history
            .filter { it.epochDay in (todayEpochDay - WINDOW_DAYS)..<todayEpochDay }
            .map { ActivityEnergy.of(it, weightKg).kcal }

        if (window.size < MIN_DAYS) return null
        return medianOf(window)
    }

    private fun medianOf(values: List<Int>): Int {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2
        }
    }
}
