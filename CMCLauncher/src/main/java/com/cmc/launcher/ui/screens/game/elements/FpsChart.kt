package com.cmc.launcher.ui.screens.game.elements

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val FPS_SEGMENTS = 5

private val FPS_AXIS_STEPS = intArrayOf(1, 2, 5, 10, 15, 20, 30, 60, 120, 240)

private fun niceFpsBounds(fpsMin: Int, fpsMax: Int): Pair<Int, Int> {
    val range = (fpsMax - fpsMin).coerceAtLeast(0)
    for (step in FPS_AXIS_STEPS) {
        val span = step * FPS_SEGMENTS
        if (span < range) continue
        val extra = span - range

        val raw = fpsMin - extra / 2
        var axisMin = raw / step * step
        if (raw < 0 && raw % step != 0) axisMin -= step
        var axisMax = axisMin + span
        if (axisMax < fpsMax) {
            axisMax = (fpsMax + step - 1) / step * step
            axisMin = axisMax - span
        }
        if (axisMin < 0) {
            axisMin = 0
            axisMax = span
        }
        return axisMin to axisMax
    }
    return fpsMin to fpsMax
}

private val FpsLabelStyle = TextStyle(fontSize = 9.sp)

private val FpsLabelHeight = 12.dp

@Composable
fun FpsChart(
    history: List<Int>,
    fpsMax: Int,
    fpsMin: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density = density.density, fontScale = 1f)
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val lineColor = primaryColor.copy(alpha = 0.3f)
        val labelColor = LocalContentColor.current
        val textMeasurer = rememberTextMeasurer()

        val bounds = remember(fpsMax, fpsMin) { niceFpsBounds(fpsMin, fpsMax) }
        val ticks = remember(bounds) {
            val step = (bounds.second - bounds.first) / FPS_SEGMENTS
            (0..FPS_SEGMENTS).map { bounds.first + step * it }
        }

        Row(
            modifier = modifier
                .width(170.dp)
                .height(95.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {

            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                ticks.reversed().forEach { tick ->
                    Box(
                        modifier = Modifier.height(FpsLabelHeight),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = tick.toString(),
                            style = FpsLabelStyle,
                            color = LocalContentColor.current
                        )
                    }
                }
            }

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                drawFpsChart(
                    history = history,
                    axisMin = bounds.first,
                    axisMax = bounds.second,
                    curveColor = primaryColor,
                    lineColor = lineColor,
                    labelColor = labelColor,
                    textMeasurer = textMeasurer
                )
            }
        }
    }
}

private fun DrawScope.drawFpsChart(
    history: List<Int>,
    axisMin: Int,
    axisMax: Int,
    curveColor: Color,
    lineColor: Color,
    labelColor: Color,
    textMeasurer: TextMeasurer
) {
    val pointRadius = 1.25.dp.toPx()
    val gridStroke = 1.dp.toPx()

    val insetX = pointRadius + gridStroke / 2f
    val insetY = FpsLabelHeight.toPx() / 2f
    val axisRange = axisMax - axisMin

    for (k in 0..FPS_SEGMENTS) {
        val fraction = k / FPS_SEGMENTS.toFloat()
        val y = insetY + fraction * (size.height - insetY * 2)
        drawLine(
            color = lineColor,
            start = Offset(insetX, y),
            end = Offset(size.width, y),
            strokeWidth = gridStroke
        )
    }

    drawLine(
        color = lineColor,
        start = Offset(insetX, insetY),
        end = Offset(insetX, size.height - insetY),
        strokeWidth = gridStroke
    )

    if (history.isEmpty()) return

    val points = history.mapIndexed { index, fps ->
        val x = if (history.size == 1) {
            (insetX + size.width) / 2f
        } else {
            insetX + index * (size.width - insetX * 2) / (history.size - 1)
        }
        val y = insetY + (1f - (fps - axisMin).toFloat() / axisRange) * (size.height - insetY * 2)
        Offset(x, y)
    }

    val path = Path().apply {
        moveTo(points.first().x, points.first().y)

        for (i in 1 until points.size) {
            val start = points[i - 1]
            val end = points[i]
            val offset = (end.x - start.x) * 0.25f
            cubicTo(
                x1 = start.x + offset,
                y1 = start.y,
                x2 = end.x - offset,
                y2 = end.y,
                x3 = end.x,
                y3 = end.y
            )
        }
    }
    drawPath(
        path = path,
        color = curveColor,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    )
    points.forEach { point ->
        drawCircle(color = curveColor, radius = pointRadius, center = point)
    }

    val last = points.last()
    val measured = textMeasurer.measure(text = history.last().toString(), style = FpsLabelStyle)
    val gap = 2.dp.toPx()
    val canBelow = last.y + gap + measured.size.height <= size.height - insetY
    val canAbove = last.y - gap - measured.size.height >= insetY
    val prevY = points.getOrNull(points.size - 2)?.y
    val placeBelow = when {
        canBelow && !canAbove -> true
        !canBelow && canAbove -> false
        prevY != null && prevY < last.y -> true
        prevY != null && prevY > last.y -> false
        else -> last.y <= size.height / 2f
    }
    val topLeft = if (placeBelow) {
        Offset(size.width - measured.size.width, last.y + gap)
    } else {
        Offset(size.width - measured.size.width, last.y - gap - measured.size.height)
    }
    drawText(textLayoutResult = measured, color = labelColor, topLeft = topLeft)
}
