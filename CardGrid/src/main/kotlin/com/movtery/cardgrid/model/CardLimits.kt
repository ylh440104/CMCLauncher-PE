package com.movtery.cardgrid.model

import com.movtery.cardgrid.model.CardLimits.Companion.DEFAULT

data class CardLimits(
    val minWidth: Int = DEFAULT_MIN_SPAN,
    val minHeight: Int = DEFAULT_MIN_SPAN,
    val maxWidth: Int = Int.MAX_VALUE,
    val maxHeight: Int = Int.MAX_VALUE
) {

    fun clampedFor(columns: Int): CardLimits {
        val maxW = maxWidth.coerceAtMost(columns)
        val minW = minWidth.coerceAtMost(maxW)
        val maxH = maxHeight
        val minH = minHeight.coerceAtMost(maxH)
        return CardLimits(minWidth = minW, minHeight = minH, maxWidth = maxW, maxHeight = maxH)
    }

    fun clampWidth(width: Int): Int = width.coerceIn(minWidth, maxWidth)

    fun clampHeight(height: Int): Int = height.coerceIn(minHeight, maxHeight)

    companion object {

        const val DEFAULT_MIN_SPAN = 4

        const val DEFAULT_MAX_HEIGHT = 12

        val DEFAULT = CardLimits(maxHeight = DEFAULT_MAX_HEIGHT)
    }
}
