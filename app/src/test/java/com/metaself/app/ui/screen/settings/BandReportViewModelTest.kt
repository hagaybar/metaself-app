package com.metaself.app.ui.screen.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.AppLabels
import com.metaself.app.data.health.BandRecord
import com.metaself.app.data.health.WalkSwitch
import com.metaself.app.domain.health.ArrivedWorkout
import com.metaself.app.domain.movement.WorkoutKind
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
import kotlinx.coroutines.test.advanceUntilIdle
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

        val state = BandReportViewModel(record, labels, today, problems, NO_SWITCH).state.first { it.report != null }

        assertThat(asked).containsExactly(TEST_EPOCH_DAY - 29 to TEST_EPOCH_DAY)
        assertThat(state.labels).containsExactly(BAND, "Example Band")
        assertThat(state.today).isEqualTo(TEST_EPOCH_DAY)
        assertThat(state.unreadable).isFalse()
    }

    @Test
    fun `a read that fails is logged and said, never thrown (D8)`() = runTest {
        val record = BandRecord { _, _ -> error("an invented failure") }

        val state = BandReportViewModel(record, AppLabels { it }, today, problems, NO_SWITCH).state.first { it.unreadable }

        assertThat(state.report).isNull()
        assertThat(problems.recorded.single().kind).isEqualTo(BandReportViewModel.PROBLEM_KIND)
        assertThat(problems.recorded.single().detail).contains("an invented failure")
    }

    /** D81: the switch moves at once, the choice is written, and the report is read again after it. */
    @Test
    fun `switching an app's walks off writes it and reads the report again`() = runTest {
        var uncounted = emptySet<String>()
        var reads = 0
        val record = BandRecord { from, to ->
            reads++
            BandReport.of(from, to, emptyList(), listOf(walk()), emptyList(), uncounted)
        }
        val written = mutableListOf<Pair<String, Boolean>>()
        val switch = WalkSwitch { origin, counted ->
            written += origin to counted
            uncounted = if (counted) uncounted - origin else uncounted + origin
        }
        val model = BandReportViewModel(record, AppLabels { it }, today, problems, switch)
        model.state.first { it.report != null }

        model.setWalksCounted(BAND, counted = false)
        assertThat(model.state.value.uncounted).containsExactly(BAND)
        advanceUntilIdle()

        assertThat(written).containsExactly(BAND to false)
        assertThat(reads).isEqualTo(2)
        assertThat(model.state.value.report!!.workouts.notCounted).isEqualTo(1)
        assertThat(model.state.value.uncounted).containsExactly(BAND)
        assertThat(model.state.value.switchFailed).isFalse()
    }

    @Test
    fun `a switch that fails is logged and said, and the page shows what is stored (D8)`() = runTest {
        val record = BandRecord { from, to -> BandReport.of(from, to, emptyList(), listOf(walk()), emptyList()) }
        val switch = WalkSwitch { _, _ -> error("an invented failure") }
        val model = BandReportViewModel(record, AppLabels { it }, today, problems, switch)
        model.state.first { it.report != null }

        model.setWalksCounted(BAND, counted = false)
        advanceUntilIdle()

        assertThat(model.state.value.switchFailed).isTrue()
        assertThat(model.state.value.uncounted).isEmpty()
        assertThat(problems.recorded.single().kind).isEqualTo(BandReportViewModel.PROBLEM_KIND)
        assertThat(problems.recorded.single().detail).contains("an invented failure")
    }

    @Test
    fun `an app listed only because it was switched off is named too`() = runTest {
        val record = BandRecord { from, to -> BandReport.of(from, to, emptyList(), emptyList(), emptyList(), setOf(BAND)) }
        val labels = AppLabels { if (it == BAND) "Example Band" else it }

        val state = BandReportViewModel(record, labels, today, problems, NO_SWITCH).state.first { it.report != null }

        assertThat(state.labels).containsExactly(BAND, "Example Band")
        assertThat(state.uncounted).containsExactly(BAND)
    }

    private fun walk() = ArrivedWorkout(
        TEST_EPOCH_DAY, WorkoutKind.WALK, typed = false, origin = BAND,
        hasDistance = false, hasCalories = false, hasHeartRate = false, hasTitle = false,
    )

    private companion object {
        const val BAND = "com.example.band"
        val NO_SWITCH = WalkSwitch { _, _ -> error("not expected") }
    }
}
