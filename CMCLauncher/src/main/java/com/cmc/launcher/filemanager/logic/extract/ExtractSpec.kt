package com.cmc.launcher.filemanager.logic.extract

data class ExtractOptions(
    val independentFolder: Boolean = true,
    val password: String? = null
)

data class ExtractSummary(
    val entryCount: Int
)
