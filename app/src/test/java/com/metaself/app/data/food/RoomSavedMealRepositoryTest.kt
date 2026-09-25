package com.metaself.app.data.food

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.assumeSqliteRuntime
import com.metaself.app.data.day.MetaSelfDatabase
import com.metaself.app.data.time.Now
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.amount.teaches
import com.metaself.app.domain.amount.Worth
import com.metaself.app.domain.amount.Rate
import com.metaself.app.domain.amount.Per
import com.metaself.app.domain.amount.ItemToLog
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.PerUnit
import com.metaself.app.domain.food.Provenance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Meals the owner built, against a real database.
 *
 * What is being protected here is the referential integrity the schema is supposed to provide, which
 * only a real database can demonstrate: **a food a meal uses cannot be deleted out from under it**,
 * deleting a meal takes its parts list and nothing else, and the same food cannot go into one meal
 * twice.
 *
 * Needs a native SQLite runtime, so it stands aside on the development box and runs in CI.
 */
@RunWith(RobolectricTestRunner::class)
class RoomSavedMealRepositoryTest {

    private lateinit var database: MetaSelfDatabase
    private lateinit var meals: SavedMealRepository
    private lateinit var foods: FoodRepository

    @Before
    fun setUp() {
        assumeSqliteRuntime()
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MetaSelfDatabase::class.java,
        ).allowMainThreadQueries().build()
        val now = Now { 1_000 }
        foods = RoomFoodRepository(database, database.foodDao(), now)
        meals = RoomSavedMealRepository(database, database.savedMealDao(), database.foodDao(), now)
    }

    @After
    fun tearDown() {
        if (this::database.isInitialized) database.close()
    }

    private suspend fun cucumber(): Food = foods.findOrCreate(
        "Cucumber",
        facts = FoodFacts(
            per100g = PerHundredGrams(
                Nutrients(16.0, 0.7, 3.6, 0.1),
                Provenance(Source.TYPED, null, 1_000),
            ),
        ),
    ).food

    private suspend fun oil(): Food = foods.findOrCreate(
        "Olive oil",
        facts = FoodFacts(
            perUnit = PerUnit(
                "spoon",
                Nutrients(119.0, 0.0, 0.0, 13.5),
                Provenance(Source.TYPED, null, 1_000),
            ),
        ),
    ).food

    private suspend fun aSalad(): Long =
        (meals.create("Vegetable salad") as MealResult.Built).mealId

    // --- D54 §12.6: the meals a unit rename changes ----------------------------------------------

    /** Hidden meals count: a rename changes what their "2" means too. Grams are not affected. */
    @Test
    fun `the meals counting a food in units are counted, hidden ones included`() = runTest {
        val oil = oil()
        val cucumber = cucumber()
        val salad = aSalad()
        meals.put(salad, oil.id, 1.0, CountedAs.UNITS)
        meals.put(salad, cucumber.id, 80.0, CountedAs.GRAMS)
        val dressing = (meals.create("Dressing") as MealResult.Built).mealId
        meals.put(dressing, oil.id, 2.0, CountedAs.UNITS)
        meals.hide(dressing)

        assertThat(meals.countingInUnits(oil.id)).isEqualTo(2)
        assertThat(meals.countingInUnits(cucumber.id)).isEqualTo(0)
    }

    // --- Building ------------------------------------------------------------------------------------

    @Test
    fun `a meal starts with nothing in it, which is a meal he has not finished`() = runTest {
        val id = aSalad()

        val meal = meals.byId(id)!!
        assertThat(meal.name).isEqualTo("Vegetable salad")
        assertThat(meal.isEmpty).isTrue()
    }

    /** No draft state: a half-built meal is offered like any other, and logs what is in it. */
    @Test
    fun `a half-built meal is offered like a finished one`() = runTest {
        aSalad()

        assertThat(meals.observeOffered().first()).hasSize(1)
    }

    @Test
    fun `two meals cannot share a name`() = runTest {
        aSalad()

        assertThat(meals.create("vegetable  salad")).isInstanceOf(MealResult.NameTaken::class.java)
    }

    // --- Made in one change --------------------------------------------------------------------------

    @Test
    fun `a meal made with its parts in one change keeps them`() = runTest {
        val cucumber = cucumber()

        val made = meals.createThen("Vegetable salad") { id ->
            meals.put(id, cucumber.id, 100.0, CountedAs.GRAMS)
        }

        val meal = meals.byId((made as MealResult.Built).mealId)!!
        assertThat(meal.components.map { it.food.id }).containsExactly(cucumber.id)
    }

    /** The whole point of the one change: a failure part-way leaves no meal and no parts behind. */
    @Test
    fun `a failure after the meal is made takes the meal back out`() = runTest {
        val cucumber = cucumber()

        val thrown = runCatching {
            meals.createThen("Vegetable salad") { id ->
                meals.put(id, cucumber.id, 100.0, CountedAs.GRAMS)
                throw IllegalStateException("disk full")
            }
        }.exceptionOrNull()

        assertThat(thrown).hasMessageThat().isEqualTo("disk full")
        assertThat(meals.observeOffered().first()).isEmpty()
        // The name is free again, which it would not be if the meal had stayed.
        assertThat(meals.create("Vegetable salad")).isInstanceOf(MealResult.Built::class.java)
    }

    @Test
    fun `a name already taken runs nothing and makes nothing`() = runTest {
        aSalad()
        var ran = false

        val made = meals.createThen("vegetable salad") { ran = true }

        assertThat(made).isInstanceOf(MealResult.NameTaken::class.java)
        assertThat(ran).isFalse()
        assertThat(meals.observeOffered().first()).hasSize(1)
    }

    @Test
    fun `foods go in with amounts and come back in the order he arranged them`() = runTest {
        val id = aSalad()
        meals.put(id, cucumber().id, 100.0, CountedAs.GRAMS)
        meals.put(id, oil().id, 1.0, CountedAs.UNITS)

        val meal = meals.byId(id)!!

        assertThat(meal.components.map { it.food.name })
            .containsExactly("Cucumber", "Olive oil").inOrder()
        assertThat(meal.kcal).isEqualTo(135)
    }

    /** The same food twice in one meal is an editing accident rather than something he meant. */
    @Test
    fun `putting the same food in twice changes the amount instead`() = runTest {
        val id = aSalad()
        val cucumber = cucumber()
        meals.put(id, cucumber.id, 100.0, CountedAs.GRAMS)

        meals.put(id, cucumber.id, 200.0, CountedAs.GRAMS)

        val meal = meals.byId(id)!!
        assertThat(meal.components).hasSize(1)
        assertThat(meal.components.single().amount).isEqualTo(200.0)
    }

    @Test
    fun `reordering keeps his order`() = runTest {
        val id = aSalad()
        meals.put(id, cucumber().id, 100.0, CountedAs.GRAMS)
        meals.put(id, oil().id, 1.0, CountedAs.UNITS)
        val components = meals.byId(id)!!.components

        meals.reorder(id, components.map { it.id }.reversed())

        assertThat(meals.byId(id)!!.components.map { it.food.name })
            .containsExactly("Olive oil", "Cucumber").inOrder()
    }

    @Test
    fun `something taken out leaves the rest`() = runTest {
        val id = aSalad()
        meals.put(id, cucumber().id, 100.0, CountedAs.GRAMS)
        meals.put(id, oil().id, 1.0, CountedAs.UNITS)

        meals.remove(meals.byId(id)!!.components.first().id)

        assertThat(meals.byId(id)!!.components.map { it.food.name }).containsExactly("Olive oil")
    }

    // --- Renaming ------------------------------------------------------------------------------------

    @Test
    fun `renaming keeps everything in it`() = runTest {
        val id = aSalad()
        meals.put(id, cucumber().id, 100.0, CountedAs.GRAMS)

        assertThat(meals.rename(id, "Big salad")).isEqualTo(MealResult.Done)

        val meal = meals.byId(id)!!
        assertThat(meal.name).isEqualTo("Big salad")
        assertThat(meal.components).hasSize(1)
    }

    @Test
    fun `renaming onto another meal's name is refused`() = runTest {
        aSalad()
        val other = (meals.create("Breakfast") as MealResult.Built).mealId

        assertThat(meals.rename(other, "Vegetable salad"))
            .isInstanceOf(MealResult.NameTaken::class.java)
    }

    // --- What the schema protects ----------------------------------------------------------------------

    /**
     * **The rule only a real database can demonstrate.** A food a saved meal uses cannot be deleted
     * out from under it: the attempt is refused, by name, so the owner can change the meal or hide
     * the food instead.
     */
    @Test
    fun `a food a meal uses cannot be deleted`() = runTest {
        val id = aSalad()
        val cucumber = cucumber()
        meals.put(id, cucumber.id, 100.0, CountedAs.GRAMS)

        val result = foods.delete(cucumber.id)

        assertThat(result).isInstanceOf(EditResult.Refused::class.java)
        val why = (result as EditResult.Refused).why
        assertThat(why).isInstanceOf(EditRefused.UsedBySavedMeals::class.java)
        assertThat((why as EditRefused.UsedBySavedMeals).meals).containsExactly("Vegetable salad")
        assertThat(foods.byId(cucumber.id)).isNotNull()
    }

    /**
     * The read the delete question is decided by (D36): asked BEFORE offering to delete, so the owner
     * is never asked a question whose only answer is a refusal. Asking deletes nothing.
     */
    @Test
    fun `the meals using a food are named, and nothing is deleted`() = runTest {
        val id = aSalad()
        val cucumber = cucumber()
        meals.put(id, cucumber.id, 100.0, CountedAs.GRAMS)

        assertThat(foods.savedMealsUsing(cucumber.id)).containsExactly("Vegetable salad")
        assertThat(foods.byId(cucumber.id)).isNotNull()
    }

    @Test
    fun `a food no meal uses is named by none`() = runTest {
        aSalad()
        val oil = oil()

        assertThat(foods.savedMealsUsing(oil.id)).isEmpty()
    }

    /** Hiding is the answer the refusal offers, and it leaves the meal working. */
    @Test
    fun `a food a meal uses can be hidden instead`() = runTest {
        val id = aSalad()
        val cucumber = cucumber()
        meals.put(id, cucumber.id, 100.0, CountedAs.GRAMS)

        foods.hide(cucumber.id)

        assertThat(meals.byId(id)!!.components).hasSize(1)
        assertThat(meals.byId(id)!!.kcal).isEqualTo(16)
    }

    /**
     * Emptying a group a meal counts a food in is refused by name — a visible refusal at the moment
     * he does it, rather than a silent hundredfold error discovered a month later.
     */
    @Test
    fun `emptying a group a meal depends on is refused by name`() = runTest {
        val id = aSalad()
        val oil = oil()
        meals.put(id, oil.id, 1.0, CountedAs.UNITS)

        val result = foods.correct(
            oil.id,
            FoodFacts(
                per100g = PerHundredGrams(
                    Nutrients(884.0, 0.0, 0.0, 100.0),
                    Provenance(Source.TYPED, null, 1_000),
                ),
            ),
        )

        assertThat(result).isInstanceOf(EditResult.Refused::class.java)
        assertThat((result as EditResult.Refused).why)
            .isInstanceOf(EditRefused.NeededBySavedMeals::class.java)
    }

    @Test
    fun `deleting a meal takes its parts list and no food`() = runTest {
        val id = aSalad()
        val cucumber = cucumber()
        meals.put(id, cucumber.id, 100.0, CountedAs.GRAMS)

        meals.delete(id)

        assertThat(meals.byId(id)).isNull()
        assertThat(foods.byId(cucumber.id)).isNotNull()
    }

    @Test
    fun `a hidden meal leaves the list and stays findable`() = runTest {
        val id = aSalad()

        meals.hide(id)

        assertThat(meals.observeOffered().first()).isEmpty()
        assertThat(meals.byId(id)).isNotNull()

        meals.unhide(id)
        assertThat(meals.observeOffered().first()).hasSize(1)
    }

    // --- Keep as a meal, logging nothing (D58 §5.2, §12.7) ------------------------------------------
    // Invented meal and figures throughout.

    private fun keeper(foodsFor: FoodRepository = foods) =
        RoomMealKeeper(database, LoggedFoods(foodsFor), foodsFor, meals)

    /** A described row as the proposal hands it over: the model's worth at the model's amount. */
    private fun described(name: String, amount: String, unit: String, rate: Rate): ToLog {
        val item = ItemToLog(
            name = name, detail = "", amountText = amount, unit = unit,
            worth = Worth.Estimated(rate, Confidence.MEDIUM), foodId = null,
        )
        return ToLog(item.toFoodItem()!!, taught = item.teaches())
    }

    private val pasta = described("Pasta", "350", "g", Rate(Nutrients(220.0, 6.0, 26.0, 10.0), Per.HUNDRED))
    private val roll = described("Bread roll", "1", "roll", Rate(Nutrients(150.0, 5.0, 28.0, 2.0), Per.ONE))

    @Test
    fun `kept as a meal, the foods are made and the parts go in, and nothing is logged`() = runTest {
        val kept = keeper().keep("Counter lunch", listOf(pasta, roll))

        val meal = meals.byId((kept as MealKeeper.Kept.Made).mealId)!!
        assertThat(meal.name).isEqualTo("Counter lunch")
        assertThat(meal.components.map { it.food.name }).containsExactly("Pasta", "Bread roll").inOrder()
        assertThat(meal.components.map { it.amount }).containsExactly(350.0, 1.0).inOrder()
        val logged = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM food_items")
        logged.use { it.moveToFirst(); assertThat(it.getInt(0)).isEqualTo(0) }
    }

    @Test
    fun `a name taken leaves no new food behind`() = runTest {
        meals.create("Counter lunch")

        val kept = keeper().keep("Counter lunch", listOf(pasta, roll))

        assertThat(kept).isEqualTo(MealKeeper.Kept.NameTaken("Counter lunch"))
        assertThat(foods.observeAll().first()).isEmpty()
    }

    /** Re-taught and then refused: the food he had goes back to what it held. */
    @Test
    fun `a refusal after a food was re-taught puts the food's facts back`() = runTest {
        val before = PerHundredGrams(Nutrients(200.0, 5.0, 30.0, 6.0), Provenance(Source.AI_ESTIMATE, null, 1_000))
        val his = foods.findOrCreate("Pasta", facts = FoodFacts(per100g = before)).food
        // Taught when kept with nothing in the way — so the refusal below has something to undo.
        keeper().keep("First lunch", listOf(pasta))
        assertThat(foods.byId(his.id)!!.facts.per100g!!.nutrients.kcal).isEqualTo(220.0)
        val taughtAgain = described("Pasta", "300", "g", Rate(Nutrients(240.0, 6.0, 28.0, 12.0), Per.HUNDRED))

        val kept = keeper().keep("First lunch", listOf(taughtAgain))

        assertThat(kept).isEqualTo(MealKeeper.Kept.NameTaken("First lunch"))
        assertThat(foods.byId(his.id)!!.facts.per100g!!.nutrients.kcal).isEqualTo(220.0)
    }

    @Test
    fun `a part that cannot join is refused, in words that say nothing was logged, and nothing is kept`() =
        runTest {
            foods.findOrCreate(
                "Bread roll",
                // His own, counted in slices, typed: a described roll cannot re-teach it (D53 §5).
                facts = FoodFacts(perUnit = PerUnit("slice", Nutrients(80.0, 3.0, 15.0, 1.0), Provenance(Source.TYPED, null, 1_000))),
            )
            val countBefore = foods.observeAll().first().size

            val kept = keeper().keep("Counter lunch", listOf(pasta, roll)) as MealKeeper.Kept.Refused

            assertThat(kept.why.single()).startsWith("Bread roll is in roll")
            assertThat(kept.why.single()).doesNotContain("logged")
            assertThat(foods.observeAll().first()).hasSize(countBefore)
            assertThat(meals.observeOffered().first()).isEmpty()
        }

    @Test
    fun `a storage failure while making a food rolls everything back and is let out`() = runTest {
        val failing = object : FoodRepository by foods {
            var calls = 0
            override suspend fun findOrCreate(
                name: String,
                brand: String?,
                facts: FoodFacts,
                barcode: String?,
            ): FoundOrCreated {
                if (++calls == 2) throw android.database.sqlite.SQLiteException("disk full")
                return foods.findOrCreate(name, brand, facts, barcode)
            }
        }

        val thrown = runCatching { keeper(failing).keep("Counter lunch", listOf(pasta, roll)) }

        assertThat(thrown.exceptionOrNull()).isInstanceOf(android.database.sqlite.SQLiteException::class.java)
        assertThat(foods.observeAll().first()).isEmpty()
        assertThat(meals.observeOffered().first()).isEmpty()
    }
}
