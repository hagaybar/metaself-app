package com.metaself.app.data.letter

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.letter.LetterSchedule
import com.metaself.app.domain.letter.WeeklyLetter
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.LocalDateTime

/** What the worker does after one run (design question 5). */
enum class LetterStep { DONE, RETRY, NOTIFY_FAILED }

/**
 * Everything the Sunday worker decides, pure but for the calls it is handed (D99; design questions 4–6).
 * The worker only runs this and tells WorkManager.
 */
object LetterRun {

    /** One run's end: its [step], and the letter to announce when one was written. */
    data class Ran(val step: LetterStep, val written: WeeklyLetter? = null)

    fun after(outcome: WriteWeeklyLetter.Outcome, weekMonday: Long, now: LocalDateTime): LetterStep = when (outcome) {
        is WriteWeeklyLetter.Outcome.Written, WriteWeeklyLetter.Outcome.Quiet, WriteWeeklyLetter.Outcome.AlreadyWritten -> LetterStep.DONE
        is WriteWeeklyLetter.Outcome.GiveUp -> LetterStep.NOTIFY_FAILED
        is WriteWeeklyLetter.Outcome.Retry -> if (LetterSchedule.mayRetry(weekMonday, now)) LetterStep.RETRY else LetterStep.NOTIFY_FAILED
    }

    /**
     * The week a run writes: the one it was queued for, else — a run queued without one — the week
     * [LetterSchedule.weekToWrite] names at [now], or null for none.
     */
    fun weekOf(queued: Long?, now: LocalDateTime, hour: Int): Long? = queued ?: LetterSchedule.weekToWrite(now, hour)

    /**
     * One run for [weekMonday]'s letter.
     *
     * - Before that week's Sunday (a clock or zone moved under a queued run): not yet — [LetterStep.RETRY].
     * - At or after Monday noon (a run held back by the network constraint, or a retry that landed late):
     *   never written this late (design question 6); the failure is announced if the week still
     *   [wanted] a letter, else nothing.
     * - Otherwise [write], and [after] its outcome.
     *
     * **D8, and the privacy of the letter:** a failure goes in [problems] by its kind only — the class of an
     * exception, never its message — so no figure, note, request text or model answer is written there. An
     * outcome's failure is not logged here at all: the writer has logged it by kind.
     */
    suspend fun run(
        weekMonday: Long,
        now: LocalDateTime,
        write: suspend (Long) -> WriteWeeklyLetter.Outcome,
        wanted: suspend (Long) -> Boolean,
        problems: ProblemLog,
    ): Ran {
        if (now.toLocalDate().isBefore(LocalDate.ofEpochDay(weekMonday + 6))) return Ran(LetterStep.RETRY)
        if (!LetterSchedule.mayRetry(weekMonday, now)) {
            val stillWanted = guarded(problems, "not checked") { wanted(weekMonday) } ?: true
            return Ran(if (stillWanted) LetterStep.NOTIFY_FAILED else LetterStep.DONE)
        }
        val outcome = guarded(problems, "not written") { write(weekMonday) }
            ?: return Ran(if (LetterSchedule.mayRetry(weekMonday, now)) LetterStep.RETRY else LetterStep.NOTIFY_FAILED)
        return Ran(after(outcome, weekMonday, now), (outcome as? WriteWeeklyLetter.Outcome.Written)?.letter)
    }

    /** [block]'s value, or null after logging its failure by kind (D8). A cancellation is passed on. */
    private suspend fun <T> guarded(problems: ProblemLog, what: String, block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        runCatching { problems.record(KIND, "$what: ${failure::class.java.simpleName}") }
        null
    }

    const val KIND = "letter"
}
