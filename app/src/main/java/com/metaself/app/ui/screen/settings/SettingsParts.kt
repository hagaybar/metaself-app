package com.metaself.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.ui.theme.Spacing

/*
 * The pieces more than one Settings page draws, or that a page draws beside its own code: the
 * sentence for an action that threw, the hour picker, and the question about Drive's months.
 */

/**
 * The sentence for an action that threw, if it was started from [part]: drawn under the part it
 * belongs to, because on a page this long a line anywhere else could be off screen.
 */
@Composable
internal fun RefusedHere(failed: SettingsRefusal?, part: SettingsPart, onDismiss: () -> Unit) {
    if (failed == null || failed.part != part) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(
            text = stringResource(failed.refused.sentence),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(
            onClick = onDismiss,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        ) { Text(stringResource(R.string.action_refused_dismiss)) }
    }
}

/**
 * An hour of the day, by the hour.
 *
 * Whole hours only. A window is a decision about roughly when to stop eating, not an appointment,
 * and offering minutes would invite a precision nobody actually keeps to.
 */
@Composable
internal fun HourPicker(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { onChange((hour + 23) % 24) }) { Text("−") }
        Text(
            text = String.format(java.util.Locale.US, "%02d:00", hour),
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = { onChange((hour + 1) % 24) }) { Text("+") }
    }
}

internal const val DEFAULT_START = 8
internal const val DEFAULT_END = 20

/** Sixteen hours fasting, a common ratio and the default here. */
internal const val DEFAULT_FASTING = 16

/** Fasting hours, written fasting-first as 12/12, 14/10, 16/8, 18/6 and 20/4. */
internal val RATIOS = listOf(12, 14, 16, 18, 20)

/**
 * Whether to bring back the months of detailed readings in Drive (D71). Nothing on the phone is
 * removed or replaced by accepting, so no warning.
 */
@Composable
internal fun ArchiveQuestion(question: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Related),
            verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                Button(onClick = onConfirm) {
                    Text(stringResource(R.string.settings_archive_bring))
                }
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.settings_archive_skip))
                }
            }
        }
    }
}
