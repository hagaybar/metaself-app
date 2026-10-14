package com.metaself.app.data.letter

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.trainer.RewriteTheSheet
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/**
 * Sunday's letter (D99), and after it the week's rewrite of the sheet (D150). Everything it decides is
 * [WriteWeeklyLetter]'s, [LetterRun]'s and [RewriteTheSheet]'s; this only runs them and tells WorkManager. It queues the next Sunday whenever it does not retry — under the other
 * Sunday's name ([LetterWork.nameFor]), so it never replaces itself. Nothing it logs holds more than a
 * failure's kind (D8).
 */
@HiltWorker
class WeeklyLetterWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val write: WriteWeeklyLetter,
    private val settings: LetterSettingsStore,
    private val scheduler: WeeklyLetterScheduler,
    private val notes: LetterNoteStore,
    private val problems: ProblemLog,
    private val sheet: RewriteTheSheet,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = LocalDateTime.now()
        val queued = inputData.getLong(LetterWork.WEEK, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
        val chosen = try {
            settings.settings.first()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            problems.record(LetterRun.KIND, "setting not read: ${failure::class.java.simpleName}")
            return when (LetterRun.afterSettingUnread(queued, now)) {
                LetterStep.RETRY -> Result.retry()
                else -> {
                    scheduleNext(queued)
                    Result.success()
                }
            }
        }
        if (!chosen.on) return Result.success()
        val week = LetterRun.weekOf(queued, now, chosen.hour)
        if (week == null) {
            scheduleNext(null)
            return Result.success()
        }
        val ran = LetterRun.run(week, now, write = { write(it) }, wanted = { write.wanted(it) }, problems = problems)
        ran.written?.let { LetterNotifications.arrived(applicationContext, it) }
        // D150: the sheet is rewritten once the letter's run is over — written, quiet or given up — never before a retry.
        if (ran.step != LetterStep.RETRY) rewriteSheet(week)
        return when (ran.step) {
            LetterStep.RETRY -> Result.retry()
            LetterStep.NOTIFY_FAILED -> {
                LetterNotifications.failed(applicationContext)
                // So Write it now is offered at once: this run will not try the week again.
                LetterRun.guarded(problems, "give-up not kept") { notes.markGaveUp(week) }
                scheduleNext(week)
                Result.success()
            }
            LetterStep.DONE -> {
                scheduleNext(week)
                Result.success()
            }
        }
    }

    /**
     * D150: the week's rewrite of the sheet, after its letter. Never retried: a failure leaves the sheet, and next
     * Sunday's reads the longer span. A failed read or write is logged by its kind only (D8).
     */
    private suspend fun rewriteSheet(weekMonday: Long) {
        try {
            sheet.rewrite(weekMonday + 6)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            runCatching { problems.record(SHEET_KIND, "not rewritten: ${failure::class.java.simpleName}") }
        }
    }

    /** Next Sunday's run; never [handledMonday]'s Sunday again, even if this run started before its hour. */
    private suspend fun scheduleNext(handledMonday: Long?) {
        runCatching { scheduler.schedule(handledMonday = handledMonday) }
            .onFailure { problems.record(LetterRun.KIND, "not scheduled: ${it::class.java.simpleName}") }
    }

    private companion object {
        const val SHEET_KIND = "sheet"
    }
}
