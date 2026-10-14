package com.metaself.app.data.diagnostics.measure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * D152 (temporary): "Measure the app for 3 days", kept in the one preferences DataStore as two times —
 * when it was switched on, and when it ends. Switching off moves the end to now, so the hours measured
 * are still known when the file is downloaded. The 72-hour end needs no write: the recorder stops by
 * itself when it passes ([MeasureRecorder.isOn]), and [restore] does not start it again.
 */
@Singleton
class MeasureSwitch @Inject constructor(
    private val store: DataStore<Preferences>,
    private val recorder: MeasureRecorder,
) {

    /** When the latest measuring started and ends (or ended); nulls when never switched on. */
    data class Times(val startedAt: Long?, val until: Long?)

    val times: Flow<Times> = store.data.map { Times(it[STARTED], it[UNTIL]) }

    /** On every open, before anything waits on it: the recorder follows what is stored. */
    suspend fun restore() {
        val stored = times.first()
        val now = recorder.clock.wallMillis()
        if (MeasureWindow.isOn(stored.startedAt, stored.until, now)) recorder.switchOn(stored.until!!)
        recorder.restored()
    }

    suspend fun turnOn() {
        val now = recorder.clock.wallMillis()
        val until = MeasureWindow.endOf(now)
        store.edit {
            it[STARTED] = now
            it[UNTIL] = until
        }
        recorder.switchOn(until)
    }

    suspend fun turnOff() {
        val now = recorder.clock.wallMillis()
        recorder.switchOff()
        store.edit { preferences ->
            val until = preferences[UNTIL]
            if (until != null && until > now) preferences[UNTIL] = now
        }
    }

    private companion object {
        val STARTED = longPreferencesKey("measure_started_at_millis")
        val UNTIL = longPreferencesKey("measure_until_millis")
    }
}
