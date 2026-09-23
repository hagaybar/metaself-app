package com.metaself.app.domain.day

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DayProgressTest {

    @Test
    fun `nothing eaten is an empty ring`() {
        assertThat(DayProgress.eatenFraction(target = 2090, eatenKcal = 0)).isEqualTo(0f)
    }

    @Test
    fun `half the target is half the ring`() {
        assertThat(DayProgress.eatenFraction(target = 2000, eatenKcal = 1000)).isWithin(0.001f)
            .of(0.5f)
    }

    @Test
    fun `the whole target is a full ring`() {
        assertThat(DayProgress.eatenFraction(target = 2000, eatenKcal = 2000)).isEqualTo(1f)
    }

    @Test
    fun `going over does not wrap the ring round and draw a lie`() {
        assertThat(DayProgress.eatenFraction(target = 2000, eatenKcal = 2800)).isEqualTo(1f)
    }

    @Test
    fun `a target of nothing is an empty ring rather than a division by zero`() {
        assertThat(DayProgress.eatenFraction(target = 0, eatenKcal = 500)).isEqualTo(0f)
    }
}
