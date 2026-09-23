package com.metaself.app.ui.screen.entry

import com.metaself.app.domain.amount.BelievableAmount
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.portion.Portions
import com.metaself.app.domain.repeat.withAmount

/** The fields of the item editor, so an error can be attached to the box that caused it. */
enum class EntryField { NAME, AMOUNT, KCAL, PROTEIN, CARBS, FAT }

/**
 * What the item editor currently holds, and whether it makes sense.
 *
 * Held as the strings the owner typed, for the reason the setup form is: a field mid-edit is a
 * normal state, and "6" on the way to "60" is not an error worth shouting about.
 *
 * It carries [source] and [confidence] through untouched so that the same screen can edit an item
 * the model proposed without laundering it into a typed number. Step 3 only ever creates typed
 * items; step 7 hands this an estimate.
 *
 * It still does, and that is deliberate even though correcting a packet-label figure now changes the
 * source (D44, issue #35): whether a correction changes what a row claims is decided once, on the
 * save path, so every route into this editor behaves the same and the form stays a dumb carrier.
 *
 * The upper bounds catch a slipped finger — 6000 calories typed for 600 — and nothing more. They
 * are not a view about what anybody should eat. They are D42's, read from [BelievableAmount] like
 * every other box's.
 */
data class EntryFormState(
    val id: Long = 0,
    val name: String = "",
    val portion: String? = null,
    /**
     * How much of it, as a number the owner can change.
     *
     * The editor had no amount at all, so a chicken breast logged yesterday could not be corrected
     * to today's smaller one: the only way in was to retype every calorie by hand, and doing that
     * would have left the portion still reading "180 g" beside numbers that no longer matched it.
     *
     * Empty when the item never carried a portion with numbers behind it — a typed entry, or an
     * estimate the model could not put a figure to. Those keep the editor they always had.
     */
    val amount: String = "",
    val amountUnit: String = "",
    val kcal: String = "",
    val proteinG: String = "",
    val carbsG: String = "",
    val fatG: String = "",
    val source: Source = Source.TYPED,
    val confidence: Confidence? = null,
    /**
     * The item as it stands on the record, kept so that changing the amount always scales from
     * there rather than from whatever the boxes hold now.
     *
     * Halving twice is not halving once, and without this, setting 200 g and then 100 g would
     * arrive near the original rather than at it.
     */
    val asLogged: FoodItem? = null,
) {

    /** True when there is an amount to change, and therefore something to scale. */
    val hasAmount: Boolean get() = amountUnit.isNotBlank()

    fun errors(): Map<EntryField, String> = buildMap {
        if (name.isBlank()) put(EntryField.NAME, "What was it?")

        if (hasAmount && amountValue(amount) == null) {
            put(EntryField.AMOUNT, "How much of it? A number greater than nothing ($amountMost).")
        }

        val kcalMost = BelievableAmount.ENTRY_KCAL
        val gramsMost = BelievableAmount.ENTRY_MACRO_G
        requiredNumber(kcal, kcalMost, WANTED_KCAL)?.let { put(EntryField.KCAL, it) }
        optionalNumber(proteinG, gramsMost, WANTED_GRAMS)?.let { put(EntryField.PROTEIN, it) }
        optionalNumber(carbsG, gramsMost, WANTED_GRAMS)?.let { put(EntryField.CARBS, it) }
        optionalNumber(fatG, gramsMost, WANTED_GRAMS)?.let { put(EntryField.FAT, it) }
    }

    /**
     * The ceiling on this row's amount (D42): 5000 when its unit is a mass, 100 when it is counted.
     * Asked once, here, and both the check and the refusal's wording read it — two places each
     * deciding mass or count is two places that can come to disagree.
     */
    private val amountCeiling: Double get() = BelievableAmount.amountIn(amountUnit)

    /**
     * The amount's ceiling, as the refusal names it (D42, issue #32): with the row's unit when that
     * is a mass — "at most 5000 g", "at most 5000 ml" — written as the box's own label writes it
     * ("How much (gram)" reads "at most 5000 gram"), so the refusal never calls the unit something
     * the box above it does not. Bare for a count, whose unit takes its plural on the screen (D37),
     * so "at most 100 slice" is never said.
     */
    private val amountMost: String
        get() {
            val most = amountCeiling
            val words = BelievableAmount.words(most)
            return if (most == BelievableAmount.GRAMS) {
                "at most $words $amountUnit"
            } else {
                "at most $words"
            }
        }

    /**
     * What he typed as an amount, when it is one: a number above nothing and not past the ceiling
     * for the row's unit (D42). The parse is the one this box always had — a point, no comma.
     */
    private fun amountValue(typed: String): Double? =
        typed.trim().toDoubleOrNull()?.takeIf {
            it > 0.0 && BelievableAmount.isBelievable(it, amountCeiling)
        }

    /**
     * Change the amount, and carry the numbers with it.
     *
     * Scaling from [from] — the item as it stands on the record — rather than from whatever the
     * boxes hold now, so that setting 200 g and then 100 g arrives back at exactly the original
     * rather than at the compounded rounding of two multiplications. The same rule the repeat
     * adjuster and the model's proposals both follow.
     *
     * The calorie boxes stay editable afterwards. If he scales to one slice and then types a
     * different number of calories for it, that is his number and it stands: the amount says how
     * much, and he is entitled to disagree about what that much contained.
     *
     * An amount past its ceiling moves nothing (D42, issue #32): the box keeps what he typed and
     * says why, and the figures stay as logged. Scaling from it is what crashed — "Infinity" on a
     * row with a zero figure is 0 × ∞, which does not round — and "1e300" set every figure to the
     * largest whole number there is.
     */
    fun withAmount(typed: String): EntryFormState {
        val from = asLogged
        val wanted = amountValue(typed)
        // A row that never had an amount — the model named a unit and no number — has nothing to
        // scale from, so its figures stay as they were: they were stated for whatever amount that
        // was. The amount is recorded and the words follow it, so the day stops saying "amount not
        // stated" and the row can join a meal. The repair for issue #23's rows.
        if (hasAmount && from != null && wanted != null && from.portionAmount <= 0.0) {
            return copy(amount = typed, portion = Portions.words(wanted, amountUnit))
        }
        if (!hasAmount || from == null || wanted == null) {
            return copy(amount = typed)
        }

        val scaled = from.withAmount(wanted)
        return copy(
            amount = typed,
            portion = scaled.portion,
            kcal = scaled.kcal.toString(),
            proteinG = scaled.proteinG.toString(),
            carbsG = scaled.carbsG.toString(),
            fatG = scaled.fatG.toString(),
        )
    }

    fun toItem(): FoodItem? {
        if (errors().isNotEmpty()) return null
        val amountValue = amountValue(amount)?.takeIf { hasAmount }
        return FoodItem(
            id = id,
            name = name.trim(),
            portion = portion,
            portionAmount = amountValue ?: 0.0,
            portionUnit = if (amountValue != null) amountUnit else "",
            kcal = kcal.trim().toInt(),
            proteinG = proteinG.orZero(),
            carbsG = carbsG.orZero(),
            fatG = fatG.orZero(),
            source = source,
            confidence = confidence,
            // The food this row is, carried over from the row as it was logged. Left out, every
            // correction silently detached the row from its food — it then looked the same, could
            // never join a meal, and nothing could reattach it (issue #22). Whether a RENAMED row is
            // still that food is the view model's question, answered where it can look foods up.
            foodId = asLogged?.foodId,
        )
    }

    /**
     * [wanted] is what the box is refused with when it does not hold a whole number, and it has no
     * default on purpose: one shared message once answered calories and grams alike, in words the
     * form never said before the refusal (issue #18). Each caller names what it wants, in the
     * words the form's guidance uses above the boxes (D38).
     *
     * A decimal is refused, never rounded: turning a typed 0.7 into 1 would store an altered
     * number as his own (D4). "1.0" is refused too — the rule is the form of what he typed.
     */
    private fun requiredNumber(value: String, max: Int, wanted: String): String? {
        val parsed = value.trim().toIntOrNull() ?: return wanted
        // The parse stays whole numbers (D38); the verdict is D42's, the same one every box asks.
        // A whole number is never NaN, so NOT_A_NUMBER cannot arise here — the parse refused it.
        return when (BelievableAmount.judge(parsed.toDouble(), max.toDouble())) {
            BelievableAmount.Verdict.BELIEVABLE -> null
            BelievableAmount.Verdict.NEGATIVE -> "Not a negative number."
            BelievableAmount.Verdict.TOO_MUCH -> "That looks like a slipped finger — the most is $max."
            BelievableAmount.Verdict.NOT_A_NUMBER -> wanted
        }
    }

    private fun optionalNumber(value: String, max: Int, wanted: String): String? =
        if (value.isBlank()) null else requiredNumber(value, max, wanted)

    private fun String.orZero(): Int = trim().toIntOrNull() ?: 0

    companion object {
        private fun trimmed(value: Double): String =
            if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

        /*
         * Calories are not grams, so they are refused in their own words. These two must say what
         * the guidance above the boxes says (R.string.entry_whole_numbers, D38): the guidance
         * promises the refusal, so if one changes the other must too. They stay here rather than in
         * strings.xml because this class is plain Kotlin with no Android context, tested by JUnit 5
         * alone; when the app is translated (issue #9) they move to resources together with it.
         */
        private const val WANTED_KCAL = "A whole number of calories."
        private const val WANTED_GRAMS = "Whole grams — no decimal point or comma."

        /** Pre-fill from an item, for the screens that edit one rather than create it. */
        fun from(item: FoodItem): EntryFormState = EntryFormState(
            id = item.id,
            name = item.name,
            portion = item.portion,
            amount = item.portionAmount.takeIf { it > 0.0 }?.let(::trimmed).orEmpty(),
            amountUnit = item.portionUnit,
            kcal = item.kcal.toString(),
            proteinG = item.proteinG.toString(),
            carbsG = item.carbsG.toString(),
            fatG = item.fatG.toString(),
            source = item.source,
            confidence = item.confidence,
            asLogged = item,
        )
    }
}
