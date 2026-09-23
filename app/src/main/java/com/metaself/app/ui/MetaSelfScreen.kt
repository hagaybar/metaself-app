package com.metaself.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.ui.theme.Spacing

/**
 * Every screen in this app, from the outside.
 *
 * A title bar with the screen's name, a back arrow where there is somewhere to go, and one
 * consistent padding and scroll. Before this, each screen was a bare scrolling column beginning with
 * a large word, and the way back was a text button at the BOTTOM — the one place nobody looks.
 *
 * [onTitleClick] exists for the day screen, whose title is the date and is how a day is chosen.
 *
 * [scrolls] exists because a screen that manages its own scrolling must not be wrapped in a second
 * scroller; nesting two shows up as a screen that will not quite move.
 *
 * [belowBar] is for chrome that belongs to the title bar rather than to the page: a tab strip, and
 * the search box that serves whichever tab is in front. It is drawn edge to edge, directly under
 * the bar, and OUTSIDE the padded scrolling column — a tab strip put in with the content is
 * indented from both edges, pushed down from the bar, and scrolls away with the list, which is none
 * of the things a tab strip does. A search box put in with the content scrolls away too, and a
 * search box you have to scroll back up to is one you stop using.
 *
 * [scrollKey] gives each list its own scroll position. Two tabs sharing one scroller share one
 * position, so switching from a food list scrolled halfway down landed on the meals already past
 * their top — his place lost in both lists at once. A list seen for the first time starts at its
 * top; a list come back to is where it was left. Pass whatever identifies "a different list".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetaSelfScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    scrolls: Boolean = true,
    /** What identifies the list being scrolled; each one keeps its own place. See the KDoc. */
    scrollKey: Any? = Unit,
    /** Full-bleed chrome drawn under the title bar, above and outside the padded content. */
    belowBar: @Composable () -> Unit = {},
    /** Icons at the end of the title bar: the places a screen can send you that are not "back". */
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    /** Leave room at the bottom for a floating button, which draws over the content, not beside it. */
    hasFloatingButton: Boolean = false,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        floatingActionButton = floatingActionButton,
        topBar = {
            TopAppBar(
                // A bar the same colour as the page behind it is a bar nobody sees. This was the
                // whole of the first attempt at this pass: structurally right, visually nothing.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                title = {
                    if (onTitleClick == null) {
                        Text(title)
                    } else {
                        androidx.compose.material3.TextButton(onClick = onTitleClick) {
                            Text(title)
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                // Named, so a screen reader has something to say and a test has
                                // something to look for. The auto-mirrored arrow flips for
                                // right-to-left, which matters: the app supports Hebrew, which is
                                // a right-to-left language (#9).
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
                actions = actions,
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            // Edge to edge and above the scroller, so a tab strip sits against the bar it belongs
            // to. Screens that pass nothing get an empty composable: no height, no change.
            belowBar()

            val base = Modifier
                .fillMaxWidth()
                .weight(1f)

            Column(
                modifier = (
                    if (scrolls) {
                        // rememberSaveable, exactly as rememberScrollState() does it, so turning
                        // the phone still keeps the place. Keyed, so another list starts at its
                        // top; and each key's scroller is kept, so coming back to a list finds it
                        // where it was left rather than at the top again. Only the list in front
                        // survives turning the phone — the others live in memory, not the Bundle.
                        val scrollers = remember { mutableMapOf<Any?, ScrollState>() }
                        base.verticalScroll(
                            rememberSaveable(scrollKey, saver = ScrollState.Saver) {
                                scrollers[scrollKey] ?: ScrollState(0)
                            }.also { scrollers[scrollKey] = it },
                        )
                    } else {
                        base
                    }
                    )
                    .padding(horizontal = Spacing.Screen)
                    // The first thing on the page used to sit flush against the title bar, which
                    // made the day's ring look cut off by it.
                    .padding(top = Spacing.Related)
                    // A floating button draws OVER the content, so room is left for it — but only
                    // when this column is the one that scrolls. A screen that scrolls itself
                    // ([scrolls] false) is handed the whole height and leaves that room INSIDE its
                    // own scroller, where it belongs: reserving it here as well took the room twice
                    // over, once as a dead band the day could not draw into and once as trailing
                    // space below the last row, and cost every day 88dp of usable height.
                    .padding(
                        bottom = if (hasFloatingButton && scrolls) {
                            FLOATING_BUTTON_ROOM
                        } else {
                            Spacing.Screen
                        },
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.Section),
            ) {
                content()
            }
        }
    }
}

/** Enough for a floating button and the gap Material puts around it. */
private val FLOATING_BUTTON_ROOM = 88.dp
