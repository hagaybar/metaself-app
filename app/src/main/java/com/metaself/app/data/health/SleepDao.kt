package com.metaself.app.data.health

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Nights and their stages. A night and its stages are written together inside a transaction the
 * caller owns; deleting a night deletes its stages (the foreign key cascades).
 */
@Dao
interface SleepDao {

    /** Refuses a night already stored: `(origin, recordId)` is unique. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: SleepSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStages(stages: List<SleepStageEntity>)

    @Query("SELECT * FROM sleep_sessions WHERE epochDay BETWEEN :from AND :to ORDER BY startMillis")
    suspend fun sessionsBetween(from: Long, to: Long): List<SleepSessionEntity>

    @Query("SELECT * FROM sleep_stages WHERE sessionId = :sessionId ORDER BY startMillis")
    suspend fun stagesOf(sessionId: Long): List<SleepStageEntity>

    @Query("DELETE FROM sleep_sessions WHERE origin = :origin AND recordId = :recordId")
    suspend fun deleteRecord(origin: String, recordId: String)

    @Query("SELECT * FROM sleep_sessions ORDER BY startMillis, id")
    suspend fun allSessions(): List<SleepSessionEntity>

    @Query("SELECT * FROM sleep_stages ORDER BY sessionId, startMillis, id")
    suspend fun allStages(): List<SleepStageEntity>

    @Query("DELETE FROM sleep_sessions")
    suspend fun deleteAll()

    @Transaction
    @Query("SELECT * FROM sleep_sessions WHERE epochDay = :epochDay ORDER BY startMillis")
    suspend fun nightsOn(epochDay: Long): List<SleepNight>

    @Transaction
    @Query("SELECT * FROM sleep_sessions ORDER BY startMillis, id")
    suspend fun allNights(): List<SleepNight>

    @Query("SELECT DISTINCT epochDay FROM sleep_sessions WHERE recordId = :recordId")
    suspend fun daysOf(recordId: String): List<Long>

    @Query("DELETE FROM sleep_sessions WHERE recordId = :recordId")
    suspend fun deleteByRecordId(recordId: String)

    @Query("SELECT DISTINCT epochDay FROM sleep_sessions WHERE startMillis >= :from AND startMillis < :to")
    suspend fun daysBetween(from: Long, to: Long): List<Long>

    @Query("DELETE FROM sleep_sessions WHERE startMillis >= :from AND startMillis < :to")
    suspend fun deleteBetween(from: Long, to: Long)
}
