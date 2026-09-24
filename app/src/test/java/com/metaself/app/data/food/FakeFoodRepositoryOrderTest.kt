package com.metaself.app.data.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.day.InMemoryMealRepository
import com.metaself.app.domain.day.aMeal
import com.metaself.app.domain.day.anItem
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.Provenance
import com.metaself.app.domain.day.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The food stand-in orders and stamps as `FoodDao` does (public issue #6, item 3).
 *
 * The real statements: `observeAll` is `ORDER BY updatedAtMillis DESC, id DESC`; `observeOffered`
 * orders by the later of `updatedAtMillis` and the food's latest logging, then `id DESC`; each write
 * the repository makes stamps `updatedAtMillis`, except the repair of impossible figures. Before this,
 * the stand-in returned foods in the order they were made and stamped nothing, so anything a walk said
 * about the top of a list was meaningless.
 *
 * Every food and figure is invented.
 */
class FakeFoodRepositoryOrderTest {

    private fun names(foods: List<com.metaself.app.domain.food.Food>) = foods.map { it.name }

    @Test
    fun `foods stamped alike come newest id first, as the database breaks the tie`() = runTest {
        val foods = FakeFoodRepository(listOf(aFood("Apple"), aFood("Barley"), aFood("Celery")))

        assertThat(names(foods.observeAll().first())).containsExactly("Celery", "Barley", "Apple").inOrder()
        assertThat(names(foods.observeOffered().first())).containsExactly("Celery", "Barley", "Apple").inOrder()
    }

    @Test
    fun `a food just made is stamped, and comes first`() = runTest {
        val foods = FakeFoodRepository(listOf(aFood("Apple", updatedAtMillis = 5)), now = CountingClock(10))

        val made = foods.findOrCreate("Barley", facts = FoodFacts(per100g = aPer100g())).food

        assertThat(made.updatedAtMillis).isEqualTo(10)
        assertThat(made.createdAtMillis).isEqualTo(10)
        assertThat(names(foods.observeAll().first())).containsExactly("Barley", "Apple").inOrder()
    }

    @Test
    fun `a rename, a hide and a Save each bring a food to the top`() = runTest {
        val foods = FakeFoodRepository(listOf(aFood("Apple"), aFood("Barley"), aFood("Celery")))

        foods.rename(foodId = 1, newName = "Green apple")
        assertThat(names(foods.observeAll().first()).first()).isEqualTo("Green apple")

        foods.hide(foodId = 2)
        assertThat(names(foods.observeAll().first()).first()).isEqualTo("Barley")

        foods.saveForm(foodId = 3, name = "Celery", brand = null, facts = FoodFacts(per100g = aPer100g(kcal = 20.0)))
        assertThat(names(foods.observeAll().first()).first()).isEqualTo("Celery")
    }

    @Test
    fun `a correction that changes nothing stamps nothing`() = runTest {
        // All three groups held and offered unchanged: no statement runs. A group left out would be
        // CLEARED, which is a write, and stamps.
        val all = FoodFacts(per100g = aPer100g(), perUnit = aPerUnit(), gramsPerUnit = weighing())
        val foods = FakeFoodRepository(listOf(aFood("Apple", facts = all, updatedAtMillis = 7)))

        foods.correct(foodId = 1, facts = all)

        assertThat(foods.byId(1)!!.updatedAtMillis).isEqualTo(7)
    }

    @Test
    fun `a correction leaving a group out clears it, and that stamps`() = runTest {
        val foods = FakeFoodRepository(listOf(aFood("Apple", updatedAtMillis = 7)), now = CountingClock(30))

        foods.correct(foodId = 1, facts = FoodFacts(per100g = aPer100g()))

        assertThat(foods.byId(1)!!.updatedAtMillis).isEqualTo(30)
    }

    @Test
    fun `a figure that lands is dated when it was written, as the statement dates it`() = runTest {
        val foods = FakeFoodRepository(now = CountingClock(40))

        val made = foods.findOrCreate("Apple", facts = FoodFacts(per100g = aPer100g())).food

        assertThat(made.facts.per100g!!.provenance.setAtMillis).isEqualTo(40)
    }

    @Test
    fun `a figure that does not land stamps nothing`() = runTest {
        val labelled = PerHundredGrams(Nutrients(50.0, 1.0, 1.0, 1.0), Provenance(Source.LABEL, null, 0))
        val foods = FakeFoodRepository(listOf(aFood("Apple", facts = FoodFacts(per100g = labelled), updatedAtMillis = 3)))

        foods.offerFacts(foodId = 1, facts = FoodFacts(per100g = aPer100g()))

        assertThat(foods.byId(1)!!.updatedAtMillis).isEqualTo(3)
        assertThat(foods.byId(1)!!.facts.per100g).isEqualTo(labelled)
    }

    @Test
    fun `repairing an impossible figure does not move a food up the list`() = runTest {
        val impossible = PerHundredGrams(Nutrients(Double.POSITIVE_INFINITY, 1.0, 1.0, 1.0), Provenance(Source.TYPED, null, 0))
        val foods = FakeFoodRepository(
            listOf(
                aFood("Apple", facts = FoodFacts(per100g = impossible, perUnit = aPerUnit()), updatedAtMillis = 1),
                aFood("Barley", updatedAtMillis = 2),
            ),
        )

        assertThat(foods.clearImpossibleFigures()).isEqualTo(1)

        assertThat(names(foods.observeAll().first())).containsExactly("Barley", "Apple").inOrder()
    }

    @Test
    fun `a logging brings a food to the top of what is offered, and not of the whole list`() = runTest {
        val foods = FakeFoodRepository(listOf(aFood("Apple", updatedAtMillis = 1), aFood("Barley", updatedAtMillis = 2)))
        val day = InMemoryMealRepository()
        foods.linkedTo(rows = day)

        day.log(aMeal(loggedAtMillis = 100, items = listOf(anItem(name = "Apple").copy(foodId = 1))))

        assertThat(names(foods.observeOffered().first())).containsExactly("Apple", "Barley").inOrder()
        assertThat(names(foods.observeAll().first())).containsExactly("Barley", "Apple").inOrder()
    }

    @Test
    fun `an edit after the last logging wins over it`() = runTest {
        val foods = FakeFoodRepository(
            listOf(aFood("Apple", updatedAtMillis = 1), aFood("Barley", updatedAtMillis = 2)),
            now = CountingClock(500),
        )
        val day = InMemoryMealRepository()
        foods.linkedTo(rows = day)
        day.log(aMeal(loggedAtMillis = 100, items = listOf(anItem(name = "Apple").copy(foodId = 1))))

        foods.rename(foodId = 2, newName = "Pearl barley")

        assertThat(names(foods.observeOffered().first())).containsExactly("Pearl barley", "Apple").inOrder()
    }

    @Test
    fun `the portion-only list is newest edit first`() = runTest {
        val portion = FoodFacts(perUnit = aPerUnit(unitName = FoodFacts.PORTION))
        val foods = FakeFoodRepository(
            listOf(aFood("Apple", facts = portion, updatedAtMillis = 1), aFood("Barley", facts = portion, updatedAtMillis = 2)),
        )

        assertThat(names(foods.observeOnlyAPortion().first())).containsExactly("Barley", "Apple").inOrder()
    }
}
