package com.cmc.launcher.setting.enums

import com.cmc.launcher.R

enum class GamepadInputMode(val titleRes: Int, val summaryRes: Int) {

    Mapped(
        titleRes = R.string.settings_gamepad_input_mode_mapped,
        summaryRes = R.string.settings_gamepad_input_mode_mapped_summary
    ),

    SdlDirect(
        titleRes = R.string.settings_gamepad_input_mode_sdl,
        summaryRes = R.string.settings_gamepad_input_mode_sdl_summary
    )
}
