package com.metaself.app.data.health

import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * An in-memory [MovementRecord]: the test sets [days], [workouts] and [earliest]; each read windows them.
 * [sessionOf] finds the session among [workouts] that holds the id as a witness.
 */
class FakeMovementRecord : MovementRecord, WorkoutSessions {
    val days = MutableStateFlow<List<HealthDay>>(emptyList())
    val workouts = MutableStateFlow<List<Workout>>(emptyList())
    val earliest = MutableStateFlow<Long?>(null)

    override fun observeEarliestDay(): Flow<Long?> = earliest

    override fun observeDays(from: Long, to: Long): Flow<List<HealthDay>> =
        days.map { all -> all.filter { it.epochDay in from..to } }

    override fun observeWorkouts(from: Long, to: Long): Flow<List<Workout>> =
        workouts.map { all -> all.filter { it.epochDay in from..to } }

    override suspend fun sessionOf(workoutId: Long): Workout? = workouts.value.firstOrNull { workoutId in it.witnessIds }
}
