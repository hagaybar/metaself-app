package com.metaself.app.ui.screen.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.Today
import com.metaself.app.data.weight.WeightRepository
import com.metaself.app.domain.goal.GoalProgress
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.domain.weight.WeightTrend
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.guarded
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The weight history, and the trend through it.
 *
 * Logging a weight DOES move the daily calorie target, once a week and never silently: D11 has the
 * target following the smoothed trend, with a notice on the day screen explaining each change. This
 * screen used to say the opposite, which was true until that shipped and untrue afterwards.
 *
 * **Nothing here takes the app down.** Each action is one write, run through [guarded]; one that
 * throws puts [failed] on screen, says nothing was changed, and is written to the problem log.
 */
@HiltViewModel
class WeightViewModel @Inject constructor(
    private val weights: WeightRepository,
    private val profiles: ProfileRepository,
    private val today: Today,
    private val problems: ProblemLog,
) : ViewModel() {

    val state: StateFlow<WeightUiState> = combine(
        weights.readings,
        profiles.profile,
        profiles.weightChartRange,
    ) { readings, profile, storedRange ->
        val trend = WeightTrend.of(readings)
        WeightUiState(
            readings = readings,
            trend = trend,
            progress = profile?.goal?.let { GoalProgress.of(it, trend) },
            range = ChartRange.named(storedRange),
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = WeightUiState(),
        )

    /** Which day is today, for a screen that offers to log against another one. */
    val todayEpochDay: Long get() = today().toEpochDay()

    /**
     * Record what the scale said, on [epochDay] — today unless the owner picked another.
     *
     * Logging against a day that already has a reading REPLACES it, which is how correcting one
     * works: there is no separate edit, because a weight is one number and re-stating it is the
     * whole of changing it.
     *
     * An implausible number is refused silently here because the form has already said so — this is
     * the second line of defence, not the message.
     *
     * [confirmation] is what the screen says on return — "Logged 80.5 kg for Today." It is put up at
     * once, because the editor closes before the write finishes, and taken down if the write
     * throws: otherwise dismissing the failure would uncover it, claiming a weight that was never
     * stored.
     */
    fun log(kg: Double, epochDay: Long = todayEpochDay, confirmation: String? = null) {
        val reading = runCatching {
            WeightReading(epochDay = epochDay, kg = kg)
        }.getOrNull() ?: return

        _justLogged.value = confirmation
        act(onRefused = { _justLogged.value = null }) { weights.log(reading) }
    }

    /** He has moved on from the screen the confirmation was about; take it down. */
    fun forgetJustLogged() {
        _justLogged.value = null
    }

    /**
     * Remove the reading on [epochDay], keeping it so that [undoDelete] can put it back.
     *
     * The receipt is the reading itself, read from the STORE and not from [state]. The screen's
     * state is shared only while something is collecting it, so its value is the empty placeholder
     * whenever nothing is on screen — a receipt taken from there is correct exactly when it is not
     * needed. A weight has no id to lose, unlike a row on the day; it is a date and a number, so
     * the stored reading is the whole of it and re-logging restores it exactly.
     *
     * Delete-then-undo rather than a question first, and that is the rule rather than an exception
     * to it: the app asks before removing a thing it cannot rebuild — a food with its aliases,
     * brand, three groups of figures and the provenance of each — and offers undo when it can. This
     * is the cheapest thing in the app to restore.
     *
     * The receipt is kept only once the delete has happened, so a delete that failed offers no Undo
     * for a reading that is still there.
     */
    fun delete(epochDay: Long) {
        act {
            // Read before the delete, in this order, because afterwards there is nothing to read.
            val deleted = weights.readings.first().firstOrNull { it.epochDay == epochDay }
            weights.delete(epochDay)
            deleted?.let {
                undoable.addLast(it)
                _canUndo.value = true
            }
        }
    }

    /**
     * Put back the last reading deleted here, on its own day, at its own number.
     *
     * Through [WeightRepository.log], because logging against a day that already has a reading
     * replaces it — so a restore is exactly a re-statement, and needs nothing new in the store.
     *
     * A restore that fails puts the receipt back, so Undo is still there to try again.
     */
    fun undoDelete() {
        val reading = undoable.removeLastOrNull() ?: return
        _canUndo.value = undoable.isNotEmpty()
        act(
            onRefused = {
                undoable.addLast(reading)
                _canUndo.value = true
            },
        ) { weights.log(reading) }
    }

    /**
     * Remember how much history to show.
     *
     * Written to the store rather than held on screen, because the full-screen chart is meant to be
     * turned landscape and turning the phone throws away anything the composition was holding.
     */
    fun setRange(range: ChartRange) {
        act { profiles.saveWeightChartRange(range.name) }
    }

    /** He has read the failure; take it down. */
    fun dismissFailure() {
        _failed.value = null
    }

    /**
     * Run one action under the guard. The last failure is let go of as the next action starts, so
     * what is on screen is always about the latest thing he did.
     */
    private fun act(onRefused: () -> Unit = {}, block: suspend () -> Unit) {
        _failed.value = null
        guarded(problems, onRefused = {
            onRefused()
            _failed.value = ActionRefused.NOTHING_CHANGED
        }) { block() }
    }

    /**
     * Every reading deleted on this screen, oldest first. Undo takes the most recent, which is the
     * order they left in — the same shape the day's list uses for its own rows (issue #37).
     */
    private val undoable = ArrayDeque<WeightReading>()

    private val _canUndo = MutableStateFlow(false)

    /** Whether anything deleted here can still be put back — what the Undo line is drawn from. */
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _justLogged = MutableStateFlow<String?>(null)

    /**
     * The confirmation of the last log, shown when the editor comes back — until he leaves the
     * screen, or the write turns out to have failed.
     *
     * Held here rather than on screen because it has to survive the editor being popped, and
     * because only this side knows when the write it announces has failed.
     */
    val justLogged: StateFlow<String?> = _justLogged.asStateFlow()

    private val _failed = MutableStateFlow<ActionRefused?>(null)

    /** The last action that threw rather than finishing, until he dismisses it or does another. */
    val failed: StateFlow<ActionRefused?> = _failed.asStateFlow()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
