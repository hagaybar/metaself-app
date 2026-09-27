package com.metaself.app.data.health

import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.data.time.Now
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The workouts the owner types in (D76): observed for the day's third reading (D77), and logged,
 * changed and deleted from the Movement screen's sheet. A synced workout is never changed or deleted
 * through here — its numbers are the band's.
 */
interface TypedWorkouts {

    /** The typed workouts of [from]..[to], hidden ones included. */
    fun observe(from: Long, to: Long): Flow<List<Workout>>

    /** Stores [workout] as a new typed workout; returns its id. */
    suspend fun log(workout: Workout): Long

    /**
     * Replaces the typed workout stored under [workout]'s id, keeping its day, start and hidden flag.
     * Found by id alone, so a [workout] carrying a different day still reaches its row. False when
     * nothing was changed: no row has that id, or the row is synced.
     */
    suspend fun change(workout: Workout): Boolean

    /**
     * Deletes the typed workout stored under [workout]'s id. False when nothing was deleted: no row has
     * that id, or the row is synced.
     */
    suspend fun delete(workout: Workout): Boolean

    companion object {
        /** For a caller that only reads, in a test: nothing typed, and nothing may be written. */
        val NONE: TypedWorkouts = object : TypedWorkouts {
            override fun observe(from: Long, to: Long): Flow<List<Workout>> = flowOf(emptyList())
            override suspend fun log(workout: Workout): Long = error("no store")
            override suspend fun change(workout: Workout): Boolean = error("no store")
            override suspend fun delete(workout: Workout): Boolean = error("no store")
        }
    }
}

/**
 * Over [WorkoutDao], with no schema change. After each write the day's stored summary is worked out again
 * (`health_days`, D69) through [HealthStore.summarise], **in the same transaction**, so the day's
 * workout count never disagrees with its workouts. [TotalsResult.ALL_FAILED] keeps every total already
 * stored from Health Connect: saving a workout makes no cross-process call.
 */
class RoomTypedWorkouts @Inject constructor(
    private val workouts: WorkoutDao,
    private val transaction: DatabaseTransaction,
    private val store: HealthStore,
    private val now: Now,
) : TypedWorkouts {

    override fun observe(from: Long, to: Long): Flow<List<Workout>> =
        workouts.observeBetween(from, to).map { rows ->
            rows.filter { it.source == TYPED }.map { it.toWorkout() }
        }

    override suspend fun log(workout: Workout): Long {
        var id = 0L
        transaction.run {
            id = workouts.insert(workout.toTypedEntity().copy(id = 0))
            summariseAgain(workout.epochDay)
        }
        return id
    }

    /**
     * The row is read by its id, never searched for on [workout]'s day: the day it is summarised under
     * is the STORED one. The day is kept, so the row never moves and only that day needs summarising.
     */
    override suspend fun change(workout: Workout): Boolean {
        var changed = false
        transaction.run {
            val stored = workouts.byId(workout.id)?.takeIf { it.source == TYPED } ?: return@run
            workouts.update(
                workout.toTypedEntity().copy(
                    id = stored.id,
                    epochDay = stored.epochDay,
                    startedAtMillis = stored.startedAtMillis,
                    hidden = stored.hidden,
                    avgHeartRate = stored.avgHeartRate,
                    maxHeartRate = stored.maxHeartRate,
                    zoneSeconds = stored.zoneSeconds,
                    zoneMaxSource = stored.zoneMaxSource,
                ),
            )
            summariseAgain(stored.epochDay)
            changed = true
        }
        return changed
    }

    /** Read by id, as [change] is; the stored row's day is the one summarised again. */
    override suspend fun delete(workout: Workout): Boolean {
        var deleted = false
        transaction.run {
            val stored = workouts.byId(workout.id)?.takeIf { it.source == TYPED } ?: return@run
            workouts.deleteTyped(stored.id)
            summariseAgain(stored.epochDay)
            deleted = true
        }
        return deleted
    }

    private suspend fun summariseAgain(epochDay: Long) {
        store.summarise(setOf(epochDay), TotalsResult.ALL_FAILED, now())
    }

    private companion object {
        val TYPED = WorkoutSource.TYPED.name
    }
}
