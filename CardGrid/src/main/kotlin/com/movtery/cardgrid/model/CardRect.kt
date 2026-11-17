package com.movtery.cardgrid.model

import androidx.compose.ui.unit.IntOffset

data class CardRect(
    val id: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height

    fun positionAt(position: IntOffset): CardRect = copy(x = position.x, y = position.y)

    fun intersects(other: CardRect): Boolean =
        x < other.right && other.x < right && y < other.bottom && other.y < bottom
}
