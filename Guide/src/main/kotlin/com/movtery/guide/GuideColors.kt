package com.movtery.guide

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class GuideColors(

    val scrim: Color = Color.Black.copy(alpha = 0.5f),

    val content: Color = Color.White
)
