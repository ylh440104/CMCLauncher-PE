package com.cmc.launcher.filemanager.viewmodel

import java.nio.file.Path

data class FmClipboard(

    val sources: List<Path>,

    val isCut: Boolean
)
