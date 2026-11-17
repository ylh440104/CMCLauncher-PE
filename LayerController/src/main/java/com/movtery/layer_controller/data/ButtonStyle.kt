package com.movtery.layer_controller.data

import androidx.compose.ui.graphics.Color
import com.movtery.layer_controller.data.ButtonStyle.StyleConfig
import com.movtery.layer_controller.observable.Modifiable
import com.movtery.layer_controller.utils.checkInRange
import com.movtery.layer_controller.utils.randomUUID
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

val ALPHA_RANGE: ClosedFloatingPointRange<Float> = 0.0f..1.0f

val FONT_SIZE_RANGE: ClosedFloatingPointRange<Float> = 2.0f..30.0f

const val DEFAULT_FONT_SIZE = 14

val BORDER_WIDTH: ClosedFloatingPointRange<Float> = 0.0f..50.0f

@Serializable
data class ButtonStyle(
    @SerialName("name")
    val name: String,
    @SerialName("uuid")
    val uuid: String,
    @SerialName("animateSwap")
    val animateSwap: Boolean,
    @SerialName("commonStyle")
    val commonStyle: Boolean = true,
    @SerialName("lightStyle")
    val lightStyle: StyleConfig,
    @SerialName("darkStyle")
    val darkStyle: StyleConfig
): Modifiable<ButtonStyle> {

    @Serializable
    data class StyleConfig(
        @SerialName("alpha")
        val alpha: Float,
        @SerialName("pressedAlpha")
        val pressedAlpha: Float,
        @SerialName("backgroundColor")
        @Contextual val backgroundColor: Color,
        @SerialName("pressedBackgroundColor")
        @Contextual val pressedBackgroundColor: Color,
        @SerialName("contentColor")
        @Contextual val contentColor: Color,
        @SerialName("pressedContentColor")
        @Contextual val pressedContentColor: Color,
        @SerialName("fontSize")
        val fontSize: Int? = null,
        @SerialName("pressedFontSize")
        val pressedFontSize: Int? = null,
        @SerialName("borderWidth")
        val borderWidth: Int,
        @SerialName("pressedBorderWidth")
        val pressedBorderWidth: Int,
        @SerialName("borderColor")
        @Contextual val borderColor: Color,
        @SerialName("pressedBorderColor")
        @Contextual val pressedBorderColor: Color,
        @SerialName("borderRadius")
        val borderRadius: ButtonShape,
        @SerialName("pressedBorderRadius")
        val pressedBorderRadius: ButtonShape
    ): Modifiable<StyleConfig> {
        init {
            checkInRange("alpha", alpha, ALPHA_RANGE)
            checkInRange("pressedAlpha", pressedAlpha, ALPHA_RANGE)
            if (fontSize != null) {
                checkInRange("fontSize", fontSize.toFloat(), FONT_SIZE_RANGE)
            }
            if (pressedFontSize != null) {
                checkInRange("pressedFontSize", pressedFontSize.toFloat(), FONT_SIZE_RANGE)
            }
            checkInRange("borderWidth", borderWidth.toFloat(), BORDER_WIDTH)
            checkInRange("pressedBorderWidth", pressedBorderWidth.toFloat(), BORDER_WIDTH)
        }

        override fun isModified(other: StyleConfig): Boolean {
            return this.alpha != other.alpha ||
                    this.pressedAlpha != other.pressedAlpha ||
                    this.backgroundColor != other.backgroundColor ||
                    this.pressedBackgroundColor != other.pressedBackgroundColor ||
                    this.contentColor != other.contentColor ||
                    this.pressedContentColor != other.pressedContentColor ||
                    this.borderWidth != other.borderWidth ||
                    this.pressedBorderWidth != other.pressedBorderWidth ||
                    this.borderColor != other.borderColor ||
                    this.pressedBorderColor != other.pressedBorderColor ||
                    this.borderRadius.isModified(other.borderRadius) ||
                    this.pressedBorderRadius.isModified(other.pressedBorderRadius)
        }
    }

    override fun isModified(other: ButtonStyle): Boolean {
        return this.name != other.name ||
                this.uuid != other.uuid ||
                this.animateSwap != other.animateSwap ||
                this.commonStyle != other.commonStyle ||
                this.lightStyle.isModified(other.lightStyle) ||
                this.darkStyle.isModified(other.darkStyle)
    }
}

val DefaultButtonStyleConfig = StyleConfig(
    alpha = 1f,
    backgroundColor = Color.Black.copy(alpha = 0.5f),
    contentColor = Color.White,
    fontSize = null,
    borderWidth = 0,
    borderColor = Color.White,
    borderRadius = ButtonShape(0f),
    pressedAlpha = 1f,
    pressedBackgroundColor = Color.Gray.copy(alpha = 0.7f),
    pressedContentColor = Color.White,
    pressedFontSize = null,
    pressedBorderWidth = 0,
    pressedBorderColor = Color.White,
    pressedBorderRadius = ButtonShape(0f)
)

val DefaultButtonStyle = ButtonStyle(
    name = "Default",
    uuid = randomUUID(),
    animateSwap = false,
    commonStyle = true,
    lightStyle = DefaultButtonStyleConfig,
    darkStyle = DefaultButtonStyleConfig
)

fun createNewButtonStyle(name: String): ButtonStyle = DefaultButtonStyle.copy(name = name, uuid = randomUUID())

fun ButtonStyle.cloneNew(): ButtonStyle = ButtonStyle(
    name = name,
    uuid = randomUUID(),
    animateSwap = animateSwap,
    commonStyle = commonStyle,
    lightStyle = lightStyle,
    darkStyle = darkStyle
)
