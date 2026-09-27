package com.metaself.app.ui.screen.movement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.movement.MovementDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.movement.MovementWeekWording
import com.metaself.app.ui.theme.Spacing

/**
 * This week's movement (D73–D75): the week's distance as the one large figure, the average movement
 * calories and the workouts beneath it, a short row per day — today first — and the last four weeks
 * at the foot.
 *
 * It LOOKS; it takes no input yet. "Log a workout" arrives with logging by hand (D75), not before.
 */
@Composable
fun MovementScreen(
    state: MovementUiState,
    onToggleDay: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(
        title = stringResource(R.string.movement_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        val week = state.week
        when {
            state.unreadable -> Text(
                text = stringResource(R.string.movement_unreadable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            week == null -> Unit

            else -> {
                Headline(week)

                Column(modifier = Modifier.fillMaxWidth()) {
                    week.days.forEach { day ->
                        DayRow(
                            day = day,
                            open = day.epochDay == state.openDay,
                            onToggle = { onToggleDay(day.epochDay) },
                        )
                    }
                }

                MovementWeekWording.lastFourWeeks(week)?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** D74 items 1–4: the kicker, the one large figure, and the two smaller lines beneath it. */
@Composable
private fun Headline(week: MovementWeek) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(
            text = MovementWeekWording.kicker(week.monday),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The one number this screen exists to answer, in the app's display face (D48).
        MovementWeekWording.distance(week)?.let { distance ->
            Text(
                text = distance,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        MovementWeekWording.averageMovement(week)?.let { average ->
            Text(text = average, style = MaterialTheme.typography.bodyLarge)
        }
        MovementWeekWording.workouts(week)?.let { workouts ->
            Text(
                text = workouts,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One day (D73). The heading and the one-line summary are one button that says whether it is open;
 * the summary stays whether the day is open or closed. An open day's detail lines sit beneath it,
 * outside the button, so a screen reader reads them one at a time.
 */
@Composable
private fun DayRow(day: MovementDay, open: Boolean, onToggle: () -> Unit) {
    val said = stringResource(if (open) R.string.movement_day_open else R.string.movement_day_closed)
    val action = stringResource(if (open) R.string.movement_day_hide else R.string.movement_day_show)

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = action, onClick = onToggle)
                // The usual 48 of touch; a render here cannot measure it (CLAUDE.md), the phone can.
                .heightIn(min = 48.dp)
                .semantics { stateDescription = said }
                .padding(vertical = Spacing.Related),
            verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
        ) {
            Text(
                text = MovementWeekWording.dayHeading(day.epochDay),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = MovementWeekWording.summaryLine(day),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) {
            val details = MovementWeekWording.detailLines(day)
            if (details.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.Related),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
                ) {
                    details.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }
    }
}
