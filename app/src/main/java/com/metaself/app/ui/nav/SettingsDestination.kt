package com.metaself.app.ui.nav

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.metaself.app.data.health.HealthPermissions
import com.metaself.app.ui.screen.settings.AiSettingsPage
import com.metaself.app.ui.screen.settings.BackupSettingsPage
import com.metaself.app.ui.screen.settings.EatingSettingsPage
import com.metaself.app.ui.screen.settings.FoodDatabaseSettingsPage
import com.metaself.app.ui.screen.settings.MovementSettingsPage
import com.metaself.app.ui.screen.settings.ProblemsSettingsPage
import com.metaself.app.ui.screen.settings.SettingsPage
import com.metaself.app.ui.screen.settings.SettingsScreen
import com.metaself.app.ui.screen.settings.SettingsViewModel
import java.time.LocalDate

/**
 * One Settings destination — the index when [page] is null, else that page (D79) — wired to the one
 * [SettingsViewModel] of this visit, which lives on the Settings graph's entry [graph].
 *
 * Everything that needs an activity (Android's pickers, permission screens, Google's consent screen,
 * the clipboard) is kept here rather than inside the pages, so each page stays a pure composable a
 * render test can draw without an activity underneath it. Each launcher is registered on the page
 * whose control launches it.
 */
@Composable
internal fun SettingsDestination(
    page: SettingsPage?,
    here: NavBackStackEntry,
    graph: NavBackStackEntry,
    navController: NavController,
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel(graph)
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()

    // Each time a Settings destination opens, not once per visit: the index's status lines need
    // these, and a page opened directly (the describe screen's way to the key) never passes the
    // index. All three are reads that record their own failure.
    LaunchedEffect(Unit) {
        settingsViewModel.refreshSteps()
        settingsViewModel.refreshWindow()
        settingsViewModel.refreshHealthRecord()
    }

    // Google's consent screen, shown once. A view model cannot start an activity, so it hands the
    // screen up here; when it comes back, the write is simply tried again.
    //
    // Every Settings destination watches for it, but only the one in front launches it: during a
    // move between two pages both are drawn, and neither is resumed until the move has finished, so
    // the screen cannot be shown twice.
    val consent by settingsViewModel.consent.collectAsStateWithLifecycle()
    val lifecycle by here.lifecycle.currentStateAsState()
    val showConsent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        // Retried ONCE, and told it is a retry. Retrying blindly is what turned a configuration
        // problem into an endless account picker.
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            settingsViewModel.driveNow(afterConsent = true)
        } else {
            settingsViewModel.consentDeclined()
        }
    }
    val resumed = lifecycle == Lifecycle.State.RESUMED
    LaunchedEffect(consent, resumed) {
        if (!resumed) return@LaunchedEffect
        consent?.let {
            showConsent.launch(IntentSenderRequest.Builder(it).build())
            settingsViewModel.consentShown()
        }
    }

    val onBack = { navController.popFrom(here) }

    when (page) {
        null -> SettingsScreen(
            state = settingsState,
            onOpen = { navController.openSettingsPage(here, it) },
            onBack = onBack,
        )

        SettingsPage.EATING -> {
            // From Android 13 a notification nobody permitted is a notification nobody sees. Asked
            // at the moment the owner turns the reminder on, which is the only moment it means
            // anything to him — and the setting is saved either way, so a refusal leaves a switch
            // that is on and silent rather than a switch that quietly turned itself off.
            val askForNotifications = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { }
            EatingSettingsPage(
                state = settingsState,
                onSetWindow = settingsViewModel::setEatingWindow,
                onSetRatio = settingsViewModel::setMeasuredWindow,
                onClearWindow = settingsViewModel::clearEatingWindow,
                onSetReminder = { reminder ->
                    settingsViewModel.setReminder(reminder)
                    if (reminder.enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onSendReminderNow = settingsViewModel::sendReminderNow,
                onDismissFailure = settingsViewModel::dismissFailure,
                onBack = onBack,
            )
        }

        SettingsPage.MOVEMENT -> {
            // Health Connect issues its own permission screen rather than Android's. The result is
            // whatever the owner chose there, so the settings line is asked to look again rather
            // than assuming it got what it wanted.
            val askForSteps = rememberLauncherForActivityResult(
                PermissionController.createRequestPermissionResultContract(),
            ) {
                settingsViewModel.refreshSteps()
                settingsViewModel.refreshHealthRecord()
            }
            MovementSettingsPage(
                state = settingsState,
                onConnectSteps = {
                    // A phone whose own Health Connect cannot grant history older than 30 days
                    // (D72) is not asked for it: that permission would only ever come back refused.
                    val permissions = if (settingsState.healthRecord.historyOffered) {
                        HealthPermissions.ALL
                    } else {
                        HealthPermissions.withoutHistory
                    }
                    askForSteps.launch(permissions)
                },
                onBack = onBack,
            )
        }

        SettingsPage.BACKUPS -> {
            // Android's own document picker, so the app needs no storage permission and the owner
            // puts the file wherever he already keeps things rather than wherever the app decided.
            val chooseWhereToSave = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri -> uri?.let(settingsViewModel::exportTo) }

            val chooseWhatToRestore = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri -> uri?.let(settingsViewModel::offerRestoreFrom) }

            // A whole folder rather than a file, and a permission that survives a reboot. This is
            // what lets the copy be automatic with no Cloud project and no account (D26).
            val chooseBackupFolder = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree(),
            ) { uri -> uri?.let(settingsViewModel::useBackupFolder) }

            BackupSettingsPage(
                state = settingsState,
                onPickBackupFolder = { chooseBackupFolder.launch(null) },
                onForgetBackupFolder = settingsViewModel::forgetBackupFolder,
                onBackUpNow = settingsViewModel::backUpNow,
                onSetDrive = settingsViewModel::setDriveBackup,
                onDriveNow = settingsViewModel::driveNow,
                onOfferArchive = settingsViewModel::offerArchive,
                onConfirmArchive = settingsViewModel::confirmArchive,
                onCancelArchive = settingsViewModel::cancelArchive,
                onDismissArchiveMessage = settingsViewModel::dismissArchiveMessage,
                onExport = { chooseWhereToSave.launch(backupFileName()) },
                onRestore = { chooseWhatToRestore.launch(arrayOf("application/json", "*/*")) },
                onConfirmRestore = settingsViewModel::confirmRestore,
                onCancelRestore = settingsViewModel::cancelRestore,
                onDismissBackupMessage = settingsViewModel::dismissBackupMessage,
                onDismissFailure = settingsViewModel::dismissFailure,
                onBack = onBack,
            )
        }

        SettingsPage.AI -> AiSettingsPage(
            state = settingsState,
            onSaveKey = settingsViewModel::saveKey,
            onClearKey = settingsViewModel::clearKey,
            onSetModel = settingsViewModel::setModel,
            onSetCeiling = settingsViewModel::setDailyCeiling,
            onTest = settingsViewModel::test,
            onDismissFailure = settingsViewModel::dismissFailure,
            onBack = onBack,
        )

        SettingsPage.FOOD_DATABASE -> FoodDatabaseSettingsPage(
            state = settingsState,
            onSaveOffAccount = settingsViewModel::saveOffAccount,
            onClearOffAccount = settingsViewModel::clearOffAccount,
            onDismissFailure = settingsViewModel::dismissFailure,
            onBack = onBack,
        )

        SettingsPage.PROBLEMS -> {
            val clipboard = LocalClipboardManager.current
            ProblemsSettingsPage(
                state = settingsState,
                onCopyProblems = {
                    clipboard.setText(AnnotatedString(settingsViewModel.problemsAsText()))
                },
                onClearProblems = settingsViewModel::clearProblems,
                onBack = onBack,
            )
        }
    }
}

/** "metaself-2026-09-04.json" — dated, so a folder of these sorts itself. */
private fun backupFileName(): String =
    "metaself-${LocalDate.now()}.json"
