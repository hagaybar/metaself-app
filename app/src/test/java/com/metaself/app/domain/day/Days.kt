package com.metaself.app.domain.day

/**
 * A typed food item with plausible defaults, for tests that vary one field.
 *
 * Typed rather than estimated because that is what step 3 can actually produce, and because a typed
 * item is the one that must carry no confidence.
 */
fun anItem(
    id: Long = 0,
    name: String = "Chicken shawarma",
    portion: String? = null,
    portionAmount: Double = 0.0,
    portionUnit: String = "",
    kcal: Int = 600,
    proteinG: Int = 40,
    carbsG: Int = 50,
    fatG: Int = 25,
    source: Source = Source.TYPED,
    confidence: Confidence? = null,
): FoodItem = FoodItem(
    id = id,
    name = name,
    portion = portion,
    portionAmount = portionAmount,
    portionUnit = portionUnit,
    kcal = kcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    source = source,
    confidence = confidence,
)

/** A meal holding [items], on [epochDay]. */
fun aMeal(
    id: Long = 0,
    epochDay: Long = TEST_EPOCH_DAY,
    loggedAtMillis: Long = 1_772_000_000_000,
    note: String? = null,
    items: List<FoodItem> = listOf(anItem()),
    savedMealId: Long? = null,
    savedMealName: String? = null,
    savedMealAdjusted: Boolean = false,
): Meal = Meal(
    id = id,
    epochDay = epochDay,
    loggedAtMillis = loggedAtMillis,
    note = note,
    items = items,
    savedMealId = savedMealId,
    savedMealName = savedMealName,
    savedMealAdjusted = savedMealAdjusted,
)

/** 2026-09-03, as an epoch day. Fixed so no test changes its answer overnight. */
const val TEST_EPOCH_DAY = 20_699L
