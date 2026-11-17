package com.movtery.layer_controller.data

import com.movtery.layer_controller.data.ButtonSize.Reference
import com.movtery.layer_controller.data.ButtonSize.Type
import com.movtery.layer_controller.observable.Modifiable
import com.movtery.layer_controller.utils.checkInRange
import com.movtery.layer_controller.utils.checkMin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val MIN_SIZE_DP = 5.0f

internal const val MIN_SIZE_PERCENTAGE = 100

internal const val MAX_SIZE_PERCENTAGE = 10000

val SIZE_PERCENTAGE: ClosedFloatingPointRange<Float> = 100.0f..10000.0f

val SIZE_PERCENTAGE_EDITOR: ClosedFloatingPointRange<Float> = 1.0f..100.0f

@Serializable
data class ButtonSize(
    @SerialName("type")
    val type: Type,
    @SerialName("widthDp")
    val widthDp: Float,
    @SerialName("heightDp")
    val heightDp: Float,
    @SerialName("widthPercentage")
    val widthPercentage: Int,
    @SerialName("heightPercentage")
    val heightPercentage: Int,
    @SerialName("widthReference")
    val widthReference: Reference,
    @SerialName("heightReference")
    val heightReference: Reference
): Modifiable<ButtonSize> {
    init {
        checkMin("widthDp", widthDp, MIN_SIZE_DP)
        checkMin("heightDp", heightDp, MIN_SIZE_DP)
        checkInRange("widthPercentage", widthPercentage.toFloat(), SIZE_PERCENTAGE)
        checkInRange("heightPercentage", heightPercentage.toFloat(), SIZE_PERCENTAGE)
    }

    @Serializable
    enum class Type {

        @SerialName("dp") Dp,

        @SerialName("percentage") Percentage,

        @SerialName("wrap_content") WrapContent
    }

    @Serializable
    enum class Reference {

        @SerialName("screen_width") ScreenWidth,

        @SerialName("screen_height") ScreenHeight,
    }

    override fun isModified(other: ButtonSize): Boolean {
        return this.type != other.type ||
                this.widthDp != other.widthDp ||
                this.heightDp != other.heightDp ||
                this.widthPercentage != other.widthPercentage ||
                this.heightPercentage != other.heightPercentage ||
                this.widthReference != other.widthReference ||
                this.heightReference != other.heightReference
    }
}

val DefaultSize = ButtonSize(
    type = Type.Percentage,
    widthDp = 50f,
    heightDp = 50f,
    widthPercentage = 1400,
    heightPercentage = 1400,
    widthReference = Reference.ScreenHeight,
    heightReference = Reference.ScreenHeight
)

fun createAdaptiveButtonSize(
    referenceLength: Int,
    type: Type = Type.Percentage,
    reference: Reference = Reference.ScreenHeight,
    density: Float = 1f,
    targetDpSize: Float = 50f
): ButtonSize {
    val percentage = ((targetDpSize * density) / referenceLength * 10000).toInt()

    return ButtonSize(
        type = type,
        widthDp = targetDpSize,
        heightDp = targetDpSize,
        widthPercentage = percentage,
        heightPercentage = percentage,
        widthReference = reference,
        heightReference = reference
    )
}
