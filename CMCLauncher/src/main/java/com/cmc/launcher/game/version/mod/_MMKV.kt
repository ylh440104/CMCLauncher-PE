package com.cmc.launcher.game.version.mod

import com.tencent.mmkv.MMKV

fun modProjectCache(): MMKV = MMKV.mmkvWithID("ModProjectHashMapper", MMKV.MULTI_PROCESS_MODE)

fun modFileCache(): MMKV = MMKV.mmkvWithID("ModFileHashMapper", MMKV.MULTI_PROCESS_MODE)

fun installedModCache(): MMKV = MMKV.mmkvWithID("InstalledModHashMapper", MMKV.MULTI_PROCESS_MODE)
