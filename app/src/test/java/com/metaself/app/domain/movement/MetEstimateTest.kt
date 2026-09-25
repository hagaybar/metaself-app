package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * What a typed workout cost, from the Compendium's MET table, net of resting.
 *
 * Every expected figure is worked in the plan
 * (`docs/superpowers/plans/2026-09-25-a-typed-workout-is-a-third-reading.md`, Task 2) on the
 * standard 80 kg body.
 */
class MetEstimateTest {

    private val weightKg = 80.0

    private fun kcal(
        kind: WorkoutKind,
        effort: Effort = Effort.MODERATE,
        minutes: Int,
        distanceM: Int? = null,
    ) = MetEstimate.netKcal(kind, effort, minutes, distanceM, weightKg)

    @Test
    fun `a moderate strength session is the spec's worked example`() {
        assertThat(kcal(WorkoutKind.STRENGTH, Effort.MODERATE, minutes = 45)).isEqualTo(150)
    }

    @Test
    fun `strength follows the felt effort`() {
        assertThat(kcal(WorkoutKind.STRENGTH, Effort.EASY, minutes = 30)).isEqualTo(72)
        assertThat(kcal(WorkoutKind.STRENGTH, Effort.HARD, minutes = 60)).isEqualTo(400)
    }

    /** With a distance, a run's MET follows its pace and the effort is ignored. */
    @Test
    fun `a run with a distance is priced by its pace`() {
        assertThat(kcal(WorkoutKind.RUN, Effort.EASY, minutes = 60, distanceM = 10_000)).isEqualTo(664)
        assertThat(kcal(WorkoutKind.RUN, Effort.HARD, minutes = 30, distanceM = 5_000)).isEqualTo(332)
        assertThat(kcal(WorkoutKind.RUN, minutes = 50, distanceM = 10_000)).isEqualTo(667)
    }

    @Test
    fun `a run without a distance follows the felt effort`() {
        assertThat(kcal(WorkoutKind.RUN, Effort.EASY, minutes = 30)).isEqualTo(260)
        assertThat(kcal(WorkoutKind.RUN, Effort.MODERATE, minutes = 30)).isEqualTo(332)
        assertThat(kcal(WorkoutKind.RUN, Effort.HARD, minutes = 30)).isEqualTo(432)
    }

    @Test
    fun `a walk is priced by pace when it has one, else by effort`() {
        assertThat(kcal(WorkoutKind.WALK, minutes = 60, distanceM = 5_000)).isEqualTo(224)
        assertThat(kcal(WorkoutKind.WALK, Effort.EASY, minutes = 60)).isEqualTo(160)
        assertThat(kcal(WorkoutKind.WALK, Effort.MODERATE, minutes = 60)).isEqualTo(224)
        assertThat(kcal(WorkoutKind.WALK, Effort.HARD, minutes = 60)).isEqualTo(304)
    }

    @Test
    fun `cycling is priced by speed when it has one, else by effort`() {
        assertThat(kcal(WorkoutKind.CYCLE, minutes = 60, distanceM = 20_000)).isEqualTo(560)
        assertThat(kcal(WorkoutKind.CYCLE, Effort.EASY, minutes = 60)).isEqualTo(200)
        assertThat(kcal(WorkoutKind.CYCLE, Effort.MODERATE, minutes = 60)).isEqualTo(480)
        assertThat(kcal(WorkoutKind.CYCLE, Effort.HARD, minutes = 60)).isEqualTo(720)
    }

    /** A swim's distance is not priced: the table's rows are by effort, and a band reports it anyway. */
    @Test
    fun `swimming and other follow the felt effort only`() {
        assertThat(kcal(WorkoutKind.SWIM, Effort.EASY, minutes = 30)).isEqualTo(192)
        assertThat(kcal(WorkoutKind.SWIM, Effort.MODERATE, minutes = 30, distanceM = 1_000)).isEqualTo(200)
        assertThat(kcal(WorkoutKind.SWIM, Effort.HARD, minutes = 30)).isEqualTo(352)
        assertThat(kcal(WorkoutKind.OTHER, Effort.EASY, minutes = 30)).isEqualTo(72)
        assertThat(kcal(WorkoutKind.OTHER, Effort.MODERATE, minutes = 30)).isEqualTo(180)
        assertThat(kcal(WorkoutKind.OTHER, Effort.HARD, minutes = 30)).isEqualTo(260)
    }

    /**
     * The rows are chosen by "at least this fast". Six miles an hour is 9,656.064 m in the hour:
     * one metre short takes the 5.5 mph row (9.0 MET, 8 × 80 = 640), one metre over takes the
     * 6 mph row (9.3 MET, 8.3 × 80 = 664).
     */
    @Test
    fun `a speed row applies from its threshold up`() {
        assertThat(kcal(WorkoutKind.RUN, minutes = 60, distanceM = 9_656)).isEqualTo(640)
        assertThat(kcal(WorkoutKind.RUN, minutes = 60, distanceM = 9_657)).isEqualTo(664)
    }

    /**
     * 16,764 m in 125 min is exactly 5 mph, which floating point renders as 4.999…9. It takes the
     * 5 mph row (8.5 MET): 7.5 × 80 × 125 / 60 = 1,250.
     */
    @Test
    fun `a speed exactly on a threshold takes that row`() {
        assertThat(kcal(WorkoutKind.RUN, minutes = 125, distanceM = 16_764)).isEqualTo(1_250)
    }

    @Test
    fun `nothing costs nothing`() {
        assertThat(kcal(WorkoutKind.RUN, minutes = 0)).isEqualTo(0)
        assertThat(kcal(WorkoutKind.RUN, minutes = 0, distanceM = 5_000)).isEqualTo(0)
        assertThat(kcal(WorkoutKind.UNRECOGNISED, minutes = 30)).isEqualTo(
            kcal(WorkoutKind.OTHER, minutes = 30),
        )
    }

    /** Every kind and effort has a row; none falls through to an exception or a zero. */
    @Test
    fun `every kind and effort has a value`() {
        WorkoutKind.entries.forEach { kind ->
            Effort.entries.forEach { effort ->
                assertThat(kcal(kind, effort, minutes = 30)).isGreaterThan(0)
            }
        }
    }
}
