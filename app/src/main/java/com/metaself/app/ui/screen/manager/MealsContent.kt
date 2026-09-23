package com.metaself.app.ui.screen.manager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.food.SavedMeal
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing
import java.util.Locale

/**
 * The meals the owner has built, on the manager's second tab.
 *
 * **This tab looks after a meal; it does not eat one.** Logging stays on "Add something", where he
 * is when he is eating — a manager that also logged would make looking after the list and logging
 * from it one screen again, which is the mistake this screen exists to correct. So a row here opens
 * the builder and nothing else does anything to the day.
 *
 * Renaming, changing what is in it and deleting it all happen in the builder that already exists,
 * which is why there is no second editor here.
 */
@Composable
fun MealsContent(
    state: MealsUiState,
    onBuildMeal: () -> Unit,
    onEditMeal: (Long) -> Unit,
    onDismissFailure: () -> Unit = {},
) {
    state.failed?.let { failed ->
        Text(
            text = stringResource(failed.sentence),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onDismissFailure) {
            Text(stringResource(R.string.action_refused_dismiss))
        }
    }

    if (state.nothingBuiltYet) {
        // An empty list is not a dead end to be escaped: it is the one place where building a meal
        // is the obvious thing to do, so the offer stands where the list would have been.
        Text(
            text = stringResource(R.string.meals_none),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onBuildMeal, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.repeat_build_meal))
        }
        return
    }

    // The same honesty the food editor already shows about a correction: a meal is a definition from
    // now on, and the days it is already on keep the numbers they were logged with.
    Text(
        text = stringResource(R.string.meals_note),
        style = MaterialTheme.typography.bodySmall,
        color = MetaSelfInk.two,
    )

    Button(onClick = onBuildMeal, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.repeat_build_meal))
    }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        state.meals.forEach { meal ->
            BuiltMeal(meal = meal, onOpen = { onEditMeal(meal.id) })
            HorizontalDivider()
        }
    }
}

/** One meal he built: its name, what is in it, and what it comes to. The row opens the builder. */
@Composable
private fun BuiltMeal(meal: SavedMeal, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
    ) {
        Text(text = meal.name, style = MaterialTheme.typography.bodyLarge)

        if (meal.isEmpty) {
            // No draft state: a half-built meal is simply a meal with fewer things in it, and it
            // waits. Saying so is kinder than a row that looks finished and is worth nothing.
            Text(
                text = stringResource(R.string.repeat_meal_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // What is in it as one quiet line rather than a line each: this is a list to find a
            // meal in, and the amounts are the builder's business.
            Text(
                text = meal.components.joinToString(", ") { it.food.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Something in it can no longer be costed, so the total below is short. Said, not hidden.
        if (meal.incomplete) {
            // Ink, not red (D48): red is a refusal or a field that is wrong, and this is neither —
            // a fact about the record, set one step above the captions around it.
            Text(
                text = stringResource(R.string.repeat_meal_incomplete),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = stringResource(R.string.repeat_kcal, String.format(Locale.US, "%,d", meal.kcal)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
