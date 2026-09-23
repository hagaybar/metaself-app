package com.metaself.app.domain.ai

import com.metaself.app.domain.amount.Per
import com.metaself.app.domain.amount.Rate
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.food.Nutrients

/**
 * The spec's invented beef burger (D53 §1): 250 kcal · P 18 · C 0 · F 20 per 100 g, 200 g of it,
 * estimated with moderate confidence — or any of that changed.
 */
fun aProposedItem(
    name: String = "Beef burger",
    detail: String = "",
    amount: Double = 200.0,
    unit: String = "g",
    rate: Rate = Rate(Nutrients(250.0, 18.0, 0.0, 20.0), Per.HUNDRED),
    confidence: Confidence = Confidence.MEDIUM,
): ProposedItem = ProposedItem(
    name = name,
    detail = detail,
    amount = amount,
    unit = unit,
    rate = rate,
    confidence = confidence,
)

/** The spec's invented hamburger bun, as the model gives it: 1 bun at 150 kcal per bun. */
fun aBun(amount: Double = 1.0, unit: String = "bun"): ProposedItem = aProposedItem(
    name = "Hamburger bun",
    detail = "sesame, toasted",
    amount = amount,
    unit = unit,
    rate = Rate(Nutrients(150.0, 5.0, 28.0, 2.0), Per.ONE),
)

/** The spec's invented pita, as the model gives it: 1 pita at 165 kcal per pita. */
fun aModelPita(): ProposedItem = aProposedItem(
    name = "Pita",
    amount = 1.0,
    unit = "pita",
    rate = Rate(Nutrients(165.0, 5.0, 33.0, 1.0), Per.ONE),
)

/** The two-item proposal every test here is built on: the burger and its bun. */
fun aProposal(
    items: List<ProposedItem> = listOf(aProposedItem(), aBun()),
    note: String? = "Assumed one bun of the usual size.",
): MealProposal = MealProposal(items = items, note = note)
