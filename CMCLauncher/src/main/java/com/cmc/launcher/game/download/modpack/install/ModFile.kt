package com.cmc.launcher.game.download.modpack.install

import java.io.File

data class ModFile(
    val outputFile: File? = null,
    val downloadUrls: List<String>? = null,
    val sha1: String? = null,
    val getFile: (suspend () -> ModFile)? = null
)
