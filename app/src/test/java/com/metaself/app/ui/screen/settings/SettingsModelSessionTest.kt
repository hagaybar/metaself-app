package com.metaself.app.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.ComposeSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The model's name is stored only by **Save the model** — never keystroke by keystroke, which read
 * a cleared box back as the default and snapped it back under his thumb. Pressed through against a
 * stand-in store that does what the real one does: a name is stored trimmed, and a blank one reads
 * back as the default. Model names invented where they are not the app's default.
 *
 * JUnit 4, because Compose's rule demands it: `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class SettingsModelSessionTest {

    @get:Rule
    val compose = createComposeRule()

    private val session by lazy { ComposeSession(compose) }

    /** What the stand-in store holds, and what it held each time Test it was pressed. */
    private var stored = DEFAULT
    private val tested = mutableListOf<String>()

    @Test
    fun `clearing the box to type a new name does not snap back to the default`() {
        session.start { Settings() }

        session.type(DEFAULT, "")
        session.type("Model name", "gpt-6")

        assertThat(compose.onNodeWithText("gpt-6").fetchSemanticsNode()).isNotNull()
        assertThat(stored).isEqualTo(DEFAULT)
    }

    @Test
    fun `Save the model stores the name, trimmed, and says so`() {
        session.start { Settings() }
        session.type(DEFAULT, " gpt-6-luna ")

        val after = session.press("Save the model")

        assertThat(stored).isEqualTo("gpt-6-luna")
        assertThat(after).contains("Saved: gpt-6-luna")
        assertThat(after).contains("gpt-6-luna")
    }

    /** Opened afresh — the screen recreated — the box holds the saved name. */
    @Test
    fun `opened again, the box holds the name that was saved`() {
        stored = "gpt-6-luna"

        val screen = session.start { Settings() }

        assertThat(screen).contains("gpt-6-luna")
        assertThat(screen).doesNotContain(DEFAULT)
    }

    @Test
    fun `a blank name cannot be saved, and the default is one press away`() {
        stored = "gpt-6-luna"
        session.start { Settings() }

        val cleared = session.type("gpt-6-luna", "")

        compose.onNodeWithText("Save the model").assertIsNotEnabled()
        assertThat(cleared).contains("Use the default ($DEFAULT)")
        session.press("Use the default ($DEFAULT)")
        assertThat(stored).isEqualTo(DEFAULT)
    }

    @Test
    fun `the same name as the one saved has nothing to save`() {
        session.start { Settings() }

        compose.onNodeWithText("Save the model").assertIsNotEnabled()
        session.type(DEFAULT, "gpt-5-mini")
        compose.onNodeWithText("Save the model").assertIsEnabled()
    }

    @Test
    fun `Test it asks the saved model, and says so while another name waits unsaved`() {
        session.start { Settings() }
        val typed = session.type(DEFAULT, "gpt-6-luna")

        assertThat(typed).contains("Not saved yet — Test it uses $DEFAULT")
        session.press("Test it")
        assertThat(tested).containsExactly(DEFAULT)

        val saved = session.press("Save the model")
        assertThat(saved).doesNotContain("Not saved yet — Test it uses $DEFAULT")
        session.press("Test it")
        assertThat(tested).containsExactly(DEFAULT, "gpt-6-luna").inOrder()
    }

    @Composable
    private fun Settings() {
        var model by remember { mutableStateOf(stored) }
        SettingsScreen(
            state = SettingsUiState(hasKey = true, model = model),
            onSaveKey = {},
            onClearKey = {},
            onSetModel = {
                stored = it.trim().ifEmpty { DEFAULT }
                model = stored
            },
            onSetCeiling = {},
            onTest = { tested += stored },
            onSetReminder = {},
            onSendReminderNow = {},
            onSaveOffAccount = { _, _ -> },
            onClearOffAccount = {},
            onSetWindow = { _, _ -> },
            onSetRatio = {},
            onClearWindow = {},
            onConnectSteps = {},
            onPickBackupFolder = {},
            onForgetBackupFolder = {},
            onBackUpNow = {},
            onSetDrive = {},
            onDriveNow = {},
            onExport = {},
            onRestore = {},
            onConfirmRestore = {},
            onCancelRestore = {},
            onDismissBackupMessage = {},
            onCopyProblems = {},
            onClearProblems = {},
            onDismissFailure = {},
            onBack = {},
        )
    }

    private companion object {
        /** `EstimatePrompt.DEFAULT_MODEL`: what a blank name reads back as. */
        const val DEFAULT = "gpt-4o-mini"
    }
}
