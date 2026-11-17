package com.cmc.launcher.game.version.mod

import android.os.Parcelable
import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.download.addon.platform.PlatformVersion
import com.cmc.launcher.game.download.addon.platform.curseforge.models.CurseForgeFile
import com.cmc.launcher.game.download.addon.platform.modrinth.models.ModrinthVersion
import kotlinx.parcelize.Parcelize

@Parcelize
data class InstalledMod(
    val platform: Platform,
    val projectId: String,
    val versionId: String,
    val versionName: String,
    val notFound: Boolean = false
) : Parcelable

fun PlatformVersion.toInstalledMod(): InstalledMod = when (this) {
    is ModrinthVersion -> InstalledMod(
        platform = Platform.MODRINTH,
        projectId = projectId,
        versionId = id,
        versionName = versionNumber
    )
    is CurseForgeFile -> InstalledMod(
        platform = Platform.CURSEFORGE,
        projectId = modId.toString(),
        versionId = id.toString(),
        versionName = displayName
    )
    else -> error("Unknown version type: $this")
}
