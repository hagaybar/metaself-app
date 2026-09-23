package com.metaself.app.data.backup

import com.metaself.app.domain.backup.BackupFood
import com.metaself.app.domain.backup.BackupMealComponent
import com.metaself.app.domain.backup.BackupNutrients
import com.metaself.app.domain.backup.BackupPerUnit
import com.metaself.app.domain.backup.BackupSavedMeal
import com.metaself.app.domain.backup.BackupWeight2
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.DerivedFood
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodKeys
import com.metaself.app.domain.food.GramsPerUnit
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.PerUnit
import com.metaself.app.domain.food.Provenance
import com.metaself.app.domain.food.SavedMeal

/**
 * Foods and meals, to the file and back.
 *
 * Pure, so the awkward cases — a fact with half its numbers, a component naming a food the file does
 * not declare, an alias resolving to a food that was since merged — are testable on any machine.
 *
 * **A food is referred to by key everywhere in the file**: its normalised name and brand, joined by
 * a bar. `"food": "yoghurt|na"` under an item named "Yoghurt" explains itself to a person reading
 * the file, where a row of `"food_id": 47` does not, and a key is stable across devices where an id
 * is not.
 */
object BackupFoods {

    /** How a food is named in the file. */
    fun keyOf(name: String, brand: String?): String =
        "${FoodKeys.nameKey(name)}|${FoodKeys.brandKey(brand)}"

    fun keyOf(food: Food): String = keyOf(food.name, food.brand)

    /** The same, for a food the conversion has just worked out, whose keys are already computed. */
    fun keyOf(food: DerivedFood): String = "${food.nameKey}|${food.brandKey}"

    // --- Out ---------------------------------------------------------------------------------------

    /**
     * A number group holding a figure that is not finite is written as null — "not known", as the
     * file already says it — rather than stopping the export (issue #7): JSON cannot write one. Only
     * that group; the food's others are written as they are. Such a figure could be typed until
     * 0.32.6 (D42), and the repair on opening the app clears it from the phone too.
     */
    fun toBackup(food: Food): BackupFood = BackupFood(
        key = keyOf(food),
        name = food.name,
        brand = food.brand,
        alsoKnownAs = food.alsoKnownAs,
        per100g = food.facts.per100g?.takeIf { it.nutrients.isFinite() }?.let {
            BackupNutrients(
                kcal = it.nutrients.kcal,
                proteinG = it.nutrients.proteinG,
                carbsG = it.nutrients.carbsG,
                fatG = it.nutrients.fatG,
                source = it.provenance.source.name,
                confidence = it.provenance.confidence?.name,
            )
        },
        perUnit = food.facts.perUnit?.takeIf { it.nutrients.isFinite() }?.let {
            BackupPerUnit(
                unit = it.unitName,
                kcal = it.nutrients.kcal,
                proteinG = it.nutrients.proteinG,
                carbsG = it.nutrients.carbsG,
                fatG = it.nutrients.fatG,
                source = it.provenance.source.name,
                confidence = it.provenance.confidence?.name,
            )
        },
        gramsPerUnit = food.facts.gramsPerUnit?.takeIf { it.grams.isFinite() }?.let {
            BackupWeight2(
                grams = it.grams,
                source = it.provenance.source.name,
                confidence = it.provenance.confidence?.name,
            )
        },
        barcode = food.barcode,
    )

    private fun Nutrients.isFinite(): Boolean =
        kcal.isFinite() && proteinG.isFinite() && carbsG.isFinite() && fatG.isFinite()

    fun toBackup(meal: SavedMeal): BackupSavedMeal = BackupSavedMeal(
        name = meal.name,
        components = meal.components.sortedBy { it.position }.map { component ->
            BackupMealComponent(
                food = keyOf(component.food),
                amount = component.amount,
                countedAs = component.countedAs.name,
            )
        },
    )

    // --- Back in ------------------------------------------------------------------------------------

    /**
     * A food as the file describes it, or null when the file describes something unusable.
     *
     * **A food with both number groups null is rejected rather than restored into an unusable
     * food.** It could not be logged, could not be costed inside a meal, and would be a name with
     * nothing behind it — a state the store refuses, so accepting it here would only move the
     * failure somewhere less legible.
     */
    fun toDomain(food: BackupFood, setAtMillis: Long): Food? {
        val per100g = food.per100g?.let {
            PerHundredGrams(
                Nutrients(it.kcal, it.proteinG, it.carbsG, it.fatG),
                provenance(it.source, it.confidence, setAtMillis) ?: return null,
            )
        }
        val perUnit = food.perUnit?.let {
            if (it.unit.isBlank()) return null
            PerUnit(
                it.unit,
                Nutrients(it.kcal, it.proteinG, it.carbsG, it.fatG),
                provenance(it.source, it.confidence, setAtMillis) ?: return null,
            )
        }
        if (per100g == null && perUnit == null) return null

        val weight = food.gramsPerUnit?.takeIf { it.grams > 0.0 }?.let {
            GramsPerUnit(it.grams, provenance(it.source, it.confidence, setAtMillis) ?: return null)
        }

        return runCatching {
            Food(
                name = food.name,
                alsoKnownAs = food.alsoKnownAs,
                brand = food.brand.ifBlank { FoodKeys.NO_BRAND },
                barcode = food.barcode,
                facts = FoodFacts(per100g = per100g, perUnit = perUnit, gramsPerUnit = weight),
            )
        }.getOrNull()
    }

    fun countedAs(name: String): CountedAs =
        CountedAs.entries.firstOrNull { it.name == name } ?: CountedAs.GRAMS

    /**
     * Every key a food answers to, so an item pointing at a name that was since merged still finds
     * it. An alias is a key too.
     */
    fun everyKeyOf(food: Food): List<String> = food.everyName.map { keyOf(it, food.brand) }

    /**
     * A stored pair of strings as a provenance, or null when the pair is not readable.
     *
     * On a food a confidence belongs to an estimate and to nothing else, so a source this version
     * does not know, or a combination the domain forbids, reads as unrecognised with no confidence
     * rather than being forced into a claim.
     */
    private fun provenance(source: String, confidence: String?, setAtMillis: Long): Provenance? {
        val readSource = Source.entries.firstOrNull { it.name == source } ?: Source.UNRECOGNISED
        val readConfidence = confidence?.let { name ->
            Confidence.entries.firstOrNull { it.name == name }
        }
        // An estimate that did not say how sure it was is not an estimate this version can honour.
        val honest = when {
            readSource == Source.AI_ESTIMATE && readConfidence == null -> Source.UNRECOGNISED
            else -> readSource
        }
        return runCatching {
            Provenance(
                source = honest,
                confidence = readConfidence.takeIf { honest == Source.AI_ESTIMATE },
                setAtMillis = setAtMillis,
            )
        }.getOrNull()
    }
}
