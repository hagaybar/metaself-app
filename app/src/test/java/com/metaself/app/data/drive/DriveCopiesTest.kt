package com.metaself.app.data.drive

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.ArchiveWrite
import com.metaself.app.data.health.ReadingsArchive
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.ui.RecordingProblemLog
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.ZoneOffset

/** D139: the daily copy to Drive and the month archive after it. The time is invented: 06:58 UTC on TEST_EPOCH_DAY. */
class DriveCopiesTest {

    private val problems = RecordingProblemLog()
    private var archived = 0
    private val archive = object : ReadingsArchive {
        override suspend fun monthsInDrive(): Int? = null
        override suspend fun restoreAll() = null
        override suspend fun writeOutOfDate(): ArchiveWrite {
            archived++
            return ArchiveWrite.NothingDue
        }
    }
    private val today = LocalDate.ofEpochDay(TEST_EPOCH_DAY)
    private val nowMillis = TEST_EPOCH_DAY * 86_400_000L + (6 * 60 + 58) * 60_000L

    private fun copies(outcome: () -> DriveOutcome) = DriveCopies({ _, _ -> outcome() }, archive, problems) { ZoneOffset.UTC }

    @Test
    fun `a backup that fails for want of a connection is one line, and the archive waits for the next`() = runTest {
        copies { DriveOutcome.Failed("no connection", noConnection = true) }.write(today, nowMillis)

        assertThat(problems.recorded.map { it.kind to it.detail })
            .containsExactly("drive" to "no connection at 06:58; the next backup tries again")
        assertThat(archived).isEqualTo(0)
    }

    @Test
    fun `any other failure adds no line here, and the archive is still tried`() = runTest {
        copies { DriveOutcome.Failed("Drive refused the upload") }.write(today, nowMillis)

        assertThat(problems.recorded).isEmpty()
        assertThat(archived).isEqualTo(1)
    }

    @Test
    fun `a written backup is followed by the archive`() = runTest {
        copies { DriveOutcome.Written("metaself-2026-09-03.json", 0) }.write(today, nowMillis)

        assertThat(archived).isEqualTo(1)
        assertThat(problems.recorded).isEmpty()
    }

    @Test
    fun `a daily copy that throws still leaves the archive tried`() = runTest {
        copies { throw IllegalStateException("broken") }.write(today, nowMillis)

        assertThat(archived).isEqualTo(1)
    }
}

/** D139: which failures are for want of a connection. */
class DriveConnectionTest {

    @Test
    fun `a host that cannot be resolved, or a connection that cannot be made, is no connection`() {
        assertThat(DriveConnection.lost(UnknownHostException("www.googleapis.com"))).isTrue()
        assertThat(DriveConnection.lost(ConnectException("Network is unreachable"))).isTrue()
        assertThat(DriveConnection.lost(IOException("wrapped", UnknownHostException("oauth2.googleapis.com")))).isTrue()
    }

    @Test
    fun `anything else is not`() {
        assertThat(DriveConnection.lost(SocketTimeoutException("timeout"))).isFalse()
        assertThat(DriveConnection.lost(IOException("unexpected end of stream"))).isFalse()
        assertThat(DriveConnection.lost(IllegalStateException("broken"))).isFalse()
    }
}
