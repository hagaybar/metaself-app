package com.metaself.app.domain.health

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Every figure is invented and round. */
class HeartRateZonesTest {

    @Test
    fun `the estimated maximum is 220 minus age`() {
        // The standard fixture body, born 1980, in 2026.
        assertThat(HeartRateZones.estimatedMax(birthYear = 1980, currentYear = 2026)).isEqualTo(174)
    }

    @Test
    fun `nothing measured is nothing, not zero`() {
        assertThat(HeartRateZones.of(emptyList(), endMillis = 60_000, maxHeartRate = 200)).isNull()
    }

    /**
     * Maximum 200, so the zones start at 100, 120, 140, 160 and 180 bpm. Each sample lasts until the
     * next one, and the last until the session ends.
     */
    @Test
    fun `each sample's time lands in its zone`() {
        val figures = HeartRateZones.of(
            samples = listOf(0L to 110.0, 60_000L to 130.0, 120_000L to 190.0),
            endMillis = 180_000,
            maxHeartRate = 200,
        )!!

        assertThat(figures.zoneSeconds).containsExactly(60, 60, 0, 0, 60).inOrder()
        assertThat(figures.average).isEqualTo(143)
        assertThat(figures.maximum).isEqualTo(190)
    }

    @Test
    fun `below half the maximum is in no zone, at or above the maximum is zone 5`() {
        val figures = HeartRateZones.of(
            samples = listOf(0L to 90.0, 60_000L to 210.0),
            endMillis = 120_000,
            maxHeartRate = 200,
        )!!

        assertThat(figures.zoneSeconds).containsExactly(0, 0, 0, 0, 60).inOrder()
    }

    /** A gap with no sample is not time spent at the last rate seen. */
    @Test
    fun `a sample never counts for longer than the longest span allowed`() {
        val figures = HeartRateZones.of(
            samples = listOf(0L to 110.0),
            endMillis = 3_600_000,
            maxHeartRate = 200,
        )!!

        assertThat(figures.zoneSeconds[0]).isEqualTo(HeartRateZones.LONGEST_SAMPLE_SECONDS)
    }

    @Test
    fun `zones are written as five comma-separated totals`() {
        assertThat(HeartRateZones.Figures(140, 160, listOf(0, 300, 900, 600, 0)).zonesAsText())
            .isEqualTo("0,300,900,600,0")
    }
}
