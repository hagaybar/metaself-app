package com.metaself.app.ui.goal

import com.metaself.app.domain.goal.GoalForecast
import com.metaself.app.domain.goal.GoalProgress
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
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
     * "At the 0.5 kg a week you're aiming for: about 16 weeks, around November 2024."
     *
     * The rate is in the same sentence as the weeks, always, and the sentence opens by naming the
     * rate as an intention — "you're aiming for" — rather than describing it. This is a division and
     * it is written as one: the owner has to be able to see that it is arithmetic from a number he
     * chose, not a date the app is promising him (D21, D4).
     *
     * The phrase "if it keeps up" was here until D47 and is deliberately gone. It presupposed that
     * something WAS keeping up, which made a value he typed into a form wear the grammar of an
     * observation; and with [measured] printed underneath it, "it" no longer has a referent.
     */
    fun projection(forecast: GoalForecast?): String? {
        val weeks = forecast?.chosenWeeks ?: return null
        val rate = rate(forecast.chosenKgPerWeek)
        val rounded = weeks.roundToInt()
        if (rounded < 1) return "Less than a week at $rate kg a week."
        val unit = if (rounded == 1) "week" else "weeks"
        val month = month(forecast.chosenFinishEpochDay) ?: return null
        return "At the $rate kg a week you're aiming for: about $rounded $unit, around $month."
    }

    /**
     * What the trend has ACTUALLY been doing, and where that lands (D47). Null when there is no
     * measurement to report.
     *
     * The span is stated in days and is the one observed, never the 28-day constant: it is the
     * denominator, and D21's rule that a projection must show what was divided by what applies to a
     * measurement at least as strongly.
     *
     * **This sentence reports a number and stops.** It says "up" or "down" of the TREND rather than
     * of the owner, and never says "only", "just", "still" or "behind". D22 forbids the app
     * commenting on going the wrong way, and register is what lets a factual readout coexist with
     * that: the alternative — a line that vanishes on a bad fortnight — teaches him that its absence
     * is the bad news, which nags by implication while pretending not to.
     */
    fun measured(forecast: GoalForecast?): String? {
        if (forecast == null || forecast.arrived) return null
        val rate = forecast.measured ?: return null
        val window = "Over the last ${rate.spanDays} days"

        if (abs(rate.kgPerWeek) < GoalForecast.FLAT_KG_PER_WEEK) {
            return "$window your trend has held steady."
        }

        // `<= 0.0` and not `< 0.0`: a HOLD goal resolves toward to exactly zero, and while it
        // cannot reach here today (holding produces no GoalProgress at all), falling through would
        // print "averaged 0 kg a week" — the one sentence FLAT_KG_PER_WEEK exists to forbid.
        val toward = forecast.measuredTowardGoalKgPerWeek
        if (toward == null || toward <= 0.0) {
            val direction = if (rate.kgPerWeek > 0.0) "up" else "down"
            return "$window your trend is $direction ${rate(abs(rate.kgPerWeek))} kg a week."
        }

        val averaged = "$window you've averaged ${rate(toward)} kg a week"
        val weeks = forecast.measuredWeeks ?: return "$averaged."

        val rounded = weeks.roundToInt()
        if (rounded < 1) return "$averaged: less than a week to go."
        val unit = if (rounded == 1) "week" else "weeks"
        val month = month(forecast.measuredFinishEpochDay) ?: return "$averaged."
        return "$averaged: about $rounded $unit, around $month."
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

    /** "Goal 72 kg": the label on the weight chart's dashed line (public issue #15). */
    fun goalLine(targetKg: Double): String = "Goal ${kg(targetKg)} kg"

    /** A kilogram figure as every goal sentence prints it: whole when whole, else one decimal. */
    internal fun kg(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }

    /**
     * Two decimals, trailing zeros trimmed — 0.25, 0.5, 0.05 — following `TargetWording.rate`.
     * [kg]'s single decimal would round 0.05 to 0.1 and 0.02 to 0.0, and a measured rate lives in
     * exactly that range whenever it is worth being careful about.
     */
    private fun rate(kgPerWeek: Double): String =
        String.format(Locale.US, "%.2f", kgPerWeek).trimEnd('0').trimEnd('.')

    /** "November 2024". The coarseness is the honesty: an exact date would read as a promise (D4). */
    private fun month(epochDay: Long?): String? =
        epochDay?.let { LocalDate.ofEpochDay(it).format(MONTH) }

    private val MONTH = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.US)
}
