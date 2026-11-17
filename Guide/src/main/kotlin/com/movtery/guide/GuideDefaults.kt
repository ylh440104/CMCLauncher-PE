package com.movtery.guide

import androidx.compose.animation.core.Spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object GuideDefaults {

    val colors = GuideColors()

    val holeRadius: Dp = 8.dp

    val holeBorder: Color = Color.White

    val nextTipPadding: Dp = 24.dp

    val nextTipMargin: Dp = 16.dp

    val contentGap: Dp = 12.dp

    val screenPadding: Dp = 16.dp

    val contentShape: Shape = RoundedCornerShape(28.dp)

    val expressiveSpatial = GuideSpring(dampingRatio = 0.9f, stiffness = 380f)

    val expressiveFade = GuideSpring(dampingRatio = 1f, stiffness = Spring.StiffnessLow)
}

@Immutable
data class GuideSpring(

    val dampingRatio: Float,

    val stiffness: Float
)

@Immutable
class GuideAnimations(

    val enabled: Boolean = true,

    val spatialSpring: GuideSpring = GuideDefaults.expressiveSpatial,

    val fadeSpring: GuideSpring = GuideDefaults.expressiveFade
) {
    companion object {
        val Default = GuideAnimations()

        val None = GuideAnimations(enabled = false)
    }
}
