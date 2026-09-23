package com.metaself.app.ui.screen.setup

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.profile.GoalDirection
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

    private fun draw(state: SetupFormState, showErrors: Boolean): List<String> = render.texts {
        SetupScreen(
            state = state,
            currentYear = 2026,
            showErrors = showErrors,
            onChange = {},
            onSave = {},
            onCancel = null,
        )
    }
}
