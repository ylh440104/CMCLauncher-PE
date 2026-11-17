package com.movtery.cardgrid.model

import kotlin.math.roundToInt

const val MIN_GRID_COLUMNS = 4

const val DEFAULT_TARGET_CELL_SIZE = 20f

data class GridGeometry(
    val columns: Int,
    val cellSize: Float
)

fun computeGridGeometry(
    widthDp: Float,
    targetCellSize: Float = DEFAULT_TARGET_CELL_SIZE
): GridGeometry {
    if (widthDp <= 0f) return GridGeometry(MIN_GRID_COLUMNS, targetCellSize)
    val evenColumns = (widthDp / targetCellSize / 2f)
        .roundToInt()
        .coerceAtLeast(MIN_GRID_COLUMNS / 2) * 2
    return GridGeometry(columns = evenColumns, cellSize = widthDp / evenColumns)
}
