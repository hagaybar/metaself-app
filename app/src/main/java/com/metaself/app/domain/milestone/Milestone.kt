package com.metaself.app.domain.milestone

/**
 * Something worth marking on the way to a goal.
 *
 * Identified by [name] because that is what gets stored, and a name survives the list being
 * reordered where an ordinal would silently become a different milestone. The same reasoning as
 * storing a food item's source as a word.
 *
 * Reaching the goal itself is deliberately NOT one of these. It already exists, it switches the goal
 * to holding, and folding it in would mean two mechanisms writing the same announcement.
 */
data class Milestone(val name: String) {

    companion object {
        const val FIRST_KG = "FIRST_KG"
        const val HALFWAY = "HALFWAY"
        const val LAST_KG = "LAST_KG"

        /** Every fifth kilogram: `EVERY_5_KG_5`, `_10`, `_15`. */
        fun everyFifth(kg: Int): Milestone = Milestone("EVERY_5_KG_$kg")

        /** A run of weeks each moving the right way: `STEADY_4W`, `STEADY_12W`. */
        fun steady(weeks: Int): Milestone = Milestone("STEADY_${weeks}W")

        val firstKg = Milestone(FIRST_KG)
        val halfway = Milestone(HALFWAY)
        val lastKg = Milestone(LAST_KG)
    }
}
