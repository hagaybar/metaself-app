package com.metaself.app.ui.screen.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.about.AboutWording
import com.metaself.app.ui.about.ChangeLog
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * About this app (D134): the installed version and build, then what each version changed, newest
 * first, each heading followed by its sentence. Version name and build number invented.
 *
 * JUnit 4 because Robolectric's runner is.
 */
@RunWith(RobolectricTestRunner::class)
class AboutSettingsPageRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `the version and build come first, then what changed`() {
        val texts = draw()

        // The title bar is drawn after the page in the tree, so it is checked apart from the order.
        assertThat(texts).contains("About this app")
        assertThat(texts).containsAtLeast("MetaSelf 0.1.0", "Build 2 · installed version", "WHAT CHANGED", AboutWording.heading(ChangeLog.entries.first()))
            .inOrder()
    }

    @Test
    fun `every entry, newest first, each heading straight before its sentence`() {
        val texts = draw()

        val lines = ChangeLog.entries.flatMap { listOf(AboutWording.heading(it), it.sentence) }
        assertThat(texts).containsAtLeastElementsIn(lines).inOrder()
        ChangeLog.entries.forEach {
            assertThat(texts.indexOf(it.sentence)).isEqualTo(texts.indexOf(AboutWording.heading(it)) + 1)
        }
    }

    @Test
    fun `back leaves the page`() {
        var back = 0
        render.texts { AboutSettingsPage(versionName = "0.1.0", versionCode = 2, onBack = { back++ }) }

        render.clickDescribed("Back")

        assertThat(back).isEqualTo(1)
    }

    private fun draw(): List<String> = render.texts {
        AboutSettingsPage(versionName = "0.1.0", versionCode = 2, onBack = {})
    }
}
