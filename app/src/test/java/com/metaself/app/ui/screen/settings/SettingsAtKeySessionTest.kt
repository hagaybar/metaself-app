package com.metaself.app.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.metaself.app.ui.ComposeSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings opened from "Add a key in settings" opens where the key goes (issue #11).
 *
 * The key is several sections down a long page, so settings opened at the top would be a way there
 * that still leaves him to find it. Asked of Compose's own finders, whose `assertIsDisplayed` clips a
 * node by the scrolling column it sits in; [ComposeSession] alone reads every node whether scrolled
 * into view or not. The guard proves the section starts off screen when nothing asks for it, so a
 * page short enough to show everything cannot pass the first test by accident. Existence is asserted
 * before visibility, for the reason `FoodsAskFirstSessionTest` gives. Pinned to a phone's size so the
 * scrolling does not depend on Robolectric's default.
 *
 * JUnit 4, because Compose's rule demands it: `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class SettingsAtKeySessionTest {

    @get:Rule
    val compose = createComposeRule()

    private val session by lazy { ComposeSession(compose) }

    @Test
    fun `opened for the key, the whole key section is on screen`() {
        session.start { Settings(openAtKey = true) }

        compose.onNodeWithText(KEY_TITLE).assertExists().assertIsDisplayed()
        compose.onNodeWithText(SAVE_KEY).assertExists().assertIsDisplayed()
    }

    @Test
    fun `opened from the menu, it opens at the top and the key is further down`() {
        session.start { Settings(openAtKey = false) }

        compose.onNodeWithText(KEY_TITLE).assertExists().assertIsNotDisplayed()
    }

    @Composable
    private fun Settings(openAtKey: Boolean) {
        SettingsScreen(
            state = SettingsUiState(),
            onSaveKey = {},
            onClearKey = {},
            onSetModel = {},
            onSetCeiling = {},
            onTest = {},
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
            onBack = {},
            openAtKey = openAtKey,
        )
    }

    private companion object {
        /** `R.string.settings_key_title`, as the phone draws it. */
        const val KEY_TITLE = "Your OpenAI API key"

        /** `R.string.settings_key_save`, as the phone draws it. */
        const val SAVE_KEY = "Save the key"
    }
}
