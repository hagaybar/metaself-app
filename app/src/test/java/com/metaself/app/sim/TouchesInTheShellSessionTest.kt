package com.metaself.app.sim

import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.ComposeSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The walk touches the app as a finger does (public issue #6, item 1), shown on the day's real
 * More menu — the menu behind the false finding that it "will not close".
 *
 * JUnit 4, because Compose's rule demands it: `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class TouchesInTheShellSessionTest {

    @get:Rule
    val compose = createComposeRule()

    private val session by lazy { ComposeSession(compose) }

    @Test
    fun `with the day's menu open, only its items can be touched`() {
        session.start { SimulatedApp(World()) }

        session.press("More")

        assertThat(session.actions().map { it.label })
            .containsExactly("Weight", "Your numbers", "Foods & meals", "Settings")
    }

    @Test
    fun `a press beneath the day's open menu closes the menu and opens nothing`() {
        session.start { SimulatedApp(World()) }
        session.press("More")

        val failure = runCatching { session.press("Add something") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(ComposeSession.LandedOutside::class.java)
        val now = session.screen()
        assertThat(now).doesNotContain("Foods & meals")
        assertThat(now).doesNotContain("Search your foods")
        assertThat(session.actions().map { it.label }).contains("Add something")
    }
}
