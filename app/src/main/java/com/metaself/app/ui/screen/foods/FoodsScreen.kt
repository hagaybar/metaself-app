package com.metaself.app.ui.screen.foods

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalHapticFeedback
import com.metaself.app.ui.theme.Feel
import com.metaself.app.ui.theme.givesUnderPress
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodField
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.food.AskBeforeDeleting
import com.metaself.app.ui.food.FoodWording
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/**
 * The screen the owner's food list was put right on, on its own.
 *
 * **Nothing navigates here any more**: the food list is now the first tab of the foods-and-meals
 * manager, which brings its own title bar, and that is where every way in lands. This composable is
 * kept because the render tests written against it assert the pairing of the standard chrome with
 * [FoodsContent] — the very pairing the split into a reusable list had to preserve — so they are
 * what guards the split, and deleting the screen would delete the guard with it.
 *
 * Renaming, correcting, deleting, hiding and joining two duplicates into one, in one place, because
 * they are one job: he has noticed the list has a duplicate in it, or a wrong number, or a name he
 * has changed his mind about, and he has sat down to fix it.
 *
 * **Everything opens in place rather than on a screen of its own.** What he is editing stays next to
 * the foods around it, which is what he needs when the thing he is deciding is whether two entries
 * are the same thing. Deleting a food and joining two ask first, in place too, and the question or
 * the refusal is always drawn where he can see it: in the editor where he pressed Delete, or brought
 * into view from the top of the list (D36).
 *
 * Two things this screen shows that nothing else can. The first is the count of foods that know
 * nothing but "one portion of it was worth this" — the ones the conversion of his record could say
 * least about — as a filter he can work through, derived from the data so it shrinks as he fixes
 * them. The second is a food whose own three facts do not multiply out, shown and never reconciled:
 * the app cannot know which of them is wrong, and picking one would be exactly the silent guess this
 * whole model exists to avoid.
 */
@Composable
fun FoodsScreen(
    state: FoodsUiState,
    onSearch: (String) -> Unit,
    onShowOnlyPortions: (Boolean) -> Unit,
    onShowHidden: (Boolean) -> Unit,
    onEdit: (Long) -> Unit,
    onSetForm: (FoodForm) -> Unit,
    onSave: () -> Unit,
    onCancelEditing: () -> Unit,
    onHide: (Long) -> Unit,
    onUnhide: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onConfirmDeleting: () -> Unit,
    onCancelDeleting: () -> Unit,
    onBeginMerging: (Long) -> Unit,
    onMergeInto: (Long) -> Unit,
    onConfirmMerging: () -> Unit,
    onCancelMerging: () -> Unit,
    onDismissRefusal: () -> Unit,
    onBeginChoosing: (Long) -> Unit,
    onToggleChosen: (Long) -> Unit,
    onClearChoosing: () -> Unit,
    onMakeMeal: () -> Unit,
    onJoinChosen: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(
        title = stringResource(R.string.foods_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        FoodsContent(
            state = state,
            onSearch = onSearch,
            onShowOnlyPortions = onShowOnlyPortions,
            onShowHidden = onShowHidden,
            onEdit = onEdit,
            onSetForm = onSetForm,
            onSave = onSave,
            onCancelEditing = onCancelEditing,
            onHide = onHide,
            onUnhide = onUnhide,
            onDelete = onDelete,
            onConfirmDeleting = onConfirmDeleting,
            onCancelDeleting = onCancelDeleting,
            onBeginMerging = onBeginMerging,
            onMergeInto = onMergeInto,
            onConfirmMerging = onConfirmMerging,
            onCancelMerging = onCancelMerging,
            onDismissRefusal = onDismissRefusal,
            onBeginChoosing = onBeginChoosing,
            onToggleChosen = onToggleChosen,
            onClearChoosing = onClearChoosing,
            onMakeMeal = onMakeMeal,
            onJoinChosen = onJoinChosen,
        )
    }
}

/**
 * The food list itself, without the screen around it.
 *
 * Split out of [FoodsScreen] so the same list can be drawn on the manager's "My foods" tab, which
 * brings its own title bar and its own pair of tabs. Nothing about what is drawn changed in the
 * splitting — the screen is this and a title bar, exactly as it was — and the render tests passing
 * untouched is what proves it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoodsContent(
    state: FoodsUiState,
    onSearch: (String) -> Unit,
    onShowOnlyPortions: (Boolean) -> Unit,
    onShowHidden: (Boolean) -> Unit,
    onEdit: (Long) -> Unit,
    onSetForm: (FoodForm) -> Unit,
    onSave: () -> Unit,
    onCancelEditing: () -> Unit,
    onHide: (Long) -> Unit,
    onUnhide: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onConfirmDeleting: () -> Unit,
    onCancelDeleting: () -> Unit,
    onBeginMerging: (Long) -> Unit,
    onMergeInto: (Long) -> Unit,
    onConfirmMerging: () -> Unit,
    onCancelMerging: () -> Unit,
    onDismissRefusal: () -> Unit,
    onBeginChoosing: (Long) -> Unit,
    onToggleChosen: (Long) -> Unit,
    onClearChoosing: () -> Unit,
    onMakeMeal: () -> Unit,
    onJoinChosen: () -> Unit,
) {
    if (state.nothingAtAll) {
        Text(
            text = stringResource(R.string.foods_none),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    // A refusal is not a failure: it names what stands in the way so he can go and deal with it.
    // An action that threw is a failure, and says so in the same place (ActionRefused).
    //
    // While a food is open the sentence is drawn in its editor instead, beside the Save or Delete
    // that produced it: the editor can be far down the list, and a sentence at the top would be off
    // screen from there, making the tap look dead. Only when the open food is actually drawn — a
    // search that has since hidden it leaves nowhere nearer than here.
    val sentence = state.refusal ?: state.failed?.let { stringResource(it.sentence) }
    val saidInTheEditor = state.editing != null && state.foods.any { it.id == state.editing.foodId }
    if (!saidInTheEditor) {
        sentence?.let { SlotSentence(it, onDismissRefusal) }
    }

    // Two shapes, because there are two ways in. From a food's own editor only the survivor is
    // known and the other one is still to be picked off the list. From two ticked in the list both
    // are known already, so the list is not a picker at all: it names the pair, says which one
    // survives, and asks. A merge cannot be undone, so the one thing neither shape may do is leave a
    // stray tap on a row able to join the wrong two. Once the other one is known — picked or
    // ticked — both ways in ask the same question here, in the same words (D36).
    //
    // Brought into view whenever it changes, because it is drawn at the top of the list and the
    // duplicate he picked may be far down it: left where it is, the question would be off screen,
    // the pick would look like it did nothing, and his next tap on a row would abandon a join he
    // never saw asked about. Everything that does the bringing sits INSIDE this `let`: outside it,
    // an empty column would add a gap to every ordinary Foods screen, and an effect keyed on a null
    // join would scroll to the top whenever a join ended — away from a food he had just opened.
    state.merging?.let { merging ->
        val requester = remember { BringIntoViewRequester() }
        Column(
            modifier = Modifier.bringIntoViewRequester(requester),
            // What the screen's own column spaces its children by, so wrapping them changes nothing.
            verticalArrangement = Arrangement.spacedBy(Spacing.Section),
        ) {
            if (merging.losing != null) {
                Text(
                    text = stringResource(
                        R.string.foods_merge_both,
                        merging.keeping.name,
                        merging.losing.name,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    Button(onClick = onConfirmMerging) {
                        Text(stringResource(R.string.foods_merge_do_it))
                    }
                    TextButton(onClick = onCancelMerging) {
                        Text(stringResource(R.string.foods_merge_cancel))
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.foods_merge_pick, merging.keeping.name),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onCancelMerging) {
                    Text(stringResource(R.string.foods_merge_cancel))
                }
            }
        }
        LaunchedEffect(merging) {
            // bringIntoView() silently does nothing until this block has been placed (foundation
            // 1.6.1 returns early without layout coordinates), and an effect can start before the
            // first layout. One frame is enough.
            withFrameNanos { }
            requester.bringIntoView()
        }
    }

    // Above the search rather than under the list, and deliberately so: what is chosen outlives a
    // search, so the count and the two actions have to outlive one too. Under the list they would
    // vanish the moment a search matched nothing, taking the salad he was collecting with them.
    // Design §3.4, as amended during the build: the count is a line in the body, above the search.
    // It first read "a count replaces the screen's title", which the title bar cannot do — that bar
    // belongs to the manager that draws this list, not to the list, so a count written into it would
    // say "3 chosen" over the meals tab as well. The design file carries the correction and its
    // reason; this is the code it agrees with.
    if (state.choosing) {
        Chosen(
            state = state,
            onClear = onClearChoosing,
            onMakeMeal = onMakeMeal,
            onJoin = onJoinChosen,
        )
    }

    OutlinedTextField(
        value = state.query,
        onValueChange = onSearch,
        label = { Text(stringResource(R.string.foods_search)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        // Derived from the data, so the number falls as he fixes them. A count written down
        // once by the conversion would have gone stale the first time he corrected one.
        if (state.onlyAPortionCount > 0 || state.onlyPortions) {
            FilterChip(
                selected = state.onlyPortions,
                onClick = { onShowOnlyPortions(!state.onlyPortions) },
                label = {
                    Text(
                        stringResource(
                            R.string.foods_only_portions,
                            state.onlyAPortionCount.toString(),
                        ),
                    )
                },
            )
        }
        FilterChip(
            selected = state.showHidden,
            onClick = { onShowHidden(!state.showHidden) },
            label = { Text(stringResource(R.string.foods_show_hidden)) },
        )
    }

    // Drawn BELOW the chip row and never above it, because the sentence's only job is to point at
    // the chip. Said where the list would have been, in place of it.
    if (state.everythingHidden) {
        Text(
            text = stringResource(R.string.foods_all_hidden),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    if (state.nothingLeftToFix) {
        Text(
            text = stringResource(R.string.foods_nothing_left_to_fix),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    if (state.searchedAndFoundNothing) {
        Text(
            text = stringResource(R.string.foods_no_match, state.query.trim()),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    // A gesture nothing on screen mentions is a gesture nobody finds. Said once, quietly, and only
    // while there is nothing to say about a choice already made.
    if (!state.choosing && state.editing == null && state.merging == null) {
        Text(
            text = stringResource(R.string.foods_hold_to_choose),
            style = MaterialTheme.typography.bodySmall,
            color = MetaSelfInk.two,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        state.foods.forEach { food ->
            val editing = state.editing?.takeIf { it.foodId == food.id }

            when {
                editing != null -> Editor(
                    editing = editing,
                    food = food,
                    onSetForm = onSetForm,
                    onSave = onSave,
                    onCancel = onCancelEditing,
                    onHide = { onHide(food.id) },
                    onUnhide = { onUnhide(food.id) },
                    onDelete = { onDelete(food.id) },
                    onBeginMerging = { onBeginMerging(food.id) },
                    deleting = state.deleting?.takeIf { it.food.id == food.id },
                    onConfirmDelete = onConfirmDeleting,
                    onKeep = onCancelDeleting,
                    sentence = sentence,
                    onDismissSentence = onDismissRefusal,
                )

                // Only while the other food is still to be picked. With both already ticked the
                // question is "shall I?", not "which?", and every row being a target would mean one
                // mistaken tap joining the wrong pair irreversibly.
                state.merging != null && state.merging.losing == null -> MergeCandidate(
                    food = food,
                    isTheOneKept = food.id == state.merging.keeping.id,
                    onPick = { onMergeInto(food.id) },
                )

                else -> FoodRow(
                    food = food,
                    chosen = food.id in state.chosen,
                    choosing = state.choosing,
                    onOpen = { onEdit(food.id) },
                    onBeginChoosing = { onBeginChoosing(food.id) },
                    onToggleChosen = { onToggleChosen(food.id) },
                )
            }
            HorizontalDivider()
        }
    }
}

/**
 * The count of what is chosen, and the two things that can be done with it.
 *
 * The meal action carries the number — "Make a meal from these 3" — because "Make a meal" on its own
 * does not say whether the three he thinks are ticked are the three that are. The join carries no
 * number: it is offered at two and at no other size, so "these two" is the whole of it, and the
 * count is on the line above either way.
 *
 * A plural resource rather than a test for 1 in code (D37): the meal is offered here only from two,
 * so the one-form is never drawn on this screen, but the resource is what the translation (#9)
 * works from, and Hebrew has more forms than English.
 */
@Composable
private fun Chosen(
    state: FoodsUiState,
    onClear: () -> Unit,
    onMakeMeal: () -> Unit,
    onJoin: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.foods_chosen, state.chosen.size.toString()),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.foods_clear_choosing))
            }
        }
        // At one, the hint about holding has been acted on and is gone, and nothing can be done with
        // one food alone — so without this the screen has no next step on it (public issue #12).
        if (state.chosen.size == 1) {
            Text(
                text = stringResource(R.string.foods_tap_to_add),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
        }
        if (state.canMakeAMeal) {
            Button(onClick = onMakeMeal, modifier = Modifier.fillMaxWidth()) {
                Text(
                    pluralStringResource(
                        R.plurals.foods_make_meal,
                        state.chosen.size,
                        state.chosen.size,
                    ),
                )
            }
        }
        // Merging is pairwise and stays pairwise, so this is offered at two and at no other size.
        if (state.canJoin) {
            TextButton(onClick = onJoin) {
                Text(stringResource(R.string.foods_join_two))
            }
        }
    }
}

/**
 * One food, closed: everything it is, at a glance.
 *
 * Holding it starts choosing; an ordinary tap still opens it to be corrected. That way round because
 * a tap already means "put this right", and choosing that began on a tap would turn every attempt to
 * fix a wrong number into the start of a meal. While choosing, the tap ticks instead.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FoodRow(
    food: Food,
    chosen: Boolean,
    choosing: Boolean,
    onOpen: () -> Unit,
    onBeginChoosing: () -> Unit,
    onToggleChosen: () -> Unit,
) {
    // A firm press when holding starts choosing, a light tick when a tap ticks or unticks (#16).
    // An ordinary tap opens the food and is felt as nothing of its own.
    val haptics = LocalHapticFeedback.current
    // The row gives a little under the finger (#16), unless animations are removed.
    val press = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .givesUnderPress(press)
            .combinedClickable(
                interactionSource = press,
                indication = LocalIndication.current,
                onClick = {
                    if (choosing) {
                        haptics.performHapticFeedback(Feel.Tick)
                        onToggleChosen()
                    } else {
                        onOpen()
                    }
                },
                onLongClick = {
                    haptics.performHapticFeedback(Feel.Thump)
                    onBeginChoosing()
                },
            )
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (choosing) {
            // Named for a screen reader: a tick is the whole of what this says, and nothing else on
            // the row says whether the food is in the choice or not.
            val tick = stringResource(
                if (chosen) R.string.foods_is_chosen else R.string.foods_not_chosen,
            )
            Checkbox(
                checked = chosen,
                // The row is the target. Two hit areas doing one thing is two things to get wrong.
                onCheckedChange = null,
                modifier = Modifier.semantics { contentDescription = tick },
            )
        }
        FoodSummary(food = food, modifier = Modifier.weight(1f))
    }
}

/**
 * Everything a closed food says about itself: its name, its brand, the other names it answers to,
 * what it knows, and anything wrong with that. One copy, drawn by the ordinary row and by the row he
 * picks a duplicate from — the brand and the numbers are what tell two duplicates apart, so the one
 * irreversible decision on this screen must not be made on a bare name.
 */
@Composable
private fun FoodSummary(
    food: Food,
    modifier: Modifier = Modifier,
    nameColor: Color = Color.Unspecified,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
    ) {
        Text(text = food.name, style = MaterialTheme.typography.bodyLarge, color = nameColor)

        FoodWording.brand(food)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // What merging two duplicates leaves behind, and the reason it is worth doing: the other
        // name still finds this food.
        FoodWording.alsoKnownAs(food)?.let {
            Text(
                text = stringResource(R.string.foods_also_known_as, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FoodWording.whatItKnows(food).forEach {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FoodWording.disagreement(food)?.let {
            // Ink, not red (D48): red is a refusal or a field that is wrong, and this is neither —
            // a fact about the record, set one step above the captions around it.
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (food.hidden) {
            Text(
                text = stringResource(R.string.foods_hidden),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One food while a merge is being chosen: the same row, but it picks rather than opens. */
@Composable
private fun MergeCandidate(food: Food, isTheOneKept: Boolean, onPick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (isTheOneKept) it else it.clickable(onClick = onPick) }
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
    ) {
        FoodSummary(
            food = food,
            nameColor = if (isTheOneKept) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        if (isTheOneKept) {
            Text(
                text = stringResource(R.string.foods_merge_this_one_stays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One food, open: everything it is, as fields.
 *
 * **A group left empty means the food does not know that**, and clearing one is how he says so. The
 * form allows it because the three facts are optional by design — a form insisting on all of them
 * would quietly reintroduce the idea that a food has a kind, which is exactly what this model
 * removed.
 *
 * Where each stored number came from is shown beside its group, because a number he is about to
 * overwrite is worth knowing the provenance of: a figure off a packet deserves more hesitation than
 * one a model guessed.
 */
@Composable
private fun Editor(
    editing: Editing,
    food: Food,
    onSetForm: (FoodForm) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    onDelete: () -> Unit,
    onBeginMerging: () -> Unit,
    deleting: Deleting?,
    onConfirmDelete: () -> Unit,
    onKeep: () -> Unit,
    /** The screen's refusal or failure, drawn here rather than at the top while this is open. */
    sentence: String?,
    onDismissSentence: () -> Unit,
) {
    val form = editing.form
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        // D48's grouping. Every field here was 4 dp from every other, so the per-100 g four and the
        // per-portion four — the same four labels — ran together with nothing between them. Each
        // group is now one block, tight inside, and the blocks are a section apart.
        verticalArrangement = Arrangement.spacedBy(Spacing.Section),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Field(
                value = form.name,
                onValueChange = { onSetForm(form.copy(name = it)) },
                label = stringResource(R.string.foods_field_name),
                error = editing.errorFor(FoodField.NAME),
            )
            Field(
                value = form.brand,
                onValueChange = { onSetForm(form.copy(brand = it)) },
                label = stringResource(R.string.foods_field_brand),
                error = null,
            )
            // Putting a real brand on a food changes what it is, so the next plain one starts a new
            // entry. Correct, and it will look like a duplicate coming back unless it was expected.
            Text(
                text = stringResource(R.string.foods_brand_splits),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
            // A food's facts are kept as typed, decimals included. Said once above both groups, so the
            // whole-grams rule of Type the numbers is not taken for this form's and a packet's 0.5 g is
            // not rounded by hand before it is typed (D38).
            Text(
                text = stringResource(R.string.food_facts_decimals_kept),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            FactHeading(
                title = stringResource(R.string.foods_group_per_100g),
                origin = food.facts.per100g
                    ?.let { FoodWording.origin(it.provenance.source, it.provenance.confidence) },
            )
            Field(form.kcalPer100g, { onSetForm(form.copy(kcalPer100g = it)) }, stringResource(R.string.foods_field_kcal), editing.errorFor(FoodField.PER_100G), numeric = true)
            Field(form.proteinPer100g, { onSetForm(form.copy(proteinPer100g = it)) }, stringResource(R.string.foods_field_protein), null, numeric = true)
            Field(form.carbsPer100g, { onSetForm(form.copy(carbsPer100g = it)) }, stringResource(R.string.foods_field_carbs), null, numeric = true)
            Field(form.fatPer100g, { onSetForm(form.copy(fatPer100g = it)) }, stringResource(R.string.foods_field_fat), null, numeric = true)
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            FactHeading(
                title = stringResource(R.string.foods_group_per_unit),
                origin = food.facts.perUnit
                    ?.let { FoodWording.origin(it.provenance.source, it.provenance.confidence) },
            )
            Field(form.unitName, { onSetForm(form.copy(unitName = it)) }, stringResource(R.string.foods_field_unit), editing.errorFor(FoodField.UNIT_NAME))
            Field(form.kcalPerUnit, { onSetForm(form.copy(kcalPerUnit = it)) }, stringResource(R.string.foods_field_kcal), editing.errorFor(FoodField.PER_UNIT), numeric = true)
            Field(form.proteinPerUnit, { onSetForm(form.copy(proteinPerUnit = it)) }, stringResource(R.string.foods_field_protein), null, numeric = true)
            Field(form.carbsPerUnit, { onSetForm(form.copy(carbsPerUnit = it)) }, stringResource(R.string.foods_field_carbs), null, numeric = true)
            Field(form.fatPerUnit, { onSetForm(form.copy(fatPerUnit = it)) }, stringResource(R.string.foods_field_fat), null, numeric = true)
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            FactHeading(
                title = stringResource(R.string.foods_group_weight),
                origin = food.facts.gramsPerUnit
                    ?.let { FoodWording.origin(it.provenance.source, it.provenance.confidence) },
            )
            // Nothing works this out. It is the number that turns one way of counting into the other, so
            // a wrong one propagates into every future gram-counted log of this food.
            Text(
                text = stringResource(R.string.foods_weight_never_guessed),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
            Field(form.gramsPerUnit, { onSetForm(form.copy(gramsPerUnit = it)) }, stringResource(R.string.foods_field_weight), editing.errorFor(FoodField.WEIGHT), numeric = true)
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            editing.errorFor(FoodField.NOTHING_KNOWN)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Correcting fixes the food from now on. The days already logged keep the numbers they were
            // logged with, which is his own decision and worth restating where he is about to act on it.
            Text(
                text = stringResource(R.string.foods_correction_not_retroactive),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            // A Save refused or an action that threw, said directly above the buttons that did it.
            sentence?.let { SlotSentence(it, onDismissSentence) }

            // The question takes the buttons' place, so it is where his finger is and exactly one thing
            // on the editor says Delete (D36).
            if (deleting is Deleting.Asking) {
                AskBeforeDeleting(
                    name = deleting.food.name,
                    onDelete = onConfirmDelete,
                    onKeep = onKeep,
                )
            } else {
                // The refusal to delete is said here, directly above the buttons, and not in the slot at
                // the top of the list: Delete sits at the foot of this editor, the top of the list is off
                // screen from there, and a refusal he cannot see makes the tap look dead. The buttons
                // stay, because the sentence tells him to hide it instead and Hide must be in reach.
                if (deleting is Deleting.Refused) {
                    Text(
                        text = deleting.sentence,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    Button(onClick = onSave) { Text(stringResource(R.string.foods_save)) }
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.foods_cancel)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    TextButton(onClick = onBeginMerging) { Text(stringResource(R.string.foods_merge)) }
                    if (food.hidden) {
                        TextButton(onClick = onUnhide) { Text(stringResource(R.string.foods_unhide)) }
                    } else {
                        TextButton(onClick = onHide) { Text(stringResource(R.string.foods_hide)) }
                    }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.foods_delete)) }
                }
            }
            // Hiding keeps the history pointing here, so every past day still shows this food's current
            // name. Deleting lets those days fall back to whatever was typed on the day.
            Text(
                text = stringResource(R.string.foods_hide_or_delete),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
        }
    }
}

/** The screen's one refusal or failure, with the button that takes it down. */
@Composable
private fun SlotSentence(sentence: String, onDismiss: () -> Unit) {
    Text(
        text = sentence,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
    TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.foods_refusal_dismiss))
    }
}

@Composable
private fun FactHeading(title: String, origin: String?) {
    Text(text = title, style = MaterialTheme.typography.titleSmall)
    origin?.let {
        Text(
            text = stringResource(R.string.foods_origin, it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One field of the food editor.
 *
 * [numeric] picks the keyboard. Decimal rather than Number, because a food's facts are kept exactly
 * as typed, decimals included (D38) — a keyboard with no point on it would make the packet's 0.5 g
 * untypable on the one screen that promises to keep it.
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
