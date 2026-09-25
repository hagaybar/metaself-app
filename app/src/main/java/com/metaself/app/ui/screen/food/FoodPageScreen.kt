package com.metaself.app.ui.screen.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.metaself.app.R
import com.metaself.app.domain.food.FactGroup
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodField
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.FoodUse
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.food.AskBeforeDeleting
import com.metaself.app.ui.food.CountInMillilitres
import com.metaself.app.ui.food.FactHeading
import com.metaself.app.ui.food.Field
import com.metaself.app.ui.food.figureSaid
import com.metaself.app.ui.food.FoodWording
import com.metaself.app.ui.food.PartsLine
import com.metaself.app.ui.food.perUnitHeading
import com.metaself.app.ui.food.ReviewActions
import com.metaself.app.ui.food.ReviewTheFigures
import com.metaself.app.ui.food.FormBox
import com.metaself.app.ui.food.PendingView
import com.metaself.app.ui.food.SlotSentence
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/**
 * One food's own page (D55).
 *
 * A food in *My foods* opens here rather than in place of its row, because correcting one food's
 * figures — the job a review serves — is not helped by the foods around it, and the editor had grown
 * taller than the phone: with a review on screen the foods above and below were off screen anyway.
 * The list keeps what is about more than one food: finding, choosing, and joining.
 *
 * From the top, in the drawing's order: the name and the list row's own summary line; the name and
 * brand boxes; **Review the figures** and its verdict (D54); the three groups, each headed by where
 * its figures came from; Save and Leave it alone; *Where it's used*; and Join, Hide and Delete, whose
 * question takes their place (D36).
 *
 * **A review's suggestions are drawn in the boxes** (D54 §12.6), each with its reason and its
 * **Back**; while any waits, **Accept changes and save** and **Cancel** stand in place of Save and
 * Leave it alone.
 *
 * **Nothing is saved but by Save**, or by Accept changes and save. Leave it alone is the same Back as
 * the arrow and the system gesture: all three leave the page and discard what was typed (D55 §2).
 *
 * The title bar says *My foods*, the list the food belongs to, whichever way he arrived.
 */
@Composable
fun FoodPageScreen(
    state: FoodPageUiState,
    onSetForm: (FoodForm) -> Unit,
    onSave: () -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    onDelete: () -> Unit,
    onConfirmDeleting: () -> Unit,
    onCancelDeleting: () -> Unit,
    onBeginJoining: () -> Unit,
    onDismissRefusal: () -> Unit,
    review: ReviewActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(
        title = stringResource(R.string.foods_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        val food = state.food
        val editing = state.editing
        // Before the first read, and for a food that is gone, there is no form to draw: a page
        // never draws a form for a food that is not there (§7). The page is closing in the second
        // case, so this is a frame at most.
        if (food != null && editing != null) {
            Page(
                food = food,
                editing = editing,
                state = state,
                onSetForm = onSetForm,
                onSave = onSave,
                onHide = onHide,
                onUnhide = onUnhide,
                onDelete = onDelete,
                onConfirmDeleting = onConfirmDeleting,
                onCancelDeleting = onCancelDeleting,
                onBeginJoining = onBeginJoining,
                onDismissRefusal = onDismissRefusal,
                review = review,
                onLeave = onBack,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Page(
    food: Food,
    editing: Editing,
    state: FoodPageUiState,
    onSetForm: (FoodForm) -> Unit,
    onSave: () -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    onDelete: () -> Unit,
    onConfirmDeleting: () -> Unit,
    onCancelDeleting: () -> Unit,
    onBeginJoining: () -> Unit,
    onDismissRefusal: () -> Unit,
    review: ReviewActions,
    onLeave: () -> Unit,
) {
    val form = editing.form
    val reviewing = editing.reviewing
    fun box(at: FormBox, value: String, onValueChange: (String) -> Unit, error: String? = null) =
        Box(value, onValueChange, error, reviewing.view(at)) { review.onPutBack(at) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        // D48's grouping: each group one block, tight inside, and the blocks a section apart.
        verticalArrangement = Arrangement.spacedBy(Spacing.Section),
    ) {
        // The food as STORED, not as half-typed: the heading says what Save would be changing.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(text = food.name, style = MaterialTheme.typography.headlineMedium)
            // The list row's line, from the same function, so the two cannot drift (§1).
            PartsLine(
                parts = FoodWording.summary(food),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FoodWording.alsoKnownAs(food)?.let {
                Text(
                    text = stringResource(R.string.foods_also_known_as, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FoodWording.disagreement(food)?.let {
                // Ink, not red (D48): a fact about the record, not a refusal.
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

        // Kept as boxes (D55 open question 1's default): renaming and branding happen nowhere else.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Field(
                value = form.name,
                onValueChange = { onSetForm(form.copy(name = it)) },
                label = stringResource(R.string.foods_field_name),
                error = editing.errorFor(FoodField.NAME),
                pending = reviewing.view(FormBox.NAME),
                onBack = { review.onPutBack(FormBox.NAME) },
            )
            Field(
                value = form.brand,
                onValueChange = { onSetForm(form.copy(brand = it)) },
                label = stringResource(R.string.foods_field_brand),
                error = null,
            )
            // Putting a real brand on a food changes what it is, so the next plain one starts a new
            // entry. Correct, and it will look like a duplicate coming back unless it was expected.
            Caption(stringResource(R.string.foods_brand_splits))
            // Said once above every group, so a packet's 0.5 g is not rounded by hand (D38).
            Caption(stringResource(R.string.food_facts_decimals_kept))
        }

        // Beneath the name and brand, above the groups it may suggest figures for (D54 §1).
        ReviewTheFigures(
            reviewing = editing.reviewing,
            offered = FoodField.NAME !in editing.errors,
            unitName = form.unitName,
            actions = review,
        )

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            FactHeading(
                title = stringResource(R.string.foods_group_per_100g),
                origin = food.facts.per100g
                    ?.let { FoodWording.origin(it.provenance.source, it.provenance.confidence) },
            )
            FourFigures(
                group = FactGroup.PER_100G,
                unitName = form.unitName,
                kcal = box(FormBox.KCAL_100G, form.kcalPer100g, { onSetForm(form.copy(kcalPer100g = it)) }, editing.errorFor(FoodField.PER_100G)),
                protein = box(FormBox.PROTEIN_100G, form.proteinPer100g, { onSetForm(form.copy(proteinPer100g = it)) }),
                carbs = box(FormBox.CARBS_100G, form.carbsPer100g, { onSetForm(form.copy(carbsPer100g = it)) }),
                fat = box(FormBox.FAT_100G, form.fatPer100g, { onSetForm(form.copy(fatPer100g = it)) }),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            FactHeading(
                // Per 100 ml when the unit box names the millilitre, live as it is typed (D56).
                title = perUnitHeading(form.unitName),
                origin = food.facts.perUnit
                    ?.let { FoodWording.origin(it.provenance.source, it.provenance.confidence) },
            )
            Field(
                value = form.unitName,
                onValueChange = { onSetForm(form.copy(unitName = it)) },
                label = stringResource(R.string.foods_field_unit),
                error = editing.errorFor(FoodField.UNIT_NAME),
                pending = reviewing.view(FormBox.UNIT),
                onBack = { review.onPutBack(FormBox.UNIT) },
            )
            // A unit the review names or renames changes what a saved meal's "2" means (§12.6).
            reviewing.pending[FormBox.UNIT]?.let { unit ->
                if (editing.unitMeals > 0 && unit.original.isNotBlank()) {
                    Caption(
                        pluralStringResource(
                            R.plurals.review_meals_count_units,
                            editing.unitMeals,
                            editing.unitMeals,
                            unit.original.trim(),
                            unit.suggested.trim(),
                        ),
                    )
                }
            }
            // One tap to ml, offered only while no per-one figure would change meaning (#5).
            CountInMillilitres(form, onSetForm)
            FourFigures(
                group = FactGroup.PER_UNIT,
                unitName = form.unitName,
                kcal = box(FormBox.KCAL_UNIT, form.kcalPerUnit, { onSetForm(form.copy(kcalPerUnit = it)) }, editing.errorFor(FoodField.PER_UNIT)),
                protein = box(FormBox.PROTEIN_UNIT, form.proteinPerUnit, { onSetForm(form.copy(proteinPerUnit = it)) }),
                carbs = box(FormBox.CARBS_UNIT, form.carbsPerUnit, { onSetForm(form.copy(carbsPerUnit = it)) }),
                fat = box(FormBox.FAT_UNIT, form.fatPerUnit, { onSetForm(form.copy(fatPerUnit = it)) }),
            )
        }

        // What one millilitre weighs is a density, which the app never assumes (D4), so a food counted
        // in ml is not asked it (D56). One the stored food already holds, or one typed into the box
        // before the unit became ml, is not deleted by the app: Save would keep it, so the box stays
        // in sight, with its value, its refusal and why, until he clears it. Nothing is saved or
        // refused unseen.
        val millilitres = form.perHundredMl
        if (!millilitres || food.facts.gramsPerUnit != null || form.gramsPerUnit.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                FactHeading(
                    title = stringResource(R.string.foods_group_weight),
                    origin = food.facts.gramsPerUnit
                        ?.let { FoodWording.origin(it.provenance.source, it.provenance.confidence) },
                )
                // Nothing works this out. It is the number that turns one way of counting into the
                // other, so a wrong one propagates into every future gram-counted log of this food.
                Caption(
                    stringResource(
                        if (millilitres) R.string.foods_weight_not_asked_ml else R.string.foods_weight_never_guessed,
                    ),
                )
                Field(
                    value = form.gramsPerUnit,
                    onValueChange = { onSetForm(form.copy(gramsPerUnit = it)) },
                    label = stringResource(R.string.foods_field_weight),
                    error = editing.errorFor(FoodField.WEIGHT),
                    numeric = true,
                    pending = reviewing.view(FormBox.WEIGHT),
                    onBack = { review.onPutBack(FormBox.WEIGHT) },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            editing.errorFor(FoodField.NOTHING_KNOWN)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            // Correcting fixes the food from now on; the days already logged keep their numbers.
            Caption(stringResource(R.string.foods_correction_not_retroactive))
            // A Save refused or an action that threw, said directly above the buttons (§2).
            (state.refusal ?: state.failed?.let { stringResource(it.sentence) })
                ?.let { SlotSentence(it, onDismissRefusal) }
            // While a suggestion waits in a box, the only two answers are to accept every one and
            // save, or to cancel back to the page as it was (D54 §12.6): Save and Leave it alone
            // are not drawn, so no tap saves a suggestion without saying accept.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                if (reviewing.hasPending) {
                    Button(onClick = review.onAcceptAndSave) { Text(stringResource(R.string.review_accept_and_save)) }
                    TextButton(onClick = review.onCancel) { Text(stringResource(R.string.review_cancel)) }
                } else {
                    Button(onClick = onSave) { Text(stringResource(R.string.foods_save)) }
                    TextButton(onClick = onLeave) { Text(stringResource(R.string.foods_cancel)) }
                }
            }
        }

        state.use?.let { WhereItIsUsed(it) }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            val deleting = state.deleting
            // The question takes the buttons' place, so it is where his finger is and exactly one
            // thing on the page says Delete (D36).
            if (deleting is Deleting.Asking) {
                AskBeforeDeleting(
                    name = deleting.food.name,
                    onDelete = onConfirmDeleting,
                    onKeep = onCancelDeleting,
                )
            } else {
                // Said here, directly above the buttons he pressed, never at the top of the page. The
                // buttons stay, because the sentence tells him to hide it instead.
                if (deleting is Deleting.Refused) {
                    Text(
                        text = deleting.sentence,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                // Wraps a button that does not fit onto the next line, whole. A Row squeezed the
                // last one into what was left, and its label broke mid-word on a narrow phone.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    TextButton(onClick = onBeginJoining) { Text(stringResource(R.string.foods_merge)) }
                    if (food.hidden) {
                        TextButton(onClick = onUnhide) { Text(stringResource(R.string.foods_unhide)) }
                    } else {
                        TextButton(onClick = onHide) { Text(stringResource(R.string.foods_hide)) }
                    }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.foods_delete)) }
                }
            }
            // Hiding keeps the history pointing here; deleting lets those days fall back to what was
            // typed on the day.
            Caption(stringResource(R.string.foods_hide_or_delete))
        }
    }
}

/**
 * *Where it's used* (§3): the exact number of entries that point at this food, and every saved meal
 * that holds it, by name. Plurals from resources (D37); the count grouped as every figure is.
 */
@Composable
private fun WhereItIsUsed(use: FoodUse) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(
            text = stringResource(R.string.food_page_used_heading),
            style = MaterialTheme.typography.titleSmall,
        )
        val logged = if (use.logged == 0) {
            stringResource(R.string.food_page_not_logged)
        } else {
            pluralStringResource(
                R.plurals.food_page_logged,
                use.logged,
                FoodWording.grouped(use.logged.toDouble()),
            )
        }
        val meals = use.savedMeals.takeIf { it.isNotEmpty() }?.let { names ->
            listOf(pluralStringResource(R.plurals.food_page_in_meals, names.size, names.size)) + names
        }
        PartsLine(
            parts = listOf(logged),
            style = MaterialTheme.typography.bodyMedium,
            then = meals,
        )
    }
}

/** One box of a row of figures: what it holds, what typing does, its error, and a suggestion in it. */
private data class Box(
    val value: String,
    val onValueChange: (String) -> Unit,
    val error: String?,
    val pending: PendingView?,
    val onBack: () -> Unit,
)

/**
 * A group's four figures, two by two, as D55's drawing lays them out — or one per row while a
 * suggestion waits in any of them, so each reason sits under its own box at a width it can be read
 * at (D54 §12.6). Each box is named for a screen reader by its label and its [group] (public issue
 * #3), since both groups draw the same four labels.
 */
@Composable
private fun FourFigures(group: FactGroup, unitName: String, kcal: Box, protein: Box, carbs: Box, fat: Box) {
    val boxes = listOf(
        kcal to R.string.foods_field_kcal,
        protein to R.string.foods_field_protein,
        carbs to R.string.foods_field_carbs,
        fat to R.string.foods_field_fat,
    )
    if (boxes.any { it.first.pending != null }) {
        boxes.forEach { (box, label) -> FigureBox(box, stringResource(label), group, unitName, Modifier) }
        return
    }
    boxes.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            pair.forEach { (box, label) ->
                FigureBox(box, stringResource(label), group, unitName, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FigureBox(box: Box, label: String, group: FactGroup, unitName: String, modifier: Modifier) {
    Field(
        value = box.value,
        onValueChange = box.onValueChange,
        label = label,
        error = box.error,
        modifier = modifier,
        numeric = true,
        said = figureSaid(label, group, unitName),
        pending = box.pending,
        onBack = box.onBack,
    )
}

@Composable
private fun Caption(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MetaSelfInk.two)
}

