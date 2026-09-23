package com.metaself.app.data.day

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.assumeSqliteRuntime
import com.metaself.app.data.food.RoomFoodRepository
import com.metaself.app.data.food.SavedMealEntity
import com.metaself.app.data.food.aPer100g
import com.metaself.app.data.time.Now
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The DAO against a REAL database, because a query that nothing executes is a query nobody has
 * checked.
 *
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4. Skipped on this aarch64 box and run for
 * real in CI; see [assumeSqliteRuntime] for why the condition is the processor and not an exception.
 */
@RunWith(RobolectricTestRunner::class)
class MealDaoTest {

    private lateinit var db: MetaSelfDatabase
    private lateinit var dao: MealDao

    @Before
    fun setUp() {
        assumeSqliteRuntime()
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MetaSelfDatabase::class.java,
        ).build()
        dao = db.mealDao()
    }

    @After
    fun tearDown() {
        if (this::db.isInitialized) db.close()
    }

    @Test
    fun `a day with nothing logged observes an empty list`() = runTest {
        assertThat(dao.observeDay(DAY).first()).isEmpty()
    }

    @Test
    fun `a meal and its items come back together`() = runTest {
        insert(name = "Chicken shawarma", kcal = 600)

        val day = dao.observeDay(DAY).first()

        assertThat(day).hasSize(1)
        assertThat(day.single().items).hasSize(1)
        assertThat(day.single().items.single().name).isEqualTo("Chicken shawarma")
    }

    @Test
    fun `another day's meals are not this day's`() = runTest {
        insert(name = "Yesterday's dinner", kcal = 700, epochDay = DAY - 1)

        assertThat(dao.observeDay(DAY).first()).isEmpty()
    }

    @Test
    fun `meals come back in the order they were logged`() = runTest {
        insert(name = "Lunch", kcal = 600, loggedAtMillis = 2_000)
        insert(name = "Breakfast", kcal = 300, loggedAtMillis = 1_000)

        val names = dao.observeDay(DAY).first().map { it.items.single().name }

        assertThat(names).containsExactly("Breakfast", "Lunch").inOrder()
    }

    @Test
    fun `deleting the only item deletes the meal with it`() = runTest {
        insert(name = "A mistake", kcal = 900)
        val itemId = dao.observeDay(DAY).first().single().items.single().id

        dao.deleteItem(itemId)

        assertThat(dao.observeDay(DAY).first()).isEmpty()
    }

    @Test
    fun `deleting one item of several leaves the meal and its other items`() = runTest {
        val mealId = dao.insertMeal(MealEntity(epochDay = DAY, loggedAtMillis = 1_000, note = null))
        dao.insertItems(
            listOf(
                itemEntity(mealId, "Pita", 250),
                itemEntity(mealId, "Hummus", 180),
            ),
        )
        val pitaId = dao.observeDay(DAY).first().single().items.first { it.name == "Pita" }.id

        dao.deleteItem(pitaId)

        val remaining = dao.observeDay(DAY).first().single().items
        assertThat(remaining.map { it.name }).containsExactly("Hummus")
    }

    @Test
    fun `an item's numbers can be corrected in place`() = runTest {
        insert(name = "Hummus", kcal = 180)
        val stored = dao.observeDay(DAY).first().single().items.single()

        dao.updateItem(stored.copy(kcal = 210, proteinG = 7))

        val updated = dao.observeDay(DAY).first().single().items.single()
        assertThat(updated.kcal).isEqualTo(210)
        assertThat(updated.proteinG).isEqualTo(7)
        assertThat(updated.id).isEqualTo(stored.id)
    }

    @Test
    fun `correcting an item leaves it on the day and in the meal it was already in`() = runTest {
        insert(name = "Hummus", kcal = 180)
        val stored = dao.observeDay(DAY).first().single().items.single()

        dao.updateItem(stored.copy(name = "Hummus, large"))

        val day = dao.observeDay(DAY).first()
        assertThat(day).hasSize(1)
        assertThat(day.single().items.single().mealId).isEqualTo(stored.mealId)
    }

    @Test
    fun `the logged days are the distinct days that hold food`() = runTest {
        insert(name = "Breakfast", kcal = 400, epochDay = DAY)
        insert(name = "Dinner", kcal = 700, epochDay = DAY)
        insert(name = "Lunch", kcal = 500, epochDay = DAY - 1)

        assertThat(dao.observeLoggedDays().first()).containsExactly(DAY, DAY - 1)
    }

    /**
     * A meal row whose items have all gone is not a day the owner logged. The app deletes the row
     * when its last item goes, but the streak must not depend on that having happened.
     */
    @Test
    fun `a day whose only meal has no items left does not count`() = runTest {
        dao.insertMeal(MealEntity(epochDay = DAY, loggedAtMillis = 1_000, note = null))

        assertThat(dao.observeLoggedDays().first()).isEmpty()
    }

    // --- issue #25: a delete hands back what it took, and restore puts exactly that back ---------

    /**
     * The snapshot is the stored row and the stored meal, every column, read with the delete.
     *
     * Every column is set to something other than its default so that a snapshot built from any
     * other source — the screen's domain form, a meal re-read after the delete — would differ here.
     */
    @Test
    fun `deleting a row hands back the row and its meal exactly as stored`() = runTest {
        val savedMealId = db.savedMealDao().insertMeal(
            SavedMealEntity(
                name = "Greek salad",
                nameKey = FoodKeys.nameKey("Greek salad"),
                createdAtMillis = 0,
                updatedAtMillis = 0,
            ),
        )
        val foodId = RoomFoodRepository(db, db.foodDao(), Now { 0 })
            .findOrCreate("Cucumber", facts = FoodFacts(per100g = aPer100g()))
            .food.id
        val mealId = dao.insertMeal(
            MealEntity(
                epochDay = DAY,
                loggedAtMillis = 1_772_020_000_000,
                note = "written down later",
                savedMealId = savedMealId,
                savedMealAdjusted = true,
            ),
        )
        dao.insertItems(
            listOf(
                itemEntity(mealId, "Cucumber", 16).copy(
                    portion = "1 cucumber",
                    portionAmount = 120.0,
                    portionUnit = "g",
                    source = "AI_ESTIMATE",
                    confidence = "MEDIUM",
                    foodId = foodId,
                ),
            ),
        )
        val stored = dao.observeDay(DAY).first().single()

        val deleted = dao.deleteItem(stored.items.single().id)

        assertThat(deleted).isEqualTo(DeletedEntry(meal = stored.meal, item = stored.items.single()))
        assertThat(dao.observeDay(DAY).first()).isEmpty()
    }

    @Test
    fun `deleting a row that is not there hands back nothing`() = runTest {
        insert(name = "Hummus", kcal = 180)
        val before = dao.observeDay(DAY).first()

        val deleted = dao.deleteItem(9_999)

        assertThat(deleted).isNull()
        assertThat(dao.observeDay(DAY).first()).isEqualTo(before)
    }

    /**
     * D4, and the reason the snapshot is a stored row rather than the domain item (plan §3.1).
     *
     * The domain form reads a source this version does not know as UNRECOGNISED. An undo that went
     * through it would write UNRECOGNISED back, and the record would stop saying what it said.
     */
    @Test
    fun `a restored row keeps a source this version cannot read, exactly as stored`() = runTest {
        val mealId = dao.insertMeal(MealEntity(epochDay = DAY, loggedAtMillis = 1_000, note = null))
        dao.insertItems(
            listOf(
                itemEntity(mealId, "Something new", 250)
                    .copy(source = "FROM_A_LATER_VERSION", confidence = "HIGH"),
            ),
        )
        val stored = dao.observeDay(DAY).first().single()

        val deleted = dao.deleteItem(stored.items.single().id)
        assertThat(deleted).isNotNull()
        dao.restore(deleted!!)

        val back = dao.observeDay(DAY).first().single()
        assertThat(back.meal).isEqualTo(stored.meal)
        assertThat(back.items.single()).isEqualTo(stored.items.single())
        assertThat(back.items.single().source).isEqualTo("FROM_A_LATER_VERSION")
        assertThat(back.items.single().confidence).isEqualTo("HIGH")
    }

    /**
     * The schema fact restore-by-original-id rests on (plan §3.2): AUTOINCREMENT never hands a
     * freed id out again, so an id in a snapshot is either free or still its own.
     */
    @Test
    fun `a deleted id is never handed out again`() = runTest {
        insert(name = "Breakfast", kcal = 300, loggedAtMillis = 1_000)
        insert(name = "Lunch", kcal = 600, loggedAtMillis = 2_000)
        val highest = dao.observeDay(DAY).first().maxBy { it.meal.id }
        val highestRow = highest.items.single()

        dao.deleteItem(highestRow.id)
        insert(name = "Dinner", kcal = 700, loggedAtMillis = 3_000)

        val dinner = dao.observeDay(DAY).first().single { it.items.single().name == "Dinner" }
        assertThat(dinner.meal.id).isGreaterThan(highest.meal.id)
        assertThat(dinner.items.single().id).isGreaterThan(highestRow.id)
    }

    private suspend fun insert(
        name: String,
        kcal: Int,
        epochDay: Long = DAY,
        loggedAtMillis: Long = 1_000,
    ) {
        val mealId = dao.insertMeal(
            MealEntity(epochDay = epochDay, loggedAtMillis = loggedAtMillis, note = null),
        )
        dao.insertItems(listOf(itemEntity(mealId, name, kcal)))
    }

    private fun itemEntity(mealId: Long, name: String, kcal: Int) = FoodItemEntity(
        mealId = mealId,
        name = name,
        portion = null,
        kcal = kcal,
        proteinG = 10,
        carbsG = 10,
        fatG = 10,
        source = "TYPED",
        confidence = null,
    )

    private companion object {
        const val DAY = 20_699L
    }
}
