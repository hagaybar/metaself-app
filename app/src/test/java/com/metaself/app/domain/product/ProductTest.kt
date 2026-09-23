package com.metaself.app.domain.product

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.amount.BelievableAmount
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.Nutrients
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProductTest {

    /** The real figures from Bamba's entry in Open Food Facts, checked on 2026-09-04. */
    private val bamba = Product(
        barcode = "7290000066318",
        name = "במבה",
        brand = "אסם",
        kcalPer100g = 535.0,
        proteinPer100g = 17.0,
        carbsPer100g = 49.0,
        fatPer100g = 30.0,
        servingSizeG = 80.0,
    )

    @Test
    fun `a whole eighty gram packet`() {
        val item = bamba.toFoodItem(80.0)!!

        assertThat(item.kcal).isEqualTo(428)
        assertThat(item.proteinG).isEqualTo(14)
        assertThat(item.carbsG).isEqualTo(39)
        assertThat(item.fatG).isEqualTo(24)
    }

    @Test
    fun `a hundred grams is exactly what the label says`() {
        val item = bamba.toFoodItem(100.0)!!

        assertThat(item.kcal).isEqualTo(535)
        assertThat(item.proteinG).isEqualTo(17)
    }

    @Test
    fun `nothing eaten is not a thing to log`() {
        assertThat(bamba.toFoodItem(0.0)).isNull()
        assertThat(bamba.toFoodItem(-50.0)).isNull()
    }

    /** D4: a label is a declaration, not a guess, and the record says which. */
    @Test
    fun `it is marked as coming from the label and carries no confidence`() {
        val item = bamba.toFoodItem(80.0)!!

        assertThat(item.source).isEqualTo(Source.LABEL)
        assertThat(item.confidence).isNull()
    }

    /** So that a barcode entry can be adjusted afterwards like anything else. */
    @Test
    fun `the portion keeps its numbers as well as its words`() {
        val item = bamba.toFoodItem(80.0)!!

        assertThat(item.portion).isEqualTo("80 g")
        assertThat(item.portionAmount).isEqualTo(80.0)
        assertThat(item.portionUnit).isEqualTo("g")
    }

    @Test
    fun `the amount box opens at the package's own serving`() {
        assertThat(bamba.suggestedGrams).isEqualTo(80.0)
    }

    @Test
    fun `and at a hundred grams when the package does not say`() {
        assertThat(bamba.copy(servingSizeG = null).suggestedGrams).isEqualTo(100.0)
    }

    /**
     * A packet typed on the label form before D39 (issue #31) may hold a serving of "Infinity",
     * and a serving_size of 309 digits reads as one too. Offered, it would open the amount box at
     * an amount nothing can be logged for; the package has then said nothing usable, so 100 g.
     */
    @Test
    fun `and at a hundred grams when the package's serving is not finite`() {
        assertThat(bamba.copy(servingSizeG = Double.POSITIVE_INFINITY).suggestedGrams)
            .isEqualTo(100.0)
        assertThat(bamba.copy(servingSizeG = Double.NaN).suggestedGrams).isEqualTo(100.0)
    }

    @Test
    fun `the label names the product and its brand`() {
        assertThat(bamba.label).isEqualTo("במבה — אסם")
        assertThat(bamba.copy(brand = null).label).isEqualTo("במבה")
    }

    /**
     * A packet printing half a gram of fat, with figures no whole-gram row can be worked back to —
     * so an exact comparison can only pass if nothing was computed on the way (issue #28).
     */
    private val rice = Product(
        barcode = "2000000000011",
        name = "Rice cakes",
        brand = null,
        kcalPer100g = 387.4,
        proteinPer100g = 8.3,
        carbsPer100g = 81.6,
        fatPer100g = 0.5,
    )

    /**
     * What a food learns from a scan is the label itself, not the rounded row it was logged as.
     * Compared exactly, not within a tolerance: a copy has no error to tolerate (D4, issue #28).
     */
    @Test
    fun `the label's figures per 100 g are the product's own, decimals and all`() {
        assertThat(rice.nutrientsPer100g()).isEqualTo(Nutrients(387.4, 8.3, 81.6, 0.5))
    }

    /**
     * A negative or not-a-number figure is no amount of food, so there are no label figures to
     * teach — never a zero put in its place. A zero, though, is a quantity the label can state.
     */
    @Test
    fun `a figure that is not a quantity of food gives no label figures`() {
        assertThat(rice.copy(fatPer100g = -0.3).nutrientsPer100g()).isNull()
        assertThat(rice.copy(fatPer100g = Double.NaN).nutrientsPer100g()).isNull()

        val zero = rice.copy(fatPer100g = 0.0).nutrientsPer100g()
        assertThat(zero).isNotNull()
        assertThat(zero!!.fatG).isEqualTo(0.0)
    }

    /**
     * An infinite figure is no amount of food either. Until D39 (issue #31) it was the one that
     * reached a scan's logging — the packet-label form kept "Infinity" as a number, and rounding it
     * does not throw the way a not-a-number does (D38). The doors refuse it now; this pins the last
     * line behind them.
     */
    @Test
    fun `an infinite figure gives no label figures`() {
        assertThat(rice.copy(kcalPer100g = Double.POSITIVE_INFINITY).nutrientsPer100g()).isNull()
        assertThat(rice.copy(fatPer100g = Double.POSITIVE_INFINITY).nutrientsPer100g()).isNull()
    }

    /**
     * The one rule every door applies (D39, issue #31), with the ceilings D42 (issue #32) gave it:
     * a figure is a quantity of food when it is a finite number not below zero and not above what
     * a label for 100 g of anything prints — 1000 kcal, or 110 g of any one macro. Exactly at the
     * ceiling is food; zero is a quantity a label can state. The figure is judged with its field,
     * so nothing can check a packet figure without its ceiling.
     */
    @Test
    fun `a packet figure at its ceiling is food, and one past it is not`() {
        val kcalMost = BelievableAmount.KCAL_PER_100G
        val macroMost = BelievableAmount.MACRO_PER_100G

        assertThat(Product.isQuantityOfFood(ProductField.KCAL, kcalMost)).isTrue()
        assertThat(Product.isQuantityOfFood(ProductField.FAT, macroMost)).isTrue()
        assertThat(Product.isQuantityOfFood(ProductField.PROTEIN, 0.0)).isTrue()
        assertThat(Product.isQuantityOfFood(ProductField.CARBS, 0.5)).isTrue()
        assertThat(Product.isQuantityOfFood(ProductField.KCAL, 535.0)).isTrue()

        assertThat(Product.isQuantityOfFood(ProductField.KCAL, kcalMost + 0.5)).isFalse()
        assertThat(Product.isQuantityOfFood(ProductField.CARBS, macroMost + 0.5)).isFalse()
        // A macro's ceiling is not the calories': 150 g of protein in 100 g is no food.
        assertThat(Product.isQuantityOfFood(ProductField.PROTEIN, 150.0)).isFalse()
        assertThat(Product.isQuantityOfFood(ProductField.FAT, Double.POSITIVE_INFINITY)).isFalse()
        assertThat(Product.isQuantityOfFood(ProductField.FAT, Double.NaN)).isFalse()
        assertThat(Product.isQuantityOfFood(ProductField.FAT, -0.1)).isFalse()
        assertThat(Product.isQuantityOfFood(ProductField.KCAL, Double.NEGATIVE_INFINITY)).isFalse()
    }

    /**
     * What the ceilings sit where they do for (D42): a real label is never refused. An oil label
     * rounded per spoon and scaled to 100 g prints about 923 kcal, and 14 g of fat in a 13.6 g
     * spoon is 103 g per 100 g — past the chemistry, but what the packet says, and refusing it
     * would leave him typing a number it does not state (D4). An oil's kilojoules typed as
     * calories, about 3700, is still no food.
     */
    @Test
    fun `a rounded oil label is food, and its kilojoules typed as calories are not`() {
        assertThat(Product.isQuantityOfFood(ProductField.KCAL, 923.0)).isTrue()
        assertThat(Product.isQuantityOfFood(ProductField.FAT, 103.0)).isTrue()

        assertThat(Product.isQuantityOfFood(ProductField.KCAL, 3_700.0)).isFalse()
    }

    /**
     * The name and the serving are not per-100 g figures. The serving's own ceiling is an amount in
     * grams (5000), never a macro's per-100 g ceiling — a silent macro ceiling would turn a 250 g
     * serving away. A name is no figure at all: asking about one is a caller's mistake, and it
     * throws rather than answer, so a regression to a silent answer fails here.
     */
    @Test
    fun `a serving is judged by the grams ceiling, and asking about a name throws`() {
        assertThat(Product.isQuantityOfFood(ProductField.SERVING, 250.0)).isTrue()
        assertThat(Product.isQuantityOfFood(ProductField.SERVING, BelievableAmount.GRAMS)).isTrue()
        assertThat(Product.isQuantityOfFood(ProductField.SERVING, 5_000.5)).isFalse()

        assertThrows<IllegalArgumentException> {
            Product.isQuantityOfFood(ProductField.NAME, 250.0)
        }
    }

    /**
     * A finite figure no packet can hold — 1e12 kcal per 100 g — used to pass the rule, and rounding
     * it saturates rather than throws, so a 2,147,483,647-kcal row went on the day. Past its ceiling
     * it is no food, so nothing is logged and there are no label figures to teach (D42).
     */
    @Test
    fun `logging a packet with an absurd figure refuses rather than logs Int MAX_VALUE`() {
        val absurd = rice.copy(kcalPer100g = 1e12)

        assertThat(absurd.figuresAreFood).isFalse()
        assertThat(absurd.toFoodItem(30.0)).isNull()
        assertThat(absurd.nutrientsPer100g()).isNull()

        assertThat(rice.copy(fatPer100g = BelievableAmount.MACRO_PER_100G + 0.5).toFoodItem(30.0))
            .isNull()
    }

    /** The amount has the same ceiling the scan's grams box has: 5000 g logs, the next gram does not. */
    @Test
    fun `an amount past 5000 g logs nothing, and 5000 g logs`() {
        val atTheCeiling = rice.toFoodItem(5_000.0)
        assertThat(atTheCeiling).isNotNull()
        assertThat(atTheCeiling!!.portionAmount).isEqualTo(5_000.0)
        assertThat(atTheCeiling.kcal).isEqualTo(19_370)

        assertThat(rice.toFoodItem(Math.nextUp(5_000.0))).isNull()
        assertThat(rice.toFoodItem(1e300)).isNull()
    }

    /**
     * A pack's net weight of 6 kg is no serving to offer: the scan would open with its own grams box
     * already refused. Offered only when believable; else 100 g, as for an infinite one.
     */
    @Test
    fun `a serving past 5000 g is not offered as the amount`() {
        assertThat(bamba.copy(servingSizeG = 6_000.0).suggestedGrams).isEqualTo(100.0)
        assertThat(bamba.copy(servingSizeG = 5_000.0).suggestedGrams).isEqualTo(5_000.0)
    }

    /**
     * The crash the issue names: rounding a not-a-number throws, and it did so while the scan
     * screen was drawing. The doors refuse such a product now, so none the app builds today reaches
     * this; it is the last line behind them, so a caller holding such a product some other way gets
     * nothing to log rather than a crash (D39).
     */
    @Test
    fun `logging a product whose figure is not a number refuses rather than throws`() {
        assertThat(rice.copy(fatPer100g = Double.NaN).toFoodItem(30.0)).isNull()
    }

    /**
     * Infinite calories used to log a 2147483647-kcal row; minus infinity and a large negative fat
     * rounded below zero and crashed; a small negative logged a 0 g row. None is a quantity of
     * food, so none is logged — one rule, not "only what would round below zero" (D39).
     */
    @Test
    fun `logging a product with an infinite or negative figure refuses, however small`() {
        assertThat(rice.copy(kcalPer100g = Double.POSITIVE_INFINITY).toFoodItem(30.0)).isNull()
        assertThat(rice.copy(kcalPer100g = Double.NEGATIVE_INFINITY).toFoodItem(30.0)).isNull()
        assertThat(rice.copy(fatPer100g = -5.0).toFoodItem(30.0)).isNull()
        assertThat(rice.copy(fatPer100g = -0.3).toFoodItem(30.0)).isNull()
    }

    /**
     * The amount feeds the same arithmetic: zero fat times an infinite amount is not a number, and
     * the scan's grams box still reads "Infinity" (a follow-up, not this issue's). Logging nothing
     * beats crashing.
     */
    @Test
    fun `an amount that is not finite logs nothing`() {
        assertThat(rice.copy(fatPer100g = 0.0).toFoodItem(Double.POSITIVE_INFINITY)).isNull()
    }
}
