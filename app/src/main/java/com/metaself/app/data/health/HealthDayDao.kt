package com.metaself.app.data.health

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Daily summaries. A day is recomputed whole, so writing one replaces it (the day is the key). */
@Dao
interface HealthDayDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(day: HealthDayEntity)

    @Query("SELECT * FROM health_days WHERE epochDay = :epochDay")
    suspend fun day(epochDay: Long): HealthDayEntity?

    @Query("SELECT * FROM health_days WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    fun observeBetween(from: Long, to: Long): Flow<List<HealthDayEntity>>

    @Query("SELECT * FROM health_days ORDER BY epochDay")
    suspend fun all(): List<HealthDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(days: List<HealthDayEntity>)

    @Query("DELETE FROM health_days")
    suspend fun deleteAll()

    @Query("DELETE FROM health_days WHERE epochDay = :epochDay")
    suspend fun delete(epochDay: Long)

    @Query("SELECT COUNT(*) FROM health_days")
    fun observeCount(): Flow<Int>

    @Query("SELECT MIN(epochDay) FROM health_days")
    fun observeEarliest(): Flow<Long?>
}
