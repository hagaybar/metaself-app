package com.metaself.app.data.backup

import android.net.Uri
import com.metaself.app.data.drive.DriveBackup
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.domain.backup.BackupSchedule
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Taking the copy without being asked.
 *
 * The app has no background work of its own, so "automatic" means "when he next opens it". That is
 * the same compromise the weekly target revision makes: a backup taken while the phone was in a
 * drawer would buy nothing over one taken when it is next looked at, and it would cost a wakelock
 * and a foreground service to arrange.
 *
 * Nothing here can throw upwards. A backup that crashes the app it is protecting is worse than no
 * backup, and the owner would have no way to connect the crash to the cause.
 */
/**
 * The daily copy, behind an interface so that everything above it can be tested without a folder,
 * a filesystem or an Android context.
 */
interface DailyBackup {

    /** Null when there was nothing to do; otherwise what happened. */
    suspend fun runIfDue(today: LocalDate, nowMillis: Long): BackupOutcome?
}

@Singleton
class AutomaticBackup @Inject constructor(
    private val profiles: ProfileRepository,
    private val backups: BackupRepository,
    private val folder: BackupFolder,
    private val drive: DriveBackup,
) : DailyBackup {

    override suspend fun runIfDue(today: LocalDate, nowMillis: Long): BackupOutcome? {
        val folderUri = profiles.backupFolderUri.first() ?: return null
        val last = profiles.lastBackupEpochDay.first()
        if (!BackupSchedule.isDue(last, today.toEpochDay())) return null

        val backup = runCatching { backups.export(nowMillis) }.getOrNull() ?: return null
        val outcome = folder.write(
            folderUri = Uri.parse(folderUri),
            fileName = BackupSchedule.fileNameFor(today.toString()),
            contents = BackupCodec.encode(backup),
        )

        // Drive is a SECOND destination, never a replacement. It is attempted whether or not the
        // folder copy worked, and its failure cannot cost him the local one — a backup with a
        // single way to fail is not a backup.
        if (profiles.driveBackupOn.first()) {
            runCatching { drive.write(today, nowMillis) }
        }

        // Only a written file counts. A failure must leave the date alone, or one bad day would
        // stop it trying again for a week and the owner would never know.
        if (outcome is BackupOutcome.Written) profiles.saveLastBackupDay(today.toEpochDay())
        return outcome
    }
}
