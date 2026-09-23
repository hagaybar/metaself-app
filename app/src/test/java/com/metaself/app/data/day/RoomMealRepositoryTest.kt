package com.metaself.app.data.day

import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.data.time.Now
import com.metaself.app.data.food.aPer100g
import com.metaself.app.data.food.RoomFoodRepository
import com.metaself.app.data.food.EditResult
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.assumeSqliteRuntime
import com.metaself.app.data.food.SavedMealEntity
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.food.FoodKeys
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.day.aMeal
import com.metaself.app.domain.day.anItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The repository over a real database. JUnit 4 by necessity; skipped on aarch64, run in CI.
 */
@RunWith(RobolectricTestRunner::class)
class RoomMealRepositoryTest {

    private lateinit var db: MetaSelfDatabase
    private lateinit var repository: RoomMealRepository

    @Before
    fun setUp() {
        assumeSqliteRuntime()
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MetaSelfDatabase::class.java,
        ).build()
        repository = RoomMealRepository(db.mealDao())
    }

    @After
    fun tearDown() {
        if (this::db.isInitialized) db.close()
    }

    @Test
    fun `a logged meal appears on its day`() = runTest {
        repository.log(aMeal(epochDay = DAY, items = listOf(anItem(name = "Hummus"))))

        val day = repository.observeDay(DAY).first()

        assertThat(day.single().items.single().name).isEqualTo("Hummus")
    }

    @Test
    fun `a logged meal keeps where its numbers came from`() = runTest {
        repository.log(aMeal(epochDay = DAY, items = listOf(anItem(source = Source.TYPED))))

        val item = repository.observeDay(DAY).first().single().items.single()

        assertThat(item.source).isEqualTo(Source.TYPED)
        assertThat(item.confidence).isNull()
    }

    @Test
    fun `deleting the only item empties the day`() = runTest {
        repository.log(aMeal(epochDay = DAY, items = listOf(anItem())))
        val itemId = repository.observeDay(DAY).first().single().items.single().id

        repository.deleteItem(itemId)

        assertThat(repository.observeDay(DAY).first()).isEmpty()
    }

    /**
     * Setting when a meal was eaten moves the whole meal, and only its time (D33).
     *
     * Runs in CI only, like every test here: Robolectric's SQLite has no aarch64 build. That matters
     * more for this than for most, because it is the one statement that writes a meal's time, and
     * nothing on the owner's box can execute it.
     */
    /**
     * Rows attached to no food are listed, and one can be attached — only while still detached.
     *
     * The two statements the repair of issue #22 uses. Runs in CI only.
     */
    @Test
    fun `a detached row is listed and can be attached, once`() = runTest {
        repository.log(aMeal(epochDay = DAY, items = listOf(anItem(name = "Cucumber"))))
        val row = repository.rowsWithNoFood().single()
        assertThat(row.name).isEqualTo("Cucumber")

        val foodId = RoomFoodRepository(db, db.foodDao(), Now { 0 })
            .findOrCreate("Cucumber", facts = FoodFacts(per100g = aPer100g()))
            .food.id
        repository.attachRow(row.itemId, foodId)

        assertThat(repository.rowsWithNoFood()).isEmpty()
        assertThat(repository.observeDay(DAY).first().single().items.single().foodId)
            .isEqualTo(foodId)
    }

    @Test
    fun `setting when a meal was eaten moves the whole meal and nothing else`() = runTest {
        repository.log(
            aMeal(
                epochDay = DAY,
                loggedAtMillis = 1_000L,
                items = listOf(anItem(name = "Cucumber"), anItem(name = "Feta")),
            ),
        )
        val stored = repository.observeDay(DAY).first().single()

        repository.setEatenAt(stored.id, atMillis = 5_000L)

        val moved = repository.observeDay(DAY).first().single()
        assertThat(moved.id).isEqualTo(stored.id)
        assertThat(moved.epochDay).isEqualTo(DAY)
        assertThat(moved.loggedAtMillis).isEqualTo(5_000L)
        assertThat(moved.items.map { it.name }).containsExactly("Cucumber", "Feta").inOrder()
        assertThat(moved.items.map { it.kcal }).isEqualTo(stored.items.map { it.kcal })
    }

    @Test
    fun `a corrected item keeps its identity and its origin`() = runTest {
        repository.log(
            aMeal(
                epochDay = DAY,
                items = listOf(anItem(source = Source.AI_ESTIMATE, confidence = Confidence.LOW)),
            ),
        )
        val stored = repository.observeDay(DAY).first().single().items.single()

        repository.updateItem(stored.copy(kcal = stored.kcal + 50))

        val updated = repository.observeDay(DAY).first().single().items.single()
        assertThat(updated.id).isEqualTo(stored.id)
        assertThat(updated.kcal).isEqualTo(stored.kcal + 50)
        assertThat(updated.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(updated.confidence).isEqualTo(Confidence.LOW)
    }

    /**
     * Rows already logged, gathered under a meal he has just named (design §3.5).
     *
     * The one irreversible act in this feature, and the reason it is here rather than against a fake:
     * it moves rows between meals and deletes a meal left empty, and **it must not touch a single
     * stored number while doing it.** Grouping a day's rows is a labelling act — the day gains a
     * title, the parts stay exactly as they were logged — so the strongest assertion in this test is
     * the one that compares every item with itself.
     */
    @Test
    fun `rows already logged can be gathered under a meal, changing not one number`() = runTest {
        // Said here as well as in setUp, so what this test needs is visible where it is read.
        assumeSqliteRuntime()

        repository.log(
            aMeal(
                epochDay = DAY,
                loggedAtMillis = at(hour = 9),
                items = listOf(
                    anItem(
                        name = "Eggs",
                        kcal = 155,
                        portion = "2 eggs",
                        portionAmount = 2.0,
                        portionUnit = "egg",
                    ),
                    anItem(
                        name = "Tomato",
                        kcal = 27,
                        portion = "150 g",
                        portionAmount = 150.0,
                        portionUnit = "g",
                    ),
                ),
            ),
        )
        repository.log(
            aMeal(
                epochDay = DAY,
                loggedAtMillis = at(hour = 13),
                items = listOf(
                    anItem(
                        name = "Coffee with milk",
                        kcal = 60,
                        portion = "1 cup",
                        portionAmount = 1.0,
                        portionUnit = "cup",
                        source = Source.AI_ESTIMATE,
                        confidence = Confidence.MEDIUM,
                    ),
                ),
            ),
        )

        val before = repository.observeDay(DAY).first()
        val itemsBefore = before.flatMap { it.items }
        val byName = itemsBefore.associateBy { it.name }
        val eggs = byName.getValue("Eggs")
        val coffee = byName.getValue("Coffee with milk")
        val tomatoMealId = before.single { meal -> meal.items.any { it.name == "Tomato" } }.id

        val savedMealId = savedMealCalled("Shakshuka breakfast")

        repository.gatherIntoSavedMeal(
            epochDay = DAY,
            itemIds = listOf(eggs.id, coffee.id),
            savedMealId = savedMealId,
        )

        val after = repository.observeDay(DAY).first()

        // The day still holds the same three things eaten.
        assertThat(after.flatMap { it.items }.map { it.name })
            .containsExactly("Eggs", "Tomato", "Coffee with milk")

        // The two chosen sit in one meal, which is the one he just built, and which was not adjusted:
        // nothing was logged differently from its definition, because the definition came from it.
        val gathered = after.single { it.savedMealId == savedMealId }
        assertThat(gathered.items.map { it.name }).containsExactly("Eggs", "Coffee with milk")
        assertThat(gathered.savedMealAdjusted).isFalse()
        assertThat(gathered.title).isEqualTo("Shakshuka breakfast")

        // The third is exactly where it was, in the meal it was logged in.
        val untouched = after.single { it.savedMealId == null }
        assertThat(untouched.items.map { it.name }).containsExactly("Tomato")
        assertThat(untouched.id).isEqualTo(tomatoMealId)

        // The meal the coffee was the only thing in is gone: a meal with nothing in it is not a
        // record of anything.
        assertThat(after).hasSize(2)

        // Not one item's calories, macros, portion, source or confidence moved — compared item by
        // item, by identity, which is the whole of what "no stored number changes" means.
        val afterById = after.flatMap { it.items }.associateBy { it.id }
        itemsBefore.forEach { was -> assertThat(afterById[was.id]).isEqualTo(was) }

        // And so the day comes to the same number it did before.
        assertThat(after.sumOf { meal -> meal.items.sumOf { it.kcal } })
            .isEqualTo(before.sumOf { meal -> meal.items.sumOf { it.kcal } })
    }

    /**
     * The gathered row keeps the day's order rather than jumping to the end.
     *
     * Rows logged at different moments can be pulled together, and the grouping is not a claim about
     * when they were eaten — so the one honest place for the new row is where the earliest thing in
     * it already was.
     */
    @Test
    fun `a gathered meal sits where the earliest row in it sat`() = runTest {
        assumeSqliteRuntime()

        repository.log(
            aMeal(epochDay = DAY, loggedAtMillis = at(9), items = listOf(anItem(name = "Eggs"))),
        )
        repository.log(
            aMeal(epochDay = DAY, loggedAtMillis = at(11), items = listOf(anItem(name = "Tomato"))),
        )
        repository.log(
            aMeal(epochDay = DAY, loggedAtMillis = at(13), items = listOf(anItem(name = "Coffee"))),
        )
        val before = repository.observeDay(DAY).first()
        val chosen = before.flatMap { it.items }
            .filter { it.name == "Eggs" || it.name == "Coffee" }
            .map { it.id }

        repository.gatherIntoSavedMeal(
            epochDay = DAY,
            itemIds = chosen,
            savedMealId = savedMealCalled("Shakshuka breakfast"),
        )

        val after = repository.observeDay(DAY).first()
        assertThat(after.first().title).isEqualTo("Shakshuka breakfast")
        assertThat(after.first().loggedAtMillis).isEqualTo(at(9))
    }

    // --- issue #25: undo puts back the entry that was deleted, as it was ------------------------
    //
    // Each of these is repeated, under the same name, in InMemoryMealRepositoryTest, which runs on
    // this box. Diff the two by name in review: this file is where Room's refusals and cascades are
    // proven, and it only runs in CI.

    @Test
    fun `undoing the only row of a meal brings the meal back as it was`() = runTest {
        val foodId = foodCalled("Greek salad")
        val savedMealId = savedMealCalled("Greek salad")
        repository.log(
            aMeal(
                epochDay = DAY,
                loggedAtMillis = at(12),
                note = "written down later",
                savedMealId = savedMealId,
                savedMealAdjusted = true,
                items = listOf(
                    anItem(
                        name = "Greek salad",
                        portion = "1 bowl",
                        portionAmount = 1.0,
                        portionUnit = "bowl",
                        kcal = 320,
                        source = Source.AI_ESTIMATE,
                        confidence = Confidence.MEDIUM,
                    ).copy(foodId = foodId),
                ),
            ),
        )
        val before = repository.observeDay(DAY).first().single()

        val entry = repository.deleteItem(before.items.single().id)
        assertThat(repository.observeDay(DAY).first()).isEmpty()
        assertThat(entry).isNotNull()
        repository.restore(entry!!)

        val after = repository.observeDay(DAY).first().single()
        assertThat(after.id).isEqualTo(before.id)
        assertThat(after.epochDay).isEqualTo(DAY)
        assertThat(after.loggedAtMillis).isEqualTo(at(12))
        assertThat(after.note).isEqualTo("written down later")
        assertThat(after.savedMealId).isEqualTo(savedMealId)
        assertThat(after.savedMealName).isEqualTo("Greek salad")
        assertThat(after.savedMealAdjusted).isTrue()
        val row = after.items.single()
        assertThat(row.id).isEqualTo(before.items.single().id)
        assertThat(row.portion).isEqualTo("1 bowl")
        assertThat(row.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(row.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(row.foodId).isEqualTo(foodId)
        // And everything else, field by field, as one comparison.
        assertThat(after).isEqualTo(before)
    }

    @Test
    fun `undoing one row of several puts it back into the same meal`() = runTest {
        repository.log(
            aMeal(
                epochDay = DAY,
                loggedAtMillis = at(9),
                items = listOf(anItem(name = "Eggs"), anItem(name = "Tomato")),
            ),
        )
        val before = repository.observeDay(DAY).first().single()
        val eggs = before.items.first()

        val entry = repository.deleteItem(eggs.id)
        assertThat(entry).isNotNull()
        repository.restore(entry!!)

        val after = repository.observeDay(DAY).first()
        assertThat(after).hasSize(1)
        assertThat(after.single().id).isEqualTo(before.id)
        assertThat(after.single().items.map { it.name }).containsExactly("Eggs", "Tomato").inOrder()
        assertThat(after.single().items.first().id).isEqualTo(eggs.id)
        assertThat(after.single()).isEqualTo(before)
    }

    /** The meal's time now is his latest word on it (D33); the snapshot's copy is older. */
    @Test
    fun `a row put back into a meal re-timed meanwhile keeps the new time`() = runTest {
        repository.log(
            aMeal(
                epochDay = DAY,
                loggedAtMillis = at(9),
                items = listOf(anItem(name = "Eggs"), anItem(name = "Tomato")),
            ),
        )
        val before = repository.observeDay(DAY).first().single()

        val entry = repository.deleteItem(before.items.last().id)
        repository.setEatenAt(before.id, atMillis = at(8))
        assertThat(entry).isNotNull()
        repository.restore(entry!!)

        val after = repository.observeDay(DAY).first().single()
        assertThat(after.id).isEqualTo(before.id)
        assertThat(after.loggedAtMillis).isEqualTo(at(8))
        assertThat(after.items.map { it.id }).isEqualTo(before.items.map { it.id })
    }

    @Test
    fun `a restored meal goes back to its place in the day`() = runTest {
        listOf(8 to "Eggs", 12 to "Hummus", 19 to "Pasta").forEach { (hour, name) ->
            repository.log(
                aMeal(epochDay = DAY, loggedAtMillis = at(hour), items = listOf(anItem(name = name))),
            )
        }
        val noon = repository.observeDay(DAY).first().single { it.loggedAtMillis == at(12) }

        val entry = repository.deleteItem(noon.items.single().id)
        repository.log(
            aMeal(epochDay = DAY, loggedAtMillis = at(21), items = listOf(anItem(name = "Tea"))),
        )
        assertThat(entry).isNotNull()
        repository.restore(entry!!)

        val after = repository.observeDay(DAY).first()
        assertThat(after.map { it.loggedAtMillis })
            .containsExactly(at(8), at(12), at(19), at(21)).inOrder()
        assertThat(after[1].id).isEqualTo(noon.id)
        assertThat(after[1].items.single().id).isEqualTo(noon.items.single().id)
    }

    /**
     * `food_items.foodId` has a foreign key to `foods`; putting back a dead id is refused. Null is
     * what deleting the food already did to every other row of it.
     *
     * The delete is not refused: a food is refused deletion only while a SAVED meal uses it, and
     * logged rows never block it.
     */
    @Test
    fun `a row whose food was deleted meanwhile comes back detached`() = runTest {
        val foods = RoomFoodRepository(db, db.foodDao(), Now { 0 })
        val foodId = foods.findOrCreate("Hummus", facts = FoodFacts(per100g = aPer100g())).food.id
        repository.log(
            aMeal(
                epochDay = DAY,
                loggedAtMillis = at(12),
                items = listOf(
                    anItem(name = "Hummus", portionAmount = 100.0, portionUnit = "g", kcal = 180)
                        .copy(foodId = foodId),
                ),
            ),
        )
        val before = repository.observeDay(DAY).first().single()

        val entry = repository.deleteItem(before.items.single().id)
        assertThat(foods.delete(foodId)).isEqualTo(EditResult.Done)
        assertThat(entry).isNotNull()
        repository.restore(entry!!)

        val after = repository.observeDay(DAY).first().single()
        assertThat(after.items.single().foodId).isNull()
        assertThat(after).isEqualTo(
            before.copy(items = listOf(before.items.single().copy(foodId = null, currentName = null))),
        )
    }

    /**
     * `meals.savedMealId` has a foreign key to `saved_meals`. The adjusted flag stays: deleting a
     * saved meal leaves it alone on every other meal, and it is written once, never updated.
     */
    @Test
    fun `a meal whose saved meal was deleted meanwhile comes back without the link`() = runTest {
        val savedMealId = savedMealCalled("Greek salad")
        repository.log(
            aMeal(
                epochDay = DAY,
                loggedAtMillis = at(12),
                note = "written down later",
                savedMealId = savedMealId,
                savedMealAdjusted = true,
                items = listOf(anItem(name = "Greek salad")),
            ),
        )
        val before = repository.observeDay(DAY).first().single()

        val entry = repository.deleteItem(before.items.single().id)
        db.savedMealDao().deleteMeal(savedMealId)
        assertThat(entry).isNotNull()
        repository.restore(entry!!)

        val after = repository.observeDay(DAY).first().single()
        assertThat(after.savedMealId).isNull()
        assertThat(after.savedMealAdjusted).isTrue()
        assertThat(after.loggedAtMillis).isEqualTo(at(12))
        assertThat(after.note).isEqualTo("written down later")
        assertThat(after).isEqualTo(before.copy(savedMealId = null, savedMealName = null))
    }

    @Test
    fun `restoring the same entry twice puts the row back once`() = runTest {
        repository.log(aMeal(epochDay = DAY, loggedAtMillis = at(12), items = listOf(anItem())))
        val before = repository.observeDay(DAY).first().single()

        val entry = repository.deleteItem(before.items.single().id)
        assertThat(entry).isNotNull()
        repository.restore(entry!!)
        repository.restore(entry)

        val after = repository.observeDay(DAY).first()
        assertThat(after).hasSize(1)
        assertThat(after.single().items.map { it.id }).containsExactly(before.items.single().id)
    }

    /**
     * Logging hands back the ids of the rows it wrote, in the order they were given (D46, #24).
     *
     * The one thing keeping a described meal rests on: the rows it gathers are the rows that write
     * just made, named by id. Read off the day instead, two meals logged in the same millisecond
     * could not be told apart and the meal would swallow the wrong rows. Asserted against the day
     * the database then shows, matched by name, because an id in the wrong order is the same defect
     * as an id that is wrong.
     *
     * The same case runs against the stand-in in `InMemoryMealRepositoryTest`, under this name.
     */
    @Test
    fun `logging hands back the ids of the rows it wrote, in order`() = runTest {
        // Said here as well as in setUp, so what this test needs is visible where it is read.
        assumeSqliteRuntime()

        val ids = repository.log(
            aMeal(
                epochDay = DAY,
                loggedAtMillis = at(hour = 9),
                items = listOf(
                    anItem(name = "Cucumber", portion = "100 g", portionAmount = 100.0, portionUnit = "g"),
                    anItem(name = "Olive oil", portion = "1 spoon", portionAmount = 1.0, portionUnit = "spoon"),
                ),
            ),
        )

        val rows = repository.observeDay(DAY).first().single().items
        assertThat(ids).hasSize(2)
        assertThat(ids).containsNoDuplicates()
        assertThat(rows.map { it.name }).containsExactly("Cucumber", "Olive oil").inOrder()
        assertThat(ids).isEqualTo(rows.map { it.id })
    }

    /** A food the rows can point at, made the only way a food may be made. */
    private suspend fun foodCalled(name: String): Long =
        RoomFoodRepository(db, db.foodDao(), Now { 0 })
            .findOrCreate(name, facts = FoodFacts(per100g = aPer100g()))
            .food.id

    /** A meal the owner built, with no parts: all this test needs is something to point rows at. */
    private suspend fun savedMealCalled(name: String): Long = db.savedMealDao().insertMeal(
        SavedMealEntity(
            name = name,
            nameKey = FoodKeys.nameKey(name),
            createdAtMillis = 0,
            updatedAtMillis = 0,
        ),
    )

    private fun at(hour: Int): Long = 1_772_000_000_000L + hour * 3_600_000L

    private companion object {
        const val DAY = 20_699L
    }
}
