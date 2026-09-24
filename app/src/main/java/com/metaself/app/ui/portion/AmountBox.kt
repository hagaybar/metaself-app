package com.metaself.app.ui.portion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.ui.food.AmountTooMuch
import com.metaself.app.ui.food.saidAs
import com.metaself.app.ui.theme.Spacing

/**
 * How much of something, typed — the one amount box of D53 §6, shared by the proposal screen and
 * the repeat screen's *Just for today*.
 *
 * A number box with its unit beside it. For a counted unit, − and + either side ([onStep] with -1
 * or +1; whoever holds the amount decides what a step does, and that it never goes below one). The
 * unit is not edited here: a different unit needs a different worth (D53 §1). Under the box, D42's
 * sentence — said only past [most], never for a blank or a zero, which just leave saving off.
 *
 * @param text the box's text as typed; a string because "1." and "" are real states of a box.
 * @param unitWords the unit as it is drawn beside the number (the app's own "portion" pluralised,
 *   D37 — see [unitWord]).
 * @param counted true for a piece, which is stepped; false for grams, millilitres and the rest.
 * @param inGrams whether the ceiling sentence names grams; a ceiling of anything else names no unit.
 * @param of the thing whose amount this is, when the box is drawn once per row: the box and its
 *   − and + are then said with its name — "How much of Pizza", "One more of Pizza" — so one row's
 *   controls are not heard by the same names as the next row's (public issue #3). Never drawn.
 */
@Composable
fun AmountBox(
    text: String,
    unitWords: String,
    counted: Boolean,
    tooMuch: Boolean,
    most: Double,
    inGrams: Boolean,
    onText: (String) -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    of: String? = null,
) {
    val fewerSaid = of?.let { stringResource(R.string.said_one_less, it) }
    val moreSaid = of?.let { stringResource(R.string.said_one_more, it) }
    val boxSaid = of?.let { stringResource(R.string.said_how_much, it) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (counted) Step(stringResource(R.string.propose_count_fewer), fewerSaid) { onStep(-1) }
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                label = { Text(stringResource(R.string.propose_amount_label)) },
                isError = tooMuch,
                singleLine = true,
                // Decimal, not Number: an amount is read as a decimal and a comma is accepted for
                // the point, so a keyboard with neither would refuse half a bun.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).saidAs(boxSaid),
            )
            Text(text = unitWords, style = MaterialTheme.typography.bodyLarge)
            if (counted) Step(stringResource(R.string.propose_count_more), moreSaid) { onStep(+1) }
        }
        AmountTooMuch(
            tooMuch = tooMuch,
            most = most,
            countedAs = if (inGrams) CountedAs.GRAMS else CountedAs.UNITS,
        )
    }
}

@Composable
private fun Step(label: String, said: String?, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.saidAs(said),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}
