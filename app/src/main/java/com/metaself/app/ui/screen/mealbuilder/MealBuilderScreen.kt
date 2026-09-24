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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.metaself.app.domain.ai.Figure
import com.metaself.app.domain.food.FactGroup
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodField
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.LoggedFrom
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.food.AmountTooMuch
import com.metaself.app.ui.food.AskBeforeDeleting
import com.metaself.app.ui.food.FoodWording
import com.metaself.app.ui.food.HowItIsCounted
import com.metaself.app.ui.food.ReviewActions
import com.metaself.app.ui.food.ReviewTheFigures
import com.metaself.app.ui.food.ReviewedBox
import com.metaself.app.ui.food.changedBoxColors
import com.metaself.app.ui.food.changedByReview
import com.metaself.app.ui.food.figureSaid
import com.metaself.app.ui.food.named
import com.metaself.app.ui.food.namesTogether
import com.metaself.app.ui.food.saidAs
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
    onChangePart: (Long) -> Unit,
    onChangeFood: (Long) -> Unit,
    onBeginCreatingFood: () -> Unit,
    onSetNewFood: (FoodForm) -> Unit,
    onCreateFood: () -> Unit,
    onCancelCreatingFood: () -> Unit,
    newFoodReview: ReviewActions,
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
        // An action that threw says so in the refusal's place (ActionRefused), with the same way out.
        val sentence = state.refusal ?: state.failed?.let { stringResource(it.sentence) }
        sentence?.let { refusal ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                Text(
                    text = refusal,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onDismissRefusal) {
                    Text(stringResource(R.string.foods_refusal_dismiss))
                }
            }
        }

        // The one gate: a meal is only something he built AND named, and the app never invents a
        // name for him.
        if (state.needsAName) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
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
            }
            return@MetaSelfScreen
        }

        val meal = state.meal ?: return@MetaSelfScreen

        // D48's grouping: the name and everything said about it are one block.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
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
        }

        // --- Waiting for an amount -------------------------------------------------------------
        // Above what is in the meal, because these are the unfinished business: each one is a food he
        // chose in the list, and each becomes part of the meal the moment its amount can be costed.
        if (state.pending.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
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
        }

        // --- What is in it so far ------------------------------------------------------------
        if (meal.components.isEmpty()) {
            Text(
                text = stringResource(R.string.builder_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    meal.components.forEach { component ->
                        InMeal(
                            component = component,
                            onChange = { onChangePart(component.id) },
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

        state.making?.let { making ->
            NewFood(
                making = making,
                onSetForm = onSetNewFood,
                onCreate = onCreateFood,
                onCancel = onCancelCreatingFood,
                review = newFoodReview,
            )
            return@MetaSelfScreen
        }

        // The search and what it says about what it found are one block.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
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
                // And a way to its part, so "take it out and put it back" is never the only way to
                // change how much of it there is (D53 §7, #4).
                state.alreadyIn.forEach { food ->
                    TextButton(onClick = { onChangeFood(food.id) }) {
                        Text(stringResource(R.string.builder_change_how_much, named(food)))
                    }
                }
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

/**
 * One food already in the meal, with how much of it and where it sits in his order.
 *
 * Its name and amount are one tap that opens the panel for changing how much of it there is
 * (D53 §7, #4); Up, Down and Remove stay on the row, outside that tap.
 */
@Composable
private fun InMeal(
    component: MealComponent,
    onChange: () -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onChange),
            verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
        ) {
            Text(text = component.food.name, style = MaterialTheme.typography.bodyLarge)
            BrandLine(component.food)
            Text(
                text = describe(component, portionWords(component)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The food no longer knows the thing this counts it in. Said rather than shown as a
        // smaller total that looks right.
        if (component.cannotBeCosted) {
            // Ink, not red (D48): red is a refusal or a field that is wrong, and this is neither —
            // a fact about the record, set one step above the captions around it.
            Text(
                text = stringResource(R.string.builder_cannot_cost),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Drawn once per part, so each is said with the part it moves or takes out: two parts'
            // Remove are two controls to a screen reader, not one name twice (public issue #3).
            val name = component.food.name
            TextButton(onClick = onUp, modifier = Modifier.saidAs(stringResource(R.string.said_move_up, name))) {
                Text(stringResource(R.string.builder_up))
            }
            TextButton(onClick = onDown, modifier = Modifier.saidAs(stringResource(R.string.said_move_down, name))) {
                Text(stringResource(R.string.builder_down))
            }
            TextButton(onClick = onRemove, modifier = Modifier.saidAs(stringResource(R.string.said_remove, name))) {
                Text(stringResource(R.string.propose_remove))
            }
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
 * nothing to correct with, since a part's amount could not then be changed. So the same "Put it in" this
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

        // A way of counting this food does not support is shown WITH ITS REASON, never hidden: the
        // owner is owed the reason his own food cannot answer the question, not a shorter list.
        // Several foods can wait here at once, so every control below is also said with the
        // food's name — the chips, the box and both buttons (public issue #3).
        val name = pending.food.name
        HowItIsCounted(
            countedAs = pending.countedAs,
            unitName = pending.unitName,
            cannotWeigh = pending.cannotWeigh,
            cannotCount = pending.cannotCount,
            onCountAs = onCountAs,
            of = name,
        )

        OutlinedTextField(
            value = pending.amount,
            onValueChange = onSetAmount,
            label = { Text(stringResource(R.string.food_how_much)) },
            isError = pending.amountTooMuch,
            singleLine = true,
            // Decimal, not Number: an amount is read as a decimal and a comma is accepted for
            // the point, so a keyboard with neither would refuse half a bar.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().saidAs(stringResource(R.string.said_how_much, name)),
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
            Button(
                onClick = onConfirm,
                enabled = pending.canAdd,
                modifier = Modifier.saidAs(stringResource(R.string.said_put_in, name)),
            ) {
                Text(stringResource(R.string.builder_put_it_in))
            }
            // Dropping one touches the meal not at all: nothing was put in it to take out.
            TextButton(
                onClick = onDrop,
                modifier = Modifier.saidAs(stringResource(R.string.said_leave_out, name)),
            ) { Text(stringResource(R.string.builder_drop_pending)) }
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

        HowItIsCounted(
            countedAs = adding.countedAs,
            unitName = adding.unitName,
            cannotWeigh = adding.cannotWeigh,
            cannotCount = adding.cannotCount,
            onCountAs = onCountAs,
        )

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
                // A part already in is changed in place, not put in a second time (#4).
                Text(
                    stringResource(
                        if (adding.changing != null) R.string.builder_change_it else R.string.builder_put_it_in,
                    ),
                )
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.foods_cancel)) }
        }
    }
}

/**
 * A food made without leaving the meal, through the same door as every other.
 *
 * Its form lives in the view model ([MakingFood]) rather than in a `remember`, because a review
 * asked for here has to outlive its request (D54). The review is drawn by the same pieces as My
 * foods' editor draws it — the button under the name, what would change under the button, and each
 * box the review changed drawn in the teal accent (D54 §11).
 */
@Composable
private fun NewFood(
    making: MakingFood,
    onSetForm: (FoodForm) -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
    review: ReviewActions,
) {
    val form = making.form
    val changed = making.reviewing.changedBoxes

    // Grouped as My foods' editor is (D48): each group one block, tight inside, a section apart.
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(
                text = stringResource(R.string.builder_make_a_food),
                style = MaterialTheme.typography.titleSmall,
            )
            Field(form.name, { onSetForm(form.copy(name = it)) }, stringResource(R.string.foods_field_name), making.errorFor(FoodField.NAME))
            // The same food form as My foods, so the same true sentence: decimals are kept here (D38).
            Text(
                text = stringResource(R.string.food_facts_decimals_kept),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
        }

        ReviewTheFigures(
            reviewing = making.reviewing,
            offered = FoodField.NAME !in making.errors,
            unitName = form.unitName,
            actions = review,
            // Make it is this panel's Save, so the line after Apply these changes names it.
            appliedLine = R.plurals.review_applied_new_food,
        )

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            // Each group's heading is a kicker, as My foods sets it — not small print level with the
            // notes, which left the two groups of four identical labels with nothing between them.
            // A screen reader could not hear that heading from inside a box, so each box is also
            // named by its group: "Calories per 100 g", not a second "Calories" (public issue #3).
            Text(
                text = stringResource(R.string.foods_group_per_100g),
                style = MaterialTheme.typography.titleSmall,
            )
            Field(form.kcalPer100g, { onSetForm(form.copy(kcalPer100g = it)) }, stringResource(R.string.foods_field_kcal), making.errorFor(FoodField.PER_100G), numeric = true, changed = ReviewedBox(FactGroup.PER_100G, Figure.KCAL) in changed, said = figureSaid(stringResource(R.string.foods_field_kcal), FactGroup.PER_100G, form.unitName))
            Field(form.proteinPer100g, { onSetForm(form.copy(proteinPer100g = it)) }, stringResource(R.string.foods_field_protein), null, numeric = true, changed = ReviewedBox(FactGroup.PER_100G, Figure.PROTEIN) in changed, said = figureSaid(stringResource(R.string.foods_field_protein), FactGroup.PER_100G, form.unitName))
            Field(form.carbsPer100g, { onSetForm(form.copy(carbsPer100g = it)) }, stringResource(R.string.foods_field_carbs), null, numeric = true, changed = ReviewedBox(FactGroup.PER_100G, Figure.CARBS) in changed, said = figureSaid(stringResource(R.string.foods_field_carbs), FactGroup.PER_100G, form.unitName))
            Field(form.fatPer100g, { onSetForm(form.copy(fatPer100g = it)) }, stringResource(R.string.foods_field_fat), null, numeric = true, changed = ReviewedBox(FactGroup.PER_100G, Figure.FAT) in changed, said = figureSaid(stringResource(R.string.foods_field_fat), FactGroup.PER_100G, form.unitName))
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(
                text = stringResource(R.string.foods_group_per_unit),
                style = MaterialTheme.typography.titleSmall,
            )
            Field(form.unitName, { onSetForm(form.copy(unitName = it)) }, stringResource(R.string.foods_field_unit), making.errorFor(FoodField.UNIT_NAME))
            Field(form.kcalPerUnit, { onSetForm(form.copy(kcalPerUnit = it)) }, stringResource(R.string.foods_field_kcal), making.errorFor(FoodField.PER_UNIT), numeric = true, changed = ReviewedBox(FactGroup.PER_UNIT, Figure.KCAL) in changed, said = figureSaid(stringResource(R.string.foods_field_kcal), FactGroup.PER_UNIT, form.unitName))
            Field(form.proteinPerUnit, { onSetForm(form.copy(proteinPerUnit = it)) }, stringResource(R.string.foods_field_protein), null, numeric = true, changed = ReviewedBox(FactGroup.PER_UNIT, Figure.PROTEIN) in changed, said = figureSaid(stringResource(R.string.foods_field_protein), FactGroup.PER_UNIT, form.unitName))
            Field(form.carbsPerUnit, { onSetForm(form.copy(carbsPerUnit = it)) }, stringResource(R.string.foods_field_carbs), null, numeric = true, changed = ReviewedBox(FactGroup.PER_UNIT, Figure.CARBS) in changed, said = figureSaid(stringResource(R.string.foods_field_carbs), FactGroup.PER_UNIT, form.unitName))
            Field(form.fatPerUnit, { onSetForm(form.copy(fatPerUnit = it)) }, stringResource(R.string.foods_field_fat), null, numeric = true, changed = ReviewedBox(FactGroup.PER_UNIT, Figure.FAT) in changed, said = figureSaid(stringResource(R.string.foods_field_fat), FactGroup.PER_UNIT, form.unitName))
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            making.errorFor(FoodField.NOTHING_KNOWN)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                Button(onClick = onCreate) {
                    Text(stringResource(R.string.builder_make_it))
                }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.foods_cancel)) }
            }
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
    /** The review wrote this box's value and it is not saved yet: drawn and said so (D54 §11). */
    changed: Boolean = false,
    /** What a screen reader calls the box when its label names another box too (public issue #3). */
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
        modifier = Modifier.fillMaxWidth().changedByReview(changed).saidAs(said),
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
