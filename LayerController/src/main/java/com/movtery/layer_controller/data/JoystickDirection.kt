package com.movtery.layer_controller.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class JoystickDirection {
    @SerialName("east") East,
    @SerialName("north_east") NorthEast,
    @SerialName("north") North,
    @SerialName("north_west") NorthWest,
    @SerialName("west") West,
    @SerialName("south_west") SouthWest,
    @SerialName("south") South,
    @SerialName("south_east") SouthEast,
    @SerialName("none") None
}
