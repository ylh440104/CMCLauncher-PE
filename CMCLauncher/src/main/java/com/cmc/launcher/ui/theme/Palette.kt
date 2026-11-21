package com.cmc.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import com.cmc.launcher.setting.enums.isLauncherInDarkTheme
import com.cmc.launcher.ui.components.influencedByBackgroundColor

fun ColorScheme.withQuietSurfaces(dark: Boolean = surface.luminance() < 0.5f): ColorScheme {
    val tone = if (dark) 0f else 1f
    fun quiet(color: Color, weight: Float): Color = lerp(
        color,
        Color(tone, tone, tone),
        weight.coerceIn(0f, 1f)
    )
    return copy(
        background = quiet(background, 0.94f),
        surface = quiet(surface, 0.94f),
        surfaceDim = quiet(surfaceDim, 0.9f),
        surfaceBright = quiet(surfaceBright, 0.9f),
        surfaceContainerLowest = quiet(surfaceContainerLowest, 0.93f),
        surfaceContainerLow = quiet(surfaceContainerLow, 0.92f),
        surfaceContainer = quiet(surfaceContainer, 0.9f),
        surfaceContainerHigh = quiet(surfaceContainerHigh, 0.88f),
        surfaceContainerHighest = quiet(surfaceContainerHighest, 0.86f),
        surfaceVariant = quiet(surfaceVariant, 0.82f),
        outline = quiet(outline, 0.55f),
        outlineVariant = quiet(outlineVariant, 0.75f)
    )
}

private fun Color.luminance(): Float {
    val c = convert(ColorSpaces.Srgb)
    fun channel(v: Float) = if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * channel(c.red) + 0.7152f * channel(c.green) + 0.0722f * channel(c.blue)
}

@Composable
fun backgroundColor(): Color = MaterialTheme.colorScheme.surface
@Composable
fun onBackgroundColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun cardColor(
    influencedByBackground: Boolean = true
): Color = influencedByBackgroundColor(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    enabled = influencedByBackground
)
@Composable
fun onCardColor(): Color = MaterialTheme.colorScheme.onSurface

@Composable
fun cardTitleColor(
    alpha: Float = 0.5f
): Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)

@Composable
fun itemColor(
    influencedByBackground: Boolean = true,
    isDark: Boolean = isLauncherInDarkTheme()
): Color {
    return influencedByBackgroundColor(
        color = if (isDark) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        },
        enabled = influencedByBackground
    )
}
@Composable
fun onItemColor() = MaterialTheme.colorScheme.onSurface
