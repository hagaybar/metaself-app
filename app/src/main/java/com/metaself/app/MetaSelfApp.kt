package com.metaself.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.ui.crashDetail
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * The app's root. Empty on purpose: nothing needs to happen at process start yet.
 *
 * Hilt is wired here from the skeleton because this class and [MainActivity] are the only two
 * places its annotations go, and both exist from step 1.
 */
@HiltAndroidApp
class MetaSelfApp : Application(), Configuration.Provider {

    @Inject
    lateinit var problems: ProblemLog

    /** Hilt's factory, so a worker is built with its dependencies (D99). */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        recordCrashesBeforeDying()
    }

    /**
     * Write a crash down before the process goes.
     *
     * An early crash on the Test button left nothing to go on but the fact of the crash — the cause
     * was guessed, correctly, but guessed. This is so the next one leaves evidence that can be read
     * and passed on.
     *
     * The previous handler is still called afterwards, so Android's own behaviour is unchanged: the
     * app still dies, and still reports to the system exactly as before.
     */
    private fun recordCrashesBeforeDying() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                problems.record(
                    kind = "crash",
                    detail = crashDetail(error),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
