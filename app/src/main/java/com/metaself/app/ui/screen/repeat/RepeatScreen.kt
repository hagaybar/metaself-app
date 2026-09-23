package com.metaself.app.ui.screen.repeat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.metaself.app.R
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.LoggedFrom
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.domain.food.SavedMeal
import com.metaself.app.domain.portion.Portions
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.food.AmountTooMuch
import com.metaself.app.ui.food.FoodWording
import com.metaself.app.ui.food.HowItIsCounted
import com.metaself.app.ui.portion.portionWords
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing
import java.util.Locale

/**
 * The way into logging something: the owner's own foods first, and describing when none of them is
 * it.
 *
 * His own list comes first because nothing yet resolves a described meal against food already on
 * the record, so every description manufactures fresh items. Searching what he already has stops
 * the duplicate being made rather than detecting it afterwards — and when the search genuinely
 * misses, a new food IS the right outcome, which is why every empty state here falls through to
 * describing instead of stopping. The words typed into the search go with him (see [onDescribe]).
 *
 * A whole row logs the meal exactly as it was, which is the common case and stays one tap. What it
 * holds is shown item by item, with each portion: repeating a two-portion row used to say only the
 * meal's name and its total, with no way to see that the quantity had carried. A number you cannot see is
 * a number you cannot trust. A part the app cannot cost — shown with a "?" — is skipped when the
 * meal is logged, not guessed at.
 *
 * A food is not one tap: tapping it asks how much first, with the amount left empty, because the
 * amount is the one thing the app will not fill in for him (D4). The note above both tabs says which
 * is which (D37) — it once said "One tap logs it" of both.
 *
 * Adjust opens the meal in place. Not a screen of its own, so that what he is changing stays next
 * to the other meals he might have picked instead.
 */
@Composable
fun RepeatScreen(
    state: RepeatUiState,
    /** Nothing here matched: describe these words instead, which may be none. */
    onDescribe: (String) -> Unit,
    /** Something on this list is wrong, or is a duplicate: the place to put it right. */
    onManageFoods: () -> Unit,
    onGivePortion: (foodId: Long) -> Unit,
    onRepeat: (SavedMeal) -> Unit,
    /** Start a new meal, or open one to change for good. */
    onBuildMeal: () -> Unit,
    onEditMeal: (Long) -> Unit,
    onPickFood: (Int) -> Unit,
    onCountAs: (CountedAs) -> Unit,
    onSetAmount: (String) -> Unit,
    onCancelChoosing: () -> Unit,
    onLogChosen: () -> Unit,
    onShowTab: (RepeatTab) -> Unit,
    onSearch: (String) -> Unit,
    onBeginAdjusting: (Int) -> Unit,
    onSetComponentAmount: (Long, Double) -> Unit,
    onRemoveComponent: (Long) -> Unit,
    onCancelAdjusting: () -> Unit,
    onLogAdjusted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(
        title = stringResource(R.string.repeat_title),
        modifier = modifier,
        onBack = onBack,
        // Each tab keeps its own place. One shared scroller meant leaving the foods halfway down
        // and landing on the meals already past their top, losing his place in both at once.
        scrollKey = state.tab,
        // Chrome, not content: the strip and the box that serves it stay put while the list moves.
        // The foods manager was given this and it was never carried back here — the screen he
        // crosses three times a day was the one still scrolling its own search out of reach.
        belowBar = {
            // Nothing logged yet means no tabs and no search: a search box over nothing is noise,
            // and the branch below draws a front door instead.
            if (!state.nothingEverLogged) {
                // A food and a meal are different things to want, so they are two lists rather
                // than one interleaved one. The search box serves whichever is in front.
                TabRow(selectedTabIndex = if (state.tab == RepeatTab.FOODS) 0 else 1) {
                    Tab(
                        selected = state.tab == RepeatTab.FOODS,
                        onClick = { onShowTab(RepeatTab.FOODS) },
                        text = { Text(stringResource(R.string.repeat_tab_foods)) },
                    )
                    Tab(
                        selected = state.tab == RepeatTab.MEALS,
                        onClick = { onShowTab(RepeatTab.MEALS) },
                        text = { Text(stringResource(R.string.repeat_tab_meals)) },
                    )
                }
                // Indented by hand: belowBar is drawn edge to edge, and a text field against both
                // edges of the screen is the one thing that slot's own margins would have given it.
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onSearch,
                    label = { Text(stringResource(R.string.repeat_search)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Screen, vertical = Spacing.Tight),
                )
            }
        },
    ) {
        if (state.nothingEverLogged) {
            Text(
                text = stringResource(R.string.repeat_none),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            // A search box over nothing is noise, so this branch still returns before drawing one —
            // but it is the first screen a new owner sees, and a front door with nothing behind it
            // is worse than noise. Nothing was typed, so there is nothing to carry.
            Button(onClick = { onDescribe("") }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.propose_describe_meal))
            }
            return@MetaSelfScreen
        }

        Text(
            text = stringResource(R.string.repeat_note),
            style = MaterialTheme.typography.bodySmall,
            color = MetaSelfInk.two,
        )

        if (state.searchedAndFoundNothing) {
            // "Nothing you have logged" is only true when neither list matches. With the match one
            // tab away, the sentence names the list in front, so it does not contradict the offer.
            Text(
                text = stringResource(
                    when (state.matchesOnOtherTab) {
                        null -> R.string.repeat_no_match
                        RepeatTab.FOODS -> R.string.repeat_no_match_in_meals
                        else -> R.string.repeat_no_match_in_foods
                    },
                    state.query.trim(),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            // The sentence above is about the list in front and stays per-tab. The offer is not:
            // if the other tab holds the match it is one tap away, and offering to describe it
            // here would manufacture exactly the duplicate this screen exists to prevent. So the
            // offer is the other tab itself, named — both lists are already filtered by these words.
            //
            // A Button rather than a TextButton, because it is the only thing to do on a screen
            // that has just said it has nothing.
            state.matchesOnOtherTab?.let { other ->
                Button(onClick = { onShowTab(other) }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (other == RepeatTab.FOODS) {
                                R.string.repeat_found_in_foods
                            } else {
                                R.string.repeat_found_in_meals
                            },
                        ),
                    )
                }
            }
            if (state.nothingMatchedEither) {
                Button(
                    onClick = { onDescribe(state.query.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.repeat_describe_instead, state.query.trim()))
                }
            }
            return@MetaSelfScreen
        }

        // One list can be empty while the other is not — one food logged, no meals, the owner on
        // "My meals". Neither branch above catches it, and without this the screen draws tabs and a
        // search box over an empty space. Nothing was typed, so nothing is carried and no food has
        // been named: this is the fresh-install offer narrowed to one list, not the miss above. The
        // tabs stay drawn, so the list that does hold something is one tap up.
        //
        // **What is offered here depends on which list is empty**, and getting that wrong made the
        // meal builder unreachable: the meals list is empty until he builds one, so this branch is
        // what he sees EVERY time until he does — and it used to return before the button that
        // starts one, offering to describe a meal to the model instead. An empty meals tab is not a
        // dead end to be escaped, it is the one place building a meal is the obvious thing to do.
        if (state.thisTabIsEmpty) {
            Text(
                text = stringResource(
                    if (state.tab == RepeatTab.MEALS) {
                        R.string.repeat_no_meals_yet
                    } else {
                        R.string.repeat_tab_none
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.tab == RepeatTab.MEALS) {
                Button(onClick = onBuildMeal, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.repeat_build_meal))
                }
            } else {
                Button(onClick = { onDescribe("") }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.propose_describe_meal))
                }
            }
            return@MetaSelfScreen
        }

        if (state.tab == RepeatTab.FOODS) {
            // Where the list is put right, offered where he is looking at the list — which is where
            // he notices that two entries are the same thing, or that a number is wrong.
            TextButton(onClick = onManageFoods) {
                Text(stringResource(R.string.repeat_manage_foods))
            }

            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                state.foods.forEachIndexed { index, food ->
                    val choosing = state.choosing?.takeIf { it.index == index }

                    if (choosing == null) {
                        OwnFood(food = food, onPick = { onPickFood(index) })
                    } else {
                        HowMuch(
                            choosing = choosing,
                            onCountAs = onCountAs,
                            onSetAmount = onSetAmount,
                            onGivePortion = { onGivePortion(choosing.food.id) },
                            onCancel = onCancelChoosing,
                            onLog = onLogChosen,
                        )
                    }
                    HorizontalDivider()
                }
            }
            return@MetaSelfScreen
        }

        // The meals he built. Empty until he builds one, which is why the builder ships in the
        // same release as the change that emptied this list.
        TextButton(onClick = onBuildMeal) {
            Text(stringResource(R.string.repeat_build_meal))
        }

        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            state.meals.forEachIndexed { index, meal ->
                val adjusting = state.adjusting?.takeIf { it.asDefined.id == meal.id }

                if (adjusting == null) {
                    BuiltMeal(
                        meal = meal,
                        onLog = { onRepeat(meal) },
                        onAdjust = { onBeginAdjusting(index) },
                        onEdit = { onEditMeal(meal.id) },
                    )
                } else {
                    Adjuster(
                        adjusting = adjusting,
                        onSetAmount = onSetComponentAmount,
                        onRemove = onRemoveComponent,
                        onCancel = onCancelAdjusting,
                        onLog = onLogAdjusted,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

/**
 * One meal the owner built. The row logs it; the buttons open it for today only, or for good.
 *
 * "Just for today" is a small word beside a large target on purpose: logging the meal as it was built
 * is the ordinary way in, and it must not become the slower of the two.
 */
@Composable
private fun BuiltMeal(
    meal: SavedMeal,
    onLog: () -> Unit,
    onAdjust: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLog)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
    ) {
        Text(text = meal.name, style = MaterialTheme.typography.bodyLarge)

        if (meal.isEmpty) {
            // No draft state: a half-built meal is simply a meal with fewer things in it, and it
            // waits. Saying so is kinder than offering an empty row that logs nothing.
            Text(
                text = stringResource(R.string.repeat_meal_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        meal.components.forEach { component ->
            Text(
                text = describeComponent(component, portionWords(component)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // A component whose food no longer knows what it is counted in cannot be costed, and the
        // total would quietly be smaller than the meal. Said rather than hidden.
        if (meal.incomplete) {
            Text(
                text = stringResource(R.string.repeat_meal_incomplete),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.repeat_kcal,
                    String.format(Locale.US, "%,d", meal.kcal),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row {
                TextButton(onClick = onAdjust) {
                    Text(stringResource(R.string.repeat_adjust))
                }
                TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.repeat_meal_edit))
                }
            }
        }
    }
}

/**
 * A meal he built, for one day only.
 *
 * **Nothing here changes the meal.** He can drop the oil and double the cucumber, and tomorrow's
 * salad still has oil in it and one cucumber's worth. The app will never offer to update the meal
 * because he has dropped the oil four times running: if the meal is to change, he changes it, on
 * the builder.
 */
@Composable
private fun Adjuster(
    adjusting: Adjusting,
    onSetAmount: (Long, Double) -> Unit,
    onRemove: (Long) -> Unit,
    onCancel: () -> Unit,
    onLog: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.Related),
    ) {
        Text(text = adjusting.asDefined.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(R.string.repeat_adjust_today_only),
            style = MaterialTheme.typography.bodySmall,
            color = MetaSelfInk.two,
        )

        adjusting.rows.forEach { component ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                Text(
                    text = describeComponent(component, portionWords(component)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Tight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Small(stringResource(R.string.propose_less)) {
                        onSetAmount(component.id, component.amount * Portions.LESS)
                    }
                    Small(stringResource(R.string.repeat_as_it_was)) {
                        onSetAmount(
                            component.id,
                            adjusting.asDefined.components
                                .firstOrNull { it.id == component.id }?.amount
                                ?: component.amount,
                        )
                    }
                    Small(stringResource(R.string.propose_more)) {
                        onSetAmount(component.id, component.amount * Portions.MORE)
                    }
                    Small(stringResource(R.string.propose_remove)) { onRemove(component.id) }
                }
            }
        }

        Text(
            text = stringResource(
                R.string.repeat_kcal,
                String.format(Locale.US, "%,d", adjusting.totalKcal),
            ),
            style = MaterialTheme.typography.titleMedium,
        )

        Button(onClick = onLog, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.repeat_adjust_log))
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.repeat_adjust_cancel))
        }
    }
}

/**
 * "Cucumber — 100 g · 16 kcal", or the reason it cannot be costed.
 *
 * [amount] has no default on purpose: the app's own "portion" takes its plural on the screen, where
 * the plural resources are (D37), and a default built here would be a silent way back to
 * "2 portion" for any caller that forgot to pass it.
 */
private fun describeComponent(component: MealComponent, amount: String): String =
    when (val worth = component.worth) {
        is LoggedFrom.Numbers -> "${component.food.name} — $amount · ${worth.kcal} kcal"
        is LoggedFrom.NotOnOffer -> "${component.food.name} — $amount · ?"
    }

@Composable
private fun Small(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * One of the owner's foods: what it is called, and everything it knows about itself.
 *
 * The whole row opens it, because picking a food and saying how much is now one action rather than
 * two — there is no "log it exactly as it was last time" any more, and there should not be: a
 * food is one entry now, not one per time it was logged, and it has no last time of its own.
 *
 * What it knows is shown as separate lines rather than one sentence. A food may genuinely have two
 * things to say — what 100 g are worth and what one of it is worth — and joining a Hebrew name to a
 * Latin figure gives the bidirectional algorithm licence to reorder them.
 */
@Composable
private fun OwnFood(food: Food, onPick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
    ) {
        Text(text = food.name, style = MaterialTheme.typography.bodyLarge)

        FoodWording.brand(food)?.let { brand ->
            Text(
                text = brand,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FoodWording.whatItKnows(food).forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Shown, never reconciled. The app cannot know which of the three facts is the wrong one,
        // and picking would be exactly the silent guess this whole model exists to avoid.
        FoodWording.disagreement(food)?.let { warning ->
            Text(
                text = warning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * How much of it, and which way he is counting.
 *
 * **A way of counting the food does not support is shown with its reason, not hidden.** A missing
 * field looks like a fault in the app; the same field with "Nothing knows what one bar weighs"
 * beside it says what would have to be true for it to work. That is the rule "nothing is ever
 * guessed" made visible rather than merely obeyed.
 *
 * The calorie count updates as he types, so he sees what is about to land on the record before it
 * lands rather than afterwards.
 */
@Composable
private fun HowMuch(
    choosing: Choosing,
    onCountAs: (CountedAs) -> Unit,
    onSetAmount: (String) -> Unit,
    onGivePortion: () -> Unit,
    onCancel: () -> Unit,
    onLog: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
    ) {
        Text(text = choosing.food.name, style = MaterialTheme.typography.bodyLarge)

        // The reason the other way is unavailable, said as that option's reason, and only when it
        // is actually unavailable — with the way to make counting possible when that is the gap.
        HowItIsCounted(
            countedAs = choosing.countedAs,
            unitName = choosing.food.facts.perUnit?.unitName ?: FoodFacts.PORTION,
            cannotWeigh = choosing.cannotWeigh,
            cannotCount = choosing.cannotCount,
            onCountAs = onCountAs,
            onGivePortion = onGivePortion,
        )

        OutlinedTextField(
            value = choosing.amount,
            onValueChange = onSetAmount,
            label = { Text(stringResource(R.string.food_how_much)) },
            isError = choosing.amountTooMuch,
            singleLine = true,
            // Decimal, not Number: an amount is read as a decimal and a comma is accepted for
            // the point, so a keyboard with neither would refuse half a bar.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        AmountTooMuch(
            tooMuch = choosing.amountTooMuch,
            most = choosing.most,
            countedAs = choosing.countedAs,
        )

        choosing.preview?.let { numbers ->
            Text(
                text = stringResource(R.string.repeat_kcal, numbers.kcal.toString()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Tight),
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.repeat_adjust_cancel))
            }
            Button(onClick = onLog, enabled = choosing.canLog) {
                Text(stringResource(R.string.repeat_adjust_log))
            }
        }
    }
}
