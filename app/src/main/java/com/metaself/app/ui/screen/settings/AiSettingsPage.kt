package com.metaself.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.data.ai.EstimatePrompt
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/**
 * Settings' AI estimates page (D79): the key, the model, the daily ceiling, and the one button that
 * proves the chain works. Moved here unchanged from the single Settings scroll.
 *
 * A saved key is never shown back — not even masked-with-a-reveal. There is nothing the owner can do
 * with seeing it that he cannot do by pasting a new one, and a credential on screen is a credential
 * in a screenshot.
 */
@Composable
fun AiSettingsPage(
    state: SettingsUiState,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
    onSetModel: (String) -> Unit,
    onSetCeiling: (Int) -> Unit,
    onTest: () -> Unit,
    /** Take down the sentence of an action that threw. Required, so it cannot be left unwired. */
    onDismissFailure: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var typedKey by remember { mutableStateOf("") }
    // The model's box is his while he edits it: it follows the stored name only until he types,
    // and again once he saves. Keyed to the stored name, a box he had just cleared read back as
    // the default and snapped to it under his thumb.
    var modelEdited by rememberSaveable { mutableStateOf(false) }
    var typedModelText by rememberSaveable { mutableStateOf(state.model) }
    val typedModel = if (modelEdited) typedModelText else state.model
    var modelSavedAs by rememberSaveable { mutableStateOf<String?>(null) }
    var typedCeiling by remember(state.dailyCeiling) {
        mutableStateOf(state.dailyCeiling.toString())
    }

    MetaSelfScreen(
        title = stringResource(SettingsPage.AI.title),
        modifier = modifier,
        onBack = onBack,
    ) {
        // --- the key ---

        // First on this page, so settings opened from "Add a key in settings" on the describe screen
        // lands with the whole of it — title, field and Save — in view (public issue #11, D79).
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Text(
                text = stringResource(R.string.settings_key_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (state.hasKey) R.string.settings_key_saved else R.string.settings_key_none,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = typedKey,
                onValueChange = { typedKey = it },
                label = { Text(stringResource(R.string.settings_key_field)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                Button(
                    onClick = {
                        onSaveKey(typedKey)
                        typedKey = ""
                    },
                    enabled = typedKey.isNotBlank(),
                ) { Text(stringResource(R.string.settings_key_save)) }

                if (state.hasKey) {
                    TextButton(onClick = onClearKey) {
                        Text(stringResource(R.string.settings_key_clear))
                    }
                }
            }
            Text(
                text = stringResource(R.string.settings_key_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
            RefusedHere(state.failed, SettingsPart.KEY, onDismissFailure)
        }

        HorizontalDivider()

        // --- the model ---

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Text(
                text = stringResource(R.string.settings_model_title),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = typedModel,
                onValueChange = {
                    typedModelText = it
                    modelEdited = true
                    modelSavedAs = null
                },
                label = { Text(stringResource(R.string.settings_model_field)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // Stored only by Save, and never blank: a blank name would read back as the default.
            val wanted = typedModel.trim()
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                Button(
                    onClick = {
                        onSetModel(wanted)
                        // The box keeps what was saved, trimmed, while the store catches up.
                        typedModelText = wanted
                        modelSavedAs = wanted
                    },
                    enabled = wanted.isNotEmpty() && wanted != state.model,
                ) { Text(stringResource(R.string.settings_model_save)) }
                if (wanted.isEmpty() && state.model != EstimatePrompt.DEFAULT_MODEL) {
                    TextButton(
                        onClick = {
                            onSetModel(EstimatePrompt.DEFAULT_MODEL)
                            typedModelText = EstimatePrompt.DEFAULT_MODEL
                            modelEdited = true
                            modelSavedAs = EstimatePrompt.DEFAULT_MODEL
                        },
                    ) { Text(stringResource(R.string.settings_model_use_default, EstimatePrompt.DEFAULT_MODEL)) }
                }
            }
            modelSavedAs?.takeIf { it == state.model && it == wanted }?.let { saved ->
                Text(
                    text = stringResource(R.string.settings_model_saved, saved),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = stringResource(R.string.settings_model_note),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
            RefusedHere(state.failed, SettingsPart.MODEL, onDismissFailure)
        }

        HorizontalDivider()

        // --- the ceiling ---

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Text(
                text = stringResource(R.string.settings_ceiling_title),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = typedCeiling,
                onValueChange = {
                    typedCeiling = it
                    it.trim().toIntOrNull()?.let(onSetCeiling)
                },
                label = { Text(stringResource(R.string.settings_ceiling_field)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(
                    R.string.settings_used_today,
                    state.usedToday,
                    state.dailyCeiling,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.settings_ceiling_note),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
            RefusedHere(state.failed, SettingsPart.CEILING, onDismissFailure)
        }

        HorizontalDivider()

        // --- proving the whole chain ---

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Button(onClick = onTest, enabled = state.hasKey && !state.testing) {
                Text(stringResource(R.string.settings_test))
            }
            // Test it asks the SAVED model; a different name still in the box is not it yet.
            if (typedModel.trim() != state.model) {
                Text(
                    text = stringResource(R.string.settings_model_not_saved, state.model),
                    style = MaterialTheme.typography.bodySmall,
                    color = MetaSelfInk.two,
                )
            }

            if (state.testing) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.settings_testing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            state.testResult?.let { result ->
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // What the saved model is sent, learned from its refusals if it had any (D57 §6).
            state.testLearned?.let { learned ->
                Text(
                    text = learned,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            RefusedHere(state.failed, SettingsPart.TEST, onDismissFailure)
        }
    }
}
