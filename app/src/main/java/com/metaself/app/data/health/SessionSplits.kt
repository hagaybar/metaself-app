package com.metaself.app.data.health

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.metaself.app.domain.movement.SessionSplit
import com.metaself.app.domain.movement.SessionWitnesses
import com.metaself.app.domain.movement.Workout
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Two workouts the owner said are two sessions (D92), lower id first; unique as a pair.
 *
 * **No foreign key** to `workouts`: a typed witness deleted and put back by Undo returns under its own id
 * and is still split from the session it was parted from; a split naming a workout that is gone groups
 * nothing, and the backup leaves it out.
 */
@Entity(tableName = "session_splits", primaryKeys = ["firstWorkoutId", "secondWorkoutId"])
data class SessionSplitEntity(val firstWorkoutId: Long, val secondWorkoutId: Long)

/** Null for a row that is not lower id first — never written by this app, and never allowed to break a read. */
fun SessionSplitEntity.toSplit(): SessionSplit? =
    if (firstWorkoutId < secondWorkoutId) SessionSplit(firstWorkoutId, secondWorkoutId) else null

fun SessionSplit.toEntity(): SessionSplitEntity = SessionSplitEntity(firstId, secondId)

@Dao
interface SessionSplitDao {

    @Query("SELECT * FROM session_splits ORDER BY firstWorkoutId, secondWorkoutId")
    fun observeAll(): Flow<List<SessionSplitEntity>>

    @Query("SELECT * FROM session_splits ORDER BY firstWorkoutId, secondWorkoutId")
    suspend fun all(): List<SessionSplitEntity>

    /** A pair already split stays as it is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(splits: List<SessionSplitEntity>)

    @Query("DELETE FROM session_splits")
    suspend fun deleteAll()
}

/** "These are two sessions" (D92). */
interface SessionSplits {

    /** Parts [session]'s lead from each of its other witnesses, for good: it survives the next sync. */
    suspend fun split(session: Workout)

    companion object {
        /** For a screen built without one: nothing may be written. */
        val NONE: SessionSplits = object : SessionSplits {
            override suspend fun split(session: Workout) = error("no store")
        }
    }
}

class RoomSessionSplits @Inject constructor(private val dao: SessionSplitDao) : SessionSplits {
    override suspend fun split(session: Workout) {
        val pairs = SessionWitnesses.splitsOf(session)
        if (pairs.isNotEmpty()) dao.insertAll(pairs.map { it.toEntity() })
    }
}
