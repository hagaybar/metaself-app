package com.metaself.app.data.profile

import com.metaself.app.domain.goal.GoalArrival
import com.metaself.app.domain.milestone.Milestone
import com.metaself.app.domain.window.WindowRule
import com.metaself.app.domain.profile.Profile
import com.metaself.app.domain.target.TargetRevision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The profile store, in memory.
 *
 * One fake, shared, rather than a private copy in each test that needs one: the interface has grown
 * twice now, and two copies means two places to remember.
 */
class FakeProfileRepository(
    initial: Profile? = null,
    initialRevision: TargetRevision? = null,
    initialSeen: Boolean = false,
    initialArrival: GoalArrival? = null,
    initialMilestones: Map<Milestone, Long> = emptyMap(),
    initialChartRange: String? = null,
) : ProfileRepository {

    private val profileState = MutableStateFlow(initial)
    private val revisionState = MutableStateFlow(initialRevision)
    private val seenState = MutableStateFlow(initialSeen)
    private val arrivalState = MutableStateFlow(initialArrival)
    private val milestoneState = MutableStateFlow(initialMilestones)

    /** Every revision this store was asked to record, in order. */
    val revisions = mutableListOf<TargetRevision>()

    /** Whether the notice was ever marked as seen. */
    var seenMarked: Boolean = false
        private set

    val storedRevision: TargetRevision? get() = revisionState.value

    override val profile: Flow<Profile?> = profileState

    override suspend fun save(profile: Profile) {
        profileState.value = profile
    }

    override val revision: Flow<TargetRevision?> = revisionState

    override suspend fun saveRevision(revision: TargetRevision) {
        revisions += revision
        revisionState.value = revision
        seenState.value = false
    }

    override val revisionSeen: Flow<Boolean> = seenState

    override suspend fun markRevisionSeen() {
        seenMarked = true
        seenState.value = true
    }

    /** Every arrival this store was asked to record, in order. */
    val arrivals = mutableListOf<GoalArrival>()

    override val arrival: Flow<GoalArrival?> = arrivalState

    override suspend fun saveArrival(arrival: GoalArrival) {
        arrivals += arrival
        arrivalState.value = arrival
    }

    /** How many times the store was asked to record milestones, to catch a repeat announcement. */
    var milestoneWrites: Int = 0
        private set

    override val milestones: Flow<Map<Milestone, Long>> = milestoneState

    override suspend fun recordMilestones(reached: Map<Milestone, Long>) {
        milestoneWrites++
        milestoneState.value = reached
    }

    private val burnAdjustmentState = MutableStateFlow(0)

    override val burnAdjustmentKcal: Flow<Int> = burnAdjustmentState

    override suspend fun saveBurnAdjustment(kcal: Int) {
        burnAdjustmentState.value = kcal
    }

    private val backupFolderState = MutableStateFlow<String?>(null)
    private val lastBackupState = MutableStateFlow<Long?>(null)

    override val backupFolderUri: Flow<String?> = backupFolderState

    override suspend fun saveBackupFolder(uri: String?) {
        backupFolderState.value = uri?.takeIf { it.isNotBlank() }
    }

    override val lastBackupEpochDay: Flow<Long?> = lastBackupState

    override suspend fun saveLastBackupDay(epochDay: Long) {
        lastBackupState.value = epochDay
    }

    private val windowState = MutableStateFlow<List<WindowRule>>(emptyList())

    override val windowRules: Flow<List<WindowRule>> = windowState

    /**
     * One rule per starting day, across both kinds.
     *
     * Setting a ratio today replaces the hours set today — that is the owner choosing which he is
     * keeping — while yesterday's rule is left exactly where it was.
     */
    override suspend fun addWindowRule(rule: WindowRule) {
        windowState.value = windowState.value.filterNot {
            it.fromEpochDay == rule.fromEpochDay
        } + rule
    }

    override suspend fun clearWindowRules() {
        windowState.value = emptyList()
    }

    private val encouragedState = MutableStateFlow<Long?>(null)
    private val lastSeenState = MutableStateFlow<Long?>(null)

    override val lastEncouragedDay: Flow<Long?> = encouragedState

    override suspend fun saveEncouragedDay(epochDay: Long) {
        encouragedState.value = epochDay
    }

    override val lastSeenDay: Flow<Long?> = lastSeenState

    override suspend fun saveLastSeenDay(epochDay: Long) {
        lastSeenState.value = epochDay
    }

    private val driveState = MutableStateFlow(false)

    override val driveBackupOn: Flow<Boolean> = driveState

    override suspend fun setDriveBackup(on: Boolean) {
        driveState.value = on
    }

    private val chartRangeState = MutableStateFlow(initialChartRange)

    /** Every range this store was asked to remember, in order. */
    val chartRanges = mutableListOf<String>()

    override val weightChartRange: Flow<String?> = chartRangeState

    override suspend fun saveWeightChartRange(name: String) {
        chartRanges += name
        chartRangeState.value = name
    }

    /** The remembered range as it now stands, for a test asking what choosing one wrote. */
    val storedChartRange: String? get() = chartRangeState.value

    /** The standing adjustment as it now stands, for a test asking what a revision wrote. */
    val storedBurnAdjustment: Int get() = burnAdjustmentState.value

    /** The profile as it now stands, for a test asking what a switch to holding actually wrote. */
    val storedProfile: Profile? get() = profileState.value
}
