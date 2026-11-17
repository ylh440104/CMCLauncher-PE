package com.cmc.launcher.filemanager.logic

import com.cmc.launcher.filemanager.logic.ops.deleteRecursivePath
import java.nio.file.Files
import java.nio.file.Path

private const val COMPRESS_SUBDIR = "fileManagerCompress"
private const val EXTRACT_SUBDIR = "fileManagerExtract"
private const val IMPORT_SUBDIR = "fileManagerImport"

class TempWorkspace(private val cacheRoot: Path) {

    fun compressTempFile(extension: String): Path {
        val dir = cacheRoot.resolve(COMPRESS_SUBDIR)
        runCatching {
            Files.createDirectories(dir)
        }
        return dir.resolve("tmp_${System.nanoTime()}.$extension")
    }

    fun extractTempDir(): Path = uniqueDir(EXTRACT_SUBDIR)

    fun importTempDir(): Path = uniqueDir(IMPORT_SUBDIR)

    suspend fun delete(path: Path) {
        deleteRecursivePath(path)
    }

    fun isInside(path: Path): Boolean {
        val normalized = path.normalize().toAbsolutePath()
        return normalized.startsWith(cacheRoot)
    }

    private fun uniqueDir(sub: String): Path {
        val base = cacheRoot.resolve(sub)
        runCatching {
            Files.createDirectories(base)
        }
        return base.resolve("tmp_${System.nanoTime()}")
    }
}
