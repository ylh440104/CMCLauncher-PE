package com.cmc.launcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

@Composable
fun Modifier.infiniteShimmer(
    initialValue: Float = 0.3f,
    targetValue: Float = 0.6f
): Modifier = this.alpha(initialValue)