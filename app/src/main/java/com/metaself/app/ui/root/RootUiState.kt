package com.metaself.app.ui.root

import com.metaself.app.domain.profile.Profile
import com.metaself.app.domain.target.DailyTarget
import com.metaself.app.domain.target.MeasuredBurn

/**
 * Which of the two screens the app is on, decided by whether a profile exists.
 *
 * A sealed state rather than a navigation library: step 2 has two destinations chosen by data, not
 * a back stack. Step 3 introduces navigation when there is genuinely something to navigate.
 */
sealed interface RootUiState {

    /** The stored profile has not been read yet. Momentary, but it must not flash the setup form. */
    data object Loading : RootUiState

    data object NeedsSetup : RootUiState

    data class Ready(
        val profile: Profile,
        val target: DailyTarget,
        /** The weight the target was actually worked out from — the trend's, or the one typed. */
        val weightUsedKg: Double,
        /** Which of the two it was, so the screen can say so rather than leave it to be guessed. */
        val targetFollowsTrend: Boolean,
        /** The standing correction from what has actually happened, or zero (D25). */
        val burnAdjustmentKcal: Int = 0,
        /** What a day has actually cost, or null while there is not enough to say (D25). */
        val measuredBurn: MeasuredBurn? = null,
        val daysLoggedRecently: Int = 0,
    ) : RootUiState
}
