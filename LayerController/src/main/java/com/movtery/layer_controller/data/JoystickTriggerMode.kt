package com.movtery.layer_controller.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class JoystickTriggerMode {

    @SerialName("drag")
    DRAG,

    @SerialName("touch")
    TOUCH
}
