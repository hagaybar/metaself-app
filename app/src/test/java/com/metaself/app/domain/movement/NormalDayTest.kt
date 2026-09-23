package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NormalDayTest {

    private val today = 20_699L

    @Test
    fun `a normal is the middle of the last thirty days`() {
        val history = (1..30).map { DayMovement(today - it, steps = it * 100) }

        // 100..3000; the median of thirty values is the mean of the fifteenth and sixteenth.
        assertThat(NormalDay.steps(history, today)).isEqualTo(1_550)
    }

    /**
     * Why the window is short: a baseline must not be poisoned by a period of hard training. A
     * median does that job within the window as well.
     */
    @Test
    fun `one enormous day does not move the normal`() {
        val ordinary = (1..29).map { DayMovement(today - it, steps = 5_000) }
        val withAHike = ordinary + DayMovement(today - 30, steps = 40_000)

        assertThat(NormalDay.steps(ordinary, today)).isEqualTo(5_000)
        assertThat(NormalDay.steps(withAHike, today)).isEqualTo(5_000)
    }

    /** A day still in progress is not a day to measure a normal by. */
    @Test
    fun `today is not part of the normal`() {
        val history = (1..30).map { DayMovement(today - it, steps = 5_000) } +
            DayMovement(today, steps = 90_000)

        assertThat(NormalDay.steps(history, today)).isEqualTo(5_000)
    }

    /**
     * A week with a flat phone is not a week of sitting still. Counting missing days as zero would
     * drag the normal down and inflate every credit afterwards.
     */
    @Test
    fun `days with no record at all are ignored, not counted as zero`() {
        val onlyHalfTheMonth = (1..15).map { DayMovement(today - it, steps = 8_000) }

        assertThat(NormalDay.steps(onlyHalfTheMonth, today)).isEqualTo(8_000)
    }

    /** But a day that recorded very little is real information about a real day. */
    @Test
    fun `a day that recorded few steps is kept`() {
        val history = (1..14).map { DayMovement(today - it, steps = 8_000) } +
            (15..28).map { DayMovement(today - it, steps = 200) }

        assertThat(NormalDay.steps(history, today)).isLessThan(8_000)
    }

    @Test
    fun `too little history means no normal, and therefore no credit`() {
        val threeDays = (1..3).map { DayMovement(today - it, steps = 9_000) }

        assertThat(NormalDay.steps(threeDays, today)).isNull()
        assertThat(NormalDay.steps(emptyList(), today)).isNull()
    }

    @Test
    fun `anything older than the window is none of its business`() {
        val old = (40..80).map { DayMovement(today - it, steps = 20_000) }
        val recent = (1..12).map { DayMovement(today - it, steps = 5_000) }

        assertThat(NormalDay.steps(old + recent, today)).isEqualTo(5_000)
    }
}
