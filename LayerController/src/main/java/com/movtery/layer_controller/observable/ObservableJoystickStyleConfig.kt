package com.movtery.layer_controller.observable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.movtery.layer_controller.data.JoystickStyle

class ObservableJoystickStyleConfig(
    private val config: JoystickStyle.StyleConfig
) : Packable<JoystickStyle.StyleConfig> {
    var alpha by mutableFloatStateOf(config.alpha)
    var backgroundColor by mutableStateOf(config.backgroundColor)
    var joystickColor by mutableStateOf(config.joystickColor)
    var joystickCanLockColor by mutableStateOf(config.joystickCanLockColor)
    var joystickLockedColor by mutableStateOf(config.joystickLockedColor)
    var lockMarkColor by mutableStateOf(config.lockMarkColor)
    var borderWidthRatio by mutableIntStateOf(config.borderWidthRatio)
    var borderColor by mutableStateOf(config.borderColor)
    var backgroundShape by mutableIntStateOf(config.backgroundShape)
    var joystickShape by mutableIntStateOf(config.joystickShape)
    var joystickSize by mutableFloatStateOf(config.joystickSize)

    override fun pack(): JoystickStyle.StyleConfig {
        return JoystickStyle.StyleConfig(
            alpha = alpha,
            backgroundColor = backgroundColor,
            joystickColor = joystickColor,
            joystickCanLockColor = joystickCanLockColor,
            joystickLockedColor = joystickLockedColor,
            lockMarkColor = lockMarkColor,
            borderWidthRatio = borderWidthRatio,
            borderColor = borderColor,
            backgroundShape = backgroundShape,
            joystickShape = joystickShape,
            joystickSize = joystickSize
        )
    }

    override fun isModified(): Boolean {
        return this.config.isModified(pack())
    }
}
