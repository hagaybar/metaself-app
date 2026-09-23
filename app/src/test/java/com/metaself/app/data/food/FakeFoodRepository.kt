package com.metaself.app.data.food

import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodKeys
import com.metaself.app.domain.food.GramsPerUnit
import com.metaself.app.domain.food.JoinedFacts
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.PerUnit
import com.metaself.app.domain.food.Provenance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The food repository, in memory, for screens and view models under test.
 *
 * Faithful about the two things a screen can actually get wrong if this lies: **the identity rule**,
 * so a test cannot accidentally show a duplicate the real one would have merged, and **the
 * ranking**, so a test cannot show a guess overwriting a number the owner typed. Everything else is
 * the simplest thing that behaves.
 *
 * It is not a substitute for `RoomFoodRepositoryTest`, which is where the real statements and the
 * real indices are exercised, and which runs in CI.
 */
class FakeFoodRepository(initial: List<Food> = emptyList()) : FoodRepository {

    private val foods = MutableStateFlow(initial.mapIndexed { at, food -> food.copy(id = at + 1L) })
    private var nextId: Long = initial.size + 1L

    /** Every food it holds, for a test that wants to assert on the store rather than the screen. */
    val current: List<Food> get() = foods.value

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

    private val usedBy = mutableMapOf<Long, List<String>>()

    /**
     * Make this food one that these saved meals use.
     *
     * The other refusal decided by a query over the saved meals, which this fake has no sight of.
     * Both [savedMealsUsing] and [delete] answer from it, as the real repository answers both from
     * one query — so a test that switches it on after the owner was asked reaches the real late
     * refusal, rather than a stand-in that accepts every delete.
     */
    fun usedBySavedMeals(foodId: Long, vararg meals: String) =
        apply { usedBy[foodId] = meals.toList() }

    override fun observeOffered(): Flow<List<Food>> = foods.map { all ->
        all.filterNot { it.hidden }.sortedByDescending { it.updatedAtMillis }
    }

    override fun observeAll(): Flow<List<Food>> = foods

    override fun observeOnlyAPortionCount(): Flow<Int> =
        foods.map { all -> all.count { it.facts.onlyAPortion } }

    override fun observeOnlyAPortion(): Flow<List<Food>> =
        foods.map { all -> all.filter { it.facts.onlyAPortion } }

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
            replace(existing.id) { it.copy(facts = offer(it.facts, facts), barcode = it.barcode ?: barcode) }
            return FoundOrCreated(byId(existing.id)!!, wasCreated = false, before = before)
        }

        val made = Food(
            id = nextId++,
            name = FoodKeys.displayName(name),
            brand = brand?.takeIf { it.isNotBlank() }?.let(FoodKeys::displayName) ?: FoodKeys.NO_BRAND,
            barcode = barcode,
            facts = facts,
        )
        foods.value = foods.value + made
        // A food this call made held nothing a moment ago.
        return FoundOrCreated(made, wasCreated = true, before = null)
    }

    override suspend fun offerFacts(foodId: Long, facts: FoodFacts) {
        replace(foodId) { it.copy(facts = offer(it.facts, facts)) }
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
        replace(foodId) { it.copy(name = FoodKeys.displayName(newName)) }
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
        replace(foodId) {
            it.copy(brand = brand?.takeIf { b -> b.isNotBlank() } ?: FoodKeys.NO_BRAND)
        }
        return EditResult.Done
    }

    /** The owner's own hand, so it replaces rather than being offered against the ranking. */
    override suspend fun correct(foodId: Long, facts: FoodFacts): EditResult {
        replace(foodId) { it.copy(facts = facts) }
        return EditResult.Done
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
        replace(foodId) { it.copy(hidden = true) }
    }

    override suspend fun unhide(foodId: Long) {
        replace(foodId) { it.copy(hidden = false) }
    }

    override suspend fun savedMealsUsing(foodId: Long): List<String> = usedBy[foodId].orEmpty()

    override suspend fun delete(foodId: Long): EditResult {
        usedBy[foodId]?.takeIf { it.isNotEmpty() }?.let {
            return EditResult.Refused(EditRefused.UsedBySavedMeals(it))
        }
        foods.value = foods.value.filterNot { it.id == foodId }
        return EditResult.Done
    }

    override suspend fun merge(winnerId: Long, loserId: Long): EditResult {
        mergeRefusal?.let { return EditResult.Refused(it) }
        if (winnerId == loserId) return EditResult.Done
        val loser = byId(loserId) ?: return EditResult.Done
        // The winner keeps what it holds and fills only its blanks, by the rule the real merge uses.
        replace(winnerId) { winner ->
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
        usedBy.remove(loserId)?.let { meals ->
            usedBy[winnerId] = (usedBy[winnerId].orEmpty() + meals).distinct()
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

    private fun replace(id: Long, change: (Food) -> Food) {
        foods.value = foods.value.map { if (it.id == id) change(it).copy(id = id) else it }
    }

    /**
     * The ranking, as the guarded statements apply it: a fact lands only when it is at least as
     * credible as what is already there, and each fact is decided on its own.
     */
    private fun offer(existing: FoodFacts, incoming: FoodFacts): FoodFacts = FoodFacts(
        per100g = better(existing.per100g, incoming.per100g) { a, b -> a.provenance.rank to b.provenance.rank },
        perUnit = better(existing.perUnit, incoming.perUnit) { a, b -> a.provenance.rank to b.provenance.rank },
        gramsPerUnit = better(existing.gramsPerUnit, incoming.gramsPerUnit) { a, b ->
            a.provenance.rank to b.provenance.rank
        },
    )

    private fun <T> better(existing: T?, incoming: T?, ranks: (T, T) -> Pair<Int, Int>): T? {
        if (incoming == null) return existing
        if (existing == null) return incoming
        val (mine, theirs) = ranks(existing, incoming)
        return if (theirs >= mine) incoming else existing
    }
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
