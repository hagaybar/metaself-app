package com.metaself.app.data.profile

import com.metaself.app.domain.goal.GoalArrival
import com.metaself.app.domain.milestone.Milestone
import com.metaself.app.domain.window.WindowRule
import com.metaself.app.domain.profile.Profile
import com.metaself.app.domain.target.TargetRevision
import kotlinx.coroutines.flow.Flow

/**
 * The stored profile, or null before setup has happened.
 *
 * An interface with exactly one implementation, for the same reason decision D2 gives for the AI
 * client: the thing behind it is expected to change, and the UI should not have to.
 */
interface ProfileRepository {

    val profile: Flow<Profile?>

    suspend fun save(profile: Profile)

    /** The last weekly recalculation of the target, or null before there has been one. */
    val revision: Flow<TargetRevision?>

    /** Recording a revision clears [revisionSeen]: a new revision is a new thing to announce. */
    suspend fun saveRevision(revision: TargetRevision)

    /**
     * Whether the owner has seen the notice for the revision now stored.
     *
     * Stored rather than held on screen, because a notice that vanishes when the phone is put away
     * has announced nothing — which is the failure decision D11 is about.
     */
    val revisionSeen: Flow<Boolean>

    suspend fun markRevisionSeen()

    /** The goal weight that was reached, and when — or null if none ever has been. */
    val arrival: Flow<GoalArrival?>

    suspend fun saveArrival(arrival: GoalArrival)

    /** Every milestone already celebrated, and the day each was. */
    val milestones: Flow<Map<Milestone, Long>>

    suspend fun recordMilestones(reached: Map<Milestone, Long>)

    /**
     * The standing correction to what the formula thinks a day costs (D25).
     *
     * Zero until there is a month of logging to measure against, and bounded either way.
     */
    val burnAdjustmentKcal: Flow<Int>

    suspend fun saveBurnAdjustment(kcal: Int)

    /** The folder the owner picked for automatic backups, or null (D26). */
    val backupFolderUri: Flow<String?>

    suspend fun saveBackupFolder(uri: String?)

    /** The day the last automatic backup was written, or null if none ever has been. */
    val lastBackupEpochDay: Flow<Long?>

    suspend fun saveLastBackupDay(epochDay: Long)

    /** Every window rule ever set, of either kind, oldest first (D27, D29). */
    val windowRules: Flow<List<WindowRule>>

    /**
     * Add a rule taking effect from a day.
     *
     * Adds rather than replaces: the old one keeps governing the days it governed, so the record of
     * whether he kept to what he had actually decided stays true. Setting one twice on the same day
     * IS a replacement, across both kinds — choosing a ratio today replaces the hours set today,
     * which is the owner choosing which of the two he is keeping.
     */
    suspend fun addWindowRule(rule: WindowRule)

    /** Stop keeping tab, of either kind. Past days keep their verdicts; nothing new is judged. */
    suspend fun clearWindowRules()

    /** The day something encouraging was last said, so at most one a day is (D22's budget). */
    val lastEncouragedDay: Flow<Long?>

    suspend fun saveEncouragedDay(epochDay: Long)

    /** The last day the app was opened, which is how "coming back" is noticed at all. */
    val lastSeenDay: Flow<Long?>

    suspend fun saveLastSeenDay(epochDay: Long)

    /** Whether the daily copy also goes to the owner's own Google Drive. */
    val driveBackupOn: Flow<Boolean>

    suspend fun setDriveBackup(on: Boolean)

    /**
     * How much history the weight chart was last showing, by name, or null if never chosen.
     *
     * The name rather than the position, so that reordering the ranges in a later version cannot
     * silently change which one the owner had picked. Stored rather than remembered on screen
     * because turning the phone recreates the screen and would otherwise lose it.
     */
    val weightChartRange: Flow<String?>

    suspend fun saveWeightChartRange(name: String)
}
