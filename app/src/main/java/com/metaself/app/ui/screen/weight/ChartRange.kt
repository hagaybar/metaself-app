package com.metaself.app.ui.screen.weight

import androidx.annotation.StringRes
import com.metaself.app.R

/**
 * How much of the weight history the chart is showing.
 *
 * Five presets rather than pinch-and-pan. A gesture would mean either a charting library or writing
 * one, and the chart exists precisely because neither was wanted; five taps answer every question
 * this history is actually asked.
 *
 * [days] counts back from today INCLUSIVE, so a month is today and the twenty-nine days before it.
 * [All] is null rather than a very large number, because "everything" must not quietly depend on how
 * long the owner has been weighing himself.
 *
 * Both words a range needs are carried here — the chip's own label, and the phrase the "nothing
 * weighed" sentence is built from — so that a sixth range cannot be added with one of its two
 * sentences left behind. [spanPhrase] is null for [All] alone: an empty whole history is an empty
 * history, which the weight screen already has its own sentence for.
 */
enum class ChartRange(
    val days: Long?,
    @StringRes val chipLabel: Int,
    @StringRes val spanPhrase: Int?,
) {
    Month(
        days = 30,
        chipLabel = R.string.weight_range_1m,
        spanPhrase = R.string.weight_range_span_1m,
    ),
    Quarter(
        days = 91,
        chipLabel = R.string.weight_range_3m,
        spanPhrase = R.string.weight_range_span_3m,
    ),
    Half(
        days = 182,
        chipLabel = R.string.weight_range_6m,
        spanPhrase = R.string.weight_range_span_6m,
    ),
    Year(
        days = 365,
        chipLabel = R.string.weight_range_1y,
        spanPhrase = R.string.weight_range_span_1y,
    ),
    All(days = null, chipLabel = R.string.weight_range_all, spanPhrase = null),
    ;

    companion object {

        /**
         * The range stored under [stored], or the whole history for anything unrecognised.
         *
         * Forgiving on purpose. The store holds the name rather than the position — reordering this
         * list later must not silently change which range the owner had chosen — and a name this
         * version does not know, from a hand-edited store or a later version, is not worth a crash.
         */
        fun named(stored: String?): ChartRange = entries.firstOrNull { it.name == stored } ?: All
    }
}
