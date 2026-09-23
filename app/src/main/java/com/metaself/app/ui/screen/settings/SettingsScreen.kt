package com.metaself.app.ui.screen.settings

import java.time.LocalDate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.reminder.Reminder
import com.metaself.app.domain.window.MeasuredWindow
import com.metaself.app.domain.window.WindowRule
import com.metaself.app.ui.day.ReminderWording
import com.metaself.app.data.movement.StepAccess
import com.metaself.app.ui.movement.MovementWording
import com.metaself.app.ui.window.WindowWording
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.settings.AutomaticBackupWording
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/**
 * The key, the model, the daily ceiling, and the one button that proves the chain works.
 *
 * A saved key is never shown back — not even masked-with-a-reveal. There is nothing the owner can do
 * with seeing it that he cannot do by pasting a new one, and a credential on screen is a credential
 * in a screenshot.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
    onSetModel: (String) -> Unit,
    onSetCeiling: (Int) -> Unit,
    onTest: () -> Unit,
    onSetReminder: (Reminder) -> Unit,
    onSendReminderNow: () -> Unit,
    onSaveOffAccount: (String, String) -> Unit,
    onClearOffAccount: () -> Unit,
    onSetWindow: (Int, Int) -> Unit,
    onSetRatio: (Int) -> Unit,
    onClearWindow: () -> Unit,
    onConnectSteps: () -> Unit,
    onPickBackupFolder: () -> Unit,
    onForgetBackupFolder: () -> Unit,
    onBackUpNow: () -> Unit,
    onSetDrive: (Boolean) -> Unit,
    onDriveNow: () -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    onDismissBackupMessage: () -> Unit,
    onCopyProblems: () -> Unit,
    onClearProblems: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var typedKey by remember { mutableStateOf("") }
    var typedOffUser by remember(state.offUsername) { mutableStateOf(state.offUsername) }
    var typedOffPassword by remember { mutableStateOf("") }
    var typedModel by remember(state.model) { mutableStateOf(state.model) }
    var typedCeiling by remember(state.dailyCeiling) {
        mutableStateOf(state.dailyCeiling.toString())
    }

    MetaSelfScreen(
        title = stringResource(R.string.settings_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        // --- when to eat ---

        Text(
            text = stringResource(R.string.settings_window_title),
            style = MaterialTheme.typography.titleMedium,
        )

        // BOTH kinds are on screen at once, and that is the arrangement rather than a mode toggle
        // or a tab. The two-way choice is which one he SAVES, not which one he can see: a toggle
        // would hide the kind he is not using behind the kind he is, and picking a ratio would then
        // mean two decisions instead of one.
        val rule = state.windowRule
        val fixed = (rule as? WindowRule.Fixed)?.window
        val measured = (rule as? WindowRule.Measured)?.window

        var startHour by remember(fixed) { mutableStateOf(fixed?.startHour ?: DEFAULT_START) }
        var endHour by remember(fixed) { mutableStateOf(fixed?.endHour ?: DEFAULT_END) }
        var fastingHours by remember(measured) {
            mutableStateOf(measured?.fastingHours ?: DEFAULT_FASTING)
        }

        Text(
            // Once there is a tally it replaces the summary — which is why the ratio and its
            // spoken-out form live below, under the chips, rather than only in this sentence.
            //
            // The tally is in the unit its own kind judges in, and says so in as many words. The
            // fixed hours count days, because "eat between 06:00 and 20:00" is a statement about a
            // day; a ratio counts eating stretches bounded by the fast, because a ratio is a
            // statement about hours (design §3.1, §4). One number in the other's unit would answer
            // a question he did not ask.
            text = when (rule) {
                null -> stringResource(R.string.settings_window_none)

                is WindowRule.Fixed ->
                    WindowWording.kept(state.windowKept, state.windowJudged)
                        ?: stringResource(
                            R.string.settings_window_set,
                            WindowWording.hours(rule.window),
                        )

                is WindowRule.Measured ->
                    WindowWording.keptStretches(
                        kept = state.windowKept,
                        judged = state.windowJudged,
                        since = LocalDate.ofEpochDay(rule.fromEpochDay),
                    )
                        ?: stringResource(
                            R.string.settings_window_ratio_set,
                            WindowWording.ratio(rule.window),
                            WindowWording.inWords(rule.window),
                        )
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = stringResource(R.string.settings_window_kind_hours),
            style = MaterialTheme.typography.titleSmall,
        )

        HourPicker(
            label = stringResource(R.string.settings_window_start),
            hour = startHour,
            onChange = { startHour = it },
        )
        HourPicker(
            label = stringResource(R.string.settings_window_end),
            hour = endHour,
            onChange = { endHour = it },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Button(onClick = { onSetWindow(startHour, endHour) }) {
                Text(stringResource(R.string.settings_window_save))
            }
            if (rule != null) {
                TextButton(onClick = onClearWindow) {
                    Text(stringResource(R.string.settings_window_clear))
                }
            }
        }

        Text(
            text = stringResource(R.string.settings_window_kind_ratio),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.settings_window_ratio_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MetaSelfInk.two,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            RATIOS.forEach { hours ->
                FilterChip(
                    selected = fastingHours == hours,
                    onClick = { fastingHours = hours },
                    label = { Text(WindowWording.ratio(MeasuredWindow(hours))) },
                )
            }
        }

        // The slash never stands alone, and this is its OWN line rather than the tail of a longer
        // sentence: "16/8" means sixteen hours fasting to most of the world and eight to the rest
        // of it, and a figure whose meaning has to be inferred is a figure that will be read wrong.
        Text(
            text = WindowWording.inWords(MeasuredWindow(fastingHours)),
            style = MaterialTheme.typography.bodyMedium,
        )

        // Reads the chosen ratio when it RUNS, not when it is composed. This lambda was built
        // before the chip was pressed, so a value lifted out of it would save the default for ever
        // — a bug that is completely invisible on screen.
        Button(onClick = { onSetRatio(fastingHours) }) {
            Text(stringResource(R.string.settings_window_ratio_save))
        }

        // D27's one firm rule about this feature, said where it is set — and said once for both
        // kinds, because neither of them reaches backwards.
        Text(
            text = WindowWording.FROM_TODAY,
            style = MaterialTheme.typography.bodySmall,
            color = MetaSelfInk.two,
        )

        HorizontalDivider()

        // --- steps ---

        Text(
            text = stringResource(R.string.settings_steps_title),
            style = MaterialTheme.typography.titleMedium,
        )

        Text(
            text = MovementWording.status(
                access = state.stepAccess,
                hasNormal = state.hasStepNormal,
                daysSoFar = state.stepDaysSoFar,
                earliest = state.earliestStepDay,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )

        // A session and its energy are two different records. This says which the band writes,
        // because the day screen cannot: a swim earning nothing looks identical whether the energy
        // was never reported or the day simply was not above his usual.
        MovementWording.bandEnergy(
            daysWithEnergy = state.daysWithBandEnergy,
            daysSeen = state.stepDaysSoFar,
        )?.let { band ->
            Text(
                text = band,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.stepAccess == StepAccess.NOT_PERMITTED) {
            Button(onClick = onConnectSteps) {
                Text(stringResource(R.string.settings_steps_connect))
            }
        }

        HorizontalDivider()

        // --- the food database account ---

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

        HorizontalDivider()

        // --- keeping a copy ---

        Text(
            text = stringResource(R.string.settings_backup_title),
            style = MaterialTheme.typography.titleMedium,
        )

        Text(
            text = stringResource(R.string.settings_backup_note),
            style = MaterialTheme.typography.bodySmall,
            color = MetaSelfInk.two,
        )

        // Automatic, into a folder he picks once. No Cloud project, no account, no secret in the
        // app — and it works just as well pointed at a card or a NAS as at Drive (D26).
        Text(
            text = if (state.hasBackupFolder) {
                AutomaticBackupWording.set(state.lastBackup)
            } else {
                AutomaticBackupWording.NOT_SET
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Button(onClick = onPickBackupFolder, enabled = !state.busy) {
                Text(
                    stringResource(
                        if (state.hasBackupFolder) {
                            R.string.settings_backup_folder_change
                        } else {
                            R.string.settings_backup_folder_pick
                        },
                    ),
                )
            }
            if (state.hasBackupFolder) {
                TextButton(onClick = onBackUpNow) {
                    Text(stringResource(R.string.settings_backup_now))
                }
                TextButton(onClick = onForgetBackupFolder) {
                    Text(stringResource(R.string.settings_backup_folder_forget))
                }
            }
        }

        // A SECOND destination beside the folder, never a replacement: if Drive fails, the copy on
        // the phone is untouched. A backup with one way to fail is not a backup.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (state.driveOn) {
                    AutomaticBackupWording.DRIVE_ON
                } else {
                    AutomaticBackupWording.DRIVE_OFF
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = state.driveOn, onCheckedChange = onSetDrive)
        }

        if (state.driveOn) {
            TextButton(onClick = onDriveNow) {
                Text(stringResource(R.string.settings_drive_now))
            }
        }

        state.driveMessage?.let { message ->
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }

        state.automaticBackupMessage?.let { message ->
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }

        Text(
            text = AutomaticBackupWording.KEY_NOT_INCLUDED,
            style = MaterialTheme.typography.bodySmall,
            color = MetaSelfInk.two,
        )

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
        ) {
            Button(
                onClick = onExport,
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.settings_backup_save)) }

            OutlinedButton(
                onClick = onRestore,
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.settings_backup_restore)) }
        }

        // Nothing has been destroyed at this point. The question names what would be.
        state.pendingRestore?.let { question ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
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
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                        Button(onClick = onConfirmRestore) {
                            Text(stringResource(R.string.settings_backup_replace))
                        }
                        TextButton(onClick = onCancelRestore) {
                            Text(stringResource(R.string.settings_backup_keep))
                        }
                    }
                }
            }
        }

        state.backupMessage?.let { message ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    onClick = onDismissBackupMessage,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                ) { Text(stringResource(R.string.settings_backup_dismiss)) }
            }
        }

        HorizontalDivider()

        // --- the reminder ---

        Text(
            text = stringResource(R.string.settings_reminder_title),
            style = MaterialTheme.typography.titleMedium,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ReminderWording.schedule(state.reminder),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.reminder.enabled,
                onCheckedChange = { on -> onSetReminder(state.reminder.copy(enabled = on)) },
            )
        }

        if (state.reminder.enabled) {
            // Whole hours only. A reminder is not an appointment, and a minute picker invites a
            // precision that the alarm itself — inexact on most phones — cannot honour.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Tight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        onSetReminder(
                            state.reminder.copy(hour = (state.reminder.hour + 23) % 24),
                        )
                    },
                ) { Text(stringResource(R.string.settings_reminder_earlier)) }

                Text(
                    text = ReminderWording.clock(state.reminder),
                    style = MaterialTheme.typography.headlineSmall,
                )

                TextButton(
                    onClick = {
                        onSetReminder(
                            state.reminder.copy(hour = (state.reminder.hour + 1) % 24),
                        )
                    },
                ) { Text(stringResource(R.string.settings_reminder_later)) }
            }

            Text(
                text = stringResource(R.string.settings_reminder_note),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )

            // No test on the build machine can prove a notification arrives on a phone. This is the
            // same button, and the same reasoning, as the one that proves the API key works.
            TextButton(onClick = onSendReminderNow) {
                Text(stringResource(R.string.settings_reminder_test))
            }
        }

        HorizontalDivider()

        // --- the key ---

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

        HorizontalDivider()

        // --- the model ---

        Text(
            text = stringResource(R.string.settings_model_title),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = typedModel,
            onValueChange = {
                typedModel = it
                onSetModel(it)
            },
            label = { Text(stringResource(R.string.settings_model_field)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.settings_model_note),
            style = MaterialTheme.typography.bodySmall,
            color = MetaSelfInk.two,
        )

        HorizontalDivider()

        // --- the ceiling ---

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

        HorizontalDivider()

        // --- proving the whole chain ---

        Button(onClick = onTest, enabled = state.hasKey && !state.testing) {
            Text(stringResource(R.string.settings_test))
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

        HorizontalDivider()

        // --- what has gone wrong ---

        Text(
            text = stringResource(R.string.settings_problems_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.settings_problems_note),
            style = MaterialTheme.typography.bodySmall,
            color = MetaSelfInk.two,
        )

        if (state.problems.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_problems_none),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            state.problems.forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                TextButton(onClick = onCopyProblems) {
                    Text(stringResource(R.string.settings_problems_copy))
                }
                TextButton(onClick = onClearProblems) {
                    Text(stringResource(R.string.settings_problems_clear))
                }
            }
        }
    }
}

/**
 * An hour of the day, by the hour.
 *
 * Whole hours only. A window is a decision about roughly when to stop eating, not an appointment,
 * and offering minutes would invite a precision nobody actually keeps to.
 */
@Composable
private fun HourPicker(label: String, hour: Int, onChange: (Int) -> Unit) {
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

private const val DEFAULT_START = 8
private const val DEFAULT_END = 20

/** Sixteen hours fasting, a common ratio and the default here. */
private const val DEFAULT_FASTING = 16

/** Fasting hours, written fasting-first as 12/12, 14/10, 16/8, 18/6 and 20/4. */
private val RATIOS = listOf(12, 14, 16, 18, 20)
