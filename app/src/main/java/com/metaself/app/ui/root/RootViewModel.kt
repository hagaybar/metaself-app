package com.metaself.app.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.domain.profile.Profile
import com.metaself.app.data.day.MealRepository
import com.metaself.app.data.time.Today
import com.metaself.app.data.weight.WeightRepository
import com.metaself.app.domain.target.DailyTargetCalculator
import com.metaself.app.domain.target.MeasuredBurn
import com.metaself.app.domain.target.MeasuredBurnCalculator
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.domain.weight.WeightTrend
import com.metaself.app.domain.target.CurrentTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reads the stored profile, computes today's target from it, and writes changes back.
 *
 * The year arrives as an injected [CurrentYear] rather than being read inside the arithmetic, so
 * that a test can pin an age without pinning a clock.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val meals: MealRepository,
    private val weights: WeightRepository,
    private val today: Today,
    private val currentYear: CurrentYear,
) : ViewModel() {

    val state: StateFlow<RootUiState> = combine(
        repository.profile,
        repository.revision,
        repository.burnAdjustmentKcal,
        weights.readings,
    ) { profile, revision, burnAdjustment, readings ->
        if (profile == null) {
            RootUiState.NeedsSetup
        } else {
            // The same CurrentTarget the day screen uses, including the same measured correction.
            // Two screens showing two different targets is exactly the confusion this could
            // otherwise introduce.
            RootUiState.Ready(
                profile = profile,
                target = CurrentTarget.of(profile, revision, currentYear(), burnAdjustment),
                weightUsedKg = CurrentTarget.weightUsedKg(profile, revision),
                targetFollowsTrend = revision != null,
                burnAdjustmentKcal = burnAdjustment,
                measuredBurn = measure(profile, readings),
                daysLoggedRecently = daysLoggedRecently(),
            )
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = RootUiState.Loading,
        )

    /**
     * What a day has actually cost, for the screen that lists the arithmetic (D25).
     *
     * Read here as well as in the day view model rather than shared, because this one only shows a
     * number and the other one acts on it. Neither writes anything the other depends on.
     */
    private suspend fun measure(
        profile: Profile,
        readings: List<WeightReading>,
    ): MeasuredBurn? {
        val trend = WeightTrend.of(readings)
        val todayEpochDay = today().toEpochDay()
        val formulaKcal = DailyTargetCalculator.of(
            profile.copy(weightKg = trend.lastOrNull()?.trendKg ?: profile.weightKg),
            currentYear(),
        ).maintenanceKcal

        return MeasuredBurnCalculator.of(
            loggedKcalByDay = meals.kcalByDaySince(
                todayEpochDay - MeasuredBurnCalculator.WINDOW_DAYS + 1,
            ),
            trend = trend,
            formulaKcal = formulaKcal,
            todayEpochDay = todayEpochDay,
        )
    }

    /** How many of the last four weeks hold food, so the screen can say what is still missing. */
    private suspend fun daysLoggedRecently(): Int {
        val todayEpochDay = today().toEpochDay()
        return meals.kcalByDaySince(todayEpochDay - MeasuredBurnCalculator.WINDOW_DAYS + 1)
            .count { it.value > 0 }
    }

    /** The year the form validates a date of birth against. One clock reading, shared. */
    val year: Int get() = currentYear()

    /**
     * Put the measured correction back to nothing.
     *
     * It can walk six hundred calories over six weeks, and until now there was no way back. If the
     * owner's logging drifts for a month the target follows it and stays there; this is the door out
     * of that, and it belongs next to the number rather than buried in settings.
     */
    fun forgetBurnAdjustment() {
        viewModelScope.launch { repository.saveBurnAdjustment(0) }
    }

    fun save(profile: Profile) {
        viewModelScope.launch { repository.save(profile) }
    }

    /**
     * Record that the owner has accepted a target below the safe floor.
     *
     * Stored on the profile rather than held on the screen, so the app does not re-impose a limit
     * he has already overruled the next time it starts.
     */
    fun allowBelowFloor() {
        viewModelScope.launch {
            val current = repository.profile.first() ?: return@launch
            repository.save(current.copy(allowBelowFloor = true))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
