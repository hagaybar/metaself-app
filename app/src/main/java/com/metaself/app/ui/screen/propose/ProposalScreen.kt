package com.metaself.app.ui.screen.propose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.amount.Per
import com.metaself.app.domain.amount.Worth
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.portion.Portions
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.day.DayTotalsWording
import com.metaself.app.ui.food.ModelAnswer
import com.metaself.app.ui.food.saidAs
import com.metaself.app.ui.portion.AmountBox
import com.metaself.app.ui.portion.unitWord
import com.metaself.app.ui.propose.ProposalWording
import com.metaself.app.ui.screen.day.MealNamingSheet
import com.metaself.app.ui.theme.Spacing
import java.util.Locale

/**
 * Describe a meal; correct what comes back; save it.
 *
 * One row per component, always — the model's answer is never shown as a single total, because a
 * total gives nothing to notice when a third of the meal is missing from it.
 *
 * Every failure route ends at the manual editor with the typed words carried over. Decision D8: the
 * failure mode of a habit app is the day it refuses to work.
 *
 * **What was just described can be kept as a meal he names, here and now** (D46, issue #24). The
 * offer saves first and names afterwards, and the naming is the day's own sheet doing the day's own
 * work: [chosenRows], [isToday] and [refusal] are the day's answers about the rows it has just
 * written, and [onNameMeal] is the same act as naming a meal ticked on the day by hand. A meal of
 * one is a food already, so the offer is drawn only over an answer of more than one row.
 */
@Composable
fun ProposalScreen(
    state: ProposalUiState,
    description: String,
    onDescribe: (String) -> Unit,
    onSetAmount: (Int, String) -> Unit,
    onStep: (Int, Int) -> Unit,
    onOpenWorth: (Int) -> Unit,
    onSetWorthBox: (Int, WorthFigure, String) -> Unit,
    onCloseWorth: (Int) -> Unit,
    onUseYourFood: (Int) -> Unit,
    onUseEstimate: (Int) -> Unit,
    onCountInFoodUnit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onTellItMore: (String) -> Unit,
    onSave: () -> Unit,
    onTypeItMyself: () -> Unit,
    onAddKey: () -> Unit,
    onCancel: () -> Unit,
    // Deliberately without defaults, all seven: the sheet is reachable from one place only, and a
    // default would let that one place forget a piece of the wiring and fail in silence on the
    // phone instead of at the compiler.
    onKeepAsMeal: () -> Unit,
    onNameMeal: (String) -> Unit,
    onGiveUpNaming: () -> Unit,
    onKeepingDone: () -> Unit,
    chosenRows: List<FoodItem>,
    isToday: Boolean,
    refusal: String?,
    modifier: Modifier = Modifier,
) {
    // Saved rather than merely remembered, so that stepping out to settings for a key and coming
    // back keeps what he typed since the last ask as well as the ask itself, which the view model
    // holds (public issue #11). The back stack saves this screen's saveable state while another is
    // on top.
    var typed by rememberSaveable(description) { mutableStateOf(description) }
    var extra by remember { mutableStateOf("") }

    // That the offer was pressed is remembered here, beside the name being typed, for the reason
    // the day remembers the same two things (`DayScreenContent`): both exist only while the sheet is
    // open and nothing else reads them. The rows are the day's, and arrive after the write.
    //
    // Saved rather than merely remembered, because the rows are already on the day by the time any
    // of this matters: a rotation with the sheet open would otherwise drop the typed name AND the
    // record that naming had begun, leaving him on an accept screen with no answer on it and no way
    // back to the choice, since returning to the day by hand clears it.
    var taken by rememberSaveable { mutableStateOf(false) }
    var mealName by rememberSaveable { mutableStateOf("") }
    var opened by rememberSaveable { mutableStateOf(false) }

    val keeping = keepingAsMeal(taken, chosenRows, isToday, refusal)

    // Open once, leave once, and only after it has been open — the rule itself is `namingSheetStep`,
    // where a test can read it. A refusal leaves the choice standing, which is exactly why it leaves
    // the sheet standing. Once it has closed, this route is done with: the rows are logged, and the
    // caller takes him back to the day.
    LaunchedEffect(keeping != null) {
        when (namingSheetStep(hasSomethingToName = keeping != null, hasBeenOpen = opened)) {
            NamingSheetStep.OPEN -> opened = true
            NamingSheetStep.LEAVE -> {
                opened = false
                taken = false
                mealName = ""
                onKeepingDone()
            }
            NamingSheetStep.WAIT -> Unit
        }
    }

    MetaSelfScreen(
        title = stringResource(R.string.propose_title),
        modifier = modifier,
        onBack = onCancel,
    ) {
        when (state) {
            is ProposalUiState.Describing -> {
                // D48's grouping: the description, anything said about it, and the way to fix that
                // are one block; the two ways on are another.
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text(stringResource(R.string.propose_field)) },
                        supportingText = { Text(stringResource(R.string.propose_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    val failure = state.failure ?: state.refused?.let { stringResource(it.sentence) }
                    failure?.let { sentence ->
                        Text(
                            text = sentence,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    // An answer that arrived and could not be used can be read, to see why (#1).
                    state.answer?.let { ModelAnswer(it) }

                    // Said AND offered: the sentence above names settings, and this is the way there,
                    // straight to the key (public issue #11).
                    if (state.needsKey) {
                        OutlinedButton(onClick = onAddKey, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.propose_add_key))
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                    Button(
                        onClick = { onDescribe(typed) },
                        enabled = typed.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.propose_ask)) }

                    TextButton(onClick = onTypeItMyself, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.propose_manual))
                    }
                }
            }

            // The conversation's stages are drawn by D58's next step; until then, nothing.
            is ProposalUiState.Offer, is ProposalUiState.Asking, is ProposalUiState.ConversationFailed -> Unit

            is ProposalUiState.Waiting -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.propose_waiting),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            is ProposalUiState.Proposed -> {
                // An item the answer held and this app could not use is said, not silently
                // missing: a row that is not there is the omission a list exists to show.
                // And why can be seen: the answer as it came, when an item was dropped (#1). One
                // block with the sentence, as the failure and its answer are on the describe side.
                if (state.dropped.isNotEmpty() || state.answer != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                        if (state.dropped.isNotEmpty()) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.propose_dropped,
                                    state.dropped.size,
                                    state.dropped.size,
                                    state.dropped.joinToString(", "),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        state.answer?.let { ModelAnswer(it) }
                    }
                }

                // The rows are one list, a related step apart, not a section each.
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                    state.rows.forEachIndexed { index, row ->
                        ProposedRow(
                            row = row,
                            onSetAmount = { text -> onSetAmount(index, text) },
                            onStep = { by -> onStep(index, by) },
                            onOpenWorth = { onOpenWorth(index) },
                            onSetWorthBox = { figure, text -> onSetWorthBox(index, figure, text) },
                            onCloseWorth = { onCloseWorth(index) },
                            onUseYourFood = { onUseYourFood(index) },
                            onUseEstimate = { onUseEstimate(index) },
                            onCountInFoodUnit = { onCountInFoodUnit(index) },
                            onRemove = { onRemove(index) },
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    Text(
                        text = stringResource(
                            R.string.propose_total,
                            String.format(Locale.US, "%,d", state.totalKcal),
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )

                    state.note?.let { note ->
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // The offer was taken and the day has not answered yet: the answer stays on screen
                // until the rows are written, so a second tap in that moment would log it twice.
                val keepInFlight = taken && keeping == null && refusal == null

                // A row that cannot be logged holds both ways of saving, and is named above them
                // (D53 §6) — a Save that is simply off, with the reason three rows up, reads as broken.
                val blocked = state.blockedBy?.let { state.rows[it] }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                    blocked?.let { row ->
                        // Worded by what holds it: a refused worth box, or an amount. "Say how
                        // much" over a row whose amount is fine sends him to the wrong box.
                        val why = if (row.editingWorth?.refused == true) {
                            R.string.propose_blocked_worth
                        } else {
                            R.string.propose_blocked
                        }
                        Text(
                            text = stringResource(why, row.item.name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Button(
                        onClick = onSave,
                        enabled = !keepInFlight && blocked == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.propose_save))
                    }

                    // A meal of one is a food already, and there is a way of keeping one of those. The
                    // rule is read off the rows being drawn, so removing a row until one is left takes
                    // the offer away with it.
                    if (state.rows.size > 1) {
                        Button(
                            onClick = {
                                taken = true
                                onKeepAsMeal()
                            },
                            enabled = !keepInFlight && blocked == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.propose_keep_as_meal)) }
                    }

                    // The offer was taken and the write threw, so the sheet the sentence normally sits
                    // in never opened. Said here instead, beside the answer that is still there to try
                    // again — the answer is let go of only once the rows are on the day.
                    if (taken && keeping == null && refusal != null) {
                        Text(
                            text = refusal,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                HorizontalDivider()

                // The one case a typed amount cannot fix: the same bowl, cooked richer.
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    OutlinedTextField(
                        value = extra,
                        onValueChange = { extra = it },
                        label = { Text(stringResource(R.string.propose_tell_more_field)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = {
                            onTellItMore(extra)
                            extra = ""
                        },
                        enabled = extra.isNotBlank(),
                    ) { Text(stringResource(R.string.propose_tell_more_send)) }
                }
            }
        }
    }

    // Drawn outside the `when`, because the answer it came from is gone by then: accepting starts
    // the screen over, and the rows it is naming are the day's now, not the model's. It is the
    // day's own sheet — the same composable, the same words, the same refusal — so there is one way
    // of naming a meal and not two (D46, issue #24).
    keeping?.let { sheet ->
        MealNamingSheet(
            items = sheet.rows,
            isToday = sheet.isToday,
            name = mealName,
            refusal = sheet.refusal,
            onNameChange = { mealName = it },
            onConfirm = { onNameMeal(mealName) },
            onCancel = onGiveUpNaming,
        )
    }
}

/**
 * One item: its name and detail, how much (typed), what it is worth, what that makes, where the
 * figures came from, and Remove (D53 §6). The amount and the worth are separate lines because they
 * are separate things; the total is the one derived from the other two.
 */
@Composable
private fun ProposedRow(
    row: ProposalRow,
    onSetAmount: (String) -> Unit,
    onStep: (Int) -> Unit,
    onOpenWorth: () -> Unit,
    onSetWorthBox: (WorthFigure, String) -> Unit,
    onCloseWorth: () -> Unit,
    onUseYourFood: () -> Unit,
    onUseEstimate: () -> Unit,
    onCountInFoodUnit: () -> Unit,
    onRemove: () -> Unit,
) {
    val item = row.item
    // Only a piece is stepped; grams, millilitres and every other measured unit have only the box.
    val counted = !Portions.isMass(item.unit)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
    ) {
        Text(text = item.name, style = MaterialTheme.typography.bodyLarge)

        // What the model said about it beyond its name — the size of piece it assumed, in words.
        if (item.detail.isNotBlank()) {
            Text(
                text = item.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The unit is not edited on its own: a different unit needs a different worth (D53 §1).
        // Only the app's own "portion" takes a plural (D37), and only grams are said as grams: a
        // ceiling of millilitres or of pieces names no unit.
        AmountBox(
            text = item.amountText,
            unitWords = unitWord(item.amountOrNull, item.unit),
            counted = counted,
            tooMuch = item.amountTooMuch,
            most = item.most,
            inGrams = Portions.isGrams(item.unit),
            millilitres = item.unit.trim().takeIf { Portions.isMillilitres(item.unit) },
            onText = onSetAmount,
            onStep = onStep,
            of = item.name,
        )

        item.rateLine?.let { rate ->
            val basis = when (rate.per) {
                Per.HUNDRED -> stringResource(R.string.propose_per_100, item.unit)
                Per.ONE -> stringResource(R.string.propose_per_one, item.unit)
            }
            Text(
                text = "$basis: ${ProposalWording.worthFigures(rate)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val boxes = row.editingWorth
        if (boxes == null) {
            if (item.rateLine != null) {
                val label = stringResource(R.string.propose_change_worth)
                Small(label, onOpenWorth, said = stringResource(R.string.said_for, label, item.name))
            }
        } else {
            WorthBoxesFields(boxes, item.name, item.unit, onSetWorthBox, onCloseWorth)
        }

        row.numbers?.let { numbers ->
            Text(
                text = ProposalWording.rowFigures(numbers),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        // Wording, not a source (D53 §3): above the origin line, never in its place, so a food whose
        // figure is the label's still says so, and one whose figure is an estimate its confidence.
        if (item.worth is Worth.YourFood) {
            Text(
                text = stringResource(R.string.propose_from_your_foods),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Decision D7: an estimate says how sure it was, and keeps saying it — whatever the amount.
        row.sourceRow?.let { DayTotalsWording.origin(it) }?.let { origin ->
            Text(
                text = origin,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        YourFoodLine(row, onUseYourFood, onUseEstimate, onCountInFoodUnit)

        // Every control a row repeats is also said with the row's name (public issue #3).
        Small(stringResource(R.string.propose_remove), onRemove, said = stringResource(R.string.said_remove, item.name))

        HorizontalDivider()
    }
}

/**
 * What his own foods say about this row, when they say anything (D53 §4, §5): the way back to the
 * estimate while it is on his food; one question naming his food while it is not; or, for his food
 * counted another way, the sentence saying so and the one honest switch.
 */
@Composable
private fun YourFoodLine(
    row: ProposalRow,
    onUseYourFood: () -> Unit,
    onUseEstimate: () -> Unit,
    onCountInFoodUnit: () -> Unit,
) {
    if (row.onYourFood) {
        val label = row.estimateKcal
            ?.let { stringResource(R.string.propose_use_estimate_kcal, it.toString()) }
            ?: stringResource(R.string.propose_use_estimate)
        Small(label, onUseEstimate, said = stringResource(R.string.said_for, label, row.item.name))
        return
    }
    row.yourFoodOffered?.let { food ->
        Small(stringResource(R.string.propose_use_your_food, food.name), onUseYourFood)
        return
    }
    row.switchOffered?.let { (food, way) ->
        // Grams in words; any other unit as the food names it, the app's own "portion" plural.
        val words = when (way.countedAs) {
            CountedAs.GRAMS -> stringResource(R.string.propose_grams)
            CountedAs.UNITS -> unitWord(null, way.unit)
        }
        Text(
            text = stringResource(R.string.propose_counted_in, food.name, words),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val countIn = stringResource(R.string.propose_count_it_in, words)
        Small(countIn, onCountInFoodUnit, said = stringResource(R.string.said_for, countIn, row.item.name))
    }
}

/**
 * The worth, typed over: four boxes with the food form's labels, and the food form's refusal with
 * this row's basis and ceilings under them (D53 §6). Done closes them, except while one is refused.
 *
 * Two rows' boxes can be open at once, so each box and Done are also said with the row's [name] and
 * basis — "Calories per 100 g, for Pizza" (public issue #3).
 */
@Composable
private fun WorthBoxesFields(
    boxes: WorthBoxes,
    name: String,
    unit: String,
    onSetWorthBox: (WorthFigure, String) -> Unit,
    onClose: () -> Unit,
) {
    val labels = listOf(
        R.string.foods_field_kcal,
        R.string.foods_field_protein,
        R.string.foods_field_carbs,
        R.string.foods_field_fat,
    )
    val basis = when (boxes.per) {
        Per.HUNDRED -> stringResource(R.string.propose_per_100, unit)
        Per.ONE -> stringResource(R.string.propose_per_one, unit)
    }
    WorthFigure.entries.forEach { figure ->
        val label = stringResource(labels[figure.ordinal])
        OutlinedTextField(
            value = boxes.typed[figure.ordinal],
            onValueChange = { onSetWorthBox(figure, it) },
            label = { Text(label) },
            isError = boxes.refused,
            singleLine = true,
            // Decimal: the worth keeps decimals, as a food's figures do (D38).
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
                .saidAs(stringResource(R.string.said_for, "$label $basis", name)),
        )
    }
    if (boxes.refused) {
        Text(
            text = ProposalWording.worthRefused(boxes.per, unit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    val done = stringResource(R.string.propose_worth_done)
    TextButton(
        onClick = onClose,
        enabled = !boxes.refused,
        modifier = Modifier.saidAs(stringResource(R.string.said_for, done, name)),
    ) {
        Text(done)
    }
}

@Composable
private fun Small(label: String, onClick: () -> Unit, said: String? = null) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.saidAs(said),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}
