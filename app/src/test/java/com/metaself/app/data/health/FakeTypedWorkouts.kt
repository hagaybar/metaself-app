package com.metaself.app.data.health

import com.metaself.app.domain.movement.Workout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Typed workouts in memory, remembering what was asked. [failing] makes every write throw it; a change
 * or delete of an id not held answers false, as the real store does. [beforeWrite] runs first, ahead
 * of [failing]: a test that awaits something inside it can hold a write open to see what happens while
 * it is still in flight.
 */
class FakeTypedWorkouts(initial: List<Workout> = emptyList()) : TypedWorkouts {

    val workouts = MutableStateFlow(initial)
    val logged = mutableListOf<Workout>()
    val changed = mutableListOf<Workout>()
    val deleted = mutableListOf<Workout>()
    val restored = mutableListOf<Workout>()
    var failing: Exception? = null
    var beforeWrite: suspend () -> Unit = {}
    private var nextId = 100L

    override fun observe(from: Long, to: Long): Flow<List<Workout>> =
        workouts.map { all -> all.filter { it.epochDay in from..to } }

    override suspend fun log(workout: Workout): Long {
        beforeWrite()
        failing?.let { throw it }
        val id = nextId++
        logged += workout
        workouts.value = workouts.value + workout.copy(id = id)
        return id
    }

    override suspend fun restore(workout: Workout): Long {
        beforeWrite()
        failing?.let { throw it }
        restored += workout
        val id = if (workouts.value.none { it.id == workout.id }) workout.id else nextId++
        workouts.value = workouts.value + workout.copy(id = id)
        return id
    }

    override suspend fun change(workout: Workout): Boolean {
        beforeWrite()
        failing?.let { throw it }
        changed += workout
        if (workouts.value.none { it.id == workout.id }) return false
        workouts.value = workouts.value.map { if (it.id == workout.id) workout else it }
        return true
    }

    override suspend fun delete(workout: Workout): Boolean {
        beforeWrite()
        failing?.let { throw it }
        deleted += workout
        if (workouts.value.none { it.id == workout.id }) return false
        workouts.value = workouts.value.filterNot { it.id == workout.id }
        return true
    }
}
