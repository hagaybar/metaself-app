package com.metaself.app.ui.food

/**
 * The sentences about a meal he built.
 *
 * Pure and in `ui/`, the division this project has used since the version marker: the domain
 * produces outcomes and never reaches for a phrase.
 *
 * [nameTaken] is said from three places — naming a new meal in the builder, renaming one there, and
 * naming a meal made out of a day. Written out three times it was three chances for the app to
 * refuse the same thing in three slightly different words.
 *
 * **Why these are not string resources.** A view model cannot reach for `stringResource`, and this
 * project's answer has never been to hand a resolved string down into one: it is a `*Wording` object
 * in `ui/`, which is what `FoodWording`, `EncouragementWording`, `GoalWording` and the rest all are.
 * The cost — words the owner reads that do not live in `strings.xml` — is accepted, and affordable
 * only while the app ships one language.
 */
object MealWording {

    /**
     * Why a name was refused: another meal already holds it.
     *
     * Quotes the name back, curly, because a meal called "Salad " and one called "Salad" are
     * indistinguishable in a sentence without them.
     */
    fun nameTaken(name: String): String = "You already have a meal called “${name.trim()}”."

    /**
     * Why nothing was made: a row he had ticked is not on the day any more.
     *
     * Says that nothing was made before it says what to do, because the first thing he needs to know
     * is that the day is untouched. Names no row, and cannot: the row that went is the one that can
     * no longer be read, so there is nothing left to quote back.
     */
    val chosenRowGone: String =
        "Something you chose is no longer on this day, so nothing was made. " +
            "Choose again and the meal will be made from what is there."
}
