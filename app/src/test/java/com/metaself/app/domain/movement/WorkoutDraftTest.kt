package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

/**
 * A workout being typed in (D76). The weight is `aProfile()`'s 80 kg; every figure is invented, and
 * each expected kcal is (MET − 1) × 80 × hours with the row [MetEstimate] holds, worked beside it.
 */
class WorkoutDraftTest {

    private val weight = 80.0

    @Test
    fun `a new draft starts on Moderate with nothing else chosen, and cannot be saved`() {
        val draft = WorkoutDraft()

        assertThat(draft.effort).isEqualTo(Effort.MODERATE)
        assertThat(draft.kind).isNull()
        assertThat(draft.canSave).isFalse()
        assertThat(draft.toWorkout(0, TEST_EPOCH_DAY, 0, weight)).isNull()
    }

    @Test
    fun `kind and minutes are all that is required`() {
        assertThat(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45").canSave).isTrue()
    }

    @Test
    fun `minutes are a whole number from one to a day's worth`() {
        listOf("0", "-5", "1441", "4.5", "abc").forEach { typed ->
            val draft = WorkoutDraft(kind = WorkoutKind.RUN, minutes = typed)
            assertThat(draft.minutesValue).isNull()
            assertThat(draft.minutesProblem).isTrue()
            assertThat(draft.canSave).isFalse()
        }
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, minutes = " 1440 ").minutesValue).isEqualTo(1_440)
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, minutes = "").minutesProblem).isFalse()
    }

    @Test
    fun `a distance is offered for runs, walks, rides and swims only`() {
        assertThat(WorkoutDraft.KINDS.filter { WorkoutDraft(kind = it).takesDistance })
            .containsExactly(WorkoutKind.RUN, WorkoutKind.WALK, WorkoutKind.CYCLE, WorkoutKind.SWIM)
            .inOrder()
        assertThat(WorkoutDraft().takesDistance).isFalse()
    }

    @Test
    fun `a distance is read in kilometres, a comma as a decimal point`() {
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, distanceKm = "6.2").distanceM).isEqualTo(6_200)
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, distanceKm = "6,2").distanceM).isEqualTo(6_200)
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, distanceKm = "").distanceM).isNull()
    }

    @Test
    fun `a distance that is not one blocks saving, and a blank one does not`() {
        val bad = WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "five")

        assertThat(bad.distanceProblem).isTrue()
        assertThat(bad.canSave).isFalse()
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "0").canSave).isFalse()
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "").canSave).isTrue()
    }

    /** The ceiling is a choice (see [WorkoutDraft.MAX_DISTANCE_KM]), not a measurement. */
    @Test
    fun `a distance up to a thousand kilometres is taken, and one past it blocks saving`() {
        val ride = WorkoutDraft(kind = WorkoutKind.CYCLE, minutes = "600")

        assertThat(ride.copy(distanceKm = "1000").distanceM).isEqualTo(1_000_000)
        assertThat(ride.copy(distanceKm = "1000.001").distanceM).isNull()
        assertThat(ride.copy(distanceKm = "1000.001").distanceProblem).isTrue()
        assertThat(ride.copy(distanceKm = "1000.001").canSave).isFalse()
    }

    @Test
    fun `a distance written with an exponent is not taken`() {
        val run = WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30")

        listOf("1e3", "5E0", "2e-1").forEach { typed ->
            assertThat(run.copy(distanceKm = typed).distanceM).isNull()
            assertThat(run.copy(distanceKm = typed).canSave).isFalse()
        }
    }

    @Test
    fun `a distance typed before switching to strength is kept but not used`() {
        val draft = WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45", distanceKm = "five")

        assertThat(draft.distanceM).isNull()
        assertThat(draft.distanceProblem).isFalse()
        assertThat(draft.canSave).isTrue()
        assertThat(draft.copy(kind = WorkoutKind.RUN).distanceKm).isEqualTo("five")
    }

    /** 30 minutes over 5 km: 1,800 s / 5 = 360 s a kilometre. */
    @Test
    fun `a run with minutes and a distance has a pace`() {
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "5").paceSecondsPerKm)
            .isEqualTo(360)
    }

    /** D78: pace for runs only. */
    @Test
    fun `a walk or a ride has no pace, and nor does a run missing either figure`() {
        assertThat(WorkoutDraft(kind = WorkoutKind.WALK, minutes = "50", distanceKm = "4").paceSecondsPerKm).isNull()
        assertThat(WorkoutDraft(kind = WorkoutKind.CYCLE, minutes = "50", distanceKm = "20").paceSecondsPerKm).isNull()
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30").paceSecondsPerKm).isNull()
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, distanceKm = "5").paceSecondsPerKm).isNull()
    }

    /** Resistance training, MET 3.5: (3.5 − 1) × 80 × 0.75 = 150. */
    @Test
    fun `without a distance the estimate follows the effort`() {
        val draft = WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45")

        assertThat(draft.estimateKcal(weight)).isEqualTo(150)
        assertThat(draft.pricedByPace).isFalse()
    }

    /** 5 km in 30 min is 6.21 mph, the 6–6.3 mph row, MET 9.3: 8.3 × 80 × 0.5 = 332 — not Hard's 432. */
    @Test
    fun `a run with a distance is priced by its pace, whatever the effort`() {
        val draft = WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "5", effort = Effort.HARD)

        assertThat(draft.estimateKcal(weight)).isEqualTo(332)
        assertThat(draft.pricedByPace).isTrue()
    }

    @Test
    fun `with no weight there is no estimate`() {
        assertThat(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45").estimateKcal(null)).isNull()
    }

    @Test
    fun `it saves as a typed workout carrying its estimate, and says so`() {
        val workout = WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45", note = "  a note  ")
            .toWorkout(id = 0, epochDay = TEST_EPOCH_DAY, startedAtMillis = 1_000, weightKg = weight)

        assertThat(workout).isEqualTo(
            Workout(
                id = 0, epochDay = TEST_EPOCH_DAY, startedAtMillis = 1_000, durationMinutes = 45,
                kind = WorkoutKind.STRENGTH, title = null, distanceM = null, energyKcal = 150,
                energySource = EnergySource.MET_ESTIMATE, effort = Effort.MODERATE,
                source = WorkoutSource.TYPED, hidden = false, note = "a note",
            ),
        )
    }

    @Test
    fun `the owner's own figure is saved as his, and no estimate is made`() {
        val workout = WorkoutDraft(kind = WorkoutKind.SWIM, minutes = "40", ownEnergy = true, energyKcal = "300")
            .toWorkout(0, TEST_EPOCH_DAY, 0, weight)!!

        assertThat(workout.energyKcal).isEqualTo(300)
        assertThat(workout.energySource).isEqualTo(EnergySource.TYPED)
    }

    @Test
    fun `setting it yourself needs a whole number of zero or more`() {
        val own = WorkoutDraft(kind = WorkoutKind.SWIM, minutes = "40", ownEnergy = true)

        assertThat(own.canSave).isFalse()
        assertThat(own.copy(energyKcal = "lots").energyProblem).isTrue()
        assertThat(own.copy(energyKcal = "-1").canSave).isFalse()
        assertThat(own.copy(energyKcal = "0").canSave).isTrue()
    }

    /** The ceiling is a choice (see [WorkoutDraft.MAX_OWN_KCAL]), not a measurement. */
    @Test
    fun `a figure of his own up to twenty thousand kcal is taken, and one past it blocks saving`() {
        val own = WorkoutDraft(kind = WorkoutKind.OTHER, minutes = "600", ownEnergy = true)

        assertThat(own.copy(energyKcal = "20000").ownKcal).isEqualTo(20_000)
        assertThat(own.copy(energyKcal = "20001").ownKcal).isNull()
        assertThat(own.copy(energyKcal = "20001").energyProblem).isTrue()
        assertThat(own.copy(energyKcal = "20001").canSave).isFalse()
        assertThat(own.copy(energyKcal = "1e3").canSave).isFalse()
    }

    /** D4: no weight, no estimate — and nothing guessed in its place. */
    @Test
    fun `with no weight and no figure of his own it saves with no energy, saying so`() {
        val workout = WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45").toWorkout(0, TEST_EPOCH_DAY, 0, null)!!

        assertThat(workout.energyKcal).isNull()
        assertThat(workout.energySource).isEqualTo(EnergySource.NONE)
    }

    @Test
    fun `a strength session saves no distance, whatever was typed`() {
        val workout = WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45", distanceKm = "5")
            .toWorkout(0, TEST_EPOCH_DAY, 0, weight)!!

        assertThat(workout.distanceM).isNull()
    }

    @Test
    fun `a typed workout opens filled, as it was saved`() {
        // 6 km in 30 min = 5:00 /km.
        val run = aTypedWorkout(kind = WorkoutKind.RUN, minutes = 30, distanceM = 6_000, effort = Effort.HARD, note = "a note")

        assertThat(WorkoutDraft.from(run)).isEqualTo(
            WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "6", effort = Effort.HARD, note = "a note"),
        )
        assertThat(WorkoutDraft.from(aTypedWorkout(kind = WorkoutKind.WALK, distanceM = 10_000)).distanceKm)
            .isEqualTo("10")
    }

    @Test
    fun `a figure he set opens as his, and an estimate opens as an estimate`() {
        val own = aTypedWorkout(energyKcal = 300, energySource = EnergySource.TYPED)

        assertThat(WorkoutDraft.from(own).ownEnergy).isTrue()
        assertThat(WorkoutDraft.from(own).energyKcal).isEqualTo("300")
        assertThat(WorkoutDraft.from(aTypedWorkout()).ownEnergy).isFalse()
        assertThat(WorkoutDraft.from(aTypedWorkout()).energyKcal).isEmpty()
    }

    /** D76: onto the open day, at the clock time now. Now is 15:00 UTC on TEST_EPOCH_DAY. */
    @Test
    fun `a workout starts on the day it is logged to, at the time it is now`() {
        val now = TEST_EPOCH_DAY * DAY_MS + 15 * HOUR_MS

        assertThat(WorkoutDraft.startOn(TEST_EPOCH_DAY - 1, now, ZoneOffset.UTC))
            .isEqualTo((TEST_EPOCH_DAY - 1) * DAY_MS + 15 * HOUR_MS)
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val HOUR_MS = 3_600_000L
    }

    /** D82: a file's calories open as the workout's own figure, so saving the sheet keeps them. */
    @Test
    fun `a workout's calories from a file open as its own figure`() {
        val draft = WorkoutDraft.from(aTypedWorkout(energyKcal = 150, energySource = EnergySource.FILE))

        assertThat(draft.ownEnergy).isTrue()
        assertThat(draft.energyKcal).isEqualTo("150")
    }
}
