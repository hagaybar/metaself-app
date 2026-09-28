package com.metaself.app.ui.screen.movement

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.SharedWorkoutFiles
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A workout file shared to MetaSelf (D82) is taken by the Movement screen only while that screen is
 * the one showing: its lifecycle — in the app, the navigation entry's — is RESUMED. A screen under
 * another one, or in an activity gone to the background, leaves the file waiting for the screen the
 * share opens.
 *
 * JUnit 4: Compose's rule under Robolectric. `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class TakeSharedWorkoutFileTest {

    @get:Rule
    val compose = createComposeRule()

    private val shared = SharedWorkoutFiles()
    private val imported = mutableListOf<String>()
    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun start(state: Lifecycle.State) {
        compose.runOnIdle { owner.registry.currentState = state }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                TakeSharedWorkoutFile(shared.pending, shared::take) { imported += it }
            }
        }
        compose.waitForIdle()
    }

    private fun moveTo(state: Lifecycle.State) {
        compose.runOnIdle { owner.registry.currentState = state }
        compose.waitForIdle()
    }

    private fun offer(uri: String) {
        compose.runOnIdle { shared.offer(uri) }
        compose.waitForIdle()
    }

    @Test
    fun `a screen not showing leaves the file waiting, and takes it once it shows`() {
        start(Lifecycle.State.CREATED)
        offer(A)

        assertThat(imported).isEmpty()
        assertThat(shared.pending.value).isEqualTo(A)

        moveTo(Lifecycle.State.RESUMED)

        assertThat(imported).containsExactly(A)
        assertThat(shared.pending.value).isNull()
    }

    @Test
    fun `a file waiting before the screen shows is taken when it does, and only once`() {
        offer(A)
        start(Lifecycle.State.RESUMED)
        moveTo(Lifecycle.State.STARTED)
        moveTo(Lifecycle.State.RESUMED)

        assertThat(imported).containsExactly(A)
    }

    @Test
    fun `a screen that stops showing stops taking, and each file shared while it shows is taken`() {
        start(Lifecycle.State.RESUMED)
        offer(A)
        moveTo(Lifecycle.State.STARTED)
        offer(B)

        assertThat(imported).containsExactly(A)
        assertThat(shared.pending.value).isEqualTo(B)

        moveTo(Lifecycle.State.RESUMED)

        assertThat(imported).containsExactly(A, B).inOrder()
    }

    private companion object {
        const val A = "content://example/a.tcx"
        const val B = "content://example/b.tcx"
    }
}
