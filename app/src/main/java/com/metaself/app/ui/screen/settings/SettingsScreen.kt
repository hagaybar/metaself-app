package com.metaself.app.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.metaself.app.R
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.settings.SettingsIndexWording
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/**
 * Settings: a short list of rows, each its title and one line saying the current state, each opening
 * its own page (D79) — the last, About this app (D134). The controls are all on the pages; this screen
 * holds none.
 *
 * The first row, Your numbers (D133), is not one of [SettingsPage]: it is the root's cover, opened through
 * [onOpenYourNumbers]; closing it lands back here, since the root keeps the app's navigation while it is up.
 *
 * One row is one tap target, title and status together, so a screen reader reads a row as one
 * thing and a tap anywhere on it opens the page.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    /** Your numbers' status line, from the root's profile and target (D133). */
    yourNumbers: String,
    /** The installed version, for About this app's line (D134). */
    versionName: String,
    onOpenYourNumbers: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(
        title = stringResource(R.string.settings_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        Column {
            IndexRow(
                title = stringResource(R.string.your_numbers),
                status = yourNumbers,
                onClick = onOpenYourNumbers,
            )
            SettingsPage.entries.forEach { page ->
                HorizontalDivider()
                // Null until the state behind this row's line has actually been read
                // (SettingsIndexWording.statusOf) — drawn as an empty line rather than left out,
                // so the row keeps the same height it will have once there is something to say
                // instead of growing under him a moment later.
                IndexRow(
                    title = stringResource(page.title),
                    status = SettingsIndexWording.statusOf(page, state, versionName).orEmpty(),
                    onClick = { onOpen(page) },
                )
            }
        }
    }
}

/** One row: its title and its status line, one tap target. */
@Composable
private fun IndexRow(title: String, status: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.Related),
        verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = status, style = MaterialTheme.typography.bodyMedium, color = MetaSelfInk.two)
    }
}
