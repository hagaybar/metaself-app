package com.metaself.app.domain.food

/**
 * The two groups of a food a review can touch (D54): what 100 g are worth, and what one is worth.
 *
 * What one weighs is deliberately not one of them. It is never guessed (D4): a review is shown it and
 * cannot answer it, so no accepted suggestion can ever be a weight.
 */
enum class FactGroup {
    PER_100G,
    PER_UNIT,
}
