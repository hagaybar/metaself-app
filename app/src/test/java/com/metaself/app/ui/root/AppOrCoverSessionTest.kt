package com.metaself.app.ui.root

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.ComposeSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The profile editor, opened from a screen deep in the app, closes back onto that screen (public
 * issue #11).
 *
 * The root draws the editor INSTEAD of the app, not over it, so the app's navigation leaves the
 * composition while the editor is up. Whether its back stack comes back afterwards is the whole
 * question: the weight screen's "Change your goal" is a way out that must also be a way back, or it
 * is a trip to the day. The app under the cover here is a real `NavHost` — Navigation's own back
 * stack, saved and restored by Navigation's own saver — standing in for `MetaSelfNavHost`, which
 * cannot be drawn without Hilt.
 *
 * Your numbers is the same cover, opened from Settings' first row (D133); closing it is the same way back,
 * to the Settings index.
 *
 * JUnit 4, because Compose's rule demands it: `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class AppOrCoverSessionTest {

    @get:Rule
    val compose = createComposeRule()

    private val session by lazy { ComposeSession(compose) }

    @Test
    fun `closing the cover goes back to the screen it was opened from`() {
        session.start { Root() }

        session.press("Open weight")
        val covered = session.press("Change your goal")
        assertThat(covered).contains("The cover")
        assertThat(covered).doesNotContain("The weight screen")

        val back = session.press("Done")

        assertThat(back).contains("The weight screen")
    }

    @Test
    fun `and Back from there still goes to the day`() {
        session.start { Root() }

        session.press("Open weight")
        session.press("Change your goal")
        session.press("Done")

        assertThat(session.press("Back to the day")).contains("The day")
    }

    @Test
    fun `Your numbers, opened from Settings, closes back onto Settings`() {
        session.start { Root() }

        session.press("Open settings")
        val covered = session.press("Your numbers")
        assertThat(covered).contains("The cover")
        assertThat(covered).doesNotContain("The Settings index")

        assertThat(session.press("Done")).contains("The Settings index")
        assertThat(session.press("Back to the day")).contains("The day")
    }

    @Composable
    private fun Root() {
        var covered by remember { mutableStateOf(false) }
        AppOrCover(
            cover = if (covered) {
                {
                    Column {
                        Text("The cover")
                        TextButton(onClick = { covered = false }) { Text("Done") }
                    }
                }
            } else {
                null
            },
        ) {
            val nav = rememberNavController()
            NavHost(navController = nav, startDestination = "day") {
                composable("day") {
                    Column {
                        Text("The day")
                        TextButton(onClick = { nav.navigate("weight") }) { Text("Open weight") }
                        TextButton(onClick = { nav.navigate("settings") }) { Text("Open settings") }
                    }
                }
                composable("settings") {
                    Column {
                        Text("The Settings index")
                        TextButton(onClick = { covered = true }) { Text("Your numbers") }
                        TextButton(onClick = { nav.popBackStack() }) { Text("Back to the day") }
                    }
                }
                composable("weight") {
                    Column {
                        Text("The weight screen")
                        TextButton(onClick = { covered = true }) { Text("Change your goal") }
                        TextButton(onClick = { nav.popBackStack() }) { Text("Back to the day") }
                    }
                }
            }
        }
    }
}
