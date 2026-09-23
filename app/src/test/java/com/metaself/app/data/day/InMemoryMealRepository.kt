package com.metaself.app.data.day

import com.metaself.app.data.food.FoodRepository
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * A day's rows, in memory, that actually CHANGE when something changes them.
 *
 * There is already a fake `MealRepository` inside `DayViewModelTest`, and it is right for what it
 * does: `updateItem`, `deleteItem` and `gatherIntoSavedMeal` only RECORD the call, because what those
 * do to stored rows is the one thing a fake cannot prove and `RoomMealRepositoryTest` proves it
 * against a real database in CI. Asserting "you asked to delete row 4" is the honest assertion there.
 *
 * It is the wrong shape for a walk. A walk deletes a row and then LOOKS at the day; against a
 * recording fake the row is still sitting there, and the walk reports a defect the app does not have.
 * So this one performs the operations, and is a separate class rather than a change to that one —
 * the recording behaviour is load-bearing for those tests.
 *
 * **What this still cannot tell you** is whatever Room would refuse or cascade. It is a stand-in, and
 * a kind one: it has no constraints, no foreign keys and no uniqueness. Anything a walk discovers here
 * is a finding about the SCREENS, and holds against the database only once CI has run.
 *
 * [deleteItem] and [restore] are performed too, because undo (issue #25) is only visible on a day
 * that actually changes. Two properties of the database are copied on purpose, each because leaving
 * it out would produce a finding about the app that is really about this class:
 *
 * - **Ids behave as `AUTOINCREMENT` does**, for meals and rows alike: a freed id is never handed out
 *   again, an explicit id advances the counter as it advances `sqlite_sequence`, and nothing is ever
 *   stored under id 0. [restore] finds a row and its meal by original id; an id handed out twice
 *   would put a restored row into a stranger's meal.
 * - **The day reads in time order, then id**, as `MealDao.observeDay` does. Without it a restored
 *   meal would appear at the end of the day, and a test of where it goes back would pass or fail on
 *   the order things happened to be inserted in.
 */
class InMemoryMealRepository(
    initial: List<Meal> = emptyList(),
    /**
     * The food list and the meal list, so a row can be labelled with what its food is called NOW and
     * a logged meal can be titled with the name of the meal it came from.
     *
     * **Both are reads THROUGH a pointer in the real repository, and leaving them out was the third
     * false finding this harness produced.** `RoomMealRepository` combines the day's rows with a map
     * of current food names and a map of saved-meal names; a stand-in that returns the rows as stored
     * shows a day with no meal titles on it at all. The walk duly reported that "a logged meal leaves
     * no trace of itself on the day — it lands as its parts", which is what the app would do only if
     * this join did not exist.
     *
     * Null leaves the rows exactly as stored, which is what a test asserting on stored rows wants.
     */
    private val foods: FoodRepository? = null,
    private val mealTitles: Flow<Map<Long, String>>? = null,
) : MealRepository {

    /*
     * The two id counters, standing in for `sqlite_sequence`. Declared BEFORE `state`: property
     * initialisers run in order, and `state` is built by [stored], which advances them — declared
     * after it, they would be reset to their initial values once it had.
     */
    private var lastMealId: Long = 0L
    private var lastItemId: Long = 0L

    private val state = MutableStateFlow(stored(initial))

    /** Every gathering that happened, as the day, the rows moved, and the meal they went under. */
    val gathered = mutableListOf<Triple<Long, List<Long>, Long>>()

    val current: List<Meal> get() = state.value

    override fun observeDay(epochDay: Long): Flow<List<Meal>> =
        labelled(
            state.map { meals ->
                meals.filter { it.epochDay == epochDay }.sortedWith(compareBy({ it.loggedAtMillis }, { it.id }))
            },
        )

    /**
     * Reads the two names through their pointers, as the real repository does.
     *
     * A row shows what its food is called now, so renaming a food re-labels every day it appears on;
     * a logged meal shows the name of the meal it came from, so renaming the meal retitles every day
     * it was eaten. Neither moves a stored number, which is the whole point of reading them here
     * rather than storing them.
     */
    private fun labelled(meals: Flow<List<Meal>>): Flow<List<Meal>> {
        val foodNames = foods?.observeAll()?.map { all -> all.associate { it.id to it.name } }
        val mealNames = mealTitles
        if (foodNames == null && mealNames == null) return meals
        return combine(
            meals,
            foodNames ?: flowOf(emptyMap()),
            mealNames ?: flowOf(emptyMap()),
        ) { day, names, titles ->
            day.map { meal ->
                meal.copy(
                    items = meal.items.map { item ->
                        val now = item.foodId?.let(names::get)
                        if (now == null) item else item.copy(currentName = now)
                    },
                    savedMealName = meal.savedMealId?.let(titles::get),
                )
            }
        }
    }

    override fun observeLoggedDays(): Flow<Set<Long>> =
        state.map { meals -> meals.map { it.epochDay }.toSet() }

    override suspend fun kcalByDaySince(fromEpochDay: Long): Map<Long, Int> =
        state.value
            .filter { it.epochDay >= fromEpochDay }
            .groupBy { it.epochDay }
            .mapValues { (_, meals) -> meals.sumOf { meal -> meal.items.sumOf { it.kcal } } }

    /** The ids handed out here are the answer, in `meal.items` order, as the database's are. */
    override suspend fun log(meal: Meal): List<Long> {
        val written = meal.withIds()
        state.value = state.value + written
        return written.items.map { it.id }
    }

    /** Performed, like everything here: a walk sets a time and then looks at the day (D33). */
    override suspend fun setEatenAt(mealId: Long, atMillis: Long) {
        state.value = state.value.map { if (it.id == mealId) it.copy(loggedAtMillis = atMillis) else it }
    }

    override suspend fun rowsWithNoFood(): List<DetachedRow> =
        state.value.flatMap { it.items }.filter { it.foodId == null }.map { DetachedRow(it.id, it.name) }

    override suspend fun attachRow(itemId: Long, foodId: Long) {
        state.value = state.value.map { meal ->
            meal.copy(items = meal.items.map { if (it.id == itemId && it.foodId == null) it.copy(foodId = foodId) else it })
        }
    }

    override suspend fun updateItem(item: FoodItem) {
        state.value = state.value.map { meal ->
            meal.copy(items = meal.items.map { if (it.id == item.id) item else it })
        }
    }

    /**
     * Takes the row out, and takes the meal with it if that was the last row; hands back both.
     *
     * A meal cannot be empty — the domain type refuses to construct one — so leaving a meal behind
     * with no rows in it would not merely be unfaithful, it would throw the next time the day was
     * read, and the walk would blame the screen.
     *
     * The receipt is built with the production mapping, `toEntities` and `toEntity`, rather than by
     * hand, so the stand-in's receipt and Room's cannot drift apart field by field.
     */
    override suspend fun deleteItem(itemId: Long): DeletedEntry? {
        val meal = state.value.firstOrNull { meal -> meal.items.any { it.id == itemId } } ?: return null
        val item = meal.items.first { it.id == itemId }
        state.value = state.value.mapNotNull { stored ->
            val left = stored.items.filterNot { it.id == itemId }
            if (left.isEmpty()) null else stored.copy(items = left)
        }
        return DeletedEntry(meal = meal.toEntities().first, item = item.toEntity(mealId = meal.id))
    }

    /**
     * Puts the row back under its own id, answering each way the world may have moved as Room does.
     *
     * - The row is already there → nothing.
     * - Its meal is still there → the row goes back into it, and the meal is left as it is now.
     * - Its meal is gone → the meal comes back from the receipt, under its own id.
     * - Its food is gone → the row comes back detached, which is what Room's foreign key forces.
     * - Its saved meal is gone → the meal comes back unlinked, its adjusted flag kept.
     *
     * The stand-in has no foreign keys, so "gone" is read from the two sources it was given. **When a
     * source was not wired, the link is kept**: there is nothing to check it against, and a test that
     * asserts on these cases wires the source.
     *
     * Converted back through `MealWithItems.toDomain`, the production mapping, for the reason
     * [deleteItem] builds the receipt with the production mapping.
     */
    override suspend fun restore(entry: DeletedEntry) {
        if (state.value.any { meal -> meal.items.any { it.id == entry.item.id } }) return

        val foodId = entry.item.foodId?.takeIf { id -> foods == null || foods.byId(id) != null }
        val savedMealId = entry.meal.savedMealId?.takeIf { id -> mealTitles == null || id in mealTitles.first() }
        val restored = checkNotNull(
            MealWithItems(
                meal = entry.meal.copy(savedMealId = savedMealId),
                items = listOf(entry.item.copy(mealId = entry.meal.id, foodId = foodId)),
            ).toDomain(),
        ) { "A meal holding one row always reads back" }
        val row = restored.items.single()

        // Room's sqlite_sequence cannot be below an id it holds; neither can these.
        lastMealId = maxOf(lastMealId, entry.meal.id)
        lastItemId = maxOf(lastItemId, entry.item.id)

        state.value = if (state.value.any { it.id == entry.meal.id }) {
            state.value.map { meal ->
                if (meal.id != entry.meal.id) meal else meal.copy(items = (meal.items + row).sortedBy { it.id })
            }
        } else {
            state.value + restored
        }
    }

    /**
     * Moves the chosen rows out of whatever they were logged under and into one meal of their own.
     *
     * Modelled on what the day screen then shows: the rows keep their stored numbers exactly — that
     * is the whole point of gathering, and a stand-in that recalculated anything here would hide the
     * defect it exists to catch.
     */
    override suspend fun gatherIntoSavedMeal(epochDay: Long, itemIds: List<Long>, savedMealId: Long) {
        gathered += Triple(epochDay, itemIds, savedMealId)
        val moving = state.value
            .filter { it.epochDay == epochDay }
            .flatMap { it.items }
            .filter { it.id in itemIds }
        if (moving.isEmpty()) return

        val source = state.value.first { meal -> meal.items.any { it.id in itemIds } }
        val emptied = state.value.mapNotNull { meal ->
            if (meal.epochDay != epochDay) return@mapNotNull meal
            val left = meal.items.filterNot { it.id in itemIds }
            if (left.isEmpty()) null else meal.copy(items = left)
        }
        state.value = emptied + Meal(
            id = ++lastMealId,
            epochDay = epochDay,
            loggedAtMillis = source.loggedAtMillis,
            items = moving,
            savedMealId = savedMealId,
        )
    }

    /**
     * The seeded meals as Room would hold them: every id-0 meal and row given a fresh id.
     *
     * Room can never store id 0 — an id-0 insert is given one — and `aMeal()` and `anItem()` default
     * to it, so keeping them would leave two rows under one id: deleting one would delete both, and
     * [restore]'s checks would match the wrong row. The counters start above every explicit id
     * seeded, so none of those is handed out again either.
     */
    private fun stored(initial: List<Meal>): List<Meal> {
        lastMealId = initial.maxOfOrNull { it.id } ?: 0L
        lastItemId = initial.flatMap { it.items }.maxOfOrNull { it.id } ?: 0L
        return initial.map { it.withIds() }
    }

    /** The meal and its rows under ids as `AUTOINCREMENT` would give them; see [withIds]. */
    private fun Meal.withIds(): Meal {
        val mealId = if (id != 0L) id.also { lastMealId = maxOf(lastMealId, it) } else ++lastMealId
        return copy(id = mealId, items = items.withIds())
    }

    /**
     * Gives an id to every row that has not got one, and never hands out an id once used.
     *
     * A logging can mix rows that already carry ids with rows that do not — a repeated meal, a test
     * that seeds ids — so `max + index` collides. Ids have to behave as `AUTOINCREMENT` does, an
     * explicit id advancing the counter as it advances `sqlite_sequence`, because [restore] finds a
     * row and its meal by original id: an id handed out twice would put a restored row into a
     * stranger's meal.
     */
    private fun List<FoodItem>.withIds(): List<FoodItem> = map { item ->
        if (item.id != 0L) item.also { lastItemId = maxOf(lastItemId, it.id) } else item.copy(id = ++lastItemId)
    }
}
