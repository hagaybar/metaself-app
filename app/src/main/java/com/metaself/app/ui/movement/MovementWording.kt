package com.metaself.app.ui.movement

import com.metaself.app.data.movement.StepAccess
import com.metaself.app.domain.movement.MovementCredit
import com.metaself.app.domain.movement.MovementToday
import com.metaself.app.domain.movement.NormalDay
import java.time.LocalDate
import java.util.Locale

/**
 * What the app says about movement.
 *
 * The arithmetic is shown rather than folded in silently: the owner is being handed calories back,
 * and he should be able to see exactly what earned them and how few they are on an ordinary day
 * (D9). Nothing here ever says he "burned" a number — it says what his steps were worth under a
 * rule he can read.
 */
object MovementWording {

    /**
     * The count itself, which is shown every day whether or not it earned anything (D12a).
     *
     * "9,000 steps" and nothing else, because the number is the point. What it is worth, and what a
     * usual day looks like, are separate lines that do not compete with it.
     */
    fun steps(today: MovementToday?): String? = today?.let {
        if (it.recorded) "${number(it.steps)} steps" else "No steps recorded yet today"
    }

    /**
     * How today stands against a usual day, in plain words — and **in whichever reading decided
     * the credit.** A swimming day moves no steps, so comparing steps would call it quiet while the
     * band's figure was earning calories underneath; on such a day the line speaks in kcal of
     * movement instead. A day with no band is worded exactly as it always was.
     *
     * Never a reproach. A quiet day says what a usual day is and stops; it does not say he is
     * behind, because a target that already assumes normal movement is not owed anything by a
     * quiet Tuesday.
     */
    fun againstUsual(today: MovementToday?): String? {
        if (today?.recorded == false) return null
        if (today?.decidedByEnergy == true) return againstUsualEnergy(today)
        val usual = today?.normalSteps ?: return null
        val extra = today.extraSteps ?: return null
        return if (extra > 0) {
            "${number(extra)} more than your usual ${number(usual)}"
        } else {
            "Your usual day is ${number(usual)}"
        }
    }

    private fun againstUsualEnergy(today: MovementToday): String? {
        val energy = today.energy ?: return null
        val usual = today.normalEnergyKcal ?: return null
        val extra = (energy.kcal - usual).coerceAtLeast(0)
        return if (extra > 0) {
            "${number(extra)} kcal more movement than your usual ${number(usual)}"
        } else {
            "Your usual day is ${number(usual)} kcal of movement"
        }
    }

    /**
     * "Swimming · 45 min", one per session.
     *
     * Shown for its own sake. A screen that says what he did is worth more to somebody deciding
     * whether to do it again than the same energy folded into a total (D12a) — and for a swim it is
     * the ONLY visible sign, since swimming moves no steps at all.
     */
    fun sessions(today: MovementToday?): List<String> =
        today?.sessions.orEmpty().map { "${it.name} · ${it.minutes} min" }

    /** "+119 kcal earned." Null on any day that earned nothing. */
    fun earned(credit: MovementCredit?): String? {
        if (credit == null || credit.kcal <= 0) return null
        return "+${credit.kcal} kcal earned"
    }

    /** Said only when the cap actually bit, so it never reads as a complaint about a normal day. */
    fun capped(credit: MovementCredit?): String? {
        if (credit == null || !credit.capped) return null
        return "Held at ${credit.kcal} kcal. A big day gives some of the deficit back, never all."
    }

    /**
     * A day that earned nothing is not a failure and is not remarked upon.
     *
     * The target already assumes a normal amount of moving, so an ordinary day earning nothing is
     * the system working. Printing "0 kcal earned" would turn that into a reproach.
     */
    /**
     * @param earliest the oldest day with any steps in it, or null when there are none.
     *
     * Reported while it is still learning, because "4 of 10 days" alone cannot be acted on: it does
     * not say whether the app is failing to see a month of history or whether the phone only began
     * recording last Tuesday. The date tells the owner which, and there is nothing to do about the
     * second but wait.
     */
    fun status(
        access: StepAccess,
        hasNormal: Boolean,
        daysSoFar: Int,
        earliest: LocalDate? = null,
    ): String = when {
        access == StepAccess.UNAVAILABLE ->
            "Health Connect is not available on this phone, so steps cannot be read."

        access == StepAccess.NOT_PERMITTED ->
            "Off. Allow MetaSelf to read your steps and a long walk will add to that day's " +
                "allowance. An ordinary day adds nothing, by design."

        !hasNormal && daysSoFar == 0 ->
            "On, but there are no steps in Health Connect yet. Nothing is added until there are."

        !hasNormal ->
            "On, but still learning what an ordinary day looks like for you: $daysSoFar of the " +
                "${NormalDay.MIN_DAYS} days needed" +
                (earliest?.let { ", the earliest being $it" } ?: "") +
                ". Nothing is added until it knows."

        else ->
            "On. Steps above your usual day add to your allowance, at three quarters of what they " +
                "are worth and never more than half a day's deficit."
    }

    /**
     * Whether the band is reporting the one thing that makes a swim count.
     *
     * A session and its energy are two DIFFERENT records in Health Connect, and an app that writes
     * the first without the second is entirely ordinary. Without this line the owner sees a swim
     * listed on his day, sees his allowance unchanged, and has no way to tell whether that is the
     * cap, his baseline, or a band that simply never reported the calories.
     *
     * Steps never depend on it. Swimming and weights entirely do, because they move no steps.
     */
    fun bandEnergy(daysWithEnergy: Int, daysSeen: Int): String? {
        if (daysSeen == 0) return null
        return if (daysWithEnergy == 0) {
            "Your band has not reported calories for any day. Workouts still show, but only steps " +
                "count towards your allowance — so swimming and weights are earning nothing."
        } else {
            "Your band reported calories on $daysWithEnergy of the last $daysSeen days, which is " +
                "what lets swimming and weights count."
        }
    }

    private fun number(value: Int): String = String.format(Locale.US, "%,d", value)
}
