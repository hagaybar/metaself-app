package com.metaself.app.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metaself.app.domain.goal.GoalArrival
import com.metaself.app.domain.milestone.Milestone
import com.metaself.app.domain.window.WindowRule
import com.metaself.app.domain.profile.Profile
import com.metaself.app.domain.target.TargetRevision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The profile, in a preferences DataStore.
 *
 * The DataStore is a constructor parameter rather than built from a Context here, so a test can
 * hand it one over a temporary directory and exercise the real storage without a device.
 *
 * Room is not used because this is one record with no queries. Room arrives in step 3, where meals
 * need a table and something to ask of it.
 */
class DataStoreProfileRepository(
    private val store: DataStore<Preferences>,
) : ProfileRepository {

    private companion object {
        const val KEY_BURN_ADJUSTMENT = "burn_adjustment_kcal"
        const val KEY_BACKUP_FOLDER = "backup_folder_uri"
        const val KEY_LAST_BACKUP_DAY = "last_backup_epoch_day"
        const val KEY_LAST_ENCOURAGED = "last_encouraged_epoch_day"
        const val KEY_LAST_SEEN = "last_seen_epoch_day"
        const val KEY_DRIVE_ON = "drive_backup_on"
        const val KEY_CHART_RANGE = "weight_chart_range"
    }

    override val profile: Flow<Profile?> = store.data.map { preferences ->
        ProfileCodec.decode(preferences.asStringMap())
    }

    override suspend fun save(profile: Profile) {
        val encoded = ProfileCodec.encode(profile)
        store.edit { preferences ->
            // Only the profile's own keys. This used to clear the whole store, which was correct
            // while the profile was the only thing in it and became a data-losing bug the moment
            // the target revision moved in beside it: every profile edit would have deleted the
            // revision, and the target would have quietly stopped following the weight trend.
            encoded.keys.forEach { key -> preferences.remove(stringPreferencesKey(key)) }
            encoded.forEach { (key, value) ->
                preferences[stringPreferencesKey(key)] = value
            }
        }
    }

    override val revision: Flow<TargetRevision?> = store.data.map { preferences ->
        TargetRevisionCodec.decode(preferences.asStringMap())
    }

    override val revisionSeen: Flow<Boolean> = store.data.map { preferences ->
        preferences[stringPreferencesKey(TargetRevisionCodec.KEY_SEEN)] == "true"
    }

    override suspend fun saveRevision(revision: TargetRevision) {
        val encoded = TargetRevisionCodec.encode(revision)
        store.edit { preferences ->
            encoded.forEach { (key, value) ->
                preferences[stringPreferencesKey(key)] = value
            }
            // A new revision is a new thing to announce.
            preferences[stringPreferencesKey(TargetRevisionCodec.KEY_SEEN)] = "false"
        }
    }

    override suspend fun markRevisionSeen() {
        store.edit { preferences ->
            preferences[stringPreferencesKey(TargetRevisionCodec.KEY_SEEN)] = "true"
        }
    }

    override val arrival: Flow<GoalArrival?> = store.data.map { preferences ->
        GoalArrivalCodec.decode(preferences.asStringMap())
    }

    override suspend fun saveArrival(arrival: GoalArrival) {
        val encoded = GoalArrivalCodec.encode(arrival)
        store.edit { preferences ->
            encoded.forEach { (key, value) ->
                preferences[stringPreferencesKey(key)] = value
            }
        }
    }

    override val milestones: Flow<Map<Milestone, Long>> = store.data.map { preferences ->
        MilestonesCodec.decode(preferences[stringPreferencesKey(MilestonesCodec.KEY)])
    }

    /**
     * Written as one string holding everything reached so far, rather than appended to.
     *
     * The caller reads the existing set, adds to it and hands back the whole thing, so a write can
     * never half-succeed and leave a milestone recorded without its date.
     */
    override suspend fun recordMilestones(reached: Map<Milestone, Long>) {
        store.edit { preferences ->
            preferences[stringPreferencesKey(MilestonesCodec.KEY)] = MilestonesCodec.encode(reached)
        }
    }

    override val burnAdjustmentKcal: Flow<Int> = store.data.map { preferences ->
        preferences[stringPreferencesKey(KEY_BURN_ADJUSTMENT)]?.toIntOrNull() ?: 0
    }

    override suspend fun saveBurnAdjustment(kcal: Int) {
        store.edit { preferences ->
            preferences[stringPreferencesKey(KEY_BURN_ADJUSTMENT)] = kcal.toString()
        }
    }

    override val backupFolderUri: Flow<String?> = store.data.map { preferences ->
        preferences[stringPreferencesKey(KEY_BACKUP_FOLDER)]?.takeIf { it.isNotBlank() }
    }

    override suspend fun saveBackupFolder(uri: String?) {
        store.edit { preferences ->
            preferences[stringPreferencesKey(KEY_BACKUP_FOLDER)] = uri.orEmpty()
        }
    }

    override val lastBackupEpochDay: Flow<Long?> = store.data.map { preferences ->
        preferences[stringPreferencesKey(KEY_LAST_BACKUP_DAY)]?.toLongOrNull()
    }

    override suspend fun saveLastBackupDay(epochDay: Long) {
        store.edit { preferences ->
            preferences[stringPreferencesKey(KEY_LAST_BACKUP_DAY)] = epochDay.toString()
        }
    }

    override val windowRules: Flow<List<WindowRule>> = store.data.map { preferences ->
        WindowRuleCodec.decode(preferences[stringPreferencesKey(WindowRuleCodec.KEY)])
    }

    override suspend fun addWindowRule(rule: WindowRule) {
        store.edit { preferences ->
            val key = stringPreferencesKey(WindowRuleCodec.KEY)
            val existing = WindowRuleCodec.decode(preferences[key])
            // One rule per starting day, ACROSS BOTH KINDS: setting it twice today is a correction,
            // not a history. Choosing a ratio today therefore replaces the hours set today, while
            // yesterday's rule is left exactly where it was.
            val kept = existing.filterNot { it.fromEpochDay == rule.fromEpochDay }
            preferences[key] = WindowRuleCodec.encode(kept + rule)
        }
    }

    override suspend fun clearWindowRules() {
        store.edit { preferences ->
            preferences[stringPreferencesKey(WindowRuleCodec.KEY)] = ""
        }
    }

    override val lastEncouragedDay: Flow<Long?> = store.data.map { preferences ->
        preferences[stringPreferencesKey(KEY_LAST_ENCOURAGED)]?.toLongOrNull()
    }

    override suspend fun saveEncouragedDay(epochDay: Long) {
        store.edit { it[stringPreferencesKey(KEY_LAST_ENCOURAGED)] = epochDay.toString() }
    }

    override val lastSeenDay: Flow<Long?> = store.data.map { preferences ->
        preferences[stringPreferencesKey(KEY_LAST_SEEN)]?.toLongOrNull()
    }

    override suspend fun saveLastSeenDay(epochDay: Long) {
        store.edit { it[stringPreferencesKey(KEY_LAST_SEEN)] = epochDay.toString() }
    }

    override val driveBackupOn: Flow<Boolean> = store.data.map { preferences ->
        preferences[stringPreferencesKey(KEY_DRIVE_ON)] == "true"
    }

    override suspend fun setDriveBackup(on: Boolean) {
        store.edit { it[stringPreferencesKey(KEY_DRIVE_ON)] = on.toString() }
    }

    override val weightChartRange: Flow<String?> = store.data.map { preferences ->
        preferences[stringPreferencesKey(KEY_CHART_RANGE)]
    }

    override suspend fun saveWeightChartRange(name: String) {
        store.edit { it[stringPreferencesKey(KEY_CHART_RANGE)] = name }
    }

    /** Every string entry in the store, which is all any codec ever reads. */
    private fun Preferences.asStringMap(): Map<String, String> = asMap()
        .mapNotNull { (key, value) -> (value as? String)?.let { key.name to it } }
        .toMap()
}
