package com.metaself.app.domain.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import org.junit.jupiter.api.Test

/**
 * Turning every row ever logged into the list of foods the owner actually eats.
 *
 * This is the conversion that runs once, on the owner's real record, and cannot be undone except by
 * restoring a backup. It also runs on every version-1 backup file that is ever restored, which is
 * why it is one pure function called from both places rather than two that would drift.
 *
 * The thing it must never do is change what a past day was worth. It cannot: it reads rows and
 * writes foods, and touches no number on any row. What it CAN get wrong is which rows are the same
 * food and what that food knows, which is what every test here is about.
 */
class DerivedFoodsTest {

    private fun row(
        ref: Long,
        name: String,
        amount: Double = 0.0,
        unit: String = "",
        kcal: Int = 100,
        proteinG: Int = 5,
        carbsG: Int = 10,
        fatG: Int = 2,
        source: Source = Source.AI_ESTIMATE,
        confidence: Confidence? = Confidence.MEDIUM,
        loggedAtMillis: Long = ref * 1000,
    ) = LoggedFoodRow(
        ref = ref,
        name = name,
        portionAmount = amount,
        portionUnit = unit,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        source = source,
        confidence = confidence,
        loggedAtMillis = loggedAtMillis,
    )

    // --- Grouping -------------------------------------------------------------------------------

    /** The whole point: one food logged on thirty days is one entry, not thirty. */
    @Test
    fun `the same food logged many times becomes one food`() {
        val rows = (1L..30L).map { row(it, "Yoghurt", 180.0, "g") }

        val derived = DerivedFoods.from(rows)

        assertThat(derived.foods).hasSize(1)
        assertThat(derived.foods.single().rowRefs).hasSize(30)
    }

    @Test
    fun `names differing only by case or punctuation are one food`() {
        val rows = listOf(
            row(1, "Low-fat yoghurt", 180.0, "g"),
            row(2, "low fat yoghurt", 180.0, "g"),
            row(3, "LOW FAT YOGHURT", 180.0, "g"),
        )

        assertThat(DerivedFoods.from(rows).foods).hasSize(1)
    }

    @Test
    fun `two genuinely different names are two foods`() {
        val rows = listOf(row(1, "Yoghurt", 180.0, "g"), row(2, "Hummus", 60.0, "g"))

        assertThat(DerivedFoods.from(rows).foods).hasSize(2)
    }

    /** Nothing in the record carries a brand, so everything the conversion makes is unbranded. */
    @Test
    fun `everything derived from the record has the brand that means none`() {
        val derived = DerivedFoods.from(listOf(row(1, "Yoghurt", 180.0, "g")))

        assertThat(derived.foods.single().brandKey).isEqualTo(FoodKeys.NO_BRAND_KEY)
    }

    @Test
    fun `every row ends up attached to exactly one food`() {
        val rows = listOf(
            row(1, "Yoghurt", 180.0, "g"),
            row(2, "Hummus", 60.0, "g"),
            row(3, "yoghurt", 200.0, "g"),
        )

        val derived = DerivedFoods.from(rows)

        assertThat(derived.foods.flatMap { it.rowRefs }).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun `nothing logged derives nothing`() {
        assertThat(DerivedFoods.from(emptyList()).foods).isEmpty()
    }

    // --- Which name is kept ---------------------------------------------------------------------

    /**
     * The stored name is the cleaned-up form the owner used most, not whichever variant he happened
     * to type last. From the moment this runs, every past day showing that food shows this name.
     */
    @Test
    fun `the most frequent spelling becomes the name`() {
        val rows = listOf(
            row(1, "Yoghurt", 180.0, "g"),
            row(2, "Yoghurt", 180.0, "g"),
            row(3, "YOGHURT", 180.0, "g"),
        )

        assertThat(DerivedFoods.from(rows).foods.single().displayName).isEqualTo("Yoghurt")
    }

    @Test
    fun `a tie on frequency is broken by the most recent`() {
        val rows = listOf(
            row(1, "yoghurt", 180.0, "g", loggedAtMillis = 1_000),
            row(2, "Yoghurt", 180.0, "g", loggedAtMillis = 2_000),
        )

        assertThat(DerivedFoods.from(rows).foods.single().displayName).isEqualTo("Yoghurt")
    }

    @Test
    fun `the kept name is cleaned up, not stored as typed`() {
        val rows = listOf(row(1, "  Café  au   lait ", 200.0, "g"))

        assertThat(DerivedFoods.from(rows).foods.single().displayName).isEqualTo("Café au lait")
    }

    /**
     * A row whose name is nothing but punctuation still has to end up somewhere: the conversion
     * leaves no row behind, because a row with no food is a day that cannot say what was eaten.
     */
    @Test
    fun `a row whose name normalises to nothing still gets a food`() {
        val rows = listOf(row(1, "???", 180.0, "g"))

        val derived = DerivedFoods.from(rows)

        assertThat(derived.foods).hasSize(1)
        assertThat(derived.foods.single().rowRefs).containsExactly(1L)
        assertThat(derived.foods.single().displayName).isEqualTo(DerivedFoods.UNNAMED)
    }

    // --- What the food comes to know ------------------------------------------------------------

    @Test
    fun `a row measured in grams fills what 100 grams are worth`() {
        val rows = listOf(row(1, "Yoghurt", 200.0, "g", kcal = 130, proteinG = 20, carbsG = 8, fatG = 2))

        val facts = DerivedFoods.from(rows).foods.single().facts

        assertThat(facts.per100g!!.nutrients.kcal).isWithin(0.001).of(65.0)
        assertThat(facts.per100g!!.nutrients.proteinG).isWithin(0.001).of(10.0)
        assertThat(facts.perUnit).isNull()
    }

    @Test
    fun `a row counted in slices fills what one is worth`() {
        val rows = listOf(row(1, "Bread", 2.0, "slice", kcal = 160, proteinG = 6, carbsG = 30, fatG = 2))

        val facts = DerivedFoods.from(rows).foods.single().facts

        assertThat(facts.perUnit!!.unitName).isEqualTo("slice")
        assertThat(facts.perUnit!!.nutrients.kcal).isWithin(0.001).of(80.0)
        assertThat(facts.per100g).isNull()
    }

    /**
     * Where the new model pays. The old one had to pick one shape for the whole food and throw the
     * other away; each fact is now filled from the best row that actually has that shape.
     */
    @Test
    fun `a food logged both ways learns both, independently`() {
        val rows = listOf(
            row(1, "Bread", 60.0, "g", kcal = 150),
            row(2, "Bread", 2.0, "slice", kcal = 160),
        )

        val facts = DerivedFoods.from(rows).foods.single().facts

        assertThat(facts.per100g!!.nutrients.kcal).isWithin(0.001).of(250.0)
        assertThat(facts.perUnit!!.nutrients.kcal).isWithin(0.001).of(80.0)
    }

    /**
     * Decision 11. A row may record only words — "a bowl" — and no number at all.
     * One of it was worth what it was worth, which is honest, and is not knowledge about the food.
     */
    @Test
    fun `a row with no amount becomes one portion, worth what it was worth`() {
        val rows = listOf(row(1, "Stew", kcal = 400, proteinG = 30, carbsG = 20, fatG = 18))

        val facts = DerivedFoods.from(rows).foods.single().facts

        assertThat(facts.perUnit!!.unitName).isEqualTo(FoodFacts.PORTION)
        assertThat(facts.perUnit!!.nutrients.kcal).isWithin(0.001).of(400.0)
        assertThat(facts.per100g).isNull()
        assertThat(facts.onlyAPortion).isTrue()
    }

    @Test
    fun `an amount of zero is no amount at all`() {
        val rows = listOf(row(1, "Stew", amount = 0.0, unit = "g", kcal = 400))

        assertThat(DerivedFoods.from(rows).foods.single().facts.perUnit!!.unitName)
            .isEqualTo(FoodFacts.PORTION)
    }

    @Test
    fun `an amount with no unit is no amount at all`() {
        val rows = listOf(row(1, "Stew", amount = 2.0, unit = "", kcal = 400))

        assertThat(DerivedFoods.from(rows).foods.single().facts.perUnit!!.unitName)
            .isEqualTo(FoodFacts.PORTION)
    }

    /** Decision 11 also requires the owner be told how many, so he can go and look. */
    @Test
    fun `the foods that only know a portion are counted`() {
        val rows = listOf(
            row(1, "Stew"),
            row(2, "Soup"),
            row(3, "Yoghurt", 180.0, "g"),
        )

        assertThat(DerivedFoods.from(rows).onlyAPortionCount).isEqualTo(2)
    }

    /**
     * The cheapest possible test of the one derivation the design forbids. Nothing in the record
     * says what a slice weighs, and dividing per-slice calories by per-100-g calories would present
     * an estimate as a measurement of a physical object.
     */
    @Test
    fun `nothing derived from the record ever knows what one of it weighs`() {
        val rows = listOf(
            row(1, "Bread", 60.0, "g", kcal = 150),
            row(2, "Bread", 2.0, "slice", kcal = 160),
            row(3, "Stew"),
            row(4, "Yoghurt", 180.0, "g"),
        )

        assertThat(DerivedFoods.from(rows).foods.map { it.facts.gramsPerUnit }).containsExactly(
            null, null, null,
        )
    }

    // --- Which row a fact is taken from ---------------------------------------------------------

    /**
     * By source, not by date. A number the owner typed a year ago is better than one a model
     * guessed this morning, and the whole point of recording where a number came from is to be able
     * to say so.
     */
    @Test
    fun `the better source wins over the more recent row`() {
        val rows = listOf(
            row(1, "Yoghurt", 100.0, "g", kcal = 60, source = Source.TYPED, confidence = null, loggedAtMillis = 1_000),
            row(2, "Yoghurt", 100.0, "g", kcal = 99, source = Source.AI_ESTIMATE, loggedAtMillis = 9_000),
        )

        val per100g = DerivedFoods.from(rows).foods.single().facts.per100g!!

        assertThat(per100g.nutrients.kcal).isWithin(0.001).of(60.0)
        assertThat(per100g.provenance.source).isEqualTo(Source.TYPED)
    }

    @Test
    fun `a tie on source is broken by the most recent row`() {
        val rows = listOf(
            row(1, "Yoghurt", 100.0, "g", kcal = 60, source = Source.TYPED, confidence = null, loggedAtMillis = 1_000),
            row(2, "Yoghurt", 100.0, "g", kcal = 70, source = Source.TYPED, confidence = null, loggedAtMillis = 9_000),
        )

        assertThat(DerivedFoods.from(rows).foods.single().facts.per100g!!.nutrients.kcal)
            .isWithin(0.001).of(70.0)
    }

    /**
     * A row copied from a past meal knows nothing on its own account, so its rank is the bottom one
     * and literally anything the owner later types or scans replaces it. Relabelling it as an
     * estimate to make the ranking tidy would be inventing provenance, which is the one thing the
     * record exists to prevent.
     */
    @Test
    fun `a repeated row keeps its own source and ranks below everything`() {
        val rows = listOf(
            row(1, "Yoghurt", 100.0, "g", kcal = 60, source = Source.REPEATED, confidence = null, loggedAtMillis = 9_000),
            row(2, "Yoghurt", 100.0, "g", kcal = 99, source = Source.AI_ESTIMATE, loggedAtMillis = 1_000),
        )

        val per100g = DerivedFoods.from(rows).foods.single().facts.per100g!!

        assertThat(per100g.nutrients.kcal).isWithin(0.001).of(99.0)
        assertThat(per100g.provenance.source).isEqualTo(Source.AI_ESTIMATE)
    }

    @Test
    fun `a food with nothing better than a repeated row says so`() {
        val rows = listOf(
            row(1, "Yoghurt", 100.0, "g", kcal = 60, source = Source.REPEATED, confidence = null),
        )

        val provenance = DerivedFoods.from(rows).foods.single().facts.per100g!!.provenance

        assertThat(provenance.source).isEqualTo(Source.REPEATED)
        assertThat(provenance.rank).isEqualTo(0)
    }

    @Test
    fun `an estimate's confidence carries onto the food`() {
        val rows = listOf(
            row(1, "Yoghurt", 100.0, "g", source = Source.AI_ESTIMATE, confidence = Confidence.LOW),
        )

        assertThat(DerivedFoods.from(rows).foods.single().facts.per100g!!.provenance.confidence)
            .isEqualTo(Confidence.LOW)
    }

    /**
     * Each fact is chosen on its own, so a food can hold a figure worked back from a packet's row
     * for 100 grams and a guess for what one slice is worth. One provenance for the whole food would
     * have to claim one about the other.
     *
     * The per-100 g is filed as copied, not as the label's: it is the row scaled, and a scanned row
     * is whole grams (D38), so it is not the packet's statement (D43, issue #29). This test asserted
     * LABEL before that decision.
     */
    @Test
    fun `the two facts are chosen independently and keep their own sources`() {
        val rows = listOf(
            row(1, "Bread", 100.0, "g", kcal = 250, source = Source.LABEL, confidence = null),
            row(2, "Bread", 1.0, "slice", kcal = 80, source = Source.AI_ESTIMATE, confidence = Confidence.HIGH),
        )

        val facts = DerivedFoods.from(rows).foods.single().facts

        assertThat(facts.per100g!!.provenance.source).isEqualTo(Source.REPEATED)
        assertThat(facts.per100g!!.provenance.confidence).isNull()
        assertThat(facts.perUnit!!.provenance.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(facts.perUnit!!.provenance.confidence).isEqualTo(Confidence.HIGH)
    }

    // --- A figure worked back from a label row (D43, issue #29) ---------------------------------

    /**
     * A scanned row is whole grams (D38): 30 g of a packet printing 0.5 g of fat per 100 g is a row
     * of 0 g. Scaling it back to 100 g gives 0.0 — a number the packet never printed — so it cannot
     * be filed under the label's name (D4). It is filed as copied from a past meal, which is what it
     * is, and ranks below anything he typed or read off a packet, so it can fill a blank but never
     * overwrite one.
     *
     * The row has the shape a backup restore hands over for an item naming no food — no time, no
     * confidence — so this is also the local proof of that door, whose own test runs only in CI.
     */
    @Test
    fun `a figure worked back from a label row is filed as copied, not as the label's`() {
        val rows = listOf(
            row(
                0, "Rice cakes", 30.0, "g",
                kcal = 116, proteinG = 2, carbsG = 24, fatG = 0,
                source = Source.LABEL, confidence = null, loggedAtMillis = 0,
            ),
        )

        val per100g = DerivedFoods.from(rows).foods.single().facts.per100g!!

        assertThat(per100g.provenance.source).isEqualTo(Source.REPEATED)
        assertThat(per100g.provenance.confidence).isNull()
        assertThat(per100g.provenance.rank).isLessThan(Provenance.rankOf(Source.TYPED))
        assertThat(per100g.provenance.rank).isLessThan(Provenance.rankOf(Source.LABEL))
        // The arithmetic is unchanged; only what the figure claims to be is.
        assertThat(per100g.nutrients.kcal).isWithin(1e-9).of(116 * 100 / 30.0)
        assertThat(per100g.nutrients.fatG).isWithin(1e-9).of(0.0)
    }

    /** The same for what one of it is worth: the row divided by its count is not the packet's. */
    @Test
    fun `a label row counted in units gives a per-one figure filed as copied`() {
        val rows = listOf(
            row(1, "Granola bar", 2.0, "bar", kcal = 380, source = Source.LABEL, confidence = null),
        )

        val perUnit = DerivedFoods.from(rows).foods.single().facts.perUnit!!

        assertThat(perUnit.unitName).isEqualTo("bar")
        assertThat(perUnit.nutrients.kcal).isWithin(1e-9).of(190.0)
        assertThat(perUnit.provenance.source).isEqualTo(Source.REPEATED)
        assertThat(perUnit.provenance.confidence).isNull()
    }

    /**
     * Even the one fact that is the row itself, unscaled: "one portion was worth this" is a meal he
     * logged, not a statement a packet made about the food.
     */
    @Test
    fun `a label row with no amount gives a portion filed as copied`() {
        val rows = listOf(row(1, "Snack", 0.0, "", kcal = 150, source = Source.LABEL, confidence = null))

        val facts = DerivedFoods.from(rows).foods.single().facts

        assertThat(facts.onlyAPortion).isTrue()
        assertThat(facts.perUnit!!.unitName).isEqualTo(FoodFacts.PORTION)
        assertThat(facts.perUnit!!.provenance.source).isEqualTo(Source.REPEATED)
        assertThat(facts.perUnit!!.provenance.confidence).isNull()
    }

    /**
     * Within a group, a row is chosen by the rank of the fact it would make, not by its own source.
     * Chosen by its own, the label row (3) beats the typed one (2) and then files its figure as
     * copied (0), throwing the typed row's honest figure away — a food worse than the one offering
     * each row in turn to the guarded statements would have produced.
     */
    @Test
    fun `a typed row is not passed over for a label row whose figure can only be copied`() {
        val rows = listOf(
            row(1, "Rice cakes", 100.0, "g", kcal = 380, source = Source.TYPED, confidence = null, loggedAtMillis = 1_000),
            row(2, "Rice cakes", 60.0, "g", kcal = 232, source = Source.LABEL, confidence = null, loggedAtMillis = 9_000),
        )

        val per100g = DerivedFoods.from(rows).foods.single().facts.per100g!!

        assertThat(per100g.nutrients.kcal).isWithin(1e-9).of(380.0)
        assertThat(per100g.provenance.source).isEqualTo(Source.TYPED)
        assertThat(per100g.provenance.setAtMillis).isEqualTo(1_000)
    }

    /**
     * The cost D43 accepts: a copied figure ranks below a model's estimate too. A rounded label
     * figure is arguably better than a guess, but the ranking only says it is not the label, and D4
     * prefers the conservative claim.
     */
    @Test
    fun `an estimate outranks a figure worked back from a label`() {
        val rows = listOf(
            row(1, "Rice cakes", 100.0, "g", kcal = 400, source = Source.AI_ESTIMATE, confidence = Confidence.MEDIUM, loggedAtMillis = 1_000),
            row(2, "Rice cakes", 60.0, "g", kcal = 232, source = Source.LABEL, confidence = null, loggedAtMillis = 9_000),
        )

        val per100g = DerivedFoods.from(rows).foods.single().facts.per100g!!

        assertThat(per100g.nutrients.kcal).isWithin(1e-9).of(400.0)
        assertThat(per100g.provenance.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(per100g.provenance.confidence).isEqualTo(Confidence.MEDIUM)
    }

    // --- Which unit is kept ---------------------------------------------------------------------

    @Test
    fun `the most frequent counting unit wins`() {
        val rows = listOf(
            row(1, "Bread", 1.0, "slice", kcal = 80),
            row(2, "Bread", 2.0, "slice", kcal = 160),
            row(3, "Bread", 1.0, "cup", kcal = 300),
        )

        assertThat(DerivedFoods.from(rows).foods.single().facts.perUnit!!.unitName)
            .isEqualTo("slice")
    }

    /**
     * The best row is chosen WITHIN the winning unit, not across all of them.
     *
     * The outlier must outrank the winning unit's rows, or it loses on rank alone and the test
     * cannot tell the two rules apart. It is TYPED for that reason: it used to be LABEL, which since
     * D43 (#29) ranks as a copied figure, below the estimates.
     */
    @Test
    fun `the best row is chosen within the unit that won`() {
        val rows = listOf(
            row(1, "Bread", 1.0, "slice", kcal = 80, source = Source.AI_ESTIMATE),
            row(2, "Bread", 1.0, "slice", kcal = 90, source = Source.AI_ESTIMATE),
            row(3, "Bread", 1.0, "cup", kcal = 300, source = Source.TYPED, confidence = null),
        )

        val perUnit = DerivedFoods.from(rows).foods.single().facts.perUnit!!

        assertThat(perUnit.unitName).isEqualTo("slice")
        assertThat(perUnit.nutrients.kcal).isWithin(0.001).of(90.0)
    }

    @Test
    fun `units differing only by spelling are the same unit`() {
        val rows = listOf(
            row(1, "Bread", 1.0, "Slice", kcal = 80),
            row(2, "Bread", 1.0, "slice", kcal = 80),
            row(3, "Bread", 1.0, "cup", kcal = 300),
        )

        assertThat(DerivedFoods.from(rows).foods.single().facts.perUnit!!.unitName.lowercase())
            .isEqualTo("slice")
    }

    /**
     * Millilitres are counted in millilitres, and never read as grams (issue #26).
     *
     * This test used to assert the opposite — 250 ml of milk as "50 kcal per 100 g" — which was the
     * defect pinned in place: a volume stored as a weight, an estimate presented as a measurement
     * (D4). Close for milk only because milk is nearly as dense as water. The row says what 250 ml
     * was worth, so the food knows what ONE ml is worth, and nothing about grams at all — the same
     * shape as a food whose own unit the owner typed as "ml" in the editor.
     */
    @Test
    fun `millilitres are counted in millilitres, never read as grams`() {
        val rows = listOf(row(1, "Milk", 250.0, "ml", kcal = 125))

        val facts = DerivedFoods.from(rows).foods.single().facts

        assertThat(facts.per100g).isNull()
        assertThat(facts.perUnit!!.unitName).isEqualTo("ml")
        assertThat(facts.perUnit!!.nutrients.kcal).isWithin(0.001).of(0.5)
    }

    /**
     * Kilograms were the dangerous case: a row of half a kilo at 650 kcal became 130,000 kcal per
     * 100 g, and every meal that weighed the food afterwards was wrong by thousands (issue #26).
     */
    @Test
    fun `kilograms are counted in kilograms, never divided as though they were grams`() {
        val rows = listOf(row(1, "Rice", 0.5, "kg", kcal = 650))

        val facts = DerivedFoods.from(rows).foods.single().facts

        assertThat(facts.per100g).isNull()
        assertThat(facts.perUnit!!.unitName).isEqualTo("kg")
        assertThat(facts.perUnit!!.nutrients.kcal).isWithin(0.001).of(1300.0)
    }

    /** Litres and ounces the same way, in Hebrew as well: only the gram is a gram. */
    @Test
    fun `litres and ounces and Hebrew millilitres are none of them grams`() {
        listOf("l" to 1.0, "oz" to 2.0, "מ\"ל" to 200.0).forEach { (unit, amount) ->
            val facts = DerivedFoods.from(listOf(row(1, "Juice", amount, unit, kcal = 100)))
                .foods.single().facts

            assertThat(facts.per100g).isNull()
            assertThat(facts.perUnit!!.unitName).isEqualTo(unit)
        }
    }

    /** A food met in grams and in millilitres keeps both, each honest about its own unit. */
    @Test
    fun `grams fill the per-100-g figure and millilitres the per-ml one, side by side`() {
        val rows = listOf(
            row(1, "Yoghurt", 200.0, "g", kcal = 120),
            row(2, "Yoghurt", 150.0, "ml", kcal = 95),
        )

        val facts = DerivedFoods.from(rows).foods.single().facts

        assertThat(facts.per100g!!.nutrients.kcal).isWithin(0.001).of(60.0)
        assertThat(facts.perUnit!!.unitName).isEqualTo("ml")
    }

    @Test
    fun `Hebrew grams are measured too`() {
        val rows = listOf(row(1, "חומוס", 50.0, "גרם", kcal = 150))

        assertThat(DerivedFoods.from(rows).foods.single().facts.per100g!!.nutrients.kcal)
            .isWithin(0.001).of(300.0)
    }

    // --- What must not happen -------------------------------------------------------------------

    /**
     * The conversion reads rows and writes foods. It has no way to change a stored number, and this
     * asserts the obvious thing so that a later change which gives it one fails here first.
     */
    @Test
    fun `the rows handed in are not altered`() {
        val rows = listOf(row(1, "Yoghurt", 180.0, "g", kcal = 130))
        val before = rows.map { it.copy() }

        DerivedFoods.from(rows)

        assertThat(rows).isEqualTo(before)
    }

    /** Every derived food is loggable: the invariant that at least one number group is present. */
    @Test
    fun `every derived food knows at least one way of counting`() {
        val rows = listOf(
            row(1, "Stew"),
            row(2, "Yoghurt", 180.0, "g"),
            row(3, "Bread", 2.0, "slice"),
            row(4, "Odd", amount = -5.0, unit = "g"),
        )

        val derived = DerivedFoods.from(rows)

        assertThat(derived.foods).hasSize(4)
        derived.foods.forEach { food ->
            assertThat(food.facts.per100g != null || food.facts.perUnit != null).isTrue()
        }
    }
}
