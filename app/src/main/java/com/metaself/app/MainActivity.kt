package com.metaself.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import com.metaself.app.data.health.SharedWorkoutFiles
import com.metaself.app.ui.root.MetaSelfRoot
import com.metaself.app.ui.theme.MetaSelfTheme
import com.metaself.app.ui.theme.ProvideSystemMotion
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** A workout file shared to MetaSelf (D82) waits here until the Movement screen takes it. */
    @Inject
    lateinit var sharedWorkoutFiles: SharedWorkoutFiles

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Not again on a re-creation: the file was offered the first time, and may be imported already.
        if (savedInstanceState == null) takeSharedFile(intent)
        enableEdgeToEdge()
        setContent {
            MetaSelfTheme {
                // Whether anything may move at all, from the system's "Remove animations" (#16).
                // Here rather than in the theme, so a render test or a preview is still by default.
                ProvideSystemMotion {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        MetaSelfRoot(
                            versionName = BuildConfig.VERSION_NAME,
                            versionCode = BuildConfig.VERSION_CODE,
                        )
                    }
                }
            }
        }
    }

    /** Only from [onCreate]; see [sharedWorkoutFile] for which launches carry one. */
    private fun takeSharedFile(intent: Intent?) {
        sharedWorkoutFile(intent)?.let(sharedWorkoutFiles::offer)
    }
}

/**
 * The file a launch shares to MetaSelf (D82), as a content Uri string: a share (ACTION_SEND) carrying
 * a stream; one with no stream is not a workout file and is ignored.
 *
 * The activity keeps the default launch mode, so every share starts a new activity (in the sharing
 * app's task) and `onNewIntent` is never called; the activity has none, and reads this in `onCreate`.
 *
 * A launch from Recents replays the intent the task was started with. That share was offered when it
 * arrived, so reopening from Recents a task a share started is not the file shared again.
 */
internal fun sharedWorkoutFile(intent: Intent?): String? {
    if (intent?.action != Intent.ACTION_SEND) return null
    if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
    return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.toString()
}
