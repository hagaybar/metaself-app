package com.metaself.app.data.health

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.data.time.Now
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

    @Delete
    suspend fun delete(splits: List<SessionSplitEntity>)
}

/** "These are two sessions" (D92), and the ways back from it. */
interface SessionSplits {

    /**
     * Parts [session]'s lead from each of its other witnesses, for good: it survives the next sync.
     * Returns the pairs written, which Undo hands to [join].
     */
    suspend fun split(session: Workout): List<SessionSplit>

    /** Removes [splits]: Undo, or "Put back together". The sessions are one again from the next read. */
    suspend fun join(splits: Collection<SessionSplit>)

    companion object {
        /** For a screen built without one: nothing may be written. */
        val NONE: SessionSplits = object : SessionSplits {
            override suspend fun split(session: Workout): List<SessionSplit> = error("no store")
            override suspend fun join(splits: Collection<SessionSplit>) = error("no store")
        }
    }
}

/**
 * Each write summarises its days again (`health_days`, D69) **in the same transaction**, as a typed
 * workout's does, so the day's stored workout count and minutes follow the sessions. [TotalsResult.ALL_FAILED]
 * keeps every total already stored: parting a session makes no cross-process call.
 */
class RoomSessionSplits @Inject constructor(
    private val dao: SessionSplitDao,
    private val transaction: DatabaseTransaction,
    private val store: HealthStore,
    private val now: Now,
    private val workouts: WorkoutDao,
) : SessionSplits {

    override suspend fun split(session: Workout): List<SessionSplit> {
        val pairs = SessionWitnesses.splitsOf(session)
        if (pairs.isEmpty()) return pairs
        transaction.run {
            dao.insertAll(pairs.map { it.toEntity() })
            store.summarise(session.witnesses.mapTo(HashSet()) { it.epochDay }, TotalsResult.ALL_FAILED, now())
        }
        return pairs
    }

    override suspend fun join(splits: Collection<SessionSplit>) {
        if (splits.isEmpty()) return
        transaction.run {
            dao.delete(splits.map { it.toEntity() })
            val days = splits.flatMap { listOf(it.firstId, it.secondId) }.distinct()
                .mapNotNullTo(HashSet()) { workouts.byId(it)?.epochDay }
            if (days.isNotEmpty()) store.summarise(days, TotalsResult.ALL_FAILED, now())
        }
    }
}
