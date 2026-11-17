package com.cmc.launcher.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class ScreenType {

    Watch,

    Tablet,

    Phone
}

@Composable
fun ScreenTypeLayout(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    content: @Composable BoxWithConstraintsScope.(ScreenType) -> Unit
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = contentAlignment,
        propagateMinConstraints = propagateMinConstraints
    ) {
        val screenType = when {
            maxWidth < 800.dp && maxHeight < 500.dp &&
                    maxWidth / maxHeight in 0.75f..1.6f -> ScreenType.Watch
            maxWidth >= 800.dp && maxHeight >= 500.dp -> ScreenType.Tablet
            else -> ScreenType.Phone
        }
        content(screenType)
    }
}
