package com.cmc.launcher.setting.enums

import com.cmc.launcher.R

enum class MirrorSourceType(val textRes: Int) {

    AUTO(R.string.settings_launcher_mirror_auto),

    OFFICIAL(R.string.settings_launcher_mirror_official),

    MIRROR(R.string.settings_launcher_mirror_mirror);

    companion object {

        val LEGACY_NAMES: Map<String, MirrorSourceType> = mapOf(
            "OFFICIAL_FIRST" to OFFICIAL,
            "MIRROR_FIRST" to MIRROR
        )
    }
}
