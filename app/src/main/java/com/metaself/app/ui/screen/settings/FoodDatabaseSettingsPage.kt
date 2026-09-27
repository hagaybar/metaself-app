package com.metaself.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.metaself.app.R
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/** Settings' Food database page (D79): the Open Food Facts account, moved here unchanged. */
@Composable
fun FoodDatabaseSettingsPage(
    state: SettingsUiState,
    onSaveOffAccount: (String, String) -> Unit,
    onClearOffAccount: () -> Unit,
    /** Take down the sentence of an action that threw. Required, so it cannot be left unwired. */
    onDismissFailure: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var typedOffUser by remember(state.offUsername) { mutableStateOf(state.offUsername) }
    var typedOffPassword by remember { mutableStateOf("") }

    MetaSelfScreen(
        title = stringResource(SettingsPage.FOOD_DATABASE.title),
        modifier = modifier,
        onBack = onBack,
    ) {
        // --- the food database account ---

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Text(
                text = stringResource(R.string.settings_off_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = if (state.hasOffPassword) {
                    stringResource(R.string.settings_off_set, state.offUsername)
                } else {
                    stringResource(R.string.settings_off_none)
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = typedOffUser,
                onValueChange = { typedOffUser = it },
                label = { Text(stringResource(R.string.settings_off_user)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Hidden as it is typed, and never read back out of the store onto a screen. The same rule
            // as the API key: a credential on screen is a credential in a screenshot.
            OutlinedTextField(
                value = typedOffPassword,
                onValueChange = { typedOffPassword = it },
                label = { Text(stringResource(R.string.settings_off_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                Button(
                    onClick = {
                        onSaveOffAccount(typedOffUser, typedOffPassword)
                        typedOffPassword = ""
                    },
                    enabled = typedOffUser.isNotBlank() && typedOffPassword.isNotBlank(),
                ) { Text(stringResource(R.string.settings_off_save)) }

                if (state.hasOffPassword) {
                    TextButton(onClick = onClearOffAccount) {
                        Text(stringResource(R.string.settings_off_clear))
                    }
                }
            }

            Text(
                text = stringResource(R.string.settings_off_note),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
            RefusedHere(state.failed, SettingsPart.OFF_ACCOUNT, onDismissFailure)
        }
    }
}
