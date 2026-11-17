package com.cmc.launcher.setting

import com.cmc.launcher.BuildKeys
import com.tencent.mmkv.MMKV

fun launcherMMKV(): MMKV = MMKV.mmkvWithID(BuildKeys.LAUNCHER_IDENTIFIER, MMKV.MULTI_PROCESS_MODE)
