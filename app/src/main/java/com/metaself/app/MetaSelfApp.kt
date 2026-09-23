package com.metaself.app

import android.app.Application
import com.metaself.app.data.diagnostics.ProblemLog
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * The app's root. Empty on purpose: nothing needs to happen at process start yet.
 *
 * Hilt is wired here from the skeleton because this class and [MainActivity] are the only two
 * places its annotations go, and both exist from step 1.
 */
@HiltAndroidApp
class MetaSelfApp : Application() {

    @Inject
    lateinit var problems: ProblemLog

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
                    detail = "${error::class.java.name}: ${error.message} " +
                        "at ${error.stackTrace.firstOrNull()}",
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
