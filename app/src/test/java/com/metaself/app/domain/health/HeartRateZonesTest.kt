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

    /**
     * Maximum 200: each zone starts exactly at 100, 120, 140, 160 and 180 bpm, and a rate exactly on a
     * boundary belongs to the zone above it. 99 is below half and in none.
     */
    @Test
    fun `a rate exactly on a boundary is in the zone it starts`() {
        val figures = HeartRateZones.of(
            samples = listOf(
                0L to 99.0, 60_000L to 100.0, 120_000L to 120.0,
                180_000L to 140.0, 240_000L to 160.0, 300_000L to 180.0,
            ),
            endMillis = 360_000,
            maxHeartRate = 200,
        )!!

        assertThat(figures.zoneSeconds).containsExactly(60, 60, 60, 60, 60).inOrder()
    }

    /** Samples a second and a half apart: time is added up before it is rounded, not per sample. */
    @Test
    fun `short samples add up to their real time`() {
        val figures = HeartRateZones.of(
            samples = listOf(0L to 110.0, 1_500L to 110.0, 3_000L to 110.0),
            endMillis = 4_000,
            maxHeartRate = 200,
        )!!

        assertThat(figures.zoneSeconds[0]).isEqualTo(4)
    }

    @Test
    fun `samples out of order are put in order first`() {
        val figures = HeartRateZones.of(
            samples = listOf(120_000L to 190.0, 0L to 110.0, 60_000L to 130.0),
            endMillis = 180_000,
            maxHeartRate = 200,
        )!!

        assertThat(figures.zoneSeconds).containsExactly(60, 60, 0, 0, 60).inOrder()
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
