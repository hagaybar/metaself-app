package com.metaself.app.data.food

import com.metaself.app.data.time.Now
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.Correction
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodKeys
import com.metaself.app.domain.food.FoodUse
import com.metaself.app.domain.food.GramsPerUnit
import com.metaself.app.domain.food.JoinedFacts
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.PerUnit
import com.metaself.app.domain.food.Provenance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * What the day's logged rows tell the food tables — the part of the database the food queries join
 * against and a join or a delete writes to. Implemented by the day's stand-in
 * (`InMemoryMealRepository`), and wired in with [FakeFoodRepository.linkedTo] where a test has both.
 */
interface LoggedRowsOfFoods {
    /** Food id → the latest `loggedAtMillis` of a meal holding a row that points at it. */
    fun observeLatestLogging(): Flow<Map<Long, Long>>

    /** Food id → how many logged rows point at it. */
    fun observeRowCounts(): Flow<Map<Long, Int>>

    /** `FoodDao.movePastRows`: every row pointing at [loserId] points at [winnerId] instead. */
    suspend fun movePastRows(winnerId: Long, loserId: Long)

    /** `ON DELETE SET NULL`: every row pointing at a deleted food points at nothing. */
    suspend fun detachRowsOf(foodId: Long)
}

/**
 * What the saved meals tell the food tables: which meals hold a food, and moving a food's parts on a
 * join. Implemented by the walk's saved meals (`SimSavedMealRepository`).
 */
interface SavedMealsOfFoods {
    /**
     * Food id → the names of the saved meals that have it as a part, hidden meals included, in name
     * order — `FoodDao`'s `MEALS_USING`, keyed by food.
     */
    fun observeMealsUsing(): Flow<Map<Long, List<String>>>

    /** `FoodDao.mealsHoldingBoth`. */
    suspend fun mealsHoldingBoth(winnerId: Long, loserId: Long): List<String>

    /** `FoodDao.moveMealComponents`. */
    suspend fun moveMealComponents(winnerId: Long, loserId: Long)
}

/**
 * The food repository, in memory, for screens, view models and walks under test.
 *
 * **It answers as `RoomFoodRepository` over `FoodDao` answers, statement by statement**, because a
 * stand-in kinder or stupider than the database makes a test — and above all a walk — say something
 * untrue about the app (public issue #6). In particular:
 *
 * - **Order.** [observeAll] is `updatedAtMillis DESC, id DESC`; [observeOffered] is the later of
 *   that and the food's latest logging, then `id DESC`. Every write the real statements stamp is
 *   stamped here from [now], and nothing else is — a repair of impossible figures keeps its stamp.
 * - **Names.** Held as `food_names` rows, each with its own brand key, so a join moves the absorbed
 *   food's names across under the brands they came with, and a name two rows would share is refused
 *   as the unique index refuses it — by throwing.
 * - **Refusals.** Delete refuses a food a saved meal holds, correcting refuses to empty the units a
 *   meal counts it in, and a join refuses two foods one meal holds. With saved meals wired in
 *   ([linkedTo]) the answer comes from them; without, from the switches [usedBySavedMeals] and
 *   [refusesToMerge], for a unit test that has no meals to wire.
 * - **Reading as no food.** A row that knows neither calories group is not a food: it drops out of
 *   every read, as `toDomain` drops it, but is still stored — so it still blocks its name.
 *
 * It is not a substitute for `RoomFoodRepositoryTest`, which is where the real statements and the
 * real indices are exercised, and which runs in CI.
 *
 * @param now the clock every write is stamped from. The default counts up from 1, one tick per
 *   write, so an order decided by stamps is decided and never a tie by accident. A walk puts every
 *   stand-in on the one clock with [onClock].
 */
class FakeFoodRepository(
    initial: List<Food> = emptyList(),
    private var now: Now = CountingClock(),
) : FoodRepository {

    /** One row of `foods`: every column a read or a query here looks at. */
    private data class Row(
        val id: Long,
        val brand: String,
        val barcode: String?,
        val per100g: PerHundredGrams?,
        val perUnit: PerUnit?,
        val gramsPerUnit: GramsPerUnit?,
        val createdAtMillis: Long,
        val updatedAtMillis: Long,
        val hidden: Boolean,
    )

    /** One row of `food_names`. [id] stands in for `addedAtMillis`: both only ever rise. */
    private data class NameRow(
        val id: Long,
        val foodId: Long,
        val displayName: String,
        val nameKey: String,
        val brandKey: String,
        val preferred: Boolean,
    )

    private data class Tables(val foods: List<Row>, val names: List<NameRow>)

    private var nextNameId = 1L

    private val tables = MutableStateFlow(seeded(initial))
    private var nextId: Long = initial.size + 1L

    /**
     * Every food it holds that reads as one, in id order, for a test that wants to assert on the
     * store rather than the screen.
     */
    val current: List<Food> get() = tables.value.let { t -> t.foods.mapNotNull { it.toFood(t.names) } }

    /** Put this store on [clock], so its stamps and the rest of a walk's compare as one timeline. */
    fun onClock(clock: Now) = apply { now = clock }

    private var rows: LoggedRowsOfFoods? = null
    private var meals: SavedMealsOfFoods? = null

    /**
     * Wire in the rest of the database: the day's rows and the saved meals. Either may be left out,
     * and what it would answer is then answered by the switches below, or not at all.
     */
    fun linkedTo(rows: LoggedRowsOfFoods? = this.rows, meals: SavedMealsOfFoods? = this.meals) =
        apply {
            this.rows = rows
            this.meals = meals
        }

    private var mergeRefusal: EditRefused? = null

    /**
     * Make the next join refuse, with this reason.
     *
     * For a test with no saved meals wired in: the real refusal is decided by a query over them.
     * Without a switch the screen's refusal branch — the pair, the question and the reason all left
     * standing — would be verified only by reading the code.
     */
    fun refusesToMerge(why: EditRefused) = apply { mergeRefusal = why }

    private val usedBy = MutableStateFlow<Map<Long, List<String>>>(emptyMap())

    /**
     * Make this food one that these saved meals use, for a test with no saved meals wired in.
     *
     * [savedMealsUsing], [delete], [correct] and [observeUse] all answer from it, as the real
     * repository answers them from one query — so a test that switches it on after the owner was
     * asked reaches the real late refusal.
     */
    fun usedBySavedMeals(foodId: Long, vararg meals: String) =
        apply { usedBy.value = usedBy.value + (foodId to meals.toList()) }

    private val loggedRows = MutableStateFlow<Map<Long, Int>>(emptyMap())

    /**
     * Make this many logged rows point at this food, for a test with no day wired in.
     *
     * [observeUse] says it again when it changes, as the real count is observed.
     */
    fun logged(foodId: Long, rows: Int) =
        apply { loggedRows.value = loggedRows.value + (foodId to rows) }

    /** In name order either way, as `MEALS_USING` reads them. */
    private fun mealsUsingFlow(): Flow<Map<Long, List<String>>> =
        meals?.observeMealsUsing() ?: usedBy.map { all -> all.mapValues { (_, names) -> names.sorted() } }

    private suspend fun mealsUsing(foodId: Long): List<String> = mealsUsingFlow().first()[foodId].orEmpty()

    // --- Reading ---------------------------------------------------------------------------------

    /** `FoodDao.observeOffered`: not hidden, by the later of edited and last logged, then id. */
    override fun observeOffered(): Flow<List<Food>> =
        combine(tables, rows?.observeLatestLogging() ?: flowOf(emptyMap())) { t, latest ->
            t.foods.filterNot { it.hidden }
                .sortedWith(
                    compareByDescending<Row> { maxOf(it.updatedAtMillis, latest[it.id] ?: 0L) }
                        .thenByDescending { it.id },
                )
                .mapNotNull { it.toFood(t.names) }
        }

    /** `FoodDao.observeAll`: `ORDER BY updatedAtMillis DESC, id DESC`. */
    override fun observeAll(): Flow<List<Food>> = tables.map { t ->
        t.foods.sortedWith(compareByDescending<Row> { it.updatedAtMillis }.thenByDescending { it.id })
            .mapNotNull { it.toFood(t.names) }
    }

    /** Counted from the columns, as the real count is — hidden foods included. */
    override fun observeOnlyAPortionCount(): Flow<Int> =
        tables.map { t -> t.foods.count { it.onlyAPortion() } }

    /**
     * `ORDER BY updatedAtMillis DESC`, which leaves ties to SQLite; they are kept in id order here,
     * and a test must not rely on either.
     */
    override fun observeOnlyAPortion(): Flow<List<Food>> = tables.map { t ->
        t.foods.filter { it.onlyAPortion() }
            .sortedByDescending { it.updatedAtMillis }
            .mapNotNull { it.toFood(t.names) }
    }

    override suspend fun byId(id: Long): Food? = tables.value.let { t -> t.row(id)?.toFood(t.names) }

    override suspend fun foodIdsNamed(name: String): List<Long> {
        val key = FoodKeys.nameKey(name)
        return tables.value.names.filter { it.nameKey == key }.map { it.foodId }.distinct()
    }

    override suspend fun byBarcode(barcode: String): Food? =
        tables.value.let { t -> t.foods.firstOrNull { it.barcode == barcode }?.toFood(t.names) }

    // --- Creating and offering -------------------------------------------------------------------

    override suspend fun findOrCreate(
        name: String,
        brand: String?,
        facts: FoodFacts,
        barcode: String?,
    ): FoundOrCreated {
        val moment = now()
        val nameKey = FoodKeys.nameKey(name)
        val brandKey = FoodKeys.brandKey(brand)

        val existingId = barcode?.let { code -> tables.value.foods.firstOrNull { it.barcode == code }?.id }
            ?: foodIdNamed(nameKey, brandKey)

        if (existingId != null) {
            val stored = tables.value.row(existingId)
            // What the food held a moment ago, read before the offer, reported and not judged.
            val before = byId(existingId)?.facts
            offerEach(existingId, facts.per100g, facts.perUnit, facts.gramsPerUnit) { moment }
            if (barcode != null && stored?.barcode == null) {
                update(existingId) { it.copy(barcode = barcode, updatedAtMillis = moment) }
            }
            val found = requireNotNull(byId(existingId)) { "a food that exists must read back" }
            return FoundOrCreated(found, wasCreated = false, before = before)
        }

        val id = nextId++
        write(
            tables.value.copy(
                foods = tables.value.foods + Row(
                    id = id,
                    brand = brand?.takeIf { it.isNotBlank() } ?: FoodKeys.NO_BRAND,
                    barcode = barcode,
                    per100g = null,
                    perUnit = null,
                    gramsPerUnit = null,
                    createdAtMillis = moment,
                    updatedAtMillis = moment,
                    hidden = false,
                ),
                names = tables.value.names + NameRow(
                    id = nextNameId++,
                    foodId = id,
                    displayName = FoodKeys.displayName(name),
                    nameKey = nameKey,
                    brandKey = brandKey,
                    preferred = true,
                ),
            ),
        )
        offerEach(id, facts.per100g, facts.perUnit, facts.gramsPerUnit) { moment }
        return FoundOrCreated(
            requireNotNull(byId(id)) { "a food just created must read back" },
            wasCreated = true,
            before = null,
        )
    }

    override suspend fun offerFacts(foodId: Long, facts: FoodFacts) {
        val moment = now()
        offerEach(foodId, facts.per100g, facts.perUnit, facts.gramsPerUnit) { moment }
    }

    /**
     * The three guarded statements: each group lands only when nothing is there or it is at least as
     * credible as what is, and a group that lands is stamped — its own date from [setAt], and the
     * food's `updatedAtMillis` with it — whether or not its figures differ.
     */
    private fun offerEach(
        foodId: Long,
        per100g: PerHundredGrams?,
        perUnit: PerUnit?,
        gramsPerUnit: GramsPerUnit?,
        setAt: (Provenance) -> Long,
    ) {
        per100g?.let { fact ->
            update(foodId) { row ->
                if (!lands(row.per100g?.provenance, fact.provenance)) return@update row
                val at = setAt(fact.provenance)
                row.copy(per100g = fact.copy(provenance = fact.provenance.copy(setAtMillis = at)), updatedAtMillis = at)
            }
        }
        perUnit?.let { fact ->
            update(foodId) { row ->
                if (!lands(row.perUnit?.provenance, fact.provenance)) return@update row
                val at = setAt(fact.provenance)
                row.copy(perUnit = fact.copy(provenance = fact.provenance.copy(setAtMillis = at)), updatedAtMillis = at)
            }
        }
        gramsPerUnit?.let { fact ->
            update(foodId) { row ->
                if (!lands(row.gramsPerUnit?.provenance, fact.provenance)) return@update row
                val at = setAt(fact.provenance)
                row.copy(
                    gramsPerUnit = fact.copy(provenance = fact.provenance.copy(setAtMillis = at)),
                    updatedAtMillis = at,
                )
            }
        }
    }

    /** `WHERE ... (rank IS NULL OR :rank >= rank)`. */
    private fun lands(held: Provenance?, arriving: Provenance): Boolean =
        held == null || arriving.rank >= held.rank

    // --- Names and brands ------------------------------------------------------------------------

    override suspend fun rename(foodId: Long, newName: String): EditResult {
        val displayName = FoodKeys.displayName(newName)
        val nameKey = FoodKeys.nameKey(newName)
        val stored = tables.value.row(foodId) ?: return EditResult.Done
        val brandKey = FoodKeys.brandKey(stored.brand)

        val taken = foodIdNamed(nameKey, brandKey)
        if (taken != null && taken != foodId) {
            return EditResult.Refused(EditRefused.AlreadyAnotherFood(taken, displayName))
        }
        val preferred = shownName(foodId) ?: return EditResult.Done
        // Edited in place, so nothing has to be repointed.
        writeNames(
            tables.value.names.map {
                if (it.id == preferred.id) it.copy(displayName = displayName, nameKey = nameKey) else it
            },
        )
        update(foodId) { it.copy(updatedAtMillis = now()) }
        return EditResult.Done
    }

    override suspend fun setBrand(foodId: Long, brand: String?): EditResult {
        val moment = now()
        tables.value.row(foodId) ?: return EditResult.Done
        val display = brand?.takeIf { it.isNotBlank() }?.let(FoodKeys::displayName) ?: FoodKeys.NO_BRAND
        val brandKey = FoodKeys.brandKey(brand)
        val stored = tables.value.row(foodId)!!
        val oldKey = shownName(foodId)?.brandKey ?: FoodKeys.brandKey(stored.brand)

        if (brandKey == oldKey) {
            update(foodId) { it.copy(brand = display, updatedAtMillis = moment) }
            return EditResult.Done
        }

        // Only the names under the food's own brand move; a name a join brought in keeps its own.
        val moving = tables.value.names.filter { it.foodId == foodId && it.brandKey == oldKey }
        moving.forEach { name ->
            val taken = foodIdNamed(name.nameKey, brandKey)
            if (taken != null && taken != foodId) {
                return EditResult.Refused(EditRefused.AlreadyAnotherFood(taken, name.displayName))
            }
        }
        update(foodId) { it.copy(brand = display, updatedAtMillis = moment) }
        val movingKeys = moving.map { it.nameKey }.toSet()
        writeNames(
            tables.value.names
                // `dropJoinedName`: a joined name already under the new brand goes rather than collide.
                .filterNot {
                    it.foodId == foodId && !it.preferred && it.brandKey == brandKey && it.nameKey in movingKeys
                }
                // `moveNamesToBrand`.
                .map { if (it.foodId == foodId && it.brandKey == oldKey) it.copy(brandKey = brandKey) else it },
        )
        return EditResult.Done
    }

    /**
     * The owner's own hand, by the real repository's plan (D54): a changed group replaces what was
     * there whatever its rank, an emptied one goes, and an unchanged one keeps the provenance it had.
     * Emptying the per-unit group a saved meal counts in is refused by name, as the real one is.
     */
    override suspend fun correct(foodId: Long, facts: FoodFacts): EditResult {
        val moment = now()
        val stored = byId(foodId)
        if (stored != null) {
            val losingUnits = stored.facts.perUnit != null && facts.perUnit == null
            if (losingUnits) {
                val meals = mealsUsing(foodId)
                if (meals.isNotEmpty()) {
                    return EditResult.Refused(EditRefused.NeededBySavedMeals(meals))
                }
            }
        }
        val plan = Correction.plan(stored?.facts, facts)
        if (plan.per100g != Correction.Keep) update(foodId) { it.copy(per100g = null, updatedAtMillis = moment) }
        if (plan.perUnit != Correction.Keep) update(foodId) { it.copy(perUnit = null, updatedAtMillis = moment) }
        if (plan.gramsPerUnit != Correction.Keep) {
            update(foodId) { it.copy(gramsPerUnit = null, updatedAtMillis = moment) }
        }
        offerEach(
            foodId,
            (plan.per100g as? Correction.Replace)?.fact,
            (plan.perUnit as? Correction.Replace)?.fact,
            (plan.gramsPerUnit as? Correction.Replace)?.fact,
        ) { moment }
        return EditResult.Done
    }

    /** All or nothing, as the real one: a refusal or a failure puts every table back as it was. */
    override suspend fun saveForm(
        foodId: Long,
        name: String,
        brand: String?,
        facts: FoodFacts,
    ): EditResult {
        val before = tables.value
        return try {
            val refused = rename(foodId, name).takeIf { it is EditResult.Refused }
                ?: setBrand(foodId, brand).takeIf { it is EditResult.Refused }
                ?: correct(foodId, facts).takeIf { it is EditResult.Refused }
            if (refused != null) tables.value = before
            refused ?: EditResult.Done
        } catch (failure: Exception) {
            tables.value = before
            throw failure
        }
    }

    override suspend fun hide(foodId: Long) {
        update(foodId) { it.copy(hidden = true, updatedAtMillis = now()) }
    }

    override suspend fun unhide(foodId: Long) {
        update(foodId) { it.copy(hidden = false, updatedAtMillis = now()) }
    }

    override suspend fun savedMealsUsing(foodId: Long): List<String> = mealsUsing(foodId)

    override fun observeUse(foodId: Long): Flow<FoodUse> =
        combine(rows?.observeRowCounts() ?: loggedRows, mealsUsingFlow()) { counts, using ->
            FoodUse(logged = counts[foodId] ?: 0, savedMeals = using[foodId].orEmpty())
        }.distinctUntilChanged()

    /**
     * Refused while a saved meal holds it; otherwise the food and its names go (`CASCADE`) and every
     * logged row that pointed at it points at nothing (`SET NULL`), keeping its own name and numbers.
     */
    override suspend fun delete(foodId: Long): EditResult {
        val meals = mealsUsing(foodId)
        if (meals.isNotEmpty()) return EditResult.Refused(EditRefused.UsedBySavedMeals(meals))
        write(
            Tables(
                foods = tables.value.foods.filterNot { it.id == foodId },
                names = tables.value.names.filterNot { it.foodId == foodId },
            ),
        )
        rows?.detachRowsOf(foodId)
        loggedRows.value = loggedRows.value - foodId
        return EditResult.Done
    }

    /**
     * `RoomFoodRepository.merge`, step for step: refused when one meal holds both; otherwise the past
     * rows, the meal parts and the names move across, the food that stays fills only the groups it
     * lacks (each figure keeping its own date), the absorbed food goes, and the one kept is touched.
     */
    override suspend fun merge(winnerId: Long, loserId: Long): EditResult {
        if (winnerId == loserId) return EditResult.Done
        mergeRefusal?.let { return EditResult.Refused(it) }
        meals?.mealsHoldingBoth(winnerId, loserId)?.takeIf { it.isNotEmpty() }?.let {
            return EditResult.Refused(EditRefused.MealsHoldingBoth(it))
        }

        val absorbed = byId(loserId)?.facts

        rows?.movePastRows(winnerId, loserId)
        loggedRows.value[loserId]?.let { count ->
            loggedRows.value = loggedRows.value - loserId + (winnerId to (loggedRows.value[winnerId] ?: 0) + count)
        }
        meals?.moveMealComponents(winnerId, loserId)
        usedBy.value[loserId]?.let { names ->
            usedBy.value = usedBy.value - loserId +
                (winnerId to (usedBy.value[winnerId].orEmpty() + names).distinct().sorted())
        }
        // `moveNames`: every name of the absorbed food is the kept one's, none of them preferred.
        writeNames(
            tables.value.names.map { if (it.foodId == loserId) it.copy(foodId = winnerId, preferred = false) else it },
        )
        absorbed?.let {
            val filling = JoinedFacts.fill(byId(winnerId)?.facts, it)
            offerEach(winnerId, filling.per100g, filling.perUnit, filling.gramsPerUnit) { p -> p.setAtMillis }
        }
        write(tables.value.copy(foods = tables.value.foods.filterNot { it.id == loserId }))
        update(winnerId) { it.copy(updatedAtMillis = now()) }
        return EditResult.Done
    }

    /**
     * The real rule over each food's figures, keeping each food's own stamp so nothing moves up the
     * list for a repair he did not make. A food left knowing nothing stays stored and reads as no
     * food, as the real one does.
     */
    override suspend fun clearImpossibleFigures(): Int {
        var cleared = 0
        write(
            tables.value.copy(
                foods = tables.value.foods.map { row ->
                    val groups = ImpossibleFigures.of(row.asStoredRow())
                    cleared += groups.size
                    row.copy(
                        per100g = row.per100g.takeUnless { ImpossibleFigures.Group.PER_100G in groups },
                        perUnit = row.perUnit.takeUnless { ImpossibleFigures.Group.PER_UNIT in groups },
                        gramsPerUnit = row.gramsPerUnit
                            .takeUnless { ImpossibleFigures.Group.GRAMS_PER_UNIT in groups },
                    )
                },
            ),
        )
        return cleared
    }

    // --- The tables ------------------------------------------------------------------------------

    /** `SELECT foodId FROM food_names WHERE nameKey = :nameKey AND brandKey = :brandKey`. */
    private fun foodIdNamed(nameKey: String, brandKey: String): Long? =
        tables.value.names.firstOrNull { it.nameKey == nameKey && it.brandKey == brandKey }?.foodId

    /** The name a food is shown under: its preferred one, or its oldest. */
    private fun shownName(foodId: Long): NameRow? {
        val mine = tables.value.names.filter { it.foodId == foodId }
        return mine.firstOrNull { it.preferred } ?: mine.minByOrNull { it.id }
    }

    private fun Tables.row(id: Long): Row? = foods.firstOrNull { it.id == id }

    private fun update(id: Long, change: (Row) -> Row) {
        write(tables.value.copy(foods = tables.value.foods.map { if (it.id == id) change(it).copy(id = id) else it }))
    }

    private fun writeNames(names: List<NameRow>) = write(tables.value.copy(names = names))

    /**
     * Every write lands here, and the two unique indices are checked as the database checks them —
     * by refusing the write outright, which Room surfaces as an exception.
     */
    private fun write(next: Tables) {
        // Only a clash this write makes. Seeded foods sharing a name are a state the database could
        // never hold, but a test that seeds one is asking about something else.
        val clashing = clashes(next.names.map { it.nameKey to it.brandKey }) -
            clashes(tables.value.names.map { it.nameKey to it.brandKey })
        clashing.firstOrNull()?.let {
            throw IllegalStateException("UNIQUE constraint failed: food_names.nameKey, food_names.brandKey (${it.first})")
        }
        val barcodes = clashes(next.foods.mapNotNull { it.barcode }) - clashes(tables.value.foods.mapNotNull { it.barcode })
        barcodes.firstOrNull()?.let { throw IllegalStateException("UNIQUE constraint failed: foods.barcode ($it)") }
        tables.value = next
    }

    private fun <K> clashes(keys: List<K>): Set<K> = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

    /** `FoodWithNames.toDomain`: null when it knows neither calories group or has no name. */
    private fun Row.toFood(names: List<NameRow>): Food? {
        val mine = names.filter { it.foodId == id }
        val preferred = mine.firstOrNull { it.preferred } ?: mine.minByOrNull { it.id } ?: return null
        if (per100g == null && perUnit == null) return null
        return Food(
            id = id,
            name = preferred.displayName,
            alsoKnownAs = mine.filter { it.id != preferred.id }.sortedBy { it.id }.map { it.displayName },
            brand = brand.ifBlank { FoodKeys.NO_BRAND },
            barcode = barcode,
            facts = FoodFacts(per100g, perUnit, gramsPerUnit),
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
            hidden = hidden,
        )
    }

    /** `kcalPer100g IS NULL AND gramsPerUnit IS NULL AND unitName = 'portion'`. */
    private fun Row.onlyAPortion(): Boolean =
        per100g == null && gramsPerUnit == null && perUnit?.unitName == FoodFacts.PORTION

    private fun Row.asStoredRow() = FoodEntity(
        id = id,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        kcalPer100g = per100g?.nutrients?.kcal,
        proteinPer100g = per100g?.nutrients?.proteinG,
        carbsPer100g = per100g?.nutrients?.carbsG,
        fatPer100g = per100g?.nutrients?.fatG,
        kcalPerUnit = perUnit?.nutrients?.kcal,
        proteinPerUnit = perUnit?.nutrients?.proteinG,
        carbsPerUnit = perUnit?.nutrients?.carbsG,
        fatPerUnit = perUnit?.nutrients?.fatG,
        gramsPerUnit = gramsPerUnit?.grams,
    )

    /**
     * The seeded foods as the database would hold them: ids from 1 in the order given, each food's
     * name preferred and its other names as aliases, all under the food's own brand.
     */
    private fun seeded(initial: List<Food>): Tables {
        val foods = initial.mapIndexed { at, food ->
            Row(
                id = at + 1L,
                brand = food.brand,
                barcode = food.barcode,
                per100g = food.facts.per100g,
                perUnit = food.facts.perUnit,
                gramsPerUnit = food.facts.gramsPerUnit,
                createdAtMillis = food.createdAtMillis,
                updatedAtMillis = food.updatedAtMillis,
                hidden = food.hidden,
            )
        }
        val names = initial.flatMapIndexed { at, food ->
            food.everyName.mapIndexed { which, name ->
                NameRow(
                    id = nextNameId++,
                    foodId = at + 1L,
                    displayName = name,
                    nameKey = FoodKeys.nameKey(name),
                    brandKey = FoodKeys.brandKey(food.brand),
                    preferred = which == 0,
                )
            }
        }
        return Tables(foods, names)
    }
}

/** A clock that counts up from [start], one per reading — ordered, and never a tie by accident. */
class CountingClock(private var start: Long = 1L) : Now {
    override fun invoke(): Long = start++
}

/** A food knowing what 100 grams of it are worth, for a test that does not care which numbers. */
fun aFood(
    name: String = "Yoghurt",
    facts: FoodFacts = FoodFacts(per100g = aPer100g()),
    updatedAtMillis: Long = 0,
) = Food(name = name, facts = facts, updatedAtMillis = updatedAtMillis)

fun aPer100g(kcal: Double = 72.0) = PerHundredGrams(
    Nutrients(kcal, 4.0, 6.0, 2.0),
    Provenance(Source.TYPED, null, setAtMillis = 0),
)

fun aPerUnit(unitName: String = "bar", kcal: Double = 190.0) = PerUnit(
    unitName,
    Nutrients(kcal, 15.0, 17.0, 6.0),
    Provenance(Source.TYPED, null, setAtMillis = 0),
)

fun weighing(grams: Double = 45.0) = GramsPerUnit(
    grams,
    Provenance(Source.TYPED, null, setAtMillis = 0),
)
