package com.movtery.layer_controller.data

import androidx.compose.ui.graphics.Color
import com.movtery.layer_controller.observable.Modifiable
import com.movtery.layer_controller.utils.checkInRange
import com.movtery.layer_controller.utils.randomUUID
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

val SHAPE_PERCENT_RANGE: IntRange = 0..50

val SIZE_PERCENT_RANGE: ClosedFloatingPointRange<Float> = 0.0f..1.0f

val BORDER_RADIO_RANGE: IntRange = 0..50

@Serializable
data class JoystickStyle(
    @SerialName("name")
    val name: String,
    @SerialName("uuid")
    val uuid: String,
    @SerialName("commonStyle")
    val commonStyle: Boolean = true,
    @SerialName("lightStyle")
    val lightStyle: StyleConfig,
    @SerialName("darkStyle")
    val darkStyle: StyleConfig
): Modifiable<JoystickStyle> {

    @Serializable
    data class StyleConfig(
        @SerialName("alpha")
        val alpha: Float,
        @SerialName("backgroundColor")
        @Contextual val backgroundColor: Color,
        @SerialName("joystickColor")
        @Contextual val joystickColor: Color,
        @SerialName("joystickCanLockColor")
        @Contextual val joystickCanLockColor: Color,
        @SerialName("joystickLockedColor")
        @Contextual val joystickLockedColor: Color,
        @SerialName("lockMarkColor")
        @Contextual val lockMarkColor: Color,
        @SerialName("borderWidthRatio")
        val borderWidthRatio: Int = 0,
        @SerialName("borderColor")
        @Contextual val borderColor: Color,
        @SerialName("backgroundShape")
        val backgroundShape: Int,
        @SerialName("joystickShape")
        val joystickShape: Int,
        @SerialName("joystickSize")
        val joystickSize: Float
    ): Modifiable<StyleConfig> {
        init {
            checkInRange("alpha", alpha, ALPHA_RANGE)
            checkInRange("backgroundShape", backgroundShape, SHAPE_PERCENT_RANGE)
            checkInRange("joystickShape", joystickShape, SHAPE_PERCENT_RANGE)
            checkInRange("joystickSize", joystickSize, SIZE_PERCENT_RANGE)
            checkInRange("borderWidthRatio", borderWidthRatio, BORDER_RADIO_RANGE)
        }

        override fun isModified(other: StyleConfig): Boolean {
            return this.alpha != other.alpha ||
                    this.backgroundColor != other.backgroundColor ||
                    this.joystickColor != other.joystickColor ||
                    this.joystickCanLockColor != other.joystickCanLockColor ||
                    this.joystickLockedColor != other.joystickLockedColor ||
                    this.lockMarkColor != other.lockMarkColor ||
                    this.borderWidthRatio != other.borderWidthRatio ||
                    this.borderColor != other.borderColor ||
                    this.backgroundShape != other.backgroundShape ||
                    this.joystickShape != other.joystickShape ||
                    this.joystickSize != other.joystickSize
        }
    }

    override fun isModified(other: JoystickStyle): Boolean {
        return this.uuid != other.uuid ||
                this.lightStyle.isModified(other.lightStyle) ||
                this.darkStyle.isModified(other.darkStyle)
    }
}

val DefaultJoystickStyleConfig = JoystickStyle.StyleConfig(
    alpha = 1.0f,
    backgroundColor = Color.Black.copy(0.5f),
    joystickColor = Color.White.copy(alpha = 0.5f),
    joystickCanLockColor = Color.Yellow.copy(alpha = 0.5f),
    joystickLockedColor = Color.Green.copy(alpha = 0.5f),
    lockMarkColor = Color.White,
    borderWidthRatio = 0,
    borderColor = Color.White,
    backgroundShape = 50,
    joystickShape = 50,
    joystickSize = 0.5f
)

val DefaultJoystickStyle = JoystickStyle(
    name = "Default",
    uuid = randomUUID(),
    lightStyle = DefaultJoystickStyleConfig,
    darkStyle = DefaultJoystickStyleConfig
)

fun JoystickStyle.cloneNew(): JoystickStyle {
    return JoystickStyle(
        name = name,
        uuid = randomUUID(),
        lightStyle = lightStyle,
        darkStyle = darkStyle,
    )
}

fun createNewJoystickStyle(name: String): JoystickStyle =
    DefaultJoystickStyle.copy(
        name = name,
        uuid = randomUUID()
    )
