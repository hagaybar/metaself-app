package com.metaself.app.ui.screen.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.domain.profile.ActivityLevel
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.profile.Sex

/**
 * The setup form, and the same form again when the owner edits what he entered.
 *
 * Stateless: it draws what it is given and reports what was touched. All the judgement lives in
 * [SetupFormState], which is pure and tested without a device.
 *
 * [showErrors] is false until the owner tries to save. Colouring every empty box red before he has
 * typed a character is the app telling him off for arriving.
 *
 * [failed] is a save that threw. It is drawn above Save, where he pressed it, and the form keeps
 * what he typed so Save can simply be pressed again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    state: SetupFormState,
    currentYear: Int,
    showErrors: Boolean,
    onChange: (SetupFormState) -> Unit,
    onSave: () -> Unit,
    onCancel: (() -> Unit)?,
    failed: ActionRefused? = null,
    onDismissFailure: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val errors = if (showErrors) state.errors(currentYear) else emptyMap()

    // On first run there is nowhere to go back to, so there is no arrow. When the owner is editing
    // what he entered, there is.
    MetaSelfScreen(
        title = stringResource(R.string.setup_title),
        modifier = modifier,
        onBack = onCancel,
    ) {
        Text(
            text = stringResource(R.string.setup_intro),
            style = MaterialTheme.typography.bodyMedium,
        )

        NumberField(
            label = stringResource(R.string.setup_height),
            value = state.heightCm,
            error = errors[SetupField.HEIGHT],
            decimal = false,
            onValueChange = { onChange(state.copy(heightCm = it)) },
        )

        NumberField(
            label = stringResource(R.string.setup_birth_year),
            value = state.birthYear,
            error = errors[SetupField.BIRTH_YEAR],
            decimal = false,
            onValueChange = { onChange(state.copy(birthYear = it)) },
        )

        ChoiceRow(
            label = stringResource(R.string.setup_sex),
            error = errors[SetupField.SEX],
        ) {
            FilterChip(
                selected = state.sex == Sex.MALE,
                onClick = { onChange(state.copy(sex = Sex.MALE)) },
                label = { Text(stringResource(R.string.setup_sex_male)) },
            )
            FilterChip(
                selected = state.sex == Sex.FEMALE,
                onClick = { onChange(state.copy(sex = Sex.FEMALE)) },
                label = { Text(stringResource(R.string.setup_sex_female)) },
            )
        }

        NumberField(
            label = stringResource(R.string.setup_weight),
            value = state.weightKg,
            error = errors[SetupField.WEIGHT],
            decimal = true,
            onValueChange = { onChange(state.copy(weightKg = it)) },
        )

        ChoiceRow(
            label = stringResource(R.string.setup_activity),
            error = errors[SetupField.ACTIVITY],
        ) {
            ActivityLevel.entries.forEach { level ->
                FilterChip(
                    selected = state.activity == level,
                    onClick = { onChange(state.copy(activity = level)) },
                    label = { Text(stringResource(activityLabel(level))) },
                )
            }
        }

        ChoiceRow(
            label = stringResource(R.string.setup_goal),
            error = errors[SetupField.DIRECTION],
        ) {
            GoalDirection.entries.forEach { direction ->
                FilterChip(
                    selected = state.direction == direction,
                    onClick = {
                        onChange(
                            state.copy(
                                direction = direction,
                                kgPerWeek = if (direction == GoalDirection.HOLD) {
                                    null
                                } else {
                                    state.kgPerWeek
                                },
                                // Holding weight is not going anywhere, so a destination left
                                // behind by switching away from "lose" must go with it.
                                targetKg = if (direction == GoalDirection.HOLD) {
                                    ""
                                } else {
                                    state.targetKg
                                },
                            ),
                        )
                    },
                    label = { Text(stringResource(goalLabel(direction))) },
                )
            }
        }

        if (state.direction != null && state.direction != GoalDirection.HOLD) {
            ChoiceRow(
                label = stringResource(R.string.setup_rate),
                error = errors[SetupField.RATE],
            ) {
                Goal.OFFERED_RATES_KG_PER_WEEK.forEach { rate ->
                    FilterChip(
                        selected = state.kgPerWeek == rate,
                        onClick = { onChange(state.copy(kgPerWeek = rate)) },
                        label = { Text("$rate kg") },
                    )
                }
            }

            // Optional. A rate with no destination is still a goal, and was the only kind this app
            // had until now — so the field says so rather than looking like something unfinished.
            NumberField(
                label = stringResource(R.string.setup_target),
                value = state.targetKg,
                error = errors[SetupField.TARGET],
                decimal = true,
                onValueChange = { onChange(state.copy(targetKg = it)) },
            )
            Text(
                text = stringResource(R.string.setup_target_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
        }

        failed?.let {
            Text(
                text = stringResource(it.sentence),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onDismissFailure) {
                Text(stringResource(R.string.action_refused_dismiss))
            }
        }

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.setup_save))
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    error: String?,
    decimal: Boolean,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = error != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(
    label: String,
    error: String?,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) { content() }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun activityLabel(level: ActivityLevel): Int = when (level) {
    ActivityLevel.SEDENTARY -> R.string.setup_activity_sedentary
    ActivityLevel.LIGHT -> R.string.setup_activity_light
    ActivityLevel.MODERATE -> R.string.setup_activity_moderate
    ActivityLevel.ACTIVE -> R.string.setup_activity_active
    ActivityLevel.VERY_ACTIVE -> R.string.setup_activity_very_active
}

private fun goalLabel(direction: GoalDirection): Int = when (direction) {
    GoalDirection.LOSE -> R.string.setup_goal_lose
    GoalDirection.HOLD -> R.string.setup_goal_hold
    GoalDirection.GAIN -> R.string.setup_goal_gain
}
