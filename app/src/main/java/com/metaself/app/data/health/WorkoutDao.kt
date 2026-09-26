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
     * Running distance per week, Monday-based, newest first. A hidden session is left out; a run with
     * no distance adds nothing rather than zeroing the week. A week with no runs has no row.
     */
    @Query(
        "SELECT (epochDay + 3) / 7 AS week, SUM(distanceM) AS metres, COUNT(*) AS runs " +
            "FROM workouts WHERE kind = 'RUN' AND hidden = 0 AND distanceM IS NOT NULL " +
            "GROUP BY week ORDER BY week DESC LIMIT :weeks",
    )
    fun observeWeeklyRunning(weeks: Int): Flow<List<WeekOfRunning>>

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
