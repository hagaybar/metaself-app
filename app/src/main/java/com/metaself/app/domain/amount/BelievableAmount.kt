package com.metaself.app.domain.amount

import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.PerHundredMillilitres
import com.metaself.app.domain.portion.Portions
import java.math.BigDecimal

/**
 * The one rule every box where he types a number asks after parsing it (D42, issue #32): a number,
 * not negative, and not above the ceiling for what that box holds.
 *
 * The ceilings are not opinions about what he should eat. Each is past anything a real portion or
 * label reaches, so what it catches is a slipped finger or a paste — "Infinity", "1e999", "1e300" —
 * as the bounds on *Type the numbers* always have. They live here, once, as named constants so that
 * no box can drift from another, and changing one is a change to D42, not a tweak of a literal.
 *
 * The rule judges the value, never the spelling (D39's principle): each box keeps its own parser —
 * commas where it took them, decimals where it kept them, whole numbers where it read those — and
 * asks here only about the number that parser produced. A refused number is never rounded, clamped
 * or replaced (D4); the box says why and nothing is saved.
 *
 * Typing, and two stored things. A packet kept on this phone is judged again each time a scan looks
 * it up, by D39's per-100 g ceilings here, so one cached before these ceilings existed is not found
 * rather than believed. And a food's number groups are judged each time the app opens, with the
 * food form's ceilings here, and a group holding a refused figure is cleared (issue #7,
 * `ImpossibleFigures`). Nothing else stored is judged: a backup restores its numbers as they are,
 * however large, and a logged row keeps what it holds until he next saves it through a form.
 */
object BelievableAmount {

    /**
     * Calories in 100 g of anything, on a label or a food's per-100 g box. Pure fat is about 900,
     * but a label is not chemistry: one rounded per spoon and scaled to 100 g prints ~923 for an
     * oil, and refusing it would leave him typing a number the label does not state (D4). 1000
     * still catches the mistake the ceiling is for — kilojoules typed as calories (an oil's 3700).
     */
    const val KCAL_PER_100G = 1_000.0

    /**
     * Grams of one macro in 100 g of anything. 100 would be the chemistry, but the same rounding
     * prints just over it — 14 g of fat per 13.6 g spoon is 103 g per 100 g — so the ceiling sits
     * a little past it, where only a slipped finger (150, 1000) or a paste lands.
     */
    const val MACRO_PER_100G = 110.0

    /** Calories in one of something (a bar, a tub, a slice), on a food's per-unit box. */
    const val KCAL_PER_UNIT = 5_000.0

    /** Grams of one macro in one of something, on a food's per-unit box. */
    const val MACRO_PER_UNIT = 500.0

    /**
     * An amount eaten, a serving, or what one of something weighs, in grams — or in whatever mass
     * unit a logged row names (5000 ml, and loosely 5000 kg: a slipped-finger guard, not a
     * portion).
     */
    const val GRAMS = 5_000.0

    /** A count of portions or units eaten at once. More than this is weighed, not counted. */
    const val COUNT = 100.0

    /** Calories on a whole item typed by hand — the bound *Type the numbers* already had. Whole. */
    const val ENTRY_KCAL = 10_000

    /** Grams of one macro on a whole item typed by hand — that form's existing bound. Whole. */
    const val ENTRY_MACRO_G = 1_000

    /** What a number typed into a box turned out to be. */
    enum class Verdict { BELIEVABLE, NOT_A_NUMBER, NEGATIVE, TOO_MUCH }

    /**
     * Exactly at [most] is believable, so a ceiling is a value he can type. Minus zero is zero (as
     * in D39). Minus infinity is negative before it is anything else; plus infinity, however it was
     * spelled, is simply too much.
     */
    fun judge(value: Double, most: Double): Verdict = when {
        value.isNaN() -> Verdict.NOT_A_NUMBER
        value < 0.0 -> Verdict.NEGATIVE
        value > most -> Verdict.TOO_MUCH
        else -> Verdict.BELIEVABLE
    }

    fun isBelievable(value: Double, most: Double): Boolean =
        judge(value, most) == Verdict.BELIEVABLE

    /**
     * Only a number past the ceiling — a word, "NaN" or a negative is some other wrong, which the
     * amount boxes leave to their quiet disabled button rather than name as too much.
     */
    fun isTooMuch(value: Double, most: Double): Boolean = judge(value, most) == Verdict.TOO_MUCH

    /** The ceiling on an amount eaten of a food: weighed out, or counted out. */
    fun amountEaten(countedAs: CountedAs): Double = when (countedAs) {
        CountedAs.GRAMS -> GRAMS
        CountedAs.UNITS -> COUNT
    }

    /**
     * The ceiling on an amount eaten of the food whose [facts] these are. A food counted in
     * millilitres is measured out, not counted (D56), so its amount is capped as a measure — 5000,
     * the ceiling [GRAMS] already names for millilitres — and not as a count of 100.
     */
    fun amountEaten(countedAs: CountedAs, facts: FoodFacts): Double =
        if (PerHundredMillilitres.inMillilitres(countedAs, facts)) GRAMS else amountEaten(countedAs)

    /**
     * The ceiling on an amount of a logged row, which names its unit in words. The mass/count split
     * is [Portions.isMass]'s — the one list of what counts as measured out; a second would drift.
     */
    fun amountIn(unit: String): Double = if (Portions.isMass(unit)) GRAMS else COUNT

    /** A ceiling as a person writes it in a refusal: "1000", never "1000.0" or "1E+3". */
    fun words(most: Double): String = BigDecimal.valueOf(most).stripTrailingZeros().toPlainString()
}
