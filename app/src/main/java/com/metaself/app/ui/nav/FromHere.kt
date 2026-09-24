package com.metaself.app.ui.nav

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

/*
 * A second tap that lands before the first one's navigation has drawn still reaches the screen it
 * was made on, because that screen is still on the glass. Navigating from it again stacks a second
 * copy of the page, and a second pop takes the screen beneath with it. These two let a screen act
 * only while it is the one in front.
 */

/**
 * Runs [go] only while this entry is resumed: the screen in front, with no navigation away from it
 * under way. Navigation lowers the entry below resumed as soon as it navigates, so a second tap on
 * the way out does nothing. Lifecycle 2.8's `dropUnlessResumed` is this check; the app is on 2.7.
 */
fun NavBackStackEntry.ifResumed(go: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) go()
}

/** Pops [here], and only [here]: once it has gone, a second press pops nothing beneath it. */
fun NavController.popFrom(here: NavBackStackEntry) {
    if (currentBackStackEntry == here) popBackStack()
}
