package com.metaself.app.ui.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.movement.WorkoutDraft
import com.metaself.app.domain.movement.WorkoutKind
import org.junit.jupiter.api.Test

/** What the sheet says about a draft (D76). 80 kg is `aProfile()`'s; every figure is invented. */
class WorkoutSheetWordingTest {

    @Test
    fun `the estimate says it is one, and what it was estimated from`() {
        assertThat(WorkoutSheetWording.estimate(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45"), 80.0))
            .isEqualTo("about 150 kcal, estimated from the effort")
        assertThat(
            WorkoutSheetWording.estimate(WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "5"), 80.0),
        ).isEqualTo("about 332 kcal, estimated from the pace")
    }

    @Test
    fun `nothing is estimated until kind and minutes are in`() {
        assertThat(WorkoutSheetWording.estimate(WorkoutDraft(), 80.0)).isNull()
        assertThat(WorkoutSheetWording.estimate(WorkoutDraft(kind = WorkoutKind.RUN), 80.0)).isNull()
        assertThat(WorkoutSheetWording.estimate(WorkoutDraft(minutes = "30"), 80.0)).isNull()
    }

    @Test
    fun `with no weight it says why there is no estimate`() {
        assertThat(WorkoutSheetWording.estimate(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45"), null))
            .isEqualTo("No estimate: the profile has no weight")
    }

    /** Running at moderate effort for five hours, no distance: MET 9.3, 8.3 × 80 × 5 = 3,320. */
    @Test
    fun `a thousand and more has a separator`() {
        assertThat(WorkoutSheetWording.estimate(WorkoutDraft(kind = WorkoutKind.RUN, minutes = "300"), 80.0))
            .isEqualTo("about 3,320 kcal, estimated from the effort")
    }

    @Test
    fun `the pace line is a run's pace, and a walk has none`() {
        assertThat(WorkoutSheetWording.pace(WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "5")))
            .isEqualTo("6:00 /km")
        assertThat(WorkoutSheetWording.pace(WorkoutDraft(kind = WorkoutKind.WALK, minutes = "50", distanceKm = "4")))
            .isNull()
    }
}
