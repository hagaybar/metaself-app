package com.metaself.app.ui.screen.movement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metaself.app.domain.trainer.MovementSpan
import com.metaself.app.domain.trainer.SpanBar
import com.metaself.app.domain.trainer.SpanChart
import com.metaself.app.domain.trainer.SpanMeasure
import com.metaself.app.domain.trainer.WeekStrip
import com.metaself.app.ui.movement.MovementSpanWording
import com.metaself.app.ui.screen.trainer.WeekStripChart
import com.metaself.app.ui.theme.Spacing

/** The chart's height, as the Trainer's strip; the top [LABEL_ROOM] of it keeps the average's label clear of the tallest bar. */
private val CHART_HEIGHT = 140.dp
private val LABEL_ROOM = 18.dp
private val BAR_GAP = 4.dp

/** A recorded bar of nothing is a mark on the baseline, so "0" is seen and is not mistaken for "before the record". */
private val ZERO_MARK = 2.dp

/** An arrow's width, and the gap an absent one leaves (D83). */
internal val ARROW_SIZE = 48.dp

/** D135: Week · 12 weeks · Year, under the title bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpanSwitch(span: MovementSpan, onSpan: (MovementSpan) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        MovementSpan.entries.forEachIndexed { index, each ->
            SegmentedButton(
                selected = each == span,
                onClick = { onSpan(each) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = MovementSpan.entries.size),
            ) {
                Text(MovementSpanWording.spanName(each))
            }
        }
    }
}

/**
 * The span line between ‹ and › (D83, D135); an arrow not offered leaves its width empty, so the line keeps its place
 * from span to span.
 */
@Composable
internal fun SpanArrows(
    text: String,
    canGoEarlier: Boolean,
    canGoLater: Boolean,
    earlierSaid: String,
    laterSaid: String,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (canGoEarlier) Arrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, earlierSaid, onEarlier) else Spacer(Modifier.size(ARROW_SIZE))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (canGoLater) Arrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, laterSaid, onLater) else Spacer(Modifier.size(ARROW_SIZE))
    }
}

/** One of the two arrows (D83): Material's icon button, 48 dp of touch, named for a screen reader. */
@Composable
private fun Arrow(icon: ImageVector, said: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(ARROW_SIZE)) {
        Icon(imageVector = icon, contentDescription = said)
    }
}

/** D135: the Week chart — the Trainer's strip for the week, no "To do" — on a card, with its legend when a plan covers the week. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeekChartCard(strip: WeekStrip) {
    Card(modifier = Modifier.fillMaxWidth().semantics { contentDescription = MovementSpanWording.WEEK_CHART }) {
        Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            WeekStripChart(strip)
            if (strip.withPlan) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Section)) {
                    Legend(MovementSpanWording.PLANNED, Swatch.PLANNED)
                    Legend(MovementSpanWording.YOUR_OWN, Swatch.OWN)
                }
            }
        }
    }
}

/**
 * D136, D137: 12 weeks or Year — the headline and the line under it, the 12-week chips, and the chart on a card with
 * its labels and legend. A span wholly before the record says so and draws nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChartSpan(chart: SpanChart, measure: SpanMeasure, onMeasure: (SpanMeasure) -> Unit, onBar: (SpanBar) -> Unit) {
    if (chart.nothingRecorded) {
        Text(MovementSpanWording.NOTHING_THIS_FAR, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        val twelve = chart.span == MovementSpan.TWELVE_WEEKS
        val shown = if (twelve) measure else SpanMeasure.MINUTES
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            MovementSpanWording.headline(chart, shown)?.let {
                Text(it, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
            }
            val line = if (twelve) MovementSpanWording.twelveWeeksLine(chart, shown) else MovementSpanWording.yearLine(chart)
            line?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
        if (twelve) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                SpanMeasure.entries.forEach { each ->
                    FilterChip(selected = each == measure, onClick = { onMeasure(each) }, label = { Text(MovementSpanWording.measureName(each)) })
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                Bars(chart, shown, onBar)
                BarLabels(chart)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    MovementSpanWording.legend(chart, shown).forEach { entry ->
                        val swatch = when (entry) {
                            MovementSpanWording.PLANNED -> Swatch.PLANNED
                            MovementSpanWording.YOUR_OWN -> Swatch.OWN
                            MovementSpanWording.AVERAGE -> Swatch.AVERAGE
                            else -> null
                        }
                        Legend(entry, swatch)
                    }
                }
            }
        }
        Text(
            if (twelve) MovementSpanWording.TAP_A_WEEK else MovementSpanWording.TAP_A_MONTH,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One bar per week or month, each a button that opens it (D136) — named by [MovementSpanWording.barDescription] —
 * except one before the record, which is blank. Minutes are split, planned at the base; Km and Steps are one colour.
 * The dashed line is the headline's average.
 */
@Composable
private fun Bars(chart: SpanChart, measure: SpanMeasure, onBar: (SpanBar) -> Unit) {
    val values = chart.bars.map { it.value(measure) }
    val average = chart.average(measure)
    val scale = maxOf(values.filterNotNull().maxOrNull() ?: 0.0, average ?: 0.0)
    val full = CHART_HEIGHT - LABEL_ROOM
    val split = measure == SpanMeasure.MINUTES
    val dark = MaterialTheme.colorScheme.primary
    val light = MaterialTheme.colorScheme.primaryContainer
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Box(Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .drawWithContent {
                    drawContent()
                    if (average != null && scale > 0) {
                        val y = size.height - full.toPx() * (average / scale).toFloat()
                        drawLine(
                            color = ink,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                        )
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
        ) {
            chart.bars.forEachIndexed { index, bar ->
                val said = MovementSpanWording.barDescription(chart, bar, measure)
                val value = values[index]
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (bar.recorded) {
                                Modifier.clickable(role = Role.Button, onClickLabel = MovementSpanWording.openBar(chart.span)) { onBar(bar) }
                            } else {
                                Modifier
                            },
                        )
                        .semantics { contentDescription = said },
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    when {
                        value == null -> Unit
                        value <= 0.0 || scale <= 0.0 -> Box(Modifier.fillMaxWidth().height(ZERO_MARK).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(1.dp)))
                        split -> {
                            Block(heightOf(bar.otherMinutes.toDouble(), scale, full), light, top = true)
                            Block(heightOf(bar.plannedMinutes.toDouble(), scale, full), dark, top = bar.otherMinutes == 0)
                        }
                        else -> Block(heightOf(value, scale, full), dark, top = true)
                    }
                }
            }
        }
        if (average != null && scale > 0) {
            val fromTop = CHART_HEIGHT - full * (average / scale).toFloat() - 16.dp
            Text(
                MovementSpanWording.averageLabel(average, measure),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = maxOf(fromTop, 0.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = ink,
            )
        }
    }
}

private fun heightOf(value: Double, scale: Double, full: Dp): Dp = if (scale <= 0 || value <= 0) 0.dp else full * (value / scale).toFloat()

/** A part of a bar: the top part has rounded upper corners. */
@Composable
private fun Block(height: Dp, colour: Color, top: Boolean) {
    if (height > 0.dp) {
        val shape = if (top) RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp) else RoundedCornerShape(0.dp)
        Box(Modifier.fillMaxWidth().height(height).background(colour, shape))
    }
}

/** Under each bar: every other week's Monday, or the month's initial — the month holding today in bold. */
@Composable
private fun BarLabels(chart: SpanChart) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BAR_GAP)) {
        MovementSpanWording.barLabels(chart).forEachIndexed { index, label ->
            val current = chart.bars[index].current
            Text(
                label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (current) FontWeight.SemiBold else null,
                color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class Swatch { PLANNED, OWN, AVERAGE }

@Composable
private fun Legend(text: String, swatch: Swatch?) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val box = Modifier.size(10.dp)
        when (swatch) {
            Swatch.PLANNED -> Box(box.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
            Swatch.OWN -> Box(box.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(3.dp)))
            Swatch.AVERAGE -> Box(
                Modifier.size(width = 14.dp, height = 10.dp).drawBehind {
                    drawLine(
                        color = ink,
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
                    )
                },
            )
            null -> Unit
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = ink)
    }
}
