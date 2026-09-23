package com.metaself.app.ui.food

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.metaself.app.R
import com.metaself.app.domain.amount.BelievableAmount
import com.metaself.app.domain.food.CountedAs

/**
 * The line under an amount box saying what he typed is past its ceiling (D42, issue #32) — for the
 * boxes that had no refusal of their own: the scan's grams, Add something's amount and the meal
 * builder's two. One composable for all four, so their wording and look cannot drift apart.
 *
 * Drawn only when [tooMuch]: a blank, a zero or a half-typed number keep the quiet disabled button
 * every one of these boxes has always used. The counted wording names no unit, because the app's unit
 * words take their plural on the screen (D37) and "at most 100 slice" would be wrong.
 *
 * [most] is the ceiling the box's own state judged against, passed in rather than worked out again
 * here: the number the line names is then the number that refused him, by construction. [countedAs]
 * only chooses the wording.
 */
@Composable
fun AmountTooMuch(tooMuch: Boolean, most: Double, countedAs: CountedAs) {
    if (!tooMuch) return
    val words = BelievableAmount.words(most)
    Text(
        text = when (countedAs) {
            CountedAs.GRAMS -> stringResource(R.string.amount_at_most_grams, words)
            CountedAs.UNITS -> stringResource(R.string.amount_at_most_count, words)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
