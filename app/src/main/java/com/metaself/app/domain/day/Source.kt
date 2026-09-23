package com.metaself.app.domain.day

/**
 * Where a stored number came from — decision D4, and the field the whole record hangs on.
 *
 * The owner intends more sources later, ranked by credibility. This enum is what lets them arrive
 * without a migration of meaning: an entry made today says how it was made, so a future version
 * ranking sources against each other has something truthful to rank.
 *
 * **An estimate is never presented as a measurement.**
 */
enum class Source {

    /** The owner typed the numbers. Not a guess; his number. */
    TYPED,

    /** A model estimated them from a description or a photograph. Always carries a confidence. */
    AI_ESTIMATE,

    /**
     * Copied from a meal logged before.
     *
     * On a logged row, the row carries whatever that one carried — a repeated estimate keeps its
     * confidence. On a food's fact it says less: a figure the food took from a meal logged before,
     * whose own origin the food does not vouch for. That includes a figure worked back from any
     * label row — a scan, or a row logged from a food whose figure is the label's (D43, #29): the
     * row is rounded to whole grams (D38), so what is scaled from it is not what the packet printed.
     * Do not "correct" such a fact to LABEL — that would credit the packet with a number it never
     * stated, and rank it above what the owner typed (D4).
     */
    REPEATED,

    /**
     * Read off a package's own label, via a barcode.
     *
     * Its own kind of number, and not a variant of any of the others. Not typed, because the owner
     * did not type it. Not an estimate, because nothing guessed: a printed label is a manufacturer's
     * declaration. It therefore carries no confidence — but it CAN be out of date or simply wrong,
     * and the whole value of D4 here is that the record says where the figure came from, so that a
     * number which looks off can be traced rather than merely doubted.
     *
     * A label row whose figures the owner changes in *Correct this item* is stored as [TYPED] (D44,
     * issue #35): a packet is credited only with what it actually stated.
     */
    LABEL,

    /**
     * Written by a version of this app that this one does not understand.
     *
     * It exists so that a row from the future is shown honestly rather than dropped or relabelled.
     * Losing food silently from the record would be worse; calling it TYPED would put a claim on it
     * that decision D4 exists to prevent. Nothing ever writes this value — only reading produces it.
     */
    UNRECOGNISED,
}
