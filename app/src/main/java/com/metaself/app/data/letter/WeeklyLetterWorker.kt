package com.metaself.app.data.letter

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.metaself.app.data.diagnostics.ProblemLog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/**
 * Sunday's letter (D99). Everything it decides is [WriteWeeklyLetter]'s and [LetterRun]'s; this only runs
 * them and tells WorkManager. It queues the next Sunday whenever it does not retry — under the other
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
    private val problems: ProblemLog,
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
        return when (ran.step) {
            LetterStep.RETRY -> Result.retry()
            LetterStep.NOTIFY_FAILED -> {
                LetterNotifications.failed(applicationContext)
                scheduleNext(week)
                Result.success()
            }
            LetterStep.DONE -> {
                scheduleNext(week)
                Result.success()
            }
        }
    }

    /** Next Sunday's run; never [handledMonday]'s Sunday again, even if this run started before its hour. */
    private suspend fun scheduleNext(handledMonday: Long?) {
        runCatching { scheduler.schedule(handledMonday = handledMonday) }
            .onFailure { problems.record(LetterRun.KIND, "not scheduled: ${it::class.java.simpleName}") }
    }
}
