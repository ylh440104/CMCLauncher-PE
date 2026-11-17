package com.cmc.launcher.game.version.installed

import java.io.File

enum class VersionFolders(val folderName: String) {

    NONE(""),

    MOD("mods"),

    RESOURCE_PACK("resourcepacks"),

    SAVES("saves"),

    SHADERS("shaderpacks"),

    SCREENSHOTS("screenshots");

    fun getDir(gameDir: String): String {
        return File(gameDir, folderName).absolutePath
    }

    fun getDir(gameDir: File): File {
        return File(gameDir, folderName)
    }
}
