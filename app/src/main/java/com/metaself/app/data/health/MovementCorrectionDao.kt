package com.metaself.app.data.health

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** The backup's three calls, and one day's correction for that day's summary. */
@Dao
interface MovementCorrectionDao {

    @Query("SELECT * FROM movement_corrections ORDER BY epochDay")
    suspend fun all(): List<MovementCorrectionEntity>

    /** One per day: a second for the same day replaces the first. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(corrections: List<MovementCorrectionEntity>)

    @Query("DELETE FROM movement_corrections")
    suspend fun deleteAll()

    @Query("SELECT * FROM movement_corrections WHERE epochDay = :epochDay")
    suspend fun day(epochDay: Long): MovementCorrectionEntity?
}
