package com.metaself.app.domain.food

import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import kotlin.math.roundToInt

/** Which of a food's two ways of counting an amount is expressed in. */
enum class CountedAs {
    /** Weighed out: 90 grams of it. */
    GRAMS,

    /** Counted out: two bars of it. */
    UNITS,
}

/** Why one of the two ways is not on offer. Said out loud, never silently unavailable. */
sealed interface CannotCount {

    /**
     * Weighing needs either what 100 grams are worth, or what one of it weighs. This food knows
     * neither, and nothing is allowed to work the weight out from the calories.
     */
    data class NothingKnowsWhatOneWeighs(val unitName: String) : CannotCount

    /** Counting needs a unit, and nothing has ever said what "one" of this food is. */
    data object NothingSaysWhatOneIs : CannotCount
}

/**
 * What logging a food at some amount puts on the record.
 *
 * @property amount and [unit] the assumption as arithmetic, exactly as a logged row already keeps
 *   it — so a meal repeated from the record can still have its amount changed, and so the existing
 *   control that reads the unit string keeps working with nothing behind it.
 */
sealed interface LoggedFrom {

    data class Numbers(
        val kcal: Int,
        val proteinG: Int,
        val carbsG: Int,
        val fatG: Int,
        val source: Source,
        val confidence: Confidence?,
        val amount: Double,
        val unit: String,
    ) : LoggedFrom

    data class NotOnOffer(val why: CannotCount) : LoggedFrom
}

/**
 * The arithmetic of logging a food: what goes on the row, and what the row says about where it came
 * from.
 *
 * All of it happens once, at the moment of logging, and never again. What lands on the row is a
 * plain number that nothing recomputes — which is why correcting a food later cannot change what a
 * past day was worth, and why a day's total is simply the sum of its rows.
 *
 * **The stored number always wins; computation only ever fills a gap.** A food may know all three of
 * its facts and they may not agree — 190 kcal per bar, 422 per 100 g, and 45 g per bar multiply out,
 * until one is corrected and they do not. Nothing here reconciles them, averages them, or quietly
 * prefers one. It reaches for a computed number only when the stored one is absent.
 */
object Logging {

    /** Grams, as a logged row spells them. */
    const val GRAMS_UNIT = "g"

    private const val HUNDRED_GRAMS = 100.0

    /**
     * Whether this food can be weighed out — and if not, why, so the screen can say so where the
     * field would be rather than leaving a gap the owner has to guess at.
     */
    fun canWeigh(facts: FoodFacts): CannotCount? = when {
        facts.canBeWeighed -> null
        else -> CannotCount.NothingKnowsWhatOneWeighs(facts.perUnit?.unitName ?: FoodFacts.PORTION)
    }

    /** Whether this food can be counted out, and if not, why. */
    fun canCount(facts: FoodFacts): CannotCount? = when {
        facts.canBeCounted -> null
        else -> CannotCount.NothingSaysWhatOneIs
    }

    /**
     * What this much of this food puts on the record.
     *
     * The two derivations reached for here are exact arithmetic on two facts that are separately
     * known, and the result is a calorie figure — which the record already treats as provisional and
     * says the provenance of. The derivation in the other direction, working out what one of
     * something weighs by dividing per-unit calories by per-100-g calories, is **not** done and must
     * never be: its inputs are typically two independent estimates, and its output would be
     * presented as a fact about a physical object, then propagate into every future gram-counted log
     * of that food, silently and for ever.
     */
    fun log(facts: FoodFacts, amount: Double, countedAs: CountedAs): LoggedFrom {
        require(amount > 0.0) { "nothing is logged by eating none of it" }
        return when (countedAs) {
            CountedAs.GRAMS -> weighed(facts, amount)
            CountedAs.UNITS -> counted(facts, amount)
        }
    }

    private fun weighed(facts: FoodFacts, grams: Double): LoggedFrom {
        val per100g = facts.per100g
        val weight = facts.gramsPerUnit
        val perUnit = facts.perUnit
        return when {
            // What is known, used as it stands.
            per100g != null -> numbers(
                per100g.nutrients * (grams / HUNDRED_GRAMS),
                per100g.provenance,
                grams,
                GRAMS_UNIT,
            )
            // Filling the gap: what one is worth, and what one weighs, are both known.
            perUnit != null && weight != null -> numbers(
                perUnit.nutrients * (grams / weight.grams),
                perUnit.provenance.weakerOf(weight.provenance),
                grams,
                GRAMS_UNIT,
            )
            else -> LoggedFrom.NotOnOffer(
                CannotCount.NothingKnowsWhatOneWeighs(perUnit?.unitName ?: FoodFacts.PORTION),
            )
        }
    }

    private fun counted(facts: FoodFacts, howMany: Double): LoggedFrom {
        val perUnit = facts.perUnit
        val weight = facts.gramsPerUnit
        val per100g = facts.per100g
        return when {
            perUnit != null -> numbers(
                perUnit.nutrients * howMany,
                perUnit.provenance,
                howMany,
                perUnit.unitName,
            )
            per100g != null && weight != null -> numbers(
                per100g.nutrients * (weight.grams * howMany / HUNDRED_GRAMS),
                per100g.provenance.weakerOf(weight.provenance),
                howMany,
                FoodFacts.PORTION,
            )
            else -> LoggedFrom.NotOnOffer(CannotCount.NothingSaysWhatOneIs)
        }
    }

    /**
     * **A computed number carries the weaker of the provenances it was computed from**, and a stored
     * one carries its own.
     *
     * So a scanned packet's per-100-g figure multiplied by a weight the owner typed logs as typed.
     * That is the truthful reading: a number is only as good as its worst input, and the whole point
     * of recording where a figure came from is that the record says which.
     *
     * Rounding happens here and only here. The facts stay in fractions so that a 30 g slice does not
     * accumulate error through every step; the row gets whole numbers, once, as it always has.
     */
    private fun numbers(
        nutrients: Nutrients,
        provenance: Provenance,
        amount: Double,
        unit: String,
    ) = LoggedFrom.Numbers(
        kcal = nutrients.kcal.roundToInt(),
        proteinG = nutrients.proteinG.roundToInt(),
        carbsG = nutrients.carbsG.roundToInt(),
        fatG = nutrients.fatG.roundToInt(),
        source = provenance.source,
        confidence = provenance.confidence,
        amount = amount,
        unit = unit,
    )
}
