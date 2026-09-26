package com.metaself.app.domain.backup

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.backup.BackupCodec
import com.metaself.app.data.backup.BackupFoods
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.GramsPerUnit
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.PerUnit
import com.metaself.app.domain.food.Provenance
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class BackupCodecTest {

    /** Every figure below is invented, chosen only so each field can be told apart from the others. */
    private val full = Backup(
        version = Backup.CURRENT_VERSION,
        exportedAtMillis = 1_772_000_000_000,
        profile = BackupProfile(
            heightCm = 180,
            birthYear = 1980,
            sex = "MALE",
            weightKg = 80.0,
            activity = "MODERATE",
            goalDirection = "LOSE",
            goalKgPerWeek = 0.5,
            goalTargetKg = 75.0,
            allowBelowFloor = false,
        ),
        meals = listOf(
            BackupMeal(
                epochDay = 20_699,
                loggedAtMillis = 1_000,
                note = null,
                items = listOf(
                    BackupItem(
                        name = "יוגורט בסגנון יווני",
                        portion = "150 g",
                        portionAmount = 150.0,
                        portionUnit = "g",
                        kcal = 150,
                        proteinG = 12,
                        carbsG = 6,
                        fatG = 8,
                        source = "AI_ESTIMATE",
                        confidence = "HIGH",
                    ),
                ),
            ),
        ),
        weights = listOf(BackupWeight(20_699, 80.0), BackupWeight(20_700, 79.5)),
        revision = BackupRevision(20_699, 80.0, 2090, 2100),
        revisionSeen = true,
        arrival = BackupArrival(75.0, 20_800),
        milestones = mapOf("FIRST_KG" to 20_710L),
        reminder = BackupReminder(enabled = true, hour = 20, minute = 0),
        ai = BackupAi(model = "gpt-4o-mini", dailyCeiling = 30),
        workouts = listOf(
            BackupWorkout(
                epochDay = 20_699,
                startedAtMillis = 1_000,
                durationMinutes = 30,
                kind = "RUN",
                title = "Running",
                distanceM = 5_000,
                energyKcal = 300,
                energySource = "BAND",
                source = "SYNCED",
                origin = "com.example.band",
                originId = "abc-1",
                avgHeartRate = 140,
                maxHeartRate = 160,
                zoneSeconds = "0,300,900,600,0",
                zoneMaxSource = "ESTIMATED",
            ),
        ),
        sleep = listOf(
            BackupSleep(
                epochDay = 20_699,
                startMillis = 1_000,
                endMillis = 3_000,
                origin = "com.example.band",
                recordId = "s-1",
                stages = listOf(BackupSleepStage("DEEP", 1_000, 2_000)),
            ),
        ),
        healthDays = listOf(
            BackupHealthDay(epochDay = 20_699, computedAtMillis = 5_000, steps = 9_000, stepsSource = "TOTAL"),
        ),
        movementCorrections = listOf(
            BackupMovementCorrection(epochDay = 20_699, steps = 9_000, setAtMillis = 2_000),
        ),
    )

    @Test
    fun `everything survives a round trip`() {
        assertThat(BackupCodec.decode(BackupCodec.encode(full))).isEqualTo(full)
    }

    @Test
    fun `the health record is written in words a person can check`() {
        val text = BackupCodec.encode(full)

        assertThat(text).contains("\"workouts\"")
        assertThat(text).contains("\"energy_source\": \"BAND\"")
        assertThat(text).contains("\"sleep\"")
        assertThat(text).contains("\"health_days\"")
        assertThat(text).contains("\"steps_source\": \"TOTAL\"")
        assertThat(text).contains("\"movement_corrections\"")
    }

    /** D71: the raw readings go to Drive by month, never into the daily file. */
    @Test
    fun `the daily file has no raw readings`() {
        assertThat(BackupCodec.encode(full)).doesNotContain("readings")
    }

    /** Every file written before the health record existed stays restorable, and brings none. */
    @Test
    fun `a version 2 file still reads, with no health record`() {
        val version2 = """{"version": 2, "exported_at": 1000, "meals": [], "weights": []}"""

        val read = BackupCodec.decode(version2)!!

        assertThat(read.workouts).isEmpty()
        assertThat(read.sleep).isEmpty()
        assertThat(read.healthDays).isEmpty()
        assertThat(read.movementCorrections).isEmpty()
    }

    @Test
    fun `the format is version 3`() {
        assertThat(Backup.CURRENT_VERSION).isEqualTo(3)
    }

    /**
     * The one file the owner is expected to open and check. A backup nobody can read is a backup
     * nobody has checked, and the first time it matters is the worst time to find it was empty.
     */
    @Test
    fun `the file is readable, and a saved food is in it as words`() {
        val text = BackupCodec.encode(full)

        assertThat(text).contains("יוגורט בסגנון יווני")
        assertThat(text).contains("\"kcal\": 150")
        assertThat(text.lines().size).isGreaterThan(10)
    }

    /**
     * There is nowhere in the format to put a key, and this is the test that keeps it that way. An
     * export ends up in a cloud drive and an email; a credential that spends money does not.
     */
    @Test
    fun `nothing that looks like an api key can appear in an export`() {
        val text = BackupCodec.encode(full).lowercase()

        assertThat(text).doesNotContain("api_key")
        assertThat(text).doesNotContain("apikey")
        assertThat(text).doesNotContain("sk-")
    }

    /** No partial restore: a half-restored record is worse than a failed one. */
    @Test
    fun `a file from a later version is refused whole`() {
        // Written against the CURRENT version rather than a literal 1: the last time this was a
        // literal, raising the version made the substitution a no-op and the test passed while
        // testing nothing.
        val fromTheFuture = BackupCodec.encode(full)
            .replace("\"version\": ${Backup.CURRENT_VERSION}", "\"version\": 99")

        assertThat(BackupCodec.decode(fromTheFuture)).isNull()
    }

    @Test
    fun `rubbish is refused without throwing`() {
        assertThat(BackupCodec.decode("this is not a backup")).isNull()
        assertThat(BackupCodec.decode("")).isNull()
        assertThat(BackupCodec.decode("{\"version\":")).isNull()
    }

    @Test
    fun `an empty phone exports an empty file rather than nothing at all`() {
        val empty = Backup(exportedAtMillis = 1)

        val decoded = BackupCodec.decode(BackupCodec.encode(empty))!!

        assertThat(decoded.meals).isEmpty()
        assertThat(decoded.profile).isNull()
    }

    /**
     * The file's format for every ordinary number is the one it always had. Written against a
     * plain encoder with the codec's settings, so a change to how a finite number, a null or a
     * string is printed shows up here rather than in a file that no longer restores.
     */
    @Test
    fun `an ordinary file is written exactly as a plain encoder writes it`() {
        val plain = Json {
            prettyPrint = true
            encodeDefaults = true
        }
        val withFoods = full.copy(foods = listOf(BackupFoods.toBackup(aBar())))

        assertThat(BackupCodec.encode(withFoods))
            .isEqualTo(plain.encodeToString(Backup.serializer(), withFoods))
    }

    /**
     * Issue #7. Until 0.32.6 (D42) a food form took "Infinity", and the encoder refuses a number
     * that is not finite — so one such food made every export and every daily copy fail. The group
     * holding it is written as null, which is how the file already says "not known"; the food's
     * other groups are written as they are.
     */
    @Test
    fun `a food holding an infinite figure is exported, with that group as not known`() {
        val broken = aBar(per100gKcal = Double.POSITIVE_INFINITY)

        val text = BackupCodec.encode(Backup(foods = listOf(BackupFoods.toBackup(broken))))
        val food = BackupCodec.decode(text)!!.foods.single()

        assertThat(food.per100g).isNull()
        assertThat(food.perUnit!!.kcal).isEqualTo(190.0)
        assertThat(food.gramsPerUnit!!.grams).isEqualTo(45.0)
    }

    @Test
    fun `an infinite weight of one is exported as not known, and the rest of the food with it`() {
        val broken = aBar(gramsPerUnit = Double.POSITIVE_INFINITY)

        val food = BackupCodec.decode(
            BackupCodec.encode(Backup(foods = listOf(BackupFoods.toBackup(broken)))),
        )!!.foods.single()

        assertThat(food.gramsPerUnit).isNull()
        assertThat(food.per100g!!.kcal).isEqualTo(422.0)
    }

    /**
     * A logged row keeps what it holds (D42): the export does not judge it. But an amount saved as
     * "Infinity" before 0.32.6 cannot be written as a number, so it is written as null and read
     * back as no amount — the row, its name and its calories all restore.
     */
    @Test
    fun `a logged row with an infinite amount is exported, and restores with no amount`() {
        val item = full.meals.single().items.single().copy(portionAmount = Double.POSITIVE_INFINITY)
        val backup = full.copy(meals = listOf(full.meals.single().copy(items = listOf(item))))

        val text = BackupCodec.encode(backup)
        val restored = BackupCodec.decode(text)!!.meals.single().items.single()

        assertThat(text).contains("\"portion_amount\": null")
        assertThat(restored.portionAmount).isEqualTo(0.0)
        assertThat(restored.kcal).isEqualTo(150)
        assertThat(restored.name).isEqualTo(item.name)
    }

    /** A meal part with an infinite amount is read back as none, which a restore already skips. */
    @Test
    fun `a meal part with an infinite amount is exported, and restores as no amount`() {
        val backup = full.copy(
            savedMeals = listOf(
                BackupSavedMeal(
                    name = "Salad",
                    components = listOf(
                        BackupMealComponent("cucumber|na", Double.POSITIVE_INFINITY, "GRAMS"),
                        BackupMealComponent("feta|na", 30.0, "GRAMS"),
                    ),
                ),
            ),
        )

        val parts = BackupCodec.decode(BackupCodec.encode(backup))!!.savedMeals.single().components

        assertThat(parts.map { it.amount }).containsExactly(0.0, 30.0).inOrder()
    }

    /** Invented figures throughout, chosen to be told apart. */
    private fun aBar(
        per100gKcal: Double = 422.0,
        gramsPerUnit: Double = 45.0,
    ) = Food(
        id = 1,
        name = "Protein bar",
        brand = "Dairyco",
        facts = FoodFacts(
            per100g = PerHundredGrams(
                Nutrients(per100gKcal, 33.0, 38.0, 14.0),
                Provenance(Source.LABEL, null, 1_000),
            ),
            perUnit = PerUnit(
                "bar",
                Nutrients(190.0, 15.0, 17.0, 6.0),
                Provenance(Source.TYPED, null, 1_000),
            ),
            gramsPerUnit = GramsPerUnit(gramsPerUnit, Provenance(Source.TYPED, null, 1_000)),
        ),
    )
}
