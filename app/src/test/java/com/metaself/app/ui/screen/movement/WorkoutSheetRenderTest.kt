package com.metaself.app.ui.screen.movement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.Effort
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutDraft
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.aTypedWorkout
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4. The sheet's content, drawn on its own (see
 * `WorkoutSheet`). 80 kg is `aProfile()`'s; every figure is invented, and the kcal are worked in
 * `WorkoutDraftTest`.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutSheetRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `a new sheet offers six kinds, starts on Moderate, and cannot be saved yet`() {
        val texts = draw(WorkoutDraft())

        assertThat(texts).containsAtLeast(
            "Log a workout", "Save", "Run", "Walk", "Cycle", "Swim", "Strength", "Other",
            "Minutes", "Easy", "Moderate", "Hard", "set it yourself", "Note", "Not now",
        )
        assertThat(render.isSelected("Moderate")).isTrue()
        assertThat(render.isSelected("Easy")).isFalse()
        assertThat(render.isSelected("Run")).isFalse()
        assertThat(render.isEnabled("Save")).isFalse()
        assertThat(texts.none { it.startsWith("Distance") }).isTrue()
        assertThat(texts).doesNotContain("Delete this workout")
    }

    @Test
    fun `a run offers a distance and shows its pace and estimate live`() {
        val texts = draw(WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "5"))

        assertThat(texts).contains("Distance (km)")
        assertThat(texts).contains("6:00 /km")
        assertThat(texts).contains("about 332 kcal, estimated from the pace")
        assertThat(render.isSelected("Run")).isTrue()
        assertThat(render.isEnabled("Save")).isTrue()
        assertThat(render.fieldTexts()).containsAtLeast("30", "5")
    }

    /** D78: pace for runs only. */
    @Test
    fun `a walk offers a distance but shows no pace`() {
        val texts = draw(WorkoutDraft(kind = WorkoutKind.WALK, minutes = "50", distanceKm = "4"))

        assertThat(texts).contains("Distance (km)")
        assertThat(texts.none { it.endsWith("/km") }).isTrue()
    }

    @Test
    fun `strength has no distance, and its estimate is from the effort`() {
        val texts = draw(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45", distanceKm = "5"))

        assertThat(texts.none { it.startsWith("Distance") }).isTrue()
        assertThat(texts).contains("about 150 kcal, estimated from the effort")
    }

    @Test
    fun `choosing a kind asks for it`() {
        var asked: WorkoutDraft? = null
        draw(WorkoutDraft(), onDraft = { asked = it })

        render.click("Swim")

        assertThat(asked!!.kind).isEqualTo(WorkoutKind.SWIM)
    }

    /** Activity spec §8 item 4: effort can be changed, and choosing the chosen one keeps it. */
    @Test
    fun `effort can be changed but not cleared`() {
        var asked: WorkoutDraft? = null
        draw(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45"), onDraft = { asked = it })

        render.click("Hard")
        assertThat(asked!!.effort).isEqualTo(Effort.HARD)

        render.click("Moderate")
        assertThat(asked!!.effort).isEqualTo(Effort.MODERATE)
    }

    @Test
    fun `set it yourself asks for a figure of his own`() {
        var asked: WorkoutDraft? = null
        draw(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45"), onDraft = { asked = it })

        render.click("set it yourself")

        assertThat(asked!!.ownEnergy).isTrue()
    }

    @Test
    fun `with a figure of his own there is a field for it and no estimate`() {
        val texts = draw(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45", ownEnergy = true, energyKcal = "300"))

        assertThat(texts).contains("Energy (kcal)")
        assertThat(texts).contains("use the estimate")
        assertThat(render.fieldTexts()).contains("300")
        assertThat(texts.none { it.startsWith("about ") }).isTrue()
    }

    @Test
    fun `a bad minutes value says why`() {
        val texts = draw(WorkoutDraft(minutes = "0"))

        assertThat(texts).contains("Whole minutes, 1 to 1,440")
    }

    @Test
    fun `a bad distance value says why`() {
        val texts = draw(WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "abc"))

        assertThat(texts).contains("Up to 1,000 km")
    }

    @Test
    fun `a bad own-energy figure says why`() {
        val texts = draw(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45", ownEnergy = true, energyKcal = "-5"))

        assertThat(texts).contains("Whole kcal, 0 to 20,000")
    }

    @Test
    fun `set it yourself with nothing typed says to type it or go back`() {
        val texts = draw(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45", ownEnergy = true, energyKcal = ""))

        assertThat(texts).contains("Type the kcal, or go back to the estimate")
    }

    /** Nit: a visible group label doubles as the group's name for a screen reader. */
    @Test
    fun `the kind chips and the effort segments are each labelled`() {
        val texts = draw(WorkoutDraft())

        assertThat(texts).containsAtLeast("Kind", "Effort")
    }

    @Test
    fun `with no weight it says why there is no estimate`() {
        val texts = draw(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45"), weightKg = null)

        assertThat(texts).contains("No estimate: the profile has no weight")
    }

    @Test
    fun `a typed workout opened to change has Delete, and pressing it asks for it`() {
        var deleted = false
        val texts = draw(WorkoutDraft.from(aTypedWorkout()), editing = aTypedWorkout(), onDelete = { deleted = true })

        assertThat(texts).contains("Change this workout")
        render.click("Delete this workout")
        assertThat(deleted).isTrue()
    }

    @Test
    fun `a save that failed says so`() {
        val texts = draw(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45"), failure = WriteFailure.SAVE)

        assertThat(texts).contains("Not saved; Recent problems says why.")
        assertThat(texts).doesNotContain("Not deleted; Recent problems says why.")
    }

    @Test
    fun `a delete that failed says it was not deleted, not that it was not saved`() {
        val texts = draw(
            WorkoutDraft.from(aTypedWorkout()),
            editing = aTypedWorkout(),
            failure = WriteFailure.DELETE,
        )

        assertThat(texts).contains("Not deleted; Recent problems says why.")
        assertThat(texts).doesNotContain("Not saved; Recent problems says why.")
    }

    /**
     * D76: Save kept in view. It sits in the title row, above every field, so neither a long sheet nor
     * a keyboard rising from the bottom can cover it (plan design question 1). A short box stands in
     * for the phone; only relative geometry is asserted (CLAUDE.md). A node pushed out of the window
     * reads 0, so Save is asserted to be below the box's top as well as above the first field.
     */
    @Test
    @Config(qualifiers = "+h640dp")
    fun `Save stays in view above a long sheet`() {
        val longNote = List(40) { "line" }.joinToString("\n")
        render.texts {
            Box(Modifier.height(SHORT_SHEET_DP.dp)) {
                WorkoutSheetContent(
                    sheet = WorkoutSheetState(
                        draft = WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "5", note = longNote),
                        epochDay = TEST_EPOCH_DAY,
                        weightKg = 80.0,
                    ),
                    onDraft = {}, onSave = {}, onDelete = {}, onCancel = {},
                )
            }
        }

        val save = render.topDp("Save")
        assertThat(save).isIn(Range.open(0, SHORT_SHEET_DP))
        assertThat(save).isLessThan(render.topDp("Minutes"))
    }

    private fun draw(
        draft: WorkoutDraft,
        weightKg: Double? = 80.0,
        editing: Workout? = null,
        failure: WriteFailure? = null,
        onDraft: (WorkoutDraft) -> Unit = {},
        onDelete: () -> Unit = {},
    ): List<String> = render.texts {
        WorkoutSheetContent(
            sheet = WorkoutSheetState(
                draft = draft, epochDay = TEST_EPOCH_DAY, weightKg = weightKg, editing = editing, failure = failure,
            ),
            onDraft = onDraft,
            onSave = {},
            onDelete = onDelete,
            onCancel = {},
        )
    }

    private companion object {
        /** Shorter than a sheet with a forty-line note needs. */
        const val SHORT_SHEET_DP = 400
    }
}
