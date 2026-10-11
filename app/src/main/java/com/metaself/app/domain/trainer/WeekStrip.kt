package com.metaself.app.domain.trainer

import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.WeekSessions
import com.metaself.app.domain.movement.Workout

/** D114: one counted session on the week strip; [ticked] when it ticked a planned session (the ledger's practice). */
data class StripBlock(val workoutId: Long, val minutes: Int, val ticked: Boolean)

/** D114: one day's column, its sessions oldest first — drawn from the bottom up. */
data class StripDay(val epochDay: Long, val blocks: List<StripBlock>) {
    val minutes: Int get() = blocks.sumOf { it.minutes }

    /** A tick above the column: some session of the day ticked a planned session. */
    val ticked: Boolean get() = blocks.any { it.ticked }
}

/**
 * D114: the week strip — seven columns, Monday to Sunday — and the open planned sessions' minutes for the
 * "To do" column, which planned sessions are drawn in because they have no day (D94). [withPlan] is whether a
 * kept plan covers the week. Counted from the same partition as the week's ledger (D110). Pure.
 */
data class WeekStrip(val monday: Long, val today: Long, val days: List<StripDay>, val toDo: List<Int>, val withPlan: Boolean) {

    /** The tallest column's minutes, "To do" included: the strip's full height. 0 for an empty week. */
    val scaleMinutes: Int get() = maxOf(days.maxOf { it.minutes }, toDo.sum())

    fun isAfterToday(day: StripDay): Boolean = day.epochDay > today

    companion object {
        /** [practice] is [WeekLedgers.practiceOf]'s: a block is ticked exactly when its session is some tick's `by`. */
        fun of(monday: Long, today: Long, workouts: List<Workout>, practice: WeekProgress?): WeekStrip {
            require(MovementWeek.mondayOf(monday) == monday) { "not a Monday: $monday" }
            val ticked = practice?.ticks.orEmpty().mapNotNull { it.by?.id }.toSet()
            val sessions = WeekSessions.of(workouts, monday, today)
            val days = (0..6).map { offset ->
                val day = monday + offset
                StripDay(day, sessions.filter { it.epochDay == day }.map { StripBlock(it.id, it.durationMinutes, it.id in ticked) })
            }
            return WeekStrip(monday, today, days, practice?.ticks.orEmpty().filter { it.by == null }.map { it.planned.minutes }, practice != null)
        }

        /** A block's height: its share of [full] as its minutes are of [scaleMinutes], never under [least]. */
        fun height(minutes: Int, scaleMinutes: Int, full: Float, least: Float): Float =
            if (scaleMinutes <= 0) least else maxOf(least, full * minutes / scaleMinutes)
    }
}

/**
 * D132: the strip card's movement line — the ledger's counted [sessions] (D115), this week's km to today
 * ([distanceM]), and the four weeks before this one ([fourWeeksM]) as Movement's "Last four weeks" counts them.
 * Each distance is null when no day has one; a null is never zero.
 */
data class MovementSummary(val sessions: Int, val distanceM: Int?, val fourWeeksM: Int?)

/**
 * D114: this week as the Trainer page shows it — its ledger (D110) and its strip, counted from one read — and
 * its movement (D132), counted by Movement's own functions.
 */
data class TrainerWeek(val ledger: WeekLedger, val strip: WeekStrip, val movement: MovementSummary) {
    companion object {
        /**
         * [WeekLedgers.of]'s arguments; the strip is counted from the same practice. [days] must reach back
         * [MovementWeek.PREVIOUS_WEEKS] weeks before [monday] for the four weeks' km (the ledger's usuals read
         * as far).
         */
        fun of(
            monday: Long,
            today: Long,
            workouts: List<Workout>,
            days: List<HealthDay>,
            food: Map<Long, DayTotals>,
            targetKcal: Int?,
            plans: List<CountedPlan>,
        ): TrainerWeek {
            val ledger = WeekLedgers.of(monday, today, workouts, days, food, targetKcal, plans)
            return TrainerWeek(
                ledger,
                WeekStrip.of(monday, today, workouts, WeekLedgers.practiceOf(monday, today, workouts, plans)),
                MovementSummary(
                    sessions = ledger.sessions,
                    distanceM = MovementWeek.distanceOf(days, monday, minOf(today, monday + 6)),
                    fourWeeksM = MovementWeek.fourWeeks(MovementWeek.previousWeeksOf(days, monday)),
                ),
            )
        }
    }
}
