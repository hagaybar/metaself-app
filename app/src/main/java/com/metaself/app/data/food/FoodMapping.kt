package com.metaself.app.data.food

import com.metaself.app.data.day.readSource
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodKeys
import com.metaself.app.domain.food.GramsPerUnit
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.PerUnit
import com.metaself.app.domain.food.Provenance

/**
 * Rows to foods, purely, so the awkward cases are testable on any machine.
 *
 * Forgiving in one direction only, exactly as a logged row already is. A stored fact that does not
 * read back as a whole fact — half its numbers missing, a source this version does not know, a
 * confidence on something that is not an estimate — is dropped rather than half-believed, and if
 * that leaves a food knowing nothing at all the food itself is dropped from the list rather than
 * shown as a name with nothing behind it.
 *
 * Dropping is the right severity here and it is worth saying why it differs from a logged row, where
 * dropping would lose food from the record. A food is derived: it can be made again by logging the
 * thing, and every day that ever held it still holds its own numbers regardless. What must not
 * happen is a food that claims to know something it does not.
 */
fun FoodWithNames.toDomain(): Food? {
    val preferred = names.firstOrNull { it.isPreferred }
        // Exactly one preferred name per food is a rule the schema cannot express, so it is kept
        // above this with a test. Falling back to the oldest name makes a violation a wrong label
        // rather than a crash.
        ?: names.minByOrNull { it.addedAtMillis }
        ?: return null

    val facts = food.toFacts() ?: return null

    return Food(
        id = food.id,
        name = preferred.displayName,
        alsoKnownAs = names.filter { it.id != preferred.id }
            .sortedBy { it.addedAtMillis }
            .map { it.displayName },
        brand = food.brand.ifBlank { FoodKeys.NO_BRAND },
        barcode = food.barcode,
        facts = facts,
        createdAtMillis = food.createdAtMillis,
        updatedAtMillis = food.updatedAtMillis,
        hidden = food.hiddenAtMillis != null,
    )
}

/** Null when the row knows neither of the two number groups, which is not a food that can be used. */
private fun FoodEntity.toFacts(): FoodFacts? {
    val per100g = readPer100g()
    val perUnit = readPerUnit()
    if (per100g == null && perUnit == null) return null
    return FoodFacts(
        per100g = per100g,
        perUnit = perUnit,
        // A weight without either kind of calories is useless, but a weight alongside them is one
        // of the more valuable things a food can know, so it is read on its own terms.
        gramsPerUnit = readGramsPerUnit(),
    )
}

private fun FoodEntity.readPer100g(): PerHundredGrams? {
    val nutrients = nutrients(kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g) ?: return null
    val provenance = provenance(per100gSource, per100gConfidence, per100gSetAtMillis) ?: return null
    return PerHundredGrams(nutrients, provenance)
}

private fun FoodEntity.readPerUnit(): PerUnit? {
    // The unit name is what makes the group meaningful: "80" of what?
    val unit = unitName?.takeIf { it.isNotBlank() } ?: return null
    val nutrients = nutrients(kcalPerUnit, proteinPerUnit, carbsPerUnit, fatPerUnit) ?: return null
    val provenance = provenance(perUnitSource, perUnitConfidence, perUnitSetAtMillis) ?: return null
    return PerUnit(unit, nutrients, provenance)
}

private fun FoodEntity.readGramsPerUnit(): GramsPerUnit? {
    val grams = gramsPerUnit?.takeIf { it > 0.0 } ?: return null
    val provenance =
        provenance(gramsPerUnitSource, gramsPerUnitConfidence, gramsPerUnitSetAtMillis) ?: return null
    return GramsPerUnit(grams, provenance)
}

/** All four or none: a fact with half its numbers missing is not a fact. */
private fun nutrients(kcal: Double?, protein: Double?, carbs: Double?, fat: Double?): Nutrients? {
    if (kcal == null || protein == null || carbs == null || fat == null) return null
    if (kcal < 0.0 || protein < 0.0 || carbs < 0.0 || fat < 0.0) return null
    return Nutrients(kcal, protein, carbs, fat)
}

/**
 * The same rule a logged row reads its two columns by, so a number cannot change its story on the
 * way from a food onto a row.
 *
 * With one difference that belongs here: on a food a confidence attaches to an estimate and to
 * nothing else, so a stored pair that says otherwise is read as unrecognised with no confidence
 * rather than carried across. A food is the thing itself, not a copy of something that was believed
 * once.
 */
private fun provenance(source: String?, confidence: String?, setAtMillis: Long?): Provenance? {
    if (source == null) return null
    val (readSource, readConfidence) = readSource(source, confidence)
    return Provenance(
        source = readSource,
        confidence = readConfidence.takeIf { readSource == Source.AI_ESTIMATE },
        setAtMillis = setAtMillis ?: 0,
    )
}
