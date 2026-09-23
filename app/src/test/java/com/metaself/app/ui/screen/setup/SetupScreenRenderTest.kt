package com.metaself.app.ui.screen.setup

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** JUnit 4 by necessity — Robolectric's runner is JUnit 4, and nothing else in this file is. */
@RunWith(RobolectricTestRunner::class)
class SetupScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `asks for every field the target needs`() {
        val texts = draw(SetupFormState(), showErrors = false)
        assertThat(texts).contains("Height in centimetres")
        assertThat(texts).contains("Year of birth")
        assertThat(texts).contains("Weight in kilograms")
        assertThat(texts).contains("A normal day")
        assertThat(texts).contains("What you want to happen")
    }

    @Test
    fun `says nothing is wrong before the owner has tried to save`() {
        val texts = draw(SetupFormState(), showErrors = false)
        assertThat(texts.none { it.startsWith("A height in centimetres, between") }).isTrue()
    }

    @Test
    fun `shows what is wrong once the owner has tried to save`() {
        val texts = draw(SetupFormState(), showErrors = true)
        assertThat(texts.any { it.startsWith("A height in centimetres, between") }).isTrue()
    }

    @Test
    fun `offers a rate only when there is weight to lose or gain`() {
        val holding = SetupFormState(direction = GoalDirection.HOLD)
        assertThat(draw(holding, showErrors = false)).doesNotContain("How fast")
    }

    /** A first save that threw leaves this form up; the sentence is above Save, where he pressed. */
    @Test
    fun `a save that threw says so above Save`() {
        val texts = draw(SetupFormState(), showErrors = false, failed = ActionRefused.NOTHING_CHANGED)
        val sentence = texts.indexOf(
            "That didn't work, and nothing was changed. " +
                "What went wrong is under Settings → Recent problems.",
        )

        assertThat(sentence).isAtLeast(0)
        assertThat(texts).contains("All right")
        assertThat(sentence).isLessThan(texts.indexOf("Work out my target"))
    }

    private fun draw(
        state: SetupFormState,
        showErrors: Boolean,
        failed: ActionRefused? = null,
    ): List<String> = render.texts {
        SetupScreen(
            state = state,
            currentYear = 2026,
            showErrors = showErrors,
            onChange = {},
            onSave = {},
            onCancel = null,
            failed = failed,
        )
    }
}
