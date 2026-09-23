package com.metaself.app.data.profile

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.window.EatingWindow
import com.metaself.app.domain.window.MeasuredWindow
import com.metaself.app.domain.window.WindowRule
import org.junit.jupiter.api.Test

/**
 * The stored form of every window the owner has ever set.
 *
 * The whole of "an entry written before this release still reads" is tested here. A stored value
 * written by the shipped version must come back meaning the same thing, and — if nothing about it
 * is edited — must be written back byte for byte as it was, so an owner who never touches a ratio
 * never sees his stored value change.
 */
class WindowRuleCodecTest {

    private val sixToEight = EatingWindow(startHour = 6, endHour = 20, fromEpochDay = 20_699)
    private val eightToSix = EatingWindow(startHour = 8, endHour = 18, fromEpochDay = 20_730)
    private val sixteenEight = MeasuredWindow(fastingHours = 16)

    @Test
    fun `an entry written in the old format still reads`() {
        val rules = WindowRuleCodec.decode("6-20@20699")

        assertThat(rules).containsExactly(WindowRule.Fixed(sixToEight))
    }

    @Test
    fun `several old entries still read, oldest first`() {
        val rules = WindowRuleCodec.decode("6-20@20699,8-18@20730")

        assertThat(rules)
            .containsExactly(WindowRule.Fixed(sixToEight), WindowRule.Fixed(eightToSix))
            .inOrder()
    }

    @Test
    fun `the old format is written back unchanged`() {
        val stored = "6-20@20699,8-18@20730"

        assertThat(WindowRuleCodec.encode(WindowRuleCodec.decode(stored))).isEqualTo(stored)
        // The key is the load-bearing part: changing it would silently lose every stored window and
        // would look like the feature having been switched off.
        assertThat(WindowRuleCodec.KEY).isEqualTo("eating_windows")
    }

    /** `f` for fasting, which is also the half the number names. */
    @Test
    fun `a ratio entry reads back`() {
        val rules = WindowRuleCodec.decode("16f@20707")

        assertThat(rules)
            .containsExactly(WindowRule.Measured(sixteenEight, fromEpochDay = 20_707))
    }

    @Test
    fun `both kinds sit in one list, oldest first`() {
        val rules = WindowRuleCodec.decode("6-20@20699,16f@20707")

        assertThat(rules).containsExactly(
            WindowRule.Fixed(sixToEight),
            WindowRule.Measured(sixteenEight, fromEpochDay = 20_707),
        ).inOrder()
    }

    @Test
    fun `a ratio entry round-trips`() {
        val rules = listOf(
            WindowRule.Fixed(sixToEight),
            WindowRule.Measured(sixteenEight, fromEpochDay = 20_707),
        )

        val written = WindowRuleCodec.encode(rules)

        assertThat(written).isEqualTo("6-20@20699,16f@20707")
        assertThat(WindowRuleCodec.decode(written)).containsExactlyElementsIn(rules).inOrder()
    }

    /** The existing promise, kept: a bad entry costs one window, not all of them. */
    @Test
    fun `an unreadable entry costs one window, not all`() {
        val rules = WindowRuleCodec.decode("6-20@20699,rubbish,16f@20707")

        assertThat(rules).containsExactly(
            WindowRule.Fixed(sixToEight),
            WindowRule.Measured(sixteenEight, fromEpochDay = 20_707),
        ).inOrder()
    }

    /** The `require` inside the ratio is caught by the same dropping that catches everything else. */
    @Test
    fun `an out-of-range ratio is dropped rather than throwing`() {
        assertThat(WindowRuleCodec.decode("99f@20707")).isEmpty()
        assertThat(WindowRuleCodec.decode("0f@20707")).isEmpty()
    }

    @Test
    fun `a blank or missing value is no windows`() {
        assertThat(WindowRuleCodec.decode(null)).isEmpty()
        assertThat(WindowRuleCodec.decode("")).isEmpty()
    }
}
