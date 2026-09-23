package com.metaself.app.ui.settings

import com.metaself.app.data.backup.BackupOutcome
import com.metaself.app.data.drive.DriveOutcome
import com.metaself.app.domain.backup.BackupSchedule
import java.time.LocalDate

/** What the app says about the copy it takes on its own. */
object AutomaticBackupWording {

    const val NOT_SET =
        "Off. Pick a folder and the app will put a copy there once a day, by itself. " +
            "A Drive folder works, and so does anywhere else."

    fun set(lastBackup: LocalDate?): String = if (lastBackup == null) {
        "On. The first copy will be written next time you open the app."
    } else {
        "On. Last copy written on $lastBackup. " +
            "The most recent ${BackupSchedule.KEEP_FILES} days are kept."
    }

    /**
     * What actually happened, said in the same words whether it worked or not.
     *
     * "Backed up successfully" is a claim he cannot check. A file name is one he can, by opening
     * the folder and looking.
     */
    fun outcome(outcome: BackupOutcome): String = when (outcome) {
        is BackupOutcome.Written -> buildString {
            append("Wrote ${outcome.fileName}.")
            if (outcome.deleted > 0) {
                append(" Removed ${outcome.deleted} older ")
                append(if (outcome.deleted == 1) "copy." else "copies.")
            }
        }

        is BackupOutcome.FolderUnreachable ->
            "That folder cannot be reached any more — it may have been moved, or its permission " +
                "withdrawn. Pick it again."

        is BackupOutcome.Failed -> "The copy could not be written (${outcome.reason})."
    }

    /** What Drive did, in the same shape as the folder's outcome and for the same reason. */
    fun drive(outcome: DriveOutcome): String = when (outcome) {
        is DriveOutcome.Written -> buildString {
            append("Wrote ${outcome.fileName} to your Drive.")
            if (outcome.deleted > 0) {
                append(" Removed ${outcome.deleted} older ")
                append(if (outcome.deleted == 1) "copy." else "copies.")
            }
        }

        is DriveOutcome.NeedsConsent -> "Waiting for you to agree on Google's screen."

        is DriveOutcome.Failed ->
            "Could not write to Drive (${outcome.reason}). Your other copies are unaffected."
    }

    /**
     * Said when Google's screen was answered and Drive still will not have it.
     *
     * Names the two things that are actually wrong in that case, because "something went wrong" is
     * what the owner already knows.
     */
    const val CONSENT_DID_NOT_TAKE =
        "Google accepted the account but Drive still refused. That usually means the OAuth client " +
            "in the Cloud console is not of type Android, or its package and signing fingerprint " +
            "do not match this app, or the Drive API is not enabled on the project."

    const val CONSENT_DECLINED = "No account was chosen, so nothing was sent."

    const val DRIVE_OFF =
        "Off. A copy can also go to your own Google Drive, into files only this app can see. " +
            "Your API key is not in them."

    const val DRIVE_ON =
        "On. The daily copy also goes to your Google Drive, alongside the folder one."

    /** The one thing a backup of this app cannot carry. */
    const val KEY_NOT_INCLUDED =
        "As with the file you save yourself, your API key is not in it."
}
