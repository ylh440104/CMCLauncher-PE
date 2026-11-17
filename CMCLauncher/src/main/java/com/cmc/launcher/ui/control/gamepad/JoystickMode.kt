package com.cmc.launcher.ui.control.gamepad

import com.cmc.launcher.R

enum class JoystickMode(val titleRes: Int, val summaryRes: Int) {

    LeftMovement(
        titleRes = R.string.settings_gamepad_joystick_mode_left,
        summaryRes = R.string.settings_gamepad_joystick_mode_left_summary
    ),

    RightMovement(
        titleRes = R.string.settings_gamepad_joystick_mode_right,
        summaryRes = R.string.settings_gamepad_joystick_mode_right_summary
    )
}
