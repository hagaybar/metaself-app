package com.metaself.app.data.health

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Workouts. **No `@Upsert`:** Room's upsert updates by primary key after a unique-index conflict, and
 * a session freshly read from Health Connect has id 0, so a re-read would be dropped without a word.
 * The sync finds the row with [synced] and then calls [insert] or [update].
 */
@Dao
interface WorkoutDao {

    @Query("SELECT * FROM workouts WHERE epochDay BETWEEN :from AND :to ORDER BY startedAtMillis")
    fun observeBetween(from: Long, to: Long): Flow<List<WorkoutEntity>>

    /**
     * The first day holding any workout, hidden ones and uncounted walks included; null with none.
     * How far back the Movement screen can step (D83). A read, not a schema change.
     */
    @Query("SELECT MIN(epochDay) FROM workouts")
    fun observeEarliest(): Flow<Long?>

    /**
     * Running distance per week, Monday-based, newest first. A hidden session is left out; a run with
     * no distance adds nothing rather than zeroing the week. A week with no runs has no row.
     */
    @Query(
        "SELECT (epochDay + 3) / 7 AS week, SUM(distanceM) AS metres, COUNT(*) AS runs " +
            "FROM workouts WHERE kind = 'RUN' AND hidden = 0 AND distanceM IS NOT NULL " +
            "GROUP BY week ORDER BY week DESC LIMIT :weeks",
    )
    fun observeWeeklyRunning(weeks: Int): Flow<List<WeekOfRunning>>

    /**
     * Every day holding a synced WALK written by [origin], hidden ones included — the days whose
     * summaries change when that app's walks are switched on or off (D81).
     */
    @Query("SELECT DISTINCT epochDay FROM workouts WHERE source = 'SYNCED' AND kind = 'WALK' AND origin = :origin")
    suspend fun syncedWalkDays(origin: String): List<Long>

    /**
     * The synced workouts of [from]..[to] during which their own writing app stored a distance reading
     * (D81's investigation): a reading of that origin overlapping the workout's start plus its whole
     * minutes, looked for a day either side, as the readings are filed by their own start.
     */
    @Query(
        "SELECT w.id FROM workouts w WHERE w.source = 'SYNCED' AND w.epochDay BETWEEN :from AND :to " +
            "AND EXISTS (SELECT 1 FROM health_readings r WHERE r.kind = 'DISTANCE' AND r.origin = w.origin " +
            "AND r.epochDay BETWEEN w.epochDay - 1 AND w.epochDay + 1 " +
            "AND r.startMillis < w.startedAtMillis + w.durationMinutes * 60000 " +
            "AND COALESCE(r.endMillis, r.startMillis) > w.startedAtMillis)",
    )
    suspend fun idsWithOwnDistance(from: Long, to: Long): List<Long>

    /**
     * Visible synced workouts on [days] with no distance, or with no calories from their app (energy
     * source NONE): the figures the copying may ask Health Connect for again.
     */
    @Query(
        "SELECT * FROM workouts WHERE source = 'SYNCED' AND hidden = 0 AND epochDay IN (:days) " +
            "AND (distanceM IS NULL OR (energyKcal IS NULL AND energySource = 'NONE')) ORDER BY startedAtMillis",
    )
    suspend fun missingTotalsOn(days: List<Long>): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE origin = :origin AND originId = :originId")
    suspend fun synced(origin: String, originId: String): WorkoutEntity?

    @Query(
        "SELECT originId FROM workouts WHERE source = 'SYNCED' AND originId IS NOT NULL " +
            "AND epochDay BETWEEN :from AND :to",
    )
    suspend fun syncedIdsBetween(from: Long, to: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(workout: WorkoutEntity): Long

    @Update
    suspend fun update(workout: WorkoutEntity)

    /**
     * Synced sessions in the window the last read did not return — deleted in the writing app. A
     * hidden one is kept: it was hidden on purpose, and deleting it would let the next read bring
     * it back.
     */
    @Query(
        "DELETE FROM workouts WHERE source = 'SYNCED' AND epochDay BETWEEN :from AND :to " +
            "AND originId NOT IN (:keep) AND hidden = 0",
    )
    suspend fun dropSyncedNotIn(from: Long, to: Long, keep: List<String>)

    @Query("UPDATE workouts SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    /** A synced session is never deleted by the owner, only hidden: its numbers are the band's. */
    @Query("DELETE FROM workouts WHERE id = :id AND source = 'TYPED'")
    suspend fun deleteTyped(id: Long)

    @Query("SELECT * FROM workouts ORDER BY epochDay, startedAtMillis, id")
    suspend fun all(): List<WorkoutEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(workouts: List<WorkoutEntity>)

    @Query("DELETE FROM workouts")
    suspend fun deleteAll()

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun byId(id: Long): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE epochDay = :epochDay ORDER BY startedAtMillis")
    suspend fun onDay(epochDay: Long): List<WorkoutEntity>

    @Query("SELECT DISTINCT epochDay FROM workouts WHERE source = 'SYNCED' AND originId = :originId")
    suspend fun daysOfSynced(originId: String): List<Long>

    /** Deleted in the writing app. A hidden row is kept: it was hidden on purpose. */
    @Query("DELETE FROM workouts WHERE source = 'SYNCED' AND originId = :originId AND hidden = 0")
    suspend fun deleteSynced(originId: String)

    /** Synced sessions starting in [from, to) that the owner has not hidden. */
    @Query(
        "SELECT * FROM workouts WHERE source = 'SYNCED' AND hidden = 0 " +
            "AND startedAtMillis >= :from AND startedAtMillis < :to",
    )
    suspend fun visibleSyncedBetween(from: Long, to: Long): List<WorkoutEntity>

    /** One synced row, by its own id. A typed workout is never removed by the sync. */
    @Query("DELETE FROM workouts WHERE id = :id AND source = 'SYNCED'")
    suspend fun deleteSyncedRow(id: Long)
}
