package com.metaself.app.ui.food

import com.metaself.app.data.food.EditRefused
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.CannotCount
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.PerHundredMillilitres
import com.metaself.app.domain.portion.Portions
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * The sentences about a food.
 *
 * Pure and in `ui/`, the division this project has used since the version marker: the domain
 * produces numbers and never reaches for a phrase.
 *
 * **A food may now have two things to say about itself rather than one of two possible things** —
 * "72 kcal per 100 g" and "190 kcal per bar" — which is the visible cost of storing what it actually
 * knows instead of forcing it to pick. They are kept as separate strings rather than joined into a
 * sentence, for the reason the day's list already gives: a Hebrew name and a Latin figure in one
 * string are two runs of opposite direction and the bidirectional algorithm is entitled to reorder
 * them.
 */
object FoodWording {

    /** Every way this food can say what it is worth, in the order it would be counted. */
    fun whatItKnows(food: Food): List<String> =
        listOfNotNull(per100g(food), perUnit(food), weighs(food))

    /**
     * What a food is, in one line, as its row in *My foods* and the head of its page say it (D55
     * §1): the brand or [NO_BRAND]; the first way of counting it knows, in [whatItKnows]'s order;
     * and what one weighs, when it knows — "No brand · 100 kcal per 100 g · one cup is 150 g".
     *
     * **Parts, never one string**, for the reason this object's KDoc gives: a Hebrew brand and a
     * Latin figure are two runs of opposite direction. The screen draws each as its own text with
     * the ` · ` between them. A food knowing both ways of counting shows per 100 g only; its page
     * shows the rest.
     */
    fun summary(food: Food): List<String> = listOfNotNull(
        food.realBrand ?: NO_BRAND,
        per100g(food) ?: perUnit(food),
        weighs(food),
    )

    private fun per100g(food: Food): String? =
        food.facts.per100g?.let { "${grouped(it.nutrients.kcal)} kcal per 100 g" }

    private fun perUnit(food: Food): String? =
        food.facts.perUnit?.let { perOne(it.nutrients.kcal, it.unitName) }

    /**
     * "190 kcal per bar" — or, for a food counted in millilitres, "57 kcal per 100 ml" (D56): a
     * figure stored per one ml is said per 100 ml, as the carton says it and the food's page shows it.
     */
    internal fun perOne(kcal: Double, unitName: String): String =
        "${kcalPerOne(kcal, unitName)} kcal per ${PerHundredMillilitres.per(unitName)}"

    /** [kcal] stored per one [unitName], grouped as it is said: per 100 ml for a millilitre (D56). */
    internal fun kcalPerOne(kcal: Double, unitName: String): String =
        grouped(if (PerHundredMillilitres.applies(unitName)) PerHundredMillilitres.shown(kcal) else kcal)

    private fun weighs(food: Food): String? = food.facts.gramsPerUnit?.let { weight ->
        val unit = food.facts.perUnit?.unitName ?: FoodFacts.PORTION
        "one $unit is ${grouped(weight.grams)} g"
    }

    /** What [summary] says of a food with no brand, where `NA` would otherwise be printed. */
    const val NO_BRAND = "No brand"

    /**
     * The brand, when it is one — `NA` is the brand of food with no brand and is not worth saying.
     *
     * [Food.realBrand], so the row prints exactly the brand the search looks at (D41): typing what
     * he can read finds the row, and nothing he cannot read does.
     */
    fun brand(food: Food): String? = food.realBrand

    /** The other names it answers to, which is what merging two duplicates leaves behind. */
    fun alsoKnownAs(food: Food): String? =
        food.alsoKnownAs.takeIf { it.isNotEmpty() }?.joinToString(", ")

    /**
     * Why a way of counting is not on offer — said out loud, where the field would be.
     *
     * Decision 2's "nothing is ever guessed" made visible rather than merely obeyed. A greyed-out
     * field with no explanation looks like a fault in the app; the same field with a sentence and
     * somewhere to type the missing number is an invitation.
     */
    fun why(reason: CannotCount): String = when (reason) {
        // A food counted in ml is never asked what one ml weighs (D56): the page draws no box for
        // it, so the reason names what would switch weighing on — its per 100 g — instead.
        is CannotCount.NothingKnowsWhatOneWeighs ->
            if (PerHundredMillilitres.applies(reason.unitName)) {
                "Nothing says what 100 g of it are worth"
            } else {
                "Nothing knows what one ${reason.unitName} weighs"
            }
        CannotCount.NothingSaysWhatOneIs ->
            "Nothing has said what one of this is"
    }

    /**
     * [why], as the reason of the option it switches off: "Count portion: nothing has said what one
     * of this is". Named by the option's own label so it reads as that option's excuse, not as a
     * remark about the food.
     */
    fun whyNot(option: String, reason: CannotCount): String =
        "$option: " + why(reason).replaceFirstChar { it.lowercase() }

    /** "180 g" or "2 bars" — what is about to be logged, as words. */
    fun amount(amount: Double, unit: String): String = Portions.words(amount, unit)

    /**
     * Where a number came from, for the screen that asks the owner to accept it.
     *
     * Null for a number he typed himself, deliberately: an estimate is never presented as a
     * measurement, and the corollary is that a measurement should not be dressed up as anything at
     * all. A badge on every one of his own entries would be noise and would make the estimate badge
     * look like a category rather than a warning.
     */
    fun origin(source: Source, confidence: Confidence?): String? = when (source) {
        Source.TYPED -> null
        Source.LABEL -> "off the packet"
        Source.AI_ESTIMATE -> when (confidence) {
            Confidence.LOW -> "a rough estimate"
            Confidence.MEDIUM -> "an estimate"
            Confidence.HIGH -> "a close estimate"
            null -> "an estimate"
        }
        Source.REPEATED -> "copied from a past meal"
        Source.UNRECOGNISED -> "from a version this one does not understand"
    }

    /**
     * How far a food's own facts are from agreeing, when it knows enough for them to disagree.
     *
     * Shown rather than reconciled. The app has no way to know which of the three is the wrong one,
     * and picking one would be exactly the silent guess the whole model exists to avoid — so it says
     * what it sees and lets the owner fix whichever he knows to be wrong.
     *
     * Null below five per cent, which is rounding and arithmetic rather than a disagreement worth a
     * sentence.
     */
    fun disagreement(food: Food): String? {
        val apart = food.disagreement ?: return null
        val percent = (apart * 100).roundToInt().absoluteValue
        if (percent < WORTH_SAYING_PERCENT) return null
        val unit = food.facts.perUnit?.unitName ?: FoodFacts.PORTION
        return "What one $unit is worth and what 100 g are worth disagree by about $percent%"
    }

    /**
     * Why the last thing he tried was not done, naming what stood in the way.
     *
     * A refusal he cannot act on is just a failure. Each of these names the meals, or the food, that
     * he has to deal with first — so the sentence is the next step rather than a dead end.
     */
    fun refusal(why: EditRefused): String = when (why) {
        is EditRefused.UsedBySavedMeals ->
            "${listed(why.meals)} ${uses(why.meals)} this. Change the meal, or hide this instead."
        is EditRefused.NeededBySavedMeals ->
            "${listed(why.meals)} ${counts(why.meals)} this in units. Change the meal, or keep the numbers."
        is EditRefused.AlreadyAnotherFood ->
            "Another food is already called “${why.name}”. Join the two, or pick a different name."
        EditRefused.WouldLeaveNothingKnown ->
            "A food has to know what 100 g of it are worth, or what one of it is worth."
        is EditRefused.MealsHoldingBoth ->
            "${listed(why.meals)} ${holds(why.meals)} both of these. Take one out of it first."
    }

    private fun listed(names: List<String>): String = when (names.size) {
        0 -> "A meal"
        1 -> "“${names.single()}”"
        2 -> "“${names[0]}” and “${names[1]}”"
        else -> names.dropLast(1).joinToString(", ") { "“$it”" } + " and “${names.last()}”"
    }

    private fun uses(names: List<String>) = if (names.size == 1) "uses" else "use"

    private fun counts(names: List<String>) = if (names.size == 1) "counts" else "count"

    private fun holds(names: List<String>) = if (names.size == 1) "holds" else "hold"

    /**
     * A food's calories as they are printed, everywhere they are printed.
     *
     * `internal` rather than private since issue #13, so the sentence saying a food's figures have
     * changed rounds them by exactly the rule the food list rounds them by: one figure cannot print
     * as 15 in the list and 15.4 in the notice, and "changed from 15 to 15" cannot happen.
     */
    internal fun grouped(value: Double): String {
        val whole = value.roundToInt()
        // Locale.US, as `RevisionWording` groups: the sentences sit beside each other on the day
        // screen, and a device locale deciding that one of them separates thousands with a dot
        // would make two app voices out of one.
        return if (whole >= 1_000) String.format(Locale.US, "%,d", whole) else whole.toString()
    }

    /** Below this, the two facts differ by rounding rather than by anything the owner should fix. */
    private const val WORTH_SAYING_PERCENT = 5
}
