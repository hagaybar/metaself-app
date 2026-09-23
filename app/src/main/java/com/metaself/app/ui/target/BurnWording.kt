package com.metaself.app.ui.target

import com.metaself.app.domain.target.MeasuredBurn
import com.metaself.app.domain.target.MeasuredBurnCalculator
import java.util.Locale
import kotlin.math.abs

/**
 * What the app says about measuring, rather than predicting, what a day costs.
 *
 * **Nothing here says "metabolism", "burn rate" or "TDEE", and nothing here is phrased as a fact
 * about the owner's body.** A log captures some fraction of what is eaten, so this number comes out
 * in that log's own units. Worded as physiology it would be a claim he cannot check; worded as what it is —
 * the figure that, counted his way, has actually produced the weight he is getting — it is exactly
 * as true as the log behind it (D25a).
 */
object BurnWording {

    /**
     * The sum, shown rather than summarised.
     *
     * The same principle as the target's own visible arithmetic (D9): he is being asked to accept a
     * number that moved, so he gets to see what moved it.
     */
    fun arithmetic(measured: MeasuredBurn): String {
        val direction = if (measured.trendChangeKg <= 0) "down" else "up"
        return "Over ${measured.days} days you logged ${measured.averageLoggedKcal} kcal a day " +
            "on ${measured.daysLogged} of them, and your trend went $direction " +
            "${kg(abs(measured.trendChangeKg))} kg. " +
            "That is ${abs(measured.fromStoresKcalPerDay)} kcal a day " +
            "${if (measured.fromStoresKcalPerDay >= 0) "out of" else "into"} store, " +
            "so a day has been costing about ${measured.measuredKcal} kcal."
    }

    /** How far the formula was out, and in which direction, in plain words. */
    fun againstTheFormula(measured: MeasuredBurn): String {
        val difference = measured.differenceKcal
        return when {
            abs(difference) < WORTH_MENTIONING ->
                "The formula said ${measured.formulaKcal}, which is close enough to leave alone."

            difference < 0 ->
                "The formula said ${measured.formulaKcal} — about ${abs(difference)} kcal " +
                    "generous, so your target is coming down."

            else ->
                "The formula said ${measured.formulaKcal} — about $difference kcal short, " +
                    "so your target is going up."
        }
    }

    const val ASSUMPTION_DETAIL =
        "It does not have to be exact — only consistent from week to week. If you record roughly " +
            "the same share of what you eat each week, this number stays useful whatever that " +
            "share is. A careful fortnight followed by a sloppy one is what throws it off."

    /** Where the standing correction stands, for the screen that lists the arithmetic. */
    fun standing(kcal: Int): String? = when {
        kcal == 0 -> null
        kcal < 0 -> "Corrected down by ${abs(kcal)} kcal by what has actually happened."
        else -> "Corrected up by $kcal kcal by what has actually happened."
    }

    /** Before there is enough to measure, the screen says so rather than showing a placeholder. */
    fun notYet(daysLogged: Int, daysNeeded: Int): String =
        "Not enough yet to check the target against your weight: $daysLogged of the last 28 days " +
            "hold food, and $daysNeeded are needed."

    /**
     * Every line the profile screen shows about this, in order.
     *
     * The assumption is always last and always present, whether or not there is a number yet: it is
     * the one thing the arithmetic cannot check and the one that breaks it.
     */
    fun lines(
        measured: MeasuredBurn?,
        standingKcal: Int,
        daysLoggedRecently: Int,
    ): List<ExplanationLine> = buildList {
        if (measured == null) {
            add(
                ExplanationLine(
                    heading = "Not measured yet",
                    detail = notYet(daysLoggedRecently, MeasuredBurnCalculator.MIN_DAYS_LOGGED),
                ),
            )
        } else {
            add(
                ExplanationLine(
                    heading = "A day has been costing about ${measured.measuredKcal} kcal",
                    detail = arithmetic(measured),
                ),
            )
            add(
                ExplanationLine(
                    heading = "Against the formula",
                    detail = againstTheFormula(measured),
                ),
            )
        }

        standing(standingKcal)?.let {
            add(ExplanationLine(heading = "Your target now", detail = it))
        }

        // The assumption goes in the HEADING, not the detail. Details can be collapsed; this is
        // the one thing here that must be readable without opening anything.
        add(
            ExplanationLine(
                // Said every single time a measured number is said, and never in small print. It is
                // the one assumption the arithmetic cannot check and the one that breaks it: under-
                // recording looks exactly like a slow metabolism, and the correction it invites is
                // to cut the target, which invites more under-recording.
                heading = "This assumes what you logged is what you ate",
                detail = ASSUMPTION_DETAIL,
            ),
        )
    }

    private const val WORTH_MENTIONING = 25

    private fun kg(value: Double): String = String.format(Locale.US, "%.1f", value)
}
