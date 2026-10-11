package com.metaself.app.ui.screen.movement

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.Role
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.FigureSource
import com.metaself.app.domain.movement.FileWorkout
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.ImportOutcome
import com.metaself.app.domain.movement.WaitingLine
import com.metaself.app.domain.movement.WaitingFile
import com.metaself.app.domain.movement.FilledLater
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.SessionSplit
import com.metaself.app.domain.movement.SessionWitnesses
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutFileRefusal
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.movement.aTypedWorkout
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.MovementSpan
import com.metaself.app.domain.trainer.MovementSpans
import com.metaself.app.domain.trainer.SpanBar
import com.metaself.app.domain.trainer.SpanMeasure
import com.metaself.app.domain.trainer.WeekStrip
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.TrainerReview
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

    /** D130: the Trainer's detail page — its name, and a way back to wherever it was opened from. */
    @Test
    fun `the screen is called Your movement and has a way back`() {
        val texts = draw()

        assertThat(texts).contains("Your movement")
        assertThat(texts).doesNotContain("Movement")
        assertThat(texts).contains("Back")
    }

    /** D131: one 32-minute run; 8.0 + 4.4 km; (410 + 300) / 2 kcal. */
    @Test
    fun `the headline is the week's minutes, with its sessions, km and movement under it`() {
        val texts = draw()

        assertThat(texts).contains("THIS WEEK · FROM MON 31 AUG")
        assertThat(texts).contains("32 min")
        assertThat(texts).contains("1 session · 12.4 km · 355 kcal of movement a day")
        assertThat(render.isDrawnBefore("32 min", "1 session · 12.4 km")).isTrue()
    }

    /** D131: a week with nothing recorded still has its headline. */
    @Test
    fun `an empty week's headline is 0 min, with no line under it`() {
        val empty = MovementWeek.of(today = TEST_EPOCH_DAY, days = emptyList(), workouts = emptyList(), mealsByDay = emptyMap())

        val texts = draw(state = MovementUiState(week = empty, openDay = TEST_EPOCH_DAY, logDay = TEST_EPOCH_DAY))

        assertThat(texts).contains("0 min")
        assertThat(texts.filter { it.contains("kcal of movement a day") }).isEmpty()
    }

    @Test
    fun `today starts open, showing its summary with its detail beneath`() {
        val texts = draw(openDay = TEST_EPOCH_DAY)

        assertThat(texts).contains("410 kcal · Running 6.2 km · slept 7 h 10")
        assertThat(texts).contains("410 kcal of movement · phone and band")
        assertThat(texts).contains("9,000 steps · phone and band")
        assertThat(texts.any(RUNNING_LINE::matches)).isTrue()
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
        assertThat(texts.none(RUNNING_LINE::matches)).isTrue()
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

        assertThat(render.roleOf("Weights · ")).isEqualTo(Role.Button)
        assertThat(render.clickLabelOf("Weights · ")).isEqualTo("change this workout")
        assertThat(render.clickLabelOf("Running · ")).isNull()
        render.click("Weights · ")
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

    /** D91: no exact match, but the day holds a walk: it is offered first, then adding it, under its own line. */
    @Test
    fun `a file that matched nothing exactly offers the day's session of its kind, then adding it`() {
        var chosen: Long? = null
        var added = 0
        val half = aFile.copy(writtenAt = LocalDateTime.of(2026, 9, 3, 10, 30))
        val offered = aTypedWorkout(id = 6, kind = WorkoutKind.WALK, minutes = 40)
        val texts = draw(
            state = MovementUiState(week = week, openDay = null, fileImport = FileImportState(ImportOutcome.NoMatch(half, listOf(offered)))),
            onChooseForFile = { chosen = it },
            onAddFromFile = { added++ },
        )

        assertThat(texts).contains("No session matches this file exactly (Thu 3 Sep 10:30). Is it one of these?")
        assertThat(texts).contains("Or, if it is a session the record does not have:")
        assertThat(render.isDrawnBefore("Walking ·", "Or, if it is a session")).isTrue()
        assertThat(render.isDrawnBefore("Or, if it is a session", "Add it as a workout")).isTrue()
        render.click("Walking ·")
        assertThat(chosen).isEqualTo(6L)
        render.click("Add it as a workout")
        assertThat(added).isEqualTo(1)
    }

    /** D138: a file whose session has not arrived waits; it can still be added now, or left with OK. */
    @Test
    fun `a waiting file says so, and offers Add it as a workout now and OK`() {
        var added = 0
        var dismissed = 0
        val texts = draw(
            state = MovementUiState(week = week, openDay = null, fileImport = FileImportState(ImportOutcome.Waiting(aFile))),
            onAddFromFile = { added++ },
            onDismissFile = { dismissed++ },
        )

        assertThat(texts).contains("The band's session for this file hasn't reached the phone yet. It will be filled in when it does.")
        assertThat(texts).doesNotContain("Done")
        render.click("Add it as a workout now")
        render.click("OK")
        assertThat(added).isEqualTo(1)
        assertThat(dismissed).isEqualTo(1)
    }

    /** D138: under D91's offer, a third choice keeps the file waiting for the band's own session. */
    @Test
    fun `a file offered sessions of its kind can wait for the band instead`() {
        var waited = 0
        val offered = aTypedWorkout(id = 6, kind = WorkoutKind.WALK, minutes = 40)
        val texts = draw(
            state = MovementUiState(week = week, openDay = null, fileImport = FileImportState(ImportOutcome.NoMatch(aFile, listOf(offered)))),
            onWaitForBand = { waited++ },
        )

        assertThat(render.isDrawnBefore("Walking ·", "None of these — wait for the band")).isTrue()
        assertThat(texts).contains("None of these — wait for the band")
        render.click("None of these")
        assertThat(waited).isEqualTo(1)
    }

    /** D138: a file matched nothing is never offered the wait without sessions to set it against. */
    @Test
    fun `a file with no sessions of its kind offers no wait for the band`() {
        val texts = draw(state = MovementUiState(week = week, openDay = null, fileImport = FileImportState(ImportOutcome.NoMatch(aFile))))

        assertThat(texts).doesNotContain("None of these — wait for the band")
    }

    /** D138: above the day rows, three waiting files at most, then how many more; each can be added or dropped. */
    @Test
    fun `waiting files are listed above the days, three then how many more, each with Add and Drop`() {
        var added: FileWorkout? = null
        var dropped: FileWorkout? = null
        val files = (0 until 4).map { aFile.copy(writtenAt = LocalDateTime.of(2026, 9, 3, 7 + it, 5)) }
        val texts = draw(
            state = MovementUiState(week = week, openDay = null, waiting = files.map { WaitingLine(WaitingFile(it, 0)) }),
            onAddWaiting = { added = it },
            onDropWaiting = { dropped = it },
        )

        assertThat(texts).contains("A file from Thu 3 Sep 07:05 is waiting for the band's session")
        assertThat(texts).contains("A file from Thu 3 Sep 09:05 is waiting for the band's session")
        assertThat(texts).doesNotContain("A file from Thu 3 Sep 10:05 is waiting for the band's session")
        assertThat(texts).contains("and 1 more")
        assertThat(render.isDrawnBefore("A file from", "Mon 31 Aug")).isTrue()
        render.click("Add it as a workout")
        render.click("Drop it")
        assertThat(added).isEqualTo(files[0])
        assertThat(dropped).isEqualTo(files[0])
    }

    /** D138: several sessions match a waiting file — it is chosen for on its line. */
    @Test
    fun `a waiting file that matches several sessions offers them`() {
        var chosen: Pair<FileWorkout, Long>? = null
        val choices = listOf(aTypedWorkout(id = 4, kind = WorkoutKind.WALK), aTypedWorkout(id = 5, kind = WorkoutKind.RUN, minutes = 30))
        draw(
            state = MovementUiState(week = week, openDay = null, waiting = listOf(WaitingLine(WaitingFile(aFile, 0), choices))),
            onChooseForWaiting = { file, id -> chosen = file to id },
        )

        render.click("Running ·")
        assertThat(chosen).isEqualTo(aFile to 5L)
    }

    /** D138: a file filled in the background is said, by its session's kind and day. */
    @Test
    fun `a file filled in the background is said above the days`() {
        val texts = draw(state = MovementUiState(week = week, openDay = null, filledLater = listOf(FilledLater(WorkoutKind.WALK, TEST_EPOCH_DAY - 1))))

        assertThat(texts).contains("Filled in your walk of Wed 2 Sep from the file that was waiting.")
        assertThat(render.isDrawnBefore("Filled in your walk", "Mon 31 Aug")).isTrue()
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

    /**
     * The import line changes in place as a file is read: nothing, then "Reading the file…", then what
     * it came to. An early return out of the file lines' column once left the composer's groups
     * unbalanced (Compose compiler 1.5.9), so the SECOND change crashed on every import — a single
     * render with any one state never showed it. Drawn here as the phone does: one composition, its
     * state changed twice.
     */
    @Test
    fun `the import line changes in place from nothing to reading to its outcome`() {
        val state = mutableStateOf(MovementUiState(week = week, openDay = TEST_EPOCH_DAY, logDay = TEST_EPOCH_DAY))
        render.texts {
            MovementScreen(
                state = state.value,
                onToggleDay = {}, onBack = {}, onLogWorkout = {}, onOpenWorkout = {}, onUndoDelete = {},
                onChooseFile = {}, onAddFromFile = {}, onChooseForFile = {}, onDismissFile = {},
                onEarlierWeek = {}, onLaterWeek = {},
            )
        }

        state.value = state.value.copy(fileImport = FileImportState(outcome = null, working = true))
        Snapshot.sendApplyNotifications()
        assertThat(render.textsAgain()).contains("Reading the file…")

        state.value = state.value.copy(fileImport = FileImportState(ImportOutcome.NoMatch(aFile)))
        Snapshot.sendApplyNotifications()
        val texts = render.textsAgain()
        assertThat(texts).contains("Add it as a workout")
        assertThat(texts).doesNotContain("Reading the file…")

        state.value = state.value.copy(fileImport = FileImportState(outcome = null, working = true))
        Snapshot.sendApplyNotifications()
        assertThat(render.textsAgain()).contains("Reading the file…")

        // D91: into the same-kind choice and out of it again, in the same composition.
        state.value = state.value.copy(
            fileImport = FileImportState(ImportOutcome.NoMatch(aFile, listOf(aTypedWorkout(id = 6, kind = WorkoutKind.WALK)))),
        )
        Snapshot.sendApplyNotifications()
        assertThat(render.textsAgain()).contains("Or, if it is a session the record does not have:")

        state.value = state.value.copy(fileImport = FileImportState(outcome = null, working = true))
        Snapshot.sendApplyNotifications()
        assertThat(render.textsAgain()).contains("Reading the file…")

        state.value = state.value.copy(fileImport = null)
        Snapshot.sendApplyNotifications()
        assertThat(render.textsAgain()).doesNotContain("Reading the file…")
    }

    // --- Earlier weeks (D83). What this proves: which arrows are in the tree and what a screen reader
    // calls them, that each asks for its week, the kicker's words, an earlier week's rows and their
    // order, and that Log a workout declares itself disabled with its line above the rows. What it
    // cannot (CLAUDE.md): the arrows' 48 dp, the kicker holding still when an arrow is absent, or the
    // disabled button's grey — phone checks. ---

    /** The week of 24 August (20,689): only its Sunday, 30 August, holds anything. Invented. */
    private val pastWeek = MovementWeek.of(
        today = TEST_EPOCH_DAY,
        days = listOf(HealthDay(epochDay = 20_695, distanceM = 3_000, activeKcal = 250, activeKcalSource = FigureSource.TOTAL)),
        workouts = emptyList(),
        mealsByDay = emptyMap(),
        monday = 20_689,
    )

    @Test
    fun `this week has Previous week when there is one, and never Next week`() {
        var earlier = 0
        draw(
            state = MovementUiState(week = week, openDay = TEST_EPOCH_DAY, canGoEarlier = true, logDay = TEST_EPOCH_DAY),
            onEarlierWeek = { earlier++ },
        )

        assertThat(render.describedCount("Previous week")).isEqualTo(1)
        assertThat(render.describedCount("Next week")).isEqualTo(0)
        render.clickDescribed("Previous week")
        assertThat(earlier).isEqualTo(1)
    }

    @Test
    fun `with nothing earlier there is no Previous week`() {
        draw()

        assertThat(render.describedCount("Previous week")).isEqualTo(0)
        assertThat(render.describedCount("Next week")).isEqualTo(0)
    }

    @Test
    fun `at the earliest week there is no Previous week, and there is Next week`() {
        draw(state = MovementUiState(week = pastWeek, canGoEarlier = false, canGoLater = true))

        assertThat(render.describedCount("Previous week")).isEqualTo(0)
        assertThat(render.describedCount("Next week")).isEqualTo(1)
    }

    @Test
    fun `an earlier week names itself, lists its seven days Sunday first, and steps forward`() {
        var later = 0
        val texts = draw(state = MovementUiState(week = pastWeek, canGoEarlier = true, canGoLater = true), onLaterWeek = { later++ })

        assertThat(texts).contains("LAST WEEK · FROM MON 24 AUG")
        // D131: that week's own minutes — none — not this week's 32.
        assertThat(texts).contains("0 min")
        assertThat(texts).contains("3.0 km · 250 kcal of movement a day")
        assertThat(render.isDrawnBefore("Sun 30 Aug", "Sat 29 Aug")).isTrue()
        assertThat(render.isDrawnBefore("Tue 25 Aug", "Mon 24 Aug")).isTrue()
        assertThat(texts.count { it == "nothing recorded" }).isEqualTo(6)
        // No day is open: Sunday's summary is there, its detail is not.
        assertThat(texts).contains("250 kcal")
        assertThat(texts).doesNotContain("250 kcal of movement · phone and band")
        render.clickDescribed("Next week")
        assertThat(later).isEqualTo(1)
    }

    @Test
    fun `an earlier week with no day open disables Log a workout and says why`() {
        var asked = 0
        val texts = draw(state = MovementUiState(week = pastWeek, logDay = null), onLogWorkout = { asked++ })

        assertThat(texts).contains("Open a day to log onto it")
        assertThat(render.isDrawnBefore("Open a day to log onto it", "Sun 30 Aug")).isTrue()
        assertThat(render.isEnabledDescribed("Log a workout")).isFalse()
        render.clickDescribed("Log a workout")
        assertThat(asked).isEqualTo(0)
    }

    @Test
    fun `with a day to log onto, Log a workout is enabled and there is no line`() {
        val texts = draw(state = MovementUiState(week = pastWeek, openDay = 20_692, logDay = 20_692))

        assertThat(texts).doesNotContain("Open a day to log onto it")
        assertThat(render.isEnabledDescribed("Log a workout")).isTrue()
    }

    /** D83, amended: a week of an earlier year says the year. Invented: the week of Monday 16 June 2025. */
    @Test
    fun `a week in an earlier year says its year in the kicker and on its days`() {
        val monday = java.time.LocalDate.of(2025, 6, 16).toEpochDay()
        val old = MovementWeek.of(today = TEST_EPOCH_DAY, days = emptyList(), workouts = emptyList(), mealsByDay = emptyMap(), monday = monday)

        val texts = draw(state = MovementUiState(week = old, canGoLater = true))

        assertThat(texts).contains("WEEK OF MON 16 JUN 2025")
        assertThat(texts).contains("Sun 22 Jun 2025")
        assertThat(texts).contains("Mon 16 Jun 2025")
    }

    /** D130: the page is reached from the Trainer, so its top bar no longer offers the Trainer. */
    @Test
    fun `the top bar has no Trainer button`() {
        assertThat(draw()).doesNotContain("Trainer")
    }

    /** D87, design question 7: a session with no review asks how it went. */
    @Test
    fun `under the open day's session is How did it go, which asks for that session`() {
        var asked: Long? = null
        val texts = draw(openDay = TEST_EPOCH_DAY, onReview = { asked = it })

        assertThat(texts).contains("How did it go?")
        assertThat(render.isDrawnBefore("Running · ", "How did it go?")).isTrue()
        render.click("How did it go?")
        assertThat(asked).isEqualTo(1L)
    }

    @Test
    fun `a session reviewed without feedback offers Get feedback`() {
        val review = TrainerReview(workoutId = 1, planId = null, felt = Felt.RIGHT, words = null)
        val texts = draw(state = MovementUiState(week = week, openDay = TEST_EPOCH_DAY, reviews = mapOf(1L to review)))

        assertThat(texts).contains("Get feedback")
        assertThat(texts).doesNotContain("How did it go?")
    }

    @Test
    fun `a session with feedback offers See feedback`() {
        val feedback = Feedback(
            headline = "A steady run.", againstPlan = "", numbers = "", nextTime = "", thisWeek = "",
            followed = PlanFollowed.NO_PLAN,
        )
        val review = TrainerReview(workoutId = 1, planId = null, felt = Felt.RIGHT, words = null, feedback = feedback)
        val texts = draw(state = MovementUiState(week = week, openDay = TEST_EPOCH_DAY, reviews = mapOf(1L to review)))

        assertThat(texts).contains("See feedback")
    }

    /** D92: a combined session says who else recorded it, and offers to part it. */
    @Test
    fun `a combined session says how many more recorded it and offers These are two sessions`() {
        val run = week.days.first().workouts.single()
        val session = SessionWitnesses.combine(listOf(run, run.copy(id = 2, durationMinutes = 30)), emptySet()).single()
        val combined = MovementWeek.of(today = TEST_EPOCH_DAY, days = emptyList(), workouts = listOf(session), mealsByDay = emptyMap())
        var parted: Workout? = null

        val texts = draw(state = MovementUiState(week = combined, openDay = TEST_EPOCH_DAY), onSplit = { parted = it })

        assertThat(texts).contains("also recorded by 1 more")
        assertThat(render.isDrawnBefore("Running · ", "also recorded by 1 more")).isTrue()
        render.click("These are two sessions")
        assertThat(parted).isEqualTo(session)
    }

    @Test
    fun `a session recorded once offers no split`() {
        val texts = draw(openDay = TEST_EPOCH_DAY)

        assertThat(texts).doesNotContain("These are two sessions")
    }

    /** D92, decided after review: a split is undone from the line pinned under the title bar. */
    @Test
    fun `after a split, Undo is offered and asks to undo it`() {
        var asked = false
        val texts = draw(state = MovementUiState(week = week, openDay = TEST_EPOCH_DAY, canUndoSplit = true), onUndoSplit = { asked = true })

        assertThat(texts).contains("Split into separate sessions")
        render.click("Undo")
        assertThat(asked).isTrue()
    }

    @Test
    fun `with no split to undo there is no such line`() {
        assertThat(draw()).doesNotContain("Split into separate sessions")
    }

    /** D92, decided after review: a session parted from an overlapping one offers to be put back. */
    @Test
    fun `a session split from an overlapping one offers Put back together`() {
        val run = week.days.first().workouts.single()
        val parted = SessionWitnesses.combine(listOf(run, run.copy(id = 2, durationMinutes = 30)), setOf(SessionSplit(1, 2)))
        val shown = MovementWeek.of(today = TEST_EPOCH_DAY, days = emptyList(), workouts = parted, mealsByDay = emptyMap())
        var asked: Workout? = null

        val texts = draw(state = MovementUiState(week = shown, openDay = TEST_EPOCH_DAY), onPutBack = { asked = it })

        assertThat(texts.count { it == "Put back together" }).isEqualTo(2)
        assertThat(texts).doesNotContain("These are two sessions")
        render.click("Put back together")
        assertThat(asked!!.splits).containsExactly(SessionSplit(1, 2))
    }

    /** D92: a review that sits on the session's other witness opens as that witness's own. */
    @Test
    fun `a review on the other witness opens that witness's review`() {
        var asked: Long? = null
        val review = TrainerReview(workoutId = 2, planId = null, felt = Felt.RIGHT, words = null)
        draw(state = MovementUiState(week = week, openDay = TEST_EPOCH_DAY, reviews = mapOf(1L to review)), onReview = { asked = it })

        render.click("Get feedback")

        assertThat(asked).isEqualTo(2L)
    }

    /** D8: reviews that could not be read give no button, rather than a wrong one. */
    @Test
    fun `with the reviews unread, a session has no button`() {
        val texts = draw(state = MovementUiState(week = week, openDay = TEST_EPOCH_DAY, reviews = null))

        assertThat(texts.any(RUNNING_LINE::matches)).isTrue()
        assertThat(texts).doesNotContain("How did it go?")
    }

    @Test
    fun `a closed day offers no session button`() {
        val texts = draw(openDay = 20_698)

        assertThat(texts).doesNotContain("How did it go?")
    }

    // D135–D137: the spans.

    @Test
    fun `the switch is drawn with Week chosen, and asks for another span`() {
        var asked: MovementSpan? = null
        val texts = draw(onSpan = { asked = it })

        // Not in order: the chosen segment is drawn above its neighbours, which moves it in the semantic order.
        assertThat(texts).containsAtLeast("Week", "12 weeks", "Year")
        assertThat(render.isSelected("Week")).isTrue()
        assertThat(render.isSelected("Year")).isFalse()
        assertThat(render.isDrawnBefore("12 weeks", "THIS WEEK")).isTrue()
        render.click("Year")
        assertThat(asked).isEqualTo(MovementSpan.YEAR)
    }

    @Test
    fun `Week draws the week's chart above the day rows, and the usual week on the line`() {
        val strip = WeekStrip.of(20_696, TEST_EPOCH_DAY, week.days.flatMap { it.workouts }, null)
        val texts = draw(state = MovementUiState(week = week, openDay = TEST_EPOCH_DAY, logDay = TEST_EPOCH_DAY, strip = strip, usualWeek = 30.0))

        assertThat(render.describedCount("The week's sessions, day by day")).isEqualTo(1)
        assertThat(texts).contains("1 session · 12.4 km · 355 kcal of movement a day · about your usual week")
        // The strip's own day letters, without "To do".
        assertThat(texts).doesNotContain("To do")
        assertThat(render.isDrawnBefore("M", "Thu 3 Sep")).isTrue()
    }

    @Test
    fun `12 weeks draws its span, headline, line, chips and one bar per week`() {
        val chart = chart(minutes = List(12) { 120 })
        val texts = draw(state = MovementUiState(span = MovementSpan.TWELVE_WEEKS, chart = chart, canGoEarlier = true))

        assertThat(texts).contains("15 JUN – 3 SEP")
        assertThat(texts).contains("120 min a week")
        assertThat(texts).contains("The last 4 weeks averaged 120, about the same as 120 the 4 before.")
        assertThat(texts).containsAtLeast("Minutes", "Km", "Steps").inOrder()
        assertThat(texts).contains("avg 120")
        assertThat(texts).contains("Tap a week to see its days.")
        assertThat(render.describedCount("Week of 31 Aug: 120 min")).isEqualTo(1)
        assertThat(render.describedCount("Previous 12 weeks")).isEqualTo(1)
        assertThat(render.describedCount("Next 12 weeks")).isEqualTo(0)
        // Week's own parts are not drawn.
        assertThat(texts).doesNotContain("Log a workout")
    }

    @Test
    fun `tapping a week's bar asks for that week, and a bar before the record is not a button`() {
        var opened: SpanBar? = null
        val chart = chart(minutes = List(6) { null } + List(6) { 90 })
        draw(state = MovementUiState(span = MovementSpan.TWELVE_WEEKS, chart = chart), onBar = { opened = it })

        render.clickDescribed("Week of 31 Aug: 90 min")
        assertThat(opened).isEqualTo(chart.bars.last())
        assertThat(render.describedCount("Week of 15 Jun: before the record")).isEqualTo(1)
        assertThat(runCatching { render.clickDescribed("Week of 15 Jun: before the record") }.isFailure).isTrue()
    }

    @Test
    fun `a chip asks for its measure`() {
        var asked: SpanMeasure? = null
        draw(state = MovementUiState(span = MovementSpan.TWELVE_WEEKS, chart = chart(minutes = List(12) { 60 })), onMeasure = { asked = it })

        render.click("Km")

        assertThat(asked).isEqualTo(SpanMeasure.KM)
    }

    @Test
    fun `a span wholly before the record says so and draws no bars`() {
        val texts = draw(state = MovementUiState(span = MovementSpan.TWELVE_WEEKS, chart = chart(minutes = List(12) { null })))

        assertThat(texts).contains("Nothing recorded this far back.")
        assertThat(render.describedCount("Week of 31 Aug: before the record")).isEqualTo(0)
        assertThat(texts.filter { it.endsWith("a week") }).isEmpty()
    }

    @Test
    fun `Year draws its months and the current one so far`() {
        val year = MovementSpans.months(
            TEST_EPOCH_DAY, TEST_EPOCH_DAY,
            listOf(aTypedWorkout(id = 1, epochDay = TEST_EPOCH_DAY, minutes = 85)), emptyList(), emptyList(), recordStart = 20_697,
        )
        val texts = draw(state = MovementUiState(span = MovementSpan.YEAR, chart = year, canGoLater = false))

        assertThat(texts).contains("OCT 2025 – SEP 2026")
        assertThat(texts).contains("85 min so far")
        assertThat(texts).contains("September so far")
        assertThat(texts).contains("Tap a month to see its weeks.")
        assertThat(render.describedCount("September so far: 85 min")).isEqualTo(1)
        assertThat(render.describedCount("Next year")).isEqualTo(0)
    }

    /** Twelve invented weeks ending this week; a null is a week before the record. */
    private fun chart(minutes: List<Int?>): com.metaself.app.domain.trainer.SpanChart {
        val bars = minutes.mapIndexed { index, value ->
            val first = 20_696L - 7L * (11 - index)
            SpanBar(first, first + 6, TEST_EPOCH_DAY in first..first + 6, value != null, 0, value ?: 0, null, null)
        }
        return com.metaself.app.domain.trainer.SpanChart(MovementSpan.TWELVE_WEEKS, TEST_EPOCH_DAY, bars)
    }


    private fun draw(
        openDay: Long? = TEST_EPOCH_DAY,
        // This week: with every day closed, Log a workout still logs onto today (D76).
        state: MovementUiState = MovementUiState(week = week, openDay = openDay, logDay = openDay ?: TEST_EPOCH_DAY),
        onToggleDay: (Long) -> Unit = {},
        onLogWorkout: () -> Unit = {},
        onOpenWorkout: (Workout) -> Unit = {},
        onUndoDelete: () -> Unit = {},
        onChooseFile: () -> Unit = {},
        onAddFromFile: () -> Unit = {},
        onChooseForFile: (Long) -> Unit = {},
        onDismissFile: () -> Unit = {},
        onWaitForBand: () -> Unit = {},
        onAddWaiting: (FileWorkout) -> Unit = {},
        onChooseForWaiting: (FileWorkout, Long) -> Unit = { _, _ -> },
        onDropWaiting: (FileWorkout) -> Unit = {},
        onEarlierWeek: () -> Unit = {},
        onLaterWeek: () -> Unit = {},
        onReview: (Long) -> Unit = {},
        onSplit: (Workout) -> Unit = {},
        onUndoSplit: () -> Unit = {},
        onPutBack: (Workout) -> Unit = {},
        onSpan: (MovementSpan) -> Unit = {},
        onMeasure: (SpanMeasure) -> Unit = {},
        onBar: (SpanBar) -> Unit = {},
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
            onWaitForBand = onWaitForBand,
            onAddWaiting = onAddWaiting,
            onChooseForWaiting = onChooseForWaiting,
            onDropWaiting = onDropWaiting,
            onEarlierWeek = onEarlierWeek,
            onLaterWeek = onLaterWeek,
            onReview = onReview,
            onSplit = onSplit,
            onUndoSplit = onUndoSplit,
            onPutBack = onPutBack,
            onSpan = onSpan,
            onMeasure = onMeasure,
            onBar = onBar,
        )
    }

    private companion object {
        /** The run's line, whatever the machine's zone makes its start time (D91). */
        val RUNNING_LINE = Regex("Running · \\d\\d:\\d\\d · 6\\.2 km · 32 min · 5:10 /km")
    }
}
