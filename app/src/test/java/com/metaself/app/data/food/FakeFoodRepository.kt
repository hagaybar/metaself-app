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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * What the day's logged rows tell the food tables — the part of the database the offered list's
 * query joins against. Implemented by the day's stand-in (`InMemoryMealRepository`), and wired in
 * with [FakeFoodRepository.linkedTo] where a test has both.
 */
interface LoggedRowsOfFoods {
    /** Food id → the latest `loggedAtMillis` of a meal holding a row that points at it. */
    fun observeLatestLogging(): Flow<Map<Long, Long>>
}

/**
 * The food repository, in memory, for screens and view models under test.
 *
 * Faithful about the two things a screen can actually get wrong if this lies: **the identity rule**,
 * so a test cannot accidentally show a duplicate the real one would have merged, and **the
 * ranking**, so a test cannot show a guess overwriting a number the owner typed. Everything else is
 * the simplest thing that behaves.
 *
 * **And about order, which a walk reads off the top of every list** (public issue #6). [observeAll]
 * is `updatedAtMillis DESC, id DESC`, as `FoodDao.observeAll`; [observeOffered] is the later of that
 * and the food's latest logging, then `id DESC`, as `FoodDao.observeOffered`. Every write the real
 * statements stamp is stamped here from [now], and nothing else is: a repair of impossible figures
 * keeps the food's stamp.
 *
 * It is not a substitute for `RoomFoodRepositoryTest`, which is where the real statements and the
 * real indices are exercised, and which runs in CI.
 *
 * @param now the clock every write is stamped from. The default counts up from 1, one tick per
 *   reading, so an order decided by stamps is never a tie by accident. A walk puts every stand-in on
 *   its one clock with [onClock].
 */
class FakeFoodRepository(
    initial: List<Food> = emptyList(),
    private var now: Now = CountingClock(),
) : FoodRepository {

    private val foods = MutableStateFlow(initial.mapIndexed { at, food -> food.copy(id = at + 1L) })
    private var nextId: Long = initial.size + 1L

    /** Every food it holds, for a test that wants to assert on the store rather than the screen. */
    val current: List<Food> get() = foods.value

    /** Put this store on [clock], so its stamps and the rest of a walk's compare as one timeline. */
    fun onClock(clock: Now) = apply { now = clock }

    private var rows: LoggedRowsOfFoods? = null

    /** Wire in the day's rows, so the offered list is ordered by the last logging as well. */
    fun linkedTo(rows: LoggedRowsOfFoods?) = apply { this.rows = rows }

    private var mergeRefusal: EditRefused? = null

    /**
     * Make the next join refuse, with this reason.
     *
     * The one refusal the real repository gives that nothing else in this fake can produce: it is
     * decided by a query over the saved meals, which this fake has no sight of. Without a switch the
     * screen's refusal branch — the pair, the question and the reason all left standing — would be
     * verified only by reading the code.
     */
    fun refusesToMerge(why: EditRefused) = apply { mergeRefusal = why }

    private val usedBy = MutableStateFlow<Map<Long, List<String>>>(emptyMap())

    /**
     * Make this food one that these saved meals use.
     *
     * The other refusal decided by a query over the saved meals, which this fake has no sight of.
     * Both [savedMealsUsing] and [delete] answer from it, as the real repository answers both from
     * one query — so a test that switches it on after the owner was asked reaches the real late
     * refusal, rather than a stand-in that accepts every delete. [observeUse] says it again when it
     * changes, as the real one's meals are observed.
     */
    fun usedBySavedMeals(foodId: Long, vararg meals: String) =
        apply { usedBy.value = usedBy.value + (foodId to meals.toList()) }

    private val loggedRows = MutableStateFlow<Map<Long, Int>>(emptyMap())

    /**
     * Make this many logged rows point at this food.
     *
     * The logged rows live in the day's tables, which this fake has no sight of; this stands in for
     * them. [observeUse] says it again when it changes, as the real count is observed.
     */
    fun logged(foodId: Long, rows: Int) =
        apply { loggedRows.value = loggedRows.value + (foodId to rows) }

    /** `FoodDao.observeOffered`: not hidden, by the later of edited and last logged, then id. */
    override fun observeOffered(): Flow<List<Food>> =
        combine(foods, rows?.observeLatestLogging() ?: flowOf(emptyMap())) { all, latest ->
            all.filterNot { it.hidden }.sortedWith(
                compareByDescending<Food> { maxOf(it.updatedAtMillis, latest[it.id] ?: 0L) }
                    .thenByDescending { it.id },
            )
        }

    /** `FoodDao.observeAll`: `ORDER BY updatedAtMillis DESC, id DESC`. */
    override fun observeAll(): Flow<List<Food>> = foods.map { all ->
        all.sortedWith(compareByDescending<Food> { it.updatedAtMillis }.thenByDescending { it.id })
    }

    override fun observeOnlyAPortionCount(): Flow<Int> =
        foods.map { all -> all.count { it.facts.onlyAPortion } }

    override fun observeOnlyAPortion(): Flow<List<Food>> =
        // `ORDER BY updatedAtMillis DESC` leaves ties to SQLite; kept in id order here.
        foods.map { all -> all.filter { it.facts.onlyAPortion }.sortedByDescending { it.updatedAtMillis } }

    override suspend fun byId(id: Long): Food? = foods.value.firstOrNull { it.id == id }

    override suspend fun foodIdsNamed(name: String): List<Long> {
        val key = FoodKeys.nameKey(name)
        return foods.value.filter { food -> food.everyName.any { FoodKeys.nameKey(it) == key } }
            .map { it.id }
    }

    override suspend fun byBarcode(barcode: String): Food? =
        foods.value.firstOrNull { it.barcode == barcode }

    override suspend fun findOrCreate(
        name: String,
        brand: String?,
        facts: FoodFacts,
        barcode: String?,
    ): FoundOrCreated {
        val nameKey = FoodKeys.nameKey(name)
        val brandKey = FoodKeys.brandKey(brand)

        val existing = barcode?.let { code -> foods.value.firstOrNull { it.barcode == code } }
            ?: foods.value.firstOrNull { food ->
                food.everyName.any { FoodKeys.nameKey(it) == nameKey } &&
                    FoodKeys.brandKey(food.brand) == brandKey
            }

        if (existing != null) {
            // What the food held a moment ago, reported and not judged — exactly as the real
            // repository reports its snapshot. It already holds `existing`, so this costs nothing.
            val before = existing.facts
            val moment = now()
            val offered = offer(existing.facts, facts, moment)
            val learnsBarcode = barcode != null && existing.barcode == null
            if (lands(existing.facts, facts) || learnsBarcode) {
                replace(existing.id, moment) {
                    it.copy(facts = offered, barcode = it.barcode ?: barcode)
                }
            }
            return FoundOrCreated(byId(existing.id)!!, wasCreated = false, before = before)
        }

        val moment = now()
        val made = Food(
            id = nextId++,
            name = FoodKeys.displayName(name),
            brand = brand?.takeIf { it.isNotBlank() }?.let(FoodKeys::displayName) ?: FoodKeys.NO_BRAND,
            barcode = barcode,
            facts = stamped(facts, moment),
            createdAtMillis = moment,
            updatedAtMillis = moment,
        )
        foods.value = foods.value + made
        // A food this call made held nothing a moment ago.
        return FoundOrCreated(made, wasCreated = true, before = null)
    }

    override suspend fun offerFacts(foodId: Long, facts: FoodFacts) {
        val food = byId(foodId) ?: return
        val moment = now()
        if (lands(food.facts, facts)) replace(foodId, moment) { it.copy(facts = offer(it.facts, facts, moment)) }
    }

    override suspend fun rename(foodId: Long, newName: String): EditResult {
        val nameKey = FoodKeys.nameKey(newName)
        val food = byId(foodId) ?: return EditResult.Done
        val clash = foods.value.firstOrNull { other ->
            other.id != foodId &&
                FoodKeys.brandKey(other.brand) == FoodKeys.brandKey(food.brand) &&
                other.everyName.any { FoodKeys.nameKey(it) == nameKey }
        }
        if (clash != null) {
            return EditResult.Refused(EditRefused.AlreadyAnotherFood(clash.id, clash.name))
        }
        // `touch`: a rename is an edit, and sends the food up the list.
        replace(foodId, now()) { it.copy(name = FoodKeys.displayName(newName)) }
        return EditResult.Done
    }

    override suspend fun setBrand(foodId: Long, brand: String?): EditResult {
        val food = byId(foodId) ?: return EditResult.Done
        val brandKey = FoodKeys.brandKey(brand)
        val clash = foods.value.firstOrNull { other ->
            other.id != foodId && FoodKeys.brandKey(other.brand) == brandKey &&
                other.everyName.any { name ->
                    food.everyName.any { FoodKeys.nameKey(it) == FoodKeys.nameKey(name) }
                }
        }
        if (clash != null) {
            return EditResult.Refused(EditRefused.AlreadyAnotherFood(clash.id, clash.name))
        }
        // Stamped whether or not the brand changed, as `FoodDao.setBrand` is on every Save.
        replace(foodId, now()) {
            it.copy(brand = brand?.takeIf { b -> b.isNotBlank() } ?: FoodKeys.NO_BRAND)
        }
        return EditResult.Done
    }

    /**
     * The owner's own hand, by the real repository's plan (D54): a changed group replaces what was
     * there whatever its rank, an emptied one goes, and an unchanged one keeps the provenance it had
     * — so a view model test sees the source the real one stores.
     */
    override suspend fun correct(foodId: Long, facts: FoodFacts): EditResult {
        val food = byId(foodId) ?: return EditResult.Done
        val moment = now()
        val plan = Correction.plan(food.facts, facts)
        // A group left as it was gets no statement, so a correction that changes nothing stamps
        // nothing; every other group is cleared or written, and each of those stamps the food.
        if (plan.per100g == Correction.Keep && plan.perUnit == Correction.Keep &&
            plan.gramsPerUnit == Correction.Keep
        ) {
            return EditResult.Done
        }
        replace(foodId, moment) {
            it.copy(
                facts = FoodFacts(
                    per100g = applied(plan.per100g, food.facts.per100g, moment),
                    perUnit = applied(plan.perUnit, food.facts.perUnit, moment),
                    gramsPerUnit = applied(plan.gramsPerUnit, food.facts.gramsPerUnit, moment),
                ),
            )
        }
        return EditResult.Done
    }

    private fun <T> applied(step: Correction.Step<T>, held: T?, moment: Long): T? = when (step) {
        Correction.Keep -> held
        Correction.Clear -> null
        is Correction.Replace -> dated(step.fact, moment)
    }

    /** All or nothing, as the real one: a refusal puts every food back as it was. */
    override suspend fun saveForm(
        foodId: Long,
        name: String,
        brand: String?,
        facts: FoodFacts,
    ): EditResult {
        val before = foods.value
        val refused = rename(foodId, name).takeIf { it is EditResult.Refused }
            ?: setBrand(foodId, brand).takeIf { it is EditResult.Refused }
            ?: correct(foodId, facts).takeIf { it is EditResult.Refused }
        if (refused != null) foods.value = before
        return refused ?: EditResult.Done
    }

    override suspend fun hide(foodId: Long) {
        replace(foodId, now()) { it.copy(hidden = true) }
    }

    override suspend fun unhide(foodId: Long) {
        replace(foodId, now()) { it.copy(hidden = false) }
    }

    override suspend fun savedMealsUsing(foodId: Long): List<String> = usedBy.value[foodId].orEmpty()

    override fun observeUse(foodId: Long): Flow<FoodUse> =
        combine(loggedRows, usedBy) { rows, meals ->
            FoodUse(logged = rows[foodId] ?: 0, savedMeals = meals[foodId].orEmpty().sorted())
        }.distinctUntilChanged()

    /** The real delete leaves the rows attached to no food, so its id counts nothing afterwards. */
    override suspend fun delete(foodId: Long): EditResult {
        usedBy.value[foodId]?.takeIf { it.isNotEmpty() }?.let {
            return EditResult.Refused(EditRefused.UsedBySavedMeals(it))
        }
        foods.value = foods.value.filterNot { it.id == foodId }
        loggedRows.value = loggedRows.value - foodId
        return EditResult.Done
    }

    override suspend fun merge(winnerId: Long, loserId: Long): EditResult {
        mergeRefusal?.let { return EditResult.Refused(it) }
        if (winnerId == loserId) return EditResult.Done
        val loser = byId(loserId) ?: return EditResult.Done
        // The winner keeps what it holds and fills only its blanks, by the rule the real merge uses,
        // each filled figure keeping its own date; then the winner is touched, as the real one is.
        replace(winnerId, now()) { winner ->
            val filling = JoinedFacts.fill(winner.facts, loser.facts)
            winner.copy(
                alsoKnownAs = winner.alsoKnownAs + loser.everyName,
                facts = FoodFacts(
                    per100g = winner.facts.per100g ?: filling.per100g,
                    perUnit = winner.facts.perUnit ?: filling.perUnit,
                    gramsPerUnit = winner.facts.gramsPerUnit ?: filling.gramsPerUnit,
                ),
            )
        }
        foods.value = foods.value.filterNot { it.id == loserId }
        // The real merge moves the loser's meal components onto the winner, so the meals that used
        // the loser now use the winner — and a delete of the winner is refused for them.
        usedBy.value[loserId]?.let { meals ->
            usedBy.value = usedBy.value - loserId +
                (winnerId to (usedBy.value[winnerId].orEmpty() + meals).distinct())
        }
        // And its logged rows (`movePastRows`), so the winner counts both.
        loggedRows.value[loserId]?.let { rows ->
            val together = (loggedRows.value[winnerId] ?: 0) + rows
            loggedRows.value = loggedRows.value - loserId + (winnerId to together)
        }
        return EditResult.Done
    }

    /**
     * The real rule over each food's figures. A food left knowing nothing leaves the list, as the
     * real one reads as no food; this fake cannot hold a food with no figures to bring back later.
     */
    override suspend fun clearImpossibleFigures(): Int {
        var cleared = 0
        foods.value = foods.value.mapNotNull { food ->
            val groups = ImpossibleFigures.of(food.asStoredRow())
            cleared += groups.size
            val per100g = food.facts.per100g.takeUnless { ImpossibleFigures.Group.PER_100G in groups }
            val perUnit = food.facts.perUnit.takeUnless { ImpossibleFigures.Group.PER_UNIT in groups }
            val weight = food.facts.gramsPerUnit
                .takeUnless { ImpossibleFigures.Group.GRAMS_PER_UNIT in groups }
            when {
                groups.isEmpty() -> food
                per100g == null && perUnit == null -> null
                else -> food.copy(facts = FoodFacts(per100g, perUnit, weight))
            }
        }
        return cleared
    }

    private fun Food.asStoredRow() = FoodEntity(
        id = id,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        kcalPer100g = facts.per100g?.nutrients?.kcal,
        proteinPer100g = facts.per100g?.nutrients?.proteinG,
        carbsPer100g = facts.per100g?.nutrients?.carbsG,
        fatPer100g = facts.per100g?.nutrients?.fatG,
        kcalPerUnit = facts.perUnit?.nutrients?.kcal,
        proteinPerUnit = facts.perUnit?.nutrients?.proteinG,
        carbsPerUnit = facts.perUnit?.nutrients?.carbsG,
        fatPerUnit = facts.perUnit?.nutrients?.fatG,
        gramsPerUnit = facts.gramsPerUnit?.grams,
    )

    /** Changes one food; [stamp], when given, is written to its `updatedAtMillis` as well. */
    private fun replace(id: Long, stamp: Long? = null, change: (Food) -> Food) {
        foods.value = foods.value.map {
            if (it.id != id) return@map it
            val changed = change(it).copy(id = id)
            if (stamp == null) changed else changed.copy(updatedAtMillis = stamp)
        }
    }

    /**
     * The ranking, as the guarded statements apply it: a fact lands only when it is at least as
     * credible as what is already there, and each fact is decided on its own. A fact that lands is
     * dated [moment], as the statement writes `nowMillis` into its date column.
     */
    private fun offer(existing: FoodFacts, incoming: FoodFacts, moment: Long): FoodFacts = FoodFacts(
        per100g = better(existing.per100g, incoming.per100g?.let { dated(it, moment) }) { a, b ->
            a.provenance.rank to b.provenance.rank
        },
        perUnit = better(existing.perUnit, incoming.perUnit?.let { dated(it, moment) }) { a, b ->
            a.provenance.rank to b.provenance.rank
        },
        gramsPerUnit = better(existing.gramsPerUnit, incoming.gramsPerUnit?.let { dated(it, moment) }) { a, b ->
            a.provenance.rank to b.provenance.rank
        },
    )

    /**
     * Whether any guarded statement would write a row: some group arrives that is at least as
     * credible as what is there. Each one that does stamps the food, identical figures included.
     */
    private fun lands(existing: FoodFacts, incoming: FoodFacts): Boolean =
        landsOver(existing.per100g?.provenance, incoming.per100g?.provenance) ||
            landsOver(existing.perUnit?.provenance, incoming.perUnit?.provenance) ||
            landsOver(existing.gramsPerUnit?.provenance, incoming.gramsPerUnit?.provenance)

    private fun landsOver(held: Provenance?, arriving: Provenance?): Boolean =
        arriving != null && (held == null || arriving.rank >= held.rank)

    private fun stamped(facts: FoodFacts, moment: Long) = FoodFacts(
        per100g = facts.per100g?.let { dated(it, moment) },
        perUnit = facts.perUnit?.let { dated(it, moment) },
        gramsPerUnit = facts.gramsPerUnit?.let { dated(it, moment) },
    )

    /** A fact with its date column written, as every write of a group writes it. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> dated(fact: T, moment: Long): T = when (fact) {
        is PerHundredGrams -> fact.copy(provenance = fact.provenance.copy(setAtMillis = moment)) as T
        is PerUnit -> fact.copy(provenance = fact.provenance.copy(setAtMillis = moment)) as T
        is GramsPerUnit -> fact.copy(provenance = fact.provenance.copy(setAtMillis = moment)) as T
        else -> fact
    }

    private fun <T> better(existing: T?, incoming: T?, ranks: (T, T) -> Pair<Int, Int>): T? {
        if (incoming == null) return existing
        if (existing == null) return incoming
        val (mine, theirs) = ranks(existing, incoming)
        return if (theirs >= mine) incoming else existing
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
