package com.metaself.app.data.health

import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.data.time.Now
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFigureSource
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

    /**
     * The typed sessions of [from]..[to], hidden ones included: those a typed workout (or one added from
     * a workout file) leads (D92). A typed workout that is a witness of a synced session is that
     * session now, and is not among them — so the day's typed reading (D77) counts combined sessions.
     */
    fun observe(from: Long, to: Long): Flow<List<Workout>>

    /** Stores [workout] as a new typed workout; returns its id. */
    suspend fun log(workout: Workout): Long

    /**
     * Puts back a typed workout [delete] removed, as it was, under its own id while that id is free —
     * so a trainer review of it (D88), which names it by id, is its own again — and under a new one
     * otherwise. Returns the id it came back under.
     */
    suspend fun restore(workout: Workout): Long

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
            override suspend fun restore(workout: Workout): Long = error("no store")
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
    private val record: MovementRecord,
) : TypedWorkouts {

    override fun observe(from: Long, to: Long): Flow<List<Workout>> =
        record.observeWorkouts(from, to).map { sessions -> sessions.filter { it.source == WorkoutSource.TYPED } }

    override suspend fun log(workout: Workout): Long {
        var id = 0L
        transaction.run {
            id = workouts.insert(workout.toTypedEntity().copy(id = 0))
            summariseAgain(workout.epochDay)
        }
        return id
    }

    /**
     * In one transaction with the id check, so nothing can take the id between the two. The workouts
     * table's ids are AUTOINCREMENT, so a deleted row's id is never handed to another; only a backup
     * restored since the delete (which renumbers every workout) can have filled it.
     */
    override suspend fun restore(workout: Workout): Long {
        var id = 0L
        transaction.run {
            val free = workout.id > 0 && workouts.byId(workout.id) == null
            id = workouts.insert(workout.toTypedEntity().copy(id = if (free) workout.id else 0))
            summariseAgain(workout.epochDay)
        }
        return id
    }

    /**
     * The row is read by its id, never searched for on [workout]'s day: the day it is summarised under
     * is the STORED one. The day is kept, so the row never moves and only that day needs summarising.
     *
     * D82: the sheet knows nothing of a workout file, so what a file gave is kept here — its steps
     * always; its distance and calories, with the file as their source, while the figure is the one
     * stored. A distance the owner changed is his own (TYPED); calories he changed are TYPED already.
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
                ).keepingFileFigures(stored),
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

    private fun WorkoutEntity.keepingFileFigures(stored: WorkoutEntity): WorkoutEntity {
        val fileDistance = stored.distanceSource == FILE
        return copy(
            distanceSource = when {
                fileDistance && distanceM == stored.distanceM -> FILE
                fileDistance && distanceM != null -> WorkoutFigureSource.TYPED.name
                else -> distanceSource
            },
            energySource = if (stored.energySource == FILE && energyKcal == stored.energyKcal) FILE else energySource,
            steps = stored.steps,
            stepsSource = stored.stepsSource,
        )
    }

    private suspend fun summariseAgain(epochDay: Long) {
        store.summarise(setOf(epochDay), TotalsResult.ALL_FAILED, now())
    }

    private companion object {
        val TYPED = WorkoutSource.TYPED.name
        val FILE = WorkoutFigureSource.FILE.name
    }
}
