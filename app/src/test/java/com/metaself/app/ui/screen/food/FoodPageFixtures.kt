package com.metaself.app.ui.screen.food

import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.GramsPerUnit
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.PerUnit
import com.metaself.app.domain.food.Provenance

/*
 * D55's four worked examples, for the food page's tests. Every figure, name and count here is
 * invented, as the spec's are; none is anybody's record.
 */

private fun label() = Provenance(Source.LABEL, null, setAtMillis = 0)

private fun typed() = Provenance(Source.TYPED, null, setAtMillis = 0)

/**
 * Knows both ways of counting, off a label, and what a cup weighs, typed. Invented figures, chosen
 * so the cup agrees with 100 g exactly: 150 g of 100 · P 8 · C 4 · F 5 is 150 · P 12 · C 6 · F 7.5.
 */
fun greekYoghurt() = Food(
    name = "Greek yoghurt",
    facts = FoodFacts(
        per100g = PerHundredGrams(Nutrients(100.0, 8.0, 4.0, 5.0), label()),
        perUnit = PerUnit("cup", Nutrients(150.0, 12.0, 6.0, 7.5), label()),
        gramsPerUnit = GramsPerUnit(150.0, typed()),
    ),
)

/** Branded; per 100 g off a label and what one weighs, typed, with no unit named. */
fun examplebrandOatBiscuit() = Food(
    name = "Oat biscuit",
    brand = "Examplebrand",
    facts = FoodFacts(
        per100g = PerHundredGrams(Nutrients(450.0, 8.0, 60.0, 20.0), label()),
        gramsPerUnit = GramsPerUnit(12.0, typed()),
    ),
)

/** Per 100 g, a close estimate. */
fun lentilSoup() = Food(
    name = "Lentil soup",
    facts = FoodFacts(
        per100g = PerHundredGrams(
            Nutrients(90.0, 5.0, 12.0, 2.0),
            Provenance(Source.AI_ESTIMATE, Confidence.MEDIUM, setAtMillis = 0),
        ),
    ),
)

/** Per bun, typed, and hidden. */
fun hamburgerBun() = Food(
    name = "Hamburger bun",
    hidden = true,
    facts = FoodFacts(perUnit = PerUnit("bun", Nutrients(150.0, 5.0, 28.0, 2.0), typed())),
)
