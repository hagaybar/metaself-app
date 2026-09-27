package com.metaself.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.metaself.app.ui.screen.settings.SettingsPage

/**
 * Settings as a nested graph (D79): the index at [Destination.Settings.index], and one destination
 * per [SettingsPage].
 *
 * [content] is handed the page (null for the index), the entry being drawn, and the graph's own
 * entry. The graph's entry is on the back stack for as long as any Settings destination is, and is
 * gone as soon as none is — so the one `SettingsViewModel` every destination takes from it lives
 * exactly as long as a visit to Settings, as it did when Settings was a single destination. Hoisted
 * to the activity instead, a restore question or a Test it result would still be there on the next
 * visit.
 */
fun NavGraphBuilder.settingsGraph(
    nav: NavController,
    content: @Composable (page: SettingsPage?, here: NavBackStackEntry, graph: NavBackStackEntry) -> Unit,
) {
    navigation(startDestination = Destination.Settings.index, route = Destination.Settings.route) {
        composable(Destination.Settings.index) { here ->
            content(null, here, graphOf(nav, here))
        }
        SettingsPage.entries.forEach { page ->
            composable(Destination.Settings.page(page)) { here ->
                content(page, here, graphOf(nav, here))
            }
        }
    }
}

@Composable
private fun graphOf(nav: NavController, here: NavBackStackEntry): NavBackStackEntry =
    remember(here) { nav.getBackStackEntry(Destination.Settings.route) }

/**
 * Opens [page] from the index, only while the index is the screen in front: a second quick tap on
 * the way out does nothing, rather than stacking the page twice (see [ifResumed]).
 */
fun NavController.openSettingsPage(here: NavBackStackEntry, page: SettingsPage) {
    here.ifResumed { navigate(Destination.Settings.page(page)) }
}
