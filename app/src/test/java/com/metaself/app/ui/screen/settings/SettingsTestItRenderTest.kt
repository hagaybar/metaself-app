package com.metaself.app.ui.screen.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * After Test it, the line saying what the saved model is sent (D57 §6) is drawn under the test's
 * own result, before the problems. Asserted by order in the drawn tree, which is what a render test
 * here can say about position. JUnit 4 because Robolectric's runner is.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsTestItRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `what the model is sent is said under the test's result`() {
        val texts = draw(
            SettingsUiState(
                hasKey = true,
                model = "gpt-6-luna",
                testResult = RESULT,
                testLearned = LEARNED,
            ),
        )

        assertThat(texts.count { it == LEARNED }).isEqualTo(1)
        assertThat(texts.indexOf(LEARNED)).isGreaterThan(texts.indexOf(RESULT))
        assertThat(texts.indexOf(LEARNED)).isLessThan(texts.indexOf("Recent problems"))
    }

    @Test
    fun `with nothing learned to say, nothing is drawn for it`() {
        val texts = draw(SettingsUiState(hasKey = true, model = "gpt-6-luna", testResult = RESULT))

        assertThat(texts).contains(RESULT)
        assertThat(texts.none { it.startsWith("gpt-6-luna works") }).isTrue()
    }

    private fun draw(state: SettingsUiState): List<String> = render.texts {
        SettingsScreen(
            state = state,
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
        const val RESULT = "Connected. It answered with 1 item and 52 kcal."
        const val LEARNED = "gpt-6-luna works: no temperature, medium thinking, strict format."
    }
}
