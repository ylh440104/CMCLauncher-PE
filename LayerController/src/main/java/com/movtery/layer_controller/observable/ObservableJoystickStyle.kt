package com.movtery.layer_controller.observable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.movtery.layer_controller.data.DefaultJoystickStyle
import com.movtery.layer_controller.data.JoystickStyle
import com.movtery.layer_controller.data.cloneNew

class ObservableJoystickStyle(
    private val style: JoystickStyle
) : Packable<JoystickStyle> {
    var name by mutableStateOf(style.name)
    val uuid = style.uuid
    var commonStyle by mutableStateOf(style.commonStyle)
    var lightStyle = ObservableJoystickStyleConfig(style.lightStyle)
    var darkStyle = ObservableJoystickStyleConfig(style.darkStyle)

    override fun pack(): JoystickStyle {
        return JoystickStyle(
            name = name,
            uuid = uuid,
            commonStyle = commonStyle,
            lightStyle = lightStyle.pack(),
            darkStyle = darkStyle.pack()
        )
    }

    override fun isModified(): Boolean {
        return this.style.isModified(pack())
    }
}

val DefaultObservableJoystickStyle = ObservableJoystickStyle(DefaultJoystickStyle)

fun ObservableJoystickStyle.cloneNew(): ObservableJoystickStyle {
    return ObservableJoystickStyle(pack().cloneNew())
}
