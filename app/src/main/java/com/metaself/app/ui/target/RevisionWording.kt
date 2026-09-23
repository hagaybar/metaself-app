package com.metaself.app.ui.target

import com.metaself.app.domain.target.TargetRevision
import java.util.Locale

/**
 * What the app says when the target moves.
 *
 * Decision D11's requirement in one place: a number that moves under you without saying so is a
 * number you stop trusting. The notice therefore carries all three things — what it was, what it is,
 * and the weight that moved it — rather than announcing a change and leaving the reason implied.
 *
 * Returns null when there is nothing to announce, which is both the first revision and any revision
 * that landed on the same number.
 */
object RevisionWording {

    fun notice(revision: TargetRevision): String? {
        if (!revision.isChange) return null
        val previous = revision.previousKcal ?: return null
        return "Your daily target has changed from ${grouped(previous)} to " +
            "${grouped(revision.kcal)} kcal, because your weight trend is now " +
            "${kg(revision.trendKg)}."
    }

    fun weightUsed(weightKg: Double, fromTrend: Boolean): String = if (fromTrend) {
        "Worked out from your weight trend, ${kg(weightKg)}"
    } else {
        "Worked out from the weight you entered, ${kg(weightKg)}"
    }

    private fun grouped(value: Int): String = String.format(Locale.US, "%,d", value)

    private fun kg(value: Double): String = String.format(Locale.US, "%.1f kg", value)
}
