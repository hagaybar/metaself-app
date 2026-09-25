package com.metaself.app.domain.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Source
import org.junit.jupiter.api.Test

/**
 * What a day's rows would be as a meal (design §3.6).
 *
 * The arithmetic of logging, used backwards: a mass unit was weighed, anything else was counted, and
 * a row with no portion at all is one of whatever the food calls one. No new rule is invented here,
 * which is the whole point — a conversion with a rule of its own would drift from the one that put
 * the numbers on the row in the first place.
 *
 * **Nothing is guessed and nothing is silently dropped.** A row that cannot become a component is
 * named, with its reason, and the caller shows it rather than making half a meal.
 *
 * Pure: no Android, no database, so it runs on this machine as well as in CI.
 */
class MealFromDayTest {

    private val cucumber = Food(
        id = 1,
        name = "Cucumber",
        facts = FoodFacts(
            per100g = PerHundredGrams(
                Nutrients(16.0, 0.7, 3.6, 0.1),
                Provenance(Source.TYPED, null, 0),
            ),
        ),
    )

    /**
     * Knows what one slice is worth and what one weighs, so it can be counted either way — which is
     * why the row's own unit is what decides, and not the food.
     */
    private val bread = Food(
        id = 2,
        name = "Bread",
        facts = FoodFacts(
            perUnit = PerUnit(
                "slice",
                Nutrients(80.0, 3.0, 15.0, 1.0),
                Provenance(Source.TYPED, null, 0),
            ),
            gramsPerUnit = GramsPerUnit(30.0, Provenance(Source.TYPED, null, 0)),
        ),
    )

    /**
     * All this food knows is that one unnamed portion of it had these calories — what the conversion
     * from the old record leaves behind for a row that never said how much there was.
     */
    private val leftovers = Food(
        id = 3,
        name = "Leftovers",
        facts = FoodFacts(
            perUnit = PerUnit(
                FoodFacts.PORTION,
                Nutrients(420.0, 20.0, 40.0, 18.0),
                Provenance(Source.AI_ESTIMATE, Confidence.LOW, 0),
            ),
        ),
    )

    /** Knows what one spoon is worth and nothing about what one weighs, so it cannot be weighed. */
    private val oil = Food(
        id = 4,
        name = "Olive oil",
        facts = FoodFacts(
            perUnit = PerUnit(
                "spoon",
                Nutrients(119.0, 0.0, 0.0, 13.5),
                Provenance(Source.TYPED, null, 0),
            ),
        ),
    )

    /**
     * Counted in millilitres, because the foods editor's unit field is free text: its hint is "One
     * what? A bar, a slice, an egg" and nothing stops "ml" being typed in it. So a food really can
     * count in a word that also appears in the list of units this app measures substances by.
     */
    private val milk = Food(
        id = 5,
        name = "Milk",
        facts = FoodFacts(
            perUnit = PerUnit(
                "ml",
                Nutrients(0.64, 0.034, 0.05, 0.033),
                Provenance(Source.TYPED, null, 0),
            ),
        ),
    )

    private val everyFood = listOf(cucumber, bread, leftovers, oil, milk)

    private val foodsById: (Long) -> Food? = { id -> everyFood.firstOrNull { it.id == id } }

    @Test
    fun `a row weighed in grams becomes a component weighed in grams`() {
        val outcome = MealFromDay.from(
            listOf(row(name = "Cucumber", foodId = 1, amount = 150.0, unit = "g")),
            foodsById,
        )

        assertThat(outcome.refusals).isEmpty()
        val component = outcome.components.single()
        assertThat(component.food).isEqualTo(cucumber)
        assertThat(component.amount).isEqualTo(150.0)
        assertThat(component.countedAs).isEqualTo(CountedAs.GRAMS)
    }

    @Test
    fun `a row counted in slices becomes a component counted in whole ones`() {
        val outcome = MealFromDay.from(
            listOf(row(name = "Bread", foodId = 2, amount = 2.0, unit = "slice")),
            foodsById,
        )

        assertThat(outcome.refusals).isEmpty()
        val component = outcome.components.single()
        assertThat(component.amount).isEqualTo(2.0)
        assertThat(component.countedAs).isEqualTo(CountedAs.UNITS)
    }

    /**
     * A row that never recorded an amount is one of whatever the food calls one — which for a food
     * that has never known better is one "portion". Truthful about what is known, rather than a
     * guess at what the thing was.
     */
    @Test
    fun `a row with no portion at all is one of whatever the food calls one`() {
        val outcome = MealFromDay.from(
            listOf(row(name = "Leftovers", foodId = 3, amount = 0.0, unit = "")),
            foodsById,
        )

        assertThat(outcome.refusals).isEmpty()
        val component = outcome.components.single()
        assertThat(component.amount).isEqualTo(1.0)
        assertThat(component.countedAs).isEqualTo(CountedAs.UNITS)

        // And what it is called is the food's own word for one of it, not a number invented here.
        val worth = Logging.log(component.food.facts, component.amount, component.countedAs)
        assertThat((worth as LoggedFrom.Numbers).unit).isEqualTo(FoodFacts.PORTION)
        assertThat(worth.kcal).isEqualTo(420)
    }

    /**
     * A row that names a unit but never says how much is refused, not read as ONE of that unit.
     *
     * Reachable from the ordinary describe path: the model answers a row with `"unit": "g"` and no
     * `"amount"`, and the amount is stored as zero while the unit is kept verbatim — the row's own
     * portion words say "amount not stated". The fallback to one of whatever the food calls one is
     * only meaningful where there is no portion at all, so landing it on a WEIGHED row made it ONE
     * GRAM: a 250 kcal row stored as a component worth about two, with no refusal, while the naming
     * sheet directly above the button still said 250. Every other consumer asks `Portions.canScale`
     * first and treats such a row as having no portion whatever.
     */
    @Test
    fun `a row that names a unit but no amount is refused by name`() {
        val outcome = MealFromDay.from(
            listOf(
                row(
                    name = "Cucumber",
                    foodId = 1,
                    amount = 0.0,
                    unit = "g",
                    portion = "amount not stated",
                ),
            ),
            foodsById,
        )

        assertThat(outcome.components).isEmpty()
        assertThat(outcome.refusals).hasSize(1)
        assertThat(outcome.refusals.single()).contains("Cucumber")
        assertThat(outcome.refusals.single()).contains("g")
        // And says how to put it right (issue #23): the refusal was the first and only sign, and it
        // named the problem without the way out.
        assertThat(outcome.refusals.single()).contains("Open it on the day and say how much")
    }

    /**
     * A meal is made of foods and amounts, so a row attached to no food cannot join one. Rare —
     * everything logged since the foods release attaches on the way in — and when it happens the row
     * is named rather than dropped (design §3.5).
     */
    @Test
    fun `a row not attached to any food is refused by name`() {
        val unattachedCoffee = row(name = "Coffee with milk", foodId = null, amount = 0.0, unit = "")

        val outcome = MealFromDay.from(listOf(unattachedCoffee), foodsById)

        assertThat(outcome.refusals)
            .containsExactly("Coffee with milk is not attached to a food yet.")
        assertThat(outcome.components).isEmpty()
    }

    /**
     * D58 §12.7: kept as a meal without logging, nothing was logged — and no refusal may say it was.
     */
    @Test
    fun `refusals for a described meal kept without logging never say it was logged`() {
        val outcome = MealFromDay.fromDescribed(
            listOf(
                row(name = "Bread", foodId = 2, amount = 1.0, unit = "roll"),
                row(name = "Cucumber", foodId = 1, amount = 0.0, unit = "g", portion = "amount not stated"),
                row(name = "Coffee with milk", foodId = null, amount = 1.0, unit = "cup"),
            ),
            foodsById,
        )

        assertThat(outcome.refusals).containsExactly(
            "Bread is in roll, and your Bread is counted in slice.",
            "Cucumber has no amount. Say how much, and it can join.",
            "Coffee with milk could not be matched to a food.",
        )
        outcome.refusals.forEach { assertThat(it).doesNotContain("logged") }
    }

    /** A food deleted since the row was logged leaves the pointer dangling, which is the same case. */
    @Test
    fun `a row pointing at a food that no longer exists is refused by name too`() {
        val outcome = MealFromDay.from(
            listOf(row(name = "Coffee with milk", foodId = 99, amount = 0.0, unit = "")),
            foodsById,
        )

        assertThat(outcome.components).isEmpty()
        assertThat(outcome.refusals.single()).contains("Coffee with milk")
    }

    /**
     * Weighed on the day, but the food knows only what one spoon of it is worth and nothing about
     * what one weighs — so this much of it cannot be costed. The exact sentence is the
     * implementation's; what the design requires is the row's own name and the reason, never a
     * silent omission that would make the meal quietly smaller than the day it came from.
     */
    @Test
    fun `a component that cannot be costed is refused rather than silently left out`() {
        val outcome = MealFromDay.from(
            listOf(row(name = "Olive oil", foodId = 4, amount = 15.0, unit = "g")),
            foodsById,
        )

        assertThat(outcome.components).isEmpty()
        assertThat(outcome.refusals).hasSize(1)
        assertThat(outcome.refusals.single()).contains("Olive oil")
        assertThat(outcome.refusals.single()).contains("spoon")
    }

    /**
     * A row is named by what its food is called NOW, not by what was typed on the day.
     *
     * Renaming a food re-labels every day it was ever eaten — that is the whole point of the row
     * pointing at a food — and a refusal that reached past the new name to the old one would name a
     * row he cannot find on the screen he is looking at.
     */
    @Test
    fun `a refusal names the row by what its food is called now`() {
        val outcome = MealFromDay.from(
            listOf(
                row(
                    name = "Olive oil",
                    foodId = 4,
                    amount = 15.0,
                    unit = "g",
                    currentName = "Extra virgin olive oil",
                ),
            ),
            foodsById,
        )

        assertThat(outcome.components).isEmpty()
        assertThat(outcome.refusals.single()).startsWith("Extra virgin olive oil")
    }

    @Test
    fun `the order of the day is the order of the meal`() {
        val outcome = MealFromDay.from(
            listOf(
                row(name = "Bread", foodId = 2, amount = 2.0, unit = "slice"),
                row(name = "Cucumber", foodId = 1, amount = 150.0, unit = "g"),
                row(name = "Leftovers", foodId = 3, amount = 0.0, unit = ""),
            ),
            foodsById,
        )

        assertThat(outcome.components.map { it.food.name })
            .containsExactly("Bread", "Cucumber", "Leftovers")
            .inOrder()
    }

    /**
     * The same food twice in one day is ONE component holding both amounts.
     *
     * A meal stores its parts keyed on the food — putting one in twice changes the amount rather
     * than making a second row — so two components of one food left the meal holding the last of
     * them alone, worth half of what the sheet had just totalled up, with nothing said.
     */
    @Test
    fun `two rows of one food add up into one component`() {
        val outcome = MealFromDay.from(
            listOf(
                row(name = "Cucumber", foodId = 1, amount = 100.0, unit = "g"),
                row(name = "Cucumber", foodId = 1, amount = 150.0, unit = "g"),
            ),
            foodsById,
        )

        assertThat(outcome.refusals).isEmpty()
        val component = outcome.components.single()
        assertThat(component.food).isEqualTo(cucumber)
        assertThat(component.amount).isEqualTo(250.0)
        assertThat(component.countedAs).isEqualTo(CountedAs.GRAMS)
    }

    /**
     * And it is worth what the two rows were worth, which is what the naming sheet totals up.
     *
     * The preview adds the rows; the meal holds the merged component. If those two disagreed the
     * sheet would be promising something the stored meal does not hold.
     */
    @Test
    fun `the merged component is worth what the rows it was made from were worth`() {
        val outcome = MealFromDay.from(
            listOf(
                row(name = "Cucumber", foodId = 1, amount = 100.0, unit = "g"),
                row(name = "Cucumber", foodId = 1, amount = 150.0, unit = "g"),
            ),
            foodsById,
        )

        val merged = MealFromDay.from(
            listOf(row(name = "Cucumber", foodId = 1, amount = 250.0, unit = "g")),
            foodsById,
        ).components.single()
        assertThat(outcome.components.single()).isEqualTo(merged)

        val worth = Logging.log(cucumber.facts, merged.amount, merged.countedAs)
        val first = Logging.log(cucumber.facts, 100.0, CountedAs.GRAMS)
        val second = Logging.log(cucumber.facts, 150.0, CountedAs.GRAMS)
        assertThat((worth as LoggedFrom.Numbers).kcal).isEqualTo(
            (first as LoggedFrom.Numbers).kcal + (second as LoggedFrom.Numbers).kcal,
        )
    }

    /**
     * 100 g of bread at breakfast and 2 slices at lunch cannot be added together.
     *
     * Nothing here weighs a slice to make the sum come out, and a food that knows what one weighs
     * would still be having one of his two rows re-counted behind his back. So the food is named and
     * nothing is made — the whole-or-nothing rule, arrived at by a different route.
     */
    @Test
    fun `one food weighed on one row and counted on another is refused by name`() {
        val outcome = MealFromDay.from(
            listOf(
                row(name = "Bread", foodId = 2, amount = 100.0, unit = "g"),
                row(name = "Bread", foodId = 2, amount = 2.0, unit = "slice"),
            ),
            foodsById,
        )

        assertThat(outcome.components).isEmpty()
        assertThat(outcome.refusals.single()).contains("Bread")
    }

    /**
     * Half a kilo is not half a gram, and this app has no conversion that says what it is instead.
     *
     * `Logging` divides a weighed amount by 100 AS GRAMS, so a row logged in kilograms passed
     * through untouched went into the meal worth almost nothing — and worth nothing is a number, so
     * no refusal fired. Described rows keep whatever unit the model answered, which is how a kilo
     * gets onto a day in the first place.
     */
    /**
     * A drink measured in millilitres, end to end: described, logged, and made into one meal with
     * no grams anywhere.
     *
     * Given a cappuccino, the model answers espresso 30 ml and milk 150 ml. The foods made from
     * those rows used to know only a "per 100 g" figure divided out of millilitres, so making a meal
     * of the two rows was refused — "nothing here turns ml into grams" — leaving no way through but
     * to build it by hand and type a weight for the milk (issue #26). Made from the rows as they are, each food is counted in millilitres, and the
     * day's two rows join a meal in millilitres, every figure as the model gave it.
     */
    @Test
    fun `a cappuccino described in millilitres becomes a meal in millilitres`() {
        fun logged(ref: Long, name: String, ml: Double, kcal: Int) = LoggedFoodRow(
            ref = ref, name = name, portionAmount = ml, portionUnit = "ml", kcal = kcal,
            proteinG = 0, carbsG = 0, fatG = 0, source = Source.AI_ESTIMATE, confidence = Confidence.MEDIUM,
            loggedAtMillis = 0,
        )
        val derived = DerivedFoods.from(
            listOf(logged(1, "Espresso", 30.0, 2), logged(2, "Milk", 150.0, 95)),
        ).foods
        val espresso = Food(id = 20, name = "Espresso", facts = derived.single { it.displayName == "Espresso" }.facts)
        val milkFromMl = Food(id = 21, name = "Milk", facts = derived.single { it.displayName == "Milk" }.facts)
        val foods = listOf(espresso, milkFromMl)

        val outcome = MealFromDay.from(
            listOf(
                row(name = "Espresso", foodId = 20, amount = 30.0, unit = "ml"),
                row(name = "Milk", foodId = 21, amount = 150.0, unit = "ml"),
            ),
            { id -> foods.firstOrNull { it.id == id } },
        )

        assertThat(outcome.refusals).isEmpty()
        assertThat(outcome.components.map { it.countedAs })
            .containsExactly(CountedAs.UNITS, CountedAs.UNITS)
        assertThat(outcome.components.map { it.amount }).containsExactly(30.0, 150.0).inOrder()
        assertThat(milkFromMl.facts.per100g).isNull()
    }

    @Test
    fun `a row logged in kilograms is refused by name, with its unit`() {
        val outcome = MealFromDay.from(
            listOf(row(name = "Cucumber", foodId = 1, amount = 0.5, unit = "kg")),
            foodsById,
        )

        assertThat(outcome.components).isEmpty()
        assertThat(outcome.refusals.single()).contains("Cucumber")
        assertThat(outcome.refusals.single()).contains("kg")
    }

    /** Millilitres are not grams either, and neither are ounces. */
    @Test
    fun `the other mass units are refused too`() {
        val units = listOf("ml", "oz", "lb", "l")

        units.forEach { unit ->
            val outcome = MealFromDay.from(
                listOf(row(name = "Cucumber", foodId = 1, amount = 200.0, unit = unit)),
                foodsById,
            )

            assertThat(outcome.components).isEmpty()
            assertThat(outcome.refusals.single()).contains(unit)
        }
    }

    /**
     * A food whose own unit IS "ml" is counted in millilitres, not refused for want of a conversion.
     *
     * The unit field in the foods editor is free text, so "ml" can be what one of a food is counted
     * in; logging writes that same word back onto the row. The check for a unit needing a conversion
     * to grams ran FIRST, so such a food could never go into a meal made out of a day, and the
     * refusal blamed a conversion nobody needed: the row is already in the unit the food counts in.
     * The same shape bites every unit name that appears in the mass list — l, cl, oz, lb, kg and the
     * Hebrew spellings.
     */
    @Test
    fun `a row logged in the food's own unit is counted, even when that unit is on the mass list`() {
        val outcome = MealFromDay.from(
            listOf(row(name = "Milk", foodId = 5, amount = 200.0, unit = "ml")),
            foodsById,
        )

        assertThat(outcome.refusals).isEmpty()
        val component = outcome.components.single()
        assertThat(component.food).isEqualTo(milk)
        assertThat(component.amount).isEqualTo(200.0)
        assertThat(component.countedAs).isEqualTo(CountedAs.UNITS)
    }

    /**
     * "grams" written out is the same unit as "g" — a spelling, not a conversion.
     *
     * Refusing it would refuse a row the app plainly understands, and inventing a factor for it is
     * not what recognising a spelling does: there is no factor.
     */
    @Test
    fun `grams written out in words is still grams`() {
        val outcome = MealFromDay.from(
            listOf(row(name = "Cucumber", foodId = 1, amount = 150.0, unit = "grams")),
            foodsById,
        )

        assertThat(outcome.refusals).isEmpty()
        assertThat(outcome.components.single().amount).isEqualTo(150.0)
        assertThat(outcome.components.single().countedAs).isEqualTo(CountedAs.GRAMS)
    }

    /**
     * Three tablespoons of an oil counted in spoons are not three spoons.
     *
     * The row's unit was discarded and only its number reused, so the meal held three of whatever
     * the food happened to count in. Nothing here knows how many spoons a tablespoon is, so the row
     * is named and so are both units.
     */
    @Test
    fun `a row counted in a unit the food does not know is refused by name`() {
        val outcome = MealFromDay.from(
            listOf(row(name = "Olive oil", foodId = 4, amount = 3.0, unit = "tbsp")),
            foodsById,
        )

        assertThat(outcome.components).isEmpty()
        assertThat(outcome.refusals.single()).contains("Olive oil")
        assertThat(outcome.refusals.single()).contains("tbsp")
        assertThat(outcome.refusals.single()).contains("spoon")
    }

    private fun row(
        name: String,
        foodId: Long?,
        amount: Double,
        unit: String,
        currentName: String? = null,
        portion: String? = null,
    ): FoodItem = FoodItem(
        name = name,
        portion = portion ?: if (unit.isBlank()) null else "${amount.toInt()} $unit",
        portionAmount = amount,
        portionUnit = unit,
        kcal = 100,
        proteinG = 5,
        carbsG = 10,
        fatG = 3,
        source = Source.TYPED,
        foodId = foodId,
        currentName = currentName,
    )
}
