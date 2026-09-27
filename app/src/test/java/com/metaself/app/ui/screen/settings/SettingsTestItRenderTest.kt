package com.metaself.app.ui.screen.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * After Test it, the line saying what the saved model is sent (D57 §6) is drawn under the test's
 * own result, on the AI estimates page (D79), and not on the Recent problems page. Asserted by order in the drawn tree, which is what a render test
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
        // The problems once came straight after, on the same page; they are now a page of their
        // own (D79), so the bound below is kept as "not drawn there".
        assertThat(problemsPage(SettingsUiState(testResult = RESULT, testLearned = LEARNED)))
            .doesNotContain(LEARNED)
    }

    @Test
    fun `with nothing learned to say, nothing is drawn for it`() {
        val texts = draw(SettingsUiState(hasKey = true, model = "gpt-6-luna", testResult = RESULT))

        assertThat(texts).contains(RESULT)
        assertThat(texts.none { it.startsWith("gpt-6-luna works") }).isTrue()
    }

    private fun draw(state: SettingsUiState): List<String> = render.texts {
        AiSettingsPage(
            state = state,
            onSaveKey = {},
            onClearKey = {},
            onSetModel = {},
            onSetCeiling = {},
            onTest = {},
            onDismissFailure = {},
            onBack = {},
        )
    }

    private fun problemsPage(state: SettingsUiState): List<String> = render.texts {
        ProblemsSettingsPage(state = state, onCopyProblems = {}, onClearProblems = {}, onBack = {})
    }

    private companion object {
        const val RESULT = "Connected. It answered with 1 item and 52 kcal."
        const val LEARNED = "gpt-6-luna works: no temperature, medium thinking, strict format."
    }
}
