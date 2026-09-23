package com.metaself.app.data.day

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.metaself.app.domain.food.DerivedFood
import com.metaself.app.domain.food.DerivedFoods
import com.metaself.app.domain.food.FoodKeys
import com.metaself.app.domain.food.LoggedFoodRow

/**
 * Version 5: foods and meals become things the app knows about.
 *
 * Four new tables, two rebuilt, and the owner's food list derived from every row he has ever
 * logged. This is the code standing between a schema change and the owner losing food, so it is
 * written to be read by somebody deciding whether to trust it.
 *
 * **Not one byte of `name`, `portion`, `portionAmount`, `portionUnit`, `kcal`, `proteinG`, `carbsG`,
 * `fatG`, `source` or `confidence` on any existing row is changed.** Every logged row is copied
 * across verbatim and then given one new column saying which food it was. Every day the owner has
 * already logged is worth after this exactly what it is worth now. That is not a hope about this
 * code; it is what the migration test asserts, day by day, before and after.
 *
 * **Why two tables are rebuilt rather than altered.** `ALTER TABLE … ADD COLUMN` cannot add a
 * foreign key, and both new columns are pointers that must be enforced by the schema rather than by
 * code above it. A rebuild also produces exactly the table definition Room compares against, which
 * an altered table would not.
 *
 * **Why dropping `meals` does not take every logged row with it.** `meals` is the parent of a
 * cascading foreign key from `food_items`, and in SQLite `DROP TABLE` performs an implicit DELETE
 * that fires cascades — if foreign keys are enforced. They are not, here: Room executes
 * `PRAGMA foreign_keys = ON` in its `onOpen`, which runs after `onUpgrade`, so a migration runs with
 * the framework default of enforcement off. This is the same window every Room-generated automatic
 * migration relies on to rebuild a parent table. **Anything that changes it makes this migration
 * delete the record**, which is why the row-count assertions in the test are not ceremony.
 *
 * **No saved meal is created.** A meal is only something the owner built and named, so the migration
 * must not look at which items happened to be logged together and manufacture one. The test asserts
 * both saved-meal tables are empty afterwards, so that nobody helpfully adds it later.
 *
 * **No food comes out knowing what one of it weighs.** Nothing in the record says what a slice
 * weighs, and working it out by dividing per-slice calories by per-100-g calories would present an
 * estimate as a measurement of a physical object.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {

    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()

        createFoodTables(db)

        // Read before anything is rebuilt: these are the rows as version 4 left them.
        val derivation = DerivedFoods.from(readEveryLoggedRow(db))
        val foodIdByRow = insertFoods(db, derivation.foods, now)

        rebuildMeals(db)
        rebuildFoodItems(db, foodIdByRow)
    }

    /**
     * The four new tables, with the statements the exported schema declares, verbatim.
     *
     * Verbatim because Room validates the finished database against that file: a table written by
     * hand that differs by a default, a null, or the order of a foreign key makes the migration fail
     * validation for a reason that takes an afternoon to find.
     */
    private fun createFoodTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `foods` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`brand` TEXT NOT NULL DEFAULT 'NA', " +
                "`barcode` TEXT, " +
                "`createdAtMillis` INTEGER NOT NULL, " +
                "`updatedAtMillis` INTEGER NOT NULL, " +
                "`hiddenAtMillis` INTEGER, " +
                "`kcalPer100g` REAL, `proteinPer100g` REAL, `carbsPer100g` REAL, " +
                "`fatPer100g` REAL, `per100gSource` TEXT, `per100gSourceRank` INTEGER, " +
                "`per100gConfidence` TEXT, `per100gSetAtMillis` INTEGER, " +
                "`unitName` TEXT, `kcalPerUnit` REAL, `proteinPerUnit` REAL, " +
                "`carbsPerUnit` REAL, `fatPerUnit` REAL, `perUnitSource` TEXT, " +
                "`perUnitSourceRank` INTEGER, `perUnitConfidence` TEXT, " +
                "`perUnitSetAtMillis` INTEGER, " +
                "`gramsPerUnit` REAL, `gramsPerUnitSource` TEXT, " +
                "`gramsPerUnitSourceRank` INTEGER, `gramsPerUnitConfidence` TEXT, " +
                "`gramsPerUnitSetAtMillis` INTEGER)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_foods_barcode` ON `foods` (`barcode`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_foods_hiddenAtMillis` " +
                "ON `foods` (`hiddenAtMillis`)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `food_names` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`foodId` INTEGER NOT NULL, " +
                "`displayName` TEXT NOT NULL, " +
                "`nameKey` TEXT NOT NULL, " +
                "`brandKey` TEXT NOT NULL, " +
                "`isPreferred` INTEGER NOT NULL, " +
                "`addedAtMillis` INTEGER NOT NULL, " +
                "FOREIGN KEY(`foodId`) REFERENCES `foods`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        // This index IS the identity rule. Not a convention and not a check in a repository: the
        // database refuses the second `yoghurt` + `na`, wherever it came from.
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_food_names_nameKey_brandKey` " +
                "ON `food_names` (`nameKey`, `brandKey`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_food_names_foodId_isPreferred` " +
                "ON `food_names` (`foodId`, `isPreferred`)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_meals` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`nameKey` TEXT NOT NULL, " +
                "`createdAtMillis` INTEGER NOT NULL, " +
                "`updatedAtMillis` INTEGER NOT NULL, " +
                "`hiddenAtMillis` INTEGER)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_meals_nameKey` " +
                "ON `saved_meals` (`nameKey`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_saved_meals_hiddenAtMillis` " +
                "ON `saved_meals` (`hiddenAtMillis`)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_meal_components` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`savedMealId` INTEGER NOT NULL, " +
                "`foodId` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, " +
                "`amount` REAL NOT NULL, " +
                "`countedAs` TEXT NOT NULL, " +
                "FOREIGN KEY(`savedMealId`) REFERENCES `saved_meals`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`foodId`) REFERENCES `foods`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_saved_meal_components_savedMealId` " +
                "ON `saved_meal_components` (`savedMealId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_saved_meal_components_foodId` " +
                "ON `saved_meal_components` (`foodId`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_saved_meal_components_savedMealId_foodId` " +
                "ON `saved_meal_components` (`savedMealId`, `foodId`)",
        )
    }

    /**
     * Every logged row, oldest first, with the moment its meal was logged.
     *
     * A LEFT join rather than an inner one so that a row whose meal has somehow gone still arrives.
     * It should be impossible — the cascade from `meals` sees to that — but the one thing this
     * migration must not do is leave a row behind, and an inner join would do it silently.
     */
    private fun readEveryLoggedRow(db: SupportSQLiteDatabase): List<LoggedFoodRow> {
        val rows = mutableListOf<LoggedFoodRow>()
        db.query(
            "SELECT f.`id`, f.`name`, f.`portionAmount`, f.`portionUnit`, f.`kcal`, " +
                "f.`proteinG`, f.`carbsG`, f.`fatG`, f.`source`, f.`confidence`, " +
                "COALESCE(m.`loggedAtMillis`, 0) " +
                "FROM `food_items` f LEFT JOIN `meals` m ON m.`id` = f.`mealId` " +
                "ORDER BY COALESCE(m.`loggedAtMillis`, 0) ASC, f.`id` ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                // The same rule the day screen reads these two columns by, so that a row it calls an
                // estimate cannot become something else on the way into a food.
                val (source, confidence) = readSource(
                    source = if (cursor.isNull(8)) null else cursor.getString(8),
                    confidence = if (cursor.isNull(9)) null else cursor.getString(9),
                )
                rows += LoggedFoodRow(
                    ref = cursor.getLong(0),
                    name = cursor.getString(1),
                    portionAmount = cursor.getDouble(2),
                    portionUnit = cursor.getString(3),
                    kcal = cursor.getInt(4),
                    proteinG = cursor.getInt(5),
                    carbsG = cursor.getInt(6),
                    fatG = cursor.getInt(7),
                    source = source,
                    confidence = confidence,
                    loggedAtMillis = cursor.getLong(10),
                )
            }
        }
        return rows
    }

    /** One food and one preferred name per group, and which food each logged row turned out to be. */
    private fun insertFoods(
        db: SupportSQLiteDatabase,
        foods: List<DerivedFood>,
        now: Long,
    ): Map<Long, Long> {
        val foodIdByRow = mutableMapOf<Long, Long>()
        foods.forEach { food ->
            val facts = food.facts
            db.execSQL(
                "INSERT INTO `foods` (" +
                    "`brand`, `barcode`, `createdAtMillis`, `updatedAtMillis`, `hiddenAtMillis`, " +
                    "`kcalPer100g`, `proteinPer100g`, `carbsPer100g`, `fatPer100g`, " +
                    "`per100gSource`, `per100gSourceRank`, `per100gConfidence`, " +
                    "`per100gSetAtMillis`, " +
                    "`unitName`, `kcalPerUnit`, `proteinPerUnit`, `carbsPerUnit`, `fatPerUnit`, " +
                    "`perUnitSource`, `perUnitSourceRank`, `perUnitConfidence`, " +
                    "`perUnitSetAtMillis`, " +
                    "`gramsPerUnit`, `gramsPerUnitSource`, `gramsPerUnitSourceRank`, " +
                    "`gramsPerUnitConfidence`, `gramsPerUnitSetAtMillis`" +
                    ") VALUES (?, NULL, ?, ?, NULL, " +
                    "?, ?, ?, ?, ?, ?, ?, ?, " +
                    "?, ?, ?, ?, ?, ?, ?, ?, ?, " +
                    // The weight, and its whole provenance block. Always null, for every food,
                    // with no exceptions and no clever cases.
                    "NULL, NULL, NULL, NULL, NULL)",
                arrayOf(
                    FoodKeys.NO_BRAND,
                    now,
                    now,
                    facts.per100g?.nutrients?.kcal,
                    facts.per100g?.nutrients?.proteinG,
                    facts.per100g?.nutrients?.carbsG,
                    facts.per100g?.nutrients?.fatG,
                    facts.per100g?.provenance?.source?.name,
                    facts.per100g?.provenance?.rank,
                    facts.per100g?.provenance?.confidence?.name,
                    facts.per100g?.provenance?.setAtMillis,
                    facts.perUnit?.unitName,
                    facts.perUnit?.nutrients?.kcal,
                    facts.perUnit?.nutrients?.proteinG,
                    facts.perUnit?.nutrients?.carbsG,
                    facts.perUnit?.nutrients?.fatG,
                    facts.perUnit?.provenance?.source?.name,
                    facts.perUnit?.provenance?.rank,
                    facts.perUnit?.provenance?.confidence?.name,
                    facts.perUnit?.provenance?.setAtMillis,
                ),
            )
            val foodId = lastInsertedId(db)
            db.execSQL(
                "INSERT INTO `food_names` (" +
                    "`foodId`, `displayName`, `nameKey`, `brandKey`, `isPreferred`, " +
                    "`addedAtMillis`) VALUES (?, ?, ?, ?, 1, ?)",
                arrayOf(foodId, food.displayName, food.nameKey, food.brandKey, now),
            )
            // No aliases are invented. A second name is something the owner creates by merging two
            // foods he has decided are one, and nothing here can decide that for him.
            food.rowRefs.forEach { ref -> foodIdByRow[ref] = foodId }
        }
        return foodIdByRow
    }

    private fun lastInsertedId(db: SupportSQLiteDatabase): Long =
        db.query("SELECT last_insert_rowid()").use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    /** `meals` gains which saved meal it came from, and whether that logging differed from it. */
    private fun rebuildMeals(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_meals` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`epochDay` INTEGER NOT NULL, " +
                "`loggedAtMillis` INTEGER NOT NULL, " +
                "`note` TEXT, " +
                "`savedMealId` INTEGER DEFAULT NULL, " +
                "`savedMealAdjusted` INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(`savedMealId`) REFERENCES `saved_meals`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        // Every existing logging event came from nothing and was adjusted from nothing, which is
        // what the two defaults say. No past day acquires a title it never had.
        db.execSQL(
            "INSERT INTO `_new_meals` (`id`, `epochDay`, `loggedAtMillis`, `note`) " +
                "SELECT `id`, `epochDay`, `loggedAtMillis`, `note` FROM `meals`",
        )
        db.execSQL("DROP TABLE `meals`")
        db.execSQL("ALTER TABLE `_new_meals` RENAME TO `meals`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_epochDay` ON `meals` (`epochDay`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_meals_savedMealId` ON `meals` (`savedMealId`)",
        )
    }

    /** `food_items` gains which food it was, and keeps every other column exactly as it stands. */
    private fun rebuildFoodItems(db: SupportSQLiteDatabase, foodIdByRow: Map<Long, Long>) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_food_items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`mealId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`portion` TEXT, " +
                "`portionAmount` REAL NOT NULL DEFAULT 0, " +
                "`portionUnit` TEXT NOT NULL DEFAULT '', " +
                "`kcal` INTEGER NOT NULL, " +
                "`proteinG` INTEGER NOT NULL, " +
                "`carbsG` INTEGER NOT NULL, " +
                "`fatG` INTEGER NOT NULL, " +
                "`source` TEXT NOT NULL, " +
                "`confidence` TEXT, " +
                "`foodId` INTEGER DEFAULT NULL, " +
                "FOREIGN KEY(`mealId`) REFERENCES `meals`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`foodId`) REFERENCES `foods`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        // Every column named and copied across as it stands. Nothing is computed, nothing is
        // reformatted, and nothing is filled in.
        db.execSQL(
            "INSERT INTO `_new_food_items` (" +
                "`id`, `mealId`, `name`, `portion`, `portionAmount`, `portionUnit`, " +
                "`kcal`, `proteinG`, `carbsG`, `fatG`, `source`, `confidence`) " +
                "SELECT `id`, `mealId`, `name`, `portion`, `portionAmount`, `portionUnit`, " +
                "`kcal`, `proteinG`, `carbsG`, `fatG`, `source`, `confidence` FROM `food_items`",
        )
        db.execSQL("DROP TABLE `food_items`")
        db.execSQL("ALTER TABLE `_new_food_items` RENAME TO `food_items`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_food_items_mealId` ON `food_items` (`mealId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_food_items_foodId` ON `food_items` (`foodId`)",
        )

        foodIdByRow.forEach { (rowId, foodId) ->
            db.execSQL(
                "UPDATE `food_items` SET `foodId` = ? WHERE `id` = ?",
                arrayOf<Any>(foodId, rowId),
            )
        }
    }
}
