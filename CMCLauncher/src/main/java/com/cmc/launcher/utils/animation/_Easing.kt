package com.cmc.launcher.utils.animation

import androidx.compose.animation.core.Easing
import kotlin.math.cos
import kotlin.math.pow

class EaseOutFluent(private val power: Int) : Easing {
    override fun transform(fraction: Float): Float = 1f - (1f - fraction).coerceIn(0f, 1f).pow(power)
}

class EaseOutBack(power: Int) : Easing {
    private val exponent = 3f - power * 0.5f

    override fun transform(fraction: Float): Float {
        val t = fraction.coerceIn(0f, 1f)
        return 1f - (1f - t).pow(exponent) * cos(1.5f * Math.PI * t).toFloat()
    }
}

val EnterTranslateEasing = Easing { fraction ->

    val first = 5f * EaseOutFluent(3).transform((fraction * 350f / 250f).coerceAtMost(1f))

    val second = 11f * EaseOutBack(3).transform(fraction)
    (first + second) / 16f
}
