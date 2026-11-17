package com.movtery.layer_controller.data

import com.movtery.layer_controller.observable.Modifiable
import com.movtery.layer_controller.utils.checkInRange
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

val POSITION_RANGE: IntRange = 0..10000

@Serializable
data class ButtonPosition(
    @SerialName("x")
    val x: Int,
    @SerialName("y")
    val y: Int
): Modifiable<ButtonPosition> {
    init {
        checkInRange("x", x, POSITION_RANGE)
        checkInRange("y", y, POSITION_RANGE)
    }

    fun xPercentage(): Float {
        return (x / 10000f).coerceAtMost(1f).coerceAtLeast(0f)
    }

    fun yPercentage(): Float {
        return (y / 10000f).coerceAtMost(1f).coerceAtLeast(0f)
    }

    override fun isModified(other: ButtonPosition): Boolean {
        return this.x != other.x ||
                this.y != other.y
    }
}

val TopStartPosition = ButtonPosition(0, 0)

val TopEndPosition = ButtonPosition(10000, 0)

val CenterPosition = ButtonPosition(5000, 5000)

val BottomStartPosition = ButtonPosition(0, 10000)

val BottomEndPosition = ButtonPosition(10000, 10000)
