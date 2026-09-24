package com.metaself.app.sim

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.food.EditRefused
import com.metaself.app.data.food.EditResult
import com.metaself.app.data.food.aPer100g
import com.metaself.app.data.food.aPerUnit
import com.metaself.app.domain.day.aMeal
import com.metaself.app.domain.day.anItem
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.FoodFacts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The walk's stand-ins refuse what the database refuses, and a join moves what the real one moves
 * (public issue #6, item 4).
 *
 * Each case is `RoomFoodRepository`'s own: `delete` refuses `UsedBySavedMeals`; `correct` refuses
 * `NeededBySavedMeals`; `merge` refuses `MealsHoldingBoth`, and otherwise moves the past rows, the
 * meal parts and the names. Before this, every one of them was accepted and the join dropped the
 * meal's part in silence — so "no refusal ever appeared" in a walk proved nothing.
 *
 * Driven through [World], so the food list, the saved meals and the day are wired as a walk wires
 * them. Every food, meal and figure is invented.
 */
class StandInsRefuseAsTheDatabaseDoesTest {

    private val world = World()
    private val foods = world.foods
    private val meals = world.savedMeals

    private suspend fun food(name: String, brand: String? = null, facts: FoodFacts = FoodFacts(per100g = aPer100g())) =
        foods.findOrCreate(name, brand, facts).food.id

    private suspend fun mealHolding(name: String, vararg foodIds: Long, countedAs: CountedAs = CountedAs.GRAMS): Long {
        val id = (meals.create(name) as com.metaself.app.data.food.MealResult.Built).mealId
        foodIds.forEach { meals.put(id, it, amount = 100.0, countedAs = countedAs) }
        return id
    }

    @Test
    fun `a food a saved meal holds cannot be deleted, and the refusal names the meal`() = runTest {
        val carrot = food("Carrot")
        mealHolding("Crunchy plate", carrot)

        val result = foods.delete(carrot)

        assertThat(result).isEqualTo(EditResult.Refused(EditRefused.UsedBySavedMeals(listOf("Crunchy plate"))))
        assertThat(foods.byId(carrot)).isNotNull()
        assertThat(foods.savedMealsUsing(carrot)).containsExactly("Crunchy plate")
        assertThat(foods.observeUse(carrot).first().savedMeals).containsExactly("Crunchy plate")
    }

    @Test
    fun `the units a saved meal counts a food in cannot be emptied`() = runTest {
        val bar = food("Seed bar", facts = FoodFacts(per100g = aPer100g(), perUnit = aPerUnit()))
        mealHolding("Snack box", bar, countedAs = CountedAs.UNITS)

        val result = foods.correct(bar, FoodFacts(per100g = aPer100g()))

        assertThat(result).isEqualTo(EditResult.Refused(EditRefused.NeededBySavedMeals(listOf("Snack box"))))
        assertThat(foods.byId(bar)!!.facts.perUnit).isNotNull()
    }

    @Test
    fun `two foods one meal holds cannot be joined, and nothing moves`() = runTest {
        val carrot = food("Carrot")
        val baby = food("Baby carrot")
        mealHolding("Crunchy plate", carrot, baby)

        val result = foods.merge(winnerId = carrot, loserId = baby)

        assertThat(result).isEqualTo(EditResult.Refused(EditRefused.MealsHoldingBoth(listOf("Crunchy plate"))))
        assertThat(foods.byId(baby)).isNotNull()
        assertThat(meals.current.single().components.map { it.food.id }).containsExactly(carrot, baby)
    }

    @Test
    fun `a join keeps the meal's part, pointing at the food kept, amount and all`() = runTest {
        val carrot = food("Carrot")
        val baby = food("Baby carrot")
        mealHolding("Crunchy plate", baby)

        assertThat(foods.merge(winnerId = carrot, loserId = baby)).isEqualTo(EditResult.Done)

        val part = meals.byId(1)!!.components.single()
        assertThat(part.food.id).isEqualTo(carrot)
        assertThat(part.amount).isEqualTo(100.0)
        assertThat(foods.savedMealsUsing(carrot)).containsExactly("Crunchy plate")
    }

    @Test
    fun `a join moves the day's rows to the food kept`() = runTest {
        val carrot = food("Carrot")
        val baby = food("Baby carrot")
        world.dayMeals.log(aMeal(items = listOf(anItem(name = "Baby carrot").copy(foodId = baby))))

        foods.merge(winnerId = carrot, loserId = baby)

        assertThat(world.dayMeals.current.single().items.single().foodId).isEqualTo(carrot)
        assertThat(foods.observeUse(carrot).first().logged).isEqualTo(1)
    }

    @Test
    fun `a joined name keeps the brand it came with, so logging it again finds the food kept`() = runTest {
        val plain = food("Oat drink")
        val branded = food("Oat drink", brand = "Examplebrand")

        foods.merge(winnerId = plain, loserId = branded)
        val again = foods.findOrCreate("Oat drink", "Examplebrand", FoodFacts(per100g = aPer100g()))

        assertThat(again.wasCreated).isFalse()
        assertThat(again.food.id).isEqualTo(plain)
    }

    @Test
    fun `a deleted food's rows stay on the day, pointing at nothing`() = runTest {
        val carrot = food("Carrot")
        world.dayMeals.log(aMeal(items = listOf(anItem(name = "Carrot").copy(foodId = carrot))))

        assertThat(foods.delete(carrot)).isEqualTo(EditResult.Done)

        val row = world.dayMeals.current.single().items.single()
        assertThat(row.foodId).isNull()
        assertThat(row.name).isEqualTo("Carrot")
        assertThat(world.dayMeals.rowsWithNoFood().map { it.name }).containsExactly("Carrot")
    }

    @Test
    fun `a food that is not there cannot be put into a meal`() = runTest {
        val meal = mealHolding("Crunchy plate")

        val failure = runCatching { meals.put(meal, foodId = 99, amount = 1.0, countedAs = CountedAs.GRAMS) }

        assertThat(failure.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }
}
