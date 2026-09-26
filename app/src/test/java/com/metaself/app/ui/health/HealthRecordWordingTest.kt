package com.metaself.app.ui.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.health.HealthKind
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Every figure is invented. */
class HealthRecordWordingTest {

    private val now = 1_000_000_000L

    @Test
    fun `nothing copied yet says so`() {
        assertThat(HealthRecordWording.status(days = 0, earliest = null, lastCopiedMillis = null, nowMillis = now, catchingUp = false))
            .isEqualTo("Health record: nothing copied yet.")
    }

    @Test
    fun `the record says how far it reaches and when it was last copied`() {
        assertThat(
            HealthRecordWording.status(
                days = 52, earliest = LocalDate.of(2026, 8, 6),
                lastCopiedMillis = now - 2 * 60_000, nowMillis = now, catchingUp = true,
            ),
        ).isEqualTo("Health record: 52 days, from 6 August · last copied 2 minutes ago · still catching up")
    }

    @Test
    fun `one is not ones, and a fresh copy is just now`() {
        assertThat(
            HealthRecordWording.status(1, LocalDate.of(2026, 9, 3), now - 10_000, now, catchingUp = false),
        ).isEqualTo("Health record: 1 day, from 3 September · last copied just now")
    }

    @Test
    fun `hours and days read as hours and days`() {
        assertThat(HealthRecordWording.ago(now - 3 * 3_600_000, now)).isEqualTo("3 hours ago")
        assertThat(HealthRecordWording.ago(now - 2 * 86_400_000L, now)).isEqualTo("2 days ago")
        assertThat(HealthRecordWording.ago(now - 60_000, now)).isEqualTo("1 minute ago")
    }

    @Test
    fun `kinds not allowed are named, with how to allow them`() {
        assertThat(HealthRecordWording.notAllowed(setOf(HealthKind.HEART_RATE, HealthKind.SLEEP)))
            .isEqualTo("Heart rate and sleep are not allowed — tap Connect to allow them.")
        assertThat(HealthRecordWording.notAllowed(setOf(HealthKind.SLEEP)))
            .isEqualTo("Sleep is not allowed — tap Connect to allow it.")
        assertThat(HealthRecordWording.notAllowed(emptySet())).isNull()
    }

    @Test
    fun `many kinds are counted rather than listed`() {
        assertThat(HealthRecordWording.notAllowed(HealthKind.entries.toSet()))
            .isEqualTo("13 kinds of health data are not allowed — tap Connect to allow them.")
    }

    /** D71: the raw readings go to Drive in the next phase; until then they are on this phone only. */
    @Test
    fun `the detailed readings are said not to be backed up`() {
        assertThat(HealthRecordWording.NOT_BACKED_UP)
            .isEqualTo("Detailed readings are kept on this phone only; the daily backup has the summaries.")
    }
}
