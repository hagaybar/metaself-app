package com.metaself.app.domain.food

import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.portion.Portions

/**
 * What a day's logged rows would be as a meal (design §3.6).
 *
 * One object, one function, no Android and no database, so the day screen and any later caller share
 * it rather than each deciding for itself what a logged row means.
 *
 * **The arithmetic of logging, used backwards.** Grams were weighed, the food's own unit was counted,
 * and a row with no portion at all is one of whatever the food calls one — which for a food that has
 * never known better is one "portion". No new rule is invented here, which is the whole point: a
 * conversion with a rule of its own would drift from the one that put the numbers on the row in the
 * first place. It follows that a unit the arithmetic cannot use — a kilogram, a millilitre, a
 * tablespoon of something counted in spoons — is refused rather than read as something it is not,
 * because the conversion that would rescue it does not exist anywhere in this app and D4 does not
 * allow one to be invented for the occasion.
 *
 * **One component per food, not one per row**, because that is what a meal can hold: its parts are
 * keyed on the food. Rows of one food in one unit add up; rows of one food in two units are refused.
 *
 * **Nothing is guessed and nothing is silently dropped.** A row that cannot become a component is
 * named, with its reason, and the caller shows it rather than making half a meal.
 */
object MealFromDay {

    /**
     * One part of a meal: its food, its amount, and how that amount counts.
     *
     * One per FOOD rather than one per row. The day may hold the same coffee twice and a meal cannot
     * — its parts are keyed on the food — so the rows of one food arrive here added together.
     */
    data class Component(val food: Food, val amount: Double, val countedAs: CountedAs)

    /**
     * What the rows came to, and what stood in the way.
     *
     * Both at once rather than one or the other: the caller needs the refusals to name them, and the
     * components are what it would have built had there been none. A refusal stops the whole meal —
     * half a meal is worse than none — and that decision belongs to the caller, not here.
     */
    data class Outcome(val components: List<Component>, val refusals: List<String>)

    /**
     * What these logged rows would be as a meal, in the order they were given.
     *
     * [foodsById] is a lookup rather than a repository so that this stays pure: the caller has
     * already read whatever store it uses, and a row whose food has since been deleted arrives here
     * as a pointer that answers with null, which is the same case as a row attached to nothing.
     *
     * **The sentences are built here rather than in `ui/`**, against this project's usual division,
     * because a refusal is part of the answer: an `Outcome` whose refusals had to be worded
     * elsewhere could be returned, ignored, and the meal made anyway.
     *
     * **There is no `day_meal_refused` string, and there is nothing for one to hold.** A refusal
     * arrives already a finished sentence, naming a row and its reason, so there is no template left
     * to fill — a string resource could only be `%1$s`.
     *
     * **What is exceptional here is the package, not the absence from `strings.xml`.** Plenty of
     * what the owner reads is built in Kotlin: every `*Wording` object in `ui/` — `MealWording`,
     * `FoodWording`, `EncouragementWording` and the rest — is a finished sentence too, because a
     * view model cannot reach for `stringResource`. Those at least sit in `ui/`, on the presentation
     * side of the line. These refusals are the one place this app words a sentence in `domain/`, for
     * the reason above. All of it is affordable only while the app ships one language.
     */
    fun from(items: List<FoodItem>, foodsById: (Long) -> Food?): Outcome = outcome(items, foodsById, described = false)

    /**
     * [from], for a described meal about to be kept as a meal without being logged (D58 §5.2,
     * §12.7): the same rules, and refusals that never say anything was logged, since nothing was.
     */
    fun fromDescribed(items: List<FoodItem>, foodsById: (Long) -> Food?): Outcome =
        outcome(items, foodsById, described = true)

    private fun outcome(items: List<FoodItem>, foodsById: (Long) -> Food?, described: Boolean): Outcome {
        val components = mutableListOf<Component>()
        val refusals = mutableListOf<String>()

        items.forEach { item ->
            // A meal is made of foods and amounts, so a row attached to no food cannot join one.
            // Rare — everything logged since the foods release attaches on the way in — and when it
            // happens the row is named rather than dropped (design §3.5).
            val food = item.foodId?.let(foodsById)
            if (food == null) {
                refusals += if (described) {
                    "${item.label} could not be matched to a food."
                } else {
                    "${item.label} is not attached to a food yet."
                }
                return@forEach
            }

            val countedAs = countedAs(item, food)
            if (countedAs == null) {
                refusals += cannotReadTheUnit(item, food, described)
                return@forEach
            }
            // A row with NO PORTION AT ALL is one of whatever the food calls one. Truthful about
            // what is known, rather than a guess at how much there was.
            //
            // The unit has to be empty too, and not merely the amount: that is what "no portion at
            // all" means here, and it is the same question `Portions.canScale` asks everywhere else
            // in the app before offering a control for one. A row that NAMES a unit and no amount —
            // which the describe path really produces, from a reply with a unit and no number — is a
            // weighed row whose amount was never stated, and reading it as one of the unit made a
            // 250 kcal row into one gram, silently. So it is refused instead.
            val amount = amountOf(item)
            if (amount == null) {
                // Names the way out, not only the problem (issue #23): the refusal was the first
                // and only sign, and the fix — typing the amount the model left out — was one tap
                // away on the day with nothing saying so.
                refusals += if (described) {
                    "${item.label} has no amount. Say how much, and it can join."
                } else {
                    "${item.label} was logged in ${item.portionUnit.trim()}, " +
                        "and nothing said how much. Open it on the day and say how much, " +
                        "and it can join."
                }
                return@forEach
            }

            when (val worth = Logging.log(food.facts, amount, countedAs)) {
                is LoggedFrom.Numbers -> components += Component(food, amount, countedAs)
                is LoggedFrom.NotOnOffer ->
                    refusals += "${item.label} cannot go into a meal: ${why(worth.why)}."
            }
        }

        return gatherPerFood(components, refusals, described)
    }

    /**
     * How much this row was, or null if it named a unit and never said.
     *
     * The only amount invented here is the one for a row with no portion whatever, where 1.0 is not
     * a quantity guessed at but the food's own word for one of it (design §3.6). A row that names a
     * unit has a quantity — it simply was not recorded — and supplying one would be exactly the
     * number the owner never gave that D4 forbids.
     */
    private fun amountOf(item: FoodItem): Double? = when {
        item.portionAmount > 0.0 -> item.portionAmount
        item.portionUnit.isBlank() -> 1.0
        else -> null
    }

    /**
     * Which of the food's two ways of counting this row's amount is in — or null if neither.
     *
     * **The unit on the row decides, and it has to be a unit the arithmetic can actually use.**
     * `Logging` divides a weighed amount by 100 AS GRAMS and multiplies a counted one by what ONE of
     * the food is worth, so a unit passed through unchecked is a number silently read as something
     * it is not: half a kilo logged as 0.5 became half a gram, worth nothing, and worth nothing is
     * a number — no refusal fired. Three tablespoons of an oil counted in spoons became three
     * spoons the same way, the unit discarded and only the number reused.
     *
     * **There is no conversion here and none is invented.** This app has no factor between a
     * kilogram and a gram anywhere, and writing one in for this would be exactly the arithmetic D4
     * forbids — a number the owner never gave, presented afterwards as what he logged. So a unit
     * that is not the gram and not the food's own unit is refused, by name.
     */
    private fun countedAs(item: FoodItem, food: Food): CountedAs? {
        val unit = item.portionUnit.trim()
        return when {
            // No unit at all: one of whatever the food calls one, which is the case the row with no
            // portion has always been.
            unit.isEmpty() -> CountedAs.UNITS
            Portions.isGrams(unit) -> CountedAs.GRAMS
            // The food's own unit, asked BEFORE the mass units and not after. The unit field in the
            // foods editor is free text, so what one of a food is counted in can be "ml" — or any
            // other word that also appears in the mass list — and such a row needs no conversion at
            // all: it is already in the unit `Logging` counts this food by.
            unitKeyOf(unit) == unitKeyOf(countsIn(food)) -> CountedAs.UNITS
            // Measured out, but in something this app cannot turn into grams.
            Portions.isMass(unit) -> null
            else -> null
        }
    }

    /** Why that row's amount could not be read, naming the row and both units. */
    private fun cannotReadTheUnit(item: FoodItem, food: Food, described: Boolean): String {
        val unit = item.portionUnit.trim()
        if (described) {
            return if (Portions.isMass(unit)) {
                "${item.label} is in $unit, and nothing here turns $unit into grams."
            } else {
                "${item.label} is in $unit, and your ${food.name} is counted in ${countsIn(food)}."
            }
        }
        return if (Portions.isMass(unit)) {
            "${item.label} was logged in $unit, and nothing here turns $unit into grams."
        } else {
            "${item.label} was logged in $unit, and it is counted in ${countsIn(food)}."
        }
    }

    /** What ONE of this food is, as `Logging` would count it. */
    private fun countsIn(food: Food): String = food.facts.perUnit?.unitName ?: FoodFacts.PORTION

    /** Two spellings of one unit are one unit, by the rule `DerivedFoods` already groups them by. */
    private fun unitKeyOf(unit: String): String =
        runCatching { FoodKeys.nameKey(unit) }.getOrElse { unit.trim().lowercase() }

    /**
     * One component per FOOD, not one per row.
     *
     * A meal stores its parts keyed on the food — putting one in twice changes the amount rather
     * than making a second row — so two rows of one coffee arrived as two components and the second
     * overwrote the first. The meal came out worth one coffee where the sheet had just totalled two,
     * and nothing said so: the damage only showed when he logged the meal and it came out half size.
     *
     * **Rows of one food add up when they are in the same units, and are refused by name when they
     * are not.** Summing two amounts in one unit invents nothing — it is the addition he would have
     * done himself — and the merged component is worth exactly what the two rows were worth, which
     * is what the naming sheet tells him before he confirms; the amounts are added exactly, though
     * the merged component is costed once rather than twice, so it can sit a rounded calorie away
     * from the two rows costed separately. 100 g of bread at breakfast and 2
     * slices at lunch are a different matter: adding them needs what one slice weighs, and a food
     * that happens to know would still be having one of his two rows re-counted behind his back. So
     * the food is named and nothing is made, which is the whole-or-nothing rule this path already
     * applies to every other thing that stands in the way.
     *
     * Order is the day's: each food sits where it was first eaten.
     */
    private fun gatherPerFood(components: List<Component>, refusals: List<String>, described: Boolean): Outcome {
        val byFood = LinkedHashMap<Long, MutableList<Component>>()
        components.forEach { byFood.getOrPut(it.food.id) { mutableListOf() } += it }

        val gathered = mutableListOf<Component>()
        val stillRefused = refusals.toMutableList()
        byFood.values.forEach { ofOneFood ->
            val first = ofOneFood.first()
            if (ofOneFood.any { it.countedAs != first.countedAs }) {
                stillRefused += if (described) {
                    "${first.food.name} is here both weighed and counted, " +
                        "and a meal holds it one way or the other."
                } else {
                    "${first.food.name} is on this day both weighed and counted, " +
                        "and a meal holds it one way or the other."
                }
            } else {
                gathered += first.copy(amount = ofOneFood.sumOf { it.amount })
            }
        }

        return Outcome(components = gathered, refusals = stillRefused)
    }

    /**
     * The food's own reason, in the sentence that names the row.
     *
     * Deliberately the same two facts `FoodWording.why` states, in lower case because it lands
     * mid-sentence here. The duplication is one clause wide and the alternative is a domain object
     * reaching into `ui/`, which is the dependency this project does not have.
     */
    private fun why(reason: CannotCount): String = when (reason) {
        is CannotCount.NothingKnowsWhatOneWeighs ->
            "nothing knows what one ${reason.unitName} weighs"
        CannotCount.NothingSaysWhatOneIs ->
            "nothing has said what one of it is"
    }
}
