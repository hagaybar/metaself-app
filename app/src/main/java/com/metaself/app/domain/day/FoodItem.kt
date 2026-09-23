package com.metaself.app.domain.day

/**
 * One thing eaten: what it was, how much of it, what it contained, and where those numbers
 * came from.
 *
 * The relationship between [source] and [confidence] is enforced here rather than left to the
 * screens, because decision D4 is about what the RECORD may contain. A typed number is not a guess
 * with high confidence — it is the owner's number, and carries no confidence at all. An AI estimate
 * cannot exist without one.
 *
 * @property portion the assumption behind the numbers, when there is one — "1 medium, 90 g".
 *   Decision D5: a bare total is unarguable and therefore untrustworthy. Nothing produces a portion
 *   in step 3; step 7 fills it from the model's own stated assumption.
 * @property portionAmount, [portionUnit] the same assumption as arithmetic, so that a meal repeated
 *   from the record can have its amount changed. The words are what is displayed — they are the
 *   model's own sentence and are what make the numbers arguable — and these are what arithmetic is
 *   allowed to touch. Zero and empty mean no number was ever recorded, which downstream means no
 *   control at all rather than a wrong one. One exception to "the words are what is displayed":
 *   every screen that draws a row's words rebuilds the app's own "portion" from these two, so a
 *   stored "2 portion" reads "2 portions" (D37); every other unit is shown as stored.
 * @property name **what was typed on the day**, kept for ever and written to every export. It is
 *   the transcript, and it is no longer what the screen shows.
 * @property foodId which food this was, when it is attached to one. Null for a row whose food has
 *   since been deleted; for a row corrected before issue #22 was fixed and not yet repaired —
 *   correcting a figure used to drop the link; and for a scanned row whose label figure is no
 *   quantity of food (negative or infinite), logged when no food with that barcode, or that name
 *   and brand, exists yet — nothing honest is at hand to make one from (issue #28,
 *   `LoggedFoods.attachUntaught`); such a row could be logged only from 0.32.1 until D39 (issue
 *   #31, 0.32.2) refused those packets at the door, and older rows of this kind stay as they are.
 *   NOT for rows logged before foods existed: the foods migration attached every one of those, and
 *   its check script asserts it.
 * @property currentName what that food is called NOW, read through [foodId] when the row is read.
 *   Renaming a food re-labels every day it was ever eaten — numbers never change backwards, labels
 *   do — and this is the mechanism: the past points at the food, so not one stored number has to
 *   move for the label to follow. Never stored on the row; filled in on the way out.
 */
data class FoodItem(
    val id: Long = 0,
    val name: String,
    val portion: String? = null,
    val portionAmount: Double = 0.0,
    val portionUnit: String = "",
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val source: Source,
    val confidence: Confidence? = null,
    val foodId: Long? = null,
    val currentName: String? = null,
) {
    init {
        require(name.isNotBlank()) { "a food item needs a name" }
        require(portionAmount >= 0.0) { "a portion cannot be a negative amount" }
        require(kcal >= 0 && proteinG >= 0 && carbsG >= 0 && fatG >= 0) {
            "a food item cannot contain a negative amount of anything"
        }
        require(source != Source.TYPED || confidence == null) {
            "a typed number is the owner's number, not a guess: it carries no confidence"
        }
        require(source != Source.LABEL || confidence == null) {
            "a label is a declaration, not a guess: it carries no confidence"
        }
        require(source != Source.AI_ESTIMATE || confidence != null) {
            "an estimate must say how sure it was"
        }
    }

    /**
     * What to show: the food's current name when the row is attached to one, and otherwise the name
     * as it was typed.
     *
     * The fallback is not a degraded case — it is what every row logged before foods existed looks
     * like for ever, and what a row looks like again if its food is deleted. Both are correct: the
     * record still says what was eaten.
     */
    val label: String get() = currentName ?: name
}
