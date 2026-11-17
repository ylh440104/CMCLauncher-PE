package com.cmc.launcher.ui.screens.content.versions.elements

import java.io.File

sealed interface ShaderOperation {
    data object None : ShaderOperation

    data object Progress : ShaderOperation

    data class Rename(val info: ShaderPackInfo) : ShaderOperation

    data class Delete(val info: ShaderPackInfo) : ShaderOperation
}

data class ShaderPackInfo(
    val file: File,
    val fileSize: Long
)

fun List<ShaderPackInfo>.filterShaders(
    nameFilter: String
) = this.filter {
    nameFilter.isEmpty() || it.file.name.contains(nameFilter, true)
}
