package com.metaself.app.domain.food

import com.metaself.app.domain.amount.BelievableAmount
import com.metaself.app.domain.amount.BelievableAmount.GRAMS
import com.metaself.app.domain.amount.BelievableAmount.KCAL_PER_100G
import com.metaself.app.domain.amount.BelievableAmount.KCAL_PER_UNIT
import com.metaself.app.domain.amount.BelievableAmount.MACRO_PER_100G
import com.metaself.app.domain.amount.BelievableAmount.MACRO_PER_UNIT
import com.metaself.app.domain.amount.BelievableAmount.words
import com.metaself.app.domain.day.Source
import java.math.BigDecimal
import java.math.RoundingMode

/** Which field of the food form a complaint is about. */
enum class FoodField {
    NAME,
    PER_100G,
    PER_UNIT,
    UNIT_NAME,
    WEIGHT,
    NOTHING_KNOWN,
}

/**
 * A food as the owner edits it: everything it is, as the strings he typed.
 *
 * Strings rather than parsed numbers for the reason the packet form already gives — a field being
 * mid-edit is a normal state, and "5" on the way to "535" is not an error worth shouting about.
 *
 * **An empty group means the food does not know that.** Clearing the four per-unit fields is how he
 * says "this food is not counted in anything", and the form has to allow it, because the three facts
 * are optional by design and a form that insisted on all of them would quietly reintroduce the idea
 * that a food has a kind.
 *
 * **Everything typed here is [Source.TYPED]** — with one exception, D54: a group he accepted from a
 * review is handed over as [Source.AI_ESTIMATE] with that review's confidence, even if he then
 * changed a figure in it, because it is still a mix with a guess in it and a mixed group is labelled
 * by its weakest member (D4) — which is the source of the figures the review kept instead, where that
 * ranks below an estimate ([AcceptedGroup.provenance]). What one weighs is never accepted from anything and is always typed.
 * The rest is the owner's own hand, which is the whole reason a correction is not subject to the
 * ranking that protects him from a guess: the ranking exists to stop a model overwriting him, not to
 * stop him overwriting a model.
 */
data class FoodForm(
    val name: String = "",
    val brand: String = "",
    val kcalPer100g: String = "",
    val proteinPer100g: String = "",
    val carbsPer100g: String = "",
    val fatPer100g: String = "",
    val unitName: String = "",
    val kcalPerUnit: String = "",
    val proteinPerUnit: String = "",
    val carbsPerUnit: String = "",
    val fatPerUnit: String = "",
    val gramsPerUnit: String = "",
) {

    private val per100gTyped: List<String>
        get() = listOf(kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g)

    private val perUnitTyped: List<String>
        get() = listOf(kcalPerUnit, proteinPerUnit, carbsPerUnit, fatPerUnit)

    /** Each box of a group with its own ceiling: calories first, then the three macros (D42). */
    private val per100gJudged: List<Pair<String, Double>>
        get() = per100gTyped.zip(
            listOf(KCAL_PER_100G, MACRO_PER_100G, MACRO_PER_100G, MACRO_PER_100G),
        )

    /**
     * For a food counted in millilitres the four boxes are per 100 ml (D56), so they are judged by
     * the per-100 ceilings: a carton's figures are per 100 of something, like a packet's.
     */
    private val perUnitJudged: List<Pair<String, Double>>
        get() = perUnitTyped.zip(
            if (perHundredMl) {
                listOf(KCAL_PER_100G, MACRO_PER_100G, MACRO_PER_100G, MACRO_PER_100G)
            } else {
                listOf(KCAL_PER_UNIT, MACRO_PER_UNIT, MACRO_PER_UNIT, MACRO_PER_UNIT)
            },
        )

    /**
     * True when the unit box, as it stands and as Save would store it, names the millilitre: the per-one boxes
     * are then typed and shown per 100 ml, and stored per one ml (D56).
     */
    val perHundredMl: Boolean get() = PerHundredMillilitres.applies(unitName)

    /**
     * True when the form may offer to count the food in millilitres with one tap (public issue #5):
     * the unit box does not already name the millilitre, and the four per-one boxes are empty. With a
     * figure in them the offer stands aside, because changing the unit under it would silently turn
     * a figure typed per bar into one per 100 ml; typing ml by hand stays possible, and the group's
     * heading then says per 100 ml as it is typed.
     */
    val offersMillilitres: Boolean get() = !perHundredMl && perUnitTyped.all { it.isBlank() }

    /** True when he has started filling the group in, which is when it has to be finished. */
    val wantsPer100g: Boolean get() = per100gTyped.any { it.isNotBlank() }

    val wantsPerUnit: Boolean get() = perUnitTyped.any { it.isNotBlank() } || unitName.isNotBlank()

    val wantsWeight: Boolean get() = gramsPerUnit.isNotBlank()

    /**
     * A group is refused as one answer, under its calories box, as it always has been — so since
     * D42 (issue #32) its one sentence names every ceiling in it, having to cover whichever of the
     * four is wrong. The numbers come from [BelievableAmount], never written a second time.
     */
    fun errors(): Map<FoodField, String> = buildMap {
        if (runCatching { FoodKeys.nameKey(name) }.isFailure) {
            put(FoodField.NAME, "What is it called?")
        }

        // Present as a group or absent as a group. Three of four filled in is a half-answer, and a
        // food that knows its calories and not its protein would show a blank where a number
        // belongs on every screen that reads it.
        if (wantsPer100g && per100gJudged.any { (typed, most) -> number(typed, most) == null }) {
            put(
                FoodField.PER_100G,
                allFour("100 g", KCAL_PER_100G, MACRO_PER_100G) + ", or leave them all empty.",
            )
        }
        if (wantsPerUnit) {
            if (unitName.isBlank()) {
                put(FoodField.UNIT_NAME, "One what? A bar, a slice, an egg.")
            }
            if (perUnitJudged.any { (typed, most) -> number(typed, most) == null }) {
                put(
                    FoodField.PER_UNIT,
                    if (perHundredMl) {
                        allFour(PerHundredMillilitres.PER, KCAL_PER_100G, MACRO_PER_100G)
                    } else {
                        allFour("unit", KCAL_PER_UNIT, MACRO_PER_UNIT)
                    } + ", or leave them all empty.",
                )
            }
        }
        val weight = number(gramsPerUnit, GRAMS)
        if (wantsWeight && (weight == null || weight == 0.0)) {
            put(
                FoodField.WEIGHT,
                "What one of it weighs, in grams (at most ${words(GRAMS)}), or leave it empty.",
            )
        }

        // The one invariant across the three: a food that knows neither what 100 g of it are worth
        // nor what one of it is worth cannot be logged, cannot be costed inside a meal, and is a
        // name with nothing behind it.
        if (!wantsPer100g && !wantsPerUnit) {
            put(
                FoodField.NOTHING_KNOWN,
                "A food has to know what 100 g of it are worth, or what one of it is worth.",
            )
        }
    }

    /**
     * The facts as the owner has just stated them, or null while the form is not yet answerable.
     *
     * @param setAtMillis when he stated them, for showing beside the number later. Never for
     *   choosing between two sources: that is by rank and never by date.
     * @param estimated the groups he accepted from a review this session (D54). Each is handed over
     *   labelled by its weakest member ([AcceptedGroup.provenance]); every other group, and the
     *   weight always, as typed.
     * @param stored the food as it is stored, or null for a food not yet made. A box still showing a
     *   stored figure as it opened hands over the stored figure, not its rounding ([figure]), so
     *   Save finds the group unchanged and leaves it — figures and source — alone (D54 §8.5).
     */
    fun toFacts(
        setAtMillis: Long,
        estimated: Map<FactGroup, AcceptedGroup> = emptyMap(),
        stored: FoodFacts? = null,
    ): FoodFacts? {
        if (errors().isNotEmpty()) return null
        val typed = Provenance(Source.TYPED, confidence = null, setAtMillis = setAtMillis)
        fun provenanceOf(group: FactGroup): Provenance =
            estimated[group]?.provenance(setAtMillis) ?: typed
        return runCatching {
            FoodFacts(
                per100g = if (wantsPer100g) {
                    PerHundredGrams(
                        per100gFigures(stored?.per100g?.nutrients)!!,
                        provenanceOf(FactGroup.PER_100G),
                    )
                } else {
                    null
                },
                perUnit = if (wantsPerUnit) {
                    PerUnit(
                        FoodKeys.displayName(unitName),
                        perUnitFigures(stored?.perUnit?.nutrients)!!,
                        provenanceOf(FactGroup.PER_UNIT),
                    )
                } else {
                    null
                },
                // Never computed from the other two, whatever they say. That arithmetic looks valid
                // and produces a claim about a physical object out of two estimates.
                gramsPerUnit = if (wantsWeight) {
                    GramsPerUnit(figure(gramsPerUnit, stored?.gramsPerUnit?.grams, GRAMS)!!, typed)
                } else {
                    null
                },
            )
        }.getOrNull()
    }

    /** The four per-100 g figures, or null while the group is empty, half filled or refused. */
    fun per100gFigures(held: Nutrients? = null): Nutrients? = figures(per100gJudged, held)

    /**
     * The four per-one figures **as stored** — per one ml for a food counted in millilitres, whose
     * boxes are per 100 ml (D56) — or null while the group is empty, half filled or refused, or names
     * no unit, since "90 kcal" of nothing is not a figure.
     */
    fun perUnitFigures(held: Nutrients? = null): Nutrients? =
        if (unitName.isBlank()) {
            null
        } else if (perHundredMl) {
            figures(perUnitJudged, held, PerHundredMillilitres::shown, PerHundredMillilitres::stored)
        } else {
            figures(perUnitJudged, held)
        }

    /**
     * The four per-one figures **as the boxes state them** — per 100 ml for a food counted in
     * millilitres — or null as [perUnitFigures] is. What a review is asked about (D54, D56), so the
     * model reads the carton's figures and answers at the scale it will be shown in.
     */
    fun perUnitFiguresAsShown(held: Nutrients? = null): Nutrients? =
        perUnitFigures(held)?.let { if (perHundredMl) PerHundredMillilitres.shown(it) else it }

    /** What one weighs, or null while the box is empty or refused. */
    fun weightFigure(held: Double? = null): Double? = figure(gramsPerUnit, held, GRAMS)?.takeIf { it > 0.0 }

    /** The unit name as Save stores it, or null when none is named. */
    fun unitNameAsSaved(): String? = runCatching { FoodKeys.displayName(unitName) }.getOrNull()

    /**
     * The four boxes of [group] filled with [nutrients] — never the unit name, never the weight —
     * written as a stored figure is written into the form (D54: accepting a review's suggestion).
     *
     * [nutrients] are at the boxes' own scale: per 100 ml for a food counted in millilitres (D56),
     * which is the scale its review was asked and answered in ([perUnitFiguresAsShown]).
     */
    fun with(group: FactGroup, nutrients: Nutrients): FoodForm = when (group) {
        FactGroup.PER_100G -> copy(
            kcalPer100g = nutrients.kcal.asTyped(),
            proteinPer100g = nutrients.proteinG.asTyped(),
            carbsPer100g = nutrients.carbsG.asTyped(),
            fatPer100g = nutrients.fatG.asTyped(),
        )
        FactGroup.PER_UNIT -> copy(
            kcalPerUnit = nutrients.kcal.asTyped(),
            proteinPerUnit = nutrients.proteinG.asTyped(),
            carbsPerUnit = nutrients.carbsG.asTyped(),
            fatPerUnit = nutrients.fatG.asTyped(),
        )
    }

    /**
     * A group's four figures as stored. [toBox] turns a stored figure into the box's scale and
     * [fromBox] a typed one back — the identity but for a food counted in millilitres (D56). A box
     * still reading its [held] figure as shown hands back [held] itself, untouched by either.
     */
    private fun figures(
        judged: List<Pair<String, Double>>,
        held: Nutrients?,
        toBox: (Double) -> Double = { it },
        fromBox: (Double) -> Double = { it },
    ): Nutrients? {
        val holds = held?.let { listOf(it.kcal, it.proteinG, it.carbsG, it.fatG) }
        val read = judged.mapIndexed { i, (typed, most) ->
            val keep = holds?.get(i)
            if (keep != null && heldAsShown(typed, toBox(keep), most)) {
                keep
            } else {
                number(typed, most)?.let(fromBox) ?: return null
            }
        }
        return runCatching { Nutrients(read[0], read[1], read[2], read[3]) }.getOrNull()
    }

    companion object {
        /**
         * "All four per 100 g (at most 1000 kcal, and 110 g of protein, carbohydrate or fat)" — the
         * group refusal, without its ending. Shared with the describe screen's worth boxes (D53 §6),
         * so four figures typed anywhere are refused in one sentence naming the same ceilings.
         */
        fun allFour(per: String, kcalMost: Double, macroMost: Double): String =
            "All four per $per (at most ${words(kcalMost)} kcal, and " +
                "${words(macroMost)} g of protein, carbohydrate or fat)"

        /** A food opened for editing, as the fields it fills. */
        fun of(food: Food): FoodForm {
            // Per 100 ml for a food counted in millilitres, as its boxes are (D56); stored per ml.
            val perUnit = food.facts.perUnit?.let { held ->
                if (PerHundredMillilitres.applies(held.unitName)) {
                    PerHundredMillilitres.shown(held.nutrients)
                } else {
                    held.nutrients
                }
            }
            return of(food, perUnit)
        }

        private fun of(food: Food, perUnit: Nutrients?): FoodForm = FoodForm(
            name = food.name,
            brand = FoodKeys.brandKey(food.brand)
                .takeIf { it != FoodKeys.NO_BRAND_KEY }
                ?.let { food.brand }
                .orEmpty(),
            kcalPer100g = food.facts.per100g?.nutrients?.kcal.asTyped(),
            proteinPer100g = food.facts.per100g?.nutrients?.proteinG.asTyped(),
            carbsPer100g = food.facts.per100g?.nutrients?.carbsG.asTyped(),
            fatPer100g = food.facts.per100g?.nutrients?.fatG.asTyped(),
            unitName = food.facts.perUnit?.unitName.orEmpty(),
            kcalPerUnit = perUnit?.kcal.asTyped(),
            proteinPerUnit = perUnit?.proteinG.asTyped(),
            carbsPerUnit = perUnit?.carbsG.asTyped(),
            fatPerUnit = perUnit?.fatG.asTyped(),
            gramsPerUnit = food.facts.gramsPerUnit?.grams.asTyped(),
        )

        /** A stored number as a field to edit, [shown]; empty for a figure the food does not know. */
        private fun Double?.asTyped(): String = this?.let(::shown).orEmpty()

        /**
         * A figure as its box shows it: at most two decimals, trailing zeros trimmed (D54 §8.5).
         *
         * Whole where it is whole, so a yoghurt of 72 kcal does not read "72.0" and invite him to
         * wonder what the zero is doing there. A scanned food's per-100 g figures are a serving's
         * scaled and are stored as long doubles, which no one can read or would type; the box shows
         * 8.57. **Precision is not lost by it**: a box still reading exactly this is read back as
         * the stored figure ([figure]), so saving an untouched group changes nothing.
         */
        fun shown(value: Double): String =
            BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    /**
     * Negative is not a quantity of food, and neither is a word — nor "Infinity", "1e999" or a
     * figure past what its box can hold, [most] (D42, issue #32). The parse is the one this form
     * always had, a comma read as a decimal point; only the judgement after it is shared.
     */
    /**
     * The one rule, for Save and for what a review is told (D54 §8.5): a box whose text is exactly
     * how [held] is [shown] is still [held], the stored figure, not its rounding — so a group left
     * as it opened is found unchanged. Anything else is read as typed. [held] is null for a food not
     * yet made, or a group it does not know.
     */
    private fun figure(typed: String, held: Double?, most: Double): Double? {
        if (held != null && heldAsShown(typed, held, most)) return held
        return number(typed, most)
    }

    /** [typed] is exactly how [held], at the box's scale, is [shown] — and [held] is believable. */
    private fun heldAsShown(typed: String, held: Double, most: Double): Boolean =
        BelievableAmount.isBelievable(held, most) && typed.trim().replace(',', '.') == shown(held)

    private fun number(typed: String, most: Double): Double? =
        typed.trim().replace(',', '.').toDoubleOrNull()
            ?.takeIf { BelievableAmount.isBelievable(it, most) }
}
