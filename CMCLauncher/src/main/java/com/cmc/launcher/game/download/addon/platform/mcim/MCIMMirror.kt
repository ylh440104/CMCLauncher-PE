package com.cmc.launcher.game.download.addon.platform.mcim

import com.cmc.launcher.game.addons.mirror.MirrorPriority
import com.cmc.launcher.game.addons.mirror.orderCandidates
import com.cmc.launcher.game.addons.mirror.resolveMirrorPriority
import com.cmc.launcher.setting.AllSettings
import com.cmc.launcher.utils.isChinaMainland

private const val ROOT = "https://mod.mcimirror.top"

private val REPLACE_MIRROR_HOLDERS = listOf(

    "https://edge.forgecdn.net",

    "https://cdn.modrinth.com"
)

private fun assetPlatformPriority(): MirrorPriority =
    resolveMirrorPriority(AllSettings.addonPlatformSource.getValue(), mainland = true)

fun String.mapMCIMMirrorUrls(): List<String> {
    if (!isChinaMainland()) return listOf(this)

    val mirroredUrl = REPLACE_MIRROR_HOLDERS.find { key ->
        startsWith(key)
    }?.let { origin ->
        replaceFirst(origin, ROOT)
    }

    return orderCandidates(official = this, mirror = mirroredUrl, priority = assetPlatformPriority())
}

fun Array<String>.mapMCIMMirrorUrls(): List<String> {
    if (!isChinaMainland()) return toList()

    val sources = mapNotNull { url ->
        REPLACE_MIRROR_HOLDERS.find { key ->
            url.startsWith(key)
        }?.let { origin ->
            url.replaceFirst(origin, ROOT)
        }
    }
    if (sources.isEmpty()) return toList()

    return when (assetPlatformPriority()) {
        MirrorPriority.OFFICIAL -> this.toList()
        MirrorPriority.MIRROR_FIRST -> sources + this.toList()
    }
}
