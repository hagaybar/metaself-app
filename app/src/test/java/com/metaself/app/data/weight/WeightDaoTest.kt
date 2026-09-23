package com.metaself.app.data.weight

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.assumeSqliteRuntime
import com.metaself.app.data.day.MetaSelfDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** JUnit 4 by necessity — Robolectric's runner is JUnit 4. Skipped on aarch64, run in CI. */
@RunWith(RobolectricTestRunner::class)
class WeightDaoTest {

    private lateinit var db: MetaSelfDatabase
    private lateinit var dao: WeightDao

    @Before
    fun setUp() {
        assumeSqliteRuntime()
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MetaSelfDatabase::class.java,
        ).build()
        dao = db.weightDao()
    }

    @After
    fun tearDown() {
        if (this::db.isInitialized) db.close()
    }

    @Test
    fun `nothing logged is an empty list`() = runTest {
        assertThat(dao.observeAll().first()).isEmpty()
    }

    @Test
    fun `a reading comes back`() = runTest {
        dao.upsert(WeightEntity(epochDay = DAY, kg = 80.0))

        assertThat(dao.observeAll().first().single().kg).isEqualTo(80.0)
    }

    @Test
    fun `weighing again on the same day replaces the reading`() = runTest {
        dao.upsert(WeightEntity(epochDay = DAY, kg = 80.0))
        dao.upsert(WeightEntity(epochDay = DAY, kg = 79.6))

        val all = dao.observeAll().first()
        assertThat(all).hasSize(1)
        assertThat(all.single().kg).isEqualTo(79.6)
    }

    @Test
    fun `readings come back oldest first, so the trend can be walked forwards`() = runTest {
        dao.upsert(WeightEntity(epochDay = DAY, kg = 79.0))
        dao.upsert(WeightEntity(epochDay = DAY - 2, kg = 81.0))
        dao.upsert(WeightEntity(epochDay = DAY - 1, kg = 80.0))

        assertThat(dao.observeAll().first().map { it.epochDay })
            .containsExactly(DAY - 2, DAY - 1, DAY).inOrder()
    }

    @Test
    fun `a reading can be removed`() = runTest {
        dao.upsert(WeightEntity(epochDay = DAY, kg = 80.0))

        dao.delete(DAY)

        assertThat(dao.observeAll().first()).isEmpty()
    }

    private companion object {
        const val DAY = 20_699L
    }
}
