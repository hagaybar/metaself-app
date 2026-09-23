package com.metaself.app.data.weight

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One weighing.
 *
 * The day is the primary key, which is how "one reading per day, the later one wins" becomes a
 * property of the schema rather than a promise made by the code above it.
 */
@Entity(tableName = "weights")
data class WeightEntity(
    @PrimaryKey val epochDay: Long,
    val kg: Double,
)
