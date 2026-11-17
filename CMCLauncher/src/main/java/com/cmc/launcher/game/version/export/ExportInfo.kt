package com.cmc.launcher.game.version.export

import com.cmc.launcher.game.addons.modloader.ModLoader
import java.io.File

data class ExportInfo(
    val gamePath: File,
    val name: String = "",
    val summary: String? = null,
    val author: String = "",
    val version: String = "",
    val mcVersion: String = "",
    val loader: LoaderVersion? = null,
    val selectedFiles: List<File> = emptyList(),
    val minMemory: Int = 0,
    val gameArgs: String = "",
    val javaArgs: String = "",
    val fileApi: String? = null,
    val url: String = "",
    val forceUpdate: Boolean = false,
    val packType: PackType = PackType.Modrinth,
    val packModrinth: Boolean = false,
    val packCurseForge: Boolean = false
) {

    data class LoaderVersion(
        val loader: ModLoader,
        val version: String
    )
}
