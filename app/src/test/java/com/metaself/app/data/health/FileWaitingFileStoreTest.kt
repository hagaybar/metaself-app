package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.FileWorkout
import com.metaself.app.domain.movement.FilledLater
import com.metaself.app.domain.movement.WaitingFile
import com.metaself.app.domain.movement.WorkoutKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant
import java.time.LocalDateTime

/** D138's store of waiting files, over a real file in a temporary directory. Every figure is invented. */
class FileWaitingFileStoreTest {

    private val walk = FileWorkout(
        writtenAt = LocalDateTime.of(2026, 9, 3, 10, 0),
        instant = Instant.parse("2026-09-03T10:00:00Z"),
        seconds = 1_800,
        distanceM = 3_000.0,
        kcal = 150,
        steps = 4_000,
        avgHeartRate = 100,
        sport = "Walking",
    )
    private val run = FileWorkout(writtenAt = LocalDateTime.of(2026, 9, 2, 7, 30), instant = null, seconds = 1_200, distanceM = 4_000.0)

    @Test
    fun `nothing kept reads as nothing waiting`(@TempDir dir: File) = runTest {
        val store = FileWaitingFileStore(File(dir, "waiting.json"))

        assertThat(store.all()).isEmpty()
        assertThat(store.takeFilled()).isEmpty()
    }

    @Test
    fun `a kept file comes back whole, also to a store opened afterwards`(@TempDir dir: File) = runTest {
        val file = File(dir, "waiting.json")
        FileWaitingFileStore(file).keep(walk, 1_000L)
        FileWaitingFileStore(file).keep(run, 2_000L)

        assertThat(FileWaitingFileStore(file).all()).containsExactly(WaitingFile(walk, 1_000L), WaitingFile(run, 2_000L)).inOrder()
    }

    @Test
    fun `a file kept again keeps the time it started waiting`(@TempDir dir: File) = runTest {
        val store = FileWaitingFileStore(File(dir, "waiting.json"))
        store.keep(walk, 1_000L)
        store.keep(walk, 5_000L)

        assertThat(store.all()).containsExactly(WaitingFile(walk, 1_000L))
    }

    @Test
    fun `a dropped file stops waiting, and dropping one not waiting changes nothing`(@TempDir dir: File) = runTest {
        val store = FileWaitingFileStore(File(dir, "waiting.json"))
        store.keep(walk, 1_000L)
        store.keep(run, 2_000L)

        store.drop(walk)
        store.drop(walk)

        assertThat(store.all()).containsExactly(WaitingFile(run, 2_000L))
        assertThat(store.waiting.first()).containsExactly(WaitingFile(run, 2_000L))
    }

    @Test
    fun `a filled note is given once`(@TempDir dir: File) = runTest {
        val file = File(dir, "waiting.json")
        FileWaitingFileStore(file).noteFilled(FilledLater(WorkoutKind.WALK, TEST_EPOCH_DAY))

        val store = FileWaitingFileStore(file)
        assertThat(store.takeFilled()).containsExactly(FilledLater(WorkoutKind.WALK, TEST_EPOCH_DAY))
        assertThat(store.takeFilled()).isEmpty()
        assertThat(FileWaitingFileStore(file).takeFilled()).isEmpty()
    }

    @Test
    fun `a file that cannot be read is nothing waiting, and is written over by the next keep`(@TempDir dir: File) = runTest {
        val file = File(dir, "waiting.json").apply { writeText("{not json") }
        val store = FileWaitingFileStore(file)

        assertThat(store.all()).isEmpty()
        store.keep(walk, 1_000L)
        assertThat(FileWaitingFileStore(file).all()).containsExactly(WaitingFile(walk, 1_000L))
    }
}
