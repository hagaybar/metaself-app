package com.metaself.app.ui.screen.settings

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
import com.metaself.app.ui.about.AboutWording
import com.metaself.app.ui.about.ChangeLog
import com.metaself.app.ui.about.ChangeLogEntry
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/**
 * About this app (D134): the installed version in the display face, its build under it, then what each
 * version changed, newest first — a heading and one sentence each. It holds no controls.
 */
@Composable
fun AboutSettingsPage(
    versionName: String,
    versionCode: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    entries: List<ChangeLogEntry> = ChangeLog.entries,
) {
    MetaSelfScreen(
        title = stringResource(SettingsPage.ABOUT.title),
        modifier = modifier,
        onBack = onBack,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(text = AboutWording.title(versionName), style = MaterialTheme.typography.headlineLarge)
            Text(
                text = AboutWording.build(versionCode),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
        }

        Text(
            text = stringResource(R.string.about_what_changed),
            style = MaterialTheme.typography.labelSmall,
            color = MetaSelfInk.two,
        )

        Column {
            entries.forEachIndexed { i, entry ->
                if (i > 0) HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.Related),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
                ) {
                    Text(
                        text = AboutWording.heading(entry),
                        style = MaterialTheme.typography.bodySmall,
                        color = MetaSelfInk.two,
                    )
                    Text(text = entry.sentence, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
