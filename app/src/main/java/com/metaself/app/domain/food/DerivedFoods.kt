package com.metaself.app.domain.food

import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.portion.Portions

/**
 * One row of the record, as the conversion needs to see it.
 *
 * @property ref whatever the caller uses to find this row again afterwards — a database id for the
 *   migration, a position in the file for a restore. The conversion never looks inside it.
 */
data class LoggedFoodRow(
    val ref: Long,
    val name: String,
    val portionAmount: Double,
    val portionUnit: String,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val source: Source,
    val confidence: Confidence?,
    val loggedAtMillis: Long,
)

/**
 * One food the record turns out to contain, and every row that was it.
 *
 * @property rowRefs what to point at this food afterwards. No row is left out: a row with no food
 *   is a day that cannot say what its own name for the thing is now.
 */
data class DerivedFood(
    val nameKey: String,
    val brandKey: String,
    val displayName: String,
    val facts: FoodFacts,
    val rowRefs: List<Long>,
)

/**
 * @property onlyAPortionCount how many foods came out knowing nothing but "one of it was worth
 *   this". Decision 11 requires the owner be told the number, so he can go and look rather than
 *   discover them one at a time.
 */
data class Derivation(
    val foods: List<DerivedFood>,
    val onlyAPortionCount: Int,
)

/**
 * The list of foods the owner actually eats, worked out from every row he has ever logged.
 *
 * **Called from three places and written once.** The migration calls it with a cursor over the
 * table; restoring a backup calls it with every item the file's own foods do not cover; and every
 * row logged calls it with that one row, to find or teach its food (`LoggedFoods.attach`). Separate
 * implementations would drift, and the day they drifted the owner's restored record would not match
 * the one he backed up — and a rule about where a figure came from (D43) would hold on one door and
 * not the next.
 *
 * **It reads rows and writes foods. It cannot change what a past day was worth**, because it never
 * touches a row — which is the guarantee the whole change rests on, expressed as a shape rather than
 * as a promise.
 *
 * What it cannot get right, and nobody can: whether two foods that came out separate are actually
 * different. `יוגורט` and `Yoghurt`, `yogurt` and `yoghurt`, a typo and its correction. Only the
 * owner can read the list and say, which is why merging duplicates is a feature and not a step here.
 */
object DerivedFoods {

    /** What a row is called when its name is nothing a reader could use. */
    const val UNNAMED = "(unnamed)"

    fun from(rows: List<LoggedFoodRow>): Derivation {
        val groups = rows.groupBy { keyOf(it.name) }
        val foods = groups.map { (nameKey, groupRows) ->
            DerivedFood(
                nameKey = nameKey,
                // Nothing in the record carries a brand, so everything derived from it is the brand
                // that means none — which is exactly what lets a "yoghurt" logged tomorrow join it.
                brandKey = FoodKeys.NO_BRAND_KEY,
                displayName = nameFor(groupRows),
                facts = factsFor(groupRows),
                rowRefs = groupRows.map { it.ref },
            )
        }
        return Derivation(
            foods = foods,
            onlyAPortionCount = foods.count { it.facts.onlyAPortion },
        )
    }

    /** A name that normalises to nothing groups with the other such names rather than being lost. */
    private fun keyOf(name: String): String =
        runCatching { FoodKeys.nameKey(name) }.getOrElse { FoodKeys.nameKey(UNNAMED) }

    /**
     * The cleaned-up form the owner used most, not whichever variant he happened to type last.
     *
     * From the moment this runs, every past day showing this food shows this name — the variant
     * actually typed on the day stays on its own row and in every export, but it is no longer what
     * the screen reads. So the most-used spelling is the one worth keeping, and a tie goes to the
     * most recent because that is the one he most recently thought was right.
     */
    private fun nameFor(rows: List<LoggedFoodRow>): String {
        val cleaned = rows.mapNotNull { row ->
            // Only a name that can be keyed can be shown. `???` cleans up to `???` quite happily,
            // but it is not a name: it identifies nothing, it groups with every other such row, and
            // showing it as this food's name would put a label on the group that is true of only
            // one of them.
            runCatching { FoodKeys.nameKey(row.name); FoodKeys.displayName(row.name) }.getOrNull()
                ?.let { it to row.loggedAtMillis }
        }
        if (cleaned.isEmpty()) return UNNAMED
        return cleaned
            .groupBy({ it.first }, { it.second })
            .maxWithOrNull(
                compareBy<Map.Entry<String, List<Long>>> { it.value.size }
                    .thenBy { it.value.max() },
            )!!
            .key
    }

    /**
     * Each fact filled from the best row that actually has that shape, independently.
     *
     * This is where the model of three separate facts pays. The earlier design had to choose one
     * shape for the whole food and throw the other away, so a bread logged once in grams and once in
     * slices lost half of what the record knew about it. Here it keeps both.
     */
    private fun factsFor(rows: List<LoggedFoodRow>): FoodFacts {
        val per100g = per100gFrom(rows)
        val perUnit = perUnitFrom(rows)
        return when {
            per100g != null || perUnit != null -> FoodFacts(
                per100g = per100g,
                perUnit = perUnit,
                // Never, for any food, with no exceptions and no clever cases. Nothing in the record
                // says what a slice weighs, and working it out from the other two facts would
                // present an estimate as a measurement of a physical object.
                gramsPerUnit = null,
            )
            // Decision 11: every row for this food recorded words and no number. One of it was worth
            // what it was worth. Truthful about the row, and not knowledge about the food.
            else -> {
                val chosen = best(rows)!!
                FoodFacts(
                    perUnit = PerUnit(
                        unitName = FoodFacts.PORTION,
                        nutrients = nutrientsOf(chosen),
                        provenance = provenanceOf(chosen),
                    ),
                )
            }
        }
    }

    private fun per100gFrom(rows: List<LoggedFoodRow>): PerHundredGrams? {
        // GRAMS, not anything measured out (issue #26). `isMass` also holds ml, kg, l, oz: dividing
        // those by a hundred grams stored 150 ml of milk as a weight and half a kilo of rice at
        // 130,000 kcal per 100 g — a volume or a wrong factor presented as a measurement (D4).
        // Only a number of grams can become a per-100-g figure; everything else is counted below.
        val measured = rows.filter { usable(it) && Portions.isGrams(it.portionUnit) }
        val chosen = best(measured) ?: return null
        return PerHundredGrams(
            nutrients = nutrientsOf(chosen) * (HUNDRED_GRAMS / chosen.portionAmount),
            provenance = provenanceOf(chosen),
        )
    }

    /**
     * What one of it is worth, where "one" is the unit he counted it in most often.
     *
     * The best row is chosen WITHIN the winning unit rather than across all of them, because a
     * packet's figure for one cup is not evidence about one slice. Picking the best row first and
     * the unit from it would let a single well-sourced outlier decide what the food is counted in.
     */
    private fun perUnitFrom(rows: List<LoggedFoodRow>): PerUnit? {
        // Everything that is not grams, millilitres and kilograms included: the row says what that
        // many of the unit was worth, so the food learns what ONE of it is worth — per ml, per kg —
        // with nothing converted. The same shape as a food whose unit the owner typed as "ml".
        val counted = rows.filter { usable(it) && !Portions.isGrams(it.portionUnit) }
        if (counted.isEmpty()) return null

        val byUnit = counted.groupBy { unitKeyOf(it.portionUnit) }
        val winning = byUnit.entries.maxWithOrNull(
            compareBy<Map.Entry<String, List<LoggedFoodRow>>> { it.value.size }
                .thenBy { entry -> entry.value.maxOf { it.loggedAtMillis } },
        )!!.value

        val chosen = best(winning)!!
        return PerUnit(
            unitName = spellingOf(winning),
            nutrients = nutrientsOf(chosen) * (1.0 / chosen.portionAmount),
            provenance = provenanceOf(chosen),
        )
    }

    /** Two spellings of the same unit are one unit, by the same rule two spellings of a name are. */
    private fun unitKeyOf(unit: String): String =
        runCatching { FoodKeys.nameKey(unit) }.getOrElse { unit.trim().lowercase() }

    /** The spelling of the unit he used most, cleaned up, with a tie going to the most recent. */
    private fun spellingOf(rows: List<LoggedFoodRow>): String = rows
        .mapNotNull { row ->
            runCatching { FoodKeys.displayName(row.portionUnit) }.getOrNull()
                ?.let { it to row.loggedAtMillis }
        }
        .groupBy({ it.first }, { it.second })
        .maxWithOrNull(
            compareBy<Map.Entry<String, List<Long>>> { it.value.size }.thenBy { it.value.max() },
        )
        ?.key
        ?: FoodFacts.PORTION

    /**
     * The best row by where the figure it makes would come from, ties broken by the most recent.
     *
     * By source and not by date, which is the rule everywhere: a number the owner typed a year ago
     * is better than one a model guessed this morning.
     *
     * Ranked by the source of the FACT the row would make ([sourceOfFactFrom]), not the row's own
     * source (D43, #29). They differ only for a label row, and there the difference decides: ranked
     * by the row, a scanned row would beat one he typed and then hand the food a figure that can
     * only be filed as copied, throwing away his typed one. Ranked by the fact, the group gives the
     * food the figure the guarded statements would have kept had each row been offered in turn — so
     * a food comes out the same whichever route derived it.
     *
     * A full tie keeps the first row given. It happens in a restore, where every row's time is 0:
     * a REPEATED, UNRECOGNISED and label row all make a rank-0 fact, so which of them wins — and
     * whether the food screen says *copied from a past meal* or *from a version this one does not
     * understand* — follows the file's order. No rank can come out wrong; only that wording can.
     */
    private fun best(rows: List<LoggedFoodRow>): LoggedFoodRow? = rows.maxWithOrNull(
        compareBy<LoggedFoodRow> { Provenance.rankOf(sourceOfFactFrom(it)) }
            .thenBy { it.loggedAtMillis },
    )

    private fun usable(row: LoggedFoodRow): Boolean =
        Portions.canScale(row.portionAmount, row.portionUnit)

    private fun nutrientsOf(row: LoggedFoodRow) = Nutrients(
        kcal = row.kcal.coerceAtLeast(0).toDouble(),
        proteinG = row.proteinG.coerceAtLeast(0).toDouble(),
        carbsG = row.carbsG.coerceAtLeast(0).toDouble(),
        fatG = row.fatG.coerceAtLeast(0).toDouble(),
    )

    /**
     * The chosen row's source verbatim except for a label ([sourceOfFactFrom]), and its confidence
     * where it had one.
     *
     * Verbatim matters: a group whose best row was copied from a past meal gets that source and its
     * rank of nothing, so anything the owner later types or scans replaces it. Mapping it onto an
     * estimate to make the ranking tidy would be inventing provenance.
     *
     * The time is the row's own, not the conversion's clock: the useful thing to show beside a
     * number is when it came to be believed, and the day it was logged is that. It is never what
     * chooses between two sources — that is by rank, always.
     */
    private fun provenanceOf(row: LoggedFoodRow) = Provenance(
        source = sourceOfFactFrom(row),
        confidence = row.confidence.takeIf { row.source == Source.AI_ESTIMATE },
        setAtMillis = row.loggedAtMillis,
    )

    /**
     * Where a figure worked back from this row came from, as the food should record it.
     *
     * The row's own source, except that a label row's figure is filed as copied from a past meal
     * (D43, #29). A scanned row is rounded to whole grams (D38), so anything scaled from it — per
     * 100 g, per one, or the row kept as a portion — is not what the packet printed: 0.5 g of fat
     * logged as 30 g is a row of 0 g, and filing the 0.0 worked back from it as LABEL would credit
     * the packet with a number it never stated, and rank it above what he typed (D4). What the
     * figure honestly is, is a number taken from a meal he logged, which is what REPEATED means on
     * a food; it ranks below a typed or a label figure, so it can fill a blank and never overwrite
     * either. The scan itself still gives its food the packet's exact per-100 g as LABEL:
     * `LoggedFoods.attach` puts the label's own figures in place of this one (#28, D38).
     *
     * Not a new source: every row later logged from the food copies the fact's source onto itself,
     * so a new value would spread to the day's rows and the backup for nothing REPEATED lacks.
     */
    private fun sourceOfFactFrom(row: LoggedFoodRow): Source =
        if (row.source == Source.LABEL) Source.REPEATED else row.source

    private const val HUNDRED_GRAMS = 100.0
}
