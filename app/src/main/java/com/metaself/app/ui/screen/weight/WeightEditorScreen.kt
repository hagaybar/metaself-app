package com.metaself.app.ui.screen.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.day.DayWording
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/**
 * One weight, being written down or corrected.
 *
 * A screen of its own rather than a field on the weight screen: the weight screen is for LOOKING at
 * the trend, and a form that stays put after saving leaves the owner on a page whose job is already
 * done. This is the same shape as the meal editor, and for the same reason.
 *
 * [isEdit] changes only the title and the button. Correcting a reading is logging one for a day that
 * already has one, and the store replaces it — but the way in is an Edit, and a screen that
 * says "Change this reading" is a different thing from one that says "Log a weight" even when the
 * code beneath them is identical.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightEditorScreen(
    initial: WeightFormState,
    todayEpochDay: Long,
    isEdit: Boolean,
    existingDays: Set<Long>,
    onSave: (WeightReading) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var form by remember { mutableStateOf(initial) }
    var showError by remember { mutableStateOf(false) }
    var pickingDay by remember { mutableStateOf(false) }

    val today = LocalDate.ofEpochDay(todayEpochDay)
    val dayLabel = DayWording.label(LocalDate.ofEpochDay(form.epochDay), today)
    val replacing = !isEdit && form.epochDay in existingDays

    MetaSelfScreen(
        title = stringResource(
            if (isEdit) R.string.weight_editor_edit else R.string.weight_editor_add,
        ),
        modifier = modifier,
        onBack = onCancel,
    ) {
        // D48's grouping: the day and the way to change it are one thing, and so are the field and
        // the note about what saving it would replace. The frame puts a section between the two.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(
                text = stringResource(R.string.weight_on_day, dayLabel),
                style = MaterialTheme.typography.titleSmall,
            )

            TextButton(onClick = { pickingDay = true }) {
                Text(stringResource(R.string.weight_change_day))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            OutlinedTextField(
                value = form.kg,
                onValueChange = {
                    form = form.copy(kg = it)
                    showError = false
                },
                label = { Text(stringResource(R.string.weight_field)) },
                isError = showError,
                supportingText = if (showError) {
                    { form.error()?.let { Text(it) } }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            if (replacing) {
                Text(
                    text = stringResource(R.string.weight_replaces),
                    style = MaterialTheme.typography.bodySmall,
                    color = MetaSelfInk.two,
                )
            }
        }

        Button(
            onClick = {
                val reading = form.toReading()
                if (reading == null) showError = true else onSave(reading)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.weight_save)) }
    }

    if (pickingDay) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(form.epochDay)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDay = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            form = form.copy(
                                epochDay = Instant.ofEpochMilli(millis)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                                    .toEpochDay(),
                            )
                        }
                        pickingDay = false
                    },
                ) { Text(stringResource(R.string.day_picker_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingDay = false }) {
                    Text(stringResource(R.string.day_picker_dismiss))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
