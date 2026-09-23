package com.metaself.app.data.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileProblemLogTest {

    @Test
    fun `nothing has gone wrong yet`(@TempDir dir: File) {
        assertThat(logIn(dir).recent()).isEmpty()
    }

    @Test
    fun `a problem is recorded and read back`(@TempDir dir: File) {
        val log = logIn(dir)

        log.record("estimate refused", "Incorrect API key provided")

        val problem = log.recent().single()
        assertThat(problem.kind).isEqualTo("estimate refused")
        assertThat(problem.detail).isEqualTo("Incorrect API key provided")
        assertThat(problem.whenMillis).isGreaterThan(0L)
    }

    @Test
    fun `the newest is first, because it is the one being looked for`(@TempDir dir: File) {
        val log = logIn(dir)

        log.record("first", "one")
        log.record("second", "two")

        assertThat(log.recent().map { it.kind }).containsExactly("second", "first").inOrder()
    }

    @Test
    fun `a detail spanning several lines stays one entry`(@TempDir dir: File) {
        val log = logIn(dir)

        log.record("crash", "java.lang.SecurityException\n\tat some.package.Thing\n\tat another")

        assertThat(log.recent()).hasSize(1)
        assertThat(log.recent().single().detail).doesNotContain("\n")
    }

    @Test
    fun `it does not grow for ever`(@TempDir dir: File) {
        val log = logIn(dir)

        repeat(80) { log.record("noise", "entry $it") }

        assertThat(log.recent()).hasSize(50)
        assertThat(log.recent().first().detail).isEqualTo("entry 79")
    }

    @Test
    fun `it survives a file it cannot make sense of`(@TempDir dir: File) {
        File(dir, "problems.log").writeText("this is not a log entry\nnor is this")

        assertThat(logIn(dir).recent()).isEmpty()
    }

    @Test
    fun `it can be emptied`(@TempDir dir: File) {
        val log = logIn(dir)
        log.record("something", "happened")

        log.clear()

        assertThat(log.recent()).isEmpty()
    }

    private fun logIn(dir: File) = FileProblemLog(File(dir, "problems.log"))
}
