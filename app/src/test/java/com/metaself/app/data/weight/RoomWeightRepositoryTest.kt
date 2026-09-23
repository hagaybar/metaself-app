package com.metaself.app.data.weight

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.assumeSqliteRuntime
import com.metaself.app.data.day.MetaSelfDatabase
import com.metaself.app.domain.weight.aReading
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** JUnit 4 by necessity. Skipped on aarch64, run in CI. */
@RunWith(RobolectricTestRunner::class)
class RoomWeightRepositoryTest {

    private lateinit var db: MetaSelfDatabase
    private lateinit var repository: RoomWeightRepository

    @Before
    fun setUp() {
        assumeSqliteRuntime()
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MetaSelfDatabase::class.java,
        ).build()
        repository = RoomWeightRepository(db.weightDao())
    }

    @After
    fun tearDown() {
        if (this::db.isInitialized) db.close()
    }

    @Test
    fun `a logged reading reads back`() = runTest {
        repository.log(aReading(kg = 80.5))

        assertThat(repository.readings.first().single().kg).isEqualTo(80.5)
    }

    @Test
    fun `logging twice on a day leaves one reading`() = runTest {
        repository.log(aReading(kg = 80.0))
        repository.log(aReading(kg = 79.6))

        assertThat(repository.readings.first()).hasSize(1)
    }
}
