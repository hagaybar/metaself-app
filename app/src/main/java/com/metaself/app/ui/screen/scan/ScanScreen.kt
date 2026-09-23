package com.metaself.app.ui.screen.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.product.ProductField
import com.metaself.app.domain.product.ProductForm
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.food.AmountTooMuch
import com.metaself.app.ui.scan.ContributeWording
import com.metaself.app.ui.scan.ScanWording
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/**
 * Point the camera at a packet, say how much of it was eaten, log it.
 *
 * A miss is not a failure. Outside the few countries Open Food Facts covers densely the database
 * holds a few thousand products against a supermarket's tens of thousands, so not finding one is
 * the ordinary case rather than the exceptional one, and the way out is the meal description the
 * owner would have used anyway (D23).
 *
 * [failed] is something that threw rather than finishing — the phone's own table, not a miss — and
 * is said at the top, above whatever the screen is showing, until he takes it down.
 */
@Composable
fun ScanScreen(
    state: ScanUiState,
    hasCamera: Boolean,
    onBarcode: (String) -> Unit,
    onSetGrams: (String) -> Unit,
    onAddByHand: () -> Unit,
    onSetForm: (ProductForm) -> Unit,
    onSaveTyped: () -> Unit,
    onContribute: () -> Unit,
    onSave: () -> Unit,
    onScanAgain: () -> Unit,
    onDescribeInstead: () -> Unit,
    onBack: () -> Unit,
    // Required, as the Foods and meals tabs' are: a default would let a caller drop the way to take
    // the failure down, and while it stands the camera's reads are ignored.
    failed: ActionRefused?,
    onDismissFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(
        title = stringResource(R.string.scan_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        failed?.let { refused ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                Text(
                    text = stringResource(refused.sentence),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onDismissFailure) {
                    Text(stringResource(R.string.action_refused_dismiss))
                }
            }
        }

        when (state) {
            is ScanUiState.Looking -> {
                var typedBarcode by remember { mutableStateOf("") }

                if (hasCamera) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                        BarcodeCamera(
                            onBarcode = onBarcode,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 4f),
                        )
                        Text(
                            text = stringResource(R.string.scan_aim),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.scan_no_camera),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                HorizontalDivider()

                // Barcodes on tubes and bottles curve away from the camera and sometimes cannot be
                // read at all. Typing the digits is the way through that, and it is also how the
                // owner can reach the not-found screen on purpose without pointing a camera at
                // anything.
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    Text(
                        text = stringResource(R.string.scan_type_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MetaSelfInk.two,
                    )
                    OutlinedTextField(
                        value = typedBarcode,
                        onValueChange = { typed -> typedBarcode = typed.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.scan_type_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = { onBarcode(typedBarcode) },
                        enabled = typedBarcode.length >= MIN_BARCODE_DIGITS,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.scan_type_look_up)) }
                }

                TextButton(onClick = onDescribeInstead, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scan_describe_instead))
                }
            }

            is ScanUiState.Resolving -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.scan_looking_up),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            is ScanUiState.Found -> {
                // D48's grouping: what the packet is, what it is worth and where that came from are
                // one thing; the amount and what it comes to are another.
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    Text(text = state.product.label, style = MaterialTheme.typography.titleLarge)

                    Text(
                        text = ScanWording.per100g(state.product),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    // D4: the record says where its numbers came from, and so does the screen.
                    Text(
                        text = ScanWording.origin(state.fromThisPhone),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    OutlinedTextField(
                        value = state.grams,
                        onValueChange = onSetGrams,
                        label = { Text(stringResource(R.string.scan_grams)) },
                        isError = state.gramsTooMuch,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Only a number past the ceiling is named (D42); a blank or a zero leaves Log it
                    // off. The same line the other amount boxes draw, so the wording cannot drift
                    // from theirs.
                    AmountTooMuch(
                        tooMuch = state.gramsTooMuch,
                        most = state.most,
                        countedAs = CountedAs.GRAMS,
                    )

                    // The arithmetic in front of him, because he is agreeing to a number (D9).
                    ScanWording.forAmount(state.product, state.gramsOrNull)?.let { total ->
                        Text(text = total, style = MaterialTheme.typography.titleMedium)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    Button(
                        onClick = onSave,
                        enabled = state.gramsOrNull != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.scan_save)) }

                    // Log it pressed and nothing logged: the screen stays and says why (issue #32).
                    state.nothingLogged?.let { why ->
                        Text(
                            text = stringResource(
                                when (why) {
                                    NothingLogged.AMOUNT -> R.string.scan_nothing_logged_amount
                                    NothingLogged.PACKET -> R.string.scan_nothing_logged_packet
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                TextButton(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scan_again))
                }

                // Offered only for a product he has just typed in, and only with an account. A
                // button that always fails is worse than no button (D24).
                if (state.justAdded) {
                    HorizontalDivider()
                    if (state.canContribute) {
                        Text(
                            text = stringResource(R.string.scan_contribute_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MetaSelfInk.two,
                        )
                        TextButton(
                            onClick = onContribute,
                            enabled = !state.contributing,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.scan_contribute)) }
                    } else {
                        Text(
                            text = ContributeWording.NO_ACCOUNT,
                            style = MaterialTheme.typography.bodySmall,
                            color = MetaSelfInk.two,
                        )
                    }

                    state.contributionMessage?.let { message ->
                        Text(text = message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            is ScanUiState.Adding -> {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    Text(
                        text = stringResource(R.string.scan_add_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // First, before the label rules: it is why he is on this form rather than the found
                    // screen, and which box needs him (D40).
                    // The sentence already says what to copy from the packet. The blank form's "copy
                    // these from the package itself" would say it again above boxes the database has
                    // already filled, telling him to copy what is there — so it is one or the other.
                    if (state.databaseLacks.isNotEmpty()) {
                        Text(
                            text = databaseLacks(state.databaseLacks),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            text = ContributeWording.ONLY_THE_LABEL,
                            style = MaterialTheme.typography.bodySmall,
                            color = MetaSelfInk.two,
                        )
                    }
                    // A label's figures are stored as printed on the packet's record, 0.7 g included;
                    // said before the first box so they are not rounded by hand to match Type the
                    // numbers' rule. Its own sentence, not the food forms' "on this food": what is
                    // typed here is kept on the packet's record, and reaches a food only when the
                    // packet is logged (D38).
                    Text(
                        text = stringResource(R.string.scan_label_figures_kept),
                        style = MaterialTheme.typography.bodySmall,
                        color = MetaSelfInk.two,
                    )
                }

                val errors = if (state.showErrors) state.form.errors() else emptyMap()

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                    LabelField(
                        label = stringResource(R.string.scan_add_name),
                        value = state.form.name,
                        error = errors[ProductField.NAME],
                        number = false,
                    ) { onSetForm(state.form.copy(name = it)) }

                    LabelField(
                        label = stringResource(R.string.scan_add_brand),
                        value = state.form.brand,
                        error = null,
                        number = false,
                    ) { onSetForm(state.form.copy(brand = it)) }

                    LabelField(
                        label = stringResource(R.string.scan_add_kcal),
                        value = state.form.kcalPer100g,
                        error = errors[ProductField.KCAL],
                        number = true,
                    ) { onSetForm(state.form.copy(kcalPer100g = it)) }

                    LabelField(
                        label = stringResource(R.string.scan_add_protein),
                        value = state.form.proteinPer100g,
                        error = errors[ProductField.PROTEIN],
                        number = true,
                    ) { onSetForm(state.form.copy(proteinPer100g = it)) }

                    LabelField(
                        label = stringResource(R.string.scan_add_carbs),
                        value = state.form.carbsPer100g,
                        error = errors[ProductField.CARBS],
                        number = true,
                    ) { onSetForm(state.form.copy(carbsPer100g = it)) }

                    LabelField(
                        label = stringResource(R.string.scan_add_fat),
                        value = state.form.fatPer100g,
                        error = errors[ProductField.FAT],
                        number = true,
                    ) { onSetForm(state.form.copy(fatPer100g = it)) }

                    LabelField(
                        label = stringResource(R.string.scan_add_serving),
                        value = state.form.servingSizeG,
                        error = errors[ProductField.SERVING],
                        number = true,
                    ) { onSetForm(state.form.copy(servingSizeG = it)) }
                }

                Button(onClick = onSaveTyped, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scan_add_save))
                }
            }

            is ScanUiState.NotFound -> {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    Text(
                        text = ScanWording.notFound(state.couldNotAsk),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = state.barcode,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                    Button(onClick = onAddByHand, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.scan_add_it))
                    }
                    TextButton(onClick = onDescribeInstead, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.scan_describe_instead))
                    }
                    TextButton(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.scan_again))
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelField(
    label: String,
    value: String,
    error: String?,
    number: Boolean,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = error != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** An EAN-8 is the shortest barcode a food package carries. Fewer digits is a half-typed number. */
private const val MIN_BARCODE_DIGITS = 8

/**
 * "The food database has this packet but no protein or fat figures for it…" — the figures named in
 * the form's order and joined as a sentence, singular or plural by count, every word from
 * resources. NAME and SERVING are never lacked; if one arrived it would be named as nothing.
 */
@Composable
private fun databaseLacks(fields: List<ProductField>): String {
    val names = fields.mapNotNull { field ->
        when (field) {
            ProductField.KCAL -> stringResource(R.string.scan_lacks_kcal)
            ProductField.PROTEIN -> stringResource(R.string.scan_lacks_protein)
            ProductField.CARBS -> stringResource(R.string.scan_lacks_carbs)
            ProductField.FAT -> stringResource(R.string.scan_lacks_fat)
            ProductField.NAME, ProductField.SERVING -> null
        }
    }
    val list = when (names.size) {
        0 -> ""
        1 -> names[0]
        2 -> stringResource(R.string.scan_lacks_list_two, names[0], names[1])
        3 -> stringResource(R.string.scan_lacks_list_three, names[0], names[1], names[2])
        else -> stringResource(R.string.scan_lacks_list_four, names[0], names[1], names[2], names[3])
    }
    return pluralStringResource(R.plurals.scan_database_lacks, names.size, list)
}
