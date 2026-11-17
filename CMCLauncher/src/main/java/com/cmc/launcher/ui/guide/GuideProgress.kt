package com.cmc.launcher.ui.guide

import com.cmc.launcher.BuildKeys
import com.tencent.mmkv.MMKV

object GuideProgress {
    private val mmkv = MMKV.mmkvWithID(
        "${BuildKeys.LAUNCHER_IDENTIFIER}_guide",
        MMKV.MULTI_PROCESS_MODE
    )

    private fun legacyKeyOf(group: GuideKeys.Keys) = when (group) {
        GuideKeys.Main -> "started_Main"
        GuideKeys.Editor -> "started_Editor"
        else -> null
    }

    fun keyOf(group: GuideKeys.Keys) = when (group) {
        GuideKeys.Main -> "version_Main"
        GuideKeys.Editor -> "version_Editor"
        GuideKeys.JoystickEvents -> "version_JoystickEvents"
    }

    fun playedVersion(group: GuideKeys.Keys): Int {
        val version = mmkv.getInt(keyOf(group), 0)
        if (version != 0) return version

        val legacyKey = legacyKeyOf(group)
        return if (legacyKey != null && mmkv.getBoolean(legacyKey, false)) {
            1
        } else {
            0
        }
    }

    fun isPlayed(group: GuideKeys.Keys): Boolean {
        return playedVersion(group) >= group.version
    }

    fun markPlayed(group: GuideKeys.Keys, version: Int) {
        mmkv.putInt(keyOf(group), version).apply()
    }
}
