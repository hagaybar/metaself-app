package com.metaself.app.data.weight

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {

    /** Oldest first, so the trend can be walked forwards without sorting again. */
    @Query("SELECT * FROM weights ORDER BY epochDay ASC")
    fun observeAll(): Flow<List<WeightEntity>>

    /** Weighing again on a day replaces that day's reading — the primary key does the work. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reading: WeightEntity)

    @Query("SELECT * FROM weights ORDER BY epochDay ASC")
    suspend fun all(): List<WeightEntity>

    @Query("DELETE FROM weights")
    suspend fun deleteAll()

    @Query("DELETE FROM weights WHERE epochDay = :epochDay")
    suspend fun delete(epochDay: Long)
}
