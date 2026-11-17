package com.cmc.launcher.game.version.installed

import com.cmc.launcher.game.addons.modloader.ModLoader
import com.cmc.launcher.game.version.installed.VersionType.MODLOADERS
import com.cmc.launcher.game.version.installed.VersionType.UNKNOWN
import com.cmc.launcher.game.version.installed.VersionType.VANILLA

enum class VersionType {

    VANILLA,

    MODLOADERS,

    UNKNOWN
}

private val loaders = ModLoader.entries.filter { it.isLoader }

fun VersionInfo?.getVersionType(): VersionType {
    val loader = this?.primaryLoader
    return when {
        loader == null || loader.loader == ModLoader.OPTIFINE -> VANILLA
        loader.loader in loaders -> MODLOADERS
        else -> UNKNOWN
    }
}
