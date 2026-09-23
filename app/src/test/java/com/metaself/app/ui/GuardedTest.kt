package com.metaself.app.ui

import androidx.lifecycle.ViewModel
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.Problem
import com.metaself.app.data.diagnostics.ProblemLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The one net under every action a screen starts.
 *
 * Pure but for the view model's own scope, so JUnit 5. An exception that got past the guard would
 * fail these tests on its own: `runTest` reports any coroutine's uncaught exception when it ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuardedTest {

    private val dispatcher = StandardTestDispatcher()
    private val problems = RecordingProblemLog()
    private val model = object : ViewModel() {}
    private val refused = mutableListOf<Throwable>()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a failure is written down and handed to the screen, and goes no further`() = runTest {
        val failure = IllegalStateException("disk full")

        model.guarded(problems, onRefused = { refused += it }) { throw failure }
        advanceUntilIdle()

        assertThat(refused).containsExactly(failure)
        val problem = problems.recorded.single()
        assertThat(problem.kind).isEqualTo("refused")
        assertThat(problem.detail).startsWith("java.lang.IllegalStateException: disk full at ")
        assertThat(problem.detail).isEqualTo(problemDetail(failure))
    }

    @Test
    fun `an action that finishes records nothing and says nothing`() = runTest {
        var ran = false

        model.guarded(problems, onRefused = { refused += it }) { ran = true }
        advanceUntilIdle()

        assertThat(ran).isTrue()
        assertThat(refused).isEmpty()
        assertThat(problems.recorded).isEmpty()
    }

    @Test
    fun `being cancelled is not a failure`() = runTest {
        val job = model.guarded(problems, onRefused = { refused += it }) { awaitCancellation() }
        advanceUntilIdle()

        job.cancel(CancellationException("screen gone"))
        advanceUntilIdle()

        assertThat(job.isCancelled).isTrue()
        assertThat(refused).isEmpty()
        assertThat(problems.recorded).isEmpty()
    }

    /** Not caught, so it still reaches the crash handler — here, `runTest`'s report of it. */
    @Test
    fun `an Error is let through`() {
        assertThrows(Fatal::class.java) {
            runTest {
                model.guarded(problems, onRefused = { refused += it }) { throw Fatal() }
                advanceUntilIdle()
            }
        }
        assertThat(refused).isEmpty()
        assertThat(problems.recorded).isEmpty()
    }

    @Test
    fun `a problem log that cannot write does not stop the sentence`() = runTest {
        val broken = object : ProblemLog {
            override fun recent(): List<Problem> = emptyList()
            override fun record(kind: String, detail: String) = throw IllegalStateException("no")
            override fun clear() = Unit
        }

        model.guarded(broken, onRefused = { refused += it }) { throw IllegalStateException("x") }
        advanceUntilIdle()

        assertThat(refused).hasSize(1)
    }

    private class Fatal : Error()
}
