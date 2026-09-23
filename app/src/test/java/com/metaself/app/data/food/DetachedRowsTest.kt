package com.metaself.app.data.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.day.InMemoryMealRepository
import com.metaself.app.domain.day.aMeal
import com.metaself.app.domain.day.anItem
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Repairing the rows that corrections detached before issue #22 was fixed.
 *
 * Conservative on purpose. A row is put back only when exactly one food answers to its name, and no
 * food is ever made: a row whose food was deleted on purpose stays as it is, and a row whose name two
 * foods share — an unbranded milk and a Dairyco one — is not guessed at.
 */
class DetachedRowsTest {

    @Test
    fun `a detached row whose food still exists is put back`() = runTest {
        val foods = FakeFoodRepository(listOf(aFood(name = "Cucumber")))
        val meals = InMemoryMealRepository(
            listOf(aMeal(id = 1, items = listOf(anItem(id = 10, name = "Cucumber")))),
        )

        val put = DetachedRows.reattach(meals, foods)

        assertThat(put).isEqualTo(1)
        assertThat(meals.current.single().items.single().foodId)
            .isEqualTo(foods.current.single().id)
    }

    /** Put back under any of the food's names, the way a joined food answers to both. */
    @Test
    fun `a detached row is put back under a name its food also answers to`() = runTest {
        val foods = FakeFoodRepository(listOf(aFood(name = "Cucumber"), aFood(name = "Cucumbers")))
        foods.merge(winnerId = foods.current[0].id, loserId = foods.current[1].id)
        val meals = InMemoryMealRepository(
            listOf(aMeal(id = 1, items = listOf(anItem(id = 10, name = "cucumbers")))),
        )

        DetachedRows.reattach(meals, foods)

        assertThat(meals.current.single().items.single().foodId)
            .isEqualTo(foods.current.single().id)
    }

    /** Deleting a food detaches its rows by design, and nothing here brings the food back. */
    @Test
    fun `a row whose food was deleted stays detached, and no food is made`() = runTest {
        val foods = FakeFoodRepository()
        val meals = InMemoryMealRepository(
            listOf(aMeal(id = 1, items = listOf(anItem(id = 10, name = "Halva")))),
        )

        val put = DetachedRows.reattach(meals, foods)

        assertThat(put).isEqualTo(0)
        assertThat(meals.current.single().items.single().foodId).isNull()
        assertThat(foods.current).isEmpty()
    }

    /** Two foods share the name, so which one the row was cannot be known, and it is left. */
    @Test
    fun `a row whose name two foods share is not guessed at`() = runTest {
        val foods = FakeFoodRepository(listOf(aFood(name = "Milk")))
        foods.setBrand(foods.current.single().id, "Dairyco")
        foods.findOrCreate(name = "Milk", brand = null, facts = aFood().facts)
        val meals = InMemoryMealRepository(
            listOf(aMeal(id = 1, items = listOf(anItem(id = 10, name = "Milk")))),
        )

        val put = DetachedRows.reattach(meals, foods)

        assertThat(foods.current).hasSize(2)
        assertThat(put).isEqualTo(0)
        assertThat(meals.current.single().items.single().foodId).isNull()
    }

    /** A row already attached is not touched, whatever its name matches. */
    @Test
    fun `an attached row keeps its food`() = runTest {
        val foods = FakeFoodRepository(listOf(aFood(name = "Cucumber"), aFood(name = "Feta")))
        val feta = foods.current.single { it.name == "Feta" }.id
        val meals = InMemoryMealRepository(
            listOf(
                aMeal(id = 1, items = listOf(anItem(id = 10, name = "Cucumber").copy(foodId = feta))),
            ),
        )

        val put = DetachedRows.reattach(meals, foods)

        assertThat(put).isEqualTo(0)
        assertThat(meals.current.single().items.single().foodId).isEqualTo(feta)
    }
}
