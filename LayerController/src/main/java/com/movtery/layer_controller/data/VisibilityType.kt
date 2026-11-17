package com.movtery.layer_controller.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class VisibilityType {

    @SerialName("always")
    ALWAYS,

    @SerialName("in_game")
    IN_GAME,

    @SerialName("in_menu")
    IN_MENU
}
