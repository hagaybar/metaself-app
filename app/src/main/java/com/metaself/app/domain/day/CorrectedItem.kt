package com.metaself.app.domain.day

import com.metaself.app.domain.repeat.withAmount

/**
 * A row as the owner has just corrected it, with the source that correction leaves it honestly
 * claiming (D44, issue #35).
 *
 * The mirror of the rule D4 already holds in the other direction. Correcting a model's guess does
 * not launder it into a measurement — the editor carries `source` and `confidence` through
 * untouched, deliberately, so a corrected estimate is still an estimate. The case nobody had decided
 * is the reverse: a packet-label row whose figure he retypes went on being filed as the
 * manufacturer's declaration, which is a claim about a number the packet never stated.
 *
 * So a [Source.LABEL] row with a changed figure is stored as [Source.TYPED] — his number, ranked as
 * his. Change only the name, only the amount, or nothing at all, and the row keeps its label: the
 * packet is still the one that stated those figures.
 *
 * Every other source passes through untouched. Only a label row is demoted, because only a label row
 * is claiming something a correction stops it being able to claim.
 *
 * Both ends have to be a label before anything moves. The editor carries a row's source through, so
 * a saved row whose source differs from the one it replaces did not come from the editor — something
 * else built it and said what it is, and overwriting that would be this rule inventing provenance,
 * which is the exact thing D4 exists to prevent.
 */
fun FoodItem.correctionOf(before: FoodItem): FoodItem =
    if (before.source == Source.LABEL && source == Source.LABEL && !keepsFiguresOf(before)) {
        copy(source = Source.TYPED)
    } else {
        this
    }

/**
 * Whether the figures being saved are still the ones the packet was read for.
 *
 * The comparison is on the parsed whole numbers a row actually stores, never on what was typed: the
 * editor refuses a decimal outright rather than rounding it, so no re-formatting of the same value
 * can arrive here looking like a correction.
 *
 * **The amount is not a figure.** Moving 30 g to 60 g makes the editor rescale the row through
 * [withAmount] — the packet's reading of twice as much is still the packet's reading — so the row is
 * compared against what moving the amount alone would have produced. Both rescales are the same
 * rounding of the same source row, so an amount-only change compares equal at any amount, with no
 * tolerance — as long as the row handed in as `before` still holds the figures the form rescaled
 * from. It does on every route there is: this editor is the only writer of that row and it closes
 * on saving. Were the stored row to move underneath an open editor, an amount-only change would
 * read as a correction and flip to TYPED, which is the direction D4 prefers to err in anyway. And
 * because the editor always scales from the row as logged rather than from the boxes,
 * 30 g → 60 g → 30 g arrives back at exactly where it started.
 *
 * Anything the rescale cannot account for falls back to comparing the figures as they stand. Of the
 * two, only a row with no amount to scale can arrive today — the unit is not editable, so a changed
 * unit is defence against its becoming so, not a live case. That fallback reads as a correction
 * more readily than less, which claims less for the packet, and that is the direction D4 prefers to
 * err in.
 */
private fun FoodItem.keepsFiguresOf(before: FoodItem): Boolean {
    val rescaled = if (portionUnit == before.portionUnit) before.withAmount(portionAmount) else before
    return kcal == rescaled.kcal &&
        proteinG == rescaled.proteinG &&
        carbsG == rescaled.carbsG &&
        fatG == rescaled.fatG
}
