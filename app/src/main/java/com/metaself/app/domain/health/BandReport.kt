package com.metaself.app.domain.health

import com.metaself.app.domain.movement.WorkoutKind

/** How many of [kind]'s rows [origin] wrote on [epochDay]. A night is one sleep row. */
data class Arrival(val kind: HealthKind, val origin: String, val epochDay: Long, val count: Int)

/** One stored workout, as the report needs it: what it is, where it came from, which details it has. */
data class ArrivedWorkout(
    val epochDay: Long,
    val kind: WorkoutKind,
    val typed: Boolean,
    /** The writing app; null for a typed workout. */
    val origin: String?,
    val hasDistance: Boolean,
    /** A figure the band sent (energy source BAND), not this app's estimate (D4). */
    val hasCalories: Boolean,
    /** Worked out here from the readings inside the session (D70). */
    val hasHeartRate: Boolean,
    val hasTitle: Boolean,
)

/** The figures of the daily summary (D69) the report counts days for, in the spec's order. */
enum class DayFigure {
    STEPS, DISTANCE, ACTIVE_KCAL, TOTAL_KCAL, RESTING_HEART_RATE, HRV, OXYGEN, BREATHING, SLEEP, WORKOUTS,
}

/** Which figures one stored day has a value for. */
data class DayCoverage(val epochDay: Long, val figures: Set<DayFigure>)

/** One kind over the window. [origins] are the writing apps, most rows first, a tie by name. */
data class KindArrivals(
    val kind: HealthKind,
    val count: Int,
    val days: Int,
    val origins: List<String>,
    val firstDay: Long?,
    val lastDay: Long?,
) {
    companion object {
        fun of(kind: HealthKind, rows: List<Arrival>): KindArrivals = KindArrivals(
            kind = kind,
            count = rows.sumOf { it.count },
            days = rows.map { it.epochDay }.distinct().size,
            origins = rows.groupBy { it.origin }
                .mapValues { (_, byApp) -> byApp.sumOf { it.count } }
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { it.key },
            firstDay = rows.minOfOrNull { it.epochDay },
            lastDay = rows.maxOfOrNull { it.epochDay },
        )
    }
}

/**
 * The window's workouts, hidden ones included — a hidden session still arrived. The `copiedWith…`
 * counts are over the [copied] ones only.
 */
data class WorkoutArrivals(
    val total: Int = 0,
    /** Most first; a tie in [WorkoutKind]'s order. */
    val byKind: List<Pair<WorkoutKind, Int>> = emptyList(),
    val copied: Int = 0,
    val typed: Int = 0,
    val copiedWithDistance: Int = 0,
    val copiedWithCalories: Int = 0,
    val copiedWithHeartRate: Int = 0,
    val copiedWithTitle: Int = 0,
)

/**
 * What arrived in the stored health record over [fromDay]..[toDay], kind by kind (D80): counts, days,
 * dates and the apps that wrote them — never a reading's value. Pure, so it is tested without Room.
 */
data class BandReport(
    val fromDay: Long,
    val toDay: Long,
    /** Every one of the thirteen kinds, in [HealthKind]'s order, those with nothing included. */
    val kinds: List<KindArrivals>,
    val workouts: WorkoutArrivals,
    /** On how many days of the window each figure has a value; every figure is a key. */
    val daysWith: Map<DayFigure, Int>,
) {
    val windowDays: Int get() = (toDay - fromDay + 1).toInt()

    /** Every app that wrote anything, so each name is looked up once. */
    val origins: Set<String> get() = kinds.flatMapTo(mutableSetOf()) { it.origins }

    companion object {
        /** The last 30 days, today included: the length of the copying window (D80). */
        const val WINDOW_DAYS = 30

        fun fromDayFor(today: Long): Long = today - (WINDOW_DAYS - 1)

        /**
         * Rows outside [fromDay]..[toDay] are ignored. Workouts' arrivals are worked out from
         * [workouts] — the copied ones with an origin — so any [Arrival] of [HealthKind.EXERCISE]
         * handed in is ignored rather than counted twice.
         */
        fun of(
            fromDay: Long,
            toDay: Long,
            arrivals: List<Arrival>,
            workouts: List<ArrivedWorkout>,
            days: List<DayCoverage>,
        ): BandReport {
            val window = fromDay..toDay
            val theWorkouts = workouts.filter { it.epochDay in window }
            val copied = theWorkouts.filter { !it.typed }
            val sessions = copied.mapNotNull { w -> w.origin?.let { Arrival(HealthKind.EXERCISE, it, w.epochDay, 1) } }
            val rows = (arrivals.filter { it.kind != HealthKind.EXERCISE } + sessions)
                .filter { it.epochDay in window && it.count > 0 }
                .groupBy { it.kind }
            val theDays = days.filter { it.epochDay in window }.distinctBy { it.epochDay }

            return BandReport(
                fromDay = fromDay,
                toDay = toDay,
                kinds = HealthKind.entries.map { kind -> KindArrivals.of(kind, rows[kind].orEmpty()) },
                workouts = WorkoutArrivals(
                    total = theWorkouts.size,
                    byKind = theWorkouts.groupingBy { it.kind }.eachCount().entries
                        .sortedWith(compareByDescending<Map.Entry<WorkoutKind, Int>> { it.value }.thenBy { it.key.ordinal })
                        .map { it.key to it.value },
                    copied = copied.size,
                    typed = theWorkouts.size - copied.size,
                    copiedWithDistance = copied.count { it.hasDistance },
                    copiedWithCalories = copied.count { it.hasCalories },
                    copiedWithHeartRate = copied.count { it.hasHeartRate },
                    copiedWithTitle = copied.count { it.hasTitle },
                ),
                daysWith = DayFigure.entries.associateWith { figure -> theDays.count { figure in it.figures } },
            )
        }
    }
}
