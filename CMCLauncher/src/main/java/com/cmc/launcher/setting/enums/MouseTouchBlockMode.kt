package com.cmc.launcher.setting.enums

import com.cmc.launcher.R

enum class MouseTouchBlockMode(val nameRes: Int) {
    NONE(R.string.generic_close),

    LEFT(R.string.settings_control_mouse_block_touch_left),

    RIGHT(R.string.settings_control_mouse_block_touch_right)
}
