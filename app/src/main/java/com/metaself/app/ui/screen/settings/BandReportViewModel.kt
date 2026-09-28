package com.metaself.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.health.AppLabels
import com.metaself.app.data.health.BandRecord
import com.metaself.app.data.health.WalkSwitch
import com.metaself.app.data.time.Today
import com.metaself.app.domain.health.BandReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What "What the band sends" shows (D80).
 *
 * @property report null while the read is under way, and when it failed.
 * @property labels each writing app's name, by package; a package missing from it is shown as itself.
 * @property unreadable the read failed; the page says so (D8).
 * @property uncounted the apps whose walks do not count (D81): the report's, changed at once by a
 *   switch so it moves before the write and the re-read finish.
 * @property switchFailed the last switch could not be saved; the page says so (D8).
 */
data class BandReportUiState(
    val report: BandReport? = null,
    val labels: Map<String, String> = emptyMap(),
    val today: Long = 0,
    val unreadable: Boolean = false,
    val uncounted: Set<String> = emptySet(),
    val switchFailed: Boolean = false,
)

/**
 * The page's own view model, on its own back-stack entry rather than the Settings graph's: the read is
 * this page's alone, happens once when the page opens, and should not run each time another Settings
 * page does. Which kinds are not allowed is not read here; the destination takes it from the Settings
 * view model, which already has it.
 */
@HiltViewModel
class BandReportViewModel @Inject constructor(
    private val record: BandRecord,
    private val labels: AppLabels,
    private val today: Today,
    private val problems: ProblemLog,
    private val walks: WalkSwitch,
) : ViewModel() {

    private val _state = MutableStateFlow(BandReportUiState())
    val state: StateFlow<BandReportUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { read() }
    }

    /**
     * "Count its walks as workouts" (D81). The switch moves at once; the choice is written and the
     * days it changes summarised again, then the report is read again, so its counts follow. A failed
     * write is logged and said, and the re-read shows what is actually stored.
     */
    fun setWalksCounted(origin: String, counted: Boolean) {
        _state.update {
            it.copy(uncounted = if (counted) it.uncounted - origin else it.uncounted + origin, switchFailed = false)
        }
        viewModelScope.launch {
            val failed = try {
                walks.set(origin, counted)
                false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                problems.record(PROBLEM_KIND, "walks choice not saved: " + (failure.message ?: failure::class.java.simpleName))
                true
            }
            read()
            if (failed) _state.update { it.copy(switchFailed = true) }
        }
    }

    private suspend fun read() {
        val day = today().toEpochDay()
        _state.value = try {
            val report = record.report(BandReport.fromDayFor(day), day)
            val named = report.origins + report.workouts.apps.map { it.origin }
            BandReportUiState(
                report = report,
                labels = named.associateWith(labels::labelOf),
                today = day,
                uncounted = report.uncountedWalkApps,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            problems.record(PROBLEM_KIND, "not read: " + (failure.message ?: failure::class.java.simpleName))
            BandReportUiState(today = day, unreadable = true)
        }
    }

    companion object {
        const val PROBLEM_KIND = "band report"
    }
}
