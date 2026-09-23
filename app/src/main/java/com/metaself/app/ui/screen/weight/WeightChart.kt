package com.metaself.app.ui.screen.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.weight.TrendPoint
import com.metaself.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val topLabel: String,
    val bottomLabel: String,
    /** The date of the first reading DRAWN — not of the range asked for. */
    val startLabel: String,
    /** The date of the last reading drawn, or null when everything drawn is one day. */
    val endLabel: String?,
    /** Where the goal weight sits, or null when there is not one, or it is off this view. */
    val targetY: Float? = null,
) {
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
                topLabel = label(heaviest),
                bottomLabel = label(lightest),
                startLabel = date(firstDay, lastDay - firstDay),
                // One day shown is one date. Printing it at both ends would look like a chart
                // covering a stretch of time when it covers a morning.
                endLabel = if (lastDay == firstDay) null else date(lastDay, lastDay - firstDay),
                targetY = goal?.let { y(it) },
            )
        }

        private fun label(kg: Double): String = String.format(Locale.US, "%.1f kg", kg)

        /**
         * One date, in the detail the [spanDays] actually drawn deserves.
         *
         * The rule keys off what is on screen rather than the range chosen, so the whole history of
         * a fortnight reads as a fortnight instead of saying the same month twice.
         */
        private fun date(epochDay: Long, spanDays: Long): String {
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
 * The trend as a line, with the raw readings as dots behind it.
 *
 * The dots stay because a smoothed line with nothing behind it is asking to be taken on trust, and
 * this app does not do that — the same reasoning as decision D9's visible arithmetic.
 *
 * No charting library: a line, some dots and four labels are a hundred lines of Canvas, and a
 * library would be a dependency, a theme to fight and a second opinion about what a chart is. The
 * range chips are the answer to a chart too small to read, not a gesture nobody wrote.
 *
 * [geometry] is the same answer the canvas below will ask for again at real pixel size; it is passed
 * in rather than worked out here so that there is exactly ONE memo of it, in [WeightChartBlock],
 * which is also what has to say when there is nothing to draw.
 *
 * The four labels are Compose text, not canvas paint: paint has no semantics node, so a painted date
 * is a date no screen reader says and no test can see.
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
    val lineColour = MaterialTheme.colorScheme.primary
    val dotColour = MaterialTheme.colorScheme.secondary
    val targetColour = MaterialTheme.colorScheme.tertiary
    val density = LocalDensity.current

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
    ) {
        Text(text = geometry.topLabel, style = MaterialTheme.typography.labelSmall)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (fillsHeight) it.weight(1f) else it.height(CHART_HEIGHT) },
        ) {
            val insetPx = with(density) { DOT_RADIUS.toPx() }
            val plotted = ChartGeometry.of(
                trend = trend,
                widthPx = size.width,
                heightPx = size.height,
                range = range,
                todayEpochDay = todayEpochDay,
                insetPx = insetPx,
                targetKg = targetKg,
            ) ?: return@Canvas

            // Drawn first, so the trend crosses over it rather than being hidden under it.
            plotted.targetY?.let { targetY ->
                drawLine(
                    color = targetColour,
                    start = Offset(0f, targetY),
                    end = Offset(size.width, targetY),
                    strokeWidth = insetPx / 2f,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(insetPx * 2f, insetPx * 2f),
                    ),
                )
            }

            plotted.points.forEach { point ->
                drawCircle(
                    color = dotColour,
                    radius = insetPx,
                    center = Offset(point.x, point.y),
                )
            }

            val path = Path().apply {
                plotted.points.forEachIndexed { index, point ->
                    if (index == 0) moveTo(point.x, point.trendY) else lineTo(point.x, point.trendY)
                }
            }
            drawPath(
                path = path,
                color = lineColour,
                style = Stroke(width = with(density) { LINE_WIDTH.toPx() }),
            )
        }

        Text(text = geometry.bottomLabel, style = MaterialTheme.typography.labelSmall)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = geometry.startLabel, style = MaterialTheme.typography.labelSmall)
            geometry.endLabel?.let { end ->
                Text(text = end, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (onOpen != null) {
            Text(
                text = stringResource(R.string.weight_chart_open),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Tall enough to read a year in: the old 180 dp was what this whole change answers. */
private val CHART_HEIGHT = 260.dp

/** In dp, not raw pixels: four PIXELS is invisible on a phone with three of them to the dp. */
private val DOT_RADIUS = 4.dp
private val LINE_WIDTH = 2.dp
