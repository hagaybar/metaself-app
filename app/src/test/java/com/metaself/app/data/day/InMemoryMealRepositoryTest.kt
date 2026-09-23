package com.metaself.app.data.day

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.food.EditResult
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.aFood
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.day.aMeal
import com.metaself.app.domain.day.anItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The stand-in means what the database means, for undo (issue #25).
 *
 * JUnit 5: nothing here needs a framework, and it runs on this box. The undo tests are repeated
 * here under the SAME NAMES as in `RoomMealRepositoryTest`, which only runs in CI; diff the two
 * files by name in review. The view-model tests of undo run against this class, so if it drifted
 * from Room they would prove nothing about the app.
 *
 * The last four pin down the stand-in's own ids and order. Each is a way it could hand out an id
 * that is already somebody's, after which restoring by original id would drop a row into a
 * newcomer's meal — a false finding about the app.
 *
 * Every meal and row seeded here carries a distinct, non-zero id, because the assertions name ids.
 */
class InMemoryMealRepositoryTest {

    // --- tests 5–11: the same cases as RoomMealRepositoryTest, the same names ----------------

    @Test
    fun `undoing the only row of a meal brings the meal back as it was`() = runTest {
        val foods = FakeFoodRepository(listOf(aFood(name = "Greek salad")))
        val foodId = foods.current.single().id
        val titles = MutableStateFlow(mapOf(SALAD_MEAL to "Greek salad"))
        val repository = InMemoryMealRepository(
            initial = listOf(
                aMeal(
                    id = 1,
                    epochDay = DAY,
                    loggedAtMillis = at(12),
                    note = "written down later",
                    savedMealId = SALAD_MEAL,
                    savedMealAdjusted = true,
                    items = listOf(
                        anItem(
                            id = 10,
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
            ),
            foods = foods,
            mealTitles = titles,
        )
        val before = repository.observeDay(DAY).first().single()

        val entry = repository.deleteItem(10)
        assertThat(repository.observeDay(DAY).first()).isEmpty()
        assertThat(entry).isNotNull()
        repository.restore(entry!!)

        val after = repository.observeDay(DAY).first().single()
        assertThat(after.id).isEqualTo(1L)
        assertThat(after.epochDay).isEqualTo(DAY)
        assertThat(after.loggedAtMillis).isEqualTo(at(12))
        assertThat(after.note).isEqualTo("written down later")
        assertThat(after.savedMealId).isEqualTo(SALAD_MEAL)
        assertThat(after.savedMealName).isEqualTo("Greek salad")
        assertThat(after.savedMealAdjusted).isTrue()
        val row = after.items.single()
        assertThat(row.id).isEqualTo(10L)
        assertThat(row.portion).isEqualTo("1 bowl")
        assertThat(row.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(row.confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(row.foodId).isEqualTo(foodId)
        // And everything else, field by field, as one comparison.
        assertThat(after).isEqualTo(before)
    }

    @Test
    fun `undoing one row of several puts it back into the same meal`() = runTest {
        val repository = InMemoryMealRepository(
            listOf(
                aMeal(
                    id = 1,
                    epochDay = DAY,
                    loggedAtMillis = at(9),
                    items = listOf(anItem(id = 10, name = "Eggs"), anItem(id = 11, name = "Tomato")),
                ),
            ),
        )
        val before = repository.observeDay(DAY).first().single()

        val entry = repository.deleteItem(10)
        assertThat(entry).isNotNull()
        repository.restore(entry!!)

        val after = repository.observeDay(DAY).first()
        assertThat(after).hasSize(1)
        assertThat(after.single().id).isEqualTo(1L)
        assertThat(after.single().items.map { it.name }).containsExactly("Eggs", "Tomato").inOrder()
        assertThat(after.single().items.first().id).isEqualTo(10L)
        assertThat(after.single()).isEqualTo(before)
    }

    /** The meal's time now is his latest word on it (D33); the snapshot's copy is older. */
    @Test
    fun `a row put back into a meal re-timed meanwhile keeps the new time`() = runTest {
        val repository = InMemoryMealRepository(
            listOf(
                aMeal(
                    id = 1,
                    epochDay = DAY,
                    loggedAtMillis = at(9),
                    items = listOf(anItem(id = 10, name = "Eggs"), anItem(id = 11, name = "Tomato")),
                ),
            ),
        )

        val entry = repository.deleteItem(11)
        repository.setEatenAt(1, atMillis = at(8))
        assertThat(entry).isNotNull()
        repository.restore(entry!!)

        val after = repository.observeDay(DAY).first().single()
        assertThat(after.id).isEqualTo(1L)
        assertThat(after.loggedAtMillis).isEqualTo(at(8))
        assertThat(after.items.map { it.id }).containsExactly(10L, 11L).inOrder()
    }

    @Test
    fun `a restored meal goes back to its place in the day`() = runTest {
        val repository = InMemoryMealRepository(
            listOf(
                aMeal(id = 1, epochDay = DAY, loggedAtMillis = at(8), items = listOf(anItem(id = 10, name = "Eggs"))),
                aMeal(id = 2, epochDay = DAY, loggedAtMillis = at(12), items = listOf(anItem(id = 11, name = "Hummus"))),
                aMeal(id = 3, epochDay = DAY, loggedAtMillis = at(19), items = listOf(anItem(id = 12, name = "Pasta"))),
            ),
        )

        val entry = repository.deleteItem(11)
        repository.log(
            aMeal(epochDay = DAY, loggedAtMillis = at(21), items = listOf(anItem(name = "Tea"))),
        )
        assertThat(entry).isNotNull()
        repository.restore(entry!!)

        val after = repository.observeDay(DAY).first()
        assertThat(after.map { it.loggedAtMillis })
            .containsExactly(at(8), at(12), at(19), at(21)).inOrder()
        assertThat(after[1].id).isEqualTo(2L)
        assertThat(after[1].items.single().id).isEqualTo(11L)
    }

    /** Room refuses a dead `foodId` on the foreign key; the stand-in must answer the same way. */
    @Test
    fun `a row whose food was deleted meanwhile comes back detached`() = runTest {
        val foods = FakeFoodRepository(listOf(aFood(name = "Hummus")))
        val foodId = foods.current.single().id
        val repository = InMemoryMealRepository(
            initial = listOf(
                aMeal(
                    id = 1,
                    epochDay = DAY,
                    loggedAtMillis = at(12),
                    items = listOf(
                        anItem(id = 10, name = "Hummus", portionAmount = 100.0, portionUnit = "g", kcal = 180)
                            .copy(foodId = foodId),
                    ),
                ),
            ),
            foods = foods,
        )
        val before = repository.observeDay(DAY).first().single()

        val entry = repository.deleteItem(10)
        assertThat(foods.delete(foodId)).isEqualTo(EditResult.Done)
        assertThat(entry).isNotNull()
        repository.restore(entry!!)

        val after = repository.observeDay(DAY).first().single()
        assertThat(after.items.single().foodId).isNull()
        assertThat(after).isEqualTo(
            before.copy(items = listOf(before.items.single().copy(foodId = null, currentName = null))),
        )
    }

    /** Room refuses a dead `savedMealId`; the adjusted flag is written once and stays. */
    @Test
    fun `a meal whose saved meal was deleted meanwhile comes back without the link`() = runTest {
        val titles = MutableStateFlow(mapOf(SALAD_MEAL to "Greek salad"))
        val repository = InMemoryMealRepository(
            initial = listOf(
                aMeal(
                    id = 1,
                    epochDay = DAY,
                    loggedAtMillis = at(12),
                    note = "written down later",
                    savedMealId = SALAD_MEAL,
                    savedMealAdjusted = true,
                    items = listOf(anItem(id = 10, name = "Greek salad")),
                ),
            ),
            mealTitles = titles,
        )
        val before = repository.observeDay(DAY).first().single()

        val entry = repository.deleteItem(10)
        // The stand-in's view of the saved_meals table: the meal is gone from it.
        titles.value = emptyMap()
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
        val repository = InMemoryMealRepository(
            listOf(aMeal(id = 1, epochDay = DAY, loggedAtMillis = at(12), items = listOf(anItem(id = 10)))),
        )

        val entry = repository.deleteItem(10)
        assertThat(entry).isNotNull()
        repository.restore(entry!!)
        repository.restore(entry)

        val after = repository.observeDay(DAY).first()
        assertThat(after).hasSize(1)
        assertThat(after.single().items.map { it.id }).containsExactly(10L)
    }

    // --- tests 12–15: the stand-in's own fidelity to AUTOINCREMENT and to the DAO's order ------

    /** AUTOINCREMENT never hands a freed id out again. `max + 1` does, as soon as the top meal goes. */
    @Test
    fun `a meal logged after a delete does not take the deleted meal's id`() = runTest {
        val repository = InMemoryMealRepository(
            listOf(
                aMeal(id = 1, epochDay = DAY, loggedAtMillis = at(8), items = listOf(anItem(id = 10, name = "Eggs"))),
                aMeal(id = 2, epochDay = DAY, loggedAtMillis = at(12), items = listOf(anItem(id = 11, name = "Hummus"))),
            ),
        )

        repository.deleteItem(11)
        repository.log(
            aMeal(epochDay = DAY, loggedAtMillis = at(19), items = listOf(anItem(name = "Pasta"))),
        )

        val pasta = repository.observeDay(DAY).first().single { it.items.single().name == "Pasta" }
        assertThat(pasta.id).isGreaterThan(2L)
        assertThat(pasta.items.single().id).isGreaterThan(11L)
    }

    /**
     * An id given explicitly advances the counter, as it advances `sqlite_sequence` (checked with
     * Python's sqlite3 on the version-5 schema: insert id 5 → the next id-0 insert gets 6).
     */
    @Test
    fun `an id given explicitly is never handed out again`() = runTest {
        val repository = InMemoryMealRepository()

        repository.log(aMeal(id = 5, epochDay = DAY, loggedAtMillis = at(8), items = listOf(anItem(id = 7))))
        repository.log(
            aMeal(epochDay = DAY, loggedAtMillis = at(12), items = listOf(anItem(name = "Pita"))),
        )

        val pita = repository.observeDay(DAY).first().single { it.items.single().name == "Pita" }
        assertThat(pita.id).isGreaterThan(5L)
        assertThat(pita.items.single().id).isGreaterThan(7L)
    }

    /**
     * Room never stores id 0: an id-0 insert is given a fresh id. `aMeal()` and `anItem()` default
     * to 0, so a stand-in keeping them would hold two rows under one id, and deleting one would
     * delete both.
     */
    @Test
    fun `meals and rows seeded without ids are given distinct ones`() = runTest {
        val repository = InMemoryMealRepository(
            listOf(aMeal(epochDay = DAY, loggedAtMillis = at(8), items = listOf(anItem(), anItem(name = "Pita")))),
        )

        val seeded = repository.observeDay(DAY).first().single()
        assertThat(seeded.id).isNotEqualTo(0L)
        val rowIds = seeded.items.map { it.id }
        assertThat(rowIds).doesNotContain(0L)
        assertThat(rowIds).containsNoDuplicates()

        repository.log(
            aMeal(epochDay = DAY, loggedAtMillis = at(12), items = listOf(anItem(name = "Hummus"))),
        )

        val hummus = repository.observeDay(DAY).first().single { meal -> meal.items.any { it.name == "Hummus" } }
        assertThat(hummus.id).isGreaterThan(seeded.id)
        assertThat(hummus.items.single().id).isGreaterThan(rowIds.max())
    }

    /** `MealDao.observeDay` orders by `loggedAtMillis`, then `id`. Without this, test 8 passes by luck. */
    @Test
    fun `the day reads in time order, then id, as the database orders it`() = runTest {
        val repository = InMemoryMealRepository()

        repository.log(aMeal(id = 1, epochDay = DAY, loggedAtMillis = at(19), items = listOf(anItem(id = 10))))
        repository.log(aMeal(id = 2, epochDay = DAY, loggedAtMillis = at(8), items = listOf(anItem(id = 11))))
        repository.log(aMeal(id = 4, epochDay = DAY, loggedAtMillis = at(12), items = listOf(anItem(id = 12))))
        repository.log(aMeal(id = 3, epochDay = DAY, loggedAtMillis = at(12), items = listOf(anItem(id = 13))))

        assertThat(repository.observeDay(DAY).first().map { it.id })
            .containsExactly(2L, 3L, 4L, 1L).inOrder()
    }

    /**
     * Logging hands back the ids it handed out, in the order the rows were given (D46, issue #24).
     *
     * The stand-in has to mean what the database means here too, because the view-model tests of
     * keeping a described meal choose the rows by the ids this returns: a stand-in that answered
     * with nothing, or with ids of its own invention, would leave those tests proving something
     * about themselves. The same case runs against Room in `RoomMealRepositoryTest`, under the same
     * name.
     */
    @Test
    fun `logging hands back the ids of the rows it wrote, in order`() = runTest {
        val repository = InMemoryMealRepository()

        val ids = repository.log(
            aMeal(
                epochDay = DAY,
                loggedAtMillis = at(12),
                items = listOf(anItem(name = "Cucumber"), anItem(name = "Olive oil")),
            ),
        )

        val rows = repository.observeDay(DAY).first().single().items
        assertThat(ids).hasSize(2)
        assertThat(ids).containsNoDuplicates()
        assertThat(ids).isEqualTo(rows.map { it.id })
        // Named, not merely counted: an id in the wrong order would gather the wrong row.
        assertThat(rows.map { it.name }).containsExactly("Cucumber", "Olive oil").inOrder()
    }

    private fun at(hour: Int): Long = 1_772_000_000_000L + hour * 3_600_000L

    private companion object {
        const val DAY = TEST_EPOCH_DAY

        /** The saved meal the linked meals point at. */
        const val SALAD_MEAL = 3L
    }
}
