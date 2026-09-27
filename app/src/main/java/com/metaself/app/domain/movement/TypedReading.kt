package com.metaself.app.domain.movement

/**
 * The owner's typed workouts as each day's third reading (D60, D77): what the day's visible typed
 * workouts cost, summed — two typed sessions are two different things done — and laid beside that
 * day's steps and band figure, never added to them. [ActivityEnergy.of] takes the largest.
 */
object TypedReading {

    /** Kcal by day of the visible typed workouts that carry a figure. A day with none has no entry. */
    fun kcalByDay(workouts: List<Workout>): Map<Long, Int> = workouts
        .filter { it.source == WorkoutSource.TYPED && !it.hidden }
        .groupBy { it.epochDay }
        .mapValues { (_, day) -> day.sumOf { it.energyKcal ?: 0 } }
        .filterValues { it > 0 }

    /**
     * [history] with each day's typed kcal laid in.
     *
     * **A past day the phone did not record stays absent**: it is not a day of zero steps, and
     * [NormalDay] must keep ignoring it — so a typed workout on such a day is not a reading at all.
     *
     * **[todayEpochDay] is the exception.** Before the first step of the morning Health Connect has
     * no record for today, and a workout typed then must still count. [NormalDay] never reads today,
     * so an entry made for it cannot move the usual day; it carries no steps and no band figure, only
     * the typed kcal. The caller knows it was not recorded, because it is not in [history].
     */
    fun merge(history: List<DayMovement>, kcalByDay: Map<Long, Int>, todayEpochDay: Long): List<DayMovement> {
        val merged = history.map { day -> day.copy(typedWorkoutsKcal = kcalByDay[day.epochDay] ?: 0) }
        val typedToday = kcalByDay[todayEpochDay] ?: return merged
        if (history.any { it.epochDay == todayEpochDay }) return merged
        return merged + DayMovement(epochDay = todayEpochDay, steps = 0, typedWorkoutsKcal = typedToday)
    }
}
