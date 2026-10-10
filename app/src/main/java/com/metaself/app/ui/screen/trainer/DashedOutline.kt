package com.metaself.app.ui.screen.trainer

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * D114, D117: an open planned session's outline — dashed, in [color], inset by half its stroke so no edge is
 * clipped. The week strip's "To do" blocks and the plan screen's open tiles share it.
 */
internal fun Modifier.dashedOutline(color: Color, corner: Dp, stroke: Dp = 2.dp, dash: Dp = 4.dp, gap: Dp = 3.dp): Modifier =
    drawBehind {
        val width = stroke.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(width / 2, width / 2),
            size = Size(size.width - width, size.height - width),
            cornerRadius = CornerRadius(corner.toPx()),
            style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()))),
        )
    }
