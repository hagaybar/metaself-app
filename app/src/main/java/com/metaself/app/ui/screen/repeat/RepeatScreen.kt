package com.metaself.app.ui.screen.repeat

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.metaself.app.R
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.PerHundredMillilitres
import com.metaself.app.domain.food.LoggedFrom
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.domain.food.SavedMeal
import com.metaself.app.domain.amount.BelievableAmount
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.food.AmountTooMuch
import com.metaself.app.ui.food.FoodWording
import com.metaself.app.ui.food.HowItIsCounted
import com.metaself.app.ui.food.named
import com.metaself.app.ui.food.namesTogether
import com.metaself.app.ui.food.saidAs
import com.metaself.app.ui.portion.AmountBox
import com.metaself.app.ui.portion.PortionWording
import com.metaself.app.ui.portion.portionWords
import com.metaself.app.ui.portion.unitWord
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
 * A tap on a meal's row opens it for the day, in place — its parts and their amounts, with Log it
 * and Leave it alone — and writes nothing (public issue #21). It used to log the meal the moment it
 * was touched, while the same-looking row on the manager opens it: one gesture, two meanings, one of
 * them a write he took for navigation. Log it on a meal left as it was logs exactly what that tap
 * did. What it holds is shown item by item, with each portion: repeating a two-portion row used to say only the
 * meal's name and its total, with no way to see that the quantity had carried. A number you cannot see is
 * a number you cannot trust. A part the app cannot cost — shown with a "?" — is skipped when the
 * meal is logged, not guessed at.
 *
 * A food is not one tap: tapping it asks how much first, with the amount left empty, because the
 * amount is the one thing the app will not fill in for him (D4). The note above both tabs says which
 * is which (D37) — it once said "One tap logs it" of both.
 *
 * The meal opens in place, not on a screen of its own, so that what he is changing stays next to
 * the other meals he might have picked instead.
 */
@Composable
fun RepeatScreen(
    state: RepeatUiState,
    /** Nothing here matched: describe these words instead, which may be none. */
    onDescribe: (String) -> Unit,
    /** Something on this list is wrong, or is a duplicate: the place to put it right. */
    onManageFoods: () -> Unit,
    onGivePortion: (foodId: Long) -> Unit,
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
    onSetComponentAmount: (Long, String) -> Unit,
    onStepComponent: (Long, Int) -> Unit,
    onRemoveComponent: (Long) -> Unit,
    onCancelAdjusting: () -> Unit,
    onLogAdjusted: () -> Unit,
    /** The adjuster's own search for something to put in: open it, type in it, leave it. */
    onBeginAddingToMeal: () -> Unit,
    onSearchToAdd: (String) -> Unit,
    onStopAddingToMeal: () -> Unit,
    /** Pick a food it found, then say how much, then put it in or go back to the search. */
    onPickToAdd: (foodId: Long) -> Unit,
    onCountAddedAs: (CountedAs) -> Unit,
    onSetAddedAmount: (String) -> Unit,
    onDropPicked: () -> Unit,
    onPutItIn: () -> Unit,
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
        // The foods manager was given this and it was never carried back here — the screen every
        // log passes through was the one still scrolling its own search out of reach.
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
            // D48's grouping: a sentence and the thing to do about it are one block.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
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
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
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
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
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
                // A question whose food the search no longer finds — renamed in the editor — is
                // still about his food, so it stays open, above the rows rather than in one.
                state.choosing?.takeIf { it.index < 0 }?.let { choosing ->
                    HowMuch(
                        choosing = choosing,
                        onCountAs = onCountAs,
                        onSetAmount = onSetAmount,
                        onGivePortion = { onGivePortion(choosing.food.id) },
                        onCancel = onCancelChoosing,
                        onLog = onLogChosen,
                    )
                    HorizontalDivider()
                }
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
                        onOpen = { onBeginAdjusting(index) },
                        onEdit = { onEditMeal(meal.id) },
                    )
                } else {
                    Adjuster(
                        adjusting = adjusting,
                        onSetAmount = onSetComponentAmount,
                        onStep = onStepComponent,
                        onRemove = onRemoveComponent,
                        onCancel = onCancelAdjusting,
                        onLog = onLogAdjusted,
                        adding = AddingToIt(
                            onBegin = onBeginAddingToMeal,
                            onSearch = onSearchToAdd,
                            onStop = onStopAddingToMeal,
                            onPick = onPickToAdd,
                            onCountAs = onCountAddedAs,
                            onSetAmount = onSetAddedAmount,
                            onGivePortion = onGivePortion,
                            onDrop = onDropPicked,
                            onPutItIn = onPutItIn,
                        ),
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

/**
 * One meal the owner built. The row opens it for today only; Change it opens it for good.
 *
 * The row writes nothing (public issue #21): logging is the press on Log it once it is open. A
 * screen reader hears the row as a button that opens that meal, and Change it with the meal's name,
 * so each row's two controls are two controls and not one word said once per meal (public issue #3).
 */
@Composable
private fun BuiltMeal(
    meal: SavedMeal,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.said_open_for_today, meal.name),
                role = Role.Button,
                onClick = onOpen,
            )
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
            // Ink, not red (D48): red is a refusal or a field that is wrong, and this is neither —
            // a fact about the record, set one step above the captions around it.
            Text(
                text = stringResource(R.string.repeat_meal_incomplete),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
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
            val change = stringResource(R.string.repeat_meal_edit)
            TextButton(
                onClick = onEdit,
                modifier = Modifier.saidAs(stringResource(R.string.said_for, change, meal.name)),
            ) {
                Text(change)
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
 *
 * **Each part's amount is typed** (D53 §6): its box opens holding what the meal has for it, and the
 * three proportions it replaced — Less, As it was, More — are gone with the proposal's.
 *
 * **It can add as well as take away** (issue #10): "Put something in" opens a search of its own
 * under the rows. While that step is open, Log it and Leave it alone step aside — the step has its
 * own way out, and a food with an amount typed but not yet put in must not be left behind by a
 * press of the larger button below it.
 */
@Composable
private fun Adjuster(
    adjusting: Adjusting,
    onSetAmount: (Long, String) -> Unit,
    onStep: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit,
    onCancel: () -> Unit,
    onLog: () -> Unit,
    adding: AddingToIt,
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

        if (adjusting.isEmpty) {
            // An empty meal opens too — Put something in is how a part goes in for today — but it
            // says so, and Log it stays off below (public issue #21).
            Text(
                text = stringResource(R.string.repeat_meal_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        adjusting.rows.forEach { component ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                Text(
                    text = describeComponent(component, portionWords(component)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                // The amount the meal has for it, typed over for today (D53 §6), in the part's own
                // unit; − and + for a counted part, never for millilitres, which are measured (D56).
                // The worth is the food's and is not edited here.
                val millilitres = PerHundredMillilitres.inMillilitres(component.countedAs, component.food.facts)
                AmountBox(
                    text = adjusting.amountText(component),
                    unitWords = unitWord(
                        usableAmount(component, adjusting.amountText(component)),
                        PortionWording.unitOf(component),
                    ),
                    counted = component.countedAs == CountedAs.UNITS && !millilitres,
                    tooMuch = adjusting.amountTooMuch(component),
                    most = BelievableAmount.amountEaten(component.countedAs, component.food.facts),
                    inGrams = component.countedAs == CountedAs.GRAMS,
                    onText = { onSetAmount(component.id, it) },
                    onStep = { onStep(component.id, it) },
                    of = component.food.name,
                    inMillilitres = millilitres,
                )
                // Drawn once per part: said with the part it takes out (public issue #3).
                Small(
                    stringResource(R.string.propose_remove),
                    said = stringResource(R.string.said_remove, component.food.name),
                ) { onRemove(component.id) }
            }
        }

        if (adjusting.finding == null) {
            TextButton(onClick = adding.onBegin) {
                Text(stringResource(R.string.builder_add_something))
            }
        } else {
            PutSomethingIn(adjusting = adjusting, adding = adding)
        }

        Text(
            text = stringResource(
                R.string.repeat_kcal,
                String.format(Locale.US, "%,d", adjusting.totalKcal),
            ),
            style = MaterialTheme.typography.titleMedium,
        )

        if (adjusting.finding == null) {
            // Nothing is logged at a part's last amount while its box says something else: the
            // part is named, as the proposal screen names a row (D53 §6).
            val blocked = adjusting.rows.firstOrNull { it.id == adjusting.blockedBy }
            blocked?.let {
                Text(
                    text = stringResource(R.string.propose_blocked, it.food.name),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            // Off, too, when there is nothing it could put on the day: an empty meal, or one none of
            // whose parts can be costed.
            Button(onClick = onLog, enabled = adjusting.canLog, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.repeat_adjust_log))
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.repeat_adjust_cancel))
            }
        }
    }
}

/** What the adjuster's add step can ask for — one value, so the adjuster's own list stays readable. */
private class AddingToIt(
    val onBegin: () -> Unit,
    val onSearch: (String) -> Unit,
    val onStop: () -> Unit,
    val onPick: (Long) -> Unit,
    val onCountAs: (CountedAs) -> Unit,
    val onSetAmount: (String) -> Unit,
    val onGivePortion: (Long) -> Unit,
    val onDrop: () -> Unit,
    val onPutItIn: () -> Unit,
)

/**
 * Something that is not in the meal, for today only: find it, then say how much.
 *
 * The search is the adjuster's own. The screen's closes whatever is open, which here would throw
 * away the adjustment being built. What it finds is drawn as the foods tab draws his foods, and a
 * food the meal already holds is named rather than offered (D41), in the builder's words — a meal
 * holds a food once, and a tap that did nothing would say nothing.
 *
 * Picking one asks how much with the foods tab's own question, empty until he types (D4), and
 * "Put it in" adds it to today's rows. Nothing is written anywhere until the meal is logged.
 */
@Composable
private fun PutSomethingIn(adjusting: Adjusting, adding: AddingToIt) {
    val picked = adjusting.adding
    if (picked != null) {
        HowMuch(
            choosing = picked,
            onCountAs = adding.onCountAs,
            onSetAmount = adding.onSetAmount,
            onGivePortion = { adding.onGivePortion(picked.food.id) },
            onCancel = adding.onDrop,
            onLog = adding.onPutItIn,
            cancelLabel = R.string.builder_drop_pending,
            confirmLabel = R.string.builder_put_it_in,
        )
        return
    }

    val finding = adjusting.finding.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(
            text = stringResource(R.string.builder_add_something),
            style = MaterialTheme.typography.titleSmall,
        )
        OutlinedTextField(
            value = finding,
            onValueChange = adding.onSearch,
            label = { Text(stringResource(R.string.foods_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Above the list, not below it: the list may be every food he has.
        TextButton(onClick = adding.onStop) {
            Text(stringResource(R.string.repeat_adjust_add_stop))
        }
        if (adjusting.alreadyIn.isNotEmpty()) {
            Text(
                text = pluralStringResource(
                    R.plurals.builder_already_in,
                    adjusting.alreadyIn.size,
                    namesTogether(adjusting.alreadyIn.map { named(it) }),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (finding.isNotBlank() && adjusting.offered.isEmpty() && adjusting.alreadyIn.isEmpty()) {
            Text(
                text = stringResource(R.string.foods_no_match, finding.trim()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            adjusting.offered.forEach { food ->
                OwnFood(food = food, onPick = { adding.onPick(food.id) })
                HorizontalDivider()
            }
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
private fun Small(label: String, said: String? = null, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.saidAs(said),
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
            // Ink, not red (D48): red is a refusal or a field that is wrong, and this is neither —
            // a fact about the record, set one step above the captions around it.
            Text(
                text = warning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
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
 *
 * Asked in two places: logging one of his foods, and putting one into a meal for today only, where
 * its buttons say "Not this one" and "Put it in" instead.
 */
@Composable
private fun HowMuch(
    choosing: Choosing,
    onCountAs: (CountedAs) -> Unit,
    onSetAmount: (String) -> Unit,
    onGivePortion: () -> Unit,
    onCancel: () -> Unit,
    onLog: () -> Unit,
    @StringRes cancelLabel: Int = R.string.repeat_adjust_cancel,
    @StringRes confirmLabel: Int = R.string.repeat_adjust_log,
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
            inMillilitres = choosing.inMillilitres,
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
                Text(stringResource(cancelLabel))
            }
            Button(onClick = onLog, enabled = choosing.canLog) {
                Text(stringResource(confirmLabel))
            }
        }
    }
}
