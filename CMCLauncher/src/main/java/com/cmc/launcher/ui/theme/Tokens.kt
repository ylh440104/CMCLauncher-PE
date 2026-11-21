package com.cmc.launcher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Space {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp

    val screenH: Dp = 16.dp
    val screenV: Dp = 12.dp
    val sectionGap: Dp = 20.dp
    val cardInner: Dp = 14.dp
    val rowGap: Dp = 10.dp
}

object Radius {
    val tiny: Dp = 6.dp
    val small: Dp = 10.dp
    val medium: Dp = 14.dp
    val large: Dp = 20.dp
    val xlarge: Dp = 26.dp
    val pill: Dp = 999.dp
}

object Alpha {
    const val hairline = 0.08f
    const val faint = 0.14f
    const val soft = 0.24f
    const val medium = 0.42f
    const val strong = 0.62f
    const val heavy = 0.80f
}

object Size {
    val iconXs: Dp = 16.dp
    val iconSm: Dp = 20.dp
    val iconMd: Dp = 24.dp
    val iconLg: Dp = 30.dp
    val iconXl: Dp = 40.dp

    val touch: Dp = 48.dp
    val row: Dp = 52.dp
    val swatch: Dp = 40.dp
    val stroke: Dp = 1.dp
    val strokeBold: Dp = 2.dp
}

val CardShape = RoundedCornerShape(Radius.large)
val PanelShape = RoundedCornerShape(Radius.xlarge)
val FieldShape = RoundedCornerShape(Radius.medium)
val PillShape = RoundedCornerShape(Radius.pill)
val TagShape = RoundedCornerShape(Radius.tiny)