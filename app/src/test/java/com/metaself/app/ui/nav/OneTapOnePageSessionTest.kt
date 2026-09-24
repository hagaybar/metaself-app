package com.metaself.app.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Two quick taps on a food open its page once, and two quick presses of the page's back arrow
 * leave it once (D55 §1).
 *
 * Both taps land before the first one's navigation has drawn: the frame clock is held still
 * between them, so the list is still on screen and still takes the second tap — what a fast
 * double tap on a phone does. The graph is a real `NavHost` — Navigation's own back stack —
 * wired the way `MetaSelfNavHost` wires the list and the page, which cannot be drawn without
 * Hilt. The unguarded wiring is drawn too, so the rig is shown able to see the fault.
 *
 * JUnit 4, because Compose's rule demands it: `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class OneTapOnePageSessionTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController

    @Test
    fun `without the guard, the rig stacks two pages from two quick taps`() {
        draw(guarded = false)

        twice("Open the food")

        assertThat(routes()).containsExactly("day", "list", "page", "page").inOrder()
    }

    @Test
    fun `two quick taps on a food open its page once`() {
        draw(guarded = true)

        twice("Open the food")

        assertThat(routes()).containsExactly("day", "list", "page").inOrder()
    }

    @Test
    fun `two quick presses of the page's back arrow leave it once`() {
        draw(guarded = true)
        compose.onNodeWithText("Open the food").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        twice("Back")

        assertThat(routes()).containsExactly("day", "list").inOrder()
    }

    private fun draw(guarded: Boolean) {
        compose.setContent { Graph(guarded) }
        compose.onNodeWithText("Open the list").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** Presses [label] twice with no frame drawn between, then lets everything settle. */
    private fun twice(label: String) {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    private fun routes(): List<String?> =
        nav.currentBackStack.value.mapNotNull { it.destination.route }

    @Composable
    private fun Graph(guarded: Boolean) {
        nav = rememberNavController()
        NavHost(navController = nav, startDestination = "day") {
            composable("day") {
                TextButton(onClick = { nav.navigate("list") }) { Text("Open the list") }
            }
            composable("list") { here ->
                TextButton(
                    onClick = {
                        if (guarded) here.ifResumed { nav.navigate("page") } else nav.navigate("page")
                    },
                ) { Text("Open the food") }
            }
            composable("page") { here ->
                Column {
                    Text("The food's page")
                    TextButton(onClick = { nav.popFrom(here) }) { Text("Back") }
                }
            }
        }
    }
}
