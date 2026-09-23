package com.metaself.app.data.food

import androidx.room.withTransaction
import com.metaself.app.data.day.MetaSelfDatabase
import com.metaself.app.data.time.Now
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The one door, over Room.
 *
 * Everything that changes identity — creating, renaming, re-branding, merging — runs inside a
 * transaction, because each of them touches the food and its names together and a reader that saw
 * half of it would see a food with no name or a name pointing at nothing.
 */
class RoomFoodRepository @Inject constructor(
    private val database: MetaSelfDatabase,
    private val dao: FoodDao,
    private val now: Now,
) : FoodRepository {

    override fun observeOffered(): Flow<List<Food>> =
        dao.observeOffered().map { rows -> rows.mapNotNull { it.toDomain() } }

    override fun observeAll(): Flow<List<Food>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toDomain() } }

    override fun observeOnlyAPortionCount(): Flow<Int> = dao.observeOnlyAPortionCount()

    override fun observeOnlyAPortion(): Flow<List<Food>> =
        dao.observeOnlyAPortion().map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun byId(id: Long): Food? = dao.byId(id)?.toDomain()

    override suspend fun byBarcode(barcode: String): Food? = dao.byBarcode(barcode)?.toDomain()

    override suspend fun findOrCreate(
        name: String,
        brand: String?,
        facts: FoodFacts,
        barcode: String?,
    ): FoundOrCreated = database.withTransaction {
        val moment = now()
        val displayName = FoodKeys.displayName(name)
        val nameKey = FoodKeys.nameKey(name)
        val brandKey = FoodKeys.brandKey(brand)

        // A packet identifies a food more exactly than its name does, so it is asked first: the
        // same bar described in words last week and scanned today is one food, and the scan is what
        // knows that.
        val existingId = barcode?.let { dao.byBarcode(it)?.food?.id }
            ?: dao.foodIdNamed(nameKey, brandKey)

        if (existingId != null) {
            // What the food held a moment ago, read immediately before the offer and inside the
            // transaction that then makes it, so nothing can move between the two reads. Reported,
            // never judged: what counts as a replacement is decided by one pure comparison
            // (`ReplacedFacts`), and the DAO's row count could not answer it anyway — an identical
            // figure offered again gets through the `>=` guard and reports one row written (D45,
            // issue #13).
            // One read, held, so the ordering — read, THEN offer — is visible rather than implied
            // by where two identical calls happen to sit.
            val stored = dao.byId(existingId)
            val before = stored?.toDomain()?.facts
            offerEvery(existingId, facts, moment)
            // A food described before and scanned now learns its barcode rather than becoming a
            // second food. The collision the unique index would otherwise catch is resolved here,
            // which is the only place that can resolve it without guessing. Read from the same
            // snapshot: offering figures cannot touch the barcode column.
            if (barcode != null && stored?.food?.barcode == null) {
                dao.setBarcode(existingId, barcode, moment)
            }
            val found = requireNotNull(dao.byId(existingId)?.toDomain()) {
                "a food that exists must read back"
            }
            // The "after" side is the read-back above, which already happens after `setBarcode` —
            // harmless, since learning a barcode touches no figure column.
            return@withTransaction FoundOrCreated(found, wasCreated = false, before = before)
        }

        val foodId = dao.insertFood(
            FoodEntity(
                brand = brand?.takeIf { it.isNotBlank() } ?: FoodKeys.NO_BRAND,
                barcode = barcode,
                createdAtMillis = moment,
                updatedAtMillis = moment,
            ),
        )
        dao.insertName(
            FoodNameEntity(
                foodId = foodId,
                displayName = displayName,
                nameKey = nameKey,
                brandKey = brandKey,
                isPreferred = true,
                addedAtMillis = moment,
            ),
        )
        // A new food starts knowing nothing, so every fact the caller brought is a first learning
        // and the `IS NULL` arm of each guarded statement lets it land.
        offerEvery(foodId, facts, moment)

        FoundOrCreated(
            requireNotNull(dao.byId(foodId)?.toDomain()) { "a food just created must read back" },
            wasCreated = true,
            // A food this call made held nothing a moment ago, so there is nothing to compare.
            before = null,
        )
    }

    override suspend fun offerFacts(foodId: Long, facts: FoodFacts) {
        database.withTransaction { offerEvery(foodId, facts, now()) }
    }

    /**
     * Each fact offered through its own guarded statement, and no statement touching a column
     * belonging to another fact.
     *
     * This is what lets a scan win the argument about what 100 grams are worth while having no say
     * at all about what one bar weighs.
     */
    private suspend fun offerEvery(foodId: Long, facts: FoodFacts, now: Long) {
        facts.per100g?.let { fact ->
            dao.writePer100g(
                id = foodId,
                kcal = fact.nutrients.kcal,
                protein = fact.nutrients.proteinG,
                carbs = fact.nutrients.carbsG,
                fat = fact.nutrients.fatG,
                source = fact.provenance.source.name,
                rank = fact.provenance.rank,
                confidence = fact.provenance.confidence?.name,
                nowMillis = now,
            )
        }
        facts.perUnit?.let { fact ->
            dao.writePerUnit(
                id = foodId,
                unitName = fact.unitName,
                kcal = fact.nutrients.kcal,
                protein = fact.nutrients.proteinG,
                carbs = fact.nutrients.carbsG,
                fat = fact.nutrients.fatG,
                source = fact.provenance.source.name,
                rank = fact.provenance.rank,
                confidence = fact.provenance.confidence?.name,
                nowMillis = now,
            )
        }
        facts.gramsPerUnit?.let { fact ->
            dao.writeGramsPerUnit(
                id = foodId,
                grams = fact.grams,
                source = fact.provenance.source.name,
                rank = fact.provenance.rank,
                confidence = fact.provenance.confidence?.name,
                nowMillis = now,
            )
        }
    }

    override suspend fun rename(foodId: Long, newName: String): EditResult =
        database.withTransaction {
            val displayName = FoodKeys.displayName(newName)
            val nameKey = FoodKeys.nameKey(newName)
            val stored = dao.byId(foodId) ?: return@withTransaction EditResult.Done
            val brandKey = FoodKeys.brandKey(stored.food.brand)

            val taken = dao.foodIdNamed(nameKey, brandKey)
            if (taken != null && taken != foodId) {
                return@withTransaction EditResult.Refused(
                    EditRefused.AlreadyAnotherFood(taken, displayName),
                )
            }
            val preferred = stored.names.firstOrNull { it.isPreferred }
                ?: stored.names.minByOrNull { it.addedAtMillis }
                ?: return@withTransaction EditResult.Done

            // The name row is edited in place rather than replaced, so nothing anywhere has to be
            // repointed — which is the mechanism by which every past day re-labels without a single
            // stored number moving.
            dao.renameNameRow(preferred.id, displayName, nameKey)
            dao.touch(foodId, now())
            EditResult.Done
        }

    override suspend fun setBrand(foodId: Long, brand: String?): EditResult =
        database.withTransaction {
            val moment = now()
            val stored = dao.byId(foodId) ?: return@withTransaction EditResult.Done
            val display = brand?.takeIf { it.isNotBlank() }?.let(FoodKeys::displayName)
                ?: FoodKeys.NO_BRAND
            val brandKey = FoodKeys.brandKey(brand)
            // The brand the food's own names are under, read off the name shown rather than
            // recomputed from the display brand, so it is exactly what the rows hold.
            val shown = stored.names.firstOrNull { it.isPreferred }
                ?: stored.names.minByOrNull { it.addedAtMillis }
            val oldKey = shown?.brandKey ?: FoodKeys.brandKey(stored.food.brand)

            dao.setBrand(foodId, display, moment)
            // Saving the form sets the brand every time, changed or not. Unchanged, no name moves.
            if (brandKey == oldKey) return@withTransaction EditResult.Done

            // Only the names under the food's own brand move. A name a join brought in keeps the
            // brand it came with, because that is what lets the next log of the absorbed food find
            // this one; rewriting it as well put two rows on one (name, brand) whenever the two
            // joined foods shared a name, and the unique index refused the Save outright.
            val moving = stored.names.filter { it.brandKey == oldKey }
            moving.forEach { name ->
                val taken = dao.foodIdNamed(name.nameKey, brandKey)
                if (taken != null && taken != foodId) {
                    return@withTransaction EditResult.Refused(
                        EditRefused.AlreadyAnotherFood(taken, name.displayName),
                    )
                }
            }
            // A joined name already under the new brand is the same identity the moving name is
            // about to take, so it goes rather than collide. Nothing points at a name row.
            moving.forEach { name -> dao.dropJoinedName(foodId, name.nameKey, brandKey) }
            dao.moveNamesToBrand(foodId, oldKey, brandKey)
            EditResult.Done
        }

    override suspend fun correct(foodId: Long, facts: FoodFacts): EditResult =
        database.withTransaction {
            val moment = now()

            // Emptying a group a saved meal counts in is refused by name. This is the one residue
            // of the old guard column: a component saying UNITS against a food with no per-unit
            // numbers could not cost itself.
            val stored = dao.byId(foodId)?.toDomain()
            if (stored != null) {
                val losingUnits = stored.facts.perUnit != null && facts.perUnit == null
                if (losingUnits) {
                    val meals = dao.mealsUsing(foodId)
                    if (meals.isNotEmpty()) {
                        return@withTransaction EditResult.Refused(
                            EditRefused.NeededBySavedMeals(meals),
                        )
                    }
                }
            }

            // Clear first, then write, so that a correction can genuinely REMOVE a number. The
            // guarded statements only ever raise what is known; the owner's own hand is the one
            // thing allowed to lower it, because the ranking exists to protect him from a guess,
            // not from himself.
            dao.clearPer100g(foodId, moment)
            dao.clearPerUnit(foodId, moment)
            dao.clearGramsPerUnit(foodId, moment)
            offerEvery(foodId, facts, moment)
            EditResult.Done
        }

    /**
     * One transaction around the three, relying on [withTransaction] being reentrant: each step's
     * own transaction joins this one rather than committing on its own. A refusal is RETURNED from
     * inside, which would commit, so it is thrown instead to make Room roll back — including the
     * brand column [setBrand] writes before it checks for a clash — and turned back into the result
     * outside.
     */
    override suspend fun saveForm(
        foodId: Long,
        name: String,
        brand: String?,
        facts: FoodFacts,
    ): EditResult = try {
        database.withTransaction {
            listOf(
                suspend { rename(foodId, name) },
                suspend { setBrand(foodId, brand) },
                suspend { correct(foodId, facts) },
            ).forEach { step ->
                val result = step()
                if (result is EditResult.Refused) throw RolledBack(result)
            }
            EditResult.Done
        }
    } catch (rolledBack: RolledBack) {
        rolledBack.refusal
    }

    /** Carries a refusal out of [saveForm]'s transaction, so the transaction is not committed. */
    private class RolledBack(val refusal: EditResult.Refused) : Exception()

    override suspend fun hide(foodId: Long) {
        dao.setHidden(foodId, now(), now())
    }

    override suspend fun unhide(foodId: Long) {
        dao.setHidden(foodId, null, now())
    }

    override suspend fun foodIdsNamed(name: String): List<Long> =
        dao.foodIdsNamed(FoodKeys.nameKey(name))

    // The same query the delete's own refusal asks, so the question and the refusal cannot disagree
    // about which meals stand in the way.
    override suspend fun savedMealsUsing(foodId: Long): List<String> = dao.mealsUsing(foodId)

    override suspend fun delete(foodId: Long): EditResult = database.withTransaction {
        // Asked rather than caught. The database would refuse this anyway — that is what the
        // restriction is for — but a refusal the owner can read names the meals standing in the way.
        val meals = dao.mealsUsing(foodId)
        if (meals.isNotEmpty()) {
            return@withTransaction EditResult.Refused(EditRefused.UsedBySavedMeals(meals))
        }
        dao.deleteFood(foodId)
        EditResult.Done
    }

    override suspend fun merge(winnerId: Long, loserId: Long): EditResult =
        database.withTransaction {
            if (winnerId == loserId) return@withTransaction EditResult.Done

            // A meal holding both would end up with one food twice, which the unique index refuses.
            // Settled before it commits rather than discovered by a failure.
            val both = dao.mealsHoldingBoth(winnerId, loserId)
            if (both.isNotEmpty()) {
                return@withTransaction EditResult.Refused(EditRefused.MealsHoldingBoth(both))
            }

            // The history moves across. Not one logged row's numbers, name, portion, source or
            // confidence is touched: merging is about identity, not about rewriting the past.
            dao.movePastRows(winnerId, loserId)
            dao.moveMealComponents(winnerId, loserId)
            // And this is the step that makes it worth having: the loser's name becomes an alias,
            // so the next log under that name finds the one food.
            dao.moveNames(winnerId, loserId)
            dao.deleteFood(loserId)
            dao.touch(winnerId, now())
            EditResult.Done
        }
}
