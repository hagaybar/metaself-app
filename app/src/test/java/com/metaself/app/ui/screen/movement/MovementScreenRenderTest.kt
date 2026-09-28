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
        assertThat(texts).contains("3.0 km")
        assertThat(texts).contains("250 kcal of movement a day, on average")
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

    /** D85: the trainer is reached from Movement's top bar. */
    @Test
    fun `the top bar has Trainer, which asks for the trainer`() {
        var asked = false
        val texts = draw(onTrainer = { asked = true })

        assertThat(texts).contains("Trainer")
        render.click("Trainer")
        assertThat(asked).isTrue()
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
        onEarlierWeek: () -> Unit = {},
        onLaterWeek: () -> Unit = {},
        onTrainer: () -> Unit = {},
        onReview: (Long) -> Unit = {},
        onSplit: (Workout) -> Unit = {},
        onUndoSplit: () -> Unit = {},
        onPutBack: (Workout) -> Unit = {},
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
            onEarlierWeek = onEarlierWeek,
            onLaterWeek = onLaterWeek,
            onTrainer = onTrainer,
            onReview = onReview,
            onSplit = onSplit,
            onUndoSplit = onUndoSplit,
            onPutBack = onPutBack,
        )
    }

    private companion object {
        /** The run's line, whatever the machine's zone makes its start time (D91). */
        val RUNNING_LINE = Regex("Running · \\d\\d:\\d\\d · 6\\.2 km · 32 min · 5:10 /km")
    }
}
