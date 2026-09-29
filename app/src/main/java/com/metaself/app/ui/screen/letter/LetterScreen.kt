package com.metaself.app.ui.screen.letter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import com.metaself.app.domain.letter.WeeklyLetter
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.letter.LetterWording
import com.metaself.app.ui.theme.Spacing
import java.time.ZoneId

/**
 * One weekly letter (D103): its dates, the headline, and — before a word the model wrote — that it is the
 * trainer's advice and the figures are the phone's (D4); then the four parts under their headings, the
 * close set apart, the box of figures counted on the phone, and the notes under it. Branches only — no
 * early return out of an inline composable (InlineComposableReturnGuardTest).
 */
@Composable
fun LetterScreen(
    state: LetterViewModel.State,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    MetaSelfScreen(title = LetterWording.PAGE_TITLE, modifier = modifier, onBack = onBack) {
        when (state) {
            LetterViewModel.State.Loading -> Unit
            LetterViewModel.State.Missing -> Text(LetterWording.MISSING, style = MaterialTheme.typography.bodyMedium)
            LetterViewModel.State.Unreadable -> Text(LetterWording.UNREADABLE, color = MaterialTheme.colorScheme.error)
            is LetterViewModel.State.Shown -> Letter(state.letter, zone)
        }
    }
}

@Composable
private fun Letter(letter: WeeklyLetter, zone: ZoneId) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(
            LetterWording.weekLabel(letter.weekMonday),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(letter.texts.headline, style = MaterialTheme.typography.titleLarge)
        Text(
            LetterWording.FROM_TRAINER,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val parts = listOf(letter.texts.effort, letter.texts.progress, letter.texts.lookAt, letter.texts.nextWeek)
    LetterWording.HEADINGS.zip(parts).forEach { (heading, text) ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(heading, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        HorizontalDivider()
        Text(letter.texts.close, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
        HorizontalDivider()
    }
    Box(letter)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        LetterWording.foodNote(letter.figures.week.food.daysLogged)?.let { note ->
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        letter.bandDataUntil?.let { until ->
            Text(LetterWording.bandNote(until, zone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The box (D100, D103): three cells a row — the figure's name, this week, the four weeks' average. */
@Composable
private fun Box(letter: WeeklyLetter) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        BoxRow(LetterWording.BOX_TITLE, LetterWording.BOX_THIS_WEEK, LetterWording.BOX_AVERAGE, header = true)
        HorizontalDivider()
        LetterWording.rows(letter.figures).forEach { row -> BoxRow(row.name, row.thisWeek, row.average, header = false) }
    }
}

@Composable
private fun BoxRow(name: String, thisWeek: String, average: String, header: Boolean) {
    val style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium
    val colour = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.Tight), horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        Text(name, style = style, color = colour, modifier = Modifier.weight(2f))
        Text(thisWeek, style = style, color = colour, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(average, style = style, color = colour, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}
