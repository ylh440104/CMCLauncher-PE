package com.cmc.launcher.game.versioninfo

import com.cmc.launcher.game.versioninfo.models.VersionManifest

class MinecraftVersion(
    val version: VersionManifest.Version,
    val type: Type,
    val summary: Int?,
    val urlSuffix: String? = null
): Comparable<MinecraftVersion> {
    override fun compareTo(other: MinecraftVersion): Int {
        return version.releaseTime.compareTo(other.version.releaseTime)
    }

    enum class Type {

        Release,

        Snapshot,

        OldBeta,

        OldAlpha,

        AprilFools,

        Unknown
    }
}
