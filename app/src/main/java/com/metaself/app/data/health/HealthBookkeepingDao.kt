package com.metaself.app.data.health

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Where copying stands, and which Drive month files are out of date. Never backed up. */
@Dao
interface HealthBookkeepingDao {

    @Query("SELECT * FROM health_sync WHERE kind = :kind")
    suspend fun sync(kind: String): HealthSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSync(sync: HealthSyncEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMonth(month: ArchiveMonthEntity)

    /** Never written, or changed since it was. Oldest first. */
    @Query(
        "SELECT * FROM archive_months WHERE writtenAtMillis IS NULL " +
            "OR writtenAtMillis < changedAtMillis ORDER BY month",
    )
    suspend fun monthsOutOfDate(): List<ArchiveMonthEntity>

    @Query("SELECT * FROM health_sync")
    fun observeSync(): Flow<List<HealthSyncEntity>>

    @Query("DELETE FROM health_sync")
    suspend fun clearSync()

    @Query("SELECT * FROM archive_months WHERE month = :month")
    suspend fun month(month: String): ArchiveMonthEntity?

    /**
     * [month] is written as of [changedAtSeen], UNLESS its changedAt has moved on since — a write
     * built from an earlier read must not hide a change that landed after it.
     */
    @Query(
        "UPDATE archive_months SET writtenAtMillis = :changedAtSeen " +
            "WHERE month = :month AND changedAtMillis = :changedAtSeen",
    )
    suspend fun markWritten(month: String, changedAtSeen: Long)
}
