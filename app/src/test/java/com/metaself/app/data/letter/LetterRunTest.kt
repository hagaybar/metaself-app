package com.metaself.app.data.letter

import androidx.work.WorkInfo
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.Problem
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.EstimateResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The worker's decisions (D99; design questions 4–6). The letter's week is the one holding
 * `TEST_EPOCH_DAY` ([LETTER_MONDAY], Monday 31 August 2026); its Sunday is the 6th of September.
 */
class LetterRunTest {

    private val sunday = LocalDate.ofEpochDay(LETTER_MONDAY + 6)
    private val sundayEvening = sunday.atTime(20, 0)
    private val mondayMorning = sunday.plusDays(1).atTime(11, 0)
    private val mondayNoon = sunday.plusDays(1).atTime(12, 0)
    private val log = RecordingLog()
    private val asked = mutableListOf<Long>()

    private suspend fun run(
        now: LocalDateTime,
        outcome: () -> WriteWeeklyLetter.Outcome = { WriteWeeklyLetter.Outcome.Written(aWeeklyLetter()) },
        wanted: () -> Boolean = { true },
    ) = LetterRun.run(LETTER_MONDAY, now, write = { asked += it; outcome() }, wanted = { wanted() }, problems = log)

    @Test
    fun `a letter written, a quiet week or one already written is done`() {
        for (outcome in listOf(
            WriteWeeklyLetter.Outcome.Written(aWeeklyLetter()),
            WriteWeeklyLetter.Outcome.Quiet,
            WriteWeeklyLetter.Outcome.AlreadyWritten,
        )) {
            assertThat(LetterRun.after(outcome, LETTER_MONDAY, sundayEvening)).isEqualTo(LetterStep.DONE)
        }
    }

    @Test
    fun `no key, a refusal or the ceiling is announced at once`() {
        val outcome = WriteWeeklyLetter.Outcome.GiveUp(EstimateResult.NoKey)

        assertThat(LetterRun.after(outcome, LETTER_MONDAY, sundayEvening)).isEqualTo(LetterStep.NOTIFY_FAILED)
    }

    @Test
    fun `a failure worth retrying is retried until Monday noon, and announced from then`() {
        val outcome = WriteWeeklyLetter.Outcome.Retry(EstimateResult.Unreachable())

        assertThat(LetterRun.after(outcome, LETTER_MONDAY, mondayMorning)).isEqualTo(LetterStep.RETRY)
        assertThat(LetterRun.after(outcome, LETTER_MONDAY, mondayNoon)).isEqualTo(LetterStep.NOTIFY_FAILED)
    }

    @Test
    fun `a run writes the week it was queued for, else the week the clock names`() {
        assertThat(LetterRun.weekOf(LETTER_MONDAY - 7, sundayEvening, 20)).isEqualTo(LETTER_MONDAY - 7)
        assertThat(LetterRun.weekOf(null, sundayEvening, 20)).isEqualTo(LETTER_MONDAY)
        assertThat(LetterRun.weekOf(null, sunday.minusDays(2).atTime(20, 0), 20)).isNull()
    }

    @Test
    fun `a written letter is handed back to be announced`() = runTest {
        val ran = run(sundayEvening)

        assertThat(ran).isEqualTo(LetterRun.Ran(LetterStep.DONE, aWeeklyLetter()))
        assertThat(asked).containsExactly(LETTER_MONDAY)
        assertThat(log.problems).isEmpty()
    }

    @Test
    fun `a give-up is announced, and nothing more is logged than the writer logged`() = runTest {
        val ran = run(sundayEvening, outcome = { WriteWeeklyLetter.Outcome.GiveUp(EstimateResult.CeilingReached) })

        assertThat(ran).isEqualTo(LetterRun.Ran(LetterStep.NOTIFY_FAILED))
        assertThat(log.problems).isEmpty()
    }

    @Test
    fun `a failure that throws is logged by its kind alone, never its message, and retried before the deadline`() = runTest {
        val ran = run(sundayEvening, outcome = { throw IllegalStateException(SECRET) })

        assertThat(ran).isEqualTo(LetterRun.Ran(LetterStep.RETRY))
        assertThat(log.problems).containsExactly(Problem(0, "letter", "not written: IllegalStateException"))
        assertThat(log.problems.toString()).doesNotContain(SECRET)
    }

    @Test
    fun `a run at or after Monday noon writes nothing, and announces the failure only if the week wanted a letter`() = runTest {
        assertThat(run(mondayNoon, wanted = { true })).isEqualTo(LetterRun.Ran(LetterStep.NOTIFY_FAILED))
        assertThat(run(sunday.plusDays(3).atTime(9, 0), wanted = { false })).isEqualTo(LetterRun.Ran(LetterStep.DONE))
        assertThat(asked).isEmpty()
    }

    @Test
    fun `a late run that cannot tell whether the week wanted a letter announces the failure, logged by kind`() = runTest {
        val ran = run(mondayNoon, wanted = { throw IllegalStateException(SECRET) })

        assertThat(ran).isEqualTo(LetterRun.Ran(LetterStep.NOTIFY_FAILED))
        assertThat(log.problems).containsExactly(Problem(0, "letter", "not checked: IllegalStateException"))
    }

    @Test
    fun `a run before its week's Sunday writes nothing yet`() = runTest {
        assertThat(run(sunday.minusDays(1).atTime(20, 0))).isEqualTo(LetterRun.Ran(LetterStep.RETRY))
        assertThat(asked).isEmpty()
    }

    @Test
    fun `a cancellation is passed on, not logged`() = runTest {
        assertThrows<CancellationException> { run(sundayEvening, outcome = { throw CancellationException("stopped") }) }
        assertThat(log.problems).isEmpty()
    }

    @Test
    fun `consecutive Sundays are queued under different names, and one Sunday always under the same`() {
        val names = (0L..3L).map { LetterWork.nameFor(sunday.plusWeeks(it)) }

        assertThat(names[0]).isNotEqualTo(names[1])
        assertThat(names[1]).isNotEqualTo(names[2])
        assertThat(names[0]).isEqualTo(names[2])
        assertThat(LetterWork.NAMES).containsExactlyElementsIn(names.toSet())
        assertThat(LetterWork.nameFor(sunday)).isEqualTo(LetterWork.nameFor(LocalDate.ofEpochDay(sunday.toEpochDay())))
    }

    @Test
    fun `a queued run that is running or waiting to retry is kept, anything else may be replaced`() {
        assertThat(LetterWork.inProgress(WorkInfo.State.RUNNING, 0)).isTrue()
        assertThat(LetterWork.inProgress(WorkInfo.State.ENQUEUED, 1)).isTrue()
        assertThat(LetterWork.inProgress(WorkInfo.State.ENQUEUED, 0)).isFalse()
        for (ended in listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED)) {
            assertThat(LetterWork.inProgress(ended, 2)).isFalse()
        }
    }

    private class RecordingLog : ProblemLog {
        val problems = mutableListOf<Problem>()
        override fun recent(): List<Problem> = problems.reversed()
        override fun record(kind: String, detail: String) {
            problems += Problem(0, kind, detail)
        }
        override fun clear() = problems.clear()
    }

    private companion object {
        const val SECRET = "Invented words that must not reach the log"
    }
}
