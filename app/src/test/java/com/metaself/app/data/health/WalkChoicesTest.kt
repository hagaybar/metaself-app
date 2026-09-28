package com.metaself.app.data.health

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Whose walks count (D81): the stored choice, over a real DataStore in a temporary directory, and the
 * switch that stores it and then summarises the days it changes. The apps are invented.
 */
class WalkChoicesTest {

    @Test
    fun `a fresh store counts every app's walks`(@TempDir dir: File) = runTest {
        assertThat(DataStoreWalkChoices(storeIn(dir)).uncounted.first()).isEmpty()
    }

    @Test
    fun `an app switched off is stored, and switched on again is forgotten`(@TempDir dir: File) = runTest {
        val choices = DataStoreWalkChoices(storeIn(dir))

        choices.setCounted(BAND, counted = false)
        assertThat(choices.uncounted.first()).containsExactly(BAND)

        choices.setCounted(BAND, counted = true)
        assertThat(choices.uncounted.first()).isEmpty()
    }

    @Test
    fun `two apps are kept apart`(@TempDir dir: File) = runTest {
        val choices = DataStoreWalkChoices(storeIn(dir))

        choices.setCounted(BAND, counted = false)
        choices.setCounted(PHONE, counted = false)
        choices.setCounted(BAND, counted = true)

        assertThat(choices.uncounted.first()).containsExactly(PHONE)
    }

    @Test
    fun `switching on an app never switched off changes nothing`(@TempDir dir: File) = runTest {
        val choices = DataStoreWalkChoices(storeIn(dir))

        choices.setCounted(BAND, counted = true)

        assertThat(choices.uncounted.first()).isEmpty()
    }

    @Test
    fun `the switch stores the choice, then summarises the days of that app's walks with every total kept`() = runTest {
        val events = mutableListOf<String>()
        val choices = object : WalkChoices {
            override val uncounted = kotlinx.coroutines.flow.flowOf(emptySet<String>())
            override suspend fun setCounted(origin: String, counted: Boolean) {
                events += "stored $origin $counted"
            }
        }
        val summarised = mutableListOf<Pair<Set<Long>, TotalsResult>>()
        val switch = RecountingWalkSwitch(
            choices = choices,
            walkDays = { origin ->
                events += "days $origin"
                listOf(TEST_EPOCH_DAY - 1, TEST_EPOCH_DAY)
            },
            summarise = { days, totals ->
                events += "summarised"
                summarised += days to totals
            },
        )

        switch.set(BAND, counted = false)

        assertThat(events).containsExactly("stored $BAND false", "days $BAND", "summarised").inOrder()
        assertThat(summarised.single().first).containsExactly(TEST_EPOCH_DAY - 1, TEST_EPOCH_DAY)
        assertThat(summarised.single().second).isEqualTo(TotalsResult.ALL_FAILED)
    }

    @Test
    fun `an app with no walks stored summarises nothing`() = runTest {
        var summarised = 0
        val switch = RecountingWalkSwitch(
            choices = object : WalkChoices {
                override val uncounted = kotlinx.coroutines.flow.flowOf(emptySet<String>())
                override suspend fun setCounted(origin: String, counted: Boolean) = Unit
            },
            walkDays = { emptyList() },
            summarise = { _, _ -> summarised++ },
        )

        switch.set(BAND, counted = true)

        assertThat(summarised).isEqualTo(0)
    }

    /** Leaving the page mid-switch must not leave the stored choice and the day summaries apart. */
    @Test
    fun `the recount still runs when the caller is cancelled after the choice is stored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val switch = RecountingWalkSwitch(
            choices = recording(events),
            walkDays = { gate.await(); listOf(TEST_EPOCH_DAY) },
            summarise = { _, _ -> events += "summarised" },
        )

        val job = launch { switch.set(BAND, counted = false) }
        runCurrent()
        job.cancel()
        gate.complete(Unit)
        job.join()

        assertThat(events).containsExactly("stored $BAND false", "summarised").inOrder()
    }

    /** Two quick taps: the second is stored only after the first's recount, so the last one wins. */
    @Test
    fun `two switches in quick succession run one after the other`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var first = true
        val switch = RecountingWalkSwitch(
            choices = recording(events),
            walkDays = { listOf(TEST_EPOCH_DAY) },
            summarise = { _, _ ->
                if (first) { first = false; gate.await() }
                events += "summarised"
            },
        )

        launch { switch.set(BAND, counted = false) }
        runCurrent()
        launch { switch.set(BAND, counted = true) }
        runCurrent()
        assertThat(events).containsExactly("stored $BAND false")

        gate.complete(Unit)
        runCurrent()

        assertThat(events).containsExactly(
            "stored $BAND false", "summarised", "stored $BAND true", "summarised",
        ).inOrder()
    }

    private fun recording(events: MutableList<String>) = object : WalkChoices {
        override val uncounted = kotlinx.coroutines.flow.flowOf(emptySet<String>())
        override suspend fun setCounted(origin: String, counted: Boolean) {
            events += "stored $origin $counted"
        }
    }

    private fun storeIn(dir: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(dir, "test.preferences_pb") }

    private companion object {
        const val BAND = "com.example.band"
        const val PHONE = "com.example.phone"
    }
}
