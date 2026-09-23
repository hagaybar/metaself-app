package com.metaself.app.ui

import androidx.compose.material3.Text
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** JUnit 4 by necessity — Robolectric's runner is JUnit 4. */
@RunWith(RobolectricTestRunner::class)
class MetaSelfScreenTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `a screen shows its title where a title goes`() {
        assertThat(render.texts { MetaSelfScreen(title = "Today") { Text("content") } })
            .contains("Today")
    }

    @Test
    fun `its content is drawn`() {
        assertThat(render.texts { MetaSelfScreen(title = "Today") { Text("content") } })
            .contains("content")
    }

    @Test
    fun `a screen with somewhere to go back to says so, for a screen reader as well`() {
        val texts = render.texts {
            MetaSelfScreen(title = "Weight", onBack = {}) { Text("content") }
        }

        // The arrow is an icon; what a screen reader and a test can both see is its description.
        assertThat(texts).contains("Back")
    }

    @Test
    fun `a screen with nowhere to go back to shows no arrow`() {
        val texts = render.texts { MetaSelfScreen(title = "Today") { Text("content") } }

        assertThat(texts).doesNotContain("Back")
    }
}
