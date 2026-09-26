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

    /** A record's days, through the `(origin, recordId, sampleIndex)` index. */
    @Query("SELECT DISTINCT epochDay FROM health_readings WHERE origin = :origin AND recordId = :recordId")
    suspend fun daysOf(origin: String, recordId: String): List<Long>

    /**
     * A deleted record's days. Health Connect's deletion gives the id alone, and no index starts with
     * `recordId` (adding one is a schema change), so this scans the table. Accepted: deletions are rare.
     */
    @Query("SELECT DISTINCT epochDay FROM health_readings WHERE recordId = :recordId")
    suspend fun daysOf(recordId: String): List<Long>

    /** Unindexed, as [daysOf] by id alone is; deletions are rare. */
    @Query("DELETE FROM health_readings WHERE recordId = :recordId")
    suspend fun deleteByRecordId(recordId: String)

    /**
     * The records of [kind] with a sample starting in [from, to). [fromDay]..[toDay] must cover the
     * window's days; it is what lets the `(kind, epochDay, startMillis)` index narrow the scan.
     */
    @Query(
        "SELECT DISTINCT origin, recordId FROM health_readings WHERE kind = :kind " +
            "AND epochDay BETWEEN :fromDay AND :toDay AND startMillis >= :from AND startMillis < :to",
    )
    suspend fun recordsOfKindBetween(kind: String, fromDay: Long, toDay: Long, from: Long, to: Long): List<RecordKey>

    /** Samples of [kind] starting in [from, to); [fromDay]..[toDay] as for [recordsOfKindBetween]. */
    @Query(
        "SELECT * FROM health_readings WHERE kind = :kind AND epochDay BETWEEN :fromDay AND :toDay " +
            "AND startMillis >= :from AND startMillis < :to ORDER BY startMillis, sampleIndex",
    )
    suspend fun ofKindBetween(kind: String, fromDay: Long, toDay: Long, from: Long, to: Long): List<HealthReadingEntity>
}

/** One Health Connect record's key. */
data class RecordKey(val origin: String, val recordId: String)
