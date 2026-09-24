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
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.food.Food
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.food.FoodWording
import com.metaself.app.ui.food.PartsLine
import com.metaself.app.ui.food.SlotSentence
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
 * Finding a food, choosing several, and joining two duplicates into one: what is about more than one
 * food (D55 §1).
 *
 * **A food opens its own page** rather than an editor in place of its row. The editor had grown
 * taller than the phone — with a review on screen the foods above and below were off screen anyway,
 * and every refusal had to be pulled down into it because the top of the list was out of sight from
 * its foot. Keeping it next to the foods around it helped with one job, deciding whether two entries
 * are the same thing, and that job — joining — stays here: a page's *Join with a duplicate* comes back
 * to this list to pick, with its search as he left it. Correcting one food's figures is not helped by
 * its neighbours; it deserves the whole screen (D50's reasoning, for the day's record).
 *
 * A join asks first, and the question is brought into view from the top of the list (D36).
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
    onOpen: (Long) -> Unit,
    onMergeInto: (Long) -> Unit,
    onConfirmMerging: () -> Unit,
    onCancelMerging: () -> Unit,
    onDismissRefusal: () -> Unit,
    onShowAgain: () -> Unit,
    onDismissHidden: () -> Unit,
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
            onOpen = onOpen,
            onMergeInto = onMergeInto,
            onConfirmMerging = onConfirmMerging,
            onCancelMerging = onCancelMerging,
            onDismissRefusal = onDismissRefusal,
            onShowAgain = onShowAgain,
            onDismissHidden = onDismissHidden,
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
    onOpen: (Long) -> Unit,
    onMergeInto: (Long) -> Unit,
    onConfirmMerging: () -> Unit,
    onCancelMerging: () -> Unit,
    onDismissRefusal: () -> Unit,
    onShowAgain: () -> Unit,
    onDismissHidden: () -> Unit,
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
    // An action that threw is a failure, and says so in the same place (ActionRefused). Always at
    // the top: nothing is said in an editor on the list any more (D55).
    val sentence = state.refusal ?: state.failed?.let { stringResource(it.sentence) }
    sentence?.let { SlotSentence(it, onDismissRefusal) }

    // A food its page just hid (D55 §6). A food that silently leaves a list it was just in reads as
    // deleted, so it is said, with the undo hiding has always had: Show again.
    state.hid?.let { hidden ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(
                text = stringResource(R.string.foods_hidden_notice, hidden.name),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                TextButton(onClick = onShowAgain) { Text(stringResource(R.string.foods_unhide)) }
                TextButton(onClick = onDismissHidden) {
                    Text(stringResource(R.string.foods_refusal_dismiss))
                }
            }
        }
    }

    // Two shapes, because there are two ways in. From a food's page only the survivor is known and
    // the other one is still to be picked off the list (D55 §5). From two ticked in the list both
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
    // join would scroll to the top whenever a join ended — away from the row he had just tapped.
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
    if (!state.choosing && state.merging == null) {
        Text(
            text = stringResource(R.string.foods_hold_to_choose),
            style = MaterialTheme.typography.bodySmall,
            color = MetaSelfInk.two,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        state.foods.forEach { food ->
            when {
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
                    onOpen = { onOpen(food.id) },
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
 * Holding it starts choosing; an ordinary tap opens its page (D55). That way round because a tap
 * already means "put this right", and choosing that began on a tap would turn every attempt to fix a
 * wrong number into the start of a meal. While choosing, the tap ticks instead.
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
 * Everything a row says about its food: its name, then one summary line — the brand or No brand,
 * the first way of counting it knows, and what one weighs — then the other names it answers to and
 * anything wrong with its figures (D55 §1). One copy, drawn by the ordinary row and by the row he
 * picks a duplicate from — the brand and the numbers are what tell two duplicates apart, so the one
 * irreversible decision on this screen must not be made on a bare name.
 *
 * The summary is the page's own heading line, from the same function, so the two cannot drift; and
 * separate texts, never one string, so a Hebrew brand and a Latin figure are not reordered into each
 * other (#9).
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

        PartsLine(
            parts = FoodWording.summary(food),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // What merging two duplicates leaves behind, and the reason it is worth doing: the other
        // name still finds this food.
        FoodWording.alsoKnownAs(food)?.let {
            Text(
                text = stringResource(R.string.foods_also_known_as, it),
                style = MaterialTheme.typography.bodySmall,
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
