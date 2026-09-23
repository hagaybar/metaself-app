package com.metaself.app.data.food

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * A food: the thing the list lists, the thing a saved meal is built from, the thing a logged row
 * points at.
 *
 * **The key is a number and the identity is a rule**, and they are not the same thing. Two foods are
 * the same food when their name and brand normalise alike — that rule is enforced by a unique index
 * over on [FoodNameEntity] — but what the rest of the record POINTS at is this id. Three reasons,
 * each sufficient: renaming has to re-label every past day without touching one stored number, which
 * only works if the past points at something that is not the name; a merge would otherwise mean
 * rewriting keys rather than repointing ids; and a food with two names cannot have a name as its key
 * at all, which is exactly what logging in two languages needs.
 *
 * Three independent optional facts, each with its own provenance: what 100 grams are worth, what one
 * of it is worth, and what one of it weighs. **A food has no kind.** It may know any of them, and
 * learning one never invalidates another — which is what makes re-scanning a food counted in bars
 * safe, where the earlier design would have flipped it to grams and made yesterday's "2" mean
 * something else today.
 *
 * The rank columns exist so that "a better number wins" lives in a `WHERE` clause rather than in a
 * caller's good intentions. There are three of them because a scan can win the argument about what
 * 100 grams are worth while having no say at all about what one bar weighs.
 *
 * **`OnConflictStrategy.REPLACE` must never be used on this table.** In SQLite it is a DELETE
 * followed by an INSERT, so it would cascade every alias away, hit the restriction protecting a
 * saved meal's components, and null out the pointer from every logged row — losing the aliases,
 * detaching the history, and silently reverting every past day's label to whatever was typed at the
 * time, all for what looks in the code like an ordinary upsert.
 *
 * @property brand the display form. `NA` is itself a brand — the brand of food that has no brand —
 *   which is what lets "yoghurt" typed today join "yoghurt" typed last week.
 * @property barcode the packet this food is, when it is a packet. Unique, and SQLite treats nulls in
 *   a unique index as distinct, so every unbranded food may be null.
 * @property hiddenAtMillis non-null means "do not offer this in any picker". Usually the better
 *   answer than deleting for a food with history, because deleting detaches the past and lets every
 *   day fall back to the name typed on the day.
 * @property gramsPerUnit **only ever set when something actually knows** — the owner typed it, or he
 *   read it off a packet. Never worked out by dividing per-unit calories by per-100-g calories: that
 *   arithmetic looks valid and presents an estimate as a fact about a physical object, which then
 *   propagates into every future gram-counted log of the food, silently and for ever.
 */
@Entity(
    tableName = "foods",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index("hiddenAtMillis"),
    ],
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "NA") val brand: String = "NA",
    val barcode: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val hiddenAtMillis: Long? = null,

    // What 100 grams of it are worth — present as a group or absent as a group.
    val kcalPer100g: Double? = null,
    val proteinPer100g: Double? = null,
    val carbsPer100g: Double? = null,
    val fatPer100g: Double? = null,
    val per100gSource: String? = null,
    val per100gSourceRank: Int? = null,
    val per100gConfidence: String? = null,
    val per100gSetAtMillis: Long? = null,

    // What one of it is worth — present as a group or absent as a group.
    val unitName: String? = null,
    val kcalPerUnit: Double? = null,
    val proteinPerUnit: Double? = null,
    val carbsPerUnit: Double? = null,
    val fatPerUnit: Double? = null,
    val perUnitSource: String? = null,
    val perUnitSourceRank: Int? = null,
    val perUnitConfidence: String? = null,
    val perUnitSetAtMillis: Long? = null,

    // What one of it weighs — a third, independent fact. Null is the normal case.
    val gramsPerUnit: Double? = null,
    val gramsPerUnitSource: String? = null,
    val gramsPerUnitSourceRank: Int? = null,
    val gramsPerUnitConfidence: String? = null,
    val gramsPerUnitSetAtMillis: Long? = null,
)

/**
 * Every name a food answers to.
 *
 * A separate table because a food may have more than one, which is the whole of what makes merging
 * two duplicates worth doing: joining the Hebrew yoghurt to the English one keeps both names, so the
 * next log in either language finds the one food. Without that, merging would be a treadmill — join
 * them today, log in Hebrew tomorrow, and there are two again.
 *
 * **The unique index on (nameKey, brandKey) IS the identity rule.** Not a convention and not a check
 * in a repository: the database refuses the second `yoghurt`+`na`, wherever it came from. That
 * matters more than it sounds, because there are two places a food can be created — by being eaten
 * and by being made in the manager — and an index is the only thing true for both without anybody
 * having to remember.
 *
 * @property brandKey the normalised brand, copied from the food. Denormalised **on purpose**: the
 *   identity rule spans a name and a brand, a unique index cannot span two tables, and this is where
 *   the index has to live. The cost is that editing a food's brand must rewrite this on its own name
 *   rows, in the same transaction. A name a join brought in keeps the brand it came with, so the
 *   absorbed food's next log still finds this one.
 * @property isPreferred 1 for the name shown in lists. "Exactly one per food" is not something the
 *   schema can express here, so it is kept in the repository with a test, and display falls back to
 *   the oldest name if it is ever violated — a wrong label rather than a crash.
 */
@Entity(
    tableName = "food_names",
    foreignKeys = [
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["foodId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["nameKey", "brandKey"], unique = true),
        Index(value = ["foodId", "isPreferred"]),
    ],
)
data class FoodNameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodId: Long,
    val displayName: String,
    val nameKey: String,
    val brandKey: String,
    val isPreferred: Boolean,
    val addedAtMillis: Long,
)

/**
 * A meal the owner built and named.
 *
 * Called `saved_meals` rather than `meals` because `meals` already exists and means a logging event.
 * Renaming that table so it stops meaning two things would be a rebuild and a sweep through every
 * data-access interface for no change in behaviour, so it is deliberately not done here. The wart is
 * recorded so nobody later mistakes it for an oversight.
 *
 * **No stored totals.** What it is worth is the sum over its components at the moment it is looked
 * at, and the numbers that go on the record are frozen onto the log rows at the moment it is logged.
 * A total stored here would be a second answer that could disagree with its own parts.
 *
 * **There is no draft state, and that is what makes building resumable.** A half-built salad is
 * simply a saved meal with fewer components — no flag, no state machine, and no query that has to
 * remember to exclude it. The cost is that a half-built meal is offered for logging like a finished
 * one; if that turns out to annoy, [hiddenAtMillis] hides it at no schema cost.
 *
 * @property name the owner's own name for it. Never invented — which is the whole reason the derived
 *   meal list this replaces refused to label anything.
 */
@Entity(
    tableName = "saved_meals",
    indices = [
        Index(value = ["nameKey"], unique = true),
        Index("hiddenAtMillis"),
    ],
)
data class SavedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameKey: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val hiddenAtMillis: Long? = null,
)

/**
 * One food in a saved meal, and how much of it.
 *
 * The food is protected: a food a saved meal uses cannot be deleted out from under it. The attempt
 * fails at the database and the app offers to hide it instead, or names the meals that use it.
 *
 * @property countedAs `GRAMS` or `UNITS` — which of the food's ways this amount is expressed in.
 *   Under the earlier design this column was a GUARD, because a food could change which kind it was
 *   and a "150" authored as grams would silently become 150 slices. A food has no kind now, so the
 *   trap is gone with the thing that caused it, and this is simply part of what the component says.
 *   One residue remains: emptying a food's per-unit numbers while a meal counts it in units is
 *   refused by name — "Vegetable salad counts this in slices" — rather than silently breaking it.
 * @property position the order the owner arranged them in. Identity no longer depends on order; the
 *   display order is his.
 */
@Entity(
    tableName = "saved_meal_components",
    foreignKeys = [
        ForeignKey(
            entity = SavedMealEntity::class,
            parentColumns = ["id"],
            childColumns = ["savedMealId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["foodId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("savedMealId"),
        Index("foodId"),
        Index(value = ["savedMealId", "foodId"], unique = true),
    ],
)
data class SavedMealComponentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val savedMealId: Long,
    val foodId: Long,
    val position: Int,
    val amount: Double,
    val countedAs: String,
)

/** A food with every name it answers to, which is the only way a food is ever read. */
data class FoodWithNames(
    @Embedded val food: FoodEntity,
    @Relation(parentColumn = "id", entityColumn = "foodId")
    val names: List<FoodNameEntity>,
)

/** A saved meal with what is in it, which is the only way a saved meal is ever read. */
data class SavedMealWithComponents(
    @Embedded val meal: SavedMealEntity,
    @Relation(parentColumn = "id", entityColumn = "savedMealId")
    val components: List<SavedMealComponentEntity>,
)
