package com.metaself.app.domain.backup

/**
 * When an automatic backup is due, and which files to keep.
 *
 * Pure, so the rhythm can be tested without a clock or a filesystem. The app has no background work
 * of its own, so "automatic" means "when he next opens it" — which is the same compromise the weekly
 * target revision makes, and for the same reason: a backup taken while the phone was in a drawer
 * would buy nothing over one taken when it is next looked at.
 */
object BackupSchedule {

    /** One file a day. More often writes the same thing repeatedly; less often loses a day. */
    const val EVERY_DAYS = 1L

    /**
     * How many daily files to keep.
     *
     * A backup that overwrites itself is one corrupted write away from being nothing. A backup that
     * never deletes anything fills a folder with a thousand files. Fourteen days is long enough to
     * notice something has gone wrong and still have a good copy behind it.
     */
    const val KEEP_FILES = 14

    private const val PREFIX = "metaself-"
    private const val SUFFIX = ".json"

    fun isDue(lastBackupEpochDay: Long?, todayEpochDay: Long): Boolean =
        lastBackupEpochDay == null || todayEpochDay - lastBackupEpochDay >= EVERY_DAYS

    /** "metaself-2026-09-04.json" — dated, so a folder of these sorts itself and reads plainly. */
    fun fileNameFor(isoDate: String): String = "$PREFIX$isoDate$SUFFIX"

    fun isBackupFile(name: String): Boolean = name.startsWith(PREFIX) && name.endsWith(SUFFIX)

    /**
     * Which of the app's own files should go, oldest first.
     *
     * Only files this app named. Anything else in the folder is the owner's and is none of the
     * app's business — a backup routine that deletes what it did not write is a data-loss bug
     * waiting for the wrong folder to be chosen.
     */
    fun filesToDelete(namesInFolder: List<String>): List<String> = namesInFolder
        .filter(::isBackupFile)
        .sorted()
        .dropLast(KEEP_FILES)
}
