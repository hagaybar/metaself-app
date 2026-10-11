package com.metaself.app.ui.screen.letter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.metaself.app.domain.letter.LetterTexts
import com.metaself.app.domain.letter.WeeklyLetter
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.letter.LetterWording
import com.metaself.app.ui.theme.Spacing
import java.time.ZoneId

/**
 * One weekly letter (D103, D140). A letter written as the coach: its dates, the headline, "From your
 * trainer", then what you did this week, how it is paying off, the one thing to look after with what it rests
 * on, next week, the close — and the box of figures counted on the phone behind "The week's figures".
 * A letter written before D140 is drawn as it was (D102). Branches only — no early return out of an inline
 * composable (InlineComposableReturnGuardTest).
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
            is LetterViewModel.State.Shown -> when (val texts = state.letter.texts) {
                is LetterTexts.Coach -> CoachLetter(state.letter, texts, zone)
                is LetterTexts.SixParts -> SixPartLetter(state.letter, texts, zone)
            }
        }
    }
}

@Composable
private fun Heading(letter: WeeklyLetter, from: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(
            LetterWording.weekLabel(letter.weekMonday),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(letter.texts.headline, style = MaterialTheme.typography.titleLarge)
        Text(from, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** D140: the coach's letter, its parts in order, the figures closed until tapped. */
@Composable
private fun CoachLetter(letter: WeeklyLetter, texts: LetterTexts.Coach, zone: ZoneId) {
    var figuresOpen by rememberSaveable { mutableStateOf(false) }
    Heading(letter, LetterWording.FROM_COACH)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            CardLabel(LetterWording.RECOGNITION)
            LetterWording.paragraphs(texts.recognition).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
    val (lead, rest) = LetterWording.lead(texts.payingOff)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(lead) }
            append(rest)
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            CardLabel(LetterWording.LOOK_AFTER)
            Text(texts.lookAfter, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            Text(
                LetterWording.restsOn(texts.restsOn),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth(), border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)) {
        Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            CardLabel(LetterWording.NEXT_WEEK)
            Text(texts.nextWeek, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Close(texts.close)
    Text(
        LetterWording.FIGURES,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button) { figuresOpen = !figuresOpen }
            .padding(vertical = Spacing.Section),
    )
    if (figuresOpen) {
        Box(letter)
        Notes(letter, zone)
    }
}

/** A card's heading: ink, not the green, which is not checked against a card's colour. */
@Composable
private fun CardLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
}

/** D102, D103: a letter written before D140, drawn as it was. */
@Composable
private fun SixPartLetter(letter: WeeklyLetter, texts: LetterTexts.SixParts, zone: ZoneId) {
    Heading(letter, LetterWording.FROM_TRAINER)
    val parts = listOf(texts.effort, texts.progress, texts.lookAt, texts.nextWeek)
    LetterWording.HEADINGS.zip(parts).forEach { (heading, text) ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(heading, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Close(texts.close)
    Box(letter)
    Notes(letter, zone)
}

@Composable
private fun Close(close: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        HorizontalDivider()
        Text(close, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
        HorizontalDivider()
    }
}

/** The notes under the box: which days the food figures cover, and the band's line when it may be behind (D99). */
@Composable
private fun Notes(letter: WeeklyLetter, zone: ZoneId) {
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
