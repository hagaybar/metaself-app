package com.metaself.app.ui.theme

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Today moves, the past is still (public issue #16) — and nothing moves when the system has been
 * told to remove animations.
 *
 * Only the decision is tested here. Whether anything actually moves on screen is a question for
 * the phone: Robolectric draws no frames anyone watches.
 */
class MotionTest {

    @Test
    fun `the system's ordinary animation speed allows motion`() {
        assertThat(Motion.systemAllows(animatorScale = 1f)).isTrue()
    }

    @Test
    fun `slowed or hurried animations are still animations`() {
        assertThat(Motion.systemAllows(animatorScale = 0.5f)).isTrue()
        assertThat(Motion.systemAllows(animatorScale = 10f)).isTrue()
    }

    /** "Remove animations" in Android's accessibility settings sets the animator scale to 0. */
    @Test
    fun `remove animations means no motion`() {
        assertThat(Motion.systemAllows(animatorScale = 0f)).isFalse()
    }

    /** A setting that cannot be read as a speed is not read as permission to move. */
    @Test
    fun `a nonsense scale means no motion`() {
        assertThat(Motion.systemAllows(animatorScale = -1f)).isFalse()
        assertThat(Motion.systemAllows(animatorScale = Float.NaN)).isFalse()
    }

    @Test
    fun `today moves when the system allows it`() {
        assertThat(Motion.moves(isToday = true, systemAllows = true)).isTrue()
    }

    @Test
    fun `a past day is still, whatever the system allows`() {
        assertThat(Motion.moves(isToday = false, systemAllows = true)).isFalse()
        assertThat(Motion.moves(isToday = false, systemAllows = false)).isFalse()
    }

    @Test
    fun `today is still when animations are removed`() {
        assertThat(Motion.moves(isToday = true, systemAllows = false)).isFalse()
    }

    /** Short enough never to stand between the owner and the next tap. */
    @Test
    fun `no motion lasts longer than 300 ms`() {
        assertThat(Motion.PRESS_MILLIS).isAtMost(300)
        assertThat(Motion.SETTLE_MILLIS).isAtMost(300)
    }
}
