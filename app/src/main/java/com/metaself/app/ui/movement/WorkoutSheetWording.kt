package com.metaself.app.ui.movement

import com.metaself.app.domain.movement.WorkoutDraft
import java.util.Locale

/**
 * What the log-a-workout sheet says about the draft (D76). The energy is always called an estimate
 * and says what it was estimated from (D4): the pace when [WorkoutDraft.pricedByPace], else the effort.
 */
object WorkoutSheetWording {

    const val NO_WEIGHT = "No estimate: the profile has no weight"

    /** "about 150 kcal, estimated from the effort"; null until a kind and minutes are in. */
    fun estimate(draft: WorkoutDraft, weightKg: Double?): String? {
        if (draft.kind == null || draft.minutesValue == null) return null
        val kcal = draft.estimateKcal(weightKg) ?: return NO_WEIGHT
        val from = if (draft.pricedByPace) "the pace" else "the effort"
        return "about ${String.format(Locale.US, "%,d", kcal)} kcal, estimated from $from"
    }

    /** "6:00 /km", for a run with minutes and a distance; null otherwise (D78). */
    fun pace(draft: WorkoutDraft): String? = draft.paceSecondsPerKm?.let(MovementWeekWording::pace)
}
