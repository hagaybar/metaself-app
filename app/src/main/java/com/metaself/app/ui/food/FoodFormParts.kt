package com.metaself.app.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import com.metaself.app.R
import com.metaself.app.domain.food.FactGroup
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.PerHundredMillilitres
import com.metaself.app.ui.theme.Spacing

/*
 * The pieces a stored food is drawn from, one copy for each: its page's form (D55 §2), and the
 * summary line the list's row and the page's heading share (§1).
 */

/** The screen's one refusal or failure, with the button that takes it down. */
@Composable
internal fun SlotSentence(sentence: String, onDismiss: () -> Unit) {
    Text(
        text = sentence,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
    TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.foods_refusal_dismiss))
    }
}

/**
 * A group's heading, and where its stored figures came from on the same line: the heading at the
 * start, the origin at the end, as D55's drawing places them. Two texts, never one string, so a
 * right-to-left heading and a left-to-right origin are not reordered into each other (#9). A figure
 * he typed has no origin line, as before.
 */
@Composable
internal fun FactHeading(title: String, origin: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        origin?.let {
            Text(
                text = stringResource(R.string.foods_origin, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One field of a food's form.
 *
 * [numeric] picks the keyboard. Decimal rather than Number, because a food's facts are kept exactly
 * as typed, decimals included (D38) — a keyboard with no point on it would make the packet's 0.5 g
 * untypable on the one screen that promises to keep it.
 */
@Composable
internal fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    /** Where it sits in a row of two; on its own it takes the whole width. */
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    /** The review wrote this box's value and it is not saved yet: drawn and said so (D54 §11). */
    changed: Boolean = false,
    /**
     * What a screen reader calls the box, when [label] alone is not enough to tell it from another
     * box on the same screen (public issue #3) — see [figureSaid]. Never drawn.
     */
    said: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
        ),
        colors = if (changed) changedBoxColors() else OutlinedTextFieldDefaults.colors(),
        modifier = modifier.fillMaxWidth().changedByReview(changed).saidAs(said),
    )
}

/** Names the node for a screen reader as [said], or leaves it as it is when there is nothing to say. */
internal fun Modifier.saidAs(said: String?): Modifier =
    if (said == null) this else semantics { contentDescription = said }

/**
 * What a screen reader calls one figure box of a food's form: its label and the group it is in.
 *
 * The form draws the same four labels in both groups — "Calories" under per 100 g and again under
 * per one — so the label alone names two boxes, and nothing that reads the screen could aim at
 * either (public issue #3). "Calories per 100 g" and "Calories per bar" are one box each. With no
 * unit named yet the group is per "one", as its heading says; for a food counted in millilitres it
 * is per 100 ml, as its boxes are (D56).
 */
@Composable
internal fun figureSaid(label: String, group: FactGroup, unitName: String): String =
    when (group) {
        FactGroup.PER_100G -> stringResource(R.string.said_per_100g, label)
        FactGroup.PER_UNIT -> PerHundredMillilitres.per(unitName).takeIf { it.isNotEmpty() }
            ?.let { stringResource(R.string.said_per_unit, label, it) }
            ?: stringResource(R.string.said_per_one, label)
    }

/**
 * The heading of a food form's per-one group: *What one of it is worth*, or — for a unit box naming
 * the millilitre — *What 100 ml of it are worth*, since its boxes are then per 100 ml (D56).
 */
@Composable
internal fun perUnitHeading(unitName: String): String = stringResource(
    if (PerHundredMillilitres.applies(unitName)) R.string.foods_group_per_100ml else R.string.foods_group_per_unit,
)

/**
 * The one-tap way to count a food in millilitres (public issue #5): it writes "ml" into the unit
 * box and touches nothing else. Drawn only while [FoodForm.offersMillilitres] holds, so it never
 * changes what a figure already typed means. Beneath the unit box in both food forms.
 */
@Composable
internal fun CountInMillilitres(form: FoodForm, onSetForm: (FoodForm) -> Unit) {
    if (form.offersMillilitres) {
        TextButton(onClick = { onSetForm(form.copy(unitName = PerHundredMillilitres.UNIT)) }) {
            Text(stringResource(R.string.foods_count_in_ml))
        }
    }
}

/**
 * A line made of parts, each its own text with ` · ` between them — never one joined string, so a
 * Hebrew name and a Latin figure are not reordered into each other by the bidirectional algorithm
 * (D55 §1, #9). [then], when there is one, follows the parts after a ` · `, its own items separated
 * by commas: the meals part of *Where it's used*.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PartsLine(
    parts: List<String>,
    style: TextStyle,
    color: Color = Color.Unspecified,
    then: List<String>? = null,
) {
    FlowRow(modifier = Modifier.fillMaxWidth()) {
        parts.forEachIndexed { at, part ->
            if (at > 0) Text(text = SEPARATOR, style = style, color = color)
            Text(text = part, style = style, color = color)
        }
        then?.let { more ->
            Text(text = SEPARATOR, style = style, color = color)
            // The lead-in ("in 2 saved meals:"), then each name.
            Text(text = more.first(), style = style, color = color)
            more.drop(1).forEachIndexed { at, name ->
                Text(text = if (at == 0) " " else ", ", style = style, color = color)
                Text(text = name, style = style, color = color)
            }
        }
    }
}

private const val SEPARATOR = " · "
