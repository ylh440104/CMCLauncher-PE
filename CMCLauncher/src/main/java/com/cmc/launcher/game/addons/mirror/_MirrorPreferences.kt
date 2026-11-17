package com.cmc.launcher.game.addons.mirror

import com.cmc.launcher.setting.enums.MirrorSourceType

enum class MirrorPriority {

    OFFICIAL,

    MIRROR_FIRST
}

fun resolveMirrorPriority(source: MirrorSourceType, mainland: Boolean): MirrorPriority =
    when {
        source == MirrorSourceType.OFFICIAL -> MirrorPriority.OFFICIAL
        source == MirrorSourceType.MIRROR -> MirrorPriority.MIRROR_FIRST
        mainland -> MirrorPriority.MIRROR_FIRST
        else -> MirrorPriority.OFFICIAL
    }

fun orderCandidates(official: String, mirror: String?, priority: MirrorPriority): List<String> =
    when (priority) {
        MirrorPriority.OFFICIAL -> listOfNotNull(official)
        MirrorPriority.MIRROR_FIRST -> listOfNotNull(mirror, official)
    }
