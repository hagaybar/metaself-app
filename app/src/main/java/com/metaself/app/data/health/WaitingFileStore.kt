package com.metaself.app.data.health

import com.metaself.app.domain.movement.FileWorkout
import com.metaself.app.domain.movement.FilledLater
import com.metaself.app.domain.movement.WaitingFile
import com.metaself.app.domain.movement.WorkoutKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDateTime

/**
 * D138: the workout files waiting for their session, and the "filled in" notes Your movement shows once. Kept on the
 * phone only — never in a backup.
 */
interface WaitingFileStore {
    /** Every waiting file, oldest first, as it changes. */
    val waiting: Flow<List<WaitingFile>>

    suspend fun all(): List<WaitingFile>

    /** [file] waits from [sinceMillis]; a file already waiting keeps the time it started. */
    suspend fun keep(file: FileWorkout, sinceMillis: Long)

    /** [file] stops waiting; nothing changes when it was not. */
    suspend fun drop(file: FileWorkout)

    suspend fun noteFilled(filled: FilledLater)

    /** The notes not yet given, which are then gone. */
    suspend fun takeFilled(): List<FilledLater>
}

/**
 * One small JSON file in the app's own files directory. Not the settings DataStore, which a restore snapshots and
 * puts back whole, and not the database, so nothing here reaches a backup and no schema changes. A file that cannot
 * be read is taken as nothing waiting (a waiting file is a convenience; the file itself can be shared again), and the
 * next write replaces it. Written to a neighbour first and renamed over, so a write cut short leaves the last one.
 */
class FileWaitingFileStore(private val file: File) : WaitingFileStore {

    private val lock = Mutex()
    private val kept = MutableStateFlow<Kept?>(null)

    override val waiting: Flow<List<WaitingFile>> = flow {
        loaded()
        emitAll(kept)
    }.map { it?.files.orEmpty() }

    override suspend fun all(): List<WaitingFile> = loaded().files

    override suspend fun keep(file: FileWorkout, sinceMillis: Long) = change { now ->
        if (now.files.any { it.file == file }) now else now.copy(files = now.files + WaitingFile(file, sinceMillis))
    }

    override suspend fun drop(file: FileWorkout) = change { now -> now.copy(files = now.files.filterNot { it.file == file }) }

    override suspend fun noteFilled(filled: FilledLater) = change { now -> now.copy(filled = now.filled + filled) }

    override suspend fun takeFilled(): List<FilledLater> {
        var taken = emptyList<FilledLater>()
        change { now ->
            taken = now.filled
            now.copy(filled = emptyList())
        }
        return taken
    }

    private suspend fun loaded(): Kept = lock.withLock { kept.value ?: read().also { kept.value = it } }

    private suspend fun change(edit: (Kept) -> Kept) {
        lock.withLock {
            val before = kept.value ?: read()
            val after = edit(before)
            if (after != before) write(after)
            kept.value = after
        }
    }

    private suspend fun read(): Kept = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@runCatching Kept()
            val stored = JSON.decodeFromString<Stored>(file.readText())
            Kept(stored.files.map { it.toWaiting() }, stored.filled.map { FilledLater(WorkoutKind.parse(it.kind), it.epochDay) })
        }.getOrDefault(Kept())
    }

    private suspend fun write(now: Kept) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val next = File(file.parentFile, file.name + ".new")
        next.writeText(JSON.encodeToString(Stored(now.files.map(::stored), now.filled.map { StoredFilled(it.kind.name, it.epochDay) })))
        if (!next.renameTo(file)) {
            file.delete()
            check(next.renameTo(file)) { "waiting files not written" }
        }
    }

    private data class Kept(val files: List<WaitingFile> = emptyList(), val filled: List<FilledLater> = emptyList())

    @Serializable
    private data class Stored(val files: List<StoredFile> = emptyList(), val filled: List<StoredFilled> = emptyList())

    @Serializable
    private data class StoredFile(
        val writtenAt: String,
        val instant: String? = null,
        val seconds: Int,
        val distanceM: Double? = null,
        val kcal: Int? = null,
        val steps: Int? = null,
        val avgHeartRate: Int? = null,
        val sport: String? = null,
        val since: Long,
    ) {
        fun toWaiting() = WaitingFile(
            FileWorkout(
                writtenAt = LocalDateTime.parse(writtenAt),
                instant = instant?.let(Instant::parse),
                seconds = seconds,
                distanceM = distanceM,
                kcal = kcal,
                steps = steps,
                avgHeartRate = avgHeartRate,
                sport = sport,
            ),
            since,
        )
    }

    @Serializable
    private data class StoredFilled(val kind: String, val epochDay: Long)

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }

        fun stored(waiting: WaitingFile) = with(waiting.file) {
            StoredFile(writtenAt.toString(), instant?.toString(), seconds, distanceM, kcal, steps, avgHeartRate, sport, waiting.sinceMillis)
        }
    }
}
