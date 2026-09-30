package com.metaself.app.ui.screen.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.HealthRecordState
import com.metaself.app.data.movement.StepAccess
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.reminder.Reminder
import com.metaself.app.domain.window.EatingWindow
import com.metaself.app.domain.window.WindowRule
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.settings.SettingsIndexWording
import java.time.LocalDate
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The Settings index (D79): seven rows in order, each its title with its one status line straight
 * after, each opening its page — and none of any page's controls. Order in the drawn tree is what a
 * render test here can say about position; nothing about size (`CLAUDE.md`). Figures invented.
 *
 * JUnit 4 because Robolectric's runner is.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsIndexRenderTest {

    private val render = ComposeRender()

    private val opened = mutableListOf<SettingsPage>()

    private val state = SettingsUiState(
        loaded = true,
        windowRead = true,
        stepsRead = true,
        windowRule = WindowRule.Fixed(EatingWindow(10, 18, TEST_EPOCH_DAY)),
        reminder = Reminder(enabled = true, hour = 21),
        stepAccess = StepAccess.GRANTED,
        healthRecord = HealthRecordState(days = 30),
        hasBackupFolder = true,
        driveOn = true,
        lastBackup = LocalDate.ofEpochDay(TEST_EPOCH_DAY),
        hasKey = true,
        dailyCeiling = 20,
        offUsername = "example-user",
        hasOffPassword = true,
        problems = listOf("an invented problem", "another invented problem"),
    )

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `seven rows, in order, each with its status line straight after its title`() {
        val texts = draw()

        val rows = listOf(
            "Eating" to SettingsIndexWording.eating(state.windowRule, state.reminder),
            "Movement and health" to SettingsIndexWording.movement(StepAccess.GRANTED, 30),
            "Backups" to SettingsIndexWording.backups(true, true, state.lastBackup),
            "AI estimates" to SettingsIndexWording.ai(true, 20),
            "Food database" to SettingsIndexWording.foodDatabase(true),
            "Recent problems" to SettingsIndexWording.problems(2),
            "Test the trainer's instructions" to SettingsIndexWording.TRAINER_INSTRUCTIONS,
        )
        assertThat(texts).containsAtLeastElementsIn(rows.flatMap { listOf(it.first, it.second) })
            .inOrder()
        rows.forEach { (title, status) ->
            assertThat(texts.indexOf(status)).isEqualTo(texts.indexOf(title) + 1)
        }
    }

    /** The figures, as the phone draws them for this invented state. */
    @Test
    fun `the status lines read as the wording says`() {
        val texts = draw()

        assertThat(texts).containsAtLeast(
            "10:00–18:00 · reminder at 21:00",
            "On · health record 30 days",
            "Daily to a folder and to Drive · last copy 3 Sep",
            "Key saved · up to 20 a day",
            "Open Food Facts · signed in",
            "2 recent",
            "A testing tool · stores nothing",
        ).inOrder()
    }

    @Test
    fun `each row opens its page`() {
        draw()

        listOf(
            "Eating", "Movement and health", "Backups", "AI estimates", "Food database",
            "Recent problems", "Test the trainer's instructions",
        ).forEach(render::click)

        assertThat(opened).containsExactlyElementsIn(SettingsPage.entries).inOrder()
    }

    @Test
    fun `no page's controls are on the index`() {
        val texts = draw()

        assertThat(texts).containsNoneOf(
            "Save the key", "Pick a folder", "Change the folder", "Keep tab on this",
            "Save the account", "Copy them", "Allow MetaSelf to read your health data",
        )
    }

    /**
     * Before anything has been read, every status line would be a made-up zero read back as fact —
     * "No hours set" for a window nobody has looked at yet, "reminder off" for a reminder flag that
     * defaults to false. None of them may be drawn.
     */
    @Test
    fun `nothing is said about any row before the page has read anything`() {
        val texts = render.texts {
            SettingsScreen(state = SettingsUiState(), onOpen = { opened += it }, onBack = {})
        }

        assertThat(texts).containsNoneOf(
            "Health Connect not available",
            "No hours set",
            "Not set up",
            "No key yet",
            "Not signed in",
            "reminder off",
        )
    }

    private fun draw(): List<String> = render.texts {
        SettingsScreen(state = state, onOpen = { opened += it }, onBack = {})
    }
}
