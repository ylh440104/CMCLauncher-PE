package com.cmc.launcher.filemanager.logic.compress

import java.nio.file.Path

enum class CompressFormat(val extension: String, val mimeType: String) {
    ZIP("zip", "application/zip"),
    SEVEN_Z("7z", "application/x-7z-compressed"),
    TAR("tar", "application/x-tar");

    val suffix: String get() = ".$extension"

    val defaultMethod: CompressMethod
        get() = when (this) {
            ZIP -> CompressMethod.DEFLATE
            SEVEN_Z -> CompressMethod.LZMA2
            TAR -> CompressMethod.TAR_POSIX
        }
}

enum class CompressMethod(val displayName: String) {

    STORE("Store"),

    DEFLATE("Deflate"),

    LZMA2("LZMA2"),
    BZIP2("BZIP2"),

    TAR_GNU("GNU"),

    TAR_POSIX("POSIX");

    companion object {

        val zipMethods = listOf(STORE, DEFLATE)

        val sevenZMethods = listOf(LZMA2, BZIP2)

        val tarMethods = listOf(TAR_GNU, TAR_POSIX)
    }
}

data class CompressOptions(
    val format: CompressFormat,
    val method: CompressMethod? = null,
    val level: Int? = null,
    val password: String? = null
)

data class CompressSummary(
    val outputPath: Path,
    val entryCount: Int
)
