package com.cmc.launcher.ui.control.joystick

import com.movtery.inputmap.keycodes.MOVEMENT_BACK
import com.movtery.inputmap.keycodes.MOVEMENT_BACK_VALUE
import com.movtery.inputmap.keycodes.MOVEMENT_FORWARD
import com.movtery.inputmap.keycodes.MOVEMENT_FORWARD_VALUE
import com.movtery.inputmap.keycodes.MOVEMENT_LEFT
import com.movtery.inputmap.keycodes.MOVEMENT_LEFT_VALUE
import com.movtery.inputmap.keycodes.MOVEMENT_RIGHT
import com.movtery.inputmap.keycodes.MOVEMENT_RIGHT_VALUE

val directionMapping = mapOf(
    JoystickDirection.East to listOf(
        MOVEMENT_RIGHT to MOVEMENT_RIGHT_VALUE
    ),
    JoystickDirection.NorthEast to listOf(
        MOVEMENT_FORWARD to MOVEMENT_FORWARD_VALUE,
        MOVEMENT_RIGHT to MOVEMENT_RIGHT_VALUE
    ),
    JoystickDirection.North to listOf(
        MOVEMENT_FORWARD to MOVEMENT_FORWARD_VALUE
    ),
    JoystickDirection.NorthWest to listOf(
        MOVEMENT_FORWARD to MOVEMENT_FORWARD_VALUE,
        MOVEMENT_LEFT to MOVEMENT_LEFT_VALUE
    ),
    JoystickDirection.West to listOf(
        MOVEMENT_LEFT to MOVEMENT_LEFT_VALUE
    ),
    JoystickDirection.SouthWest to listOf(
        MOVEMENT_BACK to MOVEMENT_BACK_VALUE,
        MOVEMENT_LEFT to MOVEMENT_LEFT_VALUE
    ),
    JoystickDirection.South to listOf(
        MOVEMENT_BACK to MOVEMENT_BACK_VALUE
    ),
    JoystickDirection.SouthEast to listOf(
        MOVEMENT_BACK to MOVEMENT_BACK_VALUE,
        MOVEMENT_RIGHT to MOVEMENT_RIGHT_VALUE
    ),
    JoystickDirection.None to emptyList()
)

val allAction = listOf(
    MOVEMENT_FORWARD to MOVEMENT_FORWARD_VALUE,
    MOVEMENT_BACK to MOVEMENT_BACK_VALUE,
    MOVEMENT_LEFT to MOVEMENT_LEFT_VALUE,
    MOVEMENT_RIGHT to MOVEMENT_RIGHT_VALUE
)
