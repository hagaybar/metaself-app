package com.metaself.app.data.food

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.assumeSqliteRuntime
import com.metaself.app.data.day.MetaSelfDatabase
import com.metaself.app.data.time.Now
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodKeys
import com.metaself.app.domain.food.GramsPerUnit
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.PerUnit
import com.metaself.app.domain.food.Provenance
import com.metaself.app.domain.food.ReplacedFacts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The one door, against a real database.
 *
 * Two things are being protected. The first is that a food comes into existence exactly once no
 * matter which entrance it arrives by, which is what stops the list filling with duplicates the
 * owner then has to merge by hand. The second is that the ranking is enforcement rather than
 * decoration: **a guess must change nothing about a number the owner typed, and a scan must leave
 * what one of something weighs alone.** Those two are the tests that keep the guarded statements
 * honest, and without them the `WHERE` clauses are just decoration that happens to compile.
 *
 * Needs a native SQLite runtime, so it stands aside on the development box and runs in CI.
 */
@RunWith(RobolectricTestRunner::class)
class RoomFoodRepositoryTest {

    private lateinit var database: MetaSelfDatabase
    private lateinit var repository: FoodRepository
    private var moment = 1_000L

    @Before
    fun setUp() {
        assumeSqliteRuntime()
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MetaSelfDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomFoodRepository(database, database.foodDao(), Now { moment })
    }

    @After
    fun tearDown() {
        if (this::database.isInitialized) database.close()
    }

    private fun per100g(
        kcal: Double = 100.0,
        source: Source = Source.AI_ESTIMATE,
        confidence: Confidence? = Confidence.MEDIUM,
    ) = PerHundredGrams(
        Nutrients(kcal, 5.0, 10.0, 2.0),
        Provenance(source, confidence, setAtMillis = moment),
    )

    private fun perUnit(
        unitName: String = "bar",
        kcal: Double = 190.0,
        source: Source = Source.TYPED,
        confidence: Confidence? = null,
    ) = PerUnit(unitName, Nutrients(kcal, 15.0, 17.0, 6.0), Provenance(source, confidence, moment))

    private fun weighs(grams: Double = 45.0, source: Source = Source.TYPED) =
        GramsPerUnit(grams, Provenance(source, null, moment))

    // --- One door ---------------------------------------------------------------------------------

    @Test
    fun `a food asked for twice is made once`() = runTest {
        val first = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g()))
        val second = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g()))

        assertThat(first.wasCreated).isTrue()
        assertThat(second.wasCreated).isFalse()
        assertThat(second.food.id).isEqualTo(first.food.id)
        assertThat(repository.observeAll().first()).hasSize(1)
    }

    /** The naming rule, reaching the database: these are one food and not three. */
    @Test
    fun `spellings that normalise alike are one food`() = runTest {
        repository.findOrCreate("Low-fat yoghurt", facts = FoodFacts(per100g = per100g()))
        repository.findOrCreate("low fat yoghurt", facts = FoodFacts(per100g = per100g()))
        repository.findOrCreate("LOW  FAT  YOGHURT", facts = FoodFacts(per100g = per100g()))

        assertThat(repository.observeAll().first()).hasSize(1)
    }

    @Test
    fun `two brands of the same name are two foods`() = runTest {
        repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g()))
        repository.findOrCreate("Yoghurt", brand = "Dairyco", facts = FoodFacts(per100g = per100g()))

        assertThat(repository.observeAll().first()).hasSize(2)
    }

    /**
     * Every food answering to a name, across brands and alternative names — looked up, never made.
     *
     * What the repair of issue #22 asks before putting a detached row back: it acts only on an
     * answer of exactly one. Runs in CI only, like every test here.
     */
    @Test
    fun `the foods answering to a name are found across brands, and none is made`() = runTest {
        val plain = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g())).food
        val dairyco = repository
            .findOrCreate("Yoghurt", brand = "Dairyco", facts = FoodFacts(per100g = per100g())).food

        assertThat(repository.foodIdsNamed("yoghurt")).containsExactly(plain.id, dairyco.id)
        assertThat(repository.foodIdsNamed("Halva")).isEmpty()
        assertThat(repository.observeAll().first()).hasSize(2)
    }

    @Test
    fun `a food described in words carries the brand that means none`() = runTest {
        val made = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g()))

        assertThat(made.food.brand).isEqualTo(FoodKeys.NO_BRAND)
    }

    /**
     * A food described last week and scanned today is one food. The packet is asked about first
     * because it identifies the thing more exactly than its name does.
     */
    @Test
    fun `scanning a food already described attaches the packet rather than making a second`() =
        runTest {
            val described = repository.findOrCreate("Protein bar", facts = FoodFacts(perUnit = perUnit()))

            val scanned = repository.findOrCreate(
                "Protein bar",
                facts = FoodFacts(per100g = per100g(kcal = 422.0, source = Source.LABEL, confidence = null)),
                barcode = "2000012345678",
            )

            assertThat(scanned.wasCreated).isFalse()
            assertThat(scanned.food.id).isEqualTo(described.food.id)
            assertThat(scanned.food.barcode).isEqualTo("2000012345678")
            assertThat(repository.observeAll().first()).hasSize(1)
        }

    @Test
    fun `a packet already known is found by its barcode whatever it is called this time`() =
        runTest {
            val first = repository.findOrCreate(
                "Protein bar",
                facts = FoodFacts(per100g = per100g()),
                barcode = "2000012345678",
            )

            val again = repository.findOrCreate(
                "Chocolate protein bar",
                facts = FoodFacts(per100g = per100g()),
                barcode = "2000012345678",
            )

            assertThat(again.food.id).isEqualTo(first.food.id)
            assertThat(repository.observeAll().first()).hasSize(1)
        }

    // --- The ranking, which is the whole reason the statements are narrow ------------------------

    /**
     * **The test that makes the ranking enforcement rather than decoration.** A model's guess
     * arriving at a number the owner typed must change nothing at all.
     */
    @Test
    fun `a guess changes nothing about a number the owner typed`() = runTest {
        val food = repository.findOrCreate(
            "Yoghurt",
            facts = FoodFacts(per100g = per100g(kcal = 60.0, source = Source.TYPED, confidence = null)),
        ).food

        repository.offerFacts(food.id, FoodFacts(per100g = per100g(kcal = 999.0)))

        val after = repository.byId(food.id)!!
        assertThat(after.facts.per100g!!.nutrients.kcal).isWithin(0.001).of(60.0)
        assertThat(after.facts.per100g!!.provenance.source).isEqualTo(Source.TYPED)
    }

    @Test
    fun `a packet beats a number the owner typed, silently`() = runTest {
        val food = repository.findOrCreate(
            "Yoghurt",
            facts = FoodFacts(per100g = per100g(kcal = 60.0, source = Source.TYPED, confidence = null)),
        ).food

        repository.offerFacts(
            food.id,
            FoodFacts(per100g = per100g(kcal = 72.0, source = Source.LABEL, confidence = null)),
        )

        assertThat(repository.byId(food.id)!!.facts.per100g!!.nutrients.kcal).isWithin(0.001).of(72.0)
    }

    /** `>=` and not `>`: a fresh scan replaces an older scan, and a re-typed number an older one. */
    @Test
    fun `a second reading of the same credibility replaces the first`() = runTest {
        val food = repository.findOrCreate(
            "Yoghurt",
            facts = FoodFacts(per100g = per100g(kcal = 60.0, source = Source.TYPED, confidence = null)),
        ).food

        repository.offerFacts(
            food.id,
            FoodFacts(per100g = per100g(kcal = 65.0, source = Source.TYPED, confidence = null)),
        )

        assertThat(repository.byId(food.id)!!.facts.per100g!!.nutrients.kcal).isWithin(0.001).of(65.0)
    }

    @Test
    fun `a fact nothing knew yet is learned by anything at all`() = runTest {
        val food = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g())).food

        repository.offerFacts(food.id, FoodFacts(perUnit = perUnit(unitName = "pot", kcal = 130.0)))

        assertThat(repository.byId(food.id)!!.facts.perUnit!!.unitName).isEqualTo("pot")
    }

    /**
     * **The test that pins the trap the earlier design had.** A scan writes what 100 grams are worth
     * and nothing else. Re-scanning a food counted in bars must not touch the per-bar numbers and
     * must not touch what one bar weighs — otherwise the "2" the owner typed yesterday meaning two
     * bars would mean two grams today.
     */
    @Test
    fun `a packet's figures leave what one is worth and what one weighs alone`() = runTest {
        val food = repository.findOrCreate(
            "Protein bar",
            facts = FoodFacts(perUnit = perUnit(kcal = 190.0), gramsPerUnit = weighs(grams = 45.0)),
        ).food

        repository.offerFacts(
            food.id,
            FoodFacts(per100g = per100g(kcal = 422.0, source = Source.LABEL, confidence = null)),
        )

        val after = repository.byId(food.id)!!
        assertThat(after.facts.per100g!!.nutrients.kcal).isWithin(0.001).of(422.0)
        assertThat(after.facts.perUnit!!.nutrients.kcal).isWithin(0.001).of(190.0)
        assertThat(after.facts.perUnit!!.unitName).isEqualTo("bar")
        assertThat(after.facts.gramsPerUnit!!.grams).isWithin(0.001).of(45.0)
        assertThat(after.facts.gramsPerUnit!!.provenance.source).isEqualTo(Source.TYPED)
    }

    /**
     * A figure worked back from a logged row is filed as copied from a past meal, rank 0 (D43,
     * issue #29). The guarded statements must let it fill a blank and move neither a number he typed
     * nor one read off a packet — the Room half of what `LoggedFoodsTest` asserts over the stand-in,
     * so the two are shown to rank alike.
     */
    @Test
    fun `a copied figure fills a blank but moves neither a typed nor a label one`() = runTest {
        val blank = repository.findOrCreate(
            "Rice cakes",
            facts = FoodFacts(perUnit = perUnit(unitName = "cake", kcal = 35.0)),
        ).food
        val typed = repository.findOrCreate(
            "Puffed rice",
            facts = FoodFacts(per100g = per100g(kcal = 380.0, source = Source.TYPED, confidence = null)),
        ).food
        val label = repository.findOrCreate(
            "Corn cakes",
            facts = FoodFacts(per100g = per100g(kcal = 387.4, source = Source.LABEL, confidence = null)),
        ).food
        val typedBefore = repository.byId(typed.id)!!.facts.per100g
        val labelBefore = repository.byId(label.id)!!.facts.per100g
        val copied = FoodFacts(per100g = per100g(kcal = 386.7, source = Source.REPEATED, confidence = null))

        repository.offerFacts(blank.id, copied)
        repository.offerFacts(typed.id, copied)
        repository.offerFacts(label.id, copied)

        val learned = repository.byId(blank.id)!!.facts
        assertThat(learned.per100g!!.nutrients.kcal).isWithin(0.001).of(386.7)
        assertThat(learned.per100g!!.provenance.source).isEqualTo(Source.REPEATED)
        assertThat(learned.per100g!!.provenance.rank).isEqualTo(0)
        assertThat(learned.per100g!!.provenance.confidence).isNull()
        assertThat(learned.perUnit!!.unitName).isEqualTo("cake")
        assertThat(learned.perUnit!!.nutrients.kcal).isWithin(0.001).of(35.0)

        assertThat(repository.byId(typed.id)!!.facts.per100g).isEqualTo(typedBefore)
        assertThat(repository.byId(typed.id)!!.facts.per100g!!.provenance.source).isEqualTo(Source.TYPED)
        assertThat(repository.byId(label.id)!!.facts.per100g).isEqualTo(labelBefore)
        assertThat(repository.byId(label.id)!!.facts.per100g!!.provenance.source).isEqualTo(Source.LABEL)
    }

    /** No path through this repository invents a weight from the other two facts. */
    @Test
    fun `a food knowing both kinds of calories acquires no weight`() = runTest {
        val food = repository.findOrCreate(
            "Protein bar",
            facts = FoodFacts(per100g = per100g(kcal = 422.0), perUnit = perUnit(kcal = 190.0)),
        ).food

        assertThat(repository.byId(food.id)!!.facts.gramsPerUnit).isNull()
    }

    // --- Renaming ----------------------------------------------------------------------------------

    @Test
    fun `renaming keeps the food and moves not one number`() = runTest {
        val food = repository.findOrCreate("yogurt", facts = FoodFacts(per100g = per100g(kcal = 60.0))).food

        assertThat(repository.rename(food.id, "Greek yoghurt")).isEqualTo(EditResult.Done)

        val after = repository.byId(food.id)!!
        assertThat(after.name).isEqualTo("Greek yoghurt")
        assertThat(after.facts.per100g!!.nutrients.kcal).isWithin(0.001).of(60.0)
    }

    /** After renaming, the OLD name is free and the new one is taken — it is a rename, not an alias. */
    @Test
    fun `a renamed food is found by its new name and not its old one`() = runTest {
        val food = repository.findOrCreate("yogurt", facts = FoodFacts(per100g = per100g())).food
        repository.rename(food.id, "Greek yoghurt")

        val byNew = repository.findOrCreate("greek yoghurt", facts = FoodFacts(per100g = per100g()))
        val byOld = repository.findOrCreate("yogurt", facts = FoodFacts(per100g = per100g()))

        assertThat(byNew.food.id).isEqualTo(food.id)
        assertThat(byOld.wasCreated).isTrue()
    }

    @Test
    fun `renaming onto another food's name is refused rather than silently merged`() = runTest {
        val yoghurt = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g())).food
        repository.findOrCreate("Hummus", facts = FoodFacts(per100g = per100g()))

        val result = repository.rename(yoghurt.id, "Hummus")

        assertThat(result).isInstanceOf(EditResult.Refused::class.java)
        assertThat((result as EditResult.Refused).why)
            .isInstanceOf(EditRefused.AlreadyAnotherFood::class.java)
    }

    // --- The form's Save, as one change ------------------------------------------------------------

    /**
     * The rename is allowed on its own — nothing plain is called Kefir — and the brand is not, because
     * a Kefir under Dairyco exists. Done as three separate transactions, the Save that is refused
     * would leave the food renamed; as one, it leaves it exactly as it was, brand column included.
     */
    @Test
    fun `a Save refused at the brand leaves the rename undone`() = runTest {
        val yoghurt = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g())).food
        repository.findOrCreate("Kefir", brand = "Dairyco", facts = FoodFacts(per100g = per100g()))

        val result = repository.saveForm(
            yoghurt.id,
            name = "Kefir",
            brand = "Dairyco",
            facts = FoodFacts(per100g = per100g(kcal = 60.0, source = Source.TYPED, confidence = null)),
        )

        assertThat(result).isInstanceOf(EditResult.Refused::class.java)
        assertThat((result as EditResult.Refused).why)
            .isInstanceOf(EditRefused.AlreadyAnotherFood::class.java)
        val after = repository.byId(yoghurt.id)!!
        assertThat(after.name).isEqualTo("Yoghurt")
        assertThat(after.brand).isEqualTo(yoghurt.brand)
        assertThat(after.facts).isEqualTo(yoghurt.facts)
    }

    @Test
    fun `a Save nothing refuses renames, brands and corrects together`() = runTest {
        val yoghurt = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g())).food
        val typed = FoodFacts(per100g = per100g(kcal = 60.0, source = Source.TYPED, confidence = null))

        val result = repository.saveForm(yoghurt.id, name = "Kefir", brand = "Dairyco", facts = typed)

        assertThat(result).isEqualTo(EditResult.Done)
        val after = repository.byId(yoghurt.id)!!
        assertThat(after.name).isEqualTo("Kefir")
        assertThat(after.brand).isEqualTo("Dairyco")
        assertThat(after.facts.per100g?.nutrients?.kcal).isEqualTo(60.0)
    }

    // --- Brands -------------------------------------------------------------------------------------

    /**
     * Putting a real brand on a food changes its identity, so the next plain "yoghurt" starts a new
     * entry. Correct behaviour — they genuinely are different foods — and it will look like a
     * duplicate coming back.
     */
    @Test
    fun `branding a food splits it away from the plain one`() = runTest {
        val food = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g())).food

        assertThat(repository.setBrand(food.id, "Dairyco")).isEqualTo(EditResult.Done)

        val plainAgain = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g()))
        assertThat(plainAgain.wasCreated).isTrue()
        assertThat(plainAgain.food.id).isNotEqualTo(food.id)
    }

    @Test
    fun `branding onto an identity another food already holds is refused`() = runTest {
        val plain = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g())).food
        repository.findOrCreate("Yoghurt", brand = "Dairyco", facts = FoodFacts(per100g = per100g()))

        val result = repository.setBrand(plain.id, "Dairyco")

        assertThat(result).isInstanceOf(EditResult.Refused::class.java)
    }

    @Test
    fun `clearing a brand returns the food to the brand that means none`() = runTest {
        val food = repository.findOrCreate("Yoghurt", brand = "Dairyco", facts = FoodFacts(per100g = per100g())).food

        repository.setBrand(food.id, null)

        assertThat(repository.byId(food.id)!!.brand).isEqualTo(FoodKeys.NO_BRAND)
    }

    /**
     * Two foods sharing a name under different brands, joined: the absorbed name keeps its brand, so
     * the winner holds the same name twice. Saving the winner's form sets its brand every time, and
     * that once rewrote every name to one brand, which the unique index refused — Save crashed.
     */
    @Test
    fun `a food that absorbed a same-named food of another brand can still have its brand set`() = runTest {
        val plain = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g())).food
        val branded = repository.findOrCreate("Yoghurt", brand = "Dairyco", facts = FoodFacts(per100g = per100g())).food
        repository.merge(winnerId = plain.id, loserId = branded.id)

        assertThat(repository.setBrand(plain.id, null)).isEqualTo(EditResult.Done)
        assertThat(repository.setBrand(plain.id, "Othermilk")).isEqualTo(EditResult.Done)
        assertThat(repository.byId(plain.id)!!.brand).isEqualTo("Othermilk")
    }

    /** The absorbed name keeps its brand through a brand change, so its next log finds the winner. */
    @Test
    fun `after a brand change the absorbed food's next log still finds the one that absorbed it`() = runTest {
        val plain = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g())).food
        val branded = repository.findOrCreate("Yoghurt", brand = "Dairyco", facts = FoodFacts(per100g = per100g())).food
        repository.merge(winnerId = plain.id, loserId = branded.id)
        repository.setBrand(plain.id, "Othermilk")

        val again = repository.findOrCreate("Yoghurt", brand = "Dairyco", facts = FoodFacts(per100g = per100g()))

        assertThat(again.wasCreated).isFalse()
        assertThat(again.food.id).isEqualTo(plain.id)
    }

    /** Branding the winner with the absorbed food's own brand makes the two names one, not a crash. */
    @Test
    fun `branding a food with the brand of a name it absorbed folds the two names into one`() = runTest {
        val plain = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g())).food
        val branded = repository.findOrCreate("Yoghurt", brand = "Dairyco", facts = FoodFacts(per100g = per100g())).food
        repository.merge(winnerId = plain.id, loserId = branded.id)

        assertThat(repository.setBrand(plain.id, "Dairyco")).isEqualTo(EditResult.Done)

        val food = repository.byId(plain.id)!!
        assertThat(food.brand).isEqualTo("Dairyco")
        assertThat(food.name).isEqualTo("Yoghurt")
        assertThat(food.alsoKnownAs).isEmpty()
    }

    // --- Correcting ----------------------------------------------------------------------------------

    /**
     * The ranking protects the owner from a guess, not from himself. A correction is his own hand
     * and is allowed to lower what the food claims to know.
     */
    @Test
    fun `a correction may replace a number that outranks it`() = runTest {
        val food = repository.findOrCreate(
            "Yoghurt",
            facts = FoodFacts(per100g = per100g(kcal = 422.0, source = Source.LABEL, confidence = null)),
        ).food

        repository.correct(
            food.id,
            FoodFacts(per100g = per100g(kcal = 90.0, source = Source.TYPED, confidence = null)),
        )

        val after = repository.byId(food.id)!!
        assertThat(after.facts.per100g!!.nutrients.kcal).isWithin(0.001).of(90.0)
        assertThat(after.facts.per100g!!.provenance.source).isEqualTo(Source.TYPED)
    }

    @Test
    fun `a correction may empty a group the food no longer should claim`() = runTest {
        val food = repository.findOrCreate(
            "Yoghurt",
            facts = FoodFacts(per100g = per100g(), perUnit = perUnit(unitName = "pot")),
        ).food

        repository.correct(food.id, FoodFacts(per100g = per100g()))

        assertThat(repository.byId(food.id)!!.facts.perUnit).isNull()
    }

    // --- Hiding and deleting ---------------------------------------------------------------------------

    @Test
    fun `a hidden food leaves the pickers and stays in the manager`() = runTest {
        val food = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g())).food

        repository.hide(food.id)

        assertThat(repository.observeOffered().first()).isEmpty()
        assertThat(repository.observeAll().first()).hasSize(1)
        assertThat(repository.byId(food.id)!!.hidden).isTrue()
    }

    @Test
    fun `hiding is undone`() = runTest {
        val food = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g())).food
        repository.hide(food.id)

        repository.unhide(food.id)

        assertThat(repository.observeOffered().first()).hasSize(1)
    }

    @Test
    fun `a food with nothing using it is deleted`() = runTest {
        val food = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g())).food

        assertThat(repository.delete(food.id)).isEqualTo(EditResult.Done)
        assertThat(repository.observeAll().first()).isEmpty()
    }

    // --- Merging ---------------------------------------------------------------------------------------

    /**
     * **The step that makes merging worth having.** The loser's name becomes an alias, so the next
     * log under that name finds the one food. Without it, merging is a treadmill: join them today,
     * log in Hebrew tomorrow, and there are two again.
     */
    @Test
    fun `merging keeps both names, so either language finds the one food`() = runTest {
        val english = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g())).food
        val hebrew = repository.findOrCreate("יוגורט", facts = FoodFacts(per100g = per100g())).food

        assertThat(repository.merge(winnerId = english.id, loserId = hebrew.id))
            .isEqualTo(EditResult.Done)

        val merged = repository.byId(english.id)!!
        assertThat(merged.name).isEqualTo("Yoghurt")
        assertThat(merged.alsoKnownAs).containsExactly("יוגורט")
        assertThat(repository.observeAll().first()).hasSize(1)

        val loggedInHebrew = repository.findOrCreate("יוגורט", facts = FoodFacts(per100g = per100g()))
        assertThat(loggedInHebrew.wasCreated).isFalse()
        assertThat(loggedInHebrew.food.id).isEqualTo(english.id)
    }

    /**
     * A merge is about identity. Silently preferring one side's numbers would be the ranking applied
     * where it was not asked for, so the winner keeps exactly what it knew.
     */
    @Test
    fun `merging does not move the winner's numbers`() = runTest {
        val winner = repository.findOrCreate(
            "Yoghurt",
            facts = FoodFacts(per100g = per100g(kcal = 60.0, source = Source.TYPED, confidence = null)),
        ).food
        val loser = repository.findOrCreate(
            "יוגורט",
            facts = FoodFacts(per100g = per100g(kcal = 422.0, source = Source.LABEL, confidence = null)),
        ).food

        repository.merge(winnerId = winner.id, loserId = loser.id)

        val merged = repository.byId(winner.id)!!
        assertThat(merged.facts.per100g!!.nutrients.kcal).isWithin(0.001).of(60.0)
        assertThat(merged.facts.per100g!!.provenance.source).isEqualTo(Source.TYPED)
    }

    @Test
    fun `merging a food into itself does nothing`() = runTest {
        val food = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g())).food

        assertThat(repository.merge(food.id, food.id)).isEqualTo(EditResult.Done)
        assertThat(repository.observeAll().first()).hasSize(1)
    }

    // --- The list, and its order -----------------------------------------------------------------------

    /**
     * A food made in the manager has never been logged and must not sort last or nowhere — it is the
     * thing he just made. The order is the later of when it was last logged and when it was edited.
     */
    @Test
    fun `a food just made sorts above one made earlier`() = runTest {
        moment = 1_000
        repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g()))
        moment = 2_000
        repository.findOrCreate("Tahini", facts = FoodFacts(per100g = per100g()))

        assertThat(repository.observeOffered().first().map { it.name })
            .containsExactly("Tahini", "Yoghurt").inOrder()
    }

    @Test
    fun `editing a food sends it back to the top`() = runTest {
        moment = 1_000
        val yoghurt = repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g())).food
        moment = 2_000
        repository.findOrCreate("Tahini", facts = FoodFacts(per100g = per100g()))

        moment = 3_000
        repository.rename(yoghurt.id, "Greek yoghurt")

        assertThat(repository.observeOffered().first().first().name).isEqualTo("Greek yoghurt")
    }

    // --- Counting what only knows a portion -----------------------------------------------------------

    /**
     * Derived rather than stored, so the list shrinks as the owner fixes them. A stored count would
     * have gone stale the first time he corrected one.
     */
    @Test
    fun `the foods that only know a portion are counted, and stop being counted when fixed`() =
        runTest {
            val stew = repository.findOrCreate(
                "Stew",
                facts = FoodFacts(perUnit = perUnit(unitName = FoodFacts.PORTION, kcal = 400.0)),
            ).food
            repository.findOrCreate("Yoghurt", facts = FoodFacts(per100g = per100g()))

            assertThat(repository.observeOnlyAPortionCount().first()).isEqualTo(1)

            repository.correct(stew.id, FoodFacts(per100g = per100g(kcal = 120.0)))

            assertThat(repository.observeOnlyAPortionCount().first()).isEqualTo(0)
        }

    // --- issue #13 (D45): what the food held immediately before this call -----------------------

    /** His own figure, typed. */
    private fun typed(kcal: Double) = per100g(kcal, source = Source.TYPED, confidence = null)

    /**
     * The snapshot is taken INSIDE the transaction, before the offer: the call hands back 15 while
     * the food it also hands back already holds 18.
     */
    @Test
    fun `an existing food reports the figure it held before this call`() = runTest {
        repository.findOrCreate("Cucumber", facts = FoodFacts(per100g = typed(15.0)))

        moment = 2_000
        val again = repository.findOrCreate("Cucumber", facts = FoodFacts(per100g = typed(18.0)))

        assertThat(again.wasCreated).isFalse()
        assertThat(again.food.facts.per100g!!.nutrients.kcal).isEqualTo(18.0)
        assertThat(again.before!!.per100g!!.nutrients.kcal).isEqualTo(15.0)
    }

    /**
     * Trap 1, proved rather than restated: the guarded statement really DOES fire for an identical
     * figure — `>=` lets it through and the "when this came to be believed" stamp moves from 1,000
     * to 2,000 — and the comparison still has nothing to report. A caller watching the DAO's row
     * count would announce a change every time he logged the same cucumber twice.
     */
    @Test
    fun `the identical figure offered again moves the stamp and still reports nothing`() = runTest {
        repository.findOrCreate("Cucumber", facts = FoodFacts(per100g = typed(15.0)))

        moment = 2_000
        val again = repository.findOrCreate("Cucumber", facts = FoodFacts(per100g = typed(15.0)))

        assertThat(again.food.facts.per100g!!.provenance.setAtMillis).isEqualTo(2_000)
        assertThat(again.before!!.per100g!!.provenance.setAtMillis).isEqualTo(1_000)
        assertThat(again.before!!.per100g!!.nutrients)
            .isEqualTo(again.food.facts.per100g!!.nutrients)
        assertThat(ReplacedFacts.between(again.before, again.food.facts)).isEmpty()
    }

    /**
     * The opposite half: a guess arriving at a figure he typed does not fire the statement at all,
     * so not even the stamp moves and what the food held is what it still holds.
     */
    @Test
    fun `a figure that loses the ranking leaves the food exactly as it was`() = runTest {
        repository.findOrCreate("Cucumber", facts = FoodFacts(per100g = typed(15.0)))

        moment = 2_000
        val again = repository.findOrCreate(
            "Cucumber",
            facts = FoodFacts(per100g = per100g(18.0, Source.AI_ESTIMATE, Confidence.MEDIUM)),
        )

        assertThat(again.food.facts.per100g!!.provenance.setAtMillis).isEqualTo(1_000)
        assertThat(again.before).isEqualTo(again.food.facts)
        assertThat(ReplacedFacts.between(again.before, again.food.facts)).isEmpty()
    }

    /** A food this call made held nothing a moment ago, which is what null means here. */
    @Test
    fun `a food just created reports that it held nothing`() = runTest {
        val made = repository.findOrCreate("Kohlrabi", facts = FoodFacts(per100g = typed(27.0)))

        assertThat(made.wasCreated).isTrue()
        assertThat(made.before).isNull()
    }
}
