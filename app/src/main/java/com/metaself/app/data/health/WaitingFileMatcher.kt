package com.metaself.app.data.health

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.time.Now
import com.metaself.app.domain.movement.FileWorkout
import com.metaself.app.domain.movement.FilledLater
import com.metaself.app.domain.movement.WaitingFile
import com.metaself.app.domain.movement.WaitingLine
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFileMatch
import com.metaself.app.ui.movement.WorkoutFileWording
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** D138: told when a copy from Health Connect has run, so the files waiting for its sessions are matched. */
fun interface SessionsArrived {
    /** Never throws (D8). */
    suspend fun afterCopy()

    companion object {
        val NONE = SessionsArrived { }
    }
}

/** What Your movement asks about the waiting files (D138). */
interface WaitingFiles {
    /** One line per waiting file, oldest first, as they change. */
    val lines: Flow<List<WaitingLine>>

    /** The page opened: files that waited too long are dropped (and logged), and the "filled in" notes given once. */
    suspend fun opened(): List<FilledLater>

    /** "Drop it". */
    suspend fun drop(file: FileWorkout)

    companion object {
        val NONE = object : WaitingFiles {
            override val lines: Flow<List<WaitingLine>> = flowOf(emptyList())
            override suspend fun opened(): List<FilledLater> = emptyList()
            override suspend fun drop(file: FileWorkout) = Unit
        }
    }
}

/**
 * D138: each waiting file matched by D82's rule ([WorkoutFileMatch.matches]) after a copy. One match is filled by
 * D82's fill — only what the session lacks, source FILE — and the file stops waiting; a fill that added something
 * leaves a note for Your movement. No match, or several, keeps it waiting: several are offered on the page. A file
 * waiting more than [WAIT_DAYS] days is dropped and written to Recent problems.
 *
 * **Nothing here throws upwards (D8).** A failure is logged as a workout file's ([ImportWorkoutFile.PROBLEM_KIND])
 * and the file keeps waiting.
 */
@Singleton
class WaitingFileMatcher(
    private val store: WorkoutFileStore,
    private val waiting: WaitingFileStore,
    private val problems: ProblemLog,
    private val now: Now,
    private val zone: () -> ZoneId,
) : SessionsArrived, WaitingFiles {

    @Inject
    constructor(store: WorkoutFileStore, waiting: WaitingFileStore, problems: ProblemLog, now: Now) :
        this(store, waiting, problems, now, { ZoneId.systemDefault() })

    /** One pass at a time: a copy's pass and the page's must not fill the same file twice. */
    private val running = Mutex()

    override suspend fun afterCopy() = guarded {
        running.withLock {
            for (kept in dropExpired()) {
                try {
                    match(kept.file)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    note(ImportWorkoutFile.PROBLEM_KIND, "waiting: ${failure::class.java.simpleName} ${failure.message}")
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val lines: Flow<List<WaitingLine>> = waiting.waiting.mapLatest { files ->
        files.map { kept -> WaitingLine(kept, several(kept.file)) }
    }

    override suspend fun opened(): List<FilledLater> {
        var notes = emptyList<FilledLater>()
        guarded {
            running.withLock { dropExpired() }
            notes = waiting.takeFilled()
        }
        return notes
    }

    override suspend fun drop(file: FileWorkout) = guarded { waiting.drop(file) }

    private suspend fun match(file: FileWorkout) {
        val zone = zone()
        val matches = WorkoutFileMatch.matches(file, store.on(WorkoutFileMatch.days(file, zone)), zone)
        if (matches.size != 1) return
        val filling = store.fill(matches.single().id, file) ?: return
        waiting.drop(file)
        if (filling.added.any) waiting.noteFilled(FilledLater(filling.workout.kind, filling.workout.epochDay))
    }

    /** The files still in time, after the others are dropped and logged. */
    private suspend fun dropExpired(): List<WaitingFile> {
        val nowMillis = now()
        val (expired, kept) = waiting.all().partition { nowMillis - it.sinceMillis > WAIT_DAYS * DAY }
        if (expired.isNotEmpty()) {
            val today = Instant.ofEpochMilli(nowMillis).atZone(zone()).toLocalDate().toEpochDay()
            for (gone in expired) {
                waiting.drop(gone.file)
                note(IMPORT_KIND, WorkoutFileWording.neverFound(gone.file, today))
            }
        }
        return kept
    }

    /** The sessions [file] matches when there are several; a failed read offers none (and is logged). */
    private suspend fun several(file: FileWorkout): List<Workout> = try {
        val zone = zone()
        WorkoutFileMatch.matches(file, store.on(WorkoutFileMatch.days(file, zone)), zone).takeIf { it.size > 1 }.orEmpty()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        note(ImportWorkoutFile.PROBLEM_KIND, "waiting: ${failure::class.java.simpleName} ${failure.message}")
        emptyList()
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            note(ImportWorkoutFile.PROBLEM_KIND, "waiting: ${failure::class.java.simpleName} ${failure.message}")
        }
    }

    private suspend fun note(kind: String, detail: String) = withContext(Dispatchers.IO) { problems.record(kind, detail) }

    companion object {
        /** A choice, not a measurement (D138): how long a file waits for its session. */
        const val WAIT_DAYS = 14L

        /** The problem log's kind for a workout file's outcome (D82, D139): the Movement page's, shared. */
        const val IMPORT_KIND = "import"

        private const val DAY = 86_400_000L
    }
}
