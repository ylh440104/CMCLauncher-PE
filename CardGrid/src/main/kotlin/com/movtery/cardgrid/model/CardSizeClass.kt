package com.movtery.cardgrid.model

enum class CardSizeClass(val maxSpan: Int) {
    COMPACT(4),
    SMALL(5),
    MEDIUM(7),
    LARGE(9),
    EXTRA_LARGE(Int.MAX_VALUE);

    companion object {

        fun fromSpan(span: Int): CardSizeClass =
            entries.first { span <= it.maxSpan }

        fun fromFraction(width: Int, columns: Int): CardSizeClass {
            val classes = entries
            if (columns <= 0 || width <= 0) return COMPACT
            val fraction = width / columns.toFloat()
            val index = (fraction * classes.size).toInt().coerceIn(0, classes.lastIndex)
            return classes[index]
        }
    }
}

data class CardSize(
    val width: CardSizeClass,
    val height: CardSizeClass
)

fun deriveSizeClass(
    width: Int,
    height: Int,
    columns: Int
): CardSize = CardSize(
    width = CardSizeClass.fromFraction(width, columns),
    height = CardSizeClass.fromSpan(height)
)
