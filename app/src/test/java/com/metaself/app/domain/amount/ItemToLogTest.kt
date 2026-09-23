package com.metaself.app.domain.amount

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.GramsPerUnit
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerUnit
import com.metaself.app.domain.food.Provenance
import org.junit.jupiter.api.Test

/**
 * An item being logged from a description is what it is worth times how much was had (D53 §1), and
 * says where its figures came from by where the worth came from (§3).
 *
 * Every figure here is invented, and is one of the spec's worked examples where it has one.
 */
class ItemToLogTest {

    private val burgerRate = Rate(Nutrients(250.0, 18.0, 0.0, 20.0), Per.HUNDRED)

    private fun burger(amount: String = "200") = ItemToLog(
        name = "Beef burger",
        detail = "",
        amountText = amount,
        unit = "g",
        worth = Worth.Estimated(burgerRate, Confidence.MEDIUM),
        foodId = null,
    )

    private fun bun(amount: String = "1", detail: String = "") = ItemToLog(
        name = "Hamburger bun",
        detail = detail,
        amountText = amount,
        unit = "bun",
        worth = Worth.Estimated(Rate(Nutrients(150.0, 5.0, 28.0, 2.0), Per.ONE), Confidence.MEDIUM),
        foodId = null,
    )

    private fun typed() = Provenance(Source.TYPED, null, setAtMillis = 0)

    @Test
    fun `an estimate times a typed amount is still an estimate`() {
        val numbers = burger("150").numbers!!

        assertThat(listOf(numbers.kcal, numbers.proteinG, numbers.carbsG, numbers.fatG))
            .containsExactly(375, 27, 0, 30).inOrder()
        assertThat(numbers.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(numbers.confidence).isEqualTo(Confidence.MEDIUM)
    }

    @Test
    fun `the amount changes the total and nothing about the worth`() {
        val at200 = burger("200")
        val at150 = at200.copy(amountText = "150")

        assertThat(at200.numbers!!.kcal).isEqualTo(500)
        assertThat(at150.numbers!!.kcal).isEqualTo(375)
        assertThat(at150.worth).isEqualTo(at200.worth)
    }

    @Test
    fun `typing one figure of the worth makes the row typed with no confidence`() {
        val retyped = burger().withTypedRate(
            burgerRate.copy(nutrients = burgerRate.nutrients.copy(kcal = 240.0)),
        )

        val numbers = retyped.numbers!!
        assertThat(retyped.worth).isInstanceOf(Worth.Typed::class.java)
        assertThat(numbers.source).isEqualTo(Source.TYPED)
        assertThat(numbers.confidence).isNull()
        assertThat(numbers.kcal).isEqualTo(480)
        assertThat(numbers.proteinG).isEqualTo(36)
    }

    @Test
    fun `typing over his food's worth keeps the row on his food`() {
        val pita = pita()
        val item = ItemToLog("Pita", "", "1", "pita", Worth.YourFood(pita, CountedAs.UNITS), pita.id)

        val retyped = item.withTypedRate(Rate(Nutrients(240.0, 8.0, 50.0, 1.0), Per.ONE))

        assertThat(retyped.foodId).isEqualTo(pita.id)
        assertThat(retyped.toFoodItem()!!.foodId).isEqualTo(pita.id)
        assertThat(retyped.numbers!!.source).isEqualTo(Source.TYPED)
    }

    @Test
    fun `his food's figures carry his food's source`() {
        val pita = pita()
        val item = ItemToLog("Pita", "", "1", "pita", Worth.YourFood(pita, CountedAs.UNITS), pita.id)

        val numbers = item.numbers!!
        assertThat(listOf(numbers.kcal, numbers.proteinG, numbers.carbsG, numbers.fatG))
            .containsExactly(250, 8, 50, 1).inOrder()
        assertThat(numbers.source).isEqualTo(Source.TYPED)
        assertThat(numbers.confidence).isNull()
        assertThat(item.toFoodItem()!!.foodId).isEqualTo(pita.id)
    }

    /** Pins that `Logging`'s weaker-of rule is what decides, not a source of this type's own. */
    @Test
    fun `a figure computed from two of his food's facts carries the weaker source`() {
        val slices = Food(
            id = 9,
            name = "Sourdough",
            facts = FoodFacts(
                perUnit = PerUnit(
                    "slice",
                    Nutrients(120.0, 4.0, 24.0, 1.0),
                    Provenance(Source.AI_ESTIMATE, Confidence.MEDIUM, setAtMillis = 0),
                ),
                gramsPerUnit = GramsPerUnit(30.0, typed()),
            ),
        )
        val item = ItemToLog("Sourdough", "", "60", "g", Worth.YourFood(slices, CountedAs.GRAMS), 9)

        val numbers = item.numbers!!
        assertThat(numbers.kcal).isEqualTo(240)
        assertThat(numbers.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(numbers.confidence).isEqualTo(Confidence.MEDIUM)
    }

    @Test
    fun `per 100 ml divides by 100`() {
        val juice = ItemToLog(
            name = "Orange juice",
            detail = "",
            amountText = "300",
            unit = "ml",
            worth = Worth.Estimated(Rate(Nutrients(45.0, 0.0, 11.0, 0.0), Per.HUNDRED), Confidence.HIGH),
            foodId = null,
        )

        val numbers = juice.numbers!!
        assertThat(listOf(numbers.kcal, numbers.proteinG, numbers.carbsG, numbers.fatG))
            .containsExactly(135, 0, 33, 0).inOrder()
    }

    /** 9 × 0.6 = 5.4 → 5, 50 × 0.6 = 30, 4 × 0.6 = 2.4 → 2: each figure rounded once, on its own. */
    @Test
    fun `rounding happens once, figure by figure`() {
        val bun = ItemToLog(
            name = "Hamburger bun",
            detail = "",
            amountText = "60",
            unit = "g",
            worth = Worth.Typed(Rate(Nutrients(270.0, 9.0, 50.0, 4.0), Per.HUNDRED)),
            foodId = null,
        )

        val numbers = bun.numbers!!
        assertThat(listOf(numbers.kcal, numbers.proteinG, numbers.carbsG, numbers.fatG))
            .containsExactly(162, 5, 30, 2).inOrder()
        assertThat(numbers.source).isEqualTo(Source.TYPED)
    }

    @Test
    fun `a blank, zero or refused amount cannot be logged`() {
        listOf(burger(""), burger("0"), burger("abc"), burger("5001"), bun("101")).forEach {
            assertThat(it.toFoodItem()).isNull()
            assertThat(it.numbers).isNull()
        }
        assertThat(burger("").amountTooMuch).isFalse()
        assertThat(burger("0").amountTooMuch).isFalse()
        assertThat(burger("abc").amountTooMuch).isFalse()
        assertThat(burger("5001").amountTooMuch).isTrue()
        assertThat(bun("101").amountTooMuch).isTrue()
    }

    @Test
    fun `the ceiling is the one every amount box has`() {
        assertThat(burger().most).isEqualTo(BelievableAmount.GRAMS)
        assertThat(bun().most).isEqualTo(BelievableAmount.COUNT)
        assertThat(burger("5000").toFoodItem()).isNotNull()
        assertThat(bun("100").toFoodItem()).isNotNull()
    }

    @Test
    fun `a comma is a decimal point`() {
        assertThat(bun("1,5").amountOrNull).isEqualTo(1.5)
    }

    @Test
    fun `the detail is kept in the row's portion words`() {
        assertThat(bun(detail = "sesame, toasted").toFoodItem()!!.portion)
            .isEqualTo("1 bun (sesame, toasted)")
        assertThat(bun(detail = "  ").toFoodItem()!!.portion).isEqualTo("1 bun")
    }

    @Test
    fun `the row keeps the amount and unit as arithmetic, the name, and no food of its own`() {
        val row = burger("150").toFoodItem()!!

        assertThat(row.name).isEqualTo("Beef burger")
        assertThat(row.portionAmount).isEqualTo(150.0)
        assertThat(row.portionUnit).isEqualTo("g")
        assertThat(row.kcal).isEqualTo(375)
        assertThat(row.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(row.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(row.foodId).isNull()
    }

    /** So D37's plural, chosen when a row is drawn, still applies to what is stored. */
    @Test
    fun `the app's own portion is stored as the app writes it`() {
        val portions = bun("2").copy(unit = "portion")

        assertThat(portions.toFoodItem()!!.portion).isEqualTo("2 portion")
    }

    @Test
    fun `the worth line of an estimate is its own rate`() {
        assertThat(burger().rateLine).isEqualTo(burgerRate)
    }

    @Test
    fun `the worth line of his food is what one of it, or 100 g of it, logs as`() {
        val pita = pita()
        val counted = ItemToLog("Pita", "", "2", "pita", Worth.YourFood(pita, CountedAs.UNITS), pita.id)

        assertThat(counted.rateLine).isEqualTo(Rate(Nutrients(250.0, 8.0, 50.0, 1.0), Per.ONE))
    }

    private fun pita() = Food(
        id = 7,
        name = "Pita",
        facts = FoodFacts(perUnit = PerUnit("pita", Nutrients(250.0, 8.0, 50.0, 1.0), typed())),
    )
}
