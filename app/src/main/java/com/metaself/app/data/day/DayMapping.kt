package com.metaself.app.data.day

import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.Source

/**
 * Rows to things and back, purely, so the interesting cases are testable on any machine.
 *
 * Reading is deliberately forgiving in one direction only. A row this version cannot make sense of
 * — an unknown source, or a combination the domain forbids — keeps its numbers and is marked
 * [Source.UNRECOGNISED]. Dropping it would silently lose food from the record; forcing it into
 * [Source.TYPED] would make exactly the claim decision D4 exists to prevent.
 *
 * A meal with no readable items at all is dropped, because a meal cannot be empty and showing an
 * empty one would be showing something that never happened.
 */
fun Meal.toEntities(): Pair<MealEntity, List<FoodItemEntity>> {
    val mealEntity = MealEntity(
        id = id,
        epochDay = epochDay,
        loggedAtMillis = loggedAtMillis,
        note = note,
        savedMealId = savedMealId,
        savedMealAdjusted = savedMealAdjusted,
    )
    return mealEntity to items.map { it.toEntity(mealId = id) }
}

/** One item as a row. Which meal it belongs to is not the item's to know, so it is a parameter. */
fun FoodItem.toEntity(mealId: Long): FoodItemEntity = FoodItemEntity(
    id = id,
    mealId = mealId,
    name = name,
    portion = portion,
    portionAmount = portionAmount,
    portionUnit = portionUnit,
    kcal = kcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    source = source.name,
    confidence = confidence?.name,
    foodId = foodId,
)

/**
 * @param currentNames what each food is called now, keyed by its id.
 *
 * Passed in rather than joined because the answer is the same for every row on every day and is a
 * few dozen entries wide: reading it once and applying it is cheaper than a join per row, and it
 * keeps this function pure.
 */
fun MealWithItems.toDomain(
    currentNames: Map<Long, String> = emptyMap(),
    savedMealNames: Map<Long, String> = emptyMap(),
): Meal? {
    val domainItems = items.map { it.toDomain(currentNames) }
    if (domainItems.isEmpty()) return null
    return Meal(
        id = meal.id,
        epochDay = meal.epochDay,
        loggedAtMillis = meal.loggedAtMillis,
        note = meal.note,
        items = domainItems,
        savedMealId = meal.savedMealId,
        // Read through the pointer, exactly as a food's name is, so renaming a meal retitles every
        // day it was eaten without a single stored number moving.
        savedMealName = meal.savedMealId?.let(savedMealNames::get),
        savedMealAdjusted = meal.savedMealAdjusted,
    )
}

/**
 * How a stored pair of strings reads back as a source and a confidence.
 *
 * Shared, because the migration that derives the owner's food list reads the same two columns out
 * of the same table and has to reach the same answer. Two copies of this rule would mean a row the
 * day screen calls an estimate and the conversion calls something else, and the food would then
 * carry a provenance the row it came from does not.
 *
 * Forgiving in one direction only. A row this version cannot make sense of — an unknown source, or
 * a combination the domain forbids — keeps its numbers and is marked [Source.UNRECOGNISED]. Dropping
 * it would silently lose food from the record; calling it [Source.TYPED] would make exactly the
 * claim about the owner that decision D4 exists to prevent.
 */
fun readSource(source: String?, confidence: String?): Pair<Source, Confidence?> {
    val readConfidence = confidence?.let { name -> Confidence.entries.firstOrNull { it.name == name } }
    val declaredSource = Source.entries.firstOrNull { it.name == source }

    val readableSource = when {
        declaredSource == null -> Source.UNRECOGNISED
        declaredSource == Source.TYPED && readConfidence != null -> Source.UNRECOGNISED
        declaredSource == Source.LABEL && readConfidence != null -> Source.UNRECOGNISED
        declaredSource == Source.AI_ESTIMATE && readConfidence == null -> Source.UNRECOGNISED
        else -> declaredSource
    }
    return readableSource to readConfidence
}

private fun FoodItemEntity.toDomain(currentNames: Map<Long, String>): FoodItem {
    val (readableSource, confidence) = readSource(source, confidence)

    return FoodItem(
        id = id,
        name = name.ifBlank { "(unnamed)" },
        portion = portion,
        portionAmount = maxOf(0.0, portionAmount),
        portionUnit = portionUnit,
        kcal = maxOf(0, kcal),
        proteinG = maxOf(0, proteinG),
        carbsG = maxOf(0, carbsG),
        fatG = maxOf(0, fatG),
        source = readableSource,
        confidence = confidence,
        foodId = foodId,
        // Null when the row is attached to nothing, and null when its food has gone. Both fall back
        // to the name typed on the day, which is what the record has always said.
        currentName = foodId?.let(currentNames::get),
    )
}
