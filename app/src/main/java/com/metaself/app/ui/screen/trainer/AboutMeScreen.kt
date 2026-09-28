package com.metaself.app.ui.screen.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.metaself.app.R
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.TrainerWording

/**
 * D90: About me — one field, what it is for, the room left, and Save, which goes back once the note is
 * stored. Branches only — no early return out of an inline composable (InlineComposableReturnGuardTest).
 */
@Composable
fun AboutMeScreen(
    state: AboutMeViewModel.State,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onSave: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.saved) {
        LaunchedEffect(Unit) { onSaved() }
    }
    MetaSelfScreen(title = stringResource(R.string.about_me_title), modifier = modifier, onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Text(
                stringResource(R.string.about_me_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.text,
                onValueChange = onEdit,
                enabled = state.loaded && !state.saving,
                label = { Text(stringResource(R.string.about_me_field)) },
                minLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                TrainerWording.aboutMeCount(state.text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.saving) R.string.about_me_saving else R.string.about_me_save))
            }
            state.refused?.let { refused ->
                Text(stringResource(refused.sentence), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
