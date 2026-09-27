package com.metaself.app.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.screen.settings.SettingsPage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Settings as a nested graph (D79): the menu's route opens the index, a row opens its page, back
 * returns to the index, a page opened directly returns to where he was, and every destination of one
 * visit shares one view model while a new visit gets a new one.
 *
 * The Settings part of the graph is the production [settingsGraph]; only the page content is a
 * stand-in, because the real pages need Hilt. The view model is a plain probe fetched from the graph
 * entry exactly as the host fetches `SettingsViewModel` from it.
 *
 * JUnit 4, because Compose's rule demands it: `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsGraphSessionTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController

    /** Every probe a Settings destination was handed, in the order they were drawn. */
    private val probes = mutableListOf<Probe>()

    class Probe : ViewModel()

    @Test
    fun `the menu's route opens the index`() {
        draw()

        press("Open settings")

        assertThat(routes()).containsExactly("day", "settings", "settings/index").inOrder()
        compose.onNodeWithText("INDEX").assertExists()
    }

    @Test
    fun `a row opens its page and back returns to the index`() {
        draw()
        press("Open settings")

        SettingsPage.entries.forEach { page ->
            press("Open ${page.name}")
            assertThat(routes().last()).isEqualTo("settings/${page.path}")
            compose.onNodeWithText("PAGE ${page.name}").assertExists()

            press("Back")
            assertThat(routes().last()).isEqualTo("settings/index")
        }
    }

    /** The describe screen's way to the key: the index is never put under the page. */
    @Test
    fun `a page opened directly, back goes straight back to where he was`() {
        draw()

        press("Open the AI page")
        assertThat(routes()).containsExactly("day", "settings", "settings/ai").inOrder()

        press("Back")
        assertThat(routes()).containsExactly("day")
    }

    @Test
    fun `the index and a page share one view model`() {
        draw()
        press("Open settings")
        press("Open BACKUPS")

        assertThat(probes.toSet()).hasSize(1)
    }

    /** As when Settings was one destination: what a visit left behind does not greet the next. */
    @Test
    fun `leaving Settings and coming back is a fresh view model`() {
        draw()
        press("Open settings")
        val first = probes.last()

        press("Back")
        press("Open settings")

        assertThat(probes.last()).isNotSameInstanceAs(first)
    }

    @Test
    fun `two quick taps on a row open its page once`() {
        draw()
        press("Open settings")

        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Open EATING").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Open EATING").performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertThat(routes())
            .containsExactly("day", "settings", "settings/index", "settings/eating").inOrder()
    }

    /** D80: the Movement page's row opens the band report; back returns to the Movement page. */
    @Test
    fun `the band report opens from the Movement page and shares the visit's view model`() {
        draw()
        press("Open settings")
        press("Open MOVEMENT")

        press("Open the band report")
        assertThat(routes().last()).isEqualTo("settings/movement/band")
        compose.onNodeWithText("BAND REPORT").assertExists()
        assertThat(probes.toSet()).hasSize(1)

        press("Back")
        assertThat(routes().last()).isEqualTo("settings/movement")
    }

    private fun draw() {
        compose.setContent { Graph() }
        compose.waitForIdle()
    }

    private fun press(label: String) {
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun routes(): List<String?> =
        nav.currentBackStack.value.mapNotNull { it.destination.route }

    @Composable
    private fun Graph() {
        nav = rememberNavController()
        NavHost(navController = nav, startDestination = "day") {
            composable("day") {
                Column {
                    TextButton(onClick = { nav.navigate(Destination.Settings.route) }) {
                        Text("Open settings")
                    }
                    TextButton(onClick = { nav.navigate(Destination.Settings.page(SettingsPage.AI)) }) {
                        Text("Open the AI page")
                    }
                }
            }
            settingsGraph(
                nav,
                content = { page, here, graph ->
                    probes += viewModel<Probe>(viewModelStoreOwner = graph)
                    Column {
                        Text(if (page == null) "INDEX" else "PAGE ${page.name}")
                        if (page == null) {
                            SettingsPage.entries.forEach { row ->
                                TextButton(onClick = { nav.openSettingsPage(here, row) }) {
                                    Text("Open ${row.name}")
                                }
                            }
                        }
                        if (page == SettingsPage.MOVEMENT) {
                            TextButton(onClick = { nav.openBandReport(here) }) { Text("Open the band report") }
                        }
                        TextButton(onClick = { nav.popFrom(here) }) { Text("Back") }
                    }
                },
                bandReport = { here, graph ->
                    probes += viewModel<Probe>(viewModelStoreOwner = graph)
                    Column {
                        Text("BAND REPORT")
                        TextButton(onClick = { nav.popFrom(here) }) { Text("Back") }
                    }
                },
            )
        }
    }
}
