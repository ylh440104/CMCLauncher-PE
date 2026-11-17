package com.cmc.launcher.game.versioninfo.models

import com.cmc.launcher.game.versioninfo.AprilFoolsVersion
import com.cmc.launcher.game.versioninfo.MinecraftVersion
import com.cmc.launcher.game.versioninfo.allAprilFools

fun List<VersionManifest.Version>.mapVersion(): List<MinecraftVersion> {
    return this.map { version ->

        val aprilFoolsVersion = allAprilFools.find { it.version.equals(version.id, ignoreCase = true) }

        MinecraftVersion(
            version = version,
            type = checkVersionType(version.type, aprilFoolsVersion),
            summary = aprilFoolsVersion?.type?.summary,
            urlSuffix = aprilFoolsVersion?.type?.urlSuffix
        )
    }
}

fun checkVersionType(
    versionId: String,
    type: String?,
): MinecraftVersion.Type {

    val aprilFoolsVersion = allAprilFools.find { it.version.equals(versionId, ignoreCase = true) }

    return checkVersionType(
        type = type,
        aprilFoolsVersion = aprilFoolsVersion
    )
}

private fun checkVersionType(
    type: String?,
    aprilFoolsVersion: AprilFoolsVersion?
): MinecraftVersion.Type {
    return if (aprilFoolsVersion != null) {

        MinecraftVersion.Type.AprilFools
    } else {
        when (type) {
            "release" -> MinecraftVersion.Type.Release
            "snapshot", "pending", "unobfuscated" -> MinecraftVersion.Type.Snapshot
            "old_beta" -> MinecraftVersion.Type.OldBeta
            "old_alpha" -> MinecraftVersion.Type.OldAlpha
            else -> MinecraftVersion.Type.Unknown
        }
    }
}

fun MinecraftVersion.isType(
    release: Boolean,
    snapshot: Boolean,
    aprilFools: Boolean,
    old: Boolean
) = when (type) {
    MinecraftVersion.Type.Release -> release
    MinecraftVersion.Type.Snapshot -> snapshot
    MinecraftVersion.Type.OldBeta -> old
    MinecraftVersion.Type.OldAlpha -> old
    MinecraftVersion.Type.AprilFools -> aprilFools
    MinecraftVersion.Type.Unknown -> old
}
