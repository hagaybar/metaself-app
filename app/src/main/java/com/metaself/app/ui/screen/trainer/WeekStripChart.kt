package com.metaself.app.ui.screen.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metaself.app.domain.trainer.WeekStrip
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.TrainerWeekWording

/** The strip's height, as in the approved mockup; the top [TICK_ROOM] of it holds the ticks. */
private val STRIP_HEIGHT = 140.dp
private val TICK_ROOM = 22.dp

/** A block is never shorter than this: a short session in a long week is still seen (D114). */
private val LEAST_BLOCK = 8.dp
private val BLOCK_GAP = 3.dp
private val COLUMN_GAP = 6.dp

/**
 * D114, D135: the week strip — seven columns of blocks, one per counted session by its minutes, planned in the dark
 * green and the rest light, with "To do" when the strip carries open planned sessions — and its day letters. Drawn on
 * the Trainer page and, without "To do", as Your movement's Week chart, so the two cannot draw a week differently.
 */
@Composable
fun WeekStripChart(strip: WeekStrip, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        Strip(strip)
        DayLetters(strip)
    }
}

/** Seven columns, and "To do" when planned sessions are open; each column's blocks newest on top. */
@Composable
private fun Strip(strip: WeekStrip) {
    val scale = strip.scaleMinutes
    // Room for the tick and for the gaps of the column with the most blocks, so the tallest column fits.
    val most = maxOf(strip.days.maxOf { it.blocks.size }, strip.toDo.size)
    val full = STRIP_HEIGHT - TICK_ROOM - BLOCK_GAP * most
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(STRIP_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
    ) {
        strip.days.forEach { day ->
            StripColumn {
                if (day.ticked) {
                    Box(Modifier.fillMaxWidth().height(TICK_ROOM), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
                day.blocks.asReversed().forEach { block ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(blockHeight(block.minutes, scale, full))
                            .background(
                                if (block.ticked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(6.dp),
                            ),
                    )
                }
                if (day.blocks.isEmpty() && !strip.isAfterToday(day)) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(1.dp)))
                }
            }
        }
        if (strip.toDo.isNotEmpty()) {
            StripColumn {
                strip.toDo.forEach { minutes -> OpenBlock(minutes, blockHeight(minutes, scale, full)) }
            }
        }
    }
}

@Composable
private fun RowScope.StripColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(BLOCK_GAP, Alignment.Bottom),
    ) { content() }
}

/** An open planned session: a dashed outline in the dark green, labelled with its minutes ("40′"). */
@Composable
private fun OpenBlock(minutes: Int, height: Dp) {
    val outline = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .dashedOutline(outline, corner = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Ink, not the green: the theme's green on a card is under the text floor; the outline carries the colour.
        Text(TrainerWeekWording.openMinutes(minutes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun blockHeight(minutes: Int, scale: Int, full: Dp): Dp =
    WeekStrip.height(minutes, scale, full.value, LEAST_BLOCK.value).dp

/** M T W T F S S, and "To do": today's letter bold. */
@Composable
private fun DayLetters(strip: WeekStrip) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP)) {
        strip.days.forEach { day ->
            val today = day.epochDay == strip.today
            Text(
                TrainerWeekWording.dayLetter(day.epochDay),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (today) FontWeight.SemiBold else null,
                color = if (today) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (strip.toDo.isNotEmpty()) {
            Text(
                TrainerWeekWording.TO_DO,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
