package com.metaself.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.metaself.app.R
import com.metaself.app.domain.reminder.Reminder
import com.metaself.app.domain.window.MeasuredWindow
import com.metaself.app.domain.window.WindowRule
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.day.ReminderWording
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.window.WindowWording
import java.time.LocalDate

/**
 * Settings' Eating page (D79): when he means to eat — the hours or a ratio, and the tally — then the
 * daily reminder. Moved here unchanged from the single Settings scroll.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EatingSettingsPage(
    state: SettingsUiState,
    onSetWindow: (Int, Int) -> Unit,
    onSetRatio: (Int) -> Unit,
    onClearWindow: () -> Unit,
    onSetReminder: (Reminder) -> Unit,
    onSendReminderNow: () -> Unit,
    /** Take down the sentence of an action that threw. Required, so it cannot be left unwired. */
    onDismissFailure: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(
        title = stringResource(SettingsPage.EATING.title),
        modifier = modifier,
        onBack = onBack,
    ) {
        // --- when to eat ---

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Text(
                text = stringResource(R.string.settings_window_title),
                style = MaterialTheme.typography.titleMedium,
            )

            // BOTH kinds are on screen at once, and that is the arrangement rather than a mode toggle
            // or a tab. The two-way choice is which one he SAVES, not which one he can see: a toggle
            // would hide the kind he is not using behind the kind he is, and picking a ratio would then
            // mean two decisions instead of one.
            val rule = state.windowRule
            val fixed = (rule as? WindowRule.Fixed)?.window
            val measured = (rule as? WindowRule.Measured)?.window

            var startHour by remember(fixed) { mutableStateOf(fixed?.startHour ?: DEFAULT_START) }
            var endHour by remember(fixed) { mutableStateOf(fixed?.endHour ?: DEFAULT_END) }
            var fastingHours by remember(measured) {
                mutableStateOf(measured?.fastingHours ?: DEFAULT_FASTING)
            }

            Text(
                // Once there is a tally it replaces the summary — which is why the ratio and its
                // spoken-out form live below, under the chips, rather than only in this sentence.
                //
                // The tally is in the unit its own kind judges in, and says so in as many words. The
                // fixed hours count days, because "eat between 06:00 and 20:00" is a statement about a
                // day; a ratio counts eating stretches bounded by the fast, because a ratio is a
                // statement about hours (design §3.1, §4). One number in the other's unit would answer
                // a question he did not ask.
                text = when (rule) {
                    null -> stringResource(R.string.settings_window_none)

                    is WindowRule.Fixed ->
                        WindowWording.kept(state.windowKept, state.windowJudged)
                            ?: stringResource(
                                R.string.settings_window_set,
                                WindowWording.hours(rule.window),
                            )

                    is WindowRule.Measured ->
                        WindowWording.keptStretches(
                            kept = state.windowKept,
                            judged = state.windowJudged,
                            since = LocalDate.ofEpochDay(rule.fromEpochDay),
                        )
                            ?: stringResource(
                                R.string.settings_window_ratio_set,
                                WindowWording.ratio(rule.window),
                                WindowWording.inWords(rule.window),
                            )
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = stringResource(R.string.settings_window_kind_hours),
                style = MaterialTheme.typography.titleSmall,
            )

            HourPicker(
                label = stringResource(R.string.settings_window_start),
                hour = startHour,
                onChange = { startHour = it },
            )
            HourPicker(
                label = stringResource(R.string.settings_window_end),
                hour = endHour,
                onChange = { endHour = it },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                Button(onClick = { onSetWindow(startHour, endHour) }) {
                    Text(stringResource(R.string.settings_window_save))
                }
                if (rule != null) {
                    TextButton(onClick = onClearWindow) {
                        Text(stringResource(R.string.settings_window_clear))
                    }
                }
            }

            Text(
                text = stringResource(R.string.settings_window_kind_ratio),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.settings_window_ratio_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )

            // Wraps: five chips do not fit one row on a phone, and in a Row the last were squeezed
            // until "20/4" stood as a column of characters. Each label keeps to one line.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                RATIOS.forEach { hours ->
                    FilterChip(
                        selected = fastingHours == hours,
                        onClick = { fastingHours = hours },
                        label = {
                            Text(
                                text = WindowWording.ratio(MeasuredWindow(hours)),
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                    )
                }
            }

            // The slash never stands alone, and this is its OWN line rather than the tail of a longer
            // sentence: "16/8" means sixteen hours fasting to most of the world and eight to the rest
            // of it, and a figure whose meaning has to be inferred is a figure that will be read wrong.
            Text(
                text = WindowWording.inWords(MeasuredWindow(fastingHours)),
                style = MaterialTheme.typography.bodyMedium,
            )

            // Reads the chosen ratio when it RUNS, not when it is composed. This lambda was built
            // before the chip was pressed, so a value lifted out of it would save the default for ever
            // — a bug that is completely invisible on screen.
            Button(onClick = { onSetRatio(fastingHours) }) {
                Text(stringResource(R.string.settings_window_ratio_save))
            }

            // D27's one firm rule about this feature, said where it is set — and said once for both
            // kinds, because neither of them reaches backwards.
            Text(
                text = WindowWording.FROM_TODAY,
                style = MaterialTheme.typography.bodySmall,
                color = MetaSelfInk.two,
            )
            RefusedHere(state.failed, SettingsPart.WINDOW, onDismissFailure)
        }

        HorizontalDivider()

        // --- the reminder ---

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Text(
                text = stringResource(R.string.settings_reminder_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ReminderWording.schedule(state.reminder),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.reminder.enabled,
                    onCheckedChange = { on -> onSetReminder(state.reminder.copy(enabled = on)) },
                )
            }

            if (state.reminder.enabled) {
                // Whole hours only. A reminder is not an appointment, and a minute picker invites a
                // precision that the alarm itself — inexact on most phones — cannot honour.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Tight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            onSetReminder(
                                state.reminder.copy(hour = (state.reminder.hour + 23) % 24),
                            )
                        },
                    ) { Text(stringResource(R.string.settings_reminder_earlier)) }

                    Text(
                        text = ReminderWording.clock(state.reminder),
                        style = MaterialTheme.typography.headlineSmall,
                    )

                    TextButton(
                        onClick = {
                            onSetReminder(
                                state.reminder.copy(hour = (state.reminder.hour + 1) % 24),
                            )
                        },
                    ) { Text(stringResource(R.string.settings_reminder_later)) }
                }

                Text(
                    text = stringResource(R.string.settings_reminder_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MetaSelfInk.two,
                )

                // No test on the build machine can prove a notification arrives on a phone. This is the
                // same button, and the same reasoning, as the one that proves the API key works.
                TextButton(onClick = onSendReminderNow) {
                    Text(stringResource(R.string.settings_reminder_test))
                }
            }
            RefusedHere(state.failed, SettingsPart.REMINDER, onDismissFailure)
        }
    }
}
