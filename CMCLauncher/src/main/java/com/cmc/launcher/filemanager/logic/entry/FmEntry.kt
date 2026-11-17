package com.cmc.launcher.filemanager.logic.entry

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

data class FmEntry(
    val path: Path,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedMs: Long,
    val hidden: Boolean,
    val archiveType: ArchiveType?,
    val writable: Boolean
) {
    val isFile: Boolean get() = !isDirectory
}

enum class ArchiveType {
    ZIP,
    SEVEN_Z,
    TAR;

    companion object {
        fun knownSuffix() = listOf(".tar.gz", ".tar.bz2", ".tar.xz", ".zip", ".7z", ".tar")

        fun of(name: String): ArchiveType? {
            val lower = name.lowercase()
            return when {
                lower.endsWith(".zip") -> ZIP
                lower.endsWith(".7z") -> SEVEN_Z
                lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> TAR
                lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") -> TAR
                lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> TAR
                lower.endsWith(".tar") -> TAR
                else -> null
            }
        }
    }
}

data class FmListResult(
    val rootDir: Path,
    val currentDir: Path,
    val ancestors: List<Path>,
    val entries: List<FmEntry>,
    val hasSubdirectory: Boolean,
    val writable: Boolean
)

class BrowseException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal fun Path.toFmEntry(
    attrs: BasicFileAttributes,
): FmEntry {
    val name = fileName?.toString() ?: toString()
    return FmEntry(
        path = this,
        name = name,
        isDirectory = attrs.isDirectory,
        size = if (attrs.isRegularFile) attrs.size() else 0L,
        modifiedMs = attrs.lastModifiedTime().toMillis(),
        hidden = name.startsWith("."),
        archiveType = if (attrs.isRegularFile) ArchiveType.of(name) else null,
        writable = Files.isWritable(this)
    )
}
