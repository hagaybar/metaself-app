package com.metaself.app.ui.screen.propose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.portion.PortionControl
import com.metaself.app.domain.ai.PortionScale
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.day.DayTotalsWording
import com.metaself.app.ui.screen.day.MealNamingSheet
import com.metaself.app.ui.portion.portionWords
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
    onScale: (Int, PortionScale) -> Unit,
    onCount: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onTellItMore: (String) -> Unit,
    onSave: () -> Unit,
    onTypeItMyself: () -> Unit,
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
    var typed by remember(description) { mutableStateOf(description) }
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
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.propose_field)) },
                    supportingText = { Text(stringResource(R.string.propose_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                state.failure?.let { failure ->
                    Text(
                        text = failure,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Button(
                    onClick = { onDescribe(typed) },
                    enabled = typed.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.propose_ask)) }

                TextButton(onClick = onTypeItMyself, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.propose_manual))
                }
            }

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
                state.rows.forEachIndexed { index, row ->
                    ProposedRow(
                        row = row,
                        onScale = { scale -> onScale(index, scale) },
                        onCount = { howMany -> onCount(index, howMany) },
                        onRemove = { onRemove(index) },
                    )
                }

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

                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
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
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.propose_keep_as_meal)) }
                }

                HorizontalDivider()

                // The one case scaling cannot fix: the same bowl, cooked richer.
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

@Composable
private fun ProposedRow(
    row: ProposalRow,
    onScale: (PortionScale) -> Unit,
    onCount: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val item = row.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
    ) {
        Text(
            text = "${item.name} — ${item.kcal} kcal · P ${item.proteinG} · " +
                "C ${item.carbsG} · F ${item.fatG}",
            style = MaterialTheme.typography.bodyLarge,
        )

        // The model's own words, which go on the record as written; only the app's own
        // "2 portion" is drawn in the plural (D37).
        Text(
            text = portionWords(item.portionAmount, item.portionUnit, item.portion),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Decision D7: an estimate says how sure it was, and keeps saying it.
        DayTotalsWording.origin(item.toFoodItem())?.let { origin ->
            Text(
                text = origin,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (PortionScale.controlFor(row.asProposed)) {
                is PortionControl.Scale -> {
                    Small(stringResource(R.string.propose_less)) { onScale(PortionScale.LESS) }
                    Small(stringResource(R.string.propose_as_described)) {
                        onScale(PortionScale.AS_DESCRIBED)
                    }
                    Small(stringResource(R.string.propose_more)) { onScale(PortionScale.MORE) }
                }

                is PortionControl.Count -> {
                    val now = item.portionAmount.toInt().coerceAtLeast(1)
                    Small(stringResource(R.string.propose_count_fewer)) { onCount(now - 1) }
                    Text(text = "$now", style = MaterialTheme.typography.bodyLarge)
                    Small(stringResource(R.string.propose_count_more)) { onCount(now + 1) }
                }

                is PortionControl.None -> Unit
            }

            Small(stringResource(R.string.propose_remove), onRemove)
        }

        HorizontalDivider()
    }
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
