package com.metaself.app.ui.goal

import com.metaself.app.domain.goal.GoalProgress
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Everything this app says about a goal weight.
 *
 * Pure, and in one place, so that the sentences can be tested without a screen and so that the app
 * says the same thing in every place it says it.
 */
object GoalWording {

    /** "10.4 kg to go", or nothing when there is no goal or it is reached. */
    fun toGo(progress: GoalProgress?): String? {
        if (progress == null || progress.arrived) return null
        return "${kg(progress.toGoKg)} kg to go, to ${kg(progress.targetKg)} kg"
    }

    /**
     * "About 14 weeks at 0.5 kg a week."
     *
     * The rate is in the same sentence as the number of weeks, always, and the sentence opens with
     * "about". This is a division and it is written as one: the owner has to be able to see that it
     * is arithmetic from a number he chose, not a date the app is promising him. Decision D4's rule
     * that an estimate is never presented as a measurement, applied to the future.
     */
    fun projection(progress: GoalProgress?): String? {
        val weeks = progress?.weeksToGo ?: return null
        val rounded = weeks.roundToInt()
        if (rounded < 1) return "Less than a week at ${kg(progress.kgPerWeek)} kg a week."
        val unit = if (rounded == 1) "week" else "weeks"
        return "About $rounded $unit at ${kg(progress.kgPerWeek)} kg a week, if it keeps up."
    }

    /** "1.6 kg down since you started", or nothing while nothing has moved. */
    fun done(progress: GoalProgress?): String? {
        if (progress == null || progress.doneKg <= 0.05) return null
        val direction = if (progress.trendKg < progress.startKg) "down" else "up"
        return "${kg(progress.doneKg)} kg $direction since you started tracking."
    }

    /** Said on the weight screen once the trend has reached the goal. */
    fun arrived(progress: GoalProgress?): String? {
        if (progress == null || !progress.arrived) return null
        return "You are at your goal weight of ${kg(progress.targetKg)} kg."
    }

    /**
     * The celebration, shown on the day it happens and never again (D21).
     *
     * It states the new daily target in the same breath, because reaching the goal switches the goal
     * to holding and that moves the number by the whole size of the deficit. A target that changed
     * itself silently is the thing decision D11 exists to prevent.
     */
    fun celebration(targetKg: Double, newDailyKcal: Int): String =
        "You have reached ${kg(targetKg)} kg. " +
            "Your goal is now to hold it, and your daily target is $newDailyKcal kcal."

    private fun kg(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
}
