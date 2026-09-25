package com.metaself.app.domain.food

import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source

/**
 * What one number on a food knows about where it came from — decision D4, one block per fact.
 *
 * A food holds up to three independent facts and each says separately where it came from, because
 * they genuinely can come from different places: a scanned packet knows what 100 grams are worth,
 * and only the owner knows what one bar of it weighs. One provenance for the whole food would have
 * to claim one of those about the other.
 *
 * The [source] and [confidence] rules are the ones `FoodItem` already enforces on a logged row, kept
 * identical here on purpose: a number that moves from a food onto a row must not have to change its
 * story on the way.
 *
 * @property setAtMillis when this number came to be believed — for display ("corrected on…"), never
 *   for choosing between two sources. That choice is by [rank] and never by date, so that a guess
 *   typed today cannot beat a label read last year.
 */
data class Provenance(
    val source: Source,
    val confidence: Confidence? = null,
    val setAtMillis: Long,
) {
    init {
        require(source != Source.TYPED || confidence == null) {
            "a typed number is the owner's number, not a guess: it carries no confidence"
        }
        require(source != Source.LABEL || confidence == null) {
            "a label is a declaration, not a guess: it carries no confidence"
        }
        require(source != Source.AI_ESTIMATE || confidence != null) {
            "an estimate must say how sure it was"
        }
        // A logged row may carry a confidence alongside REPEATED, because it is repeating whatever
        // the original said. A food is not repeating anything — it is the thing itself — so here a
        // confidence belongs to an estimate and to nothing else, and the column says so.
        require(source == Source.AI_ESTIMATE || confidence == null) {
            "on a food, only an estimate carries a confidence"
        }
    }

    /** Where this number sits in the ranking a better number has to beat. See [rankOf]. */
    val rank: Int get() = rankOf(source)

    /** The weaker of two provenances — [Provenance] for a number computed from both (design §5.3). */
    fun weakerOf(other: Provenance): Provenance = if (other.rank < rank) other else this

    companion object {
        /**
         * How credible a number is, as a number, so the rule can live in a `WHERE` clause rather
         * than in a caller's good intentions.
         *
         * A label beats a number the owner typed, which beats a guess, which beats a figure copied
         * off a past meal or one this version cannot read. Materialised on the food as a column for
         * the same reason: three guarded statements, one per fact, are what make "a scan never
         * touches what a unit weighs" a property of the schema instead of a rule somebody has to
         * remember.
         */
        fun rankOf(source: Source): Int = when (source) {
            Source.LABEL -> 3
            Source.TYPED -> 2
            Source.AI_ESTIMATE -> 1
            Source.REPEATED, Source.UNRECOGNISED -> 0
        }
    }
}

/**
 * What something is worth, whatever the something is.
 *
 * REAL rather than whole numbers throughout, because per-100-g arithmetic on a 30 g slice produces
 * fractions and rounding at storage time compounds. A logged row still stores whole calories; the
 * rounding happens once, at the moment of logging, rather than on every fact the number passes
 * through.
 */
data class Nutrients(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
) {
    init {
        require(kcal >= 0.0 && proteinG >= 0.0 && carbsG >= 0.0 && fatG >= 0.0) {
            "a food cannot contain a negative amount of anything"
        }
    }

    operator fun times(factor: Double): Nutrients = Nutrients(
        kcal = kcal * factor,
        proteinG = proteinG * factor,
        carbsG = carbsG * factor,
        fatG = fatG * factor,
    )
}

/** What 100 grams of it are worth. */
data class PerHundredGrams(val nutrients: Nutrients, val provenance: Provenance)

/**
 * What one of it is worth — one bar, one slice, one egg.
 *
 * @property unitName what "one" is. Never invented: a food whose history recorded no amount at all
 *   is counted in `portion`, which is truthful about what is known rather than a guess at what the
 *   thing was.
 */
data class PerUnit(val unitName: String, val nutrients: Nutrients, val provenance: Provenance) {
    init { require(unitName.isNotBlank()) { "one of what?" } }
}

/**
 * What one of it weighs — the third fact, and the one nothing is allowed to work out.
 *
 * It is the number that turns one way of counting into the other, so a wrong one propagates into
 * every future gram-counted log of that food, silently and for ever. Dividing per-unit calories by
 * per-100-g calories would produce it from two figures that are usually both estimates, and present
 * the answer as a fact about a physical object. That is the one thing D4 exists to prevent, so
 * **nothing in the app ever works it out**. It is set by the owner typing it, a packet stating it,
 * or — since D54 §12 — a model proposing it on request, shown to him as a suggestion and stored as
 * an estimate when he accepts it.
 */
data class GramsPerUnit(val grams: Double, val provenance: Provenance) {
    init { require(grams > 0.0) { "nothing weighs nothing" } }
}

/**
 * Everything a food knows about what it is worth: up to three facts, each optional, each with its
 * own provenance.
 *
 * **A food has no kind.** It is not "a per-100-g food" or "a per-unit food" — it is a thing that may
 * know either, or both, and may separately know what one of it weighs. That is what removes the
 * whole failure mode the earlier design had: re-scanning a food counted in bars cannot flip it to
 * grams, because there is nothing to flip. A scan adds a fact the food did not have and changes
 * nothing about how the owner counts it.
 *
 * Adding a fact never invalidates another, so nothing here can be made inconsistent by learning
 * something new. The three CAN disagree — 190 kcal per bar, 422 per 100 g and 45 g per bar multiply
 * out, until one of them is corrected and they do not — and that disagreement is shown rather than
 * reconciled. Nothing averages, nothing silently prefers, and nothing quietly drops one.
 */
data class FoodFacts(
    val per100g: PerHundredGrams? = null,
    val perUnit: PerUnit? = null,
    val gramsPerUnit: GramsPerUnit? = null,
) {
    init {
        require(per100g != null || perUnit != null) {
            "a food that knows neither what 100 g of it are worth nor what one of it is worth " +
                "cannot be logged, cannot be costed inside a meal, and is a name with nothing " +
                "behind it"
        }
    }

    /** True when the owner can weigh this food out — design §5.1. */
    val canBeWeighed: Boolean get() = per100g != null || gramsPerUnit != null

    /** True when the owner can count this food out — design §5.1. */
    val canBeCounted: Boolean get() = perUnit != null || gramsPerUnit != null

    /**
     * True when all this food knows is that one unnamed portion of it had these calories.
     *
     * The state the migration leaves behind for a row that recorded no amount at all. Truthful about
     * that row, and not knowledge about the food — which is why the owner is told how many there
     * are and given a way to fix them.
     */
    val onlyAPortion: Boolean
        get() = per100g == null && gramsPerUnit == null && perUnit?.unitName == PORTION

    companion object {
        /** What one of something is called when nothing ever said. */
        const val PORTION = "portion"
    }
}
