package com.movtery.cardgrid.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.IntOffset

typealias CardContent = @Composable CardState.(cardId: String) -> Unit

class CardType(
    val typeId: String,
    val defaultSpan: IntOffset,
    val limits: CardLimits = CardLimits.DEFAULT,
    val shape: Shape? = null,
    val content: CardContent
)
