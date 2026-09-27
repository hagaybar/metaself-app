package com.metaself.app.ui.screen.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.AppLabels
import com.metaself.app.data.health.BandRecord
import com.metaself.app.data.time.Today
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.health.Arrival
import com.metaself.app.domain.health.BandReport
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.ui.RecordingProblemLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** The band report's one read (D80). TEST_EPOCH_DAY is 3 Sep 2026. Every count is invented. */
@OptIn(ExperimentalCoroutinesApi::class)
class BandReportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }
    private val problems = RecordingProblemLog()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `it reads the last 30 days once, and names each app`() = runTest {
        val asked = mutableListOf<Pair<Long, Long>>()
        val record = BandRecord { from, to ->
            asked += from to to
            BandReport.of(from, to, listOf(Arrival(HealthKind.STEPS, BAND, to, 10)), emptyList(), emptyList())
        }
        val labels = AppLabels { if (it == BAND) "Example Band" else it }

        val state = BandReportViewModel(record, labels, today, problems).state.first { it.report != null }

        assertThat(asked).containsExactly(TEST_EPOCH_DAY - 29 to TEST_EPOCH_DAY)
        assertThat(state.labels).containsExactly(BAND, "Example Band")
        assertThat(state.today).isEqualTo(TEST_EPOCH_DAY)
        assertThat(state.unreadable).isFalse()
    }

    @Test
    fun `a read that fails is logged and said, never thrown (D8)`() = runTest {
        val record = BandRecord { _, _ -> error("an invented failure") }

        val state = BandReportViewModel(record, AppLabels { it }, today, problems).state.first { it.unreadable }

        assertThat(state.report).isNull()
        assertThat(problems.recorded.single().kind).isEqualTo(BandReportViewModel.PROBLEM_KIND)
        assertThat(problems.recorded.single().detail).contains("an invented failure")
    }

    private companion object {
        const val BAND = "com.example.band"
    }
}
