package com.movtery.layer_controller.data

import androidx.compose.foundation.shape.RoundedCornerShape
import com.movtery.layer_controller.observable.Modifiable
import com.movtery.layer_controller.utils.checkInRange
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

val SHAPE_RANGE: ClosedFloatingPointRange<Float> = 0.0f..100.0f

@Serializable
data class ButtonShape(
    @SerialName("topStart")
    val topStart: Float,
    @SerialName("topEnd")
    val topEnd: Float,
    @SerialName("bottomEnd")
    val bottomEnd: Float,
    @SerialName("bottomStart")
    val bottomStart: Float
): Modifiable<ButtonShape> {
    constructor(size: Float) : this(size, size, size, size)

    init {
        checkInRange("topStart", topStart, SHAPE_RANGE)
        checkInRange("topEnd", topEnd, SHAPE_RANGE)
        checkInRange("bottomEnd", bottomEnd, SHAPE_RANGE)
        checkInRange("bottomStart", bottomStart, SHAPE_RANGE)
    }

    override fun isModified(other: ButtonShape): Boolean {
        return this.topStart != other.topStart ||
                this.topEnd != other.topEnd ||
                this.bottomEnd != other.bottomEnd ||
                this.bottomStart != other.bottomStart
    }
}

fun ButtonShape.toAndroidShape() = RoundedCornerShape(
    topStart = topStart,
    topEnd = topEnd,
    bottomEnd = bottomEnd,
    bottomStart = bottomStart
)
