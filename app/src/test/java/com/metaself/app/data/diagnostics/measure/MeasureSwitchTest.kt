package com.metaself.app.data.diagnostics.measure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** D152: the switch, kept across opens, and off by itself after 72 hours. Invented times. */
class MeasureSwitchTest {

    private val clock = FakeMeasureClock(wall = 5_000_000L)
    private val recorder = MeasureRecorder(RecordingSink(), clock, aWriterWithoutTimer())

    @Test
    fun `switched on, it measures for 72 hours and says so`(@TempDir dir: File) = runTest {
        val switch = MeasureSwitch(dataStore(dir), recorder)

        switch.turnOn()

        assertThat(recorder.isOn).isTrue()
        assertThat(switch.times.first()).isEqualTo(MeasureSwitch.Times(5_000_000L, 5_000_000L + MeasureWindow.LENGTH_MILLIS))
    }

    @Test
    fun `the next open picks it up again while the 72 hours last, and not after`(@TempDir dir: File) = runTest {
        val store = dataStore(dir)
        MeasureSwitch(store, recorder).turnOn()

        val reopened = MeasureRecorder(RecordingSink(), clock, aWriterWithoutTimer())
        clock.wall += MeasureWindow.LENGTH_MILLIS - 1
        MeasureSwitch(store, reopened).restore()
        assertThat(reopened.isOn).isTrue()

        val later = MeasureRecorder(RecordingSink(), clock, aWriterWithoutTimer())
        clock.wall += 1
        MeasureSwitch(store, later).restore()
        assertThat(later.isOn).isFalse()
    }

    @Test
    fun `switched off, it stops, and the hours measured end there`(@TempDir dir: File) = runTest {
        val switch = MeasureSwitch(dataStore(dir), recorder)
        switch.turnOn()
        clock.wall += 2 * 3_600_000L

        switch.turnOff()

        assertThat(recorder.isOn).isFalse()
        val times = switch.times.first()
        assertThat(MeasureWindow.hoursMeasured(times.startedAt, times.until, clock.wall + 99 * 3_600_000L)).isEqualTo(2.0)
    }

    private fun dataStore(dir: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(dir, "measure.preferences_pb") }
}
