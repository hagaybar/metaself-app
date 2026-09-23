package com.metaself.app.data.day

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.metaself.app.data.food.FoodEntity
import com.metaself.app.data.food.SavedMealEntity

/**
 * A logging event.
 *
 * @property epochDay the local calendar date. Indexed because "what did I eat today" is the only
 *   question this table is ever asked, and it is asked on every frame of the Today screen.
 */
@Entity(
    tableName = "meals",
    foreignKeys = [
        ForeignKey(
            entity = SavedMealEntity::class,
            parentColumns = ["id"],
            childColumns = ["savedMealId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("epochDay"), Index("savedMealId")],
)
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val loggedAtMillis: Long,
    val note: String?,
    /**
     * Which saved meal this logging came from, when it came from one.
     *
     * The title on the day's row is read through this pointer, so a meal renamed tomorrow retitles
     * every day it was ever eaten — the same answer a food gets, which is what stops the two
     * behaving differently for no reason. Setting it to null on delete means deleting a saved meal
     * never touches a past day: the row loses its title and shows its items, as it does now.
     */
    @ColumnInfo(defaultValue = "NULL") val savedMealId: Long? = null,
    /**
     * Whether what was logged differed from the meal's definition **as it stood at that moment**.
     *
     * A fact known exactly then and never again: the definition may be edited afterwards, so
     * comparing a past day against today's definition would be comparing it against something that
     * did not exist yet. Written once, never updated.
     *
     * **It must never be counted, aggregated, or used to offer to change a meal.** Nothing learns
     * from what the owner does — drop the oil every day for a month and the salad still has oil in
     * it. This is exactly the column a future helpful change would reach for, which is why the
     * prohibition is written on the column itself. Its only reader is whatever labels the day's row.
     */
    @ColumnInfo(defaultValue = "0") val savedMealAdjusted: Boolean = false,
)

/**
 * One thing eaten, belonging to a meal.
 *
 * [source] and [confidence] are stored as strings rather than as ordinals deliberately: an ordinal
 * is meaningless the moment somebody reorders the enum, and a backup file full of the number 2 is
 * unreadable by a human trying to work out what went wrong.
 *
 * [portionAmount] and [portionUnit] carry the same assumption as [portion] does, as arithmetic. They
 * arrived in version 3; every row written before that has zero and empty, which the app reads as
 * "no amount was ever recorded" and answers with no adjustment control rather than a wrong one. The
 * defaults are declared here as well as in the migration so that the two cannot disagree.
 *
 * The foreign key cascades on delete so that removing a meal cannot leave its items orphaned. The
 * app never deletes a meal for its own sake — a meal goes when its last item is deleted, and when
 * gathering a day's rows under a new meal leaves the meal they came out of with nothing in it — but
 * a cascade is what makes that a property of the schema instead of a promise made by the code above
 * it.
 */
@Entity(
    tableName = "food_items",
    foreignKeys = [
        ForeignKey(
            entity = MealEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["foodId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("mealId"), Index("foodId")],
)
data class FoodItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val name: String,
    val portion: String?,
    @ColumnInfo(defaultValue = "0") val portionAmount: Double = 0.0,
    @ColumnInfo(defaultValue = "''") val portionUnit: String = "",
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val source: String,
    val confidence: String?,
    /**
     * Which food this row was.
     *
     * Null means "not attached to any food" — which is what every row is before the conversion runs
     * and what every row becomes if its food is deleted, so the day screen works with or without it.
     *
     * Setting it to null on delete rather than cascading is the answer to what deleting a food does
     * to history: **it never deletes a day's food.** The row keeps its own name, portion, numbers,
     * source and confidence and simply stops pointing anywhere.
     *
     * Everything above this column stays exactly as it is, for ever. A logged row holds the numbers
     * as believed at the time because it holds its own copy of them, and there is no code path that
     * could rewrite them from a food, because the food is a pointer and not a source. Correcting a
     * food fixes the food; the day that was wrong stays wrong.
     */
    @ColumnInfo(defaultValue = "NULL") val foodId: Long? = null,
)

/** A meal with the items belonging to it, which is the only way a meal is ever read. */
data class MealWithItems(
    @Embedded val meal: MealEntity,
    @Relation(parentColumn = "id", entityColumn = "mealId")
    val items: List<FoodItemEntity>,
)

/** One day's total, for the window that measures what a day actually costs (D25). */
data class DayKcal(val epochDay: Long, val kcal: Int)

/** What one food is called now, for re-labelling the days it was eaten on. */
data class CurrentFoodName(val foodId: Long, val displayName: String)

/** What one meal the owner built is called now, for titling the days it was eaten on. */
data class CurrentSavedMealName(val id: Long, val name: String)
