package com.metaself.app.ui.screen.movement

import androidx.compose.ui.semantics.Role
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.FigureSource
import com.metaself.app.domain.movement.FileWorkout
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.ImportOutcome
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutFileRefusal
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.movement.aTypedWorkout
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

/**
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4.
 *
 * What this can prove: which words are drawn, their order, that a day row is a button that says
 * whether it is open, and that tapping it asks for that day. What it cannot (CLAUDE.md, Testing): the
 * large figure's size or face, a row's touch height, any wrapping — those are phone checks.
 *
 * TEST_EPOCH_DAY is Thursday 3 September 2026. Every figure is invented: today moved 410 kcal and
 * slept 7 h 10 with one 32-minute run; yesterday moved 300 kcal; Tuesday and Monday hold nothing; the
 * week of 24 August has one day with a distance.
 */
@RunWith(RobolectricTestRunner::class)
class MovementScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    private val week = MovementWeek.of(
        today = TEST_EPOCH_DAY,
        days = listOf(
            HealthDay(
                epochDay = 20_699, steps = 9_000, stepsSource = FigureSource.TOTAL, distanceM = 8_000,
                activeKcal = 410, activeKcalSource = FigureSource.TOTAL, sleepMinutes = 430,
            ),
            HealthDay(epochDay = 20_698, distanceM = 4_400, activeKcal = 300, activeKcalSource = FigureSource.TOTAL),
            HealthDay(epochDay = 20_690, distanceM = 10_000),
        ),
        workouts = listOf(
            Workout(
                id = 1, epochDay = 20_699, startedAtMillis = 0, durationMinutes = 32,
                kind = WorkoutKind.RUN, title = "Running", distanceM = 6_200, energyKcal = null,
                energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED,
                hidden = false, note = null,
            ),
        ),
        mealsByDay = emptyMap(),
    )

    @Test
    fun `the screen is called Movement and has a way back`() {
        val texts = draw()

        assertThat(texts).contains("Movement")
        assertThat(texts).contains("Back")
    }

    /** D74: 8.0 + 4.4 km; (410 + 300) / 2 kcal; one run. */
    @Test
    fun `the headline is the week's distance, the average movement and the workouts`() {
        val texts = draw()

        assertThat(texts).contains("THIS WEEK · FROM MON 31 AUG")
        assertThat(texts).contains("12.4 km")
        assertThat(texts).contains("355 kcal of movement a day, on average")
        assertThat(texts).contains("1 workout · 32 min")
    }

    @Test
    fun `today starts open, showing its summary with its detail beneath`() {
        val texts = draw(openDay = TEST_EPOCH_DAY)

        assertThat(texts).contains("410 kcal · Running 6.2 km · slept 7 h 10")
        assertThat(texts).contains("410 kcal of movement · phone and band")
        assertThat(texts).contains("9,000 steps · phone and band")
        assertThat(texts).contains("Running · 6.2 km · 32 min · 5:10 /km")
        assertThat(texts).contains("Slept 7 h 10")
        // The heading and the summary are one button; the detail lines are drawn after it, not in it.
        assertThat(render.clickLabelOf("410 kcal · Running")).isEqualTo("close this day")
        assertThat(render.clickLabelOf("410 kcal of movement")).isNull()
        assertThat(render.isDrawnBefore("410 kcal · Running", "410 kcal of movement")).isTrue()
        assertThat(render.isDrawnBefore("Slept 7 h 10", "Wed 2 Sep")).isTrue()
    }

    @Test
    fun `a closed day shows its summary and not its detail`() {
        val texts = draw(openDay = TEST_EPOCH_DAY)

        assertThat(texts).contains("300 kcal")
        assertThat(texts).doesNotContain("300 kcal of movement · phone and band")
    }

    @Test
    fun `with every day closed, today shows its summary and not its detail`() {
        val texts = draw(openDay = null)

        assertThat(texts).contains("410 kcal · Running 6.2 km · slept 7 h 10")
        assertThat(texts).doesNotContain("410 kcal of movement · phone and band")
        assertThat(texts).doesNotContain("Running · 6.2 km · 32 min · 5:10 /km")
    }

    @Test
    fun `another day open shows its detail and closes today's`() {
        val texts = draw(openDay = 20_698)

        assertThat(texts).contains("300 kcal")
        assertThat(texts).contains("300 kcal of movement · phone and band")
        assertThat(texts).contains("410 kcal · Running 6.2 km · slept 7 h 10")
        assertThat(texts).doesNotContain("410 kcal of movement · phone and band")
    }

    @Test
    fun `a day with nothing says so`() {
        assertThat(draw().count { it == "nothing recorded" }).isEqualTo(2)
    }

    @Test
    fun `tapping a day asks for that day`() {
        var asked: Long? = null
        draw(onToggleDay = { asked = it })

        render.click("Wed 2 Sep")

        assertThat(asked).isEqualTo(20_698L)
    }

    @Test
    fun `tapping the open day asks for it too, which closes it`() {
        var asked: Long? = null
        draw(openDay = TEST_EPOCH_DAY, onToggleDay = { asked = it })

        render.click("Thu 3 Sep")

        assertThat(asked).isEqualTo(TEST_EPOCH_DAY)
    }

    @Test
    fun `each day is a button that says whether it is open`() {
        draw(openDay = TEST_EPOCH_DAY)

        assertThat(render.roleOf("Thu 3 Sep")).isEqualTo(Role.Button)
        assertThat(render.stateOf("Thu 3 Sep")).isEqualTo("open")
        assertThat(render.clickLabelOf("Thu 3 Sep")).isEqualTo("close this day")
        assertThat(render.stateOf("Wed 2 Sep")).isEqualTo("closed")
        assertThat(render.clickLabelOf("Wed 2 Sep")).isEqualTo("show this day")
    }

    @Test
    fun `the last four weeks are at the foot`() {
        val texts = draw()

        assertThat(texts).contains("Last four weeks: 10.0 · — · — · — km")
        assertThat(render.isDrawnBefore("Mon 31 Aug", "Last four weeks")).isTrue()
    }

    /** D63: no burn, no net. */
    @Test
    fun `nothing claims a burn`() {
        val texts = draw()

        assertThat(texts.none { it.contains("burn", ignoreCase = true) }).isTrue()
    }

    /** D76: the button at the foot, kept in view. */
    @Test
    fun `Log a workout is offered, and asks to log one`() {
        var asked = false
        val texts = draw(onLogWorkout = { asked = true })

        assertThat(texts).contains("Log a workout")
        render.clickDescribed("Log a workout")
        assertThat(asked).isTrue()
    }

    @Test
    fun `a record that could not be read offers no button`() {
        val texts = draw(state = MovementUiState(unreadable = true))

        assertThat(texts.none { it.contains("Log a workout") }).isTrue()
    }

    /** D76: a typed workout's line opens it; a synced one's does not (corrections are a later phase). */
    @Test
    fun `a typed workout in the open day is a door to change it, and a synced one is not`() {
        val typed = aTypedWorkout(id = 2, startedAtMillis = 1)
        val withTyped = MovementWeek.of(
            today = TEST_EPOCH_DAY,
            days = emptyList(),
            workouts = week.days.first().workouts + typed,
            mealsByDay = emptyMap(),
        )
        var opened: Workout? = null
        draw(state = MovementUiState(week = withTyped, openDay = TEST_EPOCH_DAY), onOpenWorkout = { opened = it })

        assertThat(render.roleOf("Weights · 45 min")).isEqualTo(Role.Button)
        assertThat(render.clickLabelOf("Weights · 45 min")).isEqualTo("change this workout")
        assertThat(render.clickLabelOf("Running · 6.2 km")).isNull()
        render.click("Weights · 45 min")
        assertThat(opened).isEqualTo(typed)
    }

    /**
     * Plan design question 8: a delete is undone the way the record screen's rows are — its "Deleted ·
     * Undo" line — pinned under the title bar, above everything the list scrolls, because the bottom
     * edge holds "Log a workout".
     */
    @Test
    fun `after a delete, Undo is offered above the week and asks to put it back`() {
        var asked = false
        val texts = draw(state = MovementUiState(week = week, openDay = TEST_EPOCH_DAY, canUndo = true), onUndoDelete = { asked = true })

        assertThat(texts).containsAtLeast("Deleted", "Undo")
        assertThat(render.isDrawnBefore("Deleted", "THIS WEEK")).isTrue()
        render.click("Undo")
        assertThat(asked).isTrue()
    }

    @Test
    fun `with nothing to undo there is no Undo`() {
        val texts = draw()

        assertThat(texts).doesNotContain("Undo")
        assertThat(texts).doesNotContain("Deleted")
    }

    /** D8: a failed Undo is said, and Undo stays to try again. */
    @Test
    fun `an Undo that failed says so and is still offered`() {
        val texts = draw(state = MovementUiState(week = week, openDay = TEST_EPOCH_DAY, canUndo = true, undoFailed = true))

        assertThat(texts).contains("Not put back; Recent problems says why.")
        assertThat(texts).contains("Undo")
    }

    @Test
    fun `a week with nothing recorded has no large figure and every day says so`() {
        val empty = MovementWeek.of(TEST_EPOCH_DAY, emptyList(), emptyList(), emptyMap())

        val texts = draw(state = MovementUiState(week = empty, openDay = TEST_EPOCH_DAY))

        assertThat(texts).contains("THIS WEEK · FROM MON 31 AUG")
        assertThat(texts.none { it.endsWith(" km") }).isTrue()
        assertThat(texts.count { it == "nothing recorded" }).isEqualTo(4)
    }

    @Test
    fun `a record that could not be read says so`() {
        val texts = draw(state = MovementUiState(unreadable = true))

        assertThat(texts).contains("The movement record could not be read; Recent problems says why.")
    }

    // --- A workout file (D82). What this proves: the words and their order, and that each button
    // calls back. What it cannot: the system picker opening, or the share list — phone checks. ---

    /** An invented file: a walk at 10:00 on 3 September 2026. */
    private val aFile = FileWorkout(writtenAt = LocalDateTime.of(2026, 9, 3, 10, 0), instant = null, seconds = 1_800, distanceM = 3_000.0)

    @Test
    fun `the way to import a workout file is drawn after the week, and asks for a file`() {
        var asked = 0
        draw(onChooseFile = { asked++ })

        assertThat(render.isDrawnBefore("Mon 31 Aug", "Import a workout file")).isTrue()
        render.click("Import a workout file")

        assertThat(asked).isEqualTo(1)
    }

    @Test
    fun `a file that matched nothing says so and offers to add it`() {
        var added = 0
        val texts = draw(
            state = MovementUiState(week = week, openDay = TEST_EPOCH_DAY, fileImport = FileImportState(ImportOutcome.NoMatch(aFile))),
            onAddFromFile = { added++ },
        )

        assertThat(texts).contains("No workout matches this file (Thu 3 Sep 10:00).")
        assertThat(render.isDrawnBefore("No workout matches", "Add it as a workout")).isTrue()
        render.click("Add it as a workout")
        assertThat(added).isEqualTo(1)
    }

    @Test
    fun `several matches are listed, and choosing one calls back with it`() {
        var chosen: Long? = null
        val choices = listOf(aTypedWorkout(id = 4, kind = WorkoutKind.WALK), aTypedWorkout(id = 5, kind = WorkoutKind.RUN, minutes = 30))
        val texts = draw(
            state = MovementUiState(week = week, openDay = null, fileImport = FileImportState(ImportOutcome.Several(aFile, choices))),
            onChooseForFile = { chosen = it },
        )

        assertThat(texts).contains("Several workouts match this file; choose one.")
        render.click("Running ·")
        assertThat(chosen).isEqualTo(5L)
    }

    @Test
    fun `a refused file says why, and the line can be dismissed`() {
        var dismissed = 0
        val texts = draw(
            state = MovementUiState(
                week = week, openDay = TEST_EPOCH_DAY,
                fileImport = FileImportState(ImportOutcome.Refused(WorkoutFileRefusal.NOTHING_TO_ADD)),
            ),
            onDismissFile = { dismissed++ },
        )

        assertThat(texts).contains("The file has no distance, steps or calories to add.")
        assertThat(texts).doesNotContain("Add it as a workout")
        render.click("Done")
        assertThat(dismissed).isEqualTo(1)
    }

    private fun draw(
        openDay: Long? = TEST_EPOCH_DAY,
        state: MovementUiState = MovementUiState(week = week, openDay = openDay),
        onToggleDay: (Long) -> Unit = {},
        onLogWorkout: () -> Unit = {},
        onOpenWorkout: (Workout) -> Unit = {},
        onUndoDelete: () -> Unit = {},
        onChooseFile: () -> Unit = {},
        onAddFromFile: () -> Unit = {},
        onChooseForFile: (Long) -> Unit = {},
        onDismissFile: () -> Unit = {},
    ): List<String> = render.texts {
        MovementScreen(
            state = state,
            onToggleDay = onToggleDay,
            onBack = {},
            onLogWorkout = onLogWorkout,
            onOpenWorkout = onOpenWorkout,
            onUndoDelete = onUndoDelete,
            onChooseFile = onChooseFile,
            onAddFromFile = onAddFromFile,
            onChooseForFile = onChooseForFile,
            onDismissFile = onDismissFile,
        )
    }
}
