package com.metaself.app.data.backup

import com.metaself.app.data.ai.AiSettingsStore
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
import com.metaself.app.domain.backup.BackupWeight
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.DerivedFoods
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
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** What a restore did, so the owner is told in numbers rather than reassured in adjectives. */
data class RestoreResult(val meals: Int, val weights: Int, val hasProfile: Boolean)

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
     */
    suspend fun restore(backup: Backup): RestoreResult {
        meals.deleteAllMeals()
        weights.deleteAll()

        // The foods first, so every row restored after them has something to point at.
        val restoredFoods = restoreFoods(backup)
        val savedMealIdByName = restoreSavedMeals(backup, restoredFoods.byKey)

        backup.meals.forEachIndexed { at, meal ->
            val items = meal.items.mapIndexedNotNull { index, item ->
                item.toDomainOrNull()?.copy(
                    // By key when the file names one, and otherwise by where the row sits — which
                    // is how every item of a version-1 file finds the food the conversion made for
                    // it. A file written before foods existed names none of them.
                    foodId = item.food?.let(restoredFoods.byKey::get)
                        ?: restoredFoods.byRow[refOf(at, index)],
                )
            }
            if (items.isEmpty()) return@forEachIndexed
            val domain = Meal(
                epochDay = meal.epochDay,
                loggedAtMillis = meal.loggedAtMillis,
                note = meal.note,
                items = items,
                savedMealId = meal.savedMeal?.let(savedMealIdByName::get),
                savedMealAdjusted = meal.adjusted,
            )
            val (entity, itemEntities) = domain.toEntities()
            val mealId = meals.insertMeal(entity.copy(id = 0))
            meals.insertItems(itemEntities.map { it.copy(id = 0, mealId = mealId) })
        }

        backup.weights.forEach { weight ->
            weights.upsert(WeightEntity(epochDay = weight.epochDay, kg = weight.kg))
        }

        backup.profile?.toDomainOrNull()?.let { profiles.save(it) }
        backup.revision?.let {
            profiles.saveRevision(
                TargetRevision(it.epochDay, it.trendKg, it.kcal, it.previousKcal),
            )
        }
        if (backup.revisionSeen) profiles.markRevisionSeen()
        backup.arrival?.let { profiles.saveArrival(GoalArrival(it.targetKg, it.epochDay)) }
        if (backup.milestones.isNotEmpty()) {
            profiles.recordMilestones(backup.milestones.mapKeys { Milestone(it.key) })
        }
        backup.ai?.let {
            ai.setModel(it.model)
            ai.setDailyCeiling(it.dailyCeiling)
        }
        backup.reminder?.let {
            val reminder = Reminder(it.enabled, it.hour, it.minute)
            reminders.save(reminder)
            // The alarm belongs to the phone, not to the file. A restored reminder must be set on
            // THIS device or it is a switch that says on and never fires.
            if (reminder.enabled) scheduler.schedule(reminder) else scheduler.cancel()
        }

        return RestoreResult(
            meals = backup.meals.size,
            weights = backup.weights.size,
            hasProfile = backup.profile != null,
        )
    }

    /**
     * The foods the file describes, or — for a file written before foods existed — the foods its
     * own record implies.
     *
     * **A version-1 file stays restorable for ever**, and restoring one runs the same conversion the
     * upgrade ran over the record: written once and called from both, so the two cannot drift, and
     * so a restore never leaves the history detached from the food list.
     *
     * A version-2 file whose items name a key no food in it declares has that food created from the
     * item instead of failing. The record of what was eaten is the authority; the food list is
     * derivable from it, which is exactly why this case is recoverable.
     *
     * @return where each key in the file ended up, including every alias a food answers to, so an
     *   item naming a name that was since merged still finds it.
     */
    private suspend fun restoreFoods(backup: Backup): RestoredFoods {
        val landed = mutableMapOf<String, Long>()
        val byRow = mutableMapOf<Long, Long>()

        backup.foods.forEach { described ->
            val food = BackupFoods.toDomain(described, backup.exportedAtMillis) ?: return@forEach
            val made = runCatching {
                foods.findOrCreate(
                    name = food.name,
                    brand = food.brand,
                    facts = food.facts,
                    barcode = food.barcode,
                )
            }.getOrNull() ?: return@forEach
            landed[described.key] = made.food.id
            // An alias is a key too, and the file may refer to this food by one of them.
            BackupFoods.everyKeyOf(food).forEach { landed[it] = made.food.id }
        }

        // Whatever the food block did not cover: every item of a version-1 file, and any item of a
        // version-2 file naming no food, or a food it did not declare.
        val uncovered = backup.meals.flatMapIndexed { at, meal ->
            meal.items.mapIndexed { index, item -> refOf(at, index) to item }
        }.filter { (_, item) -> item.food == null || item.food !in landed }

        if (uncovered.isEmpty()) return RestoredFoods(landed, byRow)

        val derived = DerivedFoods.from(
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
        )

        derived.foods.forEach { food ->
            val made = runCatching {
                foods.findOrCreate(name = food.displayName, facts = food.facts)
            }.getOrNull() ?: return@forEach
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

    /**
     * The meals the file describes.
     *
     * **A version-1 file creates none**, for the same reason the upgrade created none: a meal is
     * only something the owner built, and restoring an old file must not manufacture the meals the
     * conversion refused to.
     */
    private suspend fun restoreSavedMeals(
        backup: Backup,
        foodIdByKey: Map<String, Long>,
    ): Map<String, Long> {
        val landed = mutableMapOf<String, Long>()
        backup.savedMeals.forEach { described ->
            val built = runCatching { savedMeals.create(described.name) }.getOrNull()
            val mealId = (built as? MealResult.Built)?.mealId ?: return@forEach
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
