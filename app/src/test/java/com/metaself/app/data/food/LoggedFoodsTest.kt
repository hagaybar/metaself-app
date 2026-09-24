package com.metaself.app.data.food

import com.metaself.app.data.time.Now
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.amount.ItemToLog
import com.metaself.app.domain.amount.Per
import com.metaself.app.domain.amount.Rate
import com.metaself.app.domain.amount.Worth
import com.metaself.app.domain.amount.teaches
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.day.anItem
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodRetaught
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.Provenance
import com.metaself.app.domain.food.Replaced
import com.metaself.app.domain.product.Product
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Attaching a row to its food, and what the food learns from it (issue #28).
 *
 * A scanned row is whole grams (D38), so a food that worked its per-100 g figures back out of that
 * row would file a rounded number under the label's name — 0.5 g of fat per 100 g logged as 30 g
 * becomes a row of 0 g and a "label" figure of 0.0. D4 forbids exactly that. So a scan hands the
 * food the packet's own figures, and every other way in still teaches its food from the row — and
 * a figure worked back from a label row is never the label's (D43, #29): it is filed as copied from
 * a past meal, below anything typed or read off a packet.
 *
 * Run over the in-memory repository, which applies the same ranking as the guarded statements;
 * those statements themselves are pinned by `RoomFoodRepositoryTest`, in CI.
 */
class LoggedFoodsTest {

    /**
     * A packet printing half a gram of fat, with figures no whole-gram row can be worked back to —
     * so an exact comparison passes only if the food was handed the label rather than a sum.
     * Unbranded, so it meets an unbranded food of the same name.
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

    private val exact = Nutrients(387.4, 8.3, 81.6, 0.5)

    /**
     * Logging this packet, as the scan screen hands it over: whole grams, and the label with it.
     * [item] is the row; given only by the cases whose packet logging now refuses (D39).
     */
    private suspend fun LoggedFoods.scan(
        packet: Product,
        grams: Double,
        item: FoodItem = packet.toFoodItem(grams)!!,
    ) = attach(
        item,
        barcode = packet.barcode,
        brand = packet.brand,
        labelPer100g = packet.nutrientsPer100g(),
    )

    @Test
    fun `a scan logged as 30 g teaches its food the packet's figures, not the rounded row's`() = runTest {
        val foods = FakeFoodRepository()
        val item = rice.toFoodItem(30.0)!!

        val attached = LoggedFoods(foods).attach(
            item,
            barcode = rice.barcode,
            brand = null,
            labelPer100g = rice.nutrientsPer100g(),
        ).item

        // The row is D38's: whole grams, the label's name, nothing guessed.
        val food = foods.current.single()
        assertThat(item.fatG).isEqualTo(0)
        assertThat(attached).isEqualTo(item.copy(foodId = food.id))
        assertThat(attached.source).isEqualTo(Source.LABEL)

        // The food is the packet's, compared exactly: a copy has nothing to tolerate.
        val per100g = food.facts.per100g!!
        assertThat(per100g.nutrients).isEqualTo(exact)
        assertThat(per100g.provenance.source).isEqualTo(Source.LABEL)
        assertThat(per100g.provenance.confidence).isNull()
        assertThat(food.facts.perUnit).isNull()
        assertThat(food.facts.gramsPerUnit).isNull()
        assertThat(food.barcode).isEqualTo(rice.barcode)
    }

    /** At exactly 100 g the row rounds 0.5 up to 1, and the food must not believe the row. */
    @Test
    fun `the same packet logged as exactly 100 g still teaches half a gram, not the row's one`() = runTest {
        val foods = FakeFoodRepository()

        val attached = LoggedFoods(foods).scan(rice, grams = 100.0).item

        assertThat(attached.fatG).isEqualTo(1)
        val per100g = foods.current.single().facts.per100g!!
        assertThat(per100g.nutrients.fatG).isEqualTo(0.5)
        assertThat(per100g.nutrients).isEqualTo(exact)
        assertThat(per100g.provenance.source).isEqualTo(Source.LABEL)
    }

    /**
     * A label outranks what he typed (decision 9), which is only safe if what lands is the label.
     * The packet prints 0.7 to his 0.5, so the food visibly takes the packet's number — not the
     * 0.0 a 30 g row would work back to.
     */
    @Test
    fun `a packet's figure replaces a typed one with the packet's own number`() = runTest {
        val typed = PerHundredGrams(
            Nutrients(380.0, 8.0, 80.0, 0.5),
            Provenance(Source.TYPED, null, setAtMillis = 0),
        )
        val foods = FakeFoodRepository(listOf(aFood("Rice cakes", FoodFacts(per100g = typed))))

        LoggedFoods(foods).scan(rice.copy(fatPer100g = 0.7), grams = 30.0)

        val food = foods.current.single()
        assertThat(food.facts.per100g!!.nutrients).isEqualTo(Nutrients(387.4, 8.3, 81.6, 0.7))
        assertThat(food.facts.per100g!!.provenance.source).isEqualTo(Source.LABEL)
        assertThat(food.barcode).isEqualTo(rice.barcode)
    }

    /** The same barcode, its figures since changed: the later label replaces the earlier one. */
    @Test
    fun `a second scan with new figures replaces the first scan's, with its own exact figures`() = runTest {
        val foods = FakeFoodRepository()
        val logged = LoggedFoods(foods)

        logged.scan(rice, grams = 30.0)
        logged.scan(rice.copy(fatPer100g = 0.7, kcalPer100g = 391.2), grams = 30.0)

        val per100g = foods.current.single().facts.per100g!!
        assertThat(per100g.nutrients.fatG).isEqualTo(0.7)
        assertThat(per100g.nutrients.kcal).isEqualTo(391.2)
        assertThat(per100g.provenance.source).isEqualTo(Source.LABEL)
    }

    /** Another packet found by name and brand: today's rule — a label replaces a label — kept. */
    @Test
    fun `a different packet under the same name replaces the earlier packet's figures too`() = runTest {
        val foods = FakeFoodRepository()
        val logged = LoggedFoods(foods)

        logged.scan(rice, grams = 30.0)
        logged.scan(rice.copy(barcode = "2000000000028", fatPer100g = 0.7), grams = 30.0)

        val per100g = foods.current.single().facts.per100g!!
        assertThat(per100g.nutrients).isEqualTo(Nutrients(387.4, 8.3, 81.6, 0.7))
        assertThat(per100g.provenance.source).isEqualTo(Source.LABEL)
    }

    /**
     * A REGRESSION GUARD: a packet only ever knows per 100 g, so what one cake is worth and what one
     * weighs — his numbers — are exactly as they were, while the per-100 g is the label's.
     */
    @Test
    fun `a scan leaves what one is worth and what one weighs alone`() = runTest {
        val perUnit = aPerUnit("cake", 35.0)
        val weighs = weighing(9.0)
        val foods = FakeFoodRepository(
            listOf(aFood("Rice cakes", FoodFacts(perUnit = perUnit, gramsPerUnit = weighs))),
        )

        LoggedFoods(foods).scan(rice, grams = 30.0)

        val facts = foods.current.single().facts
        assertThat(facts.perUnit).isEqualTo(perUnit)
        assertThat(facts.gramsPerUnit).isEqualTo(weighs)
        assertThat(facts.per100g!!.nutrients).isEqualTo(exact)
        assertThat(facts.per100g!!.provenance.source).isEqualTo(Source.LABEL)
    }

    /** A REGRESSION GUARD: only a scan holds a label; a typed row is still its own evidence. */
    @Test
    fun `a typed row still teaches its food from the row`() = runTest {
        val foods = FakeFoodRepository()

        LoggedFoods(foods).attach(
            anItem(
                name = "Toast", portionAmount = 30.0, portionUnit = "g",
                kcal = 80, proteinG = 3, carbsG = 15, fatG = 1,
            ),
        )

        val per100g = foods.current.single().facts.per100g!!
        assertThat(per100g.nutrients.fatG).isWithin(1e-9).of(100.0 / 30.0)
        assertThat(per100g.nutrients.kcal).isWithin(1e-9).of(80 * 100.0 / 30.0)
        assertThat(per100g.provenance.source).isEqualTo(Source.TYPED)
        assertThat(per100g.provenance.confidence).isNull()
    }

    /** A REGRESSION GUARD: a described row teaches from the row, and keeps how sure it was. */
    @Test
    fun `a described row still teaches its food from the row, with how sure it was`() = runTest {
        val foods = FakeFoodRepository()

        LoggedFoods(foods).attach(
            anItem(
                name = "Toast", portionAmount = 30.0, portionUnit = "g",
                kcal = 80, proteinG = 3, carbsG = 15, fatG = 1,
                source = Source.AI_ESTIMATE, confidence = Confidence.MEDIUM,
            ),
        )

        val per100g = foods.current.single().facts.per100g!!
        assertThat(per100g.nutrients.fatG).isWithin(1e-9).of(100.0 / 30.0)
        assertThat(per100g.nutrients.kcal).isWithin(1e-9).of(80 * 100.0 / 30.0)
        assertThat(per100g.provenance.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(per100g.provenance.confidence).isEqualTo(Confidence.MEDIUM)
    }

    /**
     * A packet whose label is no quantity of food — here a fat of -0.1 g per 100 g, which Open Food
     * Facts can hand over — logs as a 0 g row but has no label figures to teach.
     *
     * Teaching the food fat 0.0 under LABEL, worked back from the whole-gram row, would be the #28
     * defect by another door, filed under the label's name (D4) — and an earlier draft of this
     * issue's plan asserted exactly that. This pins the right behaviour: a scan with no label figures
     * teaches nothing, and with no food to land on, none is made: a food must know what some amount
     * of it is worth, and nothing here says so honestly.
     *
     * Since D39 (issue #31) such a packet is refused at the door and logging refuses it too, so the
     * row stands for one logged before then — the row 0.32.1 logged, which is exactly the sound
     * packet's at 30 g (fat -0.03 and 0.15 both round to 0). What is pinned is `attach`'s
     * teach-nothing path.
     */
    @Test
    fun `a scan whose label is no quantity of food teaches nothing and makes no food`() = runTest {
        val foods = FakeFoodRepository()
        val bad = rice.copy(fatPer100g = -0.1)
        assertThat(bad.nutrientsPer100g()).isNull()
        val row = rice.toFoodItem(30.0)!!

        val attached = LoggedFoods(foods).scan(bad, grams = 30.0, item = row).item

        assertThat(attached).isEqualTo(row)
        assertThat(attached.foodId).isNull()
        assertThat(foods.current).isEmpty()
    }

    /**
     * Such a scan still lands on the food it is, and leaves his typed figures exactly as they were.
     * The row is one logged before D39, as above.
     */
    @Test
    fun `a scan with no label figures lands on a typed food and leaves its per-100 g alone`() = runTest {
        val typed = PerHundredGrams(
            Nutrients(380.0, 8.0, 80.0, 0.5),
            Provenance(Source.TYPED, null, setAtMillis = 0),
        )
        val before = aFood("Rice cakes", FoodFacts(per100g = typed))
        val foods = FakeFoodRepository(listOf(before))

        val attached = LoggedFoods(foods).scan(
            rice.copy(fatPer100g = -0.1),
            grams = 30.0,
            item = rice.toFoodItem(30.0)!!,
        ).item

        val food = foods.current.single()
        assertThat(attached.foodId).isEqualTo(food.id)
        assertThat(food.facts).isEqualTo(before.facts)
    }

    /**
     * The packet known by its barcode from an earlier, sound scan, now arriving with a broken
     * figure: it lands on that food by the barcode, and the earlier label's exact figures stand —
     * not replaced by a LABEL 0.0 worked back from the row. The rescan is one logged before D39, as
     * above.
     */
    @Test
    fun `a rescan with no label figures keeps the earlier label's exact figures`() = runTest {
        val foods = FakeFoodRepository()
        val logged = LoggedFoods(foods)
        logged.scan(rice, grams = 30.0)

        val attached = logged.scan(
            rice.copy(name = "Rice crackers", fatPer100g = -0.1),
            grams = 30.0,
            item = rice.copy(name = "Rice crackers").toFoodItem(30.0)!!,
        ).item

        val food = foods.current.single()
        assertThat(attached.foodId).isEqualTo(food.id)
        assertThat(food.barcode).isEqualTo(rice.barcode)
        assertThat(food.facts.per100g!!.nutrients).isEqualTo(exact)
        assertThat(food.facts.per100g!!.provenance.source).isEqualTo(Source.LABEL)
    }

    // --- A scanned row sent on without its packet (D43, issue #29) ------------------------------

    /**
     * What *Correct this item* sends when he renames a scanned row: the row, still LABEL and whole
     * grams, with no barcode and no label beside it. Worked back to 100 g it is 386.67 / 6.67 / 80.0
     * / 0.0 — not what the packet printed.
     */
    private fun renamedScan(name: String = "Puffed rice") = rice.toFoodItem(30.0)!!.copy(name = name)

    private val workedBack = Nutrients(116 * 100 / 30.0, 2 * 100 / 30.0, 24 * 100 / 30.0, 0.0)

    private fun assertWorkedBack(per100g: PerHundredGrams) {
        assertThat(per100g.nutrients.kcal).isWithin(1e-9).of(workedBack.kcal)
        assertThat(per100g.nutrients.proteinG).isWithin(1e-9).of(workedBack.proteinG)
        assertThat(per100g.nutrients.carbsG).isWithin(1e-9).of(workedBack.carbsG)
        assertThat(per100g.nutrients.fatG).isWithin(1e-9).of(workedBack.fatG)
    }

    /** A figure copied from a past meal: what a food holds after learning from a renamed scan. */
    private val copied = PerHundredGrams(
        Nutrients(386.0, 6.0, 80.0, 0.0),
        Provenance(Source.REPEATED, null, setAtMillis = 0),
    )

    /**
     * The row says fat 0, and the new food may learn that — but as what it is, a figure copied from
     * a meal he logged, not the packet's. The row itself is untouched: still the label's, on the day.
     */
    @Test
    fun `a scanned row sent on without its packet teaches a new food a copied figure, not the label's`() = runTest {
        val foods = FakeFoodRepository()
        val item = renamedScan()

        val attached = LoggedFoods(foods).attach(item).item

        val food = foods.current.single()
        assertThat(food.name).isEqualTo("Puffed rice")
        assertThat(attached).isEqualTo(item.copy(foodId = food.id))
        assertThat(attached.source).isEqualTo(Source.LABEL)
        assertThat(attached.fatG).isEqualTo(0)

        val per100g = food.facts.per100g!!
        assertThat(per100g.provenance.source).isEqualTo(Source.REPEATED)
        assertThat(per100g.provenance.confidence).isNull()
        assertThat(per100g.provenance.rank).isLessThan(Provenance.rankOf(Source.TYPED))
        assertWorkedBack(per100g)
        assertThat(food.barcode).isNull()
    }

    /** Before D43 the worked-back 0.0 arrived as LABEL, outranked his 0.5, and replaced it. */
    @Test
    fun `a scanned row sent on without its packet onto a food whose per-100 g he typed leaves his figures alone`() = runTest {
        val typed = PerHundredGrams(
            Nutrients(380.0, 8.0, 80.0, 0.5),
            Provenance(Source.TYPED, null, setAtMillis = 0),
        )
        val foods = FakeFoodRepository(listOf(aFood("Puffed rice", FoodFacts(per100g = typed))))

        val attached = LoggedFoods(foods).attach(renamedScan()).item

        val food = foods.current.single()
        assertThat(attached.foodId).isEqualTo(food.id)
        assertThat(food.facts.per100g).isEqualTo(typed)
    }

    /** Before D43 a worked-back LABEL tied a real LABEL and replaced it (`>=`). */
    @Test
    fun `a scanned row sent on without its packet onto a food holding a label's per-100 g leaves the label alone`() = runTest {
        val label = PerHundredGrams(exact, Provenance(Source.LABEL, null, setAtMillis = 0))
        val foods = FakeFoodRepository(listOf(aFood("Puffed rice", FoodFacts(per100g = label))))

        val attached = LoggedFoods(foods).attach(renamedScan()).item

        val food = foods.current.single()
        assertThat(attached.foodId).isEqualTo(food.id)
        assertThat(food.facts.per100g).isEqualTo(label)
    }

    /** A blank is filled — a copied figure is better than none — and his per-one is not touched. */
    @Test
    fun `a scanned row sent on without its packet onto a food with no per-100 g fills the blank`() = runTest {
        val perUnit = aPerUnit("cake", 35.0)
        val foods = FakeFoodRepository(listOf(aFood("Puffed rice", FoodFacts(perUnit = perUnit))))

        val attached = LoggedFoods(foods).attach(renamedScan()).item

        val food = foods.current.single()
        assertThat(attached.foodId).isEqualTo(food.id)
        val per100g = food.facts.per100g!!
        assertThat(per100g.provenance.source).isEqualTo(Source.REPEATED)
        assertThat(per100g.provenance.confidence).isNull()
        assertWorkedBack(per100g)
        assertThat(food.facts.perUnit).isEqualTo(perUnit)
        assertThat(food.facts.gramsPerUnit).isNull()
    }

    /**
     * The cost D43 accepts, pinned where it is paid: a copied figure ranks below a model's estimate
     * too, so the estimate stays, though the row came off a packet.
     */
    @Test
    fun `a scanned row sent on without its packet onto a food holding an estimate leaves the estimate`() = runTest {
        val estimate = PerHundredGrams(
            Nutrients(370.0, 7.0, 78.0, 2.0),
            Provenance(Source.AI_ESTIMATE, Confidence.MEDIUM, setAtMillis = 0),
        )
        val foods = FakeFoodRepository(listOf(aFood("Puffed rice", FoodFacts(per100g = estimate))))

        val attached = LoggedFoods(foods).attach(renamedScan()).item

        val food = foods.current.single()
        assertThat(attached.foodId).isEqualTo(food.id)
        assertThat(food.facts.per100g).isEqualTo(estimate)
    }

    /** Two copied figures rank the same, and the later one replaces the earlier (`>=`). */
    @Test
    fun `a second copied figure replaces an earlier copied one`() = runTest {
        val foods = FakeFoodRepository(listOf(aFood("Puffed rice", FoodFacts(per100g = copied))))

        LoggedFoods(foods).attach(renamedScan())

        val per100g = foods.current.single().facts.per100g!!
        assertThat(per100g.provenance.source).isEqualTo(Source.REPEATED)
        assertWorkedBack(per100g)
    }

    /** A copied figure is a placeholder: the packet itself, scanned, replaces it with its own. */
    @Test
    fun `a scan replaces a copied figure with the packet's own`() = runTest {
        val foods = FakeFoodRepository(listOf(aFood("Rice cakes", FoodFacts(per100g = copied))))

        LoggedFoods(foods).scan(rice, grams = 30.0)

        val per100g = foods.current.single().facts.per100g!!
        assertThat(per100g.nutrients).isEqualTo(exact)
        assertThat(per100g.provenance.source).isEqualTo(Source.LABEL)
    }

    // --- issue #13: what the attach REPORTS about the foods it taught -------------------------

    /** A food knowing 100 g of it are worth these calories, as he typed them. */
    private fun typedPer100g(kcal: Double) = PerHundredGrams(
        Nutrients(kcal, 1.0, 3.0, 0.1),
        Provenance(Source.TYPED, null, setAtMillis = 0),
    )

    /** One row weighed out at 100 g, so what it implies per 100 g is what it says. */
    private fun typedRow(name: String, kcal: Int) = anItem(
        name = name, portionAmount = 100.0, portionUnit = "g",
        kcal = kcal, proteinG = 1, carbsG = 3, fatG = 0,
    )

    private fun onlyReplacement(retaught: List<FoodRetaught>): Replaced.Per100g {
        val food = retaught.single()
        return food.replaced.single() as Replaced.Per100g
    }

    /**
     * The defect of issue #13, at the layer that can see it: logging 18 over a typed 15 still wins,
     * and now the attach hands back what the food held so the day can say so.
     */
    @Test
    fun `a row replacing a food's typed figure reports the food, what it held and what it holds`() = runTest {
        val foods = FakeFoodRepository(
            listOf(aFood("Cucumber", FoodFacts(per100g = typedPer100g(15.0)))),
        )

        val attached = LoggedFoods(foods).attach(typedRow("Cucumber", 18))

        assertThat(attached.retaught.single().foodName).isEqualTo("Cucumber")
        val replaced = onlyReplacement(attached.retaught)
        assertThat(replaced.before.nutrients.kcal).isEqualTo(15.0)
        assertThat(replaced.after.nutrients.kcal).isEqualTo(18.0)
        // The log is never blocked and the ranking never changes: the food still ends at 18.
        assertThat(foods.current.single().facts.per100g!!.nutrients.kcal).isEqualTo(18.0)
    }

    /** A name he has never used makes a food. Nothing was replaced, so nothing is reported. */
    @Test
    fun `a row whose name belongs to no food makes one and reports nothing`() = runTest {
        val foods = FakeFoodRepository()

        val attached = LoggedFoods(foods).attach(typedRow("Kohlrabi", 27))

        assertThat(attached.retaught).isEmpty()
        assertThat(foods.current.single().name).isEqualTo("Kohlrabi")
    }

    /** A repeated meal's rows already know their food, so nothing is asked and nothing is taught. */
    @Test
    fun `a row already carrying a food reports nothing`() = runTest {
        val foods = FakeFoodRepository(
            listOf(aFood("Cucumber", FoodFacts(per100g = typedPer100g(15.0)))),
        )
        val already = typedRow("Cucumber", 18).copy(foodId = 1)

        val attached = LoggedFoods(foods).attach(already)

        assertThat(attached.item).isEqualTo(already)
        assertThat(attached.retaught).isEmpty()
        assertThat(foods.current.single().facts.per100g!!.nutrients.kcal).isEqualTo(15.0)
    }

    /** The same rule with neither figure his: a newer packet replacing an older packet's is said too. */
    @Test
    fun `a scan replacing an earlier scan's figure is reported`() = runTest {
        val foods = FakeFoodRepository()
        val logged = LoggedFoods(foods)
        logged.scan(rice, grams = 30.0)

        val attached = logged.scan(rice.copy(kcalPer100g = 391.2), grams = 30.0)

        val replaced = onlyReplacement(attached.retaught)
        assertThat(attached.retaught.single().foodName).isEqualTo("Rice cakes")
        assertThat(replaced.before.nutrients.kcal).isEqualTo(387.4)
        assertThat(replaced.after.nutrients.kcal).isEqualTo(391.2)
    }

    /** A guess arriving at a figure he typed writes nothing, so there is nothing to tell him. */
    @Test
    fun `an estimate that loses the ranking reports nothing`() = runTest {
        val his = typedPer100g(15.0)
        val foods = FakeFoodRepository(listOf(aFood("Cucumber", FoodFacts(per100g = his))))

        val attached = LoggedFoods(foods).attach(
            anItem(
                name = "Cucumber", portionAmount = 100.0, portionUnit = "g",
                kcal = 18, proteinG = 1, carbsG = 3, fatG = 0,
                source = Source.AI_ESTIMATE, confidence = Confidence.MEDIUM,
            ),
        )

        assertThat(attached.retaught).isEmpty()
        assertThat(foods.current.single().facts.per100g).isEqualTo(his)
    }

    /** The teach-nothing path teaches nothing, so it can have nothing to report either. */
    @Test
    fun `a scan with no label figures reports nothing`() = runTest {
        val his = typedPer100g(380.0)
        val foods = FakeFoodRepository(listOf(aFood("Rice cakes", FoodFacts(per100g = his))))

        val attached = LoggedFoods(foods).scan(
            rice.copy(fatPer100g = -0.1),
            grams = 30.0,
            item = rice.toFoodItem(30.0)!!,
        )

        assertThat(attached.retaught).isEmpty()
        assertThat(foods.current.single().facts.per100g).isEqualTo(his)
    }

    /**
     * A described meal that names one food twice is ONE thing he did, so he is told once: what the
     * food held before the first of those rows against what it holds after the last.
     */
    @Test
    fun `one food named twice in one meal is reported once, first held against last`() = runTest {
        val foods = FakeFoodRepository(
            listOf(aFood("Milk", FoodFacts(per100g = typedPer100g(15.0)))),
        )

        val attached = LoggedFoods(foods).attach(
            listOf(typedRow("Milk", 18), typedRow("Milk", 20)),
        )

        assertThat(attached.retaught).hasSize(1)
        assertThat(attached.retaught.single().foodName).isEqualTo("Milk")
        val replaced = onlyReplacement(attached.retaught)
        assertThat(replaced.before.nutrients.kcal).isEqualTo(15.0)
        assertThat(replaced.after.nutrients.kcal).isEqualTo(20.0)
        assertThat(foods.current.single().facts.per100g!!.nutrients.kcal).isEqualTo(20.0)
    }

    /**
     * The same meal where the FIRST of the two rows made the food: it held nothing when the action
     * began, so announcing a replacement would be announcing a change to a food that did not exist
     * a second earlier. End to end over the stand-in, the case the collapse rule exists for.
     */
    @Test
    fun `one food made and then taught again inside one meal reports nothing`() = runTest {
        val foods = FakeFoodRepository()

        val attached = LoggedFoods(foods).attach(
            listOf(typedRow("Milk", 18), typedRow("Milk", 20)),
        )

        assertThat(attached.retaught).isEmpty()
        assertThat(foods.current.single().facts.per100g!!.nutrients.kcal).isEqualTo(20.0)
    }

    /** And so does anything he types: his number outranks one copied from a past meal. */
    @Test
    fun `something typed later replaces a copied figure`() = runTest {
        val foods = FakeFoodRepository(listOf(aFood("Puffed rice", FoodFacts(per100g = copied))))

        LoggedFoods(foods).attach(
            anItem(
                name = "Puffed rice", portionAmount = 100.0, portionUnit = "g",
                kcal = 380, proteinG = 8, carbsG = 80, fatG = 1,
            ),
        )

        val per100g = foods.current.single().facts.per100g!!
        assertThat(per100g.provenance.source).isEqualTo(Source.TYPED)
        assertThat(per100g.nutrients).isEqualTo(Nutrients(380.0, 8.0, 80.0, 1.0))
    }

    // --- A described item teaches its food the worth itself (D53 §3) ------------------------------

    /**
     * Butter, invented to make the rounding visible: 717.4 kcal per 100 g, 7 g of it. The row is
     * 717.4 × 0.07 = 50.218 → 50 kcal, and 50 worked back over 7 g is 714.29 per 100 g. The food
     * must learn the 717.4 it was described with.
     */
    private val butterWorth = Rate(Nutrients(717.4, 0.9, 0.1, 81.1), Per.HUNDRED)

    private fun butter(grams: String = "7") = ItemToLog(
        name = "Butter",
        detail = "",
        amountText = grams,
        unit = "g",
        worth = Worth.Estimated(butterWorth, Confidence.MEDIUM),
        foodId = null,
    )

    @Test
    fun `a described item teaches its food the worth itself`() = runTest {
        // A figure is dated when it is written, as the database dates it; this clock reads 0.
        val foods = FakeFoodRepository(now = Now { 0 })
        val described = butter()
        val row = described.toFoodItem()!!
        assertThat(row.kcal).isEqualTo(50)

        LoggedFoods(foods).attach(row, taught = described.teaches())

        val per100g = foods.current.single().facts.per100g!!
        assertThat(per100g.nutrients).isEqualTo(butterWorth.nutrients)
        assertThat(per100g.provenance)
            .isEqualTo(Provenance(Source.AI_ESTIMATE, Confidence.MEDIUM, setAtMillis = 0))
        assertThat(foods.current.single().facts.perUnit).isNull()
    }

    /** A row carrying its food is his food's, or his typing over it: it teaches that food nothing. */
    @Test
    fun `a row carrying its food teaches it nothing, whatever it was handed`() = runTest {
        val his = FoodFacts(per100g = typedPer100g(700.0))
        val foods = FakeFoodRepository(listOf(aFood("Butter", his)))
        val row = butter().toFoodItem()!!.copy(foodId = 1)

        val attached = LoggedFoods(foods).attach(row, taught = butter().teaches())

        assertThat(attached.item).isEqualTo(row)
        assertThat(attached.retaught).isEmpty()
        assertThat(foods.current.single().facts).isEqualTo(his)
    }

    /** The guarded statements still decide: a model's guess cannot replace a figure he typed. */
    @Test
    fun `an estimate still cannot replace a figure he typed`() = runTest {
        val his = FoodFacts(per100g = typedPer100g(700.0))
        val foods = FakeFoodRepository(listOf(aFood("Butter", his)))

        val attached =
            LoggedFoods(foods).attach(butter().toFoodItem()!!, taught = butter().teaches())

        assertThat(foods.current.single().facts).isEqualTo(his)
        assertThat(attached.retaught).isEmpty()
        assertThat(attached.item.foodId).isEqualTo(1)
    }

    /**
     * A row taken as one of his branded foods, but left on the estimate because that food cannot
     * cost the amount, carries the food's brand — so it lands on that food, and no unbranded food of
     * the same name is made beside it (identity is name and brand).
     */
    @Test
    fun `a row handed a brand lands on the branded food and makes no second one`() = runTest {
        val branded = aFood("Oat drink", FoodFacts(per100g = typedPer100g(45.0)))
            .copy(brand = "Acme Oats")
        val foods = FakeFoodRepository(listOf(branded))
        val row = anItem(
            name = "Oat drink", portionAmount = 1.0, portionUnit = "glass",
            kcal = 120, proteinG = 1, carbsG = 16, fatG = 5,
            source = Source.AI_ESTIMATE, confidence = Confidence.LOW,
        )

        val attached = LoggedFoods(foods).attach(listOf(ToLog(row, brand = "Acme Oats")))

        assertThat(foods.current).hasSize(1)
        assertThat(attached.item.foodId).isEqualTo(1)
        // What one glass is worth is a new fact beside his per-100 g, which is left alone.
        assertThat(foods.current.single().facts.per100g).isEqualTo(typedPer100g(45.0))
        assertThat(foods.current.single().facts.perUnit!!.unitName).isEqualTo("glass")
    }

    @Test
    fun `rows handed over for a meal each teach what they were handed`() = runTest {
        val foods = FakeFoodRepository()
        val described = butter()
        val plain = typedRow("Cucumber", 18)

        val attached = LoggedFoods(foods).attach(
            listOf(ToLog(described.toFoodItem()!!, taught = described.teaches()), ToLog(plain)),
        )

        assertThat(attached.items.map { it.name }).containsExactly("Butter", "Cucumber").inOrder()
        assertThat(foods.current.first { it.name == "Butter" }.facts.per100g!!.nutrients)
            .isEqualTo(butterWorth.nutrients)
        assertThat(foods.current.first { it.name == "Cucumber" }.facts.per100g!!.nutrients.kcal)
            .isEqualTo(18.0)
    }
}
