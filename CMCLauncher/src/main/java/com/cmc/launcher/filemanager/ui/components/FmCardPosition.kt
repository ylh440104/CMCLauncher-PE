package com.cmc.launcher.filemanager.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@ConsistentCopyVisibility
data class FmCardPosition private constructor(
    val outerTopStart: Boolean,
    val outerTopEnd: Boolean,
    val outerBottomStart: Boolean,
    val outerBottomEnd: Boolean
) {
    companion object {

        val Single = FmCardPosition(
            outerTopStart = true,
            outerTopEnd = true,
            outerBottomStart = true,
            outerBottomEnd = true
        )

        fun of(index: Int, count: Int, columns: Int = 1): FmCardPosition {
            if (count <= 1) return Single
            val cols = columns.coerceAtLeast(1)

            if (count <= cols) {
                val start = index == 0
                val end = index == count - 1
                return FmCardPosition(
                    outerTopStart = start,
                    outerTopEnd = end,
                    outerBottomStart = start,
                    outerBottomEnd = end
                )
            }
            val row = index / cols
            val col = index % cols
            val lastRow = (count - 1) / cols
            val lastCol = (count - 1) % cols

            val top = row == 0
            val start = col == 0

            val end = col == cols - 1 || (row == lastRow && col == lastCol)

            val bottom = row == lastRow || (row == lastRow - 1 && col > lastCol)

            return FmCardPosition(
                outerTopStart = top && start,
                outerTopEnd = top && end,
                outerBottomStart = bottom && start,
                outerBottomEnd = bottom && end
            )
        }
    }
}

@Composable
fun rememberFmCardShape(
    position: FmCardPosition,
    outerShape: Dp = 28.dp,
    innerShape: Dp = 4.dp
): Shape {
    return remember(position, outerShape, innerShape) {
        RoundedCornerShape(
            topStart = if (position.outerTopStart) outerShape else innerShape,
            topEnd = if (position.outerTopEnd) outerShape else innerShape,
            bottomStart = if (position.outerBottomStart) outerShape else innerShape,
            bottomEnd = if (position.outerBottomEnd) outerShape else innerShape
        )
    }
}
