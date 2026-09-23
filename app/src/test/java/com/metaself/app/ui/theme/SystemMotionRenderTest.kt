package com.metaself.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The app reads Android's "Remove animations" setting, and nothing moves when it is on (#16).
 *
 * The setting is Android's animator duration scale; "Remove animations" sets it to 0.
 *
 * JUnit 4 because Compose's rule demands it. `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class SystemMotionRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun movesWithScale(scale: Float?): Boolean {
        if (scale != null) {
            Settings.Global.putFloat(
                ApplicationProvider.getApplicationContext<Context>().contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                scale,
            )
        }
        var moves: Boolean? = null
        compose.setContent { ProvideSystemMotion { moves = LocalMoves.current } }
        compose.waitForIdle()
        return checkNotNull(moves)
    }

    @Test
    fun `with animations removed nothing moves`() {
        assertThat(movesWithScale(0f)).isFalse()
    }

    @Test
    fun `at the ordinary speed the app may move`() {
        assertThat(movesWithScale(1f)).isTrue()
    }

    /** A phone whose setting has never been touched has Android's default, which is motion. */
    @Test
    fun `a setting never touched means the ordinary speed`() {
        assertThat(movesWithScale(null)).isTrue()
    }

    /** Anything that is not inside the app's own provider is still: a preview, a render test. */
    @Test
    fun `outside the app's own provider nothing moves`() {
        var moves: Boolean? = null
        compose.setContent { moves = LocalMoves.current }
        compose.waitForIdle()
        assertThat(moves).isFalse()
    }
}
