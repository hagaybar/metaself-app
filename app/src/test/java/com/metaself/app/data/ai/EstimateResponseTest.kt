package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.amount.Per
import com.metaself.app.domain.amount.Rate
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.food.Nutrients
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

/**
 * The reply read strictly (D53 §2). Every figure here is one of the spec's invented worked examples.
 */
class EstimateResponseTest {

    @Test
    fun `a two-item reply becomes a two-item proposal`() {
        val result = EstimateResponse.parse(replyWith(BURGER_AND_BUN))

        val proposal = (result as EstimateResult.Proposed).proposal
        assertThat(proposal.items.map { it.name }).containsExactly("Beef burger", "Hamburger bun")
            .inOrder()
        assertThat(proposal.note).isEqualTo("Assumed one bun of the usual size.")
    }

    /** Per 100 g, and the amount beside it, apart: the worth is not the total. */
    @Test
    fun `a per-100 reply is a worth per 100 and an amount`() {
        val burger = proposed(BURGER_AND_BUN).first()

        assertThat(burger.rate)
            .isEqualTo(Rate(Nutrients(250.0, 18.0, 0.0, 20.0), Per.HUNDRED))
        assertThat(burger.amount).isWithin(0.001).of(200.0)
        assertThat(burger.unit).isEqualTo("g")
        assertThat(burger.detail).isEqualTo("")
    }

    @Test
    fun `a per-one reply is a worth per piece`() {
        val bun = proposed(BURGER_AND_BUN)[1]

        assertThat(bun.rate).isEqualTo(Rate(Nutrients(150.0, 5.0, 28.0, 2.0), Per.ONE))
        assertThat(bun.amount).isWithin(0.001).of(1.0)
        assertThat(bun.unit).isEqualTo("bun")
        assertThat(bun.detail).isEqualTo("sesame, toasted")
    }

    /** The worth keeps its decimals; rounding happens once, when the row is logged (D53 §1). */
    @Test
    fun `a worth keeps its decimals`() {
        val halves = item(kcal = "52.5", fat = "0.5")

        assertThat(proposed(halves).single().rate.nutrients)
            .isEqualTo(Nutrients(52.5, 18.0, 0.0, 0.5))
    }

    /** 1200 kcal in 100 g of anything is past D42's per-100 ceiling of 1000: a slip, not food. */
    @Test
    fun `a per-100 figure past its ceiling drops the item`() {
        val items = proposed(item(name = "Burger", kcal = "1200") + "," + item(name = "Rice"))

        assertThat(items.map { it.name }).containsExactly("Rice")
    }

    /** The same 1200 is a believable worth for one piece: the per-one ceiling is 5000. */
    @Test
    fun `a per-one figure is judged against the per-one ceiling`() {
        val items = proposed(item(kcal = "1200", unit = "tray", figuresPer = "1", amount = "1"))

        assertThat(items.single().rate.nutrients.kcal).isEqualTo(1200.0)
    }

    @Test
    fun `a per-one macro past 500 g drops the item`() {
        val items = proposed(
            item(name = "Tray", protein = "501", unit = "tray", figuresPer = "1", amount = "1") +
                "," + item(name = "Rice"),
        )

        assertThat(items.map { it.name }).containsExactly("Rice")
    }

    @Test
    fun `a basis that is neither 100 nor 1 drops the item`() {
        val items =
            proposed(item(name = "Burger", figuresPer = "per serving") + "," + item(name = "Rice"))

        assertThat(items.map { it.name }).containsExactly("Rice")
    }

    /**
     * Per 100 of a piece means nothing — the basis is 100 only for grams or millilitres (D53 §2) —
     * and costing it would divide a count of buns by 100.
     */
    @Test
    fun `a per-100 worth of a counted piece drops the item`() {
        val items = proposed(
            item(name = "Bun", unit = "bun", amount = "1") + "," + item(name = "Rice"),
        )

        assertThat(items.map { it.name }).containsExactly("Rice")
    }

    /**
     * The reverse: per one gram or one millilitre is not a basis either (D53 §2 — figures are per 100
     * for grams and millilitres). Believed, 250 kcal "per 1 g" is within the per-one ceiling and makes
     * 200 g a 50,000-kcal row, whose worth would then be taught to a food.
     */
    @Test
    fun `a per-one worth of grams or millilitres drops the item`() {
        val items = proposed(
            item(name = "Burger", figuresPer = "1") + "," +
                item(name = "Juice", unit = "ml", amount = "330", figuresPer = "1") + "," +
                item(name = "Rice"),
        )

        assertThat(items.map { it.name }).containsExactly("Rice")
    }

    /**
     * No row arrives worth more than a whole item typed by hand may be (D42's 10,000 kcal and
     * 1000 g of a macro): 3000 kcal a piece is a believable worth, but four of them is not a
     * believable row. Three are.
     */
    @Test
    fun `an item whose row would be past a whole item's ceiling is dropped`() {
        val items = proposed(
            item(name = "Tray", unit = "tray", figuresPer = "1", amount = "4", kcal = "3000") + "," +
                item(name = "Tub", unit = "tub", figuresPer = "1", amount = "3", protein = "400") + "," +
                item(name = "Pie", unit = "pie", figuresPer = "1", amount = "3", kcal = "3000"),
        )

        assertThat(items.map { it.name }).containsExactly("Pie")
    }

    /** A dropped item is named on the proposal, so the row that is not there can be noticed. */
    @Test
    fun `the names of dropped items travel with the proposal`() {
        val result = EstimateResponse.parse(
            replyWith(
                items(
                    item(name = "Burger", kcal = "1200") + "," + item(name = "Rice") + "," +
                        item(name = "Sauce", fat = "-3"),
                ),
            ),
        )

        val proposal = (result as EstimateResult.Proposed).proposal
        assertThat(proposal.items.map { it.name }).containsExactly("Rice")
        assertThat(proposal.dropped).containsExactly("Burger", "Sauce").inOrder()
    }

    @Test
    fun `nothing dropped names nothing`() {
        val proposal = (EstimateResponse.parse(replyWith(BURGER_AND_BUN)) as EstimateResult.Proposed)
            .proposal

        assertThat(proposal.dropped).isEmpty()
    }

    @Test
    fun `per 100 ml is a worth per 100 of the millilitre`() {
        val juice = proposed(item(name = "Orange juice", unit = "ml", amount = "330")).single()

        assertThat(juice.rate.per).isEqualTo(Per.HUNDRED)
        assertThat(juice.unit).isEqualTo("ml")
    }

    /**
     * It used to be clamped to 0. A figure below nothing is not a food's figure, and a zero put in
     * its place is a number nobody stated (D4), so the item goes as a missing figure does.
     */
    @Test
    fun `a negative figure drops the item rather than becoming 0`() {
        val items = proposed(item(name = "Burger", fat = "-3") + "," + item(name = "Rice"))

        assertThat(items.map { it.name }).containsExactly("Rice")
    }

    /** The strict schema makes the detail required; a reply without it is not in that shape. */
    @Test
    fun `an item without its detail is dropped`() {
        val noDetail = """{"name":"Burger","amount":200,"unit":"g","figures_per":"100",
            "kcal":250,"protein_g":18,"carbs_g":0,"fat_g":20,"confidence":"MEDIUM"}"""

        val items = proposed(noDetail + "," + item(name = "Rice"))

        assertThat(items.map { it.name }).containsExactly("Rice")
    }

    /**
     * An amount past D42's ceiling is kept: the row arrives with its box saying so, and cannot be
     * saved until he changes it — exactly as if he had typed it (D53 §2).
     */
    @Test
    fun `an amount past the box's ceiling is kept, not dropped`() {
        val items = proposed(item(amount = "6000"))

        assertThat(items.single().amount).isWithin(0.001).of(6000.0)
    }

    /**
     * An item with no amount makes the whole reply one that did not give amounts (D34).
     *
     * It used to be accepted as "amount not stated" and logged. A row with a unit and no amount then
     * could not join a meal, and read on the day exactly like one that could (issue #23).
     */
    @Test
    fun `an item with no amount is a reply without amounts, and says which`() {
        val result = EstimateResponse.parse(
            replyWith(items(item(name = "Rice") + "," + item(name = "Stew", amount = "0"))),
        )

        assertThat(result).isEqualTo(EstimateResult.AmountMissing(listOf("Stew")))
    }

    /** A number without a unit says how many of nothing, which is not an amount either. */
    @Test
    fun `an item with a number and no unit is missing its amount too`() {
        val result = EstimateResponse.parse(
            replyWith(items(item(name = "Stew", amount = "2", unit = " ", figuresPer = "1"))),
        )

        assertThat(result).isEqualTo(EstimateResult.AmountMissing(listOf("Stew")))
    }

    @Test
    fun `confidence is carried through`() {
        val items = proposed(BURGER_AND_BUN)

        assertThat(items.map { it.confidence })
            .containsExactly(Confidence.MEDIUM, Confidence.HIGH).inOrder()
    }

    @Test
    fun `a reply with no items is unreadable, not an empty meal`() {
        val result = EstimateResponse.parse(replyWith("""{"note":"","items":[]}"""))

        assertThat(result).isInstanceOf(EstimateResult.Unreadable::class.java)
    }

    @Test
    fun `prose instead of JSON is unreadable and says so`() {
        val result = EstimateResponse.parse(replyWith("About 830 calories, I would guess."))

        assertThat(result).isInstanceOf(EstimateResult.Unreadable::class.java)
    }

    @Test
    fun `an item missing its calories is dropped rather than logged as zero`() {
        val noKcal = """{"name":"Burger","detail":"","amount":200,"unit":"g","figures_per":"100",
            "protein_g":18,"carbs_g":0,"fat_g":20,"confidence":"MEDIUM"}"""

        val items = proposed(noKcal + "," + item(name = "Rice"))

        assertThat(items.map { it.name }).containsExactly("Rice")
    }

    @Test
    fun `a reply whose every item is unusable is unreadable, not an empty proposal`() {
        val useless = """{"note":"","items":[{"name":"Burger","amount":200,"unit":"g"}]}"""

        assertThat(EstimateResponse.parse(replyWith(useless)))
            .isInstanceOf(EstimateResult.Unreadable::class.java)
    }

    @Test
    fun `an unknown confidence is read as low rather than refused`() {
        // Being told an estimate is "PROBABLY" should not lose the estimate. Reading it as LOW is
        // the safe direction: it shows a warning the owner can dismiss, rather than hiding doubt.
        val items = proposed(item(confidence = "PROBABLY"))

        assertThat(items.single().confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun `an empty body is unreadable`() {
        assertThat(EstimateResponse.parse("")).isInstanceOf(EstimateResult.Unreadable::class.java)
    }

    private fun proposed(itemsJson: String) =
        (EstimateResponse.parse(replyWith(items(itemsJson))) as EstimateResult.Proposed)
            .proposal.items

    private fun items(itemsJson: String): String =
        if (itemsJson.trimStart().startsWith("{\"note\"")) {
            itemsJson
        } else {
            """{"note":"","items":[$itemsJson]}"""
        }

    /** The burger of the spec's worked example, with any one field changed. */
    private fun item(
        name: String = "Beef burger",
        detail: String = "",
        amount: String = "200",
        unit: String = "g",
        figuresPer: String = "100",
        kcal: String = "250",
        protein: String = "18",
        carbs: String = "0",
        fat: String = "20",
        confidence: String = "MEDIUM",
    ): String = """{"name":"$name","detail":"$detail","amount":$amount,"unit":"$unit",
        "figures_per":"$figuresPer","kcal":$kcal,"protein_g":$protein,"carbs_g":$carbs,
        "fat_g":$fat,"confidence":"$confidence"}"""

    private fun replyWith(content: String): String =
        """{"choices":[{"message":{"content":${JsonPrimitive(content)}}}]}"""

    private companion object {
        const val BURGER_AND_BUN = """{"note":"Assumed one bun of the usual size.",
            "items":[
              {"name":"Beef burger","detail":"","amount":200,"unit":"g","figures_per":"100",
               "kcal":250,"protein_g":18,"carbs_g":0,"fat_g":20,"confidence":"MEDIUM"},
              {"name":"Hamburger bun","detail":"sesame, toasted","amount":1,"unit":"bun",
               "figures_per":"1","kcal":150,"protein_g":5,"carbs_g":28,"fat_g":2,
               "confidence":"HIGH"}]}"""
    }
}
