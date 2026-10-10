package com.metaself.app.ui.screen.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metaself.app.domain.trainer.MonthBlock
import com.metaself.app.domain.trainer.MonthWeek
import com.metaself.app.domain.trainer.PlanMonth
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.GoalWording

/** The bars' box: the tallest bar fills it. A drawing size, not a measurement. */
private val BAR_BOX = 64.dp
private val BAR_WIDTH = 20.dp
private val DONE_WIDTH = 10.dp
private val BAR_GAP = 6.dp

/**
 * D123: the Month view — the goal line, then one card per block: its phase and focus, its weeks as bars of their
 * target minutes (an easier week lighter; a past week's minutes done drawn over its target, in the green), and each
 * bar's figures in words under it, so no figure is only a height or a colour. Colours are the theme's own — `outline`,
 * `outlineVariant`, `primary` — none new. Drawn into the caller's column, after its [FromTrainerLabel].
 */
@Composable
internal fun PlanMonthView(month: PlanMonth, today: Long) {
    GoalWording.goalLine(month.goal, today)?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
    month.blocks.forEach { BlockRow(it, month.scale) }
}

@Composable
private fun BlockRow(block: MonthBlock, scale: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Text(GoalWording.blockTitle(block.phase, block.focus), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(BAR_GAP), verticalAlignment = Alignment.Bottom) {
                block.weeks.forEach { Bar(it, scale) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                block.weeks.forEach { week ->
                    Text(
                        GoalWording.barWords(week),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (week.here) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** A target bar, its height its minutes against [scale]; a past week's done minutes over it, narrower, in the green. */
@Composable
private fun Bar(week: MonthWeek, scale: Int) {
    val target = if (week.target.easier) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(4.dp)
    Box(Modifier.width(BAR_WIDTH).height(BAR_BOX), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.width(BAR_WIDTH).height(heightOf(week.target.minutes, scale)).background(target, shape))
        week.done?.let { done ->
            Box(Modifier.width(DONE_WIDTH).height(heightOf(done, scale)).background(MaterialTheme.colorScheme.primary, shape))
        }
        if (week.here) {
            Box(Modifier.width(BAR_WIDTH).height(3.dp).align(Alignment.BottomCenter).background(MaterialTheme.colorScheme.primary))
        }
    }
}

private fun heightOf(minutes: Int, scale: Int) = if (scale <= 0) 0.dp else BAR_BOX * (minutes.coerceAtMost(scale).toFloat() / scale)

