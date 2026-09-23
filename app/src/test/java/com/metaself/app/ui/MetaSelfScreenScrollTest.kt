package com.metaself.app.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.theme.MetaSelfTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Two tabs over one frame: each keeps its own place.
 *
 * Only the scroll position is asserted, read from the scroller's own semantics, never a size — see
 * CLAUDE.md on what a Robolectric render can and cannot measure here.
 *
 * JUnit 4 because Compose's rule demands it. `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class MetaSelfScreenScrollTest {

    @get:Rule
    val compose = createComposeRule()

    private var tab by mutableStateOf("first")

    private val scroller = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private fun position(): Float = compose.onNode(scroller).fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun start() {
        compose.setContent {
            MetaSelfTheme {
                MetaSelfScreen(title = "Tabs", scrollKey = tab) {
                    repeat(40) { row ->
                        Text("$tab $row")
                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the other tab opens at its top, not where the first was left`() {
        start()
        compose.onNode(scroller).performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertThat(position()).isGreaterThan(0f)

        tab = "second"
        compose.waitForIdle()

        assertThat(position()).isEqualTo(0f)
    }

    @Test
    fun `coming back to a tab finds it where it was left`() {
        start()
        compose.onNode(scroller).performTouchInput { swipeUp() }
        compose.waitForIdle()
        val left = position()
        assertThat(left).isGreaterThan(0f)

        tab = "second"
        compose.waitForIdle()
        tab = "first"
        compose.waitForIdle()

        assertThat(position()).isEqualTo(left)
    }
}
