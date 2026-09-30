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
 * Settings: a short list of seven rows, each its title and one line saying the current state, each
 * opening its own page (D79). The controls are all on the pages; this screen holds none.
 *
 * One row is one tap target, title and status together, so a screen reader reads a row as one
 * thing and a tap anywhere on it opens the page.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
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
            SettingsPage.entries.forEachIndexed { i, page ->
                if (i > 0) HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(page) }
                        .padding(vertical = Spacing.Related),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
                ) {
                    Text(
                        text = stringResource(page.title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // Null until the state behind this row's line has actually been read
                    // (SettingsIndexWording.statusOf) — drawn as an empty line rather than left out,
                    // so the row keeps the same height it will have once there is something to say
                    // instead of growing under him a moment later.
                    Text(
                        text = SettingsIndexWording.statusOf(page, state).orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MetaSelfInk.two,
                    )
                }
            }
        }
    }
}
