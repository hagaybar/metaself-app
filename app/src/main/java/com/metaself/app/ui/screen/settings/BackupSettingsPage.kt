package com.metaself.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.health.HealthRecordWording
import com.metaself.app.ui.settings.AutomaticBackupWording
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/**
 * Settings' Backups page (D79), in three groups: keeping a copy in a folder, Google Drive, and by
 * hand. Moved here unchanged from the single Settings scroll, except that Drive's way back to the
 * detailed readings' months, and the line about them, came from Movement to sit with Drive.
 */
@Composable
fun BackupSettingsPage(
    state: SettingsUiState,
    onPickBackupFolder: () -> Unit,
    onForgetBackupFolder: () -> Unit,
    onBackUpNow: () -> Unit,
    onSetDrive: (Boolean) -> Unit,
    onDriveNow: () -> Unit,
    /** Drive's way back to the months, besides the offer after a restore (D71). */
    onOfferArchive: () -> Unit,
    onConfirmArchive: () -> Unit,
    onCancelArchive: () -> Unit,
    onDismissArchiveMessage: () -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    onDismissBackupMessage: () -> Unit,
    /** Take down the sentence of an action that threw. Required, so it cannot be left unwired. */
    onDismissFailure: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(
        title = stringResource(SettingsPage.BACKUPS.title),
        modifier = modifier,
        onBack = onBack,
    ) {
        // --- keeping a copy ---

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
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

            state.automaticBackupMessage?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            }

            Text(
                text = AutomaticBackupWording.KEY_NOT_INCLUDED,
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
        }

        HorizontalDivider()

        // --- Google Drive ---

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Text(
                text = stringResource(R.string.settings_drive_title),
                style = MaterialTheme.typography.titleMedium,
            )

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

            // Moved here from Movement with the other Drive controls (D79): the detailed readings'
            // line, and the way back to their months in Drive.
            if (state.healthRecord.days > 0) {
                Text(
                    HealthRecordWording.detailedBackup(driveOn = state.driveOn),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // A way back to the months in Drive besides the offer after a restore. With the daily file
            // restored on a new phone the record may be empty, so it does not wait for any days here.
            if (state.driveOn) {
                TextButton(
                    onClick = onOfferArchive,
                    enabled = !state.busy,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                ) { Text(stringResource(R.string.settings_archive_ask)) }
            }
            state.pendingArchive?.takeIf { state.archiveFromDrive }?.let { question ->
                ArchiveQuestion(question, onConfirmArchive, onCancelArchive)
            }
            state.archiveMessage?.let { message ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    Text(text = message, style = MaterialTheme.typography.bodyMedium)
                    TextButton(
                        onClick = onDismissArchiveMessage,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    ) { Text(stringResource(R.string.settings_backup_dismiss)) }
                }
            }
        }

        HorizontalDivider()

        // --- by hand ---

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Text(
                text = stringResource(R.string.settings_by_hand_title),
                style = MaterialTheme.typography.titleMedium,
            )

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

            // Nothing has been destroyed at this point. The question names what would be. On the
            // app's ordinary card, not an error-red one: it is a question before a destructive act, as
            // "Delete …? This cannot be undone." is, and red is kept for a refusal and a field that is
            // wrong (D48). The question's own words carry the weight.
            state.pendingRestore?.let { question ->
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

            // After a restore: the detailed readings are not in the daily file, so Drive's months are
            // offered separately (D71). Asked from the Drive controls, the same question is drawn there.
            state.pendingArchive?.takeUnless { state.archiveFromDrive }?.let { question ->
                ArchiveQuestion(question, onConfirmArchive, onCancelArchive)
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
            RefusedHere(state.failed, SettingsPart.BACKUP, onDismissFailure)
        }
    }
}
