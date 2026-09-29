package com.metaself.app.data.letter

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.letter.LetterSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps exactly one Sunday run queued (design question 4); cancels it when the letter is switched off.
 * Called when the app opens and at every change of the setting ([keepScheduled]), and by the worker when
 * it finishes; any number of calls leave the same one run.
 */
@Singleton
class WeeklyLetterScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: LetterSettingsStore,
    private val problems: ProblemLog,
) {
    /**
     * Schedules now and again at every change of the setting — switched, a new hour, or a restore — for as
     * long as the caller's scope lives. Never throws but a cancellation: a failure is logged by kind (D8).
     * Nothing is written or sent; only WorkManager's queue changes.
     */
    suspend fun keepScheduled() {
        try {
            settings.settings.distinctUntilChanged().collect {
                try {
                    schedule()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    problems.record(LetterRun.KIND, "not scheduled: ${failure::class.java.simpleName}")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            problems.record(LetterRun.KIND, "setting not read: ${failure::class.java.simpleName}")
        }
    }

    /**
     * Queues the next Sunday's run under that Sunday's name ([LetterWork.nameFor]), `REPLACE`, carrying
     * the week it writes. Consecutive Sundays have different names, so a running worker queueing its
     * successor, or the app starting while a Sunday run is under way, never touches the run in progress;
     * a later call for the same Sunday replaces the one queued — unless that one is already running or
     * waiting to retry ([LetterWork.inProgress]), which is kept (an hour changed under a run still
     * trying: that run writes the week, and queues the next Sunday itself). Switched off, both names are
     * cancelled, a run in progress included. The week's uniqueness (design question 7) keeps any overlap
     * to one letter.
     */
    /** [handledMonday]: the week of the run calling this, whose Sunday is never queued again ([LetterSchedule.nextRunAfter]). */
    suspend fun schedule(now: LocalDateTime = LocalDateTime.now(), handledMonday: Long? = null) {
        val manager = WorkManager.getInstance(context)
        val chosen = settings.settings.first()
        if (!chosen.on) {
            LetterWork.NAMES.forEach(manager::cancelUniqueWork)
            return
        }
        val at = handledMonday?.let { LetterSchedule.nextRunAfter(now, it, chosen.hour) } ?: LetterSchedule.nextRun(now, chosen.hour)
        val name = LetterWork.nameFor(at.toLocalDate())
        if (manager.getWorkInfosForUniqueWorkFlow(name).first().any { LetterWork.inProgress(it.state, it.runAttemptCount) }) return
        val zone = ZoneId.systemDefault()
        val delay = Duration.between(now.atZone(zone), at.atZone(zone)).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<WeeklyLetterWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(LetterWork.WEEK to at.toLocalDate().minusDays(6).toEpochDay()))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
        manager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val BACKOFF_MINUTES = 15L
    }
}
