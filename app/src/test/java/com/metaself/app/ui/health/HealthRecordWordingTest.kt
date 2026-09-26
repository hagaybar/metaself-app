package com.metaself.app.ui.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.ArchiveRestore
import com.metaself.app.domain.health.HealthKind
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Every figure is invented. "Now" is 2026-09-03 at midnight UTC, the shared test epoch day. */
class HealthRecordWordingTest {

    private val now = 1_788_393_600_000L

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
                lastCopiedMillis = now - 2 * 60_000, nowMillis = now, catchingUp = true, zone = ZoneOffset.UTC,
            ),
        ).isEqualTo("Health record: 52 days, from 6 August · last copied 2 minutes ago · still catching up")
    }

    @Test
    fun `one is not ones, and a fresh copy is just now`() {
        assertThat(
            HealthRecordWording.status(1, LocalDate.of(2026, 9, 3), now - 10_000, now, catchingUp = false, zone = ZoneOffset.UTC),
        ).isEqualTo("Health record: 1 day, from 3 September · last copied just now")
    }

    /** The year is given only when it is not the year "now" is in, in the phone's own zone. */
    @Test
    fun `a date from an earlier year names the year`() {
        assertThat(
            HealthRecordWording.status(
                days = 10, earliest = LocalDate.of(2025, 8, 6),
                lastCopiedMillis = null, nowMillis = now, catchingUp = false, zone = ZoneOffset.UTC,
            ),
        ).isEqualTo("Health record: 10 days, from 6 August 2025")
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
            .isEqualTo("Reading heart rate and sleep is not allowed — tap Connect to allow them.")
        assertThat(HealthRecordWording.notAllowed(setOf(HealthKind.SLEEP)))
            .isEqualTo("Reading sleep is not allowed — tap Connect to allow it.")
        assertThat(HealthRecordWording.notAllowed(emptySet())).isNull()
    }

    /** The threshold itself: exactly three names are still listed, not counted. */
    @Test
    fun `three names are still listed`() {
        assertThat(HealthRecordWording.notAllowed(setOf(HealthKind.HEART_RATE, HealthKind.SLEEP, HealthKind.EXERCISE)))
            .isEqualTo("Reading heart rate, sleep and workouts is not allowed — tap Connect to allow them.")
    }

    @Test
    fun `many kinds are counted rather than listed`() {
        assertThat(HealthRecordWording.notAllowed(HealthKind.entries.toSet()))
            .isEqualTo("13 kinds of health data are not allowed — tap Connect to allow them.")
    }

    /** D71: the raw readings go to Drive, a file a month, when Drive backup is on. */
    @Test
    fun `the detailed readings line follows Drive`() {
        assertThat(HealthRecordWording.detailedBackup(driveOn = true))
            .isEqualTo("Detailed readings are copied to your Drive, one file a month.")
        assertThat(HealthRecordWording.detailedBackup(driveOn = false))
            .isEqualTo("Detailed readings are kept on this phone only while Drive backup is off; the daily backup has the summaries.")
    }

    @Test
    fun `the offer after a restore names the months`() {
        assertThat(HealthRecordWording.offerMonths(14))
            .isEqualTo("Also bring back 14 months of detailed readings from Drive?")
        assertThat(HealthRecordWording.offerMonths(1))
            .isEqualTo("Also bring back 1 month of detailed readings from Drive?")
    }

    @Test
    fun `what came back is said in numbers`() {
        assertThat(HealthRecordWording.broughtBack(ArchiveRestore(months = 2, readings = 40_000, unreadable = 0, unreachable = 0)))
            .isEqualTo("Brought back 2 months: 40,000 readings.")
        assertThat(HealthRecordWording.broughtBack(ArchiveRestore(months = 1, readings = 10, unreadable = 1, unreachable = 0)))
            .isEqualTo("Brought back 1 month: 10 readings. 1 file could not be read.")
        assertThat(HealthRecordWording.broughtBack(ArchiveRestore(months = 1, readings = 1, unreadable = 0, unreachable = 1)))
            .isEqualTo("Brought back 1 month: 1 reading. 1 file could not be downloaded.")
    }

    @Test
    fun `Drive not answering is said plainly`() {
        assertThat(HealthRecordWording.NOT_BROUGHT_BACK)
            .isEqualTo("Drive could not be reached; the detailed readings were not brought back.")
    }

    /** Could not be downloaded may work another time; could not be read will not. Both are said. */
    @Test
    fun `files that could not be downloaded and could not be read are said apart`() {
        assertThat(HealthRecordWording.broughtBack(ArchiveRestore(months = 0, readings = 0, unreadable = 2, unreachable = 3)))
            .isEqualTo("Brought back 0 months: 0 readings. 3 files could not be downloaded. 2 files could not be read.")
    }
}
