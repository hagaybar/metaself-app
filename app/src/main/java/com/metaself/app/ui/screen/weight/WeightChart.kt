package com.metaself.app.ui.screen.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.weight.TrendPoint
import com.metaself.app.ui.goal.GoalWording
import com.metaself.app.ui.theme.Feel
import com.metaself.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Where one reading lands on the canvas. */
data class ChartPoint(val x: Float, val y: Float, val trendY: Float)

/**
 * Where everything lands, worked out without a canvas so that it can be tested.
 *
 * Canvas y grows downwards, so the heaviest weight has the smallest y — which is what a chart of
 * weight should look like, and is the sort of inversion that is obvious in a test and invisible in a
 * code review.
 *
 * The range shown and the two date labels are worked out HERE rather than in the drawing code, for
 * the same reason the axis always was: they are arithmetic about dates, and arithmetic that only a
 * canvas can reach is arithmetic nobody checks.
 */
data class ChartGeometry(
    val points: List<ChartPoint>,
    /** The top of the vertical axis, in kilograms. The gridlines are laid inside it. */
    val heaviestKg: Double,
    /** The bottom of the vertical axis, in kilograms. */
    val lightestKg: Double,
    /** The date of the first reading DRAWN — not of the range asked for. */
    val startLabel: String,
    /** The date of the last reading drawn, or null when everything drawn is one day. */
    val endLabel: String?,
    /** Where the goal weight sits, or null when there is not one, or it is off this view. */
    val targetY: Float? = null,
    /** The goal weight the dashed line is drawn at; null exactly when [targetY] is. */
    val targetKg: Double? = null,
    /** The trend points drawn, oldest first — what a finger on the chart reads from. */
    val shown: List<TrendPoint> = emptyList(),
    val widthPx: Float = 0f,
    val heightPx: Float = 0f,
    val insetPx: Float = 0f,
) {
    val firstDay: Long get() = shown.first().reading.epochDay
    val lastDay: Long get() = shown.last().reading.epochDay

    /** How many days the drawn readings cover: 0 when they are all one day. */
    val spanDays: Long get() = lastDay - firstDay

    private val plotWidth: Float get() = widthPx - insetPx * 2
    private val plotHeight: Float get() = heightPx - insetPx * 2

    /** Where [epochDay] sits across the plot. One day shown sits in the middle. */
    fun xOf(epochDay: Long): Float =
        if (spanDays <= 0L) {
            widthPx / 2f
        } else {
            insetPx + (epochDay - firstDay).toFloat() / spanDays * plotWidth
        }

    /** Where [kg] sits up the plot — heavier is higher, so a smaller y. */
    fun yOf(kg: Double): Float =
        insetPx + ((heaviestKg - kg) / (heaviestKg - lightestKg) * plotHeight).toFloat()

    /**
     * The day under a finger at [x]: the nearest day, and past either end, that end. Every day
     * between the first reading and the last can be held, not only the days with a reading.
     */
    fun dayAt(x: Float): Long {
        if (spanDays <= 0L || plotWidth <= 0f) return firstDay
        val along = ((x - insetPx) / plotWidth * spanDays).roundToLong()
        return (firstDay + along).coerceIn(firstDay, lastDay)
    }

    /**
     * What [epochDay] reads: the trend, and what the scale said if anything.
     *
     * On a day with no reading the trend is taken off the line exactly as it is drawn — straight
     * between the readings either side — so the ring sits on the line and the readout agrees with
     * the picture. It is still the trend and is only ever labelled as the trend.
     */
    fun dayOn(epochDay: Long): ChartDay {
        val day = epochDay.coerceIn(firstDay, lastDay)
        val found = shown.binarySearchBy(day) { it.reading.epochDay }
        if (found >= 0) {
            val point = shown[found]
            return ChartDay(epochDay = day, trendKg = point.trendKg, weighedKg = point.reading.kg)
        }
        val after = shown[-(found + 1)]
        val before = shown[-(found + 1) - 1]
        val fraction = (day - before.reading.epochDay).toDouble() /
            (after.reading.epochDay - before.reading.epochDay)
        return ChartDay(
            epochDay = day,
            trendKg = before.trendKg + (after.trendKg - before.trendKg) * fraction,
            weighedKg = null,
        )
    }

    companion object {

        /**
         * The smallest range the vertical axis will ever cover.
         *
         * Without it, a fortnight in which the weight moved 400 g fills the whole chart and looks
         * like a cliff. The axis is honest about the numbers either way — the labels say what it
         * covers — but a picture that shouts is a picture that misleads, and the point of this
         * screen is to make a small change LOOK small.
         *
         * It applies to whatever range is being shown, which is what makes a narrow range matter
         * more rather than less: a month of 400 g is exactly the case it was written for.
         */
        const val MIN_SPAN_KG = 2.0

        /**
         * How far the goal line may stretch the axis before it is dropped instead.
         *
         * A goal 10 kg below a month in which the weight moved 400 g would balloon the axis tenfold
         * and flatten the readings — the thing the chart is FOR — into a line in the corner. Two is
         * a judgement, not a derived number: the goal may at most double what the readings need.
         * Relative rather than a fixed number of kilograms, so the whole history keeps showing a
         * goal that a single month cannot.
         */
        const val GOAL_STAYS_WITHIN = 2.0

        /** Below a year, a date says the day; at a year or more it says the month and the year. */
        private const val DAYS_IN_YEAR = 365L

        fun of(
            trend: List<TrendPoint>,
            widthPx: Float,
            heightPx: Float,
            range: ChartRange,
            todayEpochDay: Long,
            insetPx: Float = 0f,
            targetKg: Double? = null,
        ): ChartGeometry? {
            // The filter is the first thing that happens, so that everything below — the axis, the
            // minimum span, the labels — describes what is drawn and not what was stored.
            val shown = range.days?.let { days ->
                trend.filter { it.reading.epochDay >= todayEpochDay - days + 1 }
            } ?: trend
            if (shown.isEmpty()) return null

            val kgs = shown.flatMap { listOf(it.reading.kg, it.trendKg) }
            val readingSpan = maxOf(kgs.max() - kgs.min(), MIN_SPAN_KG)
            val goal = targetKg?.takeIf { goalKg ->
                val withGoal = kgs + goalKg
                maxOf(withGoal.max() - withGoal.min(), MIN_SPAN_KG) <=
                    readingSpan * GOAL_STAYS_WITHIN
            }

            val axisKgs = kgs + listOfNotNull(goal)
            val middle = (axisKgs.max() + axisKgs.min()) / 2.0
            val span = maxOf(axisKgs.max() - axisKgs.min(), MIN_SPAN_KG)
            val heaviest = middle + span / 2.0
            val lightest = middle - span / 2.0

            val firstDay = shown.first().reading.epochDay
            val lastDay = shown.last().reading.epochDay
            val days = (lastDay - firstDay).toFloat()

            // Everything is drawn inside an inset, so a dot at the very top or the far right is not
            // half-clipped by the edge of the canvas.
            val plotWidth = widthPx - insetPx * 2
            val plotHeight = heightPx - insetPx * 2

            fun y(kg: Double): Float =
                insetPx + ((heaviest - kg) / span * plotHeight).toFloat()

            fun x(epochDay: Long): Float =
                if (days <= 0f) widthPx / 2f else insetPx + (epochDay - firstDay) / days * plotWidth

            return ChartGeometry(
                points = shown.map { point ->
                    ChartPoint(
                        x = x(point.reading.epochDay),
                        y = y(point.reading.kg),
                        trendY = y(point.trendKg),
                    )
                },
                heaviestKg = heaviest,
                lightestKg = lightest,
                startLabel = date(firstDay, lastDay - firstDay),
                // One day shown is one date. Printing it at both ends would look like a chart
                // covering a stretch of time when it covers a morning.
                endLabel = if (lastDay == firstDay) null else date(lastDay, lastDay - firstDay),
                targetY = goal?.let { y(it) },
                targetKg = goal,
                shown = shown,
                widthPx = widthPx,
                heightPx = heightPx,
                insetPx = insetPx,
            )
        }

        /**
         * One date, in the detail the [spanDays] actually drawn deserves.
         *
         * The rule keys off what is on screen rather than the range chosen, so the whole history of
         * a fortnight reads as a fortnight instead of saying the same month twice.
         */
        internal fun date(epochDay: Long, spanDays: Long): String {
            val pattern = if (spanDays >= DAYS_IN_YEAR) "MMM yyyy" else "d MMM"
            return LocalDate.ofEpochDay(epochDay)
                .format(DateTimeFormatter.ofPattern(pattern, Locale.US))
        }
    }
}

/**
 * The chart, the row of ranges above it, and the sentences for what is not being drawn.
 *
 * The geometry is asked for HERE, once, rather than inside [WeightChart]: the screen has two things
 * to say that the drawing cannot — that the chosen range holds no readings, and that the goal weight
 * fell off this view — and both are answers the pure function already has.
 *
 * [captions] are drawn only when there is a chart to caption.
 */
@Composable
fun WeightChartBlock(
    trend: List<TrendPoint>,
    range: ChartRange,
    todayEpochDay: Long,
    onRange: (ChartRange) -> Unit,
    modifier: Modifier = Modifier,
    targetKg: Double? = null,
    onOpen: (() -> Unit)? = null,
    fillsHeight: Boolean = false,
    captions: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Related),
    ) {
        ChartRanges(selected = range, onRange = onRange)

        // Every argument the answer depends on is a key. Leaving the range or the day out is the
        // quiet way to ship this broken: the pure tests stay green while choosing a chip leaves
        // yesterday's labels on screen.
        val geometry = remember(trend, targetKg, range, todayEpochDay) {
            ChartGeometry.of(
                trend = trend,
                widthPx = 1f,
                heightPx = 1f,
                range = range,
                todayEpochDay = todayEpochDay,
                targetKg = targetKg,
            )
        }

        if (geometry == null) {
            // The whole history over a history that has something in it is never empty, which is
            // why All carries no phrase and needs none.
            range.spanPhrase?.let { phrase ->
                Text(
                    text = stringResource(R.string.weight_range_empty, stringResource(phrase)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            WeightChart(
                trend = trend,
                geometry = geometry,
                range = range,
                todayEpochDay = todayEpochDay,
                targetKg = targetKg,
                onOpen = onOpen,
                fillsHeight = fillsHeight,
                modifier = if (fillsHeight) Modifier.weight(1f) else Modifier,
            )

            if (targetKg != null && geometry.targetY == null) {
                Text(
                    text = stringResource(R.string.weight_goal_off_chart),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            captions()
        }
    }
}

/** The five ranges, as chips: the whole of choosing how much history to look at. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartRanges(selected: ChartRange, onRange: (ChartRange) -> Unit) {
    // Wrapping, not a fixed row. At a larger system font the fifth chip fell off the end of the
    // screen — and the fifth is All, the one the chart opens on, so the reader who most needs a
    // bigger font was the one who could not see which range was selected.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
        verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
    ) {
        ChartRange.entries.forEach { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onRange(range) },
                label = { Text(stringResource(range.chipLabel)) },
            )
        }
    }
}

/**
 * The quiet grid (public issue #15): the trend as the heavy line, the raw readings as dots behind
 * it, whole-kilo gridlines labelled at the right, the dates along the bottom, and the goal as a
 * labelled dashed line. Hold and drag to read a day.
 *
 * The dots stay because a smoothed line with nothing behind it is asking to be taken on trust, and
 * this app does not do that — the same reasoning as decision D9's visible arithmetic. They fade as
 * they crowd ([ChartLayout.dotAlpha]) so that a long history supports the trend instead of drawing
 * a band over it.
 *
 * No charting library: gridlines, a line, some dots and a few labels are Canvas and arithmetic, and
 * a library would be a dependency, a theme to fight and a second opinion about what a chart is. The
 * range chips are the answer to a chart too small to read.
 *
 * [geometry] is [WeightChartBlock]'s answer, which says what is drawn but not where; the plot works
 * the same answer out again at its real pixel size, once, for everything drawn inside it.
 *
 * Every label is Compose text, not canvas paint: paint has no semantics node, so a painted date is
 * a date no test can see. A screen reader also gets one sentence for the whole chart
 * ([ChartLayout.summary]), because the hold-and-drag is a reading a finger does and a screen reader
 * cannot.
 *
 * Nothing here animates — the ring and the readout appear under the finger and go when it lifts —
 * so the system's "Remove animations" has nothing to stop.
 */
@Composable
fun WeightChart(
    trend: List<TrendPoint>,
    geometry: ChartGeometry,
    range: ChartRange,
    todayEpochDay: Long,
    modifier: Modifier = Modifier,
    targetKg: Double? = null,
    onOpen: (() -> Unit)? = null,
    fillsHeight: Boolean = false,
) {
    // The day a finger is holding, or null. Forgotten whenever what is drawn changes, so a range
    // chosen mid-hold cannot leave a readout for a day that is no longer on the chart.
    var heldDay by remember(trend, range, todayEpochDay, targetKg) { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Clickable merges what is inside it into one node, so the caption below IS the thing
            // pressed — a canvas on its own has nothing for a finger or a screen reader to find.
            .then(
                if (onOpen == null) {
                    Modifier
                } else {
                    Modifier
                        .semantics(mergeDescendants = true) {}
                        .clickable(onClick = onOpen)
                },
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
    ) {
        // Always one line tall, whether it is the hint or a reading, so the chart does not jump
        // down when a finger lands on it.
        val held = heldDay
        Text(
            text = if (held == null) {
                stringResource(R.string.weight_chart_hold)
            } else {
                ChartLayout.readout(geometry.dayOn(held), geometry.spanDays)
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (held == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
        )

        Plot(
            trend = trend,
            geometry = geometry,
            range = range,
            todayEpochDay = todayEpochDay,
            targetKg = targetKg,
            heldDay = heldDay,
            onHold = { heldDay = it },
            modifier = Modifier
                .fillMaxWidth()
                .let { if (fillsHeight) it.weight(1f) else it.height(CHART_HEIGHT) },
        )

        if (onOpen != null) {
            Text(
                text = stringResource(R.string.weight_chart_open),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The plot, its gridline labels in a gutter on the right, and the dates under it.
 *
 * The gutter is as wide as the widest gridline label actually measures, so the plot never draws
 * under a label and a larger system font widens the gutter rather than overprinting the line.
 */
@Composable
private fun Plot(
    trend: List<TrendPoint>,
    geometry: ChartGeometry,
    range: ChartRange,
    todayEpochDay: Long,
    targetKg: Double?,
    heldDay: Long?,
    onHold: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium
    val captionInk = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val page = MaterialTheme.colorScheme.surface
    val gridColour = MaterialTheme.colorScheme.outlineVariant
    val dotColour = MaterialTheme.colorScheme.secondary
    val goalColour = MaterialTheme.colorScheme.tertiary

    val lines = remember(geometry.heaviestKg, geometry.lightestKg) {
        ChartLayout.gridLines(lightestKg = geometry.lightestKg, heaviestKg = geometry.heaviestKg)
    }
    val goalLabel = geometry.targetKg?.let { GoalWording.goalLine(it) }

    BoxWithConstraints(
        modifier = modifier.semantics { contentDescription = ChartLayout.summary(geometry) },
    ) {
        val gapPx = with(density) { LABEL_GAP.toPx() }
        val insetPx = with(density) { (ChartLayout.DOT_DIAMETER_DP / 2f).dp.toPx() }
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = if (constraints.hasBoundedHeight) {
            constraints.maxHeight.toFloat()
        } else {
            with(density) { CHART_HEIGHT.toPx() }
        }
        val gutterPx = remember(lines, labelStyle, measurer) {
            lines.maxOfOrNull { measurer.measure(it.label, labelStyle).size.width }?.toFloat() ?: 0f
        } + gapPx
        val lineHeightPx = remember(labelStyle, measurer) {
            measurer.measure("0", labelStyle).size.height.toFloat()
        }
        val plotHeightPx = heightPx - lineHeightPx - gapPx

        val plot = remember(trend, range, todayEpochDay, targetKg, widthPx, plotHeightPx, gutterPx) {
            ChartGeometry.of(
                trend = trend,
                widthPx = widthPx - gutterPx,
                heightPx = plotHeightPx,
                range = range,
                todayEpochDay = todayEpochDay,
                insetPx = insetPx,
                targetKg = targetKg,
            )
        } ?: return@BoxWithConstraints

        val readingDays = remember(plot) { plot.shown.map { it.reading.epochDay }.toSet() }
        val dotAlpha = remember(plot) {
            val plotWidthDp = (plot.widthPx - plot.insetPx * 2) / density.density
            if (plotWidthDp <= 0f) 1f else ChartLayout.dotAlpha(plot.points.size / plotWidthDp)
        }

        Canvas(
            modifier = Modifier
                .size(
                    width = with(density) { plot.widthPx.toDp() },
                    height = with(density) { plotHeightPx.toDp() },
                ),
        ) {
            val hairline = with(density) { HAIRLINE.toPx() }

            lines.forEach { line ->
                val y = plot.yOf(line.kg.toDouble())
                drawLine(gridColour, Offset(0f, y), Offset(size.width, y), strokeWidth = hairline)
            }

            // Drawn under the readings and the trend, so both cross over it.
            plot.targetY?.let { targetY ->
                drawLine(
                    color = goalColour,
                    start = Offset(0f, targetY),
                    end = Offset(size.width, targetY),
                    strokeWidth = insetPx / 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(insetPx * 2f, insetPx * 2f)),
                )
            }

            plot.points.forEach { point ->
                drawCircle(
                    color = dotColour.copy(alpha = dotAlpha),
                    radius = insetPx,
                    center = Offset(point.x, point.y),
                )
            }

            val path = Path().apply {
                plot.points.forEachIndexed { index, point ->
                    if (index == 0) moveTo(point.x, point.trendY) else lineTo(point.x, point.trendY)
                }
            }
            drawPath(
                path = path,
                color = ink,
                style = Stroke(
                    width = with(density) { LINE_WIDTH.toPx() },
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )

            heldDay?.let { day ->
                val x = plot.xOf(day)
                val y = plot.yOf(plot.dayOn(day).trendKg)
                val ring = with(density) { RING_RADIUS.toPx() }
                drawLine(captionInk, Offset(x, 0f), Offset(x, size.height), strokeWidth = hairline)
                drawCircle(color = page, radius = ring, center = Offset(x, y))
                drawCircle(
                    color = ink,
                    radius = ring,
                    center = Offset(x, y),
                    style = Stroke(width = with(density) { LINE_WIDTH.toPx() }),
                )
            }
        }

        // The gridline labels, each centred on its line and kept inside the plot's height.
        lines.forEach { line ->
            Text(
                text = line.label,
                style = labelStyle,
                color = captionInk,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val top = (plot.yOf(line.kg.toDouble()) - lineHeightPx / 2f)
                            .coerceIn(0f, (plotHeightPx - lineHeightPx).coerceAtLeast(0f))
                        IntOffset(0, top.roundToInt())
                    },
            )
        }

        // The goal's own label, at the left, just above its line — or just below when above would
        // leave the plot.
        val targetY = plot.targetY
        if (goalLabel != null && targetY != null) {
            Text(
                text = goalLabel,
                style = labelStyle,
                color = goalColour,
                maxLines = 1,
                modifier = Modifier.offset {
                    val above = targetY - lineHeightPx - gapPx / 2f
                    val top = if (above >= 0f) above else targetY + gapPx / 2f
                    IntOffset(insetPx.roundToInt(), top.roundToInt())
                },
            )
        }

        val ticks = remember(plot, labelStyle, measurer) {
            ChartLayout.dateTicks(
                firstDay = plot.firstDay,
                lastDay = plot.lastDay,
                xOf = plot::xOf,
                widthOf = { measurer.measure(it, labelStyle).size.width.toFloat() },
                gapPx = gapPx,
            )
        }
        ticks.forEach { tick ->
            Text(
                text = tick.label,
                style = labelStyle,
                color = captionInk,
                maxLines = 1,
                modifier = Modifier.offset {
                    IntOffset(tick.leftPx.roundToInt(), (plotHeightPx + gapPx).roundToInt())
                },
            )
        }

        // Last, so it is on top of everything above: the whole area can be held, the gutter and
        // the dates included, and a finger past either end reads that end.
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(plot) {
                    holdAndDrag(
                        plot = plot,
                        readingDays = readingDays,
                        onDay = onHold,
                        onTick = { haptics.performHapticFeedback(Feel.Tick) },
                    )
                },
        )
    }
}

/**
 * Hold, then drag: [onDay] follows the finger day by day, [onTick] fires each time it reaches a day
 * with a reading, and [onDay] gets null when the finger lifts or the gesture is taken away.
 *
 * Only after a long press, so that a swipe up the weight screen still scrolls and a tap on it still
 * opens the bigger chart. Everything after the hold is consumed — the lift included — so the tap
 * that opens the bigger chart does not also fire when a hold ends.
 */
private suspend fun PointerInputScope.holdAndDrag(
    plot: ChartGeometry,
    readingDays: Set<Long>,
    onDay: (Long?) -> Unit,
    onTick: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
        var day: Long? = null
        fun reach(x: Float) {
            val next = plot.dayAt(x)
            if (ChartLayout.crossesReading(day, next, readingDays)) onTick()
            day = next
            onDay(next)
        }
        try {
            held.consume()
            reach(held.position.x)
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == held.id } ?: break
                change.consume()
                if (!change.pressed) break
                reach(change.position.x)
            }
        } finally {
            onDay(null)
        }
    }
}

/** Tall enough to read a year in: the old 180 dp was what this whole change answers. */
private val CHART_HEIGHT = 260.dp

/** The trend: the heaviest thing on the chart. */
private val LINE_WIDTH = 3.dp

/** Gridlines and the finger's vertical line. */
private val HAIRLINE = 1.dp

/** The ring on the trend at the day a finger holds. */
private val RING_RADIUS = 6.dp

/** Between a label and what it labels, and between two dates. */
private val LABEL_GAP = 4.dp
