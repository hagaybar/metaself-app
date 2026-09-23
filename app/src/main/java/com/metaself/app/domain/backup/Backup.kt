package com.metaself.app.domain.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything this app knows, as one thing that can be written to a file.
 *
 * **The API key is deliberately absent, and there is no field for it.** An export ends up in a
 * cloud drive, in an email to oneself, on a laptop. A credential that spends money does not belong
 * in a document whose entire purpose is to be copied around, and "encrypted in the file" only moves
 * the question to where the passphrase lives. Restoring therefore ends with the owner pasting his
 * key again, which is the correct amount of friction for the one secret the app holds.
 *
 * Field names are spelled out rather than abbreviated because a person is expected to open this
 * file and recognise his own dinner in it. A backup nobody can read is a backup nobody can check,
 * and the first time it matters is the worst possible time to find out it was empty.
 */
@Serializable
data class Backup(
    val version: Int = CURRENT_VERSION,
    @SerialName("exported_at") val exportedAtMillis: Long = 0,
    val profile: BackupProfile? = null,
    val meals: List<BackupMeal> = emptyList(),
    val weights: List<BackupWeight> = emptyList(),
    val revision: BackupRevision? = null,
    @SerialName("revision_seen") val revisionSeen: Boolean = false,
    val arrival: BackupArrival? = null,
    val milestones: Map<String, Long> = emptyMap(),
    val reminder: BackupReminder? = null,
    val ai: BackupAi? = null,
    val foods: List<BackupFood> = emptyList(),
    @SerialName("saved_meals") val savedMeals: List<BackupSavedMeal> = emptyList(),
) {
    companion object {
        /**
         * Raised whenever the shape changes. A file from a LATER version is refused whole rather
         * than read for the parts that look familiar: a half-restored record is worse than a failed
         * one, because the failure is visible and the half is not.
         *
         * Version 2 adds the owner's foods and the meals he built. **Every version-1 file stays
         * restorable for ever**: it has neither block, and the same conversion the upgrade ran over
         * the record is run over the file's items instead, so a restore never leaves the history
         * detached from the food list.
         */
        const val CURRENT_VERSION = 2

        /** The first version, which had no foods and no meals of its own. */
        const val FIRST_VERSION = 1
    }
}

/**
 * One of the owner's foods, written out whole.
 *
 * **Entities are written rather than rebuilt on restore.** A file holding only log rows, from which
 * foods are re-derived at restore time, cannot be inspected for what it will produce — you would
 * have to run the derivation to find out what your food list will look like. A backup nobody can
 * check is a backup nobody should trust, and this costs a few kilobytes against what is already the
 * larger part of the file.
 *
 * **Each of the three facts is written as its own object with its own source**, which is what makes
 * the file readable in the sense that matters: a person can see at a glance that the per-100-g
 * figure came off a packet and the weight came out of his own head.
 *
 * @property key how items refer to this food — `"yoghurt|na"`, its normalised name and brand. A key
 *   rather than a database id because `"food": "yoghurt|na"` under an item named "Yoghurt" explains
 *   itself to a person reading the file, where `"food_id": 47` does not, and because a key is stable
 *   across devices.
 * @property alsoKnownAs every other name it answers to, which is what merging two duplicates leaves
 *   behind. An alias is a key too, so a restore into a record where this food was since merged
 *   resolves through them.
 */
@Serializable
data class BackupFood(
    val key: String,
    val name: String,
    val brand: String,
    @SerialName("also_known_as") val alsoKnownAs: List<String> = emptyList(),
    @SerialName("per_100g") val per100g: BackupNutrients? = null,
    @SerialName("per_unit") val perUnit: BackupPerUnit? = null,
    @SerialName("grams_per_unit") val gramsPerUnit: BackupWeight2? = null,
    val barcode: String? = null,
)

/** What 100 grams are worth, and where that came from. Written whole, or written as null. */
@Serializable
data class BackupNutrients(
    val kcal: Double,
    @SerialName("protein_g") val proteinG: Double,
    @SerialName("carbs_g") val carbsG: Double,
    @SerialName("fat_g") val fatG: Double,
    val source: String,
    val confidence: String? = null,
)

/** What one of it is worth, and what "one" is. */
@Serializable
data class BackupPerUnit(
    val unit: String,
    val kcal: Double,
    @SerialName("protein_g") val proteinG: Double,
    @SerialName("carbs_g") val carbsG: Double,
    @SerialName("fat_g") val fatG: Double,
    val source: String,
    val confidence: String? = null,
)

/**
 * What one of it weighs.
 *
 * Its own provenance, like the other two, because this is the number that turns one way of counting
 * into the other and a wrong one propagates into everything logged afterwards.
 */
@Serializable
data class BackupWeight2(
    val grams: Double,
    val source: String,
    val confidence: String? = null,
)

/**
 * A meal the owner built, written as its name and what is in it.
 *
 * Its parts refer to foods by key, for the same reason items do. It has no totals of its own: what
 * it is worth is the sum over its parts, and a total in the file would be a second answer that could
 * disagree with them.
 */
@Serializable
data class BackupSavedMeal(
    val name: String,
    val components: List<BackupMealComponent> = emptyList(),
)

@Serializable
data class BackupMealComponent(
    val food: String,
    val amount: Double,
    @SerialName("counted_as") val countedAs: String,
)

@Serializable
data class BackupProfile(
    @SerialName("height_cm") val heightCm: Int,
    @SerialName("birth_year") val birthYear: Int,
    val sex: String,
    @SerialName("weight_kg") val weightKg: Double,
    val activity: String,
    @SerialName("goal_direction") val goalDirection: String,
    @SerialName("goal_kg_per_week") val goalKgPerWeek: Double,
    @SerialName("goal_target_kg") val goalTargetKg: Double? = null,
    @SerialName("allow_below_floor") val allowBelowFloor: Boolean = false,
)

@Serializable
data class BackupMeal(
    @SerialName("epoch_day") val epochDay: Long,
    @SerialName("logged_at") val loggedAtMillis: Long,
    val note: String? = null,
    val items: List<BackupItem> = emptyList(),
    /** Which meal he built this logging came from, by name, when it came from one. */
    @SerialName("saved_meal") val savedMeal: String? = null,
    /** Whether what was logged differed from that meal as it stood at that moment. */
    val adjusted: Boolean = false,
)

@Serializable
data class BackupItem(
    val name: String,
    val portion: String? = null,
    @SerialName("portion_amount") val portionAmount: Double = 0.0,
    @SerialName("portion_unit") val portionUnit: String = "",
    val kcal: Int,
    @SerialName("protein_g") val proteinG: Int,
    @SerialName("carbs_g") val carbsG: Int,
    @SerialName("fat_g") val fatG: Int,
    val source: String,
    val confidence: String? = null,
    /**
     * Which food this row was, by key.
     *
     * **[name] is still the name as it was typed that day.** Renaming re-labels what the app shows,
     * not what the record holds: the current name is in the foods block, once, and the transcript is
     * here, as it always was. A person reading a file who finds an item called "yogurt" pointing at
     * a food now called "Greek yoghurt" is seeing exactly what happened, which is the whole purpose
     * of the file.
     */
    val food: String? = null,
)

@Serializable
data class BackupWeight(
    @SerialName("epoch_day") val epochDay: Long,
    val kg: Double,
)

@Serializable
data class BackupRevision(
    @SerialName("epoch_day") val epochDay: Long,
    @SerialName("trend_kg") val trendKg: Double,
    val kcal: Int,
    @SerialName("previous_kcal") val previousKcal: Int? = null,
)

@Serializable
data class BackupArrival(
    @SerialName("target_kg") val targetKg: Double,
    @SerialName("epoch_day") val epochDay: Long,
)

@Serializable
data class BackupReminder(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
)

@Serializable
data class BackupAi(
    val model: String,
    @SerialName("daily_ceiling") val dailyCeiling: Int,
)
