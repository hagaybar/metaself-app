package com.metaself.app.data.health

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Raw readings. Primitives only: replacing a record's rows is a delete and an insert inside one
 * transaction, which the store (phase 2) owns.
 */
@Dao
interface HealthReadingDao {

    /** Refuses a sample already stored: `(origin, recordId, sampleIndex)` is unique. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(readings: List<HealthReadingEntity>)

    @Query("DELETE FROM health_readings WHERE origin = :origin AND recordId = :recordId")
    suspend fun deleteRecord(origin: String, recordId: String)

    @Query("SELECT * FROM health_readings WHERE epochDay = :epochDay ORDER BY kind, startMillis, sampleIndex")
    suspend fun onDay(epochDay: Long): List<HealthReadingEntity>

    @Query(
        "SELECT * FROM health_readings WHERE kind = :kind AND epochDay = :epochDay " +
            "ORDER BY startMillis, sampleIndex",
    )
    suspend fun ofKindOnDay(kind: String, epochDay: Long): List<HealthReadingEntity>

    @Query("SELECT DISTINCT epochDay FROM health_readings WHERE recordId = :recordId")
    suspend fun daysOf(recordId: String): List<Long>

    @Query("DELETE FROM health_readings WHERE recordId = :recordId")
    suspend fun deleteByRecordId(recordId: String)

    @Query("SELECT DISTINCT epochDay FROM health_readings WHERE kind = :kind AND startMillis >= :from AND startMillis < :to")
    suspend fun daysOfKindBetween(kind: String, from: Long, to: Long): List<Long>

    @Query("DELETE FROM health_readings WHERE kind = :kind AND startMillis >= :from AND startMillis < :to")
    suspend fun deleteKindBetween(kind: String, from: Long, to: Long)

    @Query(
        "SELECT * FROM health_readings WHERE kind = :kind AND startMillis >= :from AND startMillis < :to " +
            "ORDER BY startMillis, sampleIndex",
    )
    suspend fun ofKindBetween(kind: String, from: Long, to: Long): List<HealthReadingEntity>
}
