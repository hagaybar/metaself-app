package com.metaself.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.day.MIGRATION_4_5
import com.metaself.app.data.day.MetaSelfDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The migration from version 1 to version 2, run against a real version 1 database.
 *
 * This is the test that stands between a schema change and the owner losing food he has logged. It
 * needs a native SQLite runtime and therefore runs on CI only, like every other database test here —
 * which means **a migration is unverified until CI has run**. Do not merge a schema change on a
 * local green.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MetaSelfDatabase::class.java,
    )

    @Test
    fun `a version 1 database migrates to version 2 and keeps its meals`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO meals (id, epochDay, loggedAtMillis, note) " +
                    "VALUES (1, 20699, 1000, NULL)",
            )
            db.execSQL(
                "INSERT INTO food_items " +
                    "(id, mealId, name, portion, kcal, proteinG, carbsG, fatG, source, confidence) " +
                    "VALUES (1, 1, 'Hummus', NULL, 180, 6, 12, 12, 'TYPED', NULL)",
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            MetaSelfDatabase.MIGRATION_1_2,
        )

        migrated.query("SELECT name, kcal FROM food_items").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("Hummus")
            assertThat(cursor.getInt(1)).isEqualTo(180)
        }
        migrated.query("SELECT COUNT(*) FROM weights").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(0)
        }
        migrated.close()
    }

    /**
     * The case this migration exists for: a two-portion row logged before the portion's numbers
     * were kept, arriving in a version that can adjust them.
     */
    @Test
    fun `a version 2 database migrates to version 3 and recovers the portions it can`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO meals (id, epochDay, loggedAtMillis, note) " +
                    "VALUES (1, 20699, 1000, NULL)",
            )
            db.execSQL(
                "INSERT INTO food_items " +
                    "(id, mealId, name, portion, kcal, proteinG, carbsG, fatG, source, confidence) " +
                    "VALUES (1, 1, 'Pizza', '2 slice', 570, 24, 68, 22, 'AI_ESTIMATE', 'MEDIUM')",
            )
            db.execSQL(
                "INSERT INTO food_items " +
                    "(id, mealId, name, portion, kcal, proteinG, carbsG, fatG, source, confidence) " +
                    "VALUES (2, 1, 'Hummus', 'a handful', 180, 6, 12, 12, 'TYPED', NULL)",
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            MetaSelfDatabase.MIGRATION_2_3,
        )

        migrated.query(
            "SELECT portionAmount, portionUnit, kcal FROM food_items ORDER BY id",
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getDouble(0)).isEqualTo(2.0)
            assertThat(cursor.getString(1)).isEqualTo("slice")
            // The migration recovers an amount. It must never rewrite a number the owner logged.
            assertThat(cursor.getInt(2)).isEqualTo(570)

            // Words with no number in them stay unadjustable rather than becoming a guess.
            assertThat(cursor.moveToNext()).isTrue()
            assertThat(cursor.getDouble(0)).isEqualTo(0.0)
            assertThat(cursor.getString(1)).isEmpty()
            assertThat(cursor.getInt(2)).isEqualTo(180)
        }
        migrated.close()
    }

    @Test
    fun `a version 3 database migrates to version 4 and keeps its food`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO meals (id, epochDay, loggedAtMillis, note) " +
                    "VALUES (1, 20699, 1000, NULL)",
            )
            db.execSQL(
                "INSERT INTO food_items " +
                    "(id, mealId, name, portion, portionAmount, portionUnit, kcal, proteinG, " +
                    "carbsG, fatG, source, confidence) " +
                    "VALUES (1, 1, 'Pizza', '2 slice', 2.0, 'slice', 570, 24, 68, 22, " +
                    "'AI_ESTIMATE', 'MEDIUM')",
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            true,
            MetaSelfDatabase.MIGRATION_3_4,
        )

        // The new table exists and is empty.
        migrated.query("SELECT COUNT(*) FROM products").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(0)
        }
        // And nothing that was already there was touched.
        migrated.query("SELECT name, kcal, portionAmount FROM food_items").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("Pizza")
            assertThat(cursor.getInt(1)).isEqualTo(570)
            assertThat(cursor.getDouble(2)).isEqualTo(2.0)
        }
        migrated.close()
    }

    // --- Version 5: foods and meals become entities -------------------------------------------

    /**
     * A record of lifelike shape, so the assertions below are about something recognisable: one
     * food logged on three days at two different amounts, another counted in slices and once
     * weighed, and one old row that recorded words and no number at all.
     */
    private fun SupportSQLiteDatabase.seedVersion4Record() {
        execSQL("INSERT INTO meals (id, epochDay, loggedAtMillis, note) VALUES (1, 20690, 1000, NULL)")
        execSQL("INSERT INTO meals (id, epochDay, loggedAtMillis, note) VALUES (2, 20691, 2000, NULL)")
        execSQL("INSERT INTO meals (id, epochDay, loggedAtMillis, note) VALUES (3, 20692, 3000, NULL)")

        fun item(
            id: Int,
            mealId: Int,
            name: String,
            portion: String?,
            amount: Double,
            unit: String,
            kcal: Int,
            source: String,
            confidence: String?,
        ) = execSQL(
            "INSERT INTO food_items (id, mealId, name, portion, portionAmount, portionUnit, " +
                "kcal, proteinG, carbsG, fatG, source, confidence) VALUES " +
                "($id, $mealId, '$name', ${portion?.let { "'$it'" } ?: "NULL"}, $amount, '$unit', " +
                "$kcal, 5, 10, 2, '$source', ${confidence?.let { "'$it'" } ?: "NULL"})",
        )

        item(1, 1, "Yoghurt", "180 g", 180.0, "g", 130, "AI_ESTIMATE", "MEDIUM")
        item(2, 1, "Bread", "2 slice", 2.0, "slice", 160, "AI_ESTIMATE", "MEDIUM")
        item(3, 2, "yoghurt", "200 g", 200.0, "g", 144, "TYPED", null)
        item(4, 2, "Bread", "60 g", 60.0, "g", 150, "LABEL", null)
        item(5, 3, "YOGHURT", "180 g", 180.0, "g", 130, "REPEATED", null)
        item(6, 3, "Stew", "a bowl", 0.0, "", 400, "AI_ESTIMATE", "LOW")
    }

    private fun SupportSQLiteDatabase.countOf(sql: String): Int =
        query(sql).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.getInt(0)
        }

    /** What each day was worth, which is the number that must not move. */
    private fun SupportSQLiteDatabase.kcalByDay(): Map<Long, Int> = buildMap {
        query(
            "SELECT m.epochDay, SUM(f.kcal) FROM meals m " +
                "INNER JOIN food_items f ON f.mealId = m.id GROUP BY m.epochDay ORDER BY m.epochDay",
        ).use { cursor ->
            while (cursor.moveToNext()) put(cursor.getLong(0), cursor.getInt(1))
        }
    }

    /**
     * **The assertion the whole change rests on.** Every day the owner has already logged is worth
     * after the conversion exactly what it is worth now.
     *
     * If this ever fails, nothing else in this file matters.
     */
    @Test
    fun `no day changes what it was worth`() {
        assumeSqliteRuntime()

        val before = helper.createDatabase(TEST_DB, 4).use { db ->
            db.seedVersion4Record()
            db.kcalByDay()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        assertThat(migrated.kcalByDay()).isEqualTo(before)
        assertThat(before).isNotEmpty()
        migrated.close()
    }

    /**
     * Dropping and rebuilding `meals` fires a cascade that would delete every logged row, if
     * foreign keys were being enforced during a migration. They are not — but this is the assertion
     * that would notice the day that changed.
     */
    @Test
    fun `not one logged row and not one meal is lost`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 4).use { db -> db.seedVersion4Record() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        assertThat(migrated.countOf("SELECT COUNT(*) FROM food_items")).isEqualTo(6)
        assertThat(migrated.countOf("SELECT COUNT(*) FROM meals")).isEqualTo(3)
        migrated.close()
    }

    /** Every column a logged row already had is copied across untouched. */
    @Test
    fun `nothing on a logged row is rewritten`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 4).use { db -> db.seedVersion4Record() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        migrated.query(
            "SELECT name, portion, portionAmount, portionUnit, kcal, proteinG, carbsG, fatG, " +
                "source, confidence FROM food_items WHERE id = 5",
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            // The variant typed on the day, kept verbatim, even though the food is now called
            // "Yoghurt" and that is what the day screen will show.
            assertThat(cursor.getString(0)).isEqualTo("YOGHURT")
            assertThat(cursor.getString(1)).isEqualTo("180 g")
            assertThat(cursor.getDouble(2)).isEqualTo(180.0)
            assertThat(cursor.getString(3)).isEqualTo("g")
            assertThat(cursor.getInt(4)).isEqualTo(130)
            assertThat(cursor.getInt(5)).isEqualTo(5)
            assertThat(cursor.getInt(6)).isEqualTo(10)
            assertThat(cursor.getInt(7)).isEqualTo(2)
            assertThat(cursor.getString(8)).isEqualTo("REPEATED")
            assertThat(cursor.isNull(9)).isTrue()
        }
        migrated.close()
    }

    @Test
    fun `every logged row ends up pointing at a food`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 4).use { db -> db.seedVersion4Record() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        assertThat(migrated.countOf("SELECT COUNT(*) FROM food_items WHERE foodId IS NULL"))
            .isEqualTo(0)
        migrated.close()
    }

    /** Three spellings of one yoghurt are one food; the bread and the stew are two more. */
    @Test
    fun `the same food logged under several spellings becomes one food`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 4).use { db -> db.seedVersion4Record() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        assertThat(migrated.countOf("SELECT COUNT(*) FROM foods")).isEqualTo(3)
        assertThat(
            migrated.countOf("SELECT COUNT(DISTINCT foodId) FROM food_items WHERE name LIKE '%oghurt%' OR name = 'YOGHURT'"),
        ).isEqualTo(1)
        migrated.close()
    }

    @Test
    fun `each food has exactly one preferred name and no invented aliases`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 4).use { db -> db.seedVersion4Record() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        assertThat(migrated.countOf("SELECT COUNT(*) FROM food_names")).isEqualTo(3)
        assertThat(migrated.countOf("SELECT COUNT(*) FROM food_names WHERE isPreferred = 1"))
            .isEqualTo(3)
        migrated.close()
    }

    /** The identity rule, enforced by the index rather than by anybody remembering it. */
    @Test
    fun `no two foods share a name and a brand`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 4).use { db -> db.seedVersion4Record() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        val distinct = migrated.countOf(
            "SELECT COUNT(DISTINCT nameKey || '|' || brandKey) FROM food_names",
        )
        assertThat(distinct).isEqualTo(migrated.countOf("SELECT COUNT(*) FROM foods"))
        migrated.close()
    }

    /**
     * Each fact filled from the best row of its own shape. The bread was logged in slices and
     * weighed in grams, and it comes out knowing both — which the earlier design could not do.
     */
    @Test
    fun `a food logged both ways learns both`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 4).use { db -> db.seedVersion4Record() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        migrated.query(
            "SELECT f.kcalPer100g, f.per100gSource, f.kcalPerUnit, f.unitName, f.perUnitSource, " +
                "f.per100gSourceRank " +
                "FROM foods f INNER JOIN food_names n ON n.foodId = f.id WHERE n.nameKey = 'bread'",
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            // 150 kcal for 60 g, worked back from a label row, so copied (D43, issue #29): the
            // upgrade runs the same conversion as a rename or a restore, and gives the same answer.
            assertThat(cursor.getDouble(0)).isWithin(0.001).of(250.0)
            assertThat(cursor.getString(1)).isEqualTo("REPEATED")
            assertThat(cursor.getInt(5)).isEqualTo(0)
            // 160 kcal for 2 slices, estimated.
            assertThat(cursor.getDouble(2)).isWithin(0.001).of(80.0)
            assertThat(cursor.getString(3)).isEqualTo("slice")
            assertThat(cursor.getString(4)).isEqualTo("AI_ESTIMATE")
        }
        migrated.close()
    }

    /**
     * A typed 144 for 200 g arrives on the second day; a model guessed 130 for 180 g on the first
     * and a repeat copied it on the third. The typed number wins, though it is not the most
     * recent, because the ranking is by source and never by date.
     */
    @Test
    fun `the best source wins, not the most recent row`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 4).use { db -> db.seedVersion4Record() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        migrated.query(
            "SELECT f.kcalPer100g, f.per100gSource, f.per100gSourceRank, f.per100gConfidence " +
                "FROM foods f INNER JOIN food_names n ON n.foodId = f.id " +
                "WHERE n.nameKey = 'yoghurt'",
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getDouble(0)).isWithin(0.001).of(72.0)
            assertThat(cursor.getString(1)).isEqualTo("TYPED")
            assertThat(cursor.getInt(2)).isEqualTo(2)
            assertThat(cursor.isNull(3)).isTrue()
        }
        migrated.close()
    }

    /** A row that recorded words and no number becomes one portion, worth what it was worth. */
    @Test
    fun `a row with no amount becomes one portion`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 4).use { db -> db.seedVersion4Record() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        migrated.query(
            "SELECT f.unitName, f.kcalPerUnit, f.kcalPer100g FROM foods f " +
                "INNER JOIN food_names n ON n.foodId = f.id WHERE n.nameKey = 'stew'",
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("portion")
            assertThat(cursor.getDouble(1)).isWithin(0.001).of(400.0)
            assertThat(cursor.isNull(2)).isTrue()
        }
        migrated.close()
    }

    /**
     * The cheapest possible test of the one derivation the design forbids. Nothing in the record
     * says what a slice weighs, and dividing per-slice calories by per-100-g calories would present
     * an estimate as a measurement of a physical object.
     */
    @Test
    fun `no food comes out of the conversion knowing what one of it weighs`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 4).use { db -> db.seedVersion4Record() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        assertThat(migrated.countOf("SELECT COUNT(*) FROM foods WHERE gramsPerUnit IS NOT NULL"))
            .isEqualTo(0)
        migrated.close()
    }

    /** Every food is loggable: it knows at least one of the two ways of counting. */
    @Test
    fun `every food knows at least one way of counting`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 4).use { db -> db.seedVersion4Record() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        assertThat(
            migrated.countOf(
                "SELECT COUNT(*) FROM foods WHERE kcalPer100g IS NULL AND kcalPerUnit IS NULL",
            ),
        ).isEqualTo(0)
        migrated.close()
    }

    /**
     * A meal is only something the owner built and named. The conversion must not look at which
     * items happened to be logged together and manufacture one, and this is here so that nobody
     * helpfully adds it later.
     */
    @Test
    fun `the conversion manufactures no meals`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 4).use { db -> db.seedVersion4Record() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        assertThat(migrated.countOf("SELECT COUNT(*) FROM saved_meals")).isEqualTo(0)
        assertThat(migrated.countOf("SELECT COUNT(*) FROM saved_meal_components")).isEqualTo(0)
        assertThat(migrated.countOf("SELECT COUNT(*) FROM meals WHERE savedMealId IS NOT NULL"))
            .isEqualTo(0)
        assertThat(migrated.countOf("SELECT COUNT(*) FROM meals WHERE savedMealAdjusted != 0"))
            .isEqualTo(0)
        migrated.close()
    }

    /** The packets seen before are a cache of what a label says, not a list of food he eats. */
    @Test
    fun `the conversion creates no food from a scanned packet`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 4).use { db ->
            db.seedVersion4Record()
            db.execSQL(
                "INSERT INTO products (barcode, name, brand, kcalPer100g, proteinPer100g, " +
                    "carbsPer100g, fatPer100g, servingSizeG, savedAtMillis) VALUES " +
                    "('2000012345678', 'Protein bar', 'Dairyco', 422.0, 33.0, 38.0, 14.0, 45.0, 1)",
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        assertThat(migrated.countOf("SELECT COUNT(*) FROM foods")).isEqualTo(3)
        assertThat(migrated.countOf("SELECT COUNT(*) FROM foods WHERE barcode IS NOT NULL"))
            .isEqualTo(0)
        // And the manufacturer's serving size is not copied into what one of it weighs: a serving
        // is a portion the manufacturer chose, and what one bar weighs is a fact about an object.
        assertThat(migrated.countOf("SELECT COUNT(*) FROM foods WHERE gramsPerUnit IS NOT NULL"))
            .isEqualTo(0)
        migrated.close()
    }

    /** The name kept is the cleaned-up form used most, not whichever variant was typed last. */
    @Test
    fun `the food is named by the spelling used most`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 4).use { db -> db.seedVersion4Record() }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        migrated.query("SELECT displayName FROM food_names WHERE nameKey = 'yoghurt'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            // "Yoghurt", "yoghurt" and "YOGHURT" each appear once, so the tie goes to the most
            // recent — and the most recent is the row logged on the third morning.
            assertThat(cursor.getString(0)).isEqualTo("YOGHURT")
        }
        migrated.close()
    }

    /** An empty record converts to an empty food list rather than failing. */
    @Test
    fun `a record with nothing in it converts to nothing`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 4).use { }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        assertThat(migrated.countOf("SELECT COUNT(*) FROM foods")).isEqualTo(0)
        assertThat(migrated.countOf("SELECT COUNT(*) FROM food_items")).isEqualTo(0)
        migrated.close()
    }

    /** Everything that was already there survives the two rebuilds. */
    @Test
    fun `the weights and the packets seen before are untouched`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 4).use { db ->
            db.seedVersion4Record()
            db.execSQL("INSERT INTO weights (epochDay, kg) VALUES (20000, 80.0)")
            db.execSQL(
                "INSERT INTO products (barcode, name, brand, kcalPer100g, proteinPer100g, " +
                    "carbsPer100g, fatPer100g, servingSizeG, savedAtMillis) VALUES " +
                    "('2000012345678', 'Protein bar', 'Dairyco', 422.0, 33.0, 38.0, 14.0, 45.0, 1)",
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        assertThat(migrated.countOf("SELECT COUNT(*) FROM weights")).isEqualTo(1)
        assertThat(migrated.countOf("SELECT COUNT(*) FROM products")).isEqualTo(1)
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
