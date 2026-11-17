package com.movtery.guide

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset

enum class GuideSide {
    Above, Below, Start, End
}

sealed class GuidePlacement {

    data object Auto : GuidePlacement()

    data class Fixed(
        val alignment: Alignment,
        val offset: IntOffset = IntOffset.Zero
    ) : GuidePlacement()
}

internal data class PreferSide(val side: GuideSide) : GuidePlacement()
