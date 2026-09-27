package com.metaself.app.ui.screen.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.HealthRecordState
import com.metaself.app.data.movement.StepAccess
import com.metaself.app.domain.reminder.Reminder
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.movement.MovementWording
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Four of the six Settings pages (D79) — Eating, Movement and health, Food database, Recent
 * problems — each drawn on its own: its title bar, its controls in the old order, and the sentence
 * for an action that threw on the page that holds the action. Backups and AI estimates have their
 * own classes. Asserted by order in the drawn tree, which is what a render test here can say about
 * position; nothing about size or wrapping (`CLAUDE.md`). Figures invented.
 *
 * JUnit 4 because Robolectric's runner is.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsPagesRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    // --- Eating ---

    @Test
    fun `the Eating page holds when you eat, then the reminder`() {
        val texts = eating(SettingsUiState(reminder = Reminder(enabled = true, hour = 21)))

        assertThat(texts).contains("Eating")
        assertThat(texts.indexOf("When you eat")).isLessThan(texts.indexOf("Set the hours"))
        assertThat(texts.indexOf("Set the hours")).isLessThan(texts.indexOf("Keep tab on this"))
        assertThat(texts.indexOf("Keep tab on this")).isLessThan(texts.indexOf("Set a ratio"))
        assertThat(texts.indexOf("Set a ratio")).isLessThan(texts.indexOf("Save this ratio"))
        assertThat(texts.indexOf("Save this ratio")).isLessThan(texts.indexOf("Daily reminder"))
        assertThat(texts.indexOf("Daily reminder"))
            .isLessThan(texts.indexOf("Send it now, to check it arrives"))
    }

    @Test
    fun `the Eating page holds nothing from the other pages`() {
        val texts = eating(SettingsUiState())

        assertThat(texts).containsNoneOf(
            "Your OpenAI API key", "Keeping a copy", "Open Food Facts account", "Recent problems",
        )
    }

    @Test
    fun `a window or a reminder that threw is said on the Eating page`() {
        assertThat(eating(SettingsUiState(failed = SettingsRefusal(SettingsPart.WINDOW, ActionRefused.NOTHING_CHANGED))))
            .contains(NOTHING)
        assertThat(eating(SettingsUiState(failed = SettingsRefusal(SettingsPart.REMINDER, ActionRefused.MAYBE_PARTIAL))))
            .contains(PARTIAL)
    }

    // --- Movement and health ---

    @Test
    fun `the Movement page says what can be read, and offers Connect when it cannot`() {
        val texts = movement(SettingsUiState(stepAccess = StepAccess.NOT_PERMITTED))

        assertThat(texts).contains("Movement and health")
        assertThat(texts).contains(
            MovementWording.status(access = StepAccess.NOT_PERMITTED, hasNormal = false, daysSoFar = 0),
        )
        assertThat(texts).contains(CONNECT)
    }

    /** Drive's way back to the months moved to Backups (D79); Movement points there. */
    @Test
    fun `the Movement page points to Backups for the detailed readings, and offers no Drive action`() {
        val texts = movement(
            SettingsUiState(
                stepAccess = StepAccess.GRANTED,
                driveOn = true,
                healthRecord = HealthRecordState(days = 30),
            ),
        )

        assertThat(texts).contains(POINTER)
        assertThat(texts).doesNotContain(BRING_BACK)
        assertThat(texts.none { it.startsWith("Detailed readings are") }).isTrue()
    }

    @Test
    fun `with Drive off and no record, there is nothing to point to`() {
        val texts = movement(SettingsUiState(stepAccess = StepAccess.GRANTED))

        assertThat(texts).doesNotContain(POINTER)
    }

    @Test
    fun `with Drive off but a record kept, the pointer is there`() {
        val texts = movement(
            SettingsUiState(stepAccess = StepAccess.GRANTED, healthRecord = HealthRecordState(days = 30)),
        )

        assertThat(texts).contains(POINTER)
    }

    /** D80: the way to what the band sends, from the page about what can be read. */
    @Test
    fun `the Movement page has a row for what the band sends, and it opens the page`() {
        var opened = 0
        val texts = render.texts {
            MovementSettingsPage(state = SettingsUiState(), onConnectSteps = {}, onOpenBandReport = { opened++ }, onBack = {})
        }

        assertThat(texts).contains("What the band sends")
        render.click("What the band sends")
        assertThat(opened).isEqualTo(1)
    }

    // --- Food database ---

    @Test
    fun `the Food database page holds the Open Food Facts account`() {
        val texts = food(SettingsUiState())

        assertThat(texts).contains("Food database")
        assertThat(texts).contains("Open Food Facts account")
        assertThat(texts).contains("Save the account")
        assertThat(texts).doesNotContain("Forget it")
    }

    @Test
    fun `signed in, the account can be forgotten`() {
        val texts = food(SettingsUiState(offUsername = "example-user", hasOffPassword = true))

        assertThat(texts).contains("Forget it")
    }

    @Test
    fun `an account that threw is said on the Food database page`() {
        val texts = food(SettingsUiState(failed = SettingsRefusal(SettingsPart.OFF_ACCOUNT, ActionRefused.NOTHING_CHANGED)))

        assertThat(texts).contains(NOTHING)
    }

    // --- Recent problems ---

    @Test
    fun `with no problems, the page says so and offers nothing`() {
        val texts = problems(SettingsUiState())

        assertThat(texts.count { it == "Recent problems" }).isEqualTo(1)
        assertThat(texts).contains("Nothing has gone wrong.")
        assertThat(texts).doesNotContain("Copy them")
        assertThat(texts).doesNotContain("Clear them")
    }

    @Test
    fun `with problems, each is listed and they can be copied or cleared`() {
        val texts = problems(SettingsUiState(problems = listOf(FIRST, SECOND)))

        assertThat(texts).containsAtLeast(FIRST, SECOND, "Copy them", "Clear them").inOrder()
    }

    private fun eating(state: SettingsUiState): List<String> = render.texts(heightPx = TALL) {
        EatingSettingsPage(
            state = state,
            onSetWindow = { _, _ -> },
            onSetRatio = {},
            onClearWindow = {},
            onSetReminder = {},
            onSendReminderNow = {},
            onDismissFailure = {},
            onBack = {},
        )
    }

    private fun movement(state: SettingsUiState): List<String> = render.texts {
        MovementSettingsPage(state = state, onConnectSteps = {}, onOpenBandReport = {}, onBack = {})
    }

    private fun food(state: SettingsUiState): List<String> = render.texts {
        FoodDatabaseSettingsPage(
            state = state,
            onSaveOffAccount = { _, _ -> },
            onClearOffAccount = {},
            onDismissFailure = {},
            onBack = {},
        )
    }

    private fun problems(state: SettingsUiState): List<String> = render.texts {
        ProblemsSettingsPage(state = state, onCopyProblems = {}, onClearProblems = {}, onBack = {})
    }

    private companion object {
        /** Tall enough that a whole page is laid out and none of it left unplaced. */
        const val TALL = 20_000

        const val CONNECT = "Allow MetaSelf to read your health data"
        const val POINTER = "Detailed readings: see Backups"
        const val BRING_BACK = "Bring back detailed readings from Drive"

        /** Invented problem lines, in the log's shape. */
        const val FIRST = "03 Sep 10:00 · drive · an invented failure"
        const val SECOND = "03 Sep 11:00 · backup · another invented failure"

        const val NOTHING = "That didn't work, and nothing was changed. " +
            "What went wrong is under Settings → Recent problems."
        const val PARTIAL = "That didn't finish, and may have only partly happened. " +
            "What went wrong is under Settings → Recent problems."
    }
}
