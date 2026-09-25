package com.metaself.app.domain.movement

import kotlin.math.roundToInt

/**
 * What a typed workout cost, when no band said.
 *
 * MET values from the 2024 Adult Compendium of Physical Activities, each with its activity code so
 * that a reader can check it against the published table. **Net of resting** — one MET is
 * subtracted — because the daily target already pays for existing, exactly as
 * [MovementCredit.KCAL_PER_STEP_PER_KG] is net. The three-quarters discount of D12 is applied by
 * [MovementCredit] afterwards, not here; a value discounted twice would be wrong in the stingy
 * direction, which is the safe one but still wrong.
 *
 * A run, walk or ride with a distance is priced by its speed, using the table's speed rows; the felt
 * effort is then ignored, because the pace is the better witness. Without a distance the felt effort
 * chooses among three rows. Swimming and everything else are by effort only.
 */
object MetEstimate {

    private data class Row(val code: Int, val met: Double)

    /** A speed row: applies from [atLeastMph] up to the next row's threshold. */
    private data class SpeedRow(val atLeastMph: Double, val code: Int, val met: Double)

    private const val METRES_PER_MILE = 1_609.344

    fun netKcal(
        kind: WorkoutKind,
        effort: Effort,
        durationMinutes: Int,
        distanceM: Int?,
        weightKg: Double,
    ): Int {
        if (durationMinutes <= 0) return 0
        val row = bySpeed(kind, durationMinutes, distanceM) ?: byEffort(kind, effort)
        val hours = durationMinutes / 60.0
        return ((row.met - 1.0) * weightKg * hours).roundToInt()
    }

    private fun bySpeed(kind: WorkoutKind, minutes: Int, distanceM: Int?): Row? {
        val metres = distanceM?.takeIf { it > 0 } ?: return null
        val table = when (kind) {
            WorkoutKind.RUN -> RUNNING_BY_SPEED
            WorkoutKind.WALK -> WALKING_BY_SPEED
            WorkoutKind.CYCLE -> CYCLING_BY_SPEED
            else -> return null
        }
        val mph = (metres / METRES_PER_MILE) / (minutes / 60.0)
        val chosen = table.last { mph >= it.atLeastMph }
        return Row(chosen.code, chosen.met)
    }

    private fun byEffort(kind: WorkoutKind, effort: Effort): Row = when (kind) {
        WorkoutKind.RUN -> when (effort) {
            Effort.EASY -> Row(12020, 7.5)      // Jogging, general, self-selected pace
            Effort.MODERATE -> Row(12050, 9.3)  // Running, 6-6.3 mph (10 min/mile)
            Effort.HARD -> Row(12080, 11.8)     // Running, 7.5 mph (8 min/mile)
        }
        WorkoutKind.WALK -> when (effort) {
            Effort.EASY -> Row(17170, 3.0)      // Walking, 2.5 mph, firm, level surface
            Effort.MODERATE -> Row(17190, 3.8)  // Walking, 2.8 to 3.4 mph, level, moderate pace
            Effort.HARD -> Row(17200, 4.8)      // Walking, 3.5 to 3.9 mph, level, brisk
        }
        WorkoutKind.CYCLE -> when (effort) {
            Effort.EASY -> Row(1018, 3.5)       // Bicycling, leisure 5.5 mph
            Effort.MODERATE -> Row(1014, 7.0)   // Bicycling, general
            Effort.HARD -> Row(1040, 10.0)      // Bicycling, 14-15.9 mph, fast, vigorous effort
        }
        WorkoutKind.SWIM -> when (effort) {
            Effort.EASY -> Row(18240, 5.8)      // Swimming laps, freestyle, slow, recreational
            Effort.MODERATE -> Row(18310, 6.0)  // Swimming, leisurely, not lap swimming, general
            Effort.HARD -> Row(18230, 9.8)      // Swimming laps, freestyle, fast, vigorous effort
        }
        WorkoutKind.STRENGTH -> when (effort) {
            Effort.EASY -> Row(2024, 2.8)       // Calisthenics (curl ups, crunches, plank), light
            Effort.MODERATE -> Row(2054, 3.5)   // Resistance training, multiple exercises, 8-15 reps
            Effort.HARD -> Row(2050, 6.0)       // Resistance, power lifting or body building, vigorous
        }
        WorkoutKind.OTHER, WorkoutKind.UNRECOGNISED -> when (effort) {
            Effort.EASY -> Row(2024, 2.8)       // Calisthenics, light effort
            Effort.MODERATE -> Row(2060, 5.5)   // Health club exercise, general
            Effort.HARD -> Row(2020, 7.5)       // Calisthenics, vigorous effort
        }
    }

    /** Compendium running rows by speed, ascending. Below 4 mph a "run" is priced as a jog. */
    private val RUNNING_BY_SPEED = listOf(
        SpeedRow(0.0, 12026, 3.3),   // Jogging 2.6 to 3.7 mph
        SpeedRow(4.0, 12028, 6.5),   // Running, 4 to 4.2 mph (13 min/mile)
        SpeedRow(4.3, 12029, 7.8),   // Running 4.3 to 4.8 mph
        SpeedRow(5.0, 12030, 8.5),   // Running, 5.0 to 5.2 mph (12 min/mile)
        SpeedRow(5.5, 12045, 9.0),   // Running, 5.5-5.8 mph
        SpeedRow(6.0, 12050, 9.3),   // Running, 6-6.3 mph (10 min/mile)
        SpeedRow(6.7, 12060, 10.5),  // Running, 6.7 mph (9 min/mile)
        SpeedRow(7.0, 12070, 11.0),  // Running, 7 mph (8.5 min/mile)
        SpeedRow(7.5, 12080, 11.8),  // Running, 7.5 mph (8 min/mile)
        SpeedRow(8.0, 12090, 12.0),  // Running, 8 mph (7.5 min/mile)
        SpeedRow(8.6, 12100, 12.5),  // Running, 8.6 mph (7 min/mile)
        SpeedRow(9.0, 12110, 13.0),  // Running, 9 mph (6.5 min/mile)
        SpeedRow(9.3, 12115, 14.8),  // Running, 9.3 to 9.6 mph
        SpeedRow(11.0, 12130, 16.8), // Running, 11 mph (5.5 min/mile)
        SpeedRow(12.0, 12132, 18.5), // Running, 12 mph (5.0 min/mile)
    )

    private val WALKING_BY_SPEED = listOf(
        SpeedRow(0.0, 17170, 3.0),   // Walking, 2.5 mph, firm, level surface
        SpeedRow(2.8, 17190, 3.8),   // Walking, 2.8 to 3.4 mph, level, moderate pace
        SpeedRow(3.5, 17200, 4.8),   // Walking, 3.5 to 3.9 mph, level, brisk
        SpeedRow(4.0, 17220, 5.5),   // Walking, 4.0 to 4.4 mph, level, very brisk pace
        SpeedRow(4.5, 17230, 7.0),   // Walking, 4.5 to 4.9 mph, level, very, very brisk
    )

    private val CYCLING_BY_SPEED = listOf(
        SpeedRow(0.0, 1018, 3.5),    // Bicycling, leisure 5.5 mph
        SpeedRow(10.0, 1020, 6.8),   // Bicycling, 10-11.9 mph, leisure, slow, light effort
        SpeedRow(12.0, 1030, 8.0),   // Bicycling, 12-13.9 mph, leisure, moderate effort
        SpeedRow(14.0, 1040, 10.0),  // Bicycling, 14-15.9 mph, racing or leisure, fast, vigorous
        SpeedRow(16.0, 1050, 12.0),  // Bicycling, 16-19 mph, racing/not drafting, very fast
    )
}
