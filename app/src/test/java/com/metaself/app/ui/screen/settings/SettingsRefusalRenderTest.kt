package com.metaself.app.ui.screen.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The sentence for an action that threw is drawn under the part of the page it came from, once.
 *
 * The page is long enough that a line at the top would be off screen for someone pressing Restore
 * near the middle, so where it lands is the point; asserted by order in the drawn tree, which is
 * what a render test here can say about position. JUnit 4 because Robolectric's runner is.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRefusalRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `a restore that threw is said under the backup, before the reminder`() {
        val texts = draw(SettingsRefusal(SettingsPart.BACKUP, ActionRefused.MAYBE_PARTIAL))
        val sentence = texts.indexOf(PARTIAL)

        assertThat(texts.count { it == PARTIAL }).isEqualTo(1)
        assertThat(sentence).isGreaterThan(texts.indexOf("Keeping a copy"))
        assertThat(sentence).isLessThan(texts.indexOf("Daily reminder"))
        assertThat(texts).contains("All right")
    }

    @Test
    fun `a key that threw is said under the key, before the model`() {
        val texts = draw(SettingsRefusal(SettingsPart.KEY, ActionRefused.NOTHING_CHANGED))
        val sentence = texts.indexOf(NOTHING)

        assertThat(sentence).isGreaterThan(texts.indexOf("Your OpenAI API key"))
        assertThat(sentence).isLessThan(texts.indexOf("Model"))
    }

    @Test
    fun `with nothing refused, nothing is said`() {
        val texts = draw(null)

        assertThat(texts).doesNotContain(NOTHING)
        assertThat(texts).doesNotContain(PARTIAL)
        assertThat(texts).doesNotContain("All right")
    }

    private fun draw(failed: SettingsRefusal?): List<String> = render.texts {
        SettingsScreen(
            state = SettingsUiState(failed = failed),
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
            onDismissFailure = {},
            onBack = {},
        )
    }

    private companion object {
        const val NOTHING = "That didn't work, and nothing was changed. " +
            "What went wrong is under Settings → Recent problems."
        const val PARTIAL = "That didn't finish, and may have only partly happened. " +
            "What went wrong is under Settings → Recent problems."
    }
}
