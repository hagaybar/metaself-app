package com.metaself.app.domain.ai

import com.metaself.app.domain.day.Confidence

fun aProposedItem(
    name: String = "Risotto",
    portion: String = "~280 g",
    portionAmount: Double = 280.0,
    portionUnit: String = "g",
    kcal: Int = 405,
    proteinG: Int = 9,
    carbsG: Int = 57,
    fatG: Int = 16,
    confidence: Confidence = Confidence.MEDIUM,
): ProposedItem = ProposedItem(
    name = name,
    portion = portion,
    portionAmount = portionAmount,
    portionUnit = portionUnit,
    kcal = kcal,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    confidence = confidence,
)

/** The two-item proposal every test here is built on. */
fun aProposal(
    items: List<ProposedItem> = listOf(
        aProposedItem(),
        aProposedItem(
            name = "Mozzarella",
            portion = "1 ball, ~100 g",
            portionAmount = 100.0,
            portionUnit = "g",
            kcal = 280,
            proteinG = 18,
            carbsG = 1,
            fatG = 20,
            confidence = Confidence.HIGH,
        ),
    ),
    note: String? = "Assumed a whole mozzarella ball.",
): MealProposal = MealProposal(items = items, note = note)
