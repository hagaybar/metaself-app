package com.metaself.app.data.backup

import com.metaself.app.data.ai.AiSettingsStore
import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.data.day.MealDao
import com.metaself.app.data.food.FoodRepository
import com.metaself.app.data.food.MealResult
import com.metaself.app.data.food.SavedMealRepository
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.reminder.ReminderScheduler
import com.metaself.app.data.reminder.ReminderStore
import com.metaself.app.data.weight.WeightDao
import com.metaself.app.data.weight.WeightEntity
import com.metaself.app.domain.backup.Backup
import com.metaself.app.domain.backup.BackupAi
import com.metaself.app.domain.backup.BackupArrival
import com.metaself.app.domain.backup.BackupItem
import com.metaself.app.domain.backup.BackupMeal
import com.metaself.app.domain.backup.BackupProfile
import com.metaself.app.domain.backup.BackupReminder
import com.metaself.app.domain.backup.BackupRevision
import com.metaself.app.domain.backup.BackupSavedMeal
import com.metaself.app.domain.backup.BackupWeight
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.DerivedFood
import com.metaself.app.domain.food.DerivedFoods
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodKeys
import com.metaself.app.domain.food.LoggedFoodRow
import com.metaself.app.domain.goal.GoalArrival
import com.metaself.app.domain.milestone.Milestone
import com.metaself.app.domain.profile.ActivityLevel
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.profile.Profile
import com.metaself.app.domain.profile.Sex
import com.metaself.app.domain.reminder.Reminder
import com.metaself.app.domain.target.TargetRevision
import com.metaself.app.data.day.toDomain
import com.metaself.app.data.day.toEntities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** What a restore did, so the owner is told in numbers rather than reassured in adjectives. */
data class RestoreResult(val meals: Int, val weights: Int, val hasProfile: Boolean)

/**
 * A restore that failed and left the phone exactly as it was: nothing had been written yet, or the
 * database rolled back and the settings were put back.
 *
 * Thrown ONLY then, so a screen can say "nothing was changed" on this and on nothing else. The
 * message and the frame are the cause's, so the problem log names the real fault, not this.
 */
class NothingRestored(cause: Throwable) :
    Exception("${cause::class.java.name}: ${cause.message}", cause) {
    init {
        stackTrace = cause.stackTrace
    }
}

/**
 * Gathering everything into one thing, and putting it back.
 *
 * The API key is not gathered. There is nowhere in [Backup] to put it, which is deliberate: an
 * export is a file that ends up in a cloud drive and an email, and a credential that spends money
 * does not belong in one.
 *
 * A restore REPLACES. The done-when for this step is a wiped install arriving at an identical
 * state, and merging cannot promise that — two records of the same meal, or two weights for one day,
 * have no correct resolution. Replacing has exactly one meaning, which is why the screen says what
 * is about to be destroyed before it happens.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val meals: MealDao,
    private val weights: WeightDao,
    private val profiles: ProfileRepository,
    private val reminders: ReminderStore,
    private val scheduler: ReminderScheduler,
    private val ai: AiSettingsStore,
    private val foods: FoodRepository,
    private val savedMeals: SavedMealRepository,
    private val transaction: DatabaseTransaction,
    private val snapshot: SettingsSnapshot,
) {

    suspend fun export(nowMillis: Long): Backup {
        val profile = profiles.profile.first()
        val everyFood = foods.observeAll().first()
        val foodKeyById = everyFood.associate { it.id to BackupFoods.keyOf(it) }
        val builtMeals = savedMeals.observeOffered().first()
        val mealNameById = builtMeals.associate { it.id to it.name }
        val revision = profiles.revision.first()
        val arrival = profiles.arrival.first()
        val milestones = profiles.milestones.first()
        val reminder = reminders.reminder.first()
        val aiSettings = ai.settings.first()

        return Backup(
            version = Backup.CURRENT_VERSION,
            exportedAtMillis = nowMillis,
            profile = profile?.let { it.toBackup() },
            meals = meals.allMeals().mapNotNull { row ->
                val domain = row.toDomain() ?: return@mapNotNull null
                BackupMeal(
                    epochDay = domain.epochDay,
                    loggedAtMillis = domain.loggedAtMillis,
                    note = domain.note,
                    items = domain.items.map { it.toBackup(foodKeyById) },
                    savedMeal = domain.savedMealId?.let(mealNameById::get),
                    adjusted = domain.savedMealAdjusted,
                )
            },
            weights = weights.all().map { BackupWeight(it.epochDay, it.kg) },
            revision = revision?.let {
                BackupRevision(it.epochDay, it.trendKg, it.kcal, it.previousKcal)
            },
            revisionSeen = profiles.revisionSeen.first(),
            arrival = arrival?.let { BackupArrival(it.targetKg, it.epochDay) },
            milestones = milestones.mapKeys { it.key.name },
            reminder = BackupReminder(reminder.enabled, reminder.hour, reminder.minute),
            ai = BackupAi(aiSettings.model, aiSettings.dailyCeiling),
            foods = everyFood.map(BackupFoods::toBackup),
            savedMeals = builtMeals.map(BackupFoods::toBackup),
        )
    }

    /**
     * **The meals and the weights are replaced; the foods are merged into.**
     *
     * The difference is deliberate and worth stating. Two records of the same meal, or two weights
     * for one day, have no correct resolution, so replacing is the only thing that has exactly one
     * meaning — which is why the screen says what is about to be destroyed before it happens.
     *
     * A food is not like that. The file's foods go through the same one door as every other, so a
     * food this phone already has is FOUND rather than duplicated, and the file's numbers are
     * offered to it through the guarded statements — which means the better of the two survives.
     * Wiping the food list first would throw away a number he had corrected since the backup, and
     * would mean destroying something the confirmation screen does not mention. A food the file does
     * not name is left alone, with nothing pointing at it, which is a spare entry in a list rather
     * than a wrong number anywhere.
     *
     * On the case this is measured against — a wiped install — the two behave identically.
     *
     * **All or nothing** (public issue #32). Four phases, in this order:
     * 1. every value is read and built from the file ([prepare]) — a throw here wrote nothing;
     * 2. the settings store is [snapshot]ted;
     * 3. one database [transaction] holds every database write and then, last and still inside it,
     *    the settings writes — so a settings write that throws rolls the database back too;
     * 4. only after the commit is the alarm set, for the reminder now stored.
     *
     * If the transaction does not commit, the settings are put back as they were and
     * [NothingRestored] is thrown: the phone is as it was. If putting them back fails as well, the
     * original failure is thrown instead, with that one attached, because then something did change.
     *
     * **The one window left:** the process dying after the settings writes and before the commit.
     * SQLite undoes the transaction when the database is next opened; the settings already written
     * stay. The two stores are separate files and nothing short of moving the settings into the
     * database closes that.
     */
    suspend fun restore(backup: Backup): RestoreResult {
        val prepared = try {
            prepare(backup)
        } catch (refused: Exception) {
            throw NothingRestored(refused)
        }
        val putBack = try {
            snapshot.take()
        } catch (unread: Exception) {
            throw NothingRestored(unread)
        }

        try {
            transaction.run {
                meals.deleteAllMeals()
                weights.deleteAll()
                // The foods first, so every row restored after them has something to point at.
                val restoredFoods = restoreFoods(prepared)
                val savedMealIdByName = restoreSavedMeals(prepared, restoredFoods.byKey)
                restoreMeals(prepared, restoredFoods, savedMealIdByName)
                prepared.weights.forEach { weights.upsert(it) }
                // Last, and inside: a throw here is still a throw out of the transaction.
                restoreSettings(prepared)
            }
        } catch (failure: Throwable) {
            // Whatever the transaction had written is gone; the settings may be part-written. Put
            // back even when cancelled, because an unfinished put-back is the partial state itself.
            val alsoFailed = withContext(NonCancellable) { runCatching { putBack() }.exceptionOrNull() }
            if (alsoFailed != null) {
                failure.addSuppressed(alsoFailed)
                throw failure
            }
            if (failure is CancellationException || failure !is Exception) throw failure
            throw NothingRestored(failure)
        }

        // The alarm belongs to the phone, not to the file. A restored reminder must be set on THIS
        // device or it is a switch that says on and never fires — and only once it is stored.
        prepared.reminder?.let { reminder ->
            if (reminder.enabled) scheduler.schedule(reminder) else scheduler.cancel()
        }

        return RestoreResult(
            meals = backup.meals.size,
            weights = backup.weights.size,
            hasProfile = backup.profile != null,
        )
    }

    /**
     * Everything the file says, built into the values that will be written, before one is.
     *
     * **What is dropped here is exactly what a restore has always dropped.** A food or a built meal
     * whose name leaves nothing to key on used to be thrown on by the store and caught; it is now
     * recognised here, because inside the transaction a caught throw would roll back everything and
     * still report success (see [DatabaseTransaction]).
     *
     * **A version-1 file stays restorable for ever**, and restoring one runs the same conversion the
     * upgrade ran over the record: written once and called from both, so the two cannot drift, and
     * so a restore never leaves the history detached from the food list.
     *
     * A version-2 file whose items name a key no food in it declares has that food created from the
     * item instead of failing. The record of what was eaten is the authority; the food list is
     * derivable from it, which is exactly why this case is recoverable.
     *
     * **A version-1 file creates no built meals**, for the same reason the upgrade created none: a
     * meal is only something the owner built, and restoring an old file must not manufacture the
     * meals the conversion refused to.
     */
    private fun prepare(backup: Backup): Prepared {
        val fileFoods = backup.foods.mapNotNull { described ->
            val food = BackupFoods.toDomain(described, backup.exportedAtMillis)
                ?.takeIf { canBeKeyed(it.name) }
                ?: return@mapNotNull null
            // An alias is a key too, and the file may refer to this food by one of them.
            FileFood(listOf(described.key) + BackupFoods.everyKeyOf(food), food)
        }
        val declared = fileFoods.flatMap { it.keys }.toSet()

        // Whatever the food block does not cover: every item of a version-1 file, and any item of a
        // version-2 file naming no food, or a food it did not declare.
        val uncovered = backup.meals.flatMapIndexed { at, meal ->
            meal.items.mapIndexed { index, item -> refOf(at, index) to item }
        }.filter { (_, item) -> item.food == null || item.food !in declared }

        val derived = if (uncovered.isEmpty()) {
            emptyList()
        } else {
            DerivedFoods.from(
                uncovered.map { (ref, item) ->
                    val domain = item.toDomainOrNull()
                    LoggedFoodRow(
                        ref = ref,
                        name = item.name,
                        portionAmount = item.portionAmount,
                        portionUnit = item.portionUnit,
                        kcal = item.kcal,
                        proteinG = item.proteinG,
                        carbsG = item.carbsG,
                        fatG = item.fatG,
                        source = domain?.source ?: Source.UNRECOGNISED,
                        confidence = domain?.confidence,
                        loggedAtMillis = 0,
                    )
                },
            ).foods.filter { canBeKeyed(it.displayName) }
        }

        val restoredMeals = backup.meals.mapIndexedNotNull { at, meal ->
            val kept = meal.items.mapIndexedNotNull { index, item ->
                item.toDomainOrNull()?.let { domain -> domain to ItemLink(item.food, refOf(at, index)) }
            }
            if (kept.isEmpty()) return@mapIndexedNotNull null
            PreparedMeal(
                meal = Meal(
                    epochDay = meal.epochDay,
                    loggedAtMillis = meal.loggedAtMillis,
                    note = meal.note,
                    items = kept.map { it.first },
                    savedMealAdjusted = meal.adjusted,
                ),
                links = kept.map { it.second },
                savedMeal = meal.savedMeal,
            )
        }

        return Prepared(
            fileFoods = fileFoods,
            derivedFoods = derived,
            savedMeals = backup.savedMeals.filter { canBeKeyed(it.name) },
            meals = restoredMeals,
            weights = backup.weights.map { WeightEntity(epochDay = it.epochDay, kg = it.kg) },
            profile = backup.profile?.toDomainOrNull(),
            revision = backup.revision?.let {
                TargetRevision(it.epochDay, it.trendKg, it.kcal, it.previousKcal)
            },
            revisionSeen = backup.revisionSeen,
            arrival = backup.arrival?.let { GoalArrival(it.targetKg, it.epochDay) },
            milestones = backup.milestones.mapKeys { Milestone(it.key) },
            ai = backup.ai,
            reminder = backup.reminder?.let { Reminder(it.enabled, it.hour, it.minute) },
        )
    }

    /**
     * Whether the stores can make a key of this name — the question `findOrCreate` and a built
     * meal's `create` answer by throwing.
     */
    private fun canBeKeyed(name: String): Boolean = runCatching {
        FoodKeys.nameKey(name)
        FoodKeys.displayName(name)
    }.isSuccess

    /**
     * The foods, found or created.
     *
     * @return where each key in the file ended up, including every alias a food answers to, so an
     *   item naming a name that was since merged still finds it.
     */
    private suspend fun restoreFoods(prepared: Prepared): RestoredFoods {
        val landed = mutableMapOf<String, Long>()
        val byRow = mutableMapOf<Long, Long>()

        prepared.fileFoods.forEach { (keys, food) ->
            val made = foods.findOrCreate(
                name = food.name,
                brand = food.brand,
                facts = food.facts,
                barcode = food.barcode,
            )
            keys.forEach { landed[it] = made.food.id }
        }

        prepared.derivedFoods.forEach { food ->
            val made = foods.findOrCreate(name = food.displayName, facts = food.facts)
            landed[BackupFoods.keyOf(food)] = made.food.id
            landed[BackupFoods.keyOf(food.displayName, null)] = made.food.id
            // Which rows became this food. The only way a version-1 item can be attached: it names
            // no key, so where it sits in the file is its whole identity.
            food.rowRefs.forEach { ref -> byRow[ref] = made.food.id }
        }
        return RestoredFoods(landed, byRow)
    }

    /**
     * @property byKey where each key the file uses ended up, aliases included.
     * @property byRow where each item of a file that named no keys ended up.
     */
    private data class RestoredFoods(
        val byKey: Map<String, Long>,
        val byRow: Map<Long, Long>,
    )

    /** Where a row sits in the file, which is its identity when it has no key of its own. */
    private fun refOf(mealAt: Int, itemAt: Int): Long =
        mealAt.toLong() * ITEMS_PER_MEAL + itemAt

    /** The meals the file describes. A name already taken here is left to the meal holding it. */
    private suspend fun restoreSavedMeals(
        prepared: Prepared,
        foodIdByKey: Map<String, Long>,
    ): Map<String, Long> {
        val landed = mutableMapOf<String, Long>()
        prepared.savedMeals.forEach { described ->
            val mealId = (savedMeals.create(described.name) as? MealResult.Built)?.mealId
                ?: return@forEach
            landed[described.name] = mealId
            described.components.forEach { component ->
                val foodId = foodIdByKey[component.food] ?: return@forEach
                if (component.amount <= 0.0) return@forEach
                savedMeals.put(
                    mealId = mealId,
                    foodId = foodId,
                    amount = component.amount,
                    countedAs = BackupFoods.countedAs(component.countedAs),
                )
            }
        }
        return landed
    }

    private suspend fun restoreMeals(
        prepared: Prepared,
        restoredFoods: RestoredFoods,
        savedMealIdByName: Map<String, Long>,
    ) {
        prepared.meals.forEach { (meal, links, savedMeal) ->
            val domain = meal.copy(
                items = meal.items.zip(links) { item, link ->
                    // By key when the file names one, and otherwise by where the row sits — which
                    // is how every item of a version-1 file finds the food the conversion made for
                    // it. A file written before foods existed names none of them.
                    item.copy(
                        foodId = link.food?.let(restoredFoods.byKey::get)
                            ?: restoredFoods.byRow[link.ref],
                    )
                },
                savedMealId = savedMeal?.let(savedMealIdByName::get),
            )
            val (entity, itemEntities) = domain.toEntities()
            val mealId = meals.insertMeal(entity.copy(id = 0))
            meals.insertItems(itemEntities.map { it.copy(id = 0, mealId = mealId) })
        }
    }

    /** The settings, in the order a restore has always written them. The alarm is not here. */
    private suspend fun restoreSettings(prepared: Prepared) {
        prepared.profile?.let { profiles.save(it) }
        prepared.revision?.let { profiles.saveRevision(it) }
        if (prepared.revisionSeen) profiles.markRevisionSeen()
        prepared.arrival?.let { profiles.saveArrival(it) }
        if (prepared.milestones.isNotEmpty()) profiles.recordMilestones(prepared.milestones)
        prepared.ai?.let {
            ai.setModel(it.model)
            ai.setDailyCeiling(it.dailyCeiling)
        }
        prepared.reminder?.let { reminders.save(it) }
    }

    /** A food from the file's food block, with every key the file may refer to it by. */
    private data class FileFood(val keys: List<String>, val food: Food)

    /** Which food an item names in the file, and where it sits there. */
    private data class ItemLink(val food: String?, val ref: Long)

    /** A meal with its items ready but not yet pointed at anything, which needs the ids. */
    private data class PreparedMeal(
        val meal: Meal,
        val links: List<ItemLink>,
        val savedMeal: String?,
    )

    /** Everything [restore] will write, built before it writes any of it. */
    private class Prepared(
        val fileFoods: List<FileFood>,
        val derivedFoods: List<DerivedFood>,
        val savedMeals: List<BackupSavedMeal>,
        val meals: List<PreparedMeal>,
        val weights: List<WeightEntity>,
        val profile: Profile?,
        val revision: TargetRevision?,
        val revisionSeen: Boolean,
        val arrival: GoalArrival?,
        val milestones: Map<Milestone, Long>,
        val ai: BackupAi?,
        val reminder: Reminder?,
    )

    /** How much a restore would destroy, so the question asked is a real one. */
    suspend fun whatIsHere(): RestoreResult = RestoreResult(
        meals = meals.allMeals().size,
        weights = weights.all().size,
        hasProfile = profiles.profile.first() != null,
    )

    private companion object {
        /**
         * Enough room per meal that a row's position in the file makes a unique reference.
         *
         * Only ever used to find a row again after the conversion has grouped it, and never stored.
         */
        const val ITEMS_PER_MEAL = 1_000
    }
}

private fun Profile.toBackup() = BackupProfile(
    heightCm = heightCm,
    birthYear = birthYear,
    sex = sex.name,
    weightKg = weightKg,
    activity = activity.name,
    goalDirection = goal.direction.name,
    goalKgPerWeek = goal.kgPerWeek,
    goalTargetKg = goal.targetKg,
    allowBelowFloor = allowBelowFloor,
)

private fun BackupProfile.toDomainOrNull(): Profile? = runCatching {
    Profile(
        heightCm = heightCm,
        birthYear = birthYear,
        sex = Sex.valueOf(sex),
        weightKg = weightKg,
        activity = ActivityLevel.valueOf(activity),
        goal = Goal(GoalDirection.valueOf(goalDirection), goalKgPerWeek, goalTargetKg),
        allowBelowFloor = allowBelowFloor,
    )
}.getOrNull()

/**
 * @param foodKeyById which food each row points at, as the key the file refers to foods by.
 *
 * [name] stays the name as it was typed that day. Renaming re-labels what the app shows, not what
 * the record holds: the current name is in the foods block, once, and the transcript is here.
 */
private fun FoodItem.toBackup(foodKeyById: Map<Long, String>) = BackupItem(
    name = name,
    portion = portion,
    portionAmount = portionAmount,
    portionUnit = portionUnit,
    kcal = kcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    source = source.name,
    confidence = confidence?.name,
    food = foodId?.let(foodKeyById::get),
)

/**
 * An item the domain would refuse — an unknown source, or a combination it forbids — is dropped
 * rather than forced. The same forgiveness the database mapping already applies, for the same
 * reason: a file may have been written by a version that knew things this one does not.
 */
private fun BackupItem.toDomainOrNull(): FoodItem? = runCatching {
    FoodItem(
        name = name,
        portion = portion,
        portionAmount = portionAmount,
        portionUnit = portionUnit,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        source = Source.entries.firstOrNull { it.name == source } ?: Source.UNRECOGNISED,
        confidence = confidence?.let { name -> Confidence.entries.firstOrNull { it.name == name } },
    )
}.getOrNull()
