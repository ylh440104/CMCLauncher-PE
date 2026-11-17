package com.cmc.launcher.ui.control.gamepad

import com.tencent.mmkv.MMKV

fun remapperMMKV(): MMKV = MMKV.mmkvWithID("GamepadRemapper", MMKV.MULTI_PROCESS_MODE)

fun keyMappingMMKV(): MMKV = MMKV.mmkvWithID("GamepadKeyMapping", MMKV.MULTI_PROCESS_MODE)

fun keyMappingListMMKV(): MMKV = MMKV.mmkvWithID("GamepadKeyMappingList", MMKV.MULTI_PROCESS_MODE)
