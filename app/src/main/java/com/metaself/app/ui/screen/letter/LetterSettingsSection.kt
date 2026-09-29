package com.metaself.app.ui.screen.letter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.metaself.app.data.letter.LetterSettings
import com.metaself.app.ui.letter.LetterWording
import com.metaself.app.ui.theme.Spacing

/**
 * The weekly letter's setting on the AI page (D99): the switch, the Sunday hour (only while on), and —
 * where the phone offers it and it is not held — the one ask for Health Connect's background read, with
 * its reason. Pure, so a render test can draw it; the destination supplies the permission screens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LetterSettingsSection(
    state: LetterSettingsViewModel.State,
    onSetOn: (Boolean) -> Unit,
    onSetHour: (Int) -> Unit,
    onAskBackground: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                Text(LetterWording.SETTING_TITLE, style = MaterialTheme.typography.titleMedium)
                Text(LetterWording.settingLine(state.on, state.hour), style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = state.on, onCheckedChange = onSetOn)
        }
        if (state.on) {
            Text(LetterWording.SETTING_HOUR, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                LetterSettings.HOURS.forEach { hour ->
                    FilterChip(selected = hour == state.hour, onClick = { onSetHour(hour) }, label = { Text(LetterWording.hour(hour)) })
                }
            }
            if (state.askBackground) {
                OutlinedButton(onClick = onAskBackground) { Text(LetterWording.ASK_BACKGROUND) }
                Text(LetterWording.BACKGROUND_REASON, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.refused?.let { Text(stringResource(it.sentence), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}
