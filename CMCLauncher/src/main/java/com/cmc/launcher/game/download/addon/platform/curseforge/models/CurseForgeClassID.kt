package com.cmc.launcher.game.download.addon.platform.curseforge.models

import com.cmc.launcher.game.download.addon.platform.PlatformClasses

enum class CurseForgeClassID(val classID: Int, val slug: String) {

    MOD(6, "mc-mods"),

    MOD_PACK(4471, "modpacks"),

    RESOURCE_PACK(12, "texture-packs"),

    SAVES(17, "worlds"),

    SHADERS(6552, "shaders")
}

fun CurseForgeData.getClassIdOrNull(): CurseForgeClassID? {
    return if (classId == null) {
        null
    } else {
        CurseForgeClassID.entries.find { id ->
            id.classID == classId
        }
    }
}

fun CurseForgeData.getPlatformClassesOrNull(): PlatformClasses? {
    val classIdType = getClassIdOrNull() ?: return null
    return when (classIdType) {
        CurseForgeClassID.MOD -> PlatformClasses.MOD
        CurseForgeClassID.MOD_PACK -> PlatformClasses.MOD_PACK
        CurseForgeClassID.RESOURCE_PACK -> PlatformClasses.RESOURCE_PACK
        CurseForgeClassID.SAVES -> PlatformClasses.SAVES
        CurseForgeClassID.SHADERS -> PlatformClasses.SHADERS
    }
}
