package com.metaself.app.data.health

import com.metaself.app.domain.movement.FileWorkout
import com.metaself.app.domain.movement.FilledLater
import com.metaself.app.domain.movement.WaitingFile
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFileMatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** The stored sessions a workout file is matched against (D82), in memory; [failing] makes every write throw. */
class FakeWorkoutFileStore : WorkoutFileStore {
    val rows = mutableListOf<Workout>()
    val daysAsked = mutableListOf<Set<Long>>()
    var failing = false

    override suspend fun on(days: Set<Long>): List<Workout> {
        daysAsked += days
        return rows.filter { it.epochDay in days }
    }

    override suspend fun fill(id: Long, file: FileWorkout): WorkoutFileMatch.Filling? {
        if (failing) throw IllegalStateException("disk full")
        val index = rows.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return null
        val filling = WorkoutFileMatch.fill(rows[index], file)
        rows[index] = filling.workout
        return filling
    }

    override suspend fun add(workout: Workout): Long {
        if (failing) throw IllegalStateException("disk full")
        val id = (rows.maxOfOrNull { it.id } ?: 0) + 100
        rows += workout.copy(id = id)
        return id
    }
}

/** D138's waiting files, in memory; [failing] makes every write throw. */
class InMemoryWaitingFileStore : WaitingFileStore {
    private val files = MutableStateFlow<List<WaitingFile>>(emptyList())
    val filled = mutableListOf<FilledLater>()
    var failing = false

    override val waiting: Flow<List<WaitingFile>> = files

    override suspend fun all(): List<WaitingFile> = files.value

    override suspend fun keep(file: FileWorkout, sinceMillis: Long) {
        if (failing) throw IllegalStateException("disk full")
        if (files.value.none { it.file == file }) files.value = files.value + WaitingFile(file, sinceMillis)
    }

    override suspend fun drop(file: FileWorkout) {
        if (failing) throw IllegalStateException("disk full")
        files.value = files.value.filterNot { it.file == file }
    }

    override suspend fun noteFilled(filled: FilledLater) {
        this.filled += filled
    }

    override suspend fun takeFilled(): List<FilledLater> = filled.toList().also { filled.clear() }
}
