package com.metaself.app.domain.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.amount.BelievableAmount.KCAL_PER_100G
import com.metaself.app.domain.amount.BelievableAmount.MACRO_PER_100G
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import org.junit.jupiter.api.Test

/**
 * A food as the owner edits it.
 *
 * The thing being protected is that a food can still say "I do not know that". The three facts are
 * optional by design, so a form that insisted on all of them would quietly put back the idea that a
 * food has a kind — which is the whole thing this model removed.
 */
class FoodFormTest {

    private val yoghurt = FoodForm(
        name = "Yoghurt",
        kcalPer100g = "72",
        proteinPer100g = "4",
        carbsPer100g = "6",
        fatPer100g = "2",
    )

    // --- What makes a food answerable ------------------------------------------------------------

    @Test
    fun `a food knowing what 100 grams are worth is enough`() {
        assertThat(yoghurt.errors()).isEmpty()
        assertThat(yoghurt.toFacts(setAtMillis = 0)!!.per100g!!.nutrients.kcal).isEqualTo(72.0)
    }

    @Test
    fun `a food knowing only what one of it is worth is enough`() {
        val bar = FoodForm(
            name = "Protein bar",
            unitName = "bar",
            kcalPerUnit = "190",
            proteinPerUnit = "15",
            carbsPerUnit = "17",
            fatPerUnit = "6",
        )

        assertThat(bar.errors()).isEmpty()
        assertThat(bar.toFacts(0)!!.perUnit!!.unitName).isEqualTo("bar")
        assertThat(bar.toFacts(0)!!.per100g).isNull()
    }

    /**
     * A food that knows neither cannot be logged, cannot be costed inside a meal, and is a name with
     * nothing behind it.
     */
    @Test
    fun `a food that knows nothing at all is refused`() {
        assertThat(FoodForm(name = "Tahini").errors()).containsKey(FoodField.NOTHING_KNOWN)
    }

    @Test
    fun `a food with no name is refused`() {
        assertThat(yoghurt.copy(name = "   ").errors()).containsKey(FoodField.NAME)
        assertThat(yoghurt.copy(name = "???").errors()).containsKey(FoodField.NAME)
    }

    // --- Present as a group or absent as a group ---------------------------------------------------

    /**
     * Three of four is a half-answer. A food knowing its calories and not its protein would show a
     * blank where a number belongs on every screen that reads it.
     */
    @Test
    fun `half a number group is refused`() {
        assertThat(yoghurt.copy(proteinPer100g = "").errors()).containsKey(FoodField.PER_100G)
    }

    @Test
    fun `an emptied group is how he says the food does not know that`() {
        val bothWays = yoghurt.copy(
            unitName = "pot",
            kcalPerUnit = "130",
            proteinPerUnit = "7",
            carbsPerUnit = "11",
            fatPerUnit = "4",
        )
        assertThat(bothWays.toFacts(0)!!.perUnit).isNotNull()

        val emptied = bothWays.copy(
            unitName = "",
            kcalPerUnit = "",
            proteinPerUnit = "",
            carbsPerUnit = "",
            fatPerUnit = "",
        )

        assertThat(emptied.errors()).isEmpty()
        assertThat(emptied.toFacts(0)!!.perUnit).isNull()
        assertThat(emptied.toFacts(0)!!.per100g).isNotNull()
    }

    /** "190" of what? A per-unit group with no unit is a number with nothing to attach it to. */
    @Test
    fun `numbers per unit with no unit named are refused`() {
        val noUnit = yoghurt.copy(
            kcalPerUnit = "130",
            proteinPerUnit = "7",
            carbsPerUnit = "11",
            fatPerUnit = "4",
        )

        assertThat(noUnit.errors()).containsKey(FoodField.UNIT_NAME)
    }

    @Test
    fun `a unit named with no numbers behind it is refused`() {
        assertThat(yoghurt.copy(unitName = "pot").errors()).containsKey(FoodField.PER_UNIT)
    }

    // --- What one of it weighs ----------------------------------------------------------------------

    @Test
    fun `a weight is optional and is kept when it is given`() {
        assertThat(yoghurt.errors()).isEmpty()
        assertThat(yoghurt.toFacts(0)!!.gramsPerUnit).isNull()

        val weighed = yoghurt.copy(gramsPerUnit = "45")
        assertThat(weighed.toFacts(0)!!.gramsPerUnit!!.grams).isEqualTo(45.0)
    }

    @Test
    fun `nothing weighs nothing`() {
        assertThat(yoghurt.copy(gramsPerUnit = "0").errors()).containsKey(FoodField.WEIGHT)
        assertThat(yoghurt.copy(gramsPerUnit = "-5").errors()).containsKey(FoodField.WEIGHT)
        assertThat(yoghurt.copy(gramsPerUnit = "heavy").errors()).containsKey(FoodField.WEIGHT)
    }

    /**
     * **The one derivation the design forbids, checked at the place most likely to reach for it.**
     * A form holding both kinds of calories has everything it needs to divide out a weight, and
     * must not: the inputs are typically two estimates, and the output would be presented as a fact
     * about a physical object.
     */
    @Test
    fun `a food knowing both kinds of calories still has no weight`() {
        val both = yoghurt.copy(
            unitName = "pot",
            kcalPerUnit = "130",
            proteinPerUnit = "7",
            carbsPerUnit = "11",
            fatPerUnit = "4",
        )

        assertThat(both.toFacts(0)!!.gramsPerUnit).isNull()
    }

    // --- Provenance ------------------------------------------------------------------------------------

    /**
     * Everything typed here is the owner's own hand, which is why a correction is not subject to the
     * ranking: the ranking exists to stop a model overwriting him, not to stop him overwriting one.
     */
    @Test
    fun `everything he types is his own number, carrying no confidence`() {
        val facts = yoghurt.copy(gramsPerUnit = "45").toFacts(setAtMillis = 5_000)!!

        assertThat(facts.per100g!!.provenance.source).isEqualTo(Source.TYPED)
        assertThat(facts.per100g!!.provenance.confidence).isNull()
        assertThat(facts.gramsPerUnit!!.provenance.source).isEqualTo(Source.TYPED)
        assertThat(facts.per100g!!.provenance.setAtMillis).isEqualTo(5_000)
    }

    // --- Opening a stored food ---------------------------------------------------------------------------

    @Test
    fun `a stored food opens with every number it holds`() {
        val food = Food(
            name = "Protein bar",
            brand = "Dairyco",
            facts = FoodFacts(
                per100g = PerHundredGrams(
                    Nutrients(422.0, 33.0, 38.0, 14.0),
                    Provenance(Source.LABEL, null, 0),
                ),
                perUnit = PerUnit(
                    "bar",
                    Nutrients(190.0, 15.0, 17.0, 6.0),
                    Provenance(Source.TYPED, null, 0),
                ),
                gramsPerUnit = GramsPerUnit(45.0, Provenance(Source.TYPED, null, 0)),
            ),
        )

        val form = FoodForm.of(food)

        assertThat(form.name).isEqualTo("Protein bar")
        assertThat(form.brand).isEqualTo("Dairyco")
        assertThat(form.kcalPer100g).isEqualTo("422")
        assertThat(form.unitName).isEqualTo("bar")
        assertThat(form.kcalPerUnit).isEqualTo("190")
        assertThat(form.gramsPerUnit).isEqualTo("45")
    }

    /** `NA` is the brand of food with no brand, and is not something to show him in a field. */
    @Test
    fun `a food with no brand opens with the brand field empty`() {
        assertThat(FoodForm.of(Food(name = "Yoghurt", facts = someFacts())).brand).isEmpty()
    }

    /**
     * A whole number reads as a whole number. "72.0" invites him to wonder what the zero is doing
     * there, and a fraction survives because per-100-g arithmetic produces them.
     */
    @Test
    fun `a whole number opens without a decimal point and a fraction keeps one`() {
        val food = Food(
            name = "Bread",
            facts = FoodFacts(
                per100g = PerHundredGrams(
                    Nutrients(250.0, 8.5, 45.0, 3.0),
                    Provenance(Source.TYPED, null, 0),
                ),
            ),
        )

        val form = FoodForm.of(food)

        assertThat(form.kcalPer100g).isEqualTo("250")
        assertThat(form.proteinPer100g).isEqualTo("8.5")
    }

    // --- Stored figures shown rounded, and kept when their box is untouched (D54 §8.5) ------------

    /**
     * A scanned food's per-100 g figures are a serving's scaled — here an invented 70 g serving of
     * 6 g protein, 9 g carbohydrate and 4 g fat — so they are stored as long doubles. A box shows
     * at most two decimals, trailing zeros trimmed.
     */
    private val scanned = FoodFacts(
        per100g = PerHundredGrams(
            Nutrients(100.0, 8.571428571428571, 12.857142857142858, 5.714285714285714),
            Provenance(Source.LABEL, null, 0),
        ),
        perUnit = PerUnit(
            "serving",
            Nutrients(70.0, 6.000000000000001, 9.0, 4.0),
            Provenance(Source.LABEL, null, 0),
        ),
        gramsPerUnit = GramsPerUnit(70.0, Provenance(Source.LABEL, null, 0)),
    )

    @Test
    fun `a stored figure opens rounded to at most two decimals, trailing zeros trimmed`() {
        val form = FoodForm.of(Food(name = "Seeded cracker", facts = scanned))

        assertThat(listOf(form.kcalPer100g, form.proteinPer100g, form.carbsPer100g, form.fatPer100g))
            .containsExactly("100", "8.57", "12.86", "5.71").inOrder()
        assertThat(form.proteinPerUnit).isEqualTo("6")
        assertThat(FoodForm.shown(8.5)).isEqualTo("8.5")
        assertThat(FoodForm.shown(0.30000000000000004)).isEqualTo("0.3")
        assertThat(FoodForm.shown(1000.0)).isEqualTo("1000")
    }

    /** Saving an untouched box keeps the stored figure, so Save has nothing to write for it. */
    @Test
    fun `a box left as it opened saves the stored figure, and Save leaves the group alone`() {
        val facts = FoodForm.of(Food(name = "Seeded cracker", facts = scanned)).toFacts(0, stored = scanned)!!

        assertThat(facts.per100g!!.nutrients).isEqualTo(scanned.per100g!!.nutrients)
        assertThat(facts.perUnit!!.nutrients).isEqualTo(scanned.perUnit!!.nutrients)
        assertThat(Correction.plan(scanned, facts))
            .isEqualTo(Correction.Plan(Correction.Keep, Correction.Keep, Correction.Keep))
    }

    /** Typed over — even to the figure it showed, written another way — it is his own number. */
    @Test
    fun `a box typed over saves what was typed`() {
        val form = FoodForm.of(Food(name = "Seeded cracker", facts = scanned))

        val retyped = form.copy(proteinPer100g = "8.6").toFacts(0, stored = scanned)!!
        val padded = form.copy(proteinPer100g = "8.570").toFacts(0, stored = scanned)!!

        assertThat(retyped.per100g!!.nutrients)
            .isEqualTo(Nutrients(100.0, 8.6, 12.857142857142858, 5.714285714285714))
        assertThat(padded.per100g!!.nutrients.proteinG).isEqualTo(8.57)
        assertThat(Correction.plan(scanned, retyped).per100g).isInstanceOf(Correction.Replace::class.java)
    }

    /** Opening a food and saving it unchanged must not alter what it knows. */
    @Test
    fun `a stored food survives a round trip through the form`() {
        val facts = FoodFacts(
            per100g = PerHundredGrams(
                Nutrients(422.0, 33.0, 38.0, 14.0),
                Provenance(Source.AI_ESTIMATE, Confidence.LOW, 0),
            ),
            perUnit = PerUnit(
                "bar",
                Nutrients(190.0, 15.0, 17.0, 6.0),
                Provenance(Source.TYPED, null, 0),
            ),
            gramsPerUnit = GramsPerUnit(45.0, Provenance(Source.TYPED, null, 0)),
        )

        val again = FoodForm.of(Food(name = "Protein bar", facts = facts)).toFacts(0)!!

        assertThat(again.per100g!!.nutrients).isEqualTo(facts.per100g!!.nutrients)
        assertThat(again.perUnit!!.nutrients).isEqualTo(facts.perUnit!!.nutrients)
        assertThat(again.perUnit!!.unitName).isEqualTo("bar")
        assertThat(again.gramsPerUnit!!.grams).isEqualTo(45.0)
        // The numbers survive; the story about where they came from does not, because he has now
        // put his own name to them by saving them.
        assertThat(again.per100g!!.provenance.source).isEqualTo(Source.TYPED)
    }

    @Test
    fun `a comma is a decimal point`() {
        assertThat(yoghurt.copy(kcalPer100g = "72,5").toFacts(0)!!.per100g!!.nutrients.kcal)
            .isEqualTo(72.5)
    }

    /**
     * The guarantee behind the sentence the food editor and the meal builder's form now show before
     * anything is typed — "0.5 g is kept as 0.5 g on this food" (issue #18, D38). If this ever
     * rounded, that sentence would be a lie on both forms.
     */
    @Test
    fun `half a gram of fat is kept as half a gram, per 100 g and per one`() {
        val form = yoghurt.copy(
            fatPer100g = "0.5",
            unitName = "slice",
            kcalPerUnit = "40",
            proteinPerUnit = "0.7",
            carbsPerUnit = "3",
            fatPerUnit = "1",
        )

        val facts = form.toFacts(0)!!
        assertThat(facts.per100g!!.nutrients.fatG).isEqualTo(0.5)
        assertThat(facts.perUnit!!.nutrients.proteinG).isEqualTo(0.7)
    }

    // --- Every figure has a ceiling (D42, issue #32) -----------------------------------------------

    private val per100gRefused = "All four per 100 g (at most 1000 kcal, and 110 g of protein, " +
        "carbohydrate or fat), or leave them all empty."
    private val perUnitRefused = "All four per unit (at most 5000 kcal, and 500 g of protein, " +
        "carbohydrate or fat), or leave them all empty."
    private val weightRefused = "What one of it weighs, in grams (at most 5000), or leave it empty."

    private val bar = yoghurt.copy(
        unitName = "bar",
        kcalPerUnit = "190",
        proteinPerUnit = "15",
        carbsPerUnit = "17",
        fatPerUnit = "6",
    )

    /**
     * A food with infinite calories used to be saved. Refused now under the group — the form's one
     * place for anything wrong with the four — with a sentence naming every ceiling, since the
     * group's one refusal has to cover whichever box is wrong. The comma path is judged too: it is
     * the same number written the way he writes it.
     */
    @Test
    fun `a per-100 g figure past its ceiling is refused under the group, and nothing is saved`() {
        val forms = listOf(
            yoghurt.copy(kcalPer100g = "Infinity"),
            yoghurt.copy(kcalPer100g = "1e999"),
            yoghurt.copy(kcalPer100g = "1000.5"),
            yoghurt.copy(proteinPer100g = "110,5"),
        )

        forms.forEach { form ->
            assertThat(form.errors()).isEqualTo(mapOf(FoodField.PER_100G to per100gRefused))
            assertThat(form.toFacts(0)).isNull()
        }
    }

    @Test
    fun `a per-unit figure past its ceiling is refused under its group`() {
        val forms = listOf(
            bar.copy(kcalPerUnit = "5000.5"),
            bar.copy(kcalPerUnit = "Infinity"),
            bar.copy(fatPerUnit = "500,5"),
        )

        forms.forEach { form ->
            assertThat(form.errors()).isEqualTo(mapOf(FoodField.PER_UNIT to perUnitRefused))
            assertThat(form.toFacts(0)).isNull()
        }
    }

    @Test
    fun `what one weighs past 5000 g is refused`() {
        listOf("5000.5", "Infinity").forEach { typed ->
            val form = yoghurt.copy(gramsPerUnit = typed)

            assertThat(form.errors()).isEqualTo(mapOf(FoodField.WEIGHT to weightRefused))
            assertThat(form.toFacts(0)).isNull()
        }
    }

    /**
     * Exactly at every ceiling is a food, kept exactly. And what parsed before still parses: a comma
     * is still a decimal point, zero is still a quantity, and a negative is still refused — with the
     * group's sentence, which now names the ceilings.
     */
    @Test
    fun `every figure at its ceiling is kept exactly`() {
        val atTheCeilings = FoodForm(
            name = "Oil",
            kcalPer100g = "1000",
            proteinPer100g = "110",
            carbsPer100g = "110",
            fatPer100g = "110",
            unitName = "bottle",
            kcalPerUnit = "5000",
            proteinPerUnit = "500",
            carbsPerUnit = "500",
            fatPerUnit = "500",
            gramsPerUnit = "5000",
        )

        assertThat(atTheCeilings.errors()).isEmpty()
        val facts = atTheCeilings.toFacts(0)!!
        assertThat(facts.per100g!!.nutrients).isEqualTo(
            Nutrients(KCAL_PER_100G, MACRO_PER_100G, MACRO_PER_100G, MACRO_PER_100G),
        )
        assertThat(facts.perUnit!!.nutrients).isEqualTo(Nutrients(5_000.0, 500.0, 500.0, 500.0))
        assertThat(facts.gramsPerUnit!!.grams).isEqualTo(5_000.0)

        assertThat(yoghurt.copy(kcalPer100g = "72,5").toFacts(0)!!.per100g!!.nutrients.kcal)
            .isEqualTo(72.5)
        assertThat(yoghurt.copy(fatPer100g = "0").toFacts(0)!!.per100g!!.nutrients.fatG)
            .isEqualTo(0.0)
        assertThat(yoghurt.copy(fatPer100g = "-1").errors())
            .isEqualTo(mapOf(FoodField.PER_100G to per100gRefused))
    }

    // --- D54: a group accepted from a review ------------------------------------------------------

    /** D54's invented Oat biscuit, as the form holds it. */
    private val oatBiscuit = FoodForm(
        name = "Oat biscuit",
        kcalPer100g = "480",
        proteinPer100g = "7",
        carbsPer100g = "62",
        fatPer100g = "22",
        unitName = "biscuit",
        kcalPerUnit = "90",
        proteinPerUnit = "1",
        carbsPerUnit = "12",
        fatPerUnit = "4",
        gramsPerUnit = "18",
    )

    @Test
    fun `a group accepted from a review is handed over as an estimate with its confidence`() {
        val facts = oatBiscuit.toFacts(5, estimated = mapOf(FactGroup.PER_UNIT to AcceptedGroup(Confidence.MEDIUM)))!!

        assertThat(facts.perUnit!!.provenance)
            .isEqualTo(Provenance(Source.AI_ESTIMATE, Confidence.MEDIUM, 5))
        assertThat(facts.per100g!!.provenance).isEqualTo(Provenance(Source.TYPED, null, 5))
        assertThat(facts.gramsPerUnit!!.provenance).isEqualTo(Provenance(Source.TYPED, null, 5))
    }

    // D54 §5 as amended 2026-09-24: an accepted group is stored as the weaker of an estimate and
    // where the figures the review kept in it came from. Downgrading is honest; upgrading never is.

    @Test
    fun `an accepted group that kept figures copied off a past meal stays repeated, with no confidence`() {
        val facts = oatBiscuit.toFacts(
            5,
            estimated = mapOf(FactGroup.PER_UNIT to AcceptedGroup(Confidence.HIGH, keptFrom = Source.REPEATED)),
        )!!

        assertThat(facts.perUnit!!.provenance).isEqualTo(Provenance(Source.REPEATED, null, 5))
    }

    @Test
    fun `an accepted group that kept figures of unrecognised origin stays unrecognised`() {
        val facts = oatBiscuit.toFacts(
            5,
            estimated = mapOf(FactGroup.PER_100G to AcceptedGroup(Confidence.MEDIUM, keptFrom = Source.UNRECOGNISED)),
        )!!

        assertThat(facts.per100g!!.provenance).isEqualTo(Provenance(Source.UNRECOGNISED, null, 5))
    }

    @Test
    fun `an accepted group whose every figure changed is an estimate with the review's confidence`() {
        val facts = oatBiscuit.toFacts(
            5,
            estimated = mapOf(FactGroup.PER_100G to AcceptedGroup(Confidence.LOW, keptFrom = null)),
        )!!

        assertThat(facts.per100g!!.provenance).isEqualTo(Provenance(Source.AI_ESTIMATE, Confidence.LOW, 5))
    }

    @Test
    fun `an accepted group that kept label figures is an estimate, never a label`() {
        val facts = oatBiscuit.toFacts(
            5,
            estimated = mapOf(FactGroup.PER_100G to AcceptedGroup(Confidence.MEDIUM, keptFrom = Source.LABEL)),
        )!!

        assertThat(facts.per100g!!.provenance)
            .isEqualTo(Provenance(Source.AI_ESTIMATE, Confidence.MEDIUM, 5))
    }

    /** The weight is never a review's to give (D54 §3), so it is never an estimate here either. */
    @Test
    fun `the weight is typed even when both groups were accepted`() {
        val facts = oatBiscuit.toFacts(
            5,
            estimated = mapOf(
                FactGroup.PER_100G to AcceptedGroup(Confidence.LOW),
                FactGroup.PER_UNIT to AcceptedGroup(Confidence.LOW),
            ),
        )!!

        assertThat(facts.per100g!!.provenance.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(facts.perUnit!!.provenance.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(facts.gramsPerUnit!!.provenance.source).isEqualTo(Source.TYPED)
    }

    @Test
    fun `an accepted group the form no longer holds is not invented`() {
        val facts = yoghurt.toFacts(5, estimated = mapOf(FactGroup.PER_UNIT to AcceptedGroup(Confidence.HIGH)))!!

        assertThat(facts.perUnit).isNull()
        assertThat(facts.per100g!!.provenance.source).isEqualTo(Source.TYPED)
    }

    @Test
    fun `accepting figures fills the four boxes of that group and nothing else`() {
        val filled = oatBiscuit.with(FactGroup.PER_UNIT, Nutrients(95.0, 1.5, 12.0, 4.0))

        assertThat(filled).isEqualTo(
            oatBiscuit.copy(
                kcalPerUnit = "95",
                proteinPerUnit = "1.5",
                carbsPerUnit = "12",
                fatPerUnit = "4",
            ),
        )
        assertThat(oatBiscuit.with(FactGroup.PER_100G, Nutrients(370.0, 30.0, 40.0, 10.0)))
            .isEqualTo(
                oatBiscuit.copy(
                    kcalPer100g = "370",
                    proteinPer100g = "30",
                    carbsPer100g = "40",
                    fatPer100g = "10",
                ),
            )
    }

    /** A new food with only a name and a unit: filling per 100 g leaves the unit box as typed. */
    @Test
    fun `filling a group leaves the unit name and the weight as they were`() {
        val soup = FoodForm(name = "Lentil soup", unitName = "bowl")

        val filled = soup
            .with(FactGroup.PER_100G, Nutrients(60.0, 4.0, 9.0, 1.0))
            .with(FactGroup.PER_UNIT, Nutrients(180.0, 12.0, 27.0, 3.0))

        assertThat(filled.unitName).isEqualTo("bowl")
        assertThat(filled.gramsPerUnit).isEmpty()
        assertThat(filled.errors()).isEmpty()
    }

    // --- A food counted in millilitres (D56) ----------------------------------------------------
    // Figures invented: a carton's 57 kcal, 2.9 g protein, 4.7 g carbohydrate, 3.6 g fat per 100 ml,
    // each chosen because its per-ml double is not what a float multiply or divide by 100 gives.

    private fun drink(perMl: Nutrients, unit: String = "ml", grams: Double? = null) = Food(
        name = "Oat drink",
        facts = FoodFacts(
            perUnit = PerUnit(unit, perMl, Provenance(Source.LABEL, null, 0)),
            gramsPerUnit = grams?.let { GramsPerUnit(it, Provenance(Source.TYPED, null, 0)) },
        ),
    )

    private val perMl = Nutrients(0.57, 0.029, 0.047, 0.036)

    @Test
    fun `a food counted in ml opens per 100 ml`() {
        val form = FoodForm.of(drink(perMl))

        assertThat(listOf(form.kcalPerUnit, form.proteinPerUnit, form.carbsPerUnit, form.fatPerUnit))
            .containsExactly("57", "2.9", "4.7", "3.6").inOrder()
    }

    @Test
    fun `figures typed per 100 ml are stored per ml`() {
        val form = FoodForm(
            name = "Oat drink",
            unitName = "ml",
            kcalPerUnit = "57",
            proteinPerUnit = "2.9",
            carbsPerUnit = "4.7",
            fatPerUnit = "3.6",
        )

        assertThat(form.toFacts(setAtMillis = 0)!!.perUnit!!.nutrients).isEqualTo(perMl)
    }

    @Test
    fun `a food counted in ml opened and saved untouched keeps every stored bit`() {
        // A figure carrying a double's noise, as 2.9 / 100 leaves one, and one past two decimals.
        val noisy = Nutrients(0.57, 2.9 / 100, 0.04713, 0.036)
        val stored = drink(noisy).facts

        val facts = FoodForm.of(drink(noisy)).toFacts(setAtMillis = 1, stored = stored)!!

        assertThat(facts.perUnit!!.nutrients).isEqualTo(noisy)
        assertThat(FoodForm.of(drink(noisy)).perUnitFigures(stored.perUnit!!.nutrients)).isEqualTo(noisy)
    }

    /** A pasted right-to-left mark after "ml" is stripped by Save, so the boxes are per 100 ml too. */
    @Test
    fun `a unit stored as ml is per 100 ml even with an invisible mark typed`() {
        val form = FoodForm(
            name = "Oat drink",
            unitName = "ml‏",
            kcalPerUnit = "57",
            proteinPerUnit = "2.9",
            carbsPerUnit = "4.7",
            fatPerUnit = "3.6",
        )

        val perUnit = form.toFacts(setAtMillis = 0)!!.perUnit!!
        assertThat(perUnit.unitName).isEqualTo("ml")
        assertThat(perUnit.nutrients).isEqualTo(perMl)
        assertThat(FoodForm.of(drink(perUnit.nutrients)).kcalPerUnit).isEqualTo("57")
    }

    @Test
    fun `the Hebrew millilitre is per 100 ml too`() {
        val form = FoodForm.of(drink(perMl, unit = "מ\"ל"))

        assertThat(form.kcalPerUnit).isEqualTo("57")
    }

    @Test
    fun `a food counted in ml is judged by the per 100 ceilings, and says so`() {
        val form = FoodForm(
            name = "Oat drink",
            unitName = "ml",
            kcalPerUnit = "1200",
            proteinPerUnit = "1",
            carbsPerUnit = "1",
            fatPerUnit = "1",
        )

        assertThat(form.errors()[FoodField.PER_UNIT]).startsWith(
            "All four per 100 ml (at most 1000 kcal, and 110 g of protein, carbohydrate or fat)",
        )
        assertThat(form.copy(kcalPerUnit = "1000", fatPerUnit = "110").errors()).isEmpty()
        assertThat(form.copy(kcalPerUnit = "1000", fatPerUnit = "111").errors())
            .containsKey(FoodField.PER_UNIT)
    }

    @Test
    fun `a unit that is not ml keeps its per one boxes and ceilings`() {
        val glass = FoodForm.of(drink(Nutrients(140.0, 7.0, 12.0, 6.0), unit = "glass"))

        assertThat(glass.kcalPerUnit).isEqualTo("140")
        assertThat(glass.copy(kcalPerUnit = "1200").errors()).isEmpty()
        assertThat(glass.toFacts(0)!!.perUnit!!.nutrients).isEqualTo(Nutrients(140.0, 7.0, 12.0, 6.0))
    }

    @Test
    fun `the scale follows the unit box as it is typed`() {
        val typed = FoodForm(
            name = "Oat drink",
            kcalPerUnit = "57",
            proteinPerUnit = "2.9",
            carbsPerUnit = "4.7",
            fatPerUnit = "3.6",
        )

        assertThat(typed.copy(unitName = "ml").perUnitFigures()).isEqualTo(perMl)
        assertThat(typed.copy(unitName = "cup").perUnitFigures()!!.kcal).isEqualTo(57.0)
    }

    @Test
    fun `a review's suggestion for a food counted in ml is written as shown, per 100 ml`() {
        val filled = FoodForm(name = "Oat drink", unitName = "ml")
            .with(FactGroup.PER_UNIT, Nutrients(57.0, 2.9, 4.7, 3.6))

        assertThat(filled.kcalPerUnit).isEqualTo("57")
        assertThat(filled.perUnitFigures()).isEqualTo(perMl)
    }

    private fun someFacts() = FoodFacts(
        per100g = PerHundredGrams(
            Nutrients(72.0, 4.0, 6.0, 2.0),
            Provenance(Source.TYPED, null, 0),
        ),
    )
}
