package com.metaself.app.domain.food

/**
 * The two groups of a food a review can touch (D54): what 100 g are worth, and what one is worth.
 *
 * What one weighs is not one of them: it is one figure with its own source, and a review may propose
 * it (D54 §12) — accepted, it travels as a [WeightAccepted]. Nothing in the app works it out (D4).
 */
enum class FactGroup {
    PER_100G,
    PER_UNIT,
}
