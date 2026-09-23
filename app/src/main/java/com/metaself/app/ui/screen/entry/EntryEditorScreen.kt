package com.metaself.app.ui.screen.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/**
 * One item, being written down — or one already logged, being corrected.
 *
 * Stateless, like every other screen here: it draws what it is given and reports what was touched.
 * Step 4 and step 7 both reuse it by handing it a form built from an existing item.
 *
 * Which of the two it is only shows in the heading, and it has to: correcting an estimate is not
 * starting from nothing, and one heading for both told the owner it was.
 */
@Composable
fun EntryEditorScreen(
    state: EntryFormState,
    showErrors: Boolean,
    /**
     * Whether the day's edit tap opened this, rather than the by-hand path.
     *
     * No default on purpose. "Type the numbers" means the manual, no-network path specifically
     * (D8), and a default would let a third entrance inherit that heading silently — which is the
     * bug this parameter exists to fix.
     */
    isEdit: Boolean,
    onChange: (EntryFormState) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val errors = if (showErrors) state.errors() else emptyMap()

    MetaSelfScreen(
        title = stringResource(
            if (isEdit) R.string.entry_title_edit else R.string.entry_title_add,
        ),
        modifier = modifier,
        onBack = onCancel,
    ) {
        // D48's grouping: the frame puts a section's gap between its children, so what the thing
        // is and what it is worth are two children, each spaced inside by the step that says how
        // closely its parts belong.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Field(
                label = stringResource(R.string.entry_name),
                value = state.name,
                error = errors[EntryField.NAME],
                numeric = false,
                onValueChange = { onChange(state.copy(name = it)) },
            )

            // Only for an item that carries a portion with numbers behind it. A typed entry has no
            // amount to scale, and offering one would invite a figure the record cannot support.
            if (state.hasAmount) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    Field(
                        label = stringResource(R.string.entry_amount, state.amountUnit),
                        value = state.amount,
                        error = errors[EntryField.AMOUNT],
                        numeric = true,
                        onValueChange = { onChange(state.withAmount(it)) },
                    )
                    Text(
                        text = stringResource(R.string.entry_amount_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MetaSelfInk.two,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            // Said before the first box it governs, not only in the refusal after Add it (issue
            // #18, D38). Below the amount, which takes decimals, so it is not read as governing
            // that box.
            Text(
                text = stringResource(R.string.entry_whole_numbers),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )

            Field(
                label = stringResource(R.string.entry_kcal),
                value = state.kcal,
                error = errors[EntryField.KCAL],
                numeric = true,
                onValueChange = { onChange(state.copy(kcal = it)) },
            )

            Text(
                text = stringResource(R.string.entry_macros_optional),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )

            Field(
                label = stringResource(R.string.entry_protein),
                value = state.proteinG,
                error = errors[EntryField.PROTEIN],
                numeric = true,
                onValueChange = { onChange(state.copy(proteinG = it)) },
            )

            Field(
                label = stringResource(R.string.entry_carbs),
                value = state.carbsG,
                error = errors[EntryField.CARBS],
                numeric = true,
                onValueChange = { onChange(state.copy(carbsG = it)) },
            )

            Field(
                label = stringResource(R.string.entry_fat),
                value = state.fatG,
                error = errors[EntryField.FAT],
                numeric = true,
                onValueChange = { onChange(state.copy(fatG = it)) },
            )
        }

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.entry_save))
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    error: String?,
    numeric: Boolean,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = error != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
