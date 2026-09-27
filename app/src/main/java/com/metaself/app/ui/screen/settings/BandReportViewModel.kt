package com.metaself.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.health.AppLabels
import com.metaself.app.data.health.BandRecord
import com.metaself.app.data.time.Today
import com.metaself.app.domain.health.BandReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What "What the band sends" shows (D80).
 *
 * @property report null while the read is under way, and when it failed.
 * @property labels each writing app's name, by package; a package missing from it is shown as itself.
 * @property unreadable the read failed; the page says so (D8).
 */
data class BandReportUiState(
    val report: BandReport? = null,
    val labels: Map<String, String> = emptyMap(),
    val today: Long = 0,
    val unreadable: Boolean = false,
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
) : ViewModel() {

    private val _state = MutableStateFlow(BandReportUiState())
    val state: StateFlow<BandReportUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { read() }
    }

    private suspend fun read() {
        val day = today().toEpochDay()
        _state.value = try {
            val report = record.report(BandReport.fromDayFor(day), day)
            BandReportUiState(report = report, labels = report.origins.associateWith(labels::labelOf), today = day)
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
