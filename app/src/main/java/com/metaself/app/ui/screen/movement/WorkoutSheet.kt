package com.metaself.app.ui.screen.movement

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.metaself.app.R
import com.metaself.app.domain.movement.Effort
import com.metaself.app.domain.movement.WorkoutDraft
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.ui.movement.WorkoutSheetWording
import com.metaself.app.ui.theme.Spacing

/**
 * The sheet itself: chrome around [WorkoutSheetContent]. Split in two for `MealNamingSheet`'s reason:
 * a modal sheet draws into a window of its own, which the render helper cannot see, so the content is
 * what a test draws and the chrome is a phone check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutSheet(
    sheet: WorkoutSheetState,
    onDraft: (WorkoutDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        WorkoutSheetContent(sheet, onDraft, onSave, onDelete, onCancel)
    }
}

/**
 * Typing in a workout (D76), top to bottom: kind; minutes; a distance for the kinds that have one,
 * with a run's pace live beneath it; the effort, which starts on Moderate and cannot be cleared; the
 * energy as a line — or the owner's own figure; a note. Only kind and minutes are required.
 *
 * **Save is in the title row.** The meal-naming sheet's lesson (0.52.1) was a button pushed out of
 * reach. Here the keyboard is what rises from the bottom, and Material 3 1.2's sheet does not lift its
 * content above it by default (its window insets are the system bars only), so a button at the foot
 * could be covered. One at the top cannot. The fields between scroll, and the whole is padded by the
 * keyboard's height so the lower fields can be scrolled clear of it.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutSheetContent(
    sheet: WorkoutSheetState,
    onDraft: (WorkoutDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = sheet.draft
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = Spacing.Screen, vertical = Spacing.Related),
        verticalArrangement = Arrangement.spacedBy(Spacing.Related),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(if (sheet.editing == null) R.string.workout_sheet_new else R.string.workout_sheet_edit),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onSave, enabled = draft.canSave && !sheet.saving) {
                Text(stringResource(R.string.workout_save))
            }
        }

        sheet.failure?.let { failure ->
            Text(
                text = stringResource(
                    when (failure) {
                        WriteFailure.SAVE -> R.string.workout_not_saved
                        WriteFailure.DELETE -> R.string.workout_not_deleted
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.Related),
        ) {
            // A visible label above the chips, doubling as the group's name for a screen reader.
            Text(
                text = stringResource(R.string.workout_kind_group),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Wrapping, as the weight chart's range chips do: six chips do not fit one line at 320 dp.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
                verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
            ) {
                WorkoutDraft.KINDS.forEach { kind ->
                    FilterChip(
                        selected = draft.kind == kind,
                        onClick = { onDraft(draft.copy(kind = kind)) },
                        label = { Text(stringResource(kindLabel(kind))) },
                    )
                }
            }

            OutlinedTextField(
                value = draft.minutes,
                onValueChange = { onDraft(draft.copy(minutes = it)) },
                label = { Text(stringResource(R.string.workout_minutes)) },
                isError = draft.minutesProblem,
                supportingText = if (draft.minutesProblem) {
                    { Text(stringResource(R.string.workout_minutes_hint)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            if (draft.takesDistance) {
                OutlinedTextField(
                    value = draft.distanceKm,
                    onValueChange = { onDraft(draft.copy(distanceKm = it)) },
                    label = { Text(stringResource(R.string.workout_distance)) },
                    isError = draft.distanceProblem,
                    supportingText = if (draft.distanceProblem) {
                        { Text(stringResource(R.string.workout_distance_hint)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                // Live, so a typo is seen before it is saved (activity spec §4.3).
                WorkoutSheetWording.pace(draft)?.let { pace ->
                    Text(
                        text = pace,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // A visible label above the segments, doubling as the group's name for a screen reader.
            Text(
                text = stringResource(R.string.workout_effort_group),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Effort.entries.forEachIndexed { index, effort ->
                    SegmentedButton(
                        selected = draft.effort == effort,
                        // Choosing the chosen one keeps it: effort cannot be cleared (activity spec §8 item 4).
                        onClick = { onDraft(draft.copy(effort = effort)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = Effort.entries.size),
                    ) {
                        Text(stringResource(effortLabel(effort)))
                    }
                }
            }

            if (draft.ownEnergy) {
                OutlinedTextField(
                    value = draft.energyKcal,
                    onValueChange = { onDraft(draft.copy(energyKcal = it)) },
                    label = { Text(stringResource(R.string.workout_energy_label)) },
                    isError = draft.energyProblem,
                    supportingText = when {
                        draft.energyProblem -> { { Text(stringResource(R.string.workout_energy_hint)) } }
                        draft.energyKcal.isBlank() -> { { Text(stringResource(R.string.workout_energy_blank_hint)) } }
                        else -> null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { onDraft(draft.copy(ownEnergy = false)) }) {
                    Text(stringResource(R.string.workout_energy_estimate))
                }
            } else {
                WorkoutSheetWording.estimate(draft, sheet.weightKg)?.let { line ->
                    Text(text = line, style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = { onDraft(draft.copy(ownEnergy = true)) }) {
                    Text(stringResource(R.string.workout_energy_own))
                }
            }

            OutlinedTextField(
                value = draft.note,
                onValueChange = { onDraft(draft.copy(note = it)) },
                label = { Text(stringResource(R.string.workout_note)) },
                modifier = Modifier.fillMaxWidth(),
            )

            // Plain, not red: D48 keeps red for a refusal and a bad field, and a delete he chose is neither.
            if (sheet.editing != null) {
                TextButton(onClick = onDelete, enabled = !sheet.saving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.workout_delete))
                }
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.workout_not_now))
            }
        }
    }
}

@StringRes
private fun kindLabel(kind: WorkoutKind): Int = when (kind) {
    WorkoutKind.RUN -> R.string.workout_kind_run
    WorkoutKind.WALK -> R.string.workout_kind_walk
    WorkoutKind.CYCLE -> R.string.workout_kind_cycle
    WorkoutKind.SWIM -> R.string.workout_kind_swim
    WorkoutKind.STRENGTH -> R.string.workout_kind_strength
    WorkoutKind.OTHER, WorkoutKind.UNRECOGNISED -> R.string.workout_kind_other
}

@StringRes
private fun effortLabel(effort: Effort): Int = when (effort) {
    Effort.EASY -> R.string.workout_effort_easy
    Effort.MODERATE -> R.string.workout_effort_moderate
    Effort.HARD -> R.string.workout_effort_hard
}
