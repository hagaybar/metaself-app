package com.metaself.app.data.day

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.metaself.app.data.food.FoodDao
import com.metaself.app.data.food.FoodEntity
import com.metaself.app.data.food.SavedMealDao
import com.metaself.app.data.food.FoodNameEntity
import com.metaself.app.data.food.SavedMealComponentEntity
import com.metaself.app.data.food.SavedMealEntity
import com.metaself.app.data.product.ProductDao
import com.metaself.app.data.product.ProductEntity
import com.metaself.app.data.weight.WeightDao
import com.metaself.app.data.weight.WeightEntity
import com.metaself.app.domain.portion.Portions

/**
 * The app's local store.
 *
 * Version 1, with the schema exported to `app/schemas` and committed. That file is the baseline
 * every later migration is tested against; without it, a migration test has nothing truthful to
 * migrate from.
 *
 * The profile does not live here — it is a single record in a DataStore, added in step 2, and
 * moving it into a table would be a schema and a DAO in service of one row.
 */
@Database(
    entities = [
        MealEntity::class,
        FoodItemEntity::class,
        WeightEntity::class,
        ProductEntity::class,
        FoodEntity::class,
        FoodNameEntity::class,
        SavedMealEntity::class,
        SavedMealComponentEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class MetaSelfDatabase : RoomDatabase() {

    abstract fun mealDao(): MealDao

    abstract fun weightDao(): WeightDao

    abstract fun productDao(): ProductDao

    abstract fun foodDao(): FoodDao

    abstract fun savedMealDao(): SavedMealDao

    companion object {

        /**
         * Version 2 adds the weights table and touches nothing else.
         *
         * Written by hand rather than left to Room's automatic migrations, because this is the code
         * standing between a schema change and the owner losing food he has logged, and it should
         * be readable by somebody deciding whether to trust it.
         */
        /**
         * Version 3 gives every logged item the numbers behind its portion.
         *
         * Until now a portion was kept only as words — "2 slice" — and the amount and unit the
         * model had supplied were dropped on the way to the table. That made a meal repeated from
         * the record impossible to adjust, because nothing can scale a sentence.
         *
         * The new columns default to zero and empty, which the app reads as "no amount was ever
         * recorded" and answers with no control at all. The rows already there are then backfilled
         * by parsing the words they were written as. That parse is a best effort at recovering
         * something this app should not have thrown away, and doing it here, once, where it can be
         * read and audited, is a different thing from parsing a display string at runtime every
         * time somebody presses a button. Anything unparseable is left at zero and simply cannot be
         * adjusted, which is the honest answer.
         *
         * Nothing else is touched. No name, no calorie count and no macro is written by this
         * migration.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `food_items` " +
                        "ADD COLUMN `portionAmount` REAL NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE `food_items` " +
                        "ADD COLUMN `portionUnit` TEXT NOT NULL DEFAULT ''",
                )

                val recovered = mutableListOf<Triple<Long, Double, String>>()
                db.query("SELECT `id`, `portion` FROM `food_items`").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val words = if (cursor.isNull(1)) null else cursor.getString(1)
                        Portions.parse(words)?.let { parsed ->
                            recovered += Triple(id, parsed.amount, parsed.unit)
                        }
                    }
                }
                recovered.forEach { (id, amount, unit) ->
                    db.execSQL(
                        "UPDATE `food_items` SET `portionAmount` = ?, `portionUnit` = ? " +
                            "WHERE `id` = ?",
                        arrayOf<Any>(amount, unit, id),
                    )
                }
            }
        }

        /**
         * Version 4 adds the table of products seen before, and touches nothing else.
         *
         * A product scanned once is kept so that the second scan of the same packet needs no
         * network at all (D8). Nothing about a meal, a weight or the profile is read or written
         * here.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `products` (" +
                        "`barcode` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`brand` TEXT, " +
                        "`kcalPer100g` REAL NOT NULL, " +
                        "`proteinPer100g` REAL NOT NULL, " +
                        "`carbsPer100g` REAL NOT NULL, " +
                        "`fatPer100g` REAL NOT NULL, " +
                        "`servingSizeG` REAL, " +
                        "`savedAtMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`barcode`))",
                )
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `weights` (" +
                        "`epochDay` INTEGER NOT NULL, " +
                        "`kg` REAL NOT NULL, " +
                        "PRIMARY KEY(`epochDay`))",
                )
            }
        }
    }
}
