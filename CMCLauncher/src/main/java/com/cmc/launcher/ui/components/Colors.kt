package com.cmc.launcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.viewmodel.influencedByBackground

fun Color.desaturate(factor: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[1] *= factor.coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
fun influencedByBackgroundColor(
    color: Color,
    influencedAlpha: Float = AllSettings.launcherBackgroundOpacity.state.toFloat() / 100f,
    enabled: Boolean = true
): Color {
    return influencedByBackground(
        value = color,
        influenced = color.copy(alpha = influencedAlpha),
        enabled = enabled
    )
}
