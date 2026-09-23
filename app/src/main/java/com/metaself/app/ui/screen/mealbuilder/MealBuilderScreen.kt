package com.metaself.app.ui.screen.mealbuilder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.metaself.app.R
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodField
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.LoggedFrom
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.food.AmountTooMuch
import com.metaself.app.ui.food.AskBeforeDeleting
import com.metaself.app.ui.food.FoodWording
import com.metaself.app.ui.portion.portionWords
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/**
 * Building a meal: name it, put foods in it with amounts, take them out, come back later.
 *
 * **There is no finish button and nothing to save.** What is in the meal is saved as he goes, so
 * leaving halfway through is not an interruption — a half-built salad is simply a meal with fewer
 * things in it, and it waits. A food he has picked but not yet put in — waiting for an amount, or
 * opened for adding — is not saved, and is gone when he leaves; the heading says so rather than
 * claiming everything is kept (D37). Keeping it would mean storing a part with no amount, which the
 * record cannot hold.
 *
 * **A food he has not got yet can be made here**, without leaving the meal. Sending him to the foods
 * manager to make the tahini and then back again would lose the salad he is halfway through, and the
 * food he makes here goes through the same door as every other, so it is indistinguishable
 * afterwards from one made by eating it.
 */
@Composable
fun MealBuilderScreen(
    state: MealBuilderUiState,
    onSetName: (String) -> Unit,
    onName: () -> Unit,
    onRename: (String) -> Unit,
    onSearch: (String) -> Unit,
    onBeginAdding: (Long) -> Unit,
    onCountAs: (CountedAs) -> Unit,
    onSetAmount: (String) -> Unit,
    onConfirmAdding: () -> Unit,
    onCancelAdding: () -> Unit,
    onSetPendingAmount: (Long, String) -> Unit,
    onCountPendingAs: (Long, CountedAs) -> Unit,
    onConfirmPending: (Long) -> Unit,
    onDropPending: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
    onBeginCreatingFood: () -> Unit,
    onCreateFood: (FoodForm) -> Unit,
    onCancelCreatingFood: () -> Unit,
    onDelete: () -> Unit,
    onDismissRefusal: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(
        title = state.meal?.name ?: stringResource(R.string.builder_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        state.refusal?.let { refusal ->
            Text(
                text = refusal,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onDismissRefusal) {
                Text(stringResource(R.string.foods_refusal_dismiss))
            }
        }

        // The one gate: a meal is only something he built AND named, and the app never invents a
        // name for him.
        if (state.needsAName) {
            Text(
                text = stringResource(R.string.builder_name_first),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = state.typedName,
                onValueChange = onSetName,
                label = { Text(stringResource(R.string.builder_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onName,
                enabled = state.canName,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.builder_start))
            }
            return@MetaSelfScreen
        }

        val meal = state.meal ?: return@MetaSelfScreen

        Text(
            text = stringResource(R.string.builder_nothing_to_save),
            style = MaterialTheme.typography.bodySmall,
            color = MetaSelfInk.two,
        )

        // Renaming a meal retitles every day it was ever eaten, exactly as renaming a food
        // re-labels every day that food appears on — and not one stored number moves for either.
        // Keyed on the stored name so that a rename made elsewhere is picked up rather than
        // overwritten by a stale field.
        var typedName by remember(meal.name) { mutableStateOf(meal.name) }
        OutlinedTextField(
            value = typedName,
            onValueChange = { typedName = it },
            label = { Text(stringResource(R.string.builder_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (typedName.isNotBlank() && typedName != meal.name) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                Button(onClick = { onRename(typedName) }) {
                    Text(stringResource(R.string.builder_rename))
                }
                TextButton(onClick = { typedName = meal.name }) {
                    Text(stringResource(R.string.foods_cancel))
                }
            }
            Text(
                text = stringResource(R.string.builder_rename_retitles),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
        }

        // --- Waiting for an amount -------------------------------------------------------------
        // Above what is in the meal, because these are the unfinished business: each one is a food he
        // chose in the list, and each becomes part of the meal the moment its amount can be costed.
        if (state.pending.isNotEmpty()) {
            Text(
                text = stringResource(R.string.builder_how_much_each),
                style = MaterialTheme.typography.bodyMedium,
            )
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                state.pending.forEach { waiting ->
                    Waiting(
                        pending = waiting,
                        onCountAs = { onCountPendingAs(waiting.food.id, it) },
                        onSetAmount = { onSetPendingAmount(waiting.food.id, it) },
                        onConfirm = { onConfirmPending(waiting.food.id) },
                        onDrop = { onDropPending(waiting.food.id) },
                    )
                    HorizontalDivider()
                }
            }
        }

        // --- What is in it so far ------------------------------------------------------------
        if (meal.components.isEmpty()) {
            Text(
                text = stringResource(R.string.builder_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                meal.components.forEach { component ->
                    InMeal(
                        component = component,
                        onRemove = { onRemove(component.id) },
                        onUp = { onMove(component.id, -1) },
                        onDown = { onMove(component.id, 1) },
                    )
                    HorizontalDivider()
                }
            }
            Text(
                text = stringResource(R.string.repeat_kcal, meal.kcal.toString()),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        // --- Putting something in --------------------------------------------------------------
        val adding = state.adding
        if (adding != null) {
            HowMuchOfIt(
                adding = adding,
                onCountAs = onCountAs,
                onSetAmount = onSetAmount,
                onConfirm = onConfirmAdding,
                onCancel = onCancelAdding,
            )
            return@MetaSelfScreen
        }

        if (state.creating) {
            NewFood(onCreate = onCreateFood, onCancel = onCancelCreatingFood)
            return@MetaSelfScreen
        }

        Text(
            text = stringResource(R.string.builder_add_something),
            style = MaterialTheme.typography.titleSmall,
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = onSearch,
            label = { Text(stringResource(R.string.foods_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // A food left out of the results because it is already here is named, not denied (D41):
        // saying nothing matched a food drawn a few lines up was issue #14. Shown beside any new
        // foods offered below, so every food the search found is on screen as a row or a name.
        if (state.alreadyIn.isNotEmpty()) {
            Text(
                text = pluralStringResource(
                    R.plurals.builder_already_in,
                    state.alreadyIn.size,
                    namesTogether(state.alreadyIn.map { named(it) }),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // Never "in this meal": a food with no amount is not in it (D37).
        if (state.alreadyWaiting.isNotEmpty()) {
            Text(
                text = pluralStringResource(
                    R.plurals.builder_already_waiting,
                    state.alreadyWaiting.size,
                    namesTogether(state.alreadyWaiting.map { named(it) }),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.searchedAndFoundNothing) {
            Text(
                text = stringResource(R.string.foods_no_match, state.query.trim()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Here rather than on another screen: he has realised the tahini is not in his list, and
        // going away to make it would lose the salad he is halfway through.
        TextButton(onClick = onBeginCreatingFood) {
            Text(stringResource(R.string.builder_make_a_food))
        }

        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            state.candidates.forEach { food ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBeginAdding(food.id) }
                        .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
                ) {
                    Text(text = food.name, style = MaterialTheme.typography.bodyLarge)
                    // The search finds a food by its brand (D41), so the row has to show it, or
                    // `Dairyco` would offer a row reading only `Milk` — a match on text he cannot see.
                    BrandLine(food)
                    FoodWording.whatItKnows(food).forEach {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
        }

        // Last on the screen, after the search and everything it offers. It used to sit between
        // the search box and its results, which is the path a thumb travels over and over while
        // building a meal — the one place a destructive button must never be (issue #49).
        // Deleting a meal never touches a past day: the day loses its title and shows its items,
        // which is what every day looked like before meals he built existed. It touches no food
        // either — the schema sees to that.
        // It cannot be put back, so it asks first, in place of the button (D36). onDelete also
        // leaves the screen, so it is reached only from the question's Delete: Keep it cannot
        // navigate because it never gets near it. Held here rather than in the view model because
        // nothing refuses a meal's delete and nothing else reads it; declared at the button, so a
        // detour into adding something or making a food forgets the question.
        var askingToDelete by remember(meal.id) { mutableStateOf(false) }
        if (askingToDelete) {
            AskBeforeDeleting(
                name = meal.name,
                onDelete = {
                    askingToDelete = false
                    onDelete()
                },
                onKeep = { askingToDelete = false },
            )
        } else {
            TextButton(onClick = { askingToDelete = true }) {
                Text(stringResource(R.string.builder_delete))
            }
        }
    }
}

/**
 * Any number of names as one phrase — "Cucumber", "Cucumber and Olive oil", "Cucumber, Olive oil
 * and Tomato" — in the order given. That is the search's order, not always the order on screen: the
 * foods found by a name come first and those found only by their brand after them (D41), each in
 * the order they are drawn.
 *
 * Folded rather than one resource per count because a meal can hold more foods than any fixed set
 * of resources would cover. The joiners are resources so a translation can reorder them.
 */
@Composable
private fun namesTogether(names: List<String>): String {
    if (names.size <= 1) return names.firstOrNull().orEmpty()
    var together = names.first()
    names.drop(1).dropLast(1).forEach { name ->
        together = stringResource(R.string.builder_names_more, together, name)
    }
    return stringResource(R.string.builder_names_last, together, names.last())
}

/**
 * A food as the sentence about it names it: `Milk (Dairyco)` when it has a real brand, else `Milk`.
 *
 * The search finds a food by its brand (D41), and two foods may share a name and differ only in
 * brand — so a bare name could read "Milk and Milk are already in this meal", or name a Milk while
 * offering a different one below. The brand is the one thing that tells them apart on screen. A
 * resource rather than concatenation, so a translation can place it.
 */
@Composable
private fun named(food: Food): String = FoodWording.brand(food)
    ?.let { brand -> stringResource(R.string.builder_food_with_brand, food.name, brand) }
    ?: food.name

/**
 * The brand under a row's name, when there is one — the same line an offered row prints.
 *
 * The search finds a food by its brand (D41), so a row it names as already here has to show the
 * brand too, or `Dairyco` would say "Milk is already in this meal" beside a row reading only `Milk`.
 */
@Composable
private fun BrandLine(food: Food) {
    FoodWording.brand(food)?.let { brand ->
        Text(
            text = brand,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One food already in the meal, with how much of it and where it sits in his order. */
@Composable
private fun InMeal(
    component: MealComponent,
    onRemove: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
    ) {
        Text(text = component.food.name, style = MaterialTheme.typography.bodyLarge)
        BrandLine(component.food)
        Text(
            text = describe(component, portionWords(component)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The food no longer knows the thing this counts it in. Said rather than shown as a
        // smaller total that looks right.
        if (component.cannotBeCosted) {
            Text(
                text = stringResource(R.string.builder_cannot_cost),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onUp) { Text(stringResource(R.string.builder_up)) }
            TextButton(onClick = onDown) { Text(stringResource(R.string.builder_down)) }
            TextButton(onClick = onRemove) { Text(stringResource(R.string.propose_remove)) }
        }
    }
}

/**
 * One food chosen in the list, waiting here for an amount.
 *
 * **Nothing is filled in.** The box starts empty and stays empty until he types, because a number put
 * there by the app and then saved is indistinguishable afterwards from one he measured (D4). Until
 * then it says it is waiting, and says why if the way it is being counted is one this food cannot
 * answer.
 *
 * **It goes in when he says so, and not before.** A row that joined the meal the instant what was
 * typed could be costed joined it at the first digit — "100" is typed as "1", then "10", then "100"
 * — which put one gram of cucumber in the salad, took this box off the screen mid-word, and left him
 * nothing to correct with, since a component's amount cannot be edited. So the same "Put it in" this
 * screen already uses, enabled on the same terms, with the running total under the box while he
 * types.
 */
@Composable
private fun Waiting(
    pending: Pending,
    onCountAs: (CountedAs) -> Unit,
    onSetAmount: (String) -> Unit,
    onConfirm: () -> Unit,
    onDrop: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
    ) {
        Text(text = pending.food.name, style = MaterialTheme.typography.bodyLarge)
        BrandLine(pending.food)

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            TextButton(
                onClick = { onCountAs(CountedAs.GRAMS) },
                enabled = pending.cannotWeigh == null,
            ) {
                Text(
                    text = stringResource(R.string.food_in_grams),
                    color = if (pending.countedAs == CountedAs.GRAMS) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            TextButton(
                onClick = { onCountAs(CountedAs.UNITS) },
                enabled = pending.cannotCount == null,
            ) {
                Text(
                    text = stringResource(R.string.food_in_units, pending.unitName),
                    color = if (pending.countedAs == CountedAs.UNITS) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        // A way of counting this food does not support is shown WITH ITS REASON, never hidden: the
        // owner is owed the reason his own food cannot answer the question, not a shorter list.
        listOfNotNull(pending.cannotWeigh, pending.cannotCount).forEach { reason ->
            Text(
                text = FoodWording.why(reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = pending.amount,
            onValueChange = onSetAmount,
            label = { Text(stringResource(R.string.food_how_much)) },
            isError = pending.amountTooMuch,
            singleLine = true,
            // Decimal, not Number: an amount is read as a decimal and a comma is accepted for
            // the point, so a keyboard with neither would refuse half a bar.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        AmountTooMuch(
            tooMuch = pending.amountTooMuch,
            most = pending.most,
            countedAs = pending.countedAs,
        )
        val preview = pending.preview
        if (preview != null) {
            Text(
                text = stringResource(R.string.repeat_kcal, preview.kcal.toString()),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                text = stringResource(R.string.builder_waiting_for_amount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Button(onClick = onConfirm, enabled = pending.canAdd) {
                Text(stringResource(R.string.builder_put_it_in))
            }
            // Dropping one touches the meal not at all: nothing was put in it to take out.
            TextButton(onClick = onDrop) { Text(stringResource(R.string.builder_drop_pending)) }
        }
    }
}

/**
 * How much of it goes in, and which way it is counted.
 *
 * The same question the logging screen asks, with the same answer: only the ways the food knows are
 * on offer, and the other is shown with its reason rather than quietly missing.
 */
@Composable
private fun HowMuchOfIt(
    adding: Adding,
    onCountAs: (CountedAs) -> Unit,
    onSetAmount: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(text = adding.food.name, style = MaterialTheme.typography.titleSmall)

        // What making this food just changed about the figures it already held (D45, issue #13).
        // Here because this panel is the only surface this screen has, and with no button of its
        // own: it goes when the panel does. In the secondary colour the panel's other explanatory
        // lines use.
        adding.retaughtNotice?.let { said ->
            Text(
                text = said,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            TextButton(onClick = { onCountAs(CountedAs.GRAMS) }, enabled = adding.cannotWeigh == null) {
                Text(
                    text = stringResource(R.string.food_in_grams),
                    color = if (adding.countedAs == CountedAs.GRAMS) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            TextButton(onClick = { onCountAs(CountedAs.UNITS) }, enabled = adding.cannotCount == null) {
                Text(
                    text = stringResource(R.string.food_in_units, adding.unitName),
                    color = if (adding.countedAs == CountedAs.UNITS) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        listOfNotNull(adding.cannotWeigh, adding.cannotCount).forEach { reason ->
            Text(
                text = FoodWording.why(reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = adding.amount,
            onValueChange = onSetAmount,
            label = { Text(stringResource(R.string.food_how_much)) },
            isError = adding.amountTooMuch,
            singleLine = true,
            // Decimal, not Number: an amount is read as a decimal and a comma is accepted for
            // the point, so a keyboard with neither would refuse half a bar.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        AmountTooMuch(
            tooMuch = adding.amountTooMuch,
            most = adding.most,
            countedAs = adding.countedAs,
        )
        adding.preview?.let {
            Text(
                text = stringResource(R.string.repeat_kcal, it.kcal.toString()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Button(onClick = onConfirm, enabled = adding.canAdd) {
                Text(stringResource(R.string.builder_put_it_in))
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.foods_cancel)) }
        }
    }
}

/** A food made without leaving the meal, through the same door as every other. */
@Composable
private fun NewFood(onCreate: (FoodForm) -> Unit, onCancel: () -> Unit) {
    var form by remember { mutableStateOf(FoodForm()) }
    var showErrors by remember { mutableStateOf(false) }
    val errors = form.errors()

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(
            text = stringResource(R.string.builder_make_a_food),
            style = MaterialTheme.typography.titleSmall,
        )
        Field(form.name, { form = form.copy(name = it) }, stringResource(R.string.foods_field_name), errors[FoodField.NAME].takeIf { showErrors })
        // The same food form as My foods, so the same true sentence: decimals are kept here (D38).
        Text(
            text = stringResource(R.string.food_facts_decimals_kept),
            style = MaterialTheme.typography.bodySmall,
            color = MetaSelfInk.two,
        )

        Text(
            text = stringResource(R.string.foods_group_per_100g),
            style = MaterialTheme.typography.bodySmall,
        )
        Field(form.kcalPer100g, { form = form.copy(kcalPer100g = it) }, stringResource(R.string.foods_field_kcal), errors[FoodField.PER_100G].takeIf { showErrors }, numeric = true)
        Field(form.proteinPer100g, { form = form.copy(proteinPer100g = it) }, stringResource(R.string.foods_field_protein), null, numeric = true)
        Field(form.carbsPer100g, { form = form.copy(carbsPer100g = it) }, stringResource(R.string.foods_field_carbs), null, numeric = true)
        Field(form.fatPer100g, { form = form.copy(fatPer100g = it) }, stringResource(R.string.foods_field_fat), null, numeric = true)

        Text(
            text = stringResource(R.string.foods_group_per_unit),
            style = MaterialTheme.typography.bodySmall,
        )
        Field(form.unitName, { form = form.copy(unitName = it) }, stringResource(R.string.foods_field_unit), errors[FoodField.UNIT_NAME].takeIf { showErrors })
        Field(form.kcalPerUnit, { form = form.copy(kcalPerUnit = it) }, stringResource(R.string.foods_field_kcal), errors[FoodField.PER_UNIT].takeIf { showErrors }, numeric = true)
        Field(form.proteinPerUnit, { form = form.copy(proteinPerUnit = it) }, stringResource(R.string.foods_field_protein), null, numeric = true)
        Field(form.carbsPerUnit, { form = form.copy(carbsPerUnit = it) }, stringResource(R.string.foods_field_carbs), null, numeric = true)
        Field(form.fatPerUnit, { form = form.copy(fatPerUnit = it) }, stringResource(R.string.foods_field_fat), null, numeric = true)

        if (showErrors) {
            errors[FoodField.NOTHING_KNOWN]?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Button(
                onClick = { if (errors.isEmpty()) onCreate(form) else showErrors = true },
            ) {
                Text(stringResource(R.string.builder_make_it))
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.foods_cancel)) }
        }
    }
}

/**
 * One field of the food form, the same one My foods draws.
 *
 * [numeric] picks the keyboard, and Decimal rather than Number for the reason that form gives: a
 * food's facts are kept exactly as typed, decimals included (D38).
 */
@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    numeric: Boolean = false,
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
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * "100 g · 16 kcal", or the amount with a question mark when nothing can cost it.
 *
 * [amount] has no default on purpose: the app's own "portion" takes its plural on the screen, where
 * the plural resources are (D37), and a default built here would be a silent way back to
 * "2 portion" for any caller that forgot to pass it.
 */
private fun describe(component: MealComponent, amount: String): String =
    when (val worth = component.worth) {
        is LoggedFrom.Numbers -> "$amount · ${worth.kcal} kcal"
        is LoggedFrom.NotOnOffer -> "$amount · ?"
    }
