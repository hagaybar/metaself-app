package com.metaself.app.ui.screen.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4.
 *
 * D90's page: the note in its field, what it is for, how much room is left, and Save. Every word is invented.
 */
@RunWith(RobolectricTestRunner::class)
class AboutMeScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `the page shows the note, what it is for and the room left`() {
        val texts = draw(AboutMeViewModel.State(text = "Invented note.", loaded = true))

        assertThat(texts).contains("About me")
        assertThat(render.fieldTexts()).contains("Invented note.")
        assertThat(texts).contains("14 / 1,000")
        assertThat(texts.any { it.startsWith("What the trainer should always know") }).isTrue()
        assertThat(render.isEnabled("Save")).isTrue()
    }

    @Test
    fun `save calls back, and waits while the note is read or saved`() {
        var saved = false
        draw(AboutMeViewModel.State(text = "Invented note.", loaded = true), onSave = { saved = true })
        render.click("Save")
        assertThat(saved).isTrue()

        draw(AboutMeViewModel.State())
        assertThat(render.isEnabled("Save")).isFalse()
    }

    private fun draw(state: AboutMeViewModel.State, onSave: () -> Unit = {}): List<String> = render.texts {
        AboutMeScreen(state = state, onBack = {}, onEdit = {}, onSave = onSave, onSaved = {})
    }
}
