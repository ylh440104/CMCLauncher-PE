package com.movtery.cardgrid.model

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class CardInteraction { Idle, Adjusting, Dragging, Resizing }

class CardState(
    val spanWidth: Int,
    val spanHeight: Int,
    val columns: Int,
    val interaction: CardInteraction
) {

    val sizeClass: CardSize = deriveSizeClass(spanWidth, spanHeight, columns)
}

val CardSpacing: Dp = 6.dp
