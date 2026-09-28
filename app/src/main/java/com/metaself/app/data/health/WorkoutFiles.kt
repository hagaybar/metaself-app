package com.metaself.app.data.health

import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.movement.FileWorkout
import com.metaself.app.domain.movement.ImportOutcome
import com.metaself.app.domain.movement.TcxRead
import com.metaself.app.domain.movement.TcxReader
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFileMatch
import com.metaself.app.domain.movement.WorkoutFileRefusal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import javax.inject.Inject

/** What importing a workout file (D82) reads and writes. The real one is Room; tests use a fake. */
interface WorkoutFileStore {
    /** Every stored workout on [days], hidden and typed ones included; the matcher chooses. */
    suspend fun on(days: Set<Long>): List<Workout>

    /**
     * Fills workout [id] from [file] ([WorkoutFileMatch.fill]) in one transaction, from the row as it
     * is at that moment; null when it is gone.
     */
    suspend fun fill(id: Long, file: FileWorkout): WorkoutFileMatch.Filling?

    /** Stores [workout] as a typed workout and summarises its day; returns its id. */
    suspend fun add(workout: Workout): Long
}

/**
 * Over [WorkoutDao] and [TypedWorkouts]. A fill changes a workout's figures, not its count or
 * minutes, so its day is not summarised again; an added workout is, by [TypedWorkouts.log].
 */
class RoomWorkoutFileStore @Inject constructor(
    private val workouts: WorkoutDao,
    private val transaction: DatabaseTransaction,
    private val typed: TypedWorkouts,
) : WorkoutFileStore {

    override suspend fun on(days: Set<Long>): List<Workout> =
        days.sorted().flatMap { workouts.onDay(it) }.map { it.toWorkout() }

    override suspend fun fill(id: Long, file: FileWorkout): WorkoutFileMatch.Filling? {
        var filling: WorkoutFileMatch.Filling? = null
        transaction.run {
            val row = workouts.byId(id) ?: return@run
            // Hidden since it was offered: a file never fills a session it may not (D82).
            if (WorkoutFileMatch.candidates(listOf(row.toWorkout())).isEmpty()) return@run
            val result = WorkoutFileMatch.fill(row.toWorkout(), file)
            if (result.added.any) {
                val now = result.workout
                workouts.update(
                    row.copy(
                        distanceM = now.distanceM,
                        distanceSource = now.distanceSource?.name,
                        steps = now.steps,
                        stepsSource = now.stepsSource?.name,
                        energyKcal = now.energyKcal,
                        energySource = now.energySource.name,
                    ),
                )
            }
            filling = result
        }
        return filling
    }

    override suspend fun add(workout: Workout): Long = typed.log(workout)
}

/** Importing a workout file (D82), as the Movement screen asks for it. */
interface WorkoutFileImporter {
    suspend fun import(uri: String): ImportOutcome
    suspend fun choose(file: FileWorkout, id: Long): ImportOutcome
    suspend fun add(file: FileWorkout): ImportOutcome

    companion object {
        /** For a screen built without one: every file is refused as unreadable. */
        val NONE = object : WorkoutFileImporter {
            override suspend fun import(uri: String) = ImportOutcome.Refused(WorkoutFileRefusal.UNREADABLE)
            override suspend fun choose(file: FileWorkout, id: Long) = ImportOutcome.Failed
            override suspend fun add(file: FileWorkout) = ImportOutcome.Failed
        }
    }
}

/**
 * Importing a workout file (D82): read it, find the one stored workout it belongs to, and fill in
 * what that workout lacks — or say why not, offer to add it, or list the ones to choose from.
 *
 * **Nothing here throws upwards (D8).** A file that cannot be opened is [WorkoutFileRefusal.UNREADABLE]
 * and a failed read or write of the store is [ImportOutcome.Failed]; both are logged (kind
 * [PROBLEM_KIND]). A file refused for its content is said, not logged: nothing failed.
 */
class ImportWorkoutFile(
    private val files: WorkoutFileSource,
    private val store: WorkoutFileStore,
    private val problems: ProblemLog,
    private val zone: () -> ZoneId,
) : WorkoutFileImporter {

    @Inject
    constructor(files: WorkoutFileSource, store: WorkoutFileStore, problems: ProblemLog) :
        this(files, store, problems, { ZoneId.systemDefault() })

    override suspend fun import(uri: String): ImportOutcome {
        val text = try {
            files.read(uri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            note("opening: ${failure::class.java.simpleName} ${failure.message}")
            return ImportOutcome.Refused(WorkoutFileRefusal.UNREADABLE)
        }
        return guarded {
            // Parsed off the main thread: a file may be up to ContentWorkoutFileSource.MAX_BYTES of XML.
            val file = when (text) {
                is FileText.Refused -> return@guarded ImportOutcome.Refused(text.reason)
                is FileText.Text -> when (val read = withContext(Dispatchers.Default) { TcxReader.read(text.text) }) {
                    is TcxRead.Refused -> return@guarded ImportOutcome.Refused(read.reason)
                    is TcxRead.Read -> read.workout
                }
            }
            // One read of the file's days serves both the match and, failing one, D91's same-kind offer.
            val zone = zone()
            val stored = store.on(WorkoutFileMatch.days(file, zone))
            matchAndFill(file, stored, zone) ?: ImportOutcome.NoMatch(file, WorkoutFileMatch.sameKind(file, stored, zone))
        }
    }

    /**
     * One of [ImportOutcome.Several]'s workouts, or of [ImportOutcome.NoMatch.sameKind] (D91), chosen.
     * Checked again at the tap: a session hidden or gone since the offer is not filled, and the file's
     * same-kind sessions are offered again as they now are.
     */
    override suspend fun choose(file: FileWorkout, id: Long): ImportOutcome = guarded {
        val zone = zone()
        val stored = store.on(WorkoutFileMatch.days(file, zone))
        if (WorkoutFileMatch.candidates(stored).any { it.id == id }) {
            fillOne(file, id)
        } else {
            ImportOutcome.NoMatch(file, WorkoutFileMatch.sameKind(file, stored, zone))
        }
    }

    /**
     * "Add it as a workout". Matched once more first: a session that arrived since, or a second
     * press, is filled rather than doubled.
     */
    override suspend fun add(file: FileWorkout): ImportOutcome = guarded {
        val zone = zone()
        matchAndFill(file, store.on(WorkoutFileMatch.days(file, zone)), zone) ?: run {
            val workout = WorkoutFileMatch.asWorkout(file, zone)
            ImportOutcome.AddedWorkout(workout.copy(id = store.add(workout)))
        }
    }

    /** Null when nothing of [stored] matches. */
    private suspend fun matchAndFill(file: FileWorkout, stored: List<Workout>, zone: ZoneId): ImportOutcome? {
        val matches = WorkoutFileMatch.matches(file, stored, zone)
        return when (matches.size) {
            0 -> null
            1 -> fillOne(file, matches.single().id)
            else -> ImportOutcome.Several(file, matches)
        }
    }

    private suspend fun fillOne(file: FileWorkout, id: Long): ImportOutcome {
        val filling = store.fill(id, file) ?: return ImportOutcome.NoMatch(file)
        return if (filling.added.any) {
            ImportOutcome.Filled(filling.workout, filling.added, file.metres)
        } else {
            ImportOutcome.Unchanged(filling.workout, file.metres)
        }
    }

    private suspend fun guarded(block: suspend () -> ImportOutcome): ImportOutcome = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        note("${failure::class.java.simpleName} ${failure.message}")
        ImportOutcome.Failed
    }

    private suspend fun note(detail: String) = withContext(Dispatchers.IO) {
        problems.record(kind = PROBLEM_KIND, detail = detail)
    }

    companion object {
        const val PROBLEM_KIND = "workout file"
    }
}
